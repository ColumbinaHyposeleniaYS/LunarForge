package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.Block;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockMobSpawner;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

public final class ModuleLightOverlay extends Module {
    private static final ResourceLocation FONT = new ResourceLocation("textures/font/ascii.png");

    public enum Mode implements ChoiceSetting.Option {
        OVERLAY("overlay"), CROSS("cross"), NONE("none");
        final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final NumberSetting renderRangeLimit = integer("renderRangeLimit", 2, 1, 12);
    private final KeySetting toggleKeybind = keyCombo("toggleKeybind");
    private final BoolSetting fastUpdates = bool("fastUpdates", false);
    private final BoolSetting culling = bool("culling", true);
    private final BoolSetting includeSkyLight = bool("includeSkyLight", true);
    private final BoolSetting hideUnspawnableLight = bool("hideUnspawnableLight", false);
    private final BoolSetting customLightThreshold = bool("customLightThreshold", false);
    private final NumberSetting threshold = integer("threshold", 15, 0, 15);
    private final ChoiceSetting<Mode> lightOverlayMode = choice("lightOverlayMode", Mode.CROSS);
    private final BoolSetting showLightValue = bool("showLightValue", false);
    private final NumberSetting crossThickness = decimal("crossThickness", 2.0f, 0.5f, 10.0f);
    private final BoolSetting lightOverlayDynamicColor = bool("lightOverlayDynamicColor", false);
    private final ColorSetting brightColor = color("brightColor", -16711936);
    private final ColorSetting darkColor = color("darkColor", -65536);
    private final ColorSetting textColor = color("textColor", -1);

    private final BoolSetting shown = bool("enabledToggle", true).noWidget();

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Light Overlay Executor"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean scanning = new AtomicBoolean(), filtering = new AtomicBoolean();
    private volatile boolean needsUpdate = true;
    private long lastUpdate = -1L;
    private int skylightSubtract;
    private boolean keyWasDown;
    private long ticks;
    private Data data;

