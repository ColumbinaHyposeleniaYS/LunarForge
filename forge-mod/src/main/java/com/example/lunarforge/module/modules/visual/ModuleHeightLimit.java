package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.hud.RowHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.text.WordUtils;
import org.lwjgl.opengl.GL11;

public final class ModuleHeightLimit extends Module {
    private static final String[] BEDWARS_BLOCKS = {"planks", "log", "end_stone", "glass", "obsidian", "wool", "ladder"};

    public enum OverlayMode implements ChoiceSetting.Option {
        DARKEN("darken"), BARRIER("barrier"), BOTH("both");
        final String id;
        OverlayMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum Style implements ChoiceSetting.Option {
        CLASSIC, COMPACT;
        @Override public String langId() { return name(); }
    }

    enum Filter {
        ALL, BRIDGE, BEDWARS;

        boolean matches(Block block) {
            if (this == ALL) return true;
            Object name = Block.blockRegistry.getNameForObject(block);
            if (name == null) return true;
            String path = ((ResourceLocation)name).getResourcePath();
            if (this == BRIDGE) return path.contains("clay") || path.contains("terracotta");
            for (String s : BEDWARS_BLOCKS) if (path.contains(s)) return true;
            return false;
        }
    }

    enum Source {
        BEDWARS("HEIGHT_LIMIT_BEDWARS", Filter.BEDWARS), BRIDGE("HEIGHT_LIMIT_BRIDGE", Filter.BRIDGE),
        VANILLA("HEIGHT_LIMIT_VANILLA", Filter.ALL), SERVER("HEIGHT_LIMIT_SERVER", Filter.ALL);
        final String id; final Filter filter;
        Source(String id, Filter filter) { this.id = id; this.filter = filter; }
    }

    final class Profile extends Module {
        final Source source;
        final BoolSetting showOverlay = bool("showOverlay", true);
        final ChoiceSetting<OverlayMode> overlayMode = choice("overlayMode", OverlayMode.DARKEN);
        final NumberSetting gradientHeight = integer("gradientHeight", 0, 0, 16);
        final ColorSetting darkenColor = color("darkenColor", -1342177280).noChroma();
        final ColorSetting barrierColor = color("barrierColor", -65536).noChroma();
        final BoolSetting topFaceOnly = bool("topFaceOnly", false);
        final BoolSetting showHud = bool("showHud", true);
        final ChoiceSetting<Style> style = choice("style", Style.CLASSIC);
        final BoolSetting showTitle = bool("showTitle", true);
        final BoolSetting mapName = bool("mapName", true);
        final BoolSetting heightLimit = bool("heightLimit", true);
        final BoolSetting currentHeight = bool("currentHeight", false);
        final BoolSetting distanceToHeightLimit = bool("distanceToHeightLimit", true);

        Profile(Source source) {
            super(source.id, true);
            this.source = source;
            Runnable dirty = overlay::markDirty;
            showOverlay.onChange(dirty);
            overlayMode.onChange(dirty);
            gradientHeight.onChange(dirty);
            topFaceOnly.onChange(dirty);
        }

        @Override protected void layout(Page page) {
            page.section("blockOverlayOptions", s -> s.group(showOverlay, c -> {
                c.add(overlayMode);
                c.add(gradientHeight, darkenColor).hideIf(() -> overlayMode.get() == OverlayMode.BARRIER);
                c.add(barrierColor).hideIf(() -> overlayMode.get() == OverlayMode.DARKEN);
                c.add(topFaceOnly);
            }));
            page.section("hudDisplayOptions", s -> s.group(showHud, c -> {
                c.add(style);
                c.add(showTitle).hideIf(() -> style.get() == Style.COMPACT);
                if (source != Source.VANILLA) c.add(mapName);
                c.group(heightLimit, h -> h.add(currentHeight));
                c.add(distanceToHeightLimit);
            }));
        }

        @Override protected void onEnable() { changed(); }
        @Override protected void onDisable() { changed(); }

        private void changed() {
            if (source == Source.BEDWARS || source == Source.BRIDGE) updateLocation();
            overlay.markDirty();
        }

        int gradient() { return overlayMode.get() != OverlayMode.BARRIER ? gradientHeight.intValue() : 0; }

