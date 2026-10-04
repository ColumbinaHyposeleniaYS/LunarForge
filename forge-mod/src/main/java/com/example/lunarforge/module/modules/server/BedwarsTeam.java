package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class BedwarsTeam extends Module {
    enum DisplayMode implements ChoiceSetting.Option {
        NORMAL("normal"), TEXT("text"), MINIMAL("minimal");
        final String id;
        DisplayMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    private final ModuleHypixelBedwars bedwars;
    private final BoolSetting flip = bool("flip", false);
    private final ChoiceSetting<DisplayMode> displayMode = choice("displayMode", DisplayMode.MINIMAL);
    private final BoolSetting ignoreCastlesMode = bool("ignoreCastlesMode", true);
    private final BoolSetting showName = bool("showName", true);
    private final BoolSetting dynamicHealthColor = bool("dynamicHealthColor", true);
    private final BoolSetting showHearts = bool("showHearts", true);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ColorSetting titleText = color("titleText", -171);
    private final ColorSetting textColor = color("textColor", -1);
    private final ColorSetting healthColor = color("healthColor", -256);
    private final ColorSetting lowHealthColor = color("lowHealthColor", -5636096);
    private final ColorSetting mediumHealthColor = color("mediumHealthColor", -43691);
    private final ColorSetting highHealthColor = color("highHealthColor", -171);
    private final ColorSetting highestHealthColor = color("highestHealthColor", -11141291);

    private static final class Mate {
        final String name;
        final UUID id;
        final boolean preview;
        Mate(String name, UUID id, boolean preview) { this.name = name; this.id = id; this.preview = preview; }

        EntityPlayer player() { return preview ? null : Minecraft.getMinecraft().theWorld.getPlayerEntityByUUID(id); }

        Integer health() {
            if (preview) return 1 + (int)(Math.abs(id.getLeastSignificantBits() ^ id.getMostSignificantBits()) % 20L);
            Scoreboard board = Minecraft.getMinecraft().theWorld.getScoreboard();
            ScoreObjective o = board.getObjectiveInDisplaySlot(0);
            if (o == null || !board.entityHasObjective(name, o)) return null;
            return board.getValueFromObjective(name, o).getScorePoints();
        }

        ResourceLocation skin() {
            NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
            if (preview || net == null) return STEVE;
            NetworkPlayerInfo info = net.getPlayerInfo(id);
            return info == null ? STEVE : info.getLocationSkin();
        }
    }

    private final List<Mate> sample = Arrays.asList(new Mate("Teammate_1", UUID.randomUUID(), true), new Mate("Teammate_2", UUID.randomUUID(), true),
        new Mate("Teammate_3", UUID.randomUUID(), true));

    private int maxHealth = 20;
    private String map;
    private List<Mate> mates = new ArrayList<Mate>();

    BedwarsTeam(ModuleHypixelBedwars bedwars) {
        super("HYPIXEL_BEDWARS_TEAM_DISPLAY_CHILD", true);
        this.bedwars = bedwars;
        hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(displayMode);
            s.add(showName).hideIf(() -> displayMode.get() != DisplayMode.MINIMAL);
            s.add(dynamicHealthColor, showHearts).hideIf(() -> displayMode.get() == DisplayMode.NORMAL);
            s.add(textShadow, background, border);
            s.add(flip).hideIf(() -> displayMode.get() == DisplayMode.MINIMAL);
        });
        page.section("colorOptions", s -> {
            s.add(titleText, textColor);
            s.add(healthColor).hideIf(() -> displayMode.get() == DisplayMode.NORMAL || dynamicHealthColor.on());
            s.add(backgroundColor).hideIf(() -> !background.on());
            s.add(borderColor, borderThickness).hideIf(() -> !border.on());
            s.add(lowHealthColor, mediumHealthColor, highHealthColor, highestHealthColor).hideIf(() -> displayMode.get() == DisplayMode.NORMAL || !dynamicHealthColor.on());
        });
    }

    private void clear() { mates.clear(); map = null; }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.entity == Minecraft.getMinecraft().thePlayer) maxHealth = 20;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!bedwars.inBedwars() || mc.theWorld == null) { clear(); return; }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        maxHealth = Math.max((int)player.getMaxHealth(), maxHealth);
        HypixelLocation.Location l = HypixelLocation.get();
        if (l == null || l.mode == null || l.mode.contains("_ONE")) { clear(); return; }
        if (!Objects.equals(map, l.map)) { clear(); map = l.map; }
        NetHandlerPlayClient net = mc.getNetHandler();
        if (net == null) return;
        Scoreboard board = mc.theWorld.getScoreboard();
        ScoreObjective health = board.getObjectiveInDisplaySlot(0), sidebar = board.getObjectiveInDisplaySlot(1);
        if (health == null || sidebar == null) return;
        for (Score score : board.getSortedScores(sidebar)) {
            ScorePlayerTeam team = board.getPlayersTeam(score.getPlayerName());
            String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
            if (!line.endsWith("YOU")) continue;
            String plain = StringUtils.stripControlCodes(line);
            if (plain.isEmpty()) break;
            char letter = plain.charAt(0);
            mates.clear();
            for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
                UUID id = info.getGameProfile().getId();
                if (id.equals(player.getUniqueID())) continue;
                String name = info.getGameProfile().getName();
                ScorePlayerTeam their = board.getPlayersTeam(name);
                if (their == null || !board.entityHasObjective(name, health)) continue;
                String formatted = StringUtils.stripControlCodes(ScorePlayerTeam.formatPlayerName(their, name));
                if (formatted.isEmpty() || formatted.charAt(0) != letter) continue;
                mates.add(new Mate(name, id, false));
            }
            if (!mates.isEmpty()) mates.sort((a, b) -> Integer.compare(b.name.length(), a.name.length()));
            break;
        }
    }

    private int healthColor(int hp) {
        if (!dynamicHealthColor.on()) return healthColor.color(0.0f);
        if (hp <= 5) return lowHealthColor.color(0.0f);
        if (hp <= 10) return mediumHealthColor.color(0.0f);
        if (hp <= 15) return highHealthColor.color(0.0f);
        return highestHealthColor.color(0.0f);
    }

    private final class Hud extends HudElement {
        Hud() {
            super(BedwarsTeam.this, 0.0f, 0.0f, HudAnchor.TOP_RIGHT);
            size(134.0f, 40.0f);
        }

        @Override public boolean visible(boolean preview) {
            if (!bedwars.isEnabled() || !bedwars.inBedwars()) { size(0, 0); return false; }
            if (preview) { if (width() == 0.0f) size(134.0f, 40.0f); return true; }
            return showsTeam();
        }

        private boolean showsTeam() {
            boolean shows = bedwars.inGame() && !mates.isEmpty();
            HypixelLocation.Location l = HypixelLocation.get();
            if (shows && ignoreCastlesMode.on() && l != null && l.mode != null && l.mode.contains("_CASTLE")) shows = false;
            if (shows && width() == 0.0f) size(134.0f, 40.0f);
            return shows;
        }

        @Override public void render(boolean preview) {
            if (preview && !showsTeam()) {
                List<Mate> real = mates;
                mates = sample;
                try { draw(); } finally { mates = real; }
            } else {
                draw();
            }
        }

        private void draw() {
            if (background.on()) Draw.fill(backgroundColor, 0, 0, width(), height());
            if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            boolean shadow = textShadow.on();
            float x = 3.0f, y = 3.0f;
            Draw.text(titleText, "§l" + lang("team"), x, y, shadow);
            y += 11.0f;
            DisplayMode mode = displayMode.get();
            float width = 0.0f;
            if (mode == DisplayMode.TEXT) {
                int n = Draw.fontHeight() + 1;
                for (Mate mate : mates) {
                    float w;
                    Integer hp = mate.health();
                    if (flip.on()) {
                        if (hp != null) { health(hp, x, y, shadow); w = 31.0f; }
                        else { Draw.text(healthColor, "N/A", x, y, shadow); w = 22.0f; }
                        Draw.text(textColor, mate.name, x + w, y, shadow);
                        w += Draw.width(mate.name);
                    } else {
                        Draw.text(textColor, mate.name, x, y, shadow);
                        w = Draw.width(mate.name + " ");
                        if (hp != null) { health(hp, x + w, y, shadow); w += 28.0f; }
                        else { Draw.text(healthColor, "N/A", x + w, y, shadow); w += 19.0f; }
                    }
                    y += n;
                    width = Math.max(width, w);
                }
            } else if (mode == DisplayMode.NORMAL) {
                width = 123.0f;
                y = normal(x, y);
            } else {
                width = showName.on() ? 126.0f : 28.0f;
                y = minimal(x, y);
            }
            size(width + 6.0f, Math.round(y + 1.0f));
        }

        private float normal(float x, float y) {
            for (Mate mate : mates) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, 0.0f);
                if (!flip.on()) { head(mate); GlStateManager.translate(27.0f, 0.0f, 0.0f); }
                Draw.text(textColor, mate.name, 0.0f, 3.0f, textShadow.on());
                Integer hp = mate.health();
                if (hp != null) {
                    int red = (int)Math.floor(Math.min(hp, maxHealth) / 2.0);
                    int gold = (int)Math.floor(Math.max(0.0, hp - maxHealth) / 2.0);
                    Draw.text("❤❤❤❤❤❤❤❤❤❤", 0.0f, 12.0f, -12632257, false);
                    Draw.text(hearts(red), 0.0f, 12.0f, -58854, true);
                    if (gold > 0) {
                        y += 2.0f;
                        Draw.text(hearts(gold), 0.0f, 20.0f, -75494, true);
                    }
                } else {
                    Draw.text(textColor, "N/A", 1.0f, 14.0f, textShadow.on());
                }
                if (flip.on()) { GlStateManager.translate(99.0f, 0.0f, 0.0f); head(mate); }
                GlStateManager.popMatrix();
                y += 26.0f;
            }
            return y;
        }

        private String hearts(int n) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < n; i++) b.append('❤');
            return b.toString();
        }

        private float minimal(float x, float y) {
            boolean shadow = textShadow.on();
            x += 3.0f;
            for (Mate mate : mates) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, 0.0f);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                head(mate);

                GlStateManager.pushMatrix();
                GlStateManager.scale(3.0f, 3.0f, 1.0f);
                Draw.rect(-1.0f, 0.0f, 1.0f, 8.0f, -2139062017);
                GlStateManager.popMatrix();
                if (showName.on()) Draw.text(textColor, mate.name, 28.0f, 9.5f, shadow);
                Integer hp = mate.health();
                if (hp != null) {
                    double part = (double)hp / (double)Math.max(maxHealth, hp);
                    int color = dynamicHealthColor.on() ? healthColor(hp)
                        : Color.HSBtoRGB((1.0f - Math.max(0.0f, (maxHealth - (float)hp) / maxHealth)) / 3.0f, 1.0f, 1.0f);
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(3.0f, 3.0f, 1.0f);
                    Draw.rect(-1.0f, (float)(8.0 - 8.0 * part), 1.0f, (float)(8.0 * part), color);
                    GlStateManager.popMatrix();
                    health(hp, 11.0f, 26.0f, shadow);
                } else {
                    Draw.text(healthColor, "N/A", 3.0f, 26.0f, shadow);
                }
                GlStateManager.popMatrix();
                y += 36.0f;
            }
            return y;
        }

        private void head(Mate mate) {
            GlStateManager.pushMatrix();
            GlStateManager.scale(3.0f, 3.0f, 1.0f);
            ResourceLocation skin = mate.skin();
            Draw.blit(skin, 0.0f, 0.0f, 8.0f, 8.0f, 8.0f, 8.0f, 64.0f, 64.0f, -1);
            EntityPlayer p = mate.player();
            if (p != null && p.isWearing(EnumPlayerModelParts.HAT)) Draw.blit(skin, 0.0f, 0.0f, 40.0f, 8.0f, 8.0f, 8.0f, 64.0f, 64.0f, -1);
            GlStateManager.popMatrix();
        }

        private void health(int hp, float x, float y, boolean shadow) {
            int color = healthColor(hp);
            boolean heart = showHearts.on();
            String s = heart ? String.valueOf((double)(int)(hp / 2.0 * 10.0) / 10.0) : String.valueOf(hp);
            float w = Draw.width(s);
            if (displayMode.get() == DisplayMode.TEXT) {
                Draw.text(s, x, y, color, shadow);
                if (heart) Draw.text("❤", x + w + 2.0f, y, -58854, shadow);
                else Draw.text("HP", x + 16.0f, y, color, shadow);
            }
            if (displayMode.get() == DisplayMode.MINIMAL) {
                if (!heart) { s = s + "/" + maxHealth; w = Draw.width(s); }
                else w += Draw.width("❤");
                Draw.text(s, x - w / 2.0f, y, color, shadow);
                x += Draw.width(s) - w / 2.0f;
                if (heart) Draw.text("❤", x - 10.0f, y, -58854, shadow);
            }
        }
    }
}
