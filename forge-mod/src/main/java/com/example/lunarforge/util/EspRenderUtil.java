package com.example.lunarforge.util;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/**
 * Shared world-space rendering helpers for the ESP-family modules (ported from
 * Leader-Lite's RenderUtil). Everything runs inside RenderWorldLastEvent, where
 * the camera modelview is active and RenderManager.viewerPos* is the camera
 * position, so boxes are drawn camera-relative by subtracting the viewer pos.
 * The two Leader-Lite niceties that need mixins (RenderManager.renderPos
 * accessors and a bob-free setupCameraTransform re-run for tracers) are
 * replaced by the public viewerPos fields and by drawing tracer lines straight
 * from the camera-space origin, which is geometrically identical.
 */
public final class EspRenderUtil {
    private static final Frustum FRUSTUM = new Frustum();
    private static final FloatBuffer MODEL_VIEW = FloatBuffer.allocate(16);
    private static final FloatBuffer PROJECTION = FloatBuffer.allocate(16);
    private static final IntBuffer VIEWPORT = IntBuffer.allocate(16);
    private static final FloatBuffer WINDOW = FloatBuffer.allocate(16);

    private static final int[] CHAT_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private EspRenderUtil() {}

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // ===== state =====

    public static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }

    public static void disableRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void setColor(int argb) {
        GlStateManager.color((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F,
                (argb & 255) / 255.0F, (argb >>> 24) / 255.0F);
    }

    // ===== math =====

    public static double lerpDouble(double current, double previous, float t) {
        return previous + (current - previous) * t;
    }

    public static float lerpFloat(float current, float previous, float t) {
        return previous + (current - previous) * t;
    }

    public static float partialTicks(RenderWorldLastEvent event) {
        return event.partialTicks;
    }

    /** Interpolated entity position minus the camera position (RenderWorldLastEvent space). */
    public static double[] cameraRelative(Entity entity, RenderWorldLastEvent event) {
        float t = event.partialTicks;
        RenderManager rm = mc().getRenderManager();
        return new double[]{
                lerpDouble(entity.posX, entity.lastTickPosX, t) - rm.viewerPosX,
                lerpDouble(entity.posY, entity.lastTickPosY, t) - rm.viewerPosY,
                lerpDouble(entity.posZ, entity.lastTickPosZ, t) - rm.viewerPosZ};
    }

    public static boolean isInViewFrustum(AxisAlignedBB box, double expand) {
        Entity view = mc().getRenderViewEntity();
        if (view == null) return true;
        FRUSTUM.setPosition(view.posX, view.posY, view.posZ);
        return FRUSTUM.isBoundingBoxInFrustum(box.expand(expand, expand, expand));
    }

    /** Vanilla chat color for the player's scoreboard team prefix, white fallback. */
    public static int teamColor(String playerName) {
        Minecraft mc = mc();
        if (mc.theWorld == null || mc.thePlayer == null) return 0xFFFFFFFF;
        net.minecraft.scoreboard.ScorePlayerTeam team =
                mc.theWorld.getScoreboard().getPlayersTeam(playerName);
        if (team == null) return 0xFFFFFFFF;
        String prefix = team.getColorPrefix();
        if (prefix == null) return 0xFFFFFFFF;
        for (int i = 0; i < prefix.length() - 1; i++) {
            if (prefix.charAt(i) == '\u00A7') {
                int index = "0123456789abcdef".indexOf(Character.toLowerCase(prefix.charAt(i + 1)));
                return index >= 0 ? 0xFF000000 | CHAT_COLORS[index] : 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }

    /** ColorUtil.getHealthBlend: red to green health gradient. */
    public static int healthBlend(float percent) {
        int red = 0xFFFF5555, yellow = 0xFFFFFF55, green = 0xFF55FF55;
        if (percent >= 0.9F) return green;
        if (percent >= 0.55F) return blend(yellow, green, (percent - 0.55F) / 0.35F);
        if (percent >= 0.45F) return yellow;
        if (percent >= 0.1F) return blend(red, yellow, (percent - 0.1F) / 0.35F);
        return red;
    }

    private static int blend(int from, int to, float t) {
        int out = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    public static int darker(int argb, float factor) {
        int a = argb & 0xFF000000;
        int r = Math.round((argb >> 16 & 255) * factor);
        int g = Math.round((argb >> 8 & 255) * factor);
        int b = Math.round((argb & 255) * factor);
        return a | r << 16 | g << 8 | b;
    }

    // ===== boxes =====

    public static void drawFilledBox(AxisAlignedBB box, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, box.minX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.minY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.minY, box.maxZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.maxZ, red, green, blue);
        vertex(wr, box.minX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.minX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.minX, box.minY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.minZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.minZ, red, green, blue);
        vertex(wr, box.maxX, box.maxY, box.maxZ, red, green, blue);
        vertex(wr, box.maxX, box.minY, box.maxZ, red, green, blue);
        tessellator.draw();
    }

    private static void vertex(WorldRenderer wr, double x, double y, double z, int r, int g, int b) {
        wr.pos(x, y, z).color(r, g, b, 63).endVertex();
    }

    public static void drawBoundingBox(AxisAlignedBB box, int red, int green, int blue, int alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderGlobal.drawOutlinedBoundingBox(box, red, green, blue, alpha);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
    }

    public static AxisAlignedBB entityBox(Entity entity, RenderWorldLastEvent event, double expand) {
        double[] c = cameraRelative(entity, event);
        return entity.getEntityBoundingBox().expand(expand, expand, expand)
                .offset(c[0] - entity.posX, c[1] - entity.posY, c[2] - entity.posZ);
    }

    public static void drawEntityBoundingBox(Entity entity, RenderWorldLastEvent event,
                                             int red, int green, int blue, int alpha, float lineWidth, double expand) {
        drawBoundingBox(entityBox(entity, event, expand), red, green, blue, alpha, lineWidth);
    }

    public static AxisAlignedBB blockBox(BlockPos pos, double height, RenderManager rm) {
        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0)
                .offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
    }

    // ===== lines / tracers =====

    /**
     * Tracer from the camera to a world point: in RenderWorldLastEvent space the
     * camera sits at the modelview origin, so the line runs origin -> target -
     * viewerPos and tracks view bobbing exactly (Leader-Lite needed a mixin to
     * redo setupCameraTransform for this; the current matrix already has it).
     */
    public static void drawCameraLine(double toX, double toY, double toZ,
                                      float red, float green, float blue, float alpha, float lineWidth) {
        RenderManager rm = mc().getRenderManager();
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        GL11.glVertex3d(toX - rm.viewerPosX, toY - rm.viewerPosY, toZ - rm.viewerPosZ);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
    }

    // ===== 2D (ESP 2D mode / health bars) =====

    public static void drawRect(float x1, float y1, float x2, float y2, int argb) {
        if (argb == 0) return;
        setColor(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    public static void drawOutlineRect(float x1, float y1, float x2, float y2, float lineWidth, int backgroundColor, int lineColor) {
        if (backgroundColor != 0) drawRect(x1, y1, x2, y2, backgroundColor);
        if (lineColor == 0) return;
        setColor(lineColor);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float lineWidth, int argb) {
        setColor(argb);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    /**
     * GLU projection of the entity's interpolated + expanded bounding box onto
     * the current frame; returns {minX, minY, maxX, maxY} in scaled GUI pixels
     * or null when no corner projects in front of the camera.
     */
    public static float[] projectToScreen(Entity entity, RenderWorldLastEvent event, double screenScale) {
        AxisAlignedBB box = entityBox(entity, event, 0.1);
        RenderManager rm = mc().getRenderManager();
        float minX = 0, minY = 0, maxX = 0, maxY = 0;
        boolean any = false;
        double[][] corners = {
                {box.minX, box.minY, box.minZ}, {box.minX, box.maxY, box.minZ},
                {box.maxX, box.minY, box.minZ}, {box.maxX, box.maxY, box.minZ},
                {box.minX, box.minY, box.maxZ}, {box.minX, box.maxY, box.maxZ},
                {box.maxX, box.minY, box.maxZ}, {box.maxX, box.maxY, box.maxZ}};
        for (double[] corner : corners) {
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
            if (!GLU.gluProject((float) (corner[0] - rm.viewerPosX), (float) (corner[1] - rm.viewerPosY),
                    (float) (corner[2] - rm.viewerPosZ), MODEL_VIEW, PROJECTION, VIEWPORT, WINDOW)) {
                continue;
            }
            float x = WINDOW.get(0) / (float) screenScale;
            float y = (Display.getHeight() - WINDOW.get(1)) / (float) screenScale;
            float z = WINDOW.get(2);
            if (z < 0.0f || z >= 1.0f) continue;
            if (!any) {
                minX = maxX = x;
                minY = maxY = y;
                any = true;
            }
            minX = Math.min(x, minX);
            minY = Math.min(y, minY);
            maxX = Math.max(x, maxX);
            maxY = Math.max(y, maxY);
        }
        return any ? new float[]{minX, minY, maxX, maxY} : null;
    }

    public static float guiScale() {
        return new ScaledResolution(mc()).getScaleFactor();
    }

    /**
     * Draws 2D screen-space shapes from inside RenderWorldLastEvent: the current
     * 3D camera matrices are captured, EntityRenderer.setupOverlayRendering sets
     * up the scaled ortho overlay, the shapes are drawn, and the captured
     * matrices are reloaded. This replaces Leader-Lite's private
     * setupCameraTransform accessor (their 2D pass re-applies the camera from
     * inside the 2D event; we keep the camera and swap the overlay in).
     */
    public static void withOverlay(Runnable draw) {
        FloatBuffer savedProjection = FloatBuffer.allocate(16);
        FloatBuffer savedModelView = FloatBuffer.allocate(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, savedProjection);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, savedModelView);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        mc().entityRenderer.setupOverlayRendering();
        draw.run();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(savedProjection);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(savedModelView);
        GL11.glMatrixMode(matrixMode);
    }

    // ===== billboard world-space shapes (FAKECORNER / FAKE2D / RAVEN bar) =====

    public static void drawRect3D(float x1, float y1, float x2, float y2, int argb) {
        if (argb == 0) return;
        setColor(argb);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < 2; ++i) {
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    private static void draw3DRect(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    public static void drawCornerESP(Entity entity, RenderWorldLastEvent event, float red, float green, float blue) {
        double[] c = cameraRelative(entity, event);
        GlStateManager.pushMatrix();
        GlStateManager.translate(c[0], c[1] + entity.height / 2.0, c[2]);
        GlStateManager.rotate(-mc().getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.098F, -0.098F, 0.098F);
        float width = (float) (26.6 * entity.width / 2.0);
        float height = 12.0F;
        GlStateManager.color(red, green, blue, 1.0F);
        draw3DRect(width, height - 1.0F, width - 4.0F, height);
        draw3DRect(-width, height - 1.0F, -width + 4.0F, height);
        draw3DRect(-width, height, -width + 1.0F, height - 4.0F);
        draw3DRect(width, height, width - 1.0F, height - 4.0F);
        draw3DRect(width, -height, width - 4.0F, -height + 1.0F);
        draw3DRect(-width, -height, -width + 4.0F, -height + 1.0F);
        draw3DRect(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F);
        draw3DRect(width, -height + 1.0F, width - 1.0F, -height + 4.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F, 1.0F);
        draw3DRect(width, height, width - 4.0F, height + 0.2F);
        draw3DRect(-width, height, -width + 4.0F, height + 0.2F);
        draw3DRect(-width - 0.2F, height + 0.2F, -width, height - 4.0F);
        draw3DRect(width + 0.2F, height + 0.2F, width, height - 4.0F);
        draw3DRect(width + 0.2F, -height, width - 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width, -height + 4.0F);
        draw3DRect(width + 0.2F, -height, width, -height + 4.0F);
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    public static void drawFake2DESP(Entity entity, RenderWorldLastEvent event, float red, float green, float blue) {
        double[] c = cameraRelative(entity, event);
        GlStateManager.pushMatrix();
        GlStateManager.translate(c[0], c[1] + entity.height / 2.0, c[2]);
        GlStateManager.rotate(-mc().getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.1F, -0.1F, 0.1F);
        GlStateManager.color(red, green, blue, 1.0F);
        float width = (float) (23.3 * entity.width / 2.0);
        float height = 12.0F;
        draw3DRect(width, height, -width, height + 0.4F);
        draw3DRect(width, -height, -width, -height + 0.4F);
        draw3DRect(width, -height + 0.4F, width - 0.4F, height + 0.4F);
        draw3DRect(-width, -height + 0.4F, -width + 0.4F, height + 0.4F);
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    // ===== world-space labels / items (NameTags, ItemESP) =====

    public static void renderItemInGUI(ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        Minecraft.getMinecraft().getRenderItem().zLevel = -150.0f;
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        Minecraft.getMinecraft().getRenderItem().renderItemOverlays(Minecraft.getMinecraft().fontRendererObj, stack, x, y);
        Minecraft.getMinecraft().getRenderItem().zLevel = 0.0f;
        GlStateManager.popMatrix();
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void renderPotionEffect(net.minecraft.potion.PotionEffect effect, int x, int y) {
        int icon = net.minecraft.potion.Potion.potionTypes[effect.getPotionID()].getStatusIconIndex();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        mc().getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
        Gui.drawModalRectWithCustomSizedTexture(x, y, icon % 8 * 18, 198 + icon / 8 * 18, 18, 18, 256.0f, 256.0f);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