        int[] gradientColors(int n) {
            int[] out = new int[n + 1];
            int c = darkenColor.argb();
            int alpha = c >>> 24 & 255;
            for (int i = 0; i <= n; ++i) {
                float f = n <= 0 ? 1.0f : 1.0f - (float)i / ((float)n + 1.0f);
                out[i] = Math.round(alpha * f) << 24 | c & 0xFFFFFF;
            }
            return out;
        }
    }

    private static final class Limit {
        final Profile profile; final int limit;
        Limit(Profile profile, int limit) { this.profile = profile; this.limit = limit; }
    }

    private final NumberSetting renderRange = integer("renderRange", 4, 1, 8);
    private final BoolSetting heightLimitOverlay = bool("heightLimitOverlay", true);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting autoAlign = bool("autoAlign", true);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ChoiceSetting<RowHud.Alignment> alignment = choice("alignment", RowHud.Alignment.LEFT);
    private final BoolSetting autoColorDistance = bool("autoColorDistance", true);
    private final ColorSetting titleColor = color("titleColor", -171);
    private final ColorSetting textColor = color("textColor", -1);
    private final ColorSetting numberColor = color("numberColor", -11141291);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ColorSetting red = color("red", -43691);
    private final ColorSetting gold = color("gold", -22016);
    private final ColorSetting yellow = color("yellow", -22016);

    private final Overlay overlay = new Overlay();
    private final Profile bedwars = child(new Profile(Source.BEDWARS), null);
    private final Profile bridge = child(new Profile(Source.BRIDGE), null);
    private final Profile vanilla = child(new Profile(Source.VANILLA), null);
    private final Profile server = child(new Profile(Source.SERVER), null);

    private JsonObject bedwarsHeights, duelsHeights;
    private boolean loaded;

    private int locationLimit = -1;
    private Profile locationProfile;
    private String mapName;

    private Integer serverLimit;
    private String serverName;

    public ModuleHeightLimit() {
        super("HEIGHT_LIMIT", false);
        hud(new Hud());
        renderRange.onChange(overlay::markDirty);
        HypixelLocation.listen((before, now) -> { updateLocation(); overlay.markDirty(); });
    }

    @Override protected void layout(Page page) {
        page.add(renderRange);
        page.section("hudDisplayOptions", s -> s.group(heightLimitOverlay, c -> {
            c.add(textShadow);
            c.group(background, b -> b.group(border, t -> t.add(borderThickness)));
            c.add(autoAlign);
            c.add(alignment).hideIf(autoAlign::on);
        }));
        page.section("extraRenderOptions", s -> s.add(autoColorDistance).hideIf(() -> !heightLimitOverlay.on()));
        page.section("colorOptions", s -> {
            s.add(titleColor, textColor, numberColor).hideIf(() -> !heightLimitOverlay.on());
            s.add(backgroundColor).hideIf(() -> !background.on() || !heightLimitOverlay.on());
            s.add(borderColor).hideIf(() -> !border.on() || !heightLimitOverlay.on());
        });
    }

    @Override protected void onEnable() {
        if (!loaded) {
            loaded = true;
            bedwarsHeights = heights("/assets/lunarforge/hypixel/bedwars.json");
            duelsHeights = heights("/assets/lunarforge/hypixel/duels.json");
        }
        updateLocation();
        overlay.markDirty();
    }

    @Override protected void onDisable() { overlay.clear(); }

    private static JsonObject heights(String resource) {
        try (java.io.InputStream in = ModuleHeightLimit.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("build_heights");
        } catch (Exception e) {
            return null;
        }
    }

    private void updateLocation() {
        if (!loaded) {
            loaded = true;
            bedwarsHeights = heights("/assets/lunarforge/hypixel/bedwars.json");
            duelsHeights = heights("/assets/lunarforge/hypixel/duels.json");
        }
        HypixelLocation.Location loc = HypixelLocation.get();
        mapName = null;
        locationProfile = null;
        locationLimit = -1;
        if (loc == null || loc.empty() || !Server.hypixel()) return;
        String map = loc.map == null ? null : loc.map.toLowerCase(Locale.ROOT).replace(" ", "_");
        if (bedwars.isEnabled() && "BEDWARS".equalsIgnoreCase(loc.gametype) && map != null && bedwarsHeights != null && bedwarsHeights.has(map)) {
            mapName = WordUtils.capitalizeFully(map.replace("_", " "));
            locationProfile = bedwars;
            locationLimit = bedwarsHeights.get(map).getAsInt();
            return;
        }
        if (bridge.isEnabled() && "DUELS".equalsIgnoreCase(loc.gametype) && loc.mode != null && loc.mode.toUpperCase(Locale.ROOT).contains("BRIDGE")) {
            if (map != null) mapName = WordUtils.capitalizeFully(map.replace("_", " "));
            locationProfile = bridge;
            locationLimit = map != null && duelsHeights != null && duelsHeights.has(map) ? duelsHeights.get(map).getAsInt() : 100;
        }
    }

