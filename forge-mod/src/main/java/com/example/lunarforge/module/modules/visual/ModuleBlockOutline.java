package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Modules;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.block.BlockBush;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class ModuleBlockOutline extends Module {
    private static ModuleBlockOutline instance;

    public enum OutlineMode implements ChoiceSetting.Option {
        STATIC("outlineModeStatic"), RAINBOW("outlineModeRainbow"), BLEND("outlineModeBlend");
        final String lang;
        OutlineMode(String lang) { this.lang = lang; }
        @Override public String langId() { return lang; }
    }

    public enum OverlayMode implements ChoiceSetting.Option {
        STATIC("outlineModeStatic"), RAINBOW("outlineModeRainbow"), BLEND("outlineModeBlend"),
        INVERTED("outlineModeInverted"), DARKEN("outlineModeDarken");
        final String lang;
        OverlayMode(String lang) { this.lang = lang; }
        @Override public String langId() { return lang; }
    }

    private final BoolSetting blockOutline = bool("blockOutline", true);
    private final NumberSetting blockOutlineWidth = decimal("blockOutlineWidth", 2.0f, 1.0f, 10.0f);
    private final ChoiceSetting<OutlineMode> blockOutlineMode = choice("blockOutlineMode", OutlineMode.STATIC);
    private final ColorSetting blockOutlineColor = color("blockOutlineColor", 1711276032);
    private final ColorSetting blockOutlineColorEnd = color("blockOutlineColorEnd", 1711276032);
    private final BoolSetting blockOutlineInterpolateAlpha = bool("blockOutlineInterpolateAlpha", false);
    private final BoolSetting blockOutlineTraversal = bool("blockOutlineTraversal", false);
    private final NumberSetting blockOutlineTraversalSpeed = decimal("blockOutlineTraversalSpeed", 1.0f, 0.15f, 5.0f);
    private final BoolSetting blockOverlay = bool("blockOverlay", false);
    private final ChoiceSetting<OverlayMode> blockOverlayMode = choice("blockOverlayMode", OverlayMode.STATIC);
    private final ColorSetting blockOverlayColor = color("blockOverlayColor", 436207616);
    private final ColorSetting blockOverlayColorEnd = color("blockOverlayColorEnd", 436207616);
    private final BoolSetting blockOverlayInterpolateAlpha = bool("blockOverlayInterpolateAlpha", false);
    private final BoolSetting blockOverlayTraversal = bool("blockOverlayTraversal", false);
    private final NumberSetting blockOverlayTraversalSpeed = decimal("blockOverlayTraversalSpeed", 1.0f, 0.15f, 5.0f);
    private final BoolSetting blockOutlineSide = bool("blockOutlineSide", false);
    private final BoolSetting blockOutlineShowHiddenFoliage = bool("blockOutlineShowHiddenFoliage", true);

    private int outlineTicks, overlayTicks;
    private float partialTicks;

    public ModuleBlockOutline() {
        super("BLOCK_OUTLINE", false);
        instance = this;

        blockOutlineMode.onChange(() -> {
            if (blockOutlineMode.get() == OutlineMode.RAINBOW) ModuleManager.put(key(), blockOutlineColor.key + "Chroma", "true");
        });
        blockOverlayMode.onChange(() -> {
            if (blockOverlayMode.get() == OverlayMode.RAINBOW) ModuleManager.put(key(), blockOverlayColor.key + "Chroma", "true");
        });
    }

    @Override protected void layout(Page page) {
        page.section("blockOutline", s -> s.group(blockOutline, c -> {
            c.add(blockOutlineWidth, blockOutlineMode, blockOutlineColor);
            c.add(blockOutlineColorEnd).hideIf(() -> blockOutlineMode.get() != OutlineMode.BLEND);
            c.add(blockOutlineInterpolateAlpha);
            c.group(blockOutlineTraversal, t -> t.add(blockOutlineTraversalSpeed))
                .hideIf(() -> blockOutlineMode.get() != OutlineMode.RAINBOW && blockOutlineMode.get() != OutlineMode.BLEND);
        }));
        page.section("blockOverlay", s -> s.group(blockOverlay, c -> {
            c.add(blockOverlayMode);
            c.add(blockOverlayColor).hideIf(() -> blockOverlayMode.get() == OverlayMode.INVERTED || blockOverlayMode.get() == OverlayMode.DARKEN);
            c.add(blockOverlayColorEnd, blockOverlayInterpolateAlpha).hideIf(() -> blockOverlayMode.get() != OverlayMode.BLEND);
            c.group(blockOverlayTraversal, t -> t.add(blockOverlayTraversalSpeed))
                .hideIf(() -> blockOverlayMode.get() != OverlayMode.RAINBOW && blockOverlayMode.get() != OverlayMode.BLEND);
        }));
        page.section("extraRenderOptions", s -> s.add(blockOutlineSide, blockOutlineShowHiddenFoliage)
            .hideIf(() -> !blockOutline.on() && !blockOverlay.on()));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        if (++outlineTicks >= 60.0F * blockOutlineTraversalSpeed.value()) outlineTicks = 0;
        if (++overlayTicks >= 60.0F * blockOverlayTraversalSpeed.value()) overlayTicks = 0;
    }

    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent event) { partialTicks = event.partialTicks; }

    private interface Drawer {
        void render(AxisAlignedBB box, float r, float g, float b, float a, float r2, float g2, float b2, float a2, boolean blend);
    }

    public static boolean draw(AxisAlignedBB box) {
        ModuleBlockOutline m = instance;
        if (m == null || !m.isEnabled()) return false;
        m.render(box);
        return true;
    }

    private void render(AxisAlignedBB box) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!blockOutlineShowHiddenFoliage.on() && Modules.enabledAnd("overlay_mod", "hideFoliage")) {
            MovingObjectPosition hit = mc.objectMouseOver;
            if (hit != null && hit.getBlockPos() != null && mc.theWorld.getBlockState(hit.getBlockPos()).getBlock() instanceof BlockBush) return;
        }
        if (blockOverlay.on()) {
            switch (blockOverlayMode.get()) {
                case STATIC: plain(box, blockOverlayColor, this::overlay); break;
                case RAINBOW: rainbow(box, blockOverlayColor, blockOverlayTraversal.on(), this::overlay); break;
                case BLEND: blend(box, blockOverlayColor, blockOverlayColorEnd, blockOverlayTraversal.on(), overlayTicks,
                    blockOverlayTraversalSpeed.value(), blockOverlayInterpolateAlpha.on(), this::overlay); break;
                case INVERTED:
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_DST_COLOR);
                    overlay(box, 1, 1, 1, 1, 1, 1, 1, 1, false);
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    break;
                case DARKEN:
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_ZERO, GL11.GL_DST_COLOR);
                    GL11.glShadeModel(GL11.GL_SMOOTH);
                    overlay(box, 1, 1, 1, 1, 1, 1, 1, 1, false);
                    GL11.glShadeModel(GL11.GL_FLAT);
                    GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                    break;
            }
        }
        if (blockOutline.on()) {
            switch (blockOutlineMode.get()) {
                case STATIC: plain(box, blockOutlineColor, this::outline); break;
                case RAINBOW: rainbow(box, blockOutlineColor, blockOutlineTraversal.on(), this::outline); break;
                case BLEND: blend(box, blockOutlineColor, blockOutlineColorEnd, blockOutlineTraversal.on(), outlineTicks,
                    blockOutlineTraversalSpeed.value(), blockOutlineInterpolateAlpha.on(), this::outline); break;
            }
        }
    }

    private static float r(int c) { return (c >> 16 & 255) / 255.0F; }
    private static float g(int c) { return (c >> 8 & 255) / 255.0F; }
    private static float b(int c) { return (c & 255) / 255.0F; }
    private static float a(int c) { return (c >>> 24) / 255.0F; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private void plain(AxisAlignedBB box, ColorSetting color, Drawer d) {
        int c = color.color(0.0F);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        d.render(box, r(c), g(c), b(c), a(c), r(c), g(c), b(c), a(c), false);
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private void blend(AxisAlignedBB box, ColorSetting start, ColorSetting end, boolean traverse, int ticks, float speed,
                       boolean interpolateAlpha, Drawer d) {
        int c = start.color(0.0F);
        float r = r(c), g = g(c), b = b(c), a = a(c);
        int e = end.color(50.0F);
        float r2 = r(e), g2 = g(e), b2 = b(e);
        float a2 = (end.color(0.0F) >>> 24) / 255.0F;
        boolean alpha = interpolateAlpha && a != a2;
        float startA, endA;
        if (!traverse && !alpha) {
            startA = endA = a;
        } else {
            float t = (ticks + partialTicks) / (30.0F * speed);
            t = t >= 1.0F ? 2.0F - t : t;
            if (alpha) {
                startA = lerp(a, a2, t);
                endA = lerp(a, a2, 1.0F - t);
            } else {
                startA = endA = a;
            }
            if (traverse) {
                float nr = lerp(r, r2, t); r2 = lerp(r2, r, t); r = nr;
                float ng = lerp(g, g2, t); g2 = lerp(g2, g, t); g = ng;
                float nb = lerp(b, b2, t); b2 = lerp(b2, b, t); b = nb;
            }
        }
        GL11.glShadeModel(GL11.GL_SMOOTH);
        d.render(box, r, g, b, startA, r2, g2, b2, endA, true);
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private void rainbow(AxisAlignedBB box, ColorSetting color, boolean traverse, Drawer d) {
        int c = color.color(0.0F);
        float r = r(c), g = g(c), b = b(c), a = a(c);
        float r2, g2, b2;
        if (traverse) {
            int e = color.color(50.0F);
            r2 = r(e); g2 = g(e); b2 = b(e);
        } else {
            r2 = 1.0F - r; g2 = 1.0F - g; b2 = 1.0F - b;
        }
        GL11.glShadeModel(GL11.GL_SMOOTH);
        d.render(box, r, g, b, a, r2, g2, b2, a, true);
        GL11.glShadeModel(GL11.GL_FLAT);
    }

    private EnumFacing side() {
        if (!blockOutlineSide.on()) return null;
        MovingObjectPosition hit = Minecraft.getMinecraft().objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? hit.sideHit : null;
    }

    private void outline(AxisAlignedBB bb, float r, float g, float b, float a, float r2, float g2, float b2, float a2, boolean blend) {
        GL11.glLineWidth(blockOutlineWidth.value());
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        float[] s = {r, g, b, a}, e = {r2, g2, b2, a2};
        double x0 = bb.minX, y0 = bb.minY, z0 = bb.minZ, x1 = bb.maxX, y1 = bb.maxY, z1 = bb.maxZ;
        EnumFacing side = side();
        if (side != null) {
            switch (side) {
                case DOWN: ring(wr, x0, x1, y0, z0, z1, s, e); break;
                case UP: ring(wr, x0, x1, y1, z0, z1, s, e); break;
                case NORTH:
                    line(wr, x1, y1, z0, x1, y0, z0, s, e); line(wr, x1, y0, z0, x0, y0, z0, e, s);
                    line(wr, x0, y0, z0, x0, y1, z0, s, e); line(wr, x0, y1, z0, x1, y1, z0, e, s); break;
                case SOUTH:
                    line(wr, x1, y1, z1, x1, y0, z1, s, e); line(wr, x1, y0, z1, x0, y0, z1, e, s);
                    line(wr, x0, y0, z1, x0, y1, z1, s, e); line(wr, x0, y1, z1, x1, y1, z1, e, s); break;
                case WEST:
                    line(wr, x0, y0, z1, x0, y0, z0, s, e); line(wr, x0, y0, z0, x0, y1, z0, e, s);
                    line(wr, x0, y1, z0, x0, y1, z1, s, e); line(wr, x0, y1, z1, x0, y0, z1, e, s); break;
                case EAST:
                    line(wr, x1, y0, z1, x1, y0, z0, s, e); line(wr, x1, y0, z0, x1, y1, z0, e, s);
                    line(wr, x1, y1, z0, x1, y1, z1, s, e); line(wr, x1, y1, z1, x1, y0, z1, e, s); break;
            }
        } else {
            ring(wr, x0, x1, y0, z0, z1, s, e);
            ring(wr, x0, x1, y1, z0, z1, e, s);
            line(wr, x0, y0, z0, x0, y1, z0, s, e);
            line(wr, x1, y0, z0, x1, y1, z0, e, s);
            line(wr, x1, y0, z1, x1, y1, z1, s, e);
            line(wr, x0, y0, z1, x0, y1, z1, e, s);
        }
        tess.draw();
    }

    private static void ring(WorldRenderer wr, double x0, double x1, double y, double z0, double z1, float[] s, float[] e) {
        line(wr, x0, y, z0, x1, y, z0, s, e);
        line(wr, x1, y, z0, x1, y, z1, e, s);
        line(wr, x1, y, z1, x0, y, z1, s, e);
        line(wr, x0, y, z1, x0, y, z0, e, s);
    }

    private static void line(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1, float[] s, float[] e) {
        wr.pos(x0, y0, z0).color(s[0], s[1], s[2], s[3]).endVertex();
        wr.pos(x1, y1, z1).color(e[0], e[1], e[2], e[3]).endVertex();
    }

    private void overlay(AxisAlignedBB bb, float r, float g, float b, float a, float r2, float g2, float b2, float a2, boolean blend) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        EnumFacing side = side();
        if (side != null) {
            float x0 = (float)bb.minX, y0 = (float)bb.minY, z0 = (float)bb.minZ, x1 = (float)bb.maxX, y1 = (float)bb.maxY, z1 = (float)bb.maxZ;
            switch (side) {
                case DOWN: y0 -= 5.0E-4F;
                    v(wr, x1, y0, z0, r2, g2, b2, a2); v(wr, x1, y0, z1, r2, g2, b2, a2); v(wr, x0, y0, z1, r, g, b, a); v(wr, x0, y0, z0, r, g, b, a); break;
                case UP: y1 += 5.0E-4F;
                    v(wr, x0, y1, z0, r2, g2, b2, a2); v(wr, x0, y1, z1, r2, g2, b2, a2); v(wr, x1, y1, z1, r, g, b, a); v(wr, x1, y1, z0, r, g, b, a); break;
                case NORTH: z0 -= 5.0E-4F;
                    v(wr, x1, y1, z0, r2, g2, b2, a2); v(wr, x1, y0, z0, r2, g2, b2, a2); v(wr, x0, y0, z0, r, g, b, a); v(wr, x0, y1, z0, r, g, b, a); break;
                case SOUTH: z1 += 5.0E-4F;
                    v(wr, x0, y1, z1, r2, g2, b2, a2); v(wr, x0, y0, z1, r2, g2, b2, a2); v(wr, x1, y0, z1, r, g, b, a); v(wr, x1, y1, z1, r, g, b, a); break;
                case WEST: x0 -= 5.0E-4F;
                    v(wr, x0, y1, z1, r2, g2, b2, a2); v(wr, x0, y1, z0, r2, g2, b2, a2); v(wr, x0, y0, z0, r, g, b, a); v(wr, x0, y0, z1, r, g, b, a); break;
                case EAST: x1 += 5.0E-4F;
                    v(wr, x1, y0, z1, r2, g2, b2, a2); v(wr, x1, y0, z0, r2, g2, b2, a2); v(wr, x1, y1, z0, r, g, b, a); v(wr, x1, y1, z1, r, g, b, a); break;
            }
        } else {
            AxisAlignedBB e = bb.expand(5.0E-4, 5.0E-4, 5.0E-4);
            float x0 = (float)e.minX, y0 = (float)e.minY, z0 = (float)e.minZ, x1 = (float)e.maxX, y1 = (float)e.maxY, z1 = (float)e.maxZ;

            v(wr, x0, y0, z0, r, g, b, a); v(wr, x0, y0, z1, r, g, b2, a); v(wr, x0, y1, z1, r, g2, b2, a); v(wr, x0, y1, z0, r, g2, b, a);
            v(wr, x0, y1, z1, r, g2, b2, a); v(wr, x0, y0, z1, r, g, b2, a); v(wr, x1, y0, z1, r2, g, b2, a2); v(wr, x1, y1, z1, r2, g2, b2, a2);
            v(wr, x1, y0, z1, r2, g, b2, a2); v(wr, x1, y0, z0, r2, g, b, a2); v(wr, x1, y1, z0, r2, g2, b, a2); v(wr, x1, y1, z1, r2, g2, b2, a2);
            v(wr, x1, y1, z0, r2, g2, b, a2); v(wr, x1, y0, z0, r2, g, b, a2); v(wr, x0, y0, z0, r, g, b, a); v(wr, x0, y1, z0, r, g2, b, a);
            v(wr, x0, y0, z0, r, g, b, a); v(wr, x1, y0, z0, r2, g, b, a2); v(wr, x1, y0, z1, r2, g, b2, a2); v(wr, x0, y0, z1, r, g, b2, a);
            v(wr, x0, y1, z0, r, g2, b, a); v(wr, x0, y1, z1, r, g2, b2, a); v(wr, x1, y1, z1, r2, g2, b2, a2); v(wr, x1, y1, z0, r2, g2, b, a2);
        }
        tess.draw();
    }

    private static void v(WorldRenderer wr, float x, float y, float z, float r, float g, float b, float a) {
        wr.pos(x, y, z).color(r, g, b, a).endVertex();
    }
}