    public ModuleLightOverlay() {
        super("LIGHT_OVERLAY", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(toggleKeybind);
            s.add(renderRangeLimit, fastUpdates, culling, includeSkyLight);
            s.group(hideUnspawnableLight, h -> h.group(customLightThreshold, c -> c.add(threshold)));
        });
        page.section("renderOptions", s -> {
            s.add(lightOverlayMode, showLightValue);
            s.add(crossThickness).hideIf(() -> lightOverlayMode.get() != Mode.CROSS);
            s.add(textColor).hideIf(() -> !showLightValue.on() || lightOverlayMode.get() == Mode.NONE);
            s.add(brightColor, darkColor, lightOverlayDynamicColor);
        });
    }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }

    private void reset() {
        data = null;
        needsUpdate = true;
        if (mc().renderGlobal != null && mc().theWorld != null) mc().renderGlobal.loadRenderers();
    }

    private static int sky(int sky, int subtract) { return subtract == -1 ? 0 : sky - subtract; }

    private static int light(int block, int sky, int subtract) { return Math.max(block, sky(sky, subtract)); }

    private static boolean dark(int block, int sky, int subtract) { return light(block, sky, subtract) <= 7; }

    private int colour(int block, int sky, int subtract, float position) {
        if (!lightOverlayDynamicColor.on()) return (dark(block, sky, subtract) ? darkColor : brightColor).color(position);
        int a = darkColor.color(position), b = brightColor.color(position);
        float t = light(block, sky, subtract) / 15.0f;
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 255, cb = b >>> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    private static boolean spawnable(World world, BlockPos pos, Block block) {
        if (block instanceof BlockMobSpawner || block instanceof BlockGlass || block instanceof BlockStainedGlass) return false;
        return SpawnerAnimals.canCreatureTypeSpawnAtLocation(EntityLiving.SpawnPlacementType.ON_GROUND, world, pos.up());
    }

    private static Field renderInfos, renderChunk;

    private static List<BlockPos> renderChunks(RenderGlobal rg) {
        List<BlockPos> out = new ArrayList<BlockPos>();
        try {
            if (renderInfos == null) renderInfos = ReflectionHelper.findField(RenderGlobal.class, "renderInfos", "field_72755_R");
            for (Object info : (List<?>)renderInfos.get(rg)) {
                if (renderChunk == null) renderChunk = ReflectionHelper.findField(info.getClass(), "renderChunk", "field_178036_a");
                RenderChunk chunk = (RenderChunk)renderChunk.get(info);
                if (chunk != null) out.add(chunk.getPosition());
            }
        } catch (Exception ignored) {}
        return out;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        boolean down = isEnabled() && mc().currentScreen == null && toggleKeybind.isDown();
        if (down && !keyWasDown) shown.set(!shown.on());
        keyWasDown = down;
        if (!isEnabled()) return;
        final Minecraft mc = mc();
        final EntityPlayer player = mc.thePlayer;
        final WorldClient world = mc.theWorld;
        RenderGlobal rg = mc.renderGlobal;
        if (player == null || world == null || rg == null) {
            data = null;
            needsUpdate = true;
            return;
        }
        if (!shown.on()) return;
        if (data == null) data = new Data();
        final Data d = data;
        skylightSubtract = !includeSkyLight.on() ? -1
            : player.dimension == 0 ? skylightSubtract(world, Math.abs(world.getWorldTime())) : 0;
        if ((fastUpdates.on() || lastUpdate == -1L || System.currentTimeMillis() - lastUpdate >= 500L) && needsUpdate
                && scanning.compareAndSet(false, true)) {
            needsUpdate = false;
            final List<BlockPos> chunks = renderChunks(rg);
            executor.execute(() -> {
                try { d.scan(world, chunks); } finally { scanning.set(false); }
            });
            lastUpdate = System.currentTimeMillis();
        }
        if ((fastUpdates.on() || ticks % 2 == 0) && filtering.compareAndSet(false, true)) {
            final int px = MathHelper.floor_double(player.posX), py = MathHelper.floor_double(player.posY), pz = MathHelper.floor_double(player.posZ);
            final boolean hide = hideUnspawnableLight.on(), custom = customLightThreshold.on();
            final int limit = threshold.intValue(), range = renderRangeLimit.intValue(), subtract = skylightSubtract;
            final Frustum frustum = culling.on() ? frustum(mc) : null;
            executor.execute(() -> {
                try { d.filter(px, py, pz, world, frustum, hide, custom, limit, range, subtract); } finally { filtering.set(false); }
            });
        }
    }

    private static Frustum frustum(Minecraft mc) {
        Entity view = mc.getRenderViewEntity();
        Frustum f = new Frustum();
        if (view != null) f.setPosition(view.posX, view.posY, view.posZ);
        return f;
    }

    private static int skylightSubtract(World world, long time) {
        float angle = world.provider.calculateCelestialAngle(time, 1.0F);
        float f = 1.0F - (MathHelper.cos(angle * (float)Math.PI * 2.0F) * 2.0F + 0.5F);
        f = 1.0F - Math.max(0.0F, Math.min(1.0F, f));
        f = (float)(f * (1.0 - world.getRainStrength(1.0F) * 5.0F / 16.0));
        f = (float)(f * (1.0 - world.getThunderStrength(1.0F) * 5.0F / 16.0));
        f = 1.0F - f;
        return (int)(f * 11.0F);
    }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) {
        if (!(event.world instanceof WorldClient)) return;
        data = null;
        needsUpdate = true;
        event.world.addWorldAccess(new Watcher());
    }

    private final class Watcher implements IWorldAccess {
        @Override public void markBlockForUpdate(BlockPos pos) { needsUpdate = true; }
        @Override public void notifyLightSet(BlockPos pos) { needsUpdate = true; }
        @Override public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) { needsUpdate = true; }
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

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        Data d = data;
        if (!isEnabled() || d == null || !shown.on()) return;
        RenderManager rm = mc().getRenderManager();
        if (rm != null) d.render(rm);
    }

    private final class Data {
        private volatile Scanned scanned = new Scanned(new int[0], new int[0], new int[0], new byte[0]);

        private volatile Filtered filtered = new Filtered(null, new int[0]);

        void scan(World world, List<BlockPos> chunks) {
            int n = 0;
            int[] xs = new int[4096], ys = new int[4096], zs = new int[4096];
            byte[] light = new byte[4096];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (BlockPos origin : chunks) {
                Chunk chunk = world.getChunkFromBlockCoords(origin);
                for (int i = 0; i < 4096; ++i) {
                    int dx = i >> 8 & 15, dy = i >> 4 & 15, dz = i & 15;
                    int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
                    pos.set(x, y, z);
                    Block block = chunk.getBlock(pos);
                    if (!spawnable(world, pos, block)) continue;
                    BlockPos spot = new BlockPos(x, y + 1, z);
                    int sky = chunk.getLightFor(EnumSkyBlock.SKY, spot), blockLight = chunk.getLightFor(EnumSkyBlock.BLOCK, spot);
                    if (n == xs.length) {
                        xs = java.util.Arrays.copyOf(xs, n * 3 / 2); ys = java.util.Arrays.copyOf(ys, n * 3 / 2);
                        zs = java.util.Arrays.copyOf(zs, n * 3 / 2); light = java.util.Arrays.copyOf(light, n * 3 / 2);
                    }
                    xs[n] = x; ys[n] = y; zs[n] = z;
                    light[n] = (byte)((blockLight & 15) << 4 | sky & 15);
                    n++;
                }
            }
            scanned = new Scanned(java.util.Arrays.copyOf(xs, n), java.util.Arrays.copyOf(ys, n), java.util.Arrays.copyOf(zs, n), java.util.Arrays.copyOf(light, n));
        }

        void filter(int px, int py, int pz, World world, Frustum frustum, boolean hide, boolean custom, int limit, int range, int subtract) {
            Scanned s = scanned;
            int[] keep = new int[Math.max(16, s.x.length)];
            int n = 0;
            int horizontal = (int)Math.pow(range * 8, 2.0), vertical = (int)Math.pow(range * 5, 2.0);
            for (int i = 0; i < s.x.length; ++i) {
                int dx = s.x[i] - px, dy = s.y[i] + 1 - py, dz = s.z[i] - pz;
                if (dy * dy > vertical || dx * dx + dz * dz > horizontal) continue;
                int block = s.light[i] >> 4 & 15, sky = s.light[i] & 15;
                if (hide && (custom && light(block, sky, subtract) > limit || !custom && !dark(block, sky, subtract))) continue;
                if (frustum != null && !frustum.isBoundingBoxInFrustum(new AxisAlignedBB(s.x[i], s.y[i], s.z[i], s.x[i] + 1, s.y[i] + 1, s.z[i] + 1))) continue;
                Block b = world.getBlockState(new BlockPos(s.x[i], s.y[i], s.z[i])).getBlock();
                if (b.getMaterial() == Material.air) continue;
                keep[n++] = i;
            }
            filtered = new Filtered(s, java.util.Arrays.copyOf(keep, n));
        }

        void render(RenderManager rm) {
            Filtered f = filtered;
            if (f.scan == null || f.index.length == 0) return;
            double vx = rm.viewerPosX, vy = rm.viewerPosY, vz = rm.viewerPosZ;
            Mode mode = lightOverlayMode.get();
            int subtract = skylightSubtract;
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            if (mode == Mode.CROSS) {
                GlStateManager.disableTexture2D();
                GL11.glLineWidth(crossThickness.value());
                wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
                for (int i : f.index) {
                    double x = f.scan.x[i] - vx, y = f.scan.y[i] + 1.01 - vy, z = f.scan.z[i] - vz;
                    int c = colour(f.scan.light[i] >> 4 & 15, f.scan.light[i] & 15, subtract, (float)(x + z));
                    line(wr, x + 0.25, y, z + 0.25, x + 0.75, y, z + 0.75, c);
                    line(wr, x + 0.75, y, z + 0.25, x + 0.25, y, z + 0.75, c);
                }
                tess.draw();
                GL11.glLineWidth(1.0f);
                GlStateManager.enableTexture2D();
            } else if (mode == Mode.OVERLAY) {
                GlStateManager.disableTexture2D();
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                for (int i : f.index) {
                    double x = f.scan.x[i] - vx, y = f.scan.y[i] + 1.01 - vy, z = f.scan.z[i] - vz;
                    int c = colour(f.scan.light[i] >> 4 & 15, f.scan.light[i] & 15, subtract, (float)(x + z));
                    float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f, a = (c >>> 24) / 255f * 0.66f;
                    wr.pos(x, y, z).color(r, g, b, a).endVertex();
                    wr.pos(x, y, z + 1.0).color(r, g, b, a).endVertex();
                    wr.pos(x + 1.0, y, z + 1.0).color(r, g, b, a).endVertex();
                    wr.pos(x + 1.0, y, z).color(r, g, b, a).endVertex();
                }
                tess.draw();
                GlStateManager.enableTexture2D();
            }
            if (showLightValue.on()) {
                mc().getTextureManager().bindTexture(FONT);
                double yaw = Math.toRadians(rm.playerViewY + 180.0);
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                for (int i : f.index) {
                    double x = f.scan.x[i] - vx, y = f.scan.y[i] + 1.01 - vy, z = f.scan.z[i] - vz;
                    int block = f.scan.light[i] >> 4 & 15, sky = f.scan.light[i] & 15;
                    int c = mode == Mode.NONE ? colour(block, sky, subtract, (float)(x + z)) : textColor.color((float)(x + z));
                    int value = light(block, sky, subtract);
                    if (value > 9) {
                        digit(wr, x, y, z, 1, -0.38, c, yaw);
                        digit(wr, x, y, z, value - 10, 0.38, c, yaw);
                    } else {
                        digit(wr, x, y, z, value, 0.0, c, yaw);
                    }
                }
                tess.draw();
            }
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        private void line(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1, int c) {
            float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f, a = (c >>> 24) / 255f;
            wr.pos(x0, y0, z0).color(r, g, b, a).endVertex();
            wr.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        }

        private void digit(WorldRenderer wr, double x, double y, double z, int n, double offset, int c, double yaw) {
            float u0 = n * 8.0f / 128.0f, v0 = 0.1875f, u1 = u0 + 0.0546875f, v1 = 0.2421875f;
            corner(wr, 0.0 + offset, 0.0, 0.0, u0, v0, c, x, y, z, yaw);
            corner(wr, 0.0 + offset, 0.0, 1.0, u0, v1, c, x, y, z, yaw);
            corner(wr, 1.0 + offset, 0.0, 1.0, u1, v1, c, x, y, z, yaw);
            corner(wr, 1.0 + offset, 0.0, 0.0, u1, v0, c, x, y, z, yaw);
        }

        private void corner(WorldRenderer wr, double dx, double dy, double dz, float u, float v, int c, double x, double y, double z, double yaw) {
            dx *= 0.5;
            dz *= 0.5;
            dx -= 0.18;
            dz -= 0.25;
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            double rx = dx * cos - dz * sin, rz = dx * sin + dz * cos;
            wr.pos(rx + x + 0.5, dy + y, rz + z + 0.5).tex(u, v)
                .color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f).endVertex();
        }
    }

    private static final class Scanned {
        final int[] x, y, z; final byte[] light;
        Scanned(int[] x, int[] y, int[] z, byte[] light) { this.x = x; this.y = y; this.z = z; this.light = light; }
    }

    private static final class Filtered {
        final Scanned scan; final int[] index;
        Filtered(Scanned scan, int[] index) { this.scan = scan; this.index = index; }
    }
}