    private Limit limit() {
        if (server.isEnabled() && serverLimit != null) return new Limit(server, serverLimit);
        if (locationLimit > 0 && locationProfile != null) return new Limit(locationProfile, locationLimit);
        if (vanilla.isEnabled()) {
            World world = Minecraft.getMinecraft().theWorld;
            if (world != null) {
                int n = world.getHeight();
                if (n > 0) return new Limit(vanilla, n);
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (isEnabled()) overlay.render(this);
    }

    @SubscribeEvent
    public void onChunk(ChunkEvent.Load event) {
        if (event.world.isRemote) overlay.chunkLoaded(event.getChunk().xPosition, event.getChunk().zPosition, renderRange.intValue() * 8);
    }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) {
        if (!(event.world instanceof WorldClient)) return;
        event.world.addWorldAccess(new BlockWatcher());
        overlay.markDirty();
    }

    private void blockChanged(BlockPos pos) {
        if (!isEnabled()) return;
        Limit l = limit();
        if (l == null) return;
        int y = pos.getY(), top = l.limit - 1;
        if (y >= top - l.profile.gradient() - 1 && y <= top + 1) overlay.markDirty();
    }

    private final class BlockWatcher implements IWorldAccess {
        @Override public void markBlockForUpdate(BlockPos pos) { blockChanged(pos); }
        @Override public void notifyLightSet(BlockPos pos) {}
        @Override public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}
        @Override public void playSound(String soundName, double x, double y, double z, float volume, float pitch) {}
        @Override public void playSoundToNearExcept(EntityPlayer except, String soundName, double x, double y, double z, float volume, float pitch) {}
        @Override public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xOffset, double yOffset, double zOffset, int... parameters) {}
        @Override public void onEntityAdded(Entity entityIn) {}
        @Override public void onEntityRemoved(Entity entityIn) {}
        @Override public void playRecord(String recordName, BlockPos blockPosIn) {}
        @Override public void broadcastSound(int soundID, BlockPos pos, int data) {}
        @Override public void playAuxSFX(EntityPlayer player, int sfxType, BlockPos blockPosIn, int data) {}
        @Override public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {}
    }

    private final class Hud extends RowHud {
        Hud() { super(ModuleHeightLimit.this, 0.0f, 0.0f, HudAnchor.TOP_RIGHT); }

        @Override protected boolean autoAlign() { return autoAlign.on(); }
        @Override protected Alignment alignment() { return alignment.get(); }
        @Override protected boolean background() { return background.on(); }
        @Override protected ColorSetting backgroundColor() { return backgroundColor; }
        @Override protected boolean border() { return border.on(); }
        @Override protected float borderThickness() { return borderThickness.value(); }
        @Override protected ColorSetting borderColor() { return borderColor; }

        @Override public boolean visible(boolean preview) {
            if (!heightLimitOverlay.on()) { size(0, 0); return false; }
            Limit l;
            if (!preview && ((l = limit()) == null || !l.profile.showHud.on())) { size(0, 0); return false; }
            return super.visible(preview);
        }

