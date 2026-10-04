package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleCoordinates extends Module {
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static final Field RENDER_INFOS = Fields.find(RenderGlobal.class, "renderInfos", "field_72755_R");
    private static final Field VIEW_FRUSTUM = Fields.find(RenderGlobal.class, "viewFrustum", "field_175008_n");

    final BoolSetting showWhileTyping = bool("showWhileTyping", true);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ChoiceSetting<ModuleArmorStatus.ListMode> mode = choice("mode", ModuleArmorStatus.ListMode.VERTICAL);
    private final KeySetting copyCoords = keyCombo("copyCoords");
    final BoolSetting moveChildrenIndividually = bool("moveChildrenIndividually", false);
    private final BoolSetting decimalCoordinates = bool("decimalCoordinates", false);
    private final NumberSetting decimalPlaces = integer("decimalPlaces", 2, 0, 10);

    private final ModuleCoordinatesChild x, y, z, c, direction, biome;
    private boolean copyHeld;

    private String biomeName = "Plains";
    private int biomeColor = -10510539;

    public ModuleCoordinates() {
        super("COORDINATES", true);
        hud(new Hud());

        x = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.X, "X", () -> number(player().posX), "500"), "generalOptions");
        y = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.Y, "Y", () -> number(player().getEntityBoundingBox().minY), "62"), "generalOptions");
        z = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.Z, "Z", () -> number(player().posZ), "250"), "generalOptions");
        c = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.C, "C", () -> unculled() + "/" + maximum(), "92/4269"), "generalOptions");
        direction = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.DIRECTION, "Direction", () -> direction(player().rotationYaw), "N"), "generalOptions");
        biome = child(new ModuleCoordinatesChild(this, ModuleCoordinatesChild.Kind.BIOME, "Biome", () -> biomeName, "Plains"), "generalOptions");
    }

    private static EntityPlayerSP player() { return Minecraft.getMinecraft().thePlayer; }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, textShadow, showWhileTyping).hideIf(moveChildrenIndividually::on);
            s.group(background, g -> g.group(border, b -> b.add(borderThickness))).hideIf(moveChildrenIndividually::on);
            s.add(copyCoords).hideIf(moveChildrenIndividually::on);
            s.group(decimalCoordinates, g -> g.add(decimalPlaces));
            s.add(moveChildrenIndividually);
        });
        page.section("colorOptions", s -> s.add(backgroundColor, borderColor).hideIf(moveChildrenIndividually::on));
    }

    private String number(double d) {
        if (decimalCoordinates.on()) return String.format(Locale.ROOT, "%." + decimalPlaces.intValue() + "f", d);
        return String.valueOf(MathHelper.floor_double(d));
    }

    static String direction(float yaw) {
        double d = MathHelper.wrapAngleTo180_float(yaw) + 180.0;
        d += 22.5;
        d %= 360.0;
        return DIRECTIONS[MathHelper.floor_double(d / 45.0)];
    }

    private static int unculled() {
        Object infos = Fields.get(RENDER_INFOS, Minecraft.getMinecraft().renderGlobal);
        return infos instanceof Collection ? ((Collection<?>)infos).size() : 0;
    }

    private static int maximum() {
        Object frustum = Fields.get(VIEW_FRUSTUM, Minecraft.getMinecraft().renderGlobal);
        return frustum instanceof ViewFrustum ? ((ViewFrustum)frustum).renderChunks.length : 0;
    }

    int biomeColor() { return biomeColor; }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player != null && mc.theWorld != null) {
            BiomeGenBase b = mc.theWorld.getBiomeGenForCoords(new BlockPos(player));
            Biome known = b == null ? null : BIOMES.get(b.biomeID);
            if (known != null) { biomeName = known.name; biomeColor = known.color; }
            else if (b != null) { biomeName = b.biomeName; biomeColor = -1; }
        }
        boolean down = isEnabled() && mc.currentScreen == null && copyCoords.isDown();
        if (down && !copyHeld && mc.theWorld != null && player != null) {
            GuiScreen.setClipboardString(String.format("X: %s Y: %s Z: %s", (int)player.posX, (int)player.posY, (int)player.posZ));
            LunarNotifications.info("Copied coordinates to clipboard!");
        }
        copyHeld = down;
    }

    private final class Hud extends HudElement {
        Hud() { super(ModuleCoordinates.this, 0, 0, HudAnchor.TOP_LEFT); }

        @Override public boolean editable() { return !moveChildrenIndividually.on(); }

        @Override public boolean visible(boolean preview) {
            if (moveChildrenIndividually.on()) return false;
            if (!showWhileTyping.on() && Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatOpen()) return false;
            if (!preview && player() == null) return false;
            if (c.isEnabled() || x.isEnabled() || y.isEnabled() || z.isEnabled() || biome.isEnabled() || direction.isEnabled()) {
                draw(preview, false);
                return width() > 0;
            }
            size(0, 0);
            return false;
        }

        @Override public void render(boolean preview) { draw(preview, true); }

        private float text(ColorSetting color, String text, float x, float y, boolean shadow, boolean paint) {
            if (paint) return Draw.text(color, text, x, y, shadow);
            return x + Draw.width(text);
        }

        private void draw(boolean preview, boolean paint) {
            float yaw;
            if (preview) yaw = 180.0f;
            else {
                Entity view = mc().getRenderViewEntity();
                yaw = view != null ? view.rotationYaw : player().rotationYaw;
            }
            boolean sx = x.isEnabled(), sy = y.isEnabled(), sz = z.isEnabled(), sBiome = biome.isEnabled(), sDir = direction.isEnabled();
            boolean affect = direction.directionAffect.on();
            boolean sc = c.isEnabled();
            boolean bg = background.on(), shadow = textShadow.on();
            int fh = Draw.fontHeight();
            if (paint && bg) {
                Draw.fill(backgroundColor, 0, 0, width(), height());
                if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            }
            String facing = direction(yaw);
            float w, h = 0;
            if (mode.is(ModuleArmorStatus.ListMode.HORIZONTAL)) {
                ModuleCoordinatesChild first = sx ? x : sy ? y : sz ? z : sc ? c : null;
                float top = bg ? 3 : 0;
                w = 5;
                if (!bg && first != null) w = text(first.labelColor, "(", w, top, shadow, paint);
                ColorSetting after;
                if (sx) {
                    if (x.showLabel.on()) w = text(x.labelColor, "X: ", w, top, shadow, paint);
                    w = text(x.valueColor(), x.value(preview), w, top, shadow, paint);
                    if (sDir && affect && (facing.contains("W") || facing.contains("E"))) {
                        w = text(direction.directionAffectXColor, facing.contains("W") ? " (-)" : " (+)", w, top, shadow, paint);
                        after = direction.directionAffectXColor;
                    } else after = x.valueColor();
                    if (sy || sz || sc) w = text(after, ", ", w, top, shadow, paint);
                }
                if (sy) {
                    if (y.showLabel.on()) w = text(y.labelColor, "Y: ", w, top, shadow, paint);
                    w = text(y.valueColor(), y.value(preview), w, top, shadow, paint);
                    if (sz || sc) w = text(y.valueColor(), ", ", w, top, shadow, paint);
                }
                if (sz) {
                    if (z.showLabel.on()) w = text(z.labelColor, "Z: ", w, top, shadow, paint);
                    w = text(z.valueColor(), z.value(preview), w, top, shadow, paint);
                    if (sDir && affect && (facing.contains("N") || facing.contains("S"))) {
                        w = text(direction.directionAffectZColor, facing.contains("N") ? " (-)" : " (+)", w, top, shadow, paint);
                        after = direction.directionAffectZColor;
                    } else after = z.valueColor();
                    if (sc) w = text(after, ", ", w, top, shadow, paint);
                }
                if (sc) {
                    if (c.showLabel.on()) w = text(c.labelColor, "C: ", w, top, shadow, paint);
                    w = text(c.valueColor(), c.value(preview), w, top, shadow, paint);
                }
                if (!bg) { if (first != null) w = text(first.labelColor, ")", w, top, shadow, paint); }
                else h += 4;
                if (sDir && direction.cardinalDirection.on()) {
                    String d = (sx || sy || sz || sc || sBiome) ? " " + facing : facing;
                    w = text(direction.valueColor(), d, w, top, shadow, paint);
                }
                h += fh;
            } else {
                w = 0;
                h = 5;
                float yX = 0, yY = 0, yZ = 0, yC = 0, yB = 0, yDir;
                boolean signs = sDir && affect;
                if (sx) { w = Math.max(w, line(x, x.value(preview), h, shadow, paint)); yX = h; h += fh + 2; }
                if (sy) { w = Math.max(w, line(y, y.value(preview), h, shadow, paint)); yY = h; h += fh + 2; }
                if (sz) { w = Math.max(w, line(z, z.value(preview), h, shadow, paint)); yZ = h; h += fh + 2; }
                if (sc) { w = Math.max(w, line(c, c.value(preview), h, shadow, paint)); yC = h; h += fh + 2; }
                if (sBiome) {
                    float lx = biome.showLabel.on() ? text(biome.labelColor, lang("biome") + ": ", 5, h, shadow, paint) : 5;
                    String name = preview ? "Plains" : biomeName;
                    int color = biome.presetBiomeColor.on() ? (preview ? -10510539 : biomeColor) : biome.valueColor().color(0);
                    if (paint) Draw.text(name, lx, h, color, shadow);
                    w = Math.max(w, lx + Draw.width(name));
                    yB = h;
                    h += fh + 2;
                }
                if (sDir) {
                    boolean any = sx || sy || sz || sc || sBiome;
                    if (any) w += 20;
                    else { w = Draw.width(facing); h = fh + 3; }
                    float right = 12, dx = w - right + 3, offset = any ? right - Draw.width(facing) : 0;
                    if (sy) yDir = yY;
                    else if (sx || sz) {
                        if (sx && sz) { yDir = yX + (yZ - yX) / 2; dx -= 9; }
                        else { yDir = sx ? yX : yZ; signs = false; }
                    } else if (sc) yDir = yC;
                    else if (sBiome) yDir = yB;
                    else { yDir = 3; dx = 3; signs = false; }
                    if (direction.cardinalDirection.on() && paint) Draw.text(direction.valueColor(), facing, dx + offset / 2, yDir, shadow);
                }
                if (sDir && signs && paint) {
                    float sw = Draw.width("-");
                    if (sx && (facing.contains("W") || facing.contains("E"))) Draw.text(direction.directionAffectXColor, facing.contains("W") ? "-" : "+", w - sw, yX, shadow);
                    if (sz && (facing.contains("N") || facing.contains("S"))) Draw.text(direction.directionAffectZColor, facing.contains("N") ? "-" : "+", w - sw, yZ, shadow);
                }
                h += bg ? 2 : 8;
            }
            size(w != 0 ? w + 5 : 0, w != 0 ? h : 0);
        }

        private float line(ModuleCoordinatesChild line, String value, float y, boolean shadow, boolean paint) {
            float x = line.showLabel.on() ? text(line.labelColor, line.kind.name() + ": ", 5, y, shadow, paint) : 5;
            return text(line.valueColor(), value, x, y, shadow, paint);
        }
    }

    private static final class Biome {
        final String name;
        final int color;
        Biome(String id, int color) {
            StringBuilder out = new StringBuilder();
            for (String word : id.split("_")) out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            this.name = out.toString();
            this.color = color;
        }
    }

    private static final Map<Integer, Biome> BIOMES = new HashMap<Integer, Biome>();
    private static void biome(int id, String name, int color) { BIOMES.put(id, new Biome(name, color)); }
    static {
        biome(0, "ocean", -16752657); biome(1, "plains", -10510539); biome(2, "desert", -1516367);
        biome(3, "mountains", -7368817); biome(4, "forest", -11629531); biome(5, "taiga", -11637939);
        biome(6, "swamp", -12295900); biome(7, "river", -16752657); biome(8, "nether_wastes", -7729393);
        biome(9, "the_end", -9568091); biome(10, "frozen_ocean", -6308109); biome(11, "frozen_river", -6308109);
        biome(12, "snowy_tundra", -6308109); biome(13, "snowy_mountains", -6308109); biome(14, "mushroom_fields", -9080700);
        biome(15, "mushroom_field_shore", -9080700); biome(16, "beach", -1516367); biome(17, "desert_hills", -1516367);
        biome(18, "wooded_hills", -11629531); biome(19, "taiga_hills", -11637939); biome(20, "mountain_edge", -7368817);
        biome(21, "jungle", -14913786); biome(22, "jungle_hills", -14913786); biome(23, "jungle_edge", -14913786);
        biome(24, "deep_ocean", -16752657); biome(25, "stone_shore", -7368817); biome(26, "snowy_beach", -6308109);
        biome(27, "birch_forest", -3223858); biome(28, "birch_forest_hills", -3223858); biome(29, "dark_forest", -13145823);
        biome(30, "snowy_taiga", -6308109); biome(31, "snowy_taiga_hills", -6308109); biome(32, "giant_tree_taiga", -11637939);
        biome(33, "giant_tree_taiga_hills", -11637939); biome(34, "wooded_mountains", -7368817); biome(35, "savanna", -8225734);
        biome(36, "savanna_plateau", -8225734); biome(37, "badlands", -5022683); biome(38, "wooded_badlands_plateau", -5022683);
        biome(39, "badlands_plateau", -5022683); biome(127, "the_void", -9568091); biome(129, "sunflower_plains", -10240);
        biome(130, "desert_lakes", -1516367); biome(131, "gravelly_mountains", -7368817); biome(132, "flower_forest", -32787);
        biome(133, "taiga_mountains", -11637939); biome(134, "swamp_hills", -12295900); biome(140, "ice_spikes", -6308109);
        biome(149, "modified_jungle", -14913786); biome(151, "modified_jungle_edge", -14913786); biome(155, "tall_birch_forest", -3223858);
        biome(156, "tall_birch_hills", -3223858); biome(157, "dark_forest_hills", -13145823); biome(158, "snowy_taiga_mountains", -6308109);
        biome(160, "giant_spruce_taiga", -11637939); biome(161, "giant_spruce_taiga_hills", -11637939);
        biome(162, "modified_gravelly_mountains", -7368817); biome(163, "shattered_savanna", -8225734);
        biome(164, "shattered_savanna_plateau", -8225734); biome(165, "eroded_badlands", -5022683);
        biome(166, "modified_wooded_badlands_plateau", -5022683); biome(167, "modified_badlands_plateau", -5022683);
    }
}