        @Override protected List<Piece> rows(boolean preview) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return null;
            Limit l = limit();
            Profile profile = l != null ? l.profile : bedwars;
            String name = l != null && l.profile == server && serverLimit != null ? serverName : mapName;
            int limit = l == null ? -1 : l.limit;
            int y = (int)player.getEntityBoundingBox().minY;
            int distance = Math.max(0, limit - y);
            if (preview && name == null) name = "Lighthouse";
            if (preview && limit <= 0) { limit = 110; distance = 27; y = limit - distance; }
            boolean showMap = profile.source != Source.VANILLA && profile.mapName.on() && name != null;
            boolean showLimit = profile.heightLimit.on() && limit > 0;
            boolean showDistance = profile.distanceToHeightLimit.on() && limit > 0;
            if (!showMap && !showLimit && !showDistance) return null;
            ColorSetting distanceColor = numberColor;
            if (autoColorDistance.on()) {
                if (distance <= 5) distanceColor = red;
                else if (distance <= 10) distanceColor = gold;
                else if (distance <= 15) distanceColor = yellow;
            }
            int titlePad = 0, rowPad = 0;
            switch (effective(autoAlign.on(), currentAnchor(), alignment.get())) {
                case LEFT: rowPad = 4; break;
                case RIGHT: titlePad = 4; break;
                default: break;
            }
            boolean shadow = textShadow.on();
            List<Piece> rows = new ArrayList<Piece>();
            if (profile.style.get() == Style.CLASSIC) {
                if (profile.showTitle.on()) rows.add(row(titlePad, text("§lHeight Limit", titleColor, shadow)));
                if (showMap) rows.add(row(rowPad, text("Map: ", textColor, shadow), text(name, numberColor, shadow)));
                if (showLimit) {
                    List<Piece> pieces = new ArrayList<Piece>();
                    pieces.add(text("Height Limit: ", textColor, shadow));
                    pieces.addAll(limitPieces(profile, y, limit));
                    rows.add(row(rowPad, pieces));
                }
                if (showDistance) rows.add(row(rowPad, text("Distance: ", textColor, shadow), text("" + distance, distanceColor, shadow)));
            } else {
                if (showMap) rows.add(row(rowPad, text("§l" + name, titleColor, shadow)));
                if (showLimit && showDistance) {
                    List<Piece> pieces = new ArrayList<Piece>();
                    pieces.add(text("Y: ", textColor, shadow));
                    pieces.addAll(limitPieces(profile, y, limit));
                    pieces.add(text(" §7(§r" + distance + "§7)", distanceColor, shadow));
                    rows.add(row(titlePad, pieces));
                } else if (showLimit) {
                    List<Piece> pieces = new ArrayList<Piece>();
                    pieces.add(text("Y: ", textColor, shadow));
                    pieces.addAll(limitPieces(profile, y, limit));
                    rows.add(row(titlePad, pieces));
                } else {
                    rows.add(row(titlePad, text("Dist: ", textColor, shadow), text("" + distance, distanceColor, shadow)));
                }
            }
            return rows;
        }
    }

    private List<RowHud.Piece> limitPieces(Profile profile, int y, int limit) {
        boolean shadow = textShadow.on();
        List<RowHud.Piece> out = new ArrayList<RowHud.Piece>();
        if (profile.currentHeight.on()) {
            out.add(RowHud.text("" + y, numberColor, shadow));
            out.add(RowHud.text(" / ", textColor, shadow));
        }
        out.add(RowHud.text("" + limit, numberColor, shadow));
        return out;
    }

    private static final ResourceLocation BARRIER = new ResourceLocation("textures/items/barrier.png");

    private final class Overlay {
        private volatile boolean dirty = true;
        private volatile Scan scan = Scan.EMPTY;
        private final AtomicBoolean scanning = new AtomicBoolean();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Height Limit Executor"); t.setDaemon(true); return t;
        });
        private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE, scannedLimit = -1;
        private int[] gradientColors;
        private int gradientColor, gradientSize = -1;

        void markDirty() { dirty = true; }

        void clear() { scan = Scan.EMPTY; dirty = true; }

        void chunkLoaded(int chunkX, int chunkZ, int range) {
            if (centerX == Integer.MIN_VALUE) return;
            int x = chunkX << 4, z = chunkZ << 4;
            int dx = Math.max(centerX - (x + 15), x - centerX), dz = Math.max(centerZ - (z + 15), z - centerZ);
            if (Math.max(dx, dz) <= range + 8) dirty = true;
        }

        void render(ModuleHeightLimit module) {
            Limit l = module.limit();
            if (l == null || !l.profile.showOverlay.on()) return;
            Profile profile = l.profile;
            int limit = l.limit;
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer player = mc.thePlayer;
            World world = mc.theWorld;
            RenderManager rm = mc.getRenderManager();
            if (player == null || world == null || rm == null) return;
            int px = MathHelper.floor_double(player.posX), pz = MathHelper.floor_double(player.posZ);
            boolean moved = centerX == Integer.MIN_VALUE || Math.max(Math.abs(px - centerX), Math.abs(pz - centerZ)) >= 8;
            if ((dirty || moved || limit != scannedLimit) && scanning.compareAndSet(false, true)) {
                dirty = false;
                centerX = px; centerZ = pz; scannedLimit = limit;
                final Request request = new Request(world, px, pz, limit, profile.gradient(), module.renderRange.intValue() * 8,
                    profile.topFaceOnly.on(), profile.source.filter);
                executor.execute(() -> {
                    try { scan = request.run(); }
                    catch (Throwable t) { dirty = true; }
                    finally { scanning.set(false); }
                });
            }
            Scan s = scan;
            int count = s.x.length;
            if (count == 0 || s.world != world) return;
            double vx = rm.viewerPosX, vy = rm.viewerPosY, vz = rm.viewerPosZ;
            int top = s.limit - 1;
            OverlayMode mode = profile.overlayMode.get();
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            if (mode != OverlayMode.BARRIER) {
                int[] colors = gradient(profile);
                GlStateManager.disableTexture2D();
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i < count; ++i) {
                    int by = s.y[i];
                    int layer = top - by;
                    if (layer < 0 || layer >= colors.length) continue;
                    int c = colors[layer];
                    if ((c >>> 24 & 255) == 0) continue;
                    faces(wr, s.x[i] - vx, by - vy, s.z[i] - vz, s.faces[i], (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f,
                        (c & 255) / 255f, (c >>> 24) / 255f, false);
                }
                tess.draw();
                GlStateManager.enableTexture2D();
            }
            if (mode != OverlayMode.DARKEN) {
                int c = profile.barrierColor.argb();
                if ((c >>> 24 & 255) != 0) {
                    mc.getTextureManager().bindTexture(BARRIER);
                    wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                    for (int i = 0; i < count; ++i) {
                        if (s.y[i] != top) continue;
                        faces(wr, s.x[i] - vx, top - vy, s.z[i] - vz, s.faces[i], (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f,
                            (c & 255) / 255f, (c >>> 24) / 255f, true);
                    }
                    tess.draw();
                }
            }
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        private int[] gradient(Profile profile) {
            int c = profile.darkenColor.argb(), n = profile.gradient();
            if (gradientColors == null || c != gradientColor || n != gradientSize) {
                gradientColors = profile.gradientColors(n);
                gradientColor = c;
                gradientSize = n;
            }
            return gradientColors;
        }

        private void faces(WorldRenderer wr, double x, double y, double z, byte mask, float r, float g, float b, float a, boolean tex) {
            double x0 = x - 0.005, x1 = x + 1.005, y0 = y - 0.005, y1 = y + 1.005, z0 = z - 0.005, z1 = z + 1.005;
            if ((mask & 2) != 0) { v(wr, x0, y1, z0, 0, 0, r, g, b, a, tex); v(wr, x0, y1, z1, 0, 1, r, g, b, a, tex); v(wr, x1, y1, z1, 1, 1, r, g, b, a, tex); v(wr, x1, y1, z0, 1, 0, r, g, b, a, tex); }
            if ((mask & 1) != 0) { v(wr, x0, y0, z0, 0, 0, r, g, b, a, tex); v(wr, x1, y0, z0, 1, 0, r, g, b, a, tex); v(wr, x1, y0, z1, 1, 1, r, g, b, a, tex); v(wr, x0, y0, z1, 0, 1, r, g, b, a, tex); }
            if ((mask & 4) != 0) { v(wr, x0, y0, z0, 0, 1, r, g, b, a, tex); v(wr, x0, y1, z0, 0, 0, r, g, b, a, tex); v(wr, x1, y1, z0, 1, 0, r, g, b, a, tex); v(wr, x1, y0, z0, 1, 1, r, g, b, a, tex); }
            if ((mask & 8) != 0) { v(wr, x1, y0, z1, 0, 1, r, g, b, a, tex); v(wr, x1, y1, z1, 0, 0, r, g, b, a, tex); v(wr, x0, y1, z1, 1, 0, r, g, b, a, tex); v(wr, x0, y0, z1, 1, 1, r, g, b, a, tex); }
            if ((mask & 0x10) != 0) { v(wr, x0, y0, z1, 0, 1, r, g, b, a, tex); v(wr, x0, y1, z1, 0, 0, r, g, b, a, tex); v(wr, x0, y1, z0, 1, 0, r, g, b, a, tex); v(wr, x0, y0, z0, 1, 1, r, g, b, a, tex); }
            if ((mask & 0x20) != 0) { v(wr, x1, y0, z0, 0, 1, r, g, b, a, tex); v(wr, x1, y1, z0, 0, 0, r, g, b, a, tex); v(wr, x1, y1, z1, 1, 0, r, g, b, a, tex); v(wr, x1, y0, z1, 1, 1, r, g, b, a, tex); }
        }

        private void v(WorldRenderer wr, double x, double y, double z, float u, float v, float r, float g, float b, float a, boolean tex) {
            wr.pos(x, y, z);
            if (tex) wr.tex(u, v);
            wr.color(r, g, b, a).endVertex();
        }
    }

    private static final class Request {
        final World world; final int x, z, limit, gradient, range; final boolean topOnly; final Filter filter;
        private Chunk chunk;
        private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;

        Request(World world, int x, int z, int limit, int gradient, int range, boolean topOnly, Filter filter) {
            this.world = world; this.x = x; this.z = z; this.limit = limit; this.gradient = gradient; this.range = range;
            this.topOnly = topOnly; this.filter = filter;
        }

        private Block block(int bx, int by, int bz) {
            int cx = bx >> 4, cz = bz >> 4;
            if (chunk == null || cx != chunkX || cz != chunkZ) {
                chunk = world.getChunkProvider().chunkExists(cx, cz) ? world.getChunkFromChunkCoords(cx, cz) : null;
                chunkX = cx; chunkZ = cz;
            }
            if (chunk == null || by < 0 || by >= 256) return null;
            return chunk.getBlock(bx & 15, by, bz & 15);
        }

        private boolean open(int bx, int by, int bz) {
            Block b = block(bx, by, bz);
            return b == null || b.getMaterial() == Material.air;
        }

        private int faces(int bx, int by, int bz) {
            if (topOnly) return open(bx, by + 1, bz) ? 2 : 0;
            int n = 0;
            if (open(bx, by + 1, bz)) n |= 2;
            if (open(bx, by - 1, bz)) n |= 1;
            if (open(bx, by, bz - 1)) n |= 4;
            if (open(bx, by, bz + 1)) n |= 8;
            if (open(bx - 1, by, bz)) n |= 0x10;
            if (open(bx + 1, by, bz)) n |= 0x20;
            return n;
        }

        Scan run() {
            int[] xs = new int[1024], ys = new int[1024], zs = new int[1024];
            byte[] fs = new byte[1024];
            int count = 0;
            int top = limit - 1, bottom = top - gradient, r = range + 8;
            outer:
            for (int i = x - r; i <= x + r; ++i) {
                for (int j = z - r; j <= z + r; ++j) {
                    for (int k = bottom; k <= top; ++k) {
                        Block b = block(i, k, j);
                        if (b == null || b.getMaterial() == Material.air || !filter.matches(b)) continue;
                        int f = faces(i, k, j);
                        if (f == 0) continue;
                        if (count == xs.length) {
                            xs = java.util.Arrays.copyOf(xs, count * 2); ys = java.util.Arrays.copyOf(ys, count * 2);
                            zs = java.util.Arrays.copyOf(zs, count * 2); fs = java.util.Arrays.copyOf(fs, count * 2);
                        }
                        xs[count] = i; ys[count] = k; zs[count] = j; fs[count] = (byte)f;
                        if (++count >= 40000) break outer;
                    }
                }
            }
            return new Scan(java.util.Arrays.copyOf(xs, count), java.util.Arrays.copyOf(ys, count), java.util.Arrays.copyOf(zs, count),
                java.util.Arrays.copyOf(fs, count), limit, world);
        }
    }

    private static final class Scan {
        static final Scan EMPTY = new Scan(new int[0], new int[0], new int[0], new byte[0], -1, null);
        final int[] x, y, z; final byte[] faces; final int limit; final World world;
        Scan(int[] x, int[] y, int[] z, byte[] faces, int limit, World world) {
            this.x = x; this.y = y; this.z = z; this.faces = faces; this.limit = limit; this.world = world;
        }
    }
}
