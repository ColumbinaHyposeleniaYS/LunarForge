package com.example.lunarforge.module.modules.mechanic;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

final class CrosshairGl implements CrosshairSurface {
    static final CrosshairGl INSTANCE = new CrosshairGl();

    private final Map<String, Object[]> images = new HashMap<String, Object[]>();

    private CrosshairGl() {}

    @Override public void push() { GlStateManager.pushMatrix(); }
    @Override public void pop() { GlStateManager.popMatrix(); }
    @Override public void translate(float x, float y) { GlStateManager.translate(x, y, 0.0f); }
    @Override public void scale(float x, float y) { GlStateManager.scale(x, y, 1.0f); }
    @Override public void rotate(float degrees) { GlStateManager.rotate(degrees, 0.0f, 0.0f, 1.0f); }

    private static void blend(boolean invert) {
        GlStateManager.enableBlend();
        if (invert) GlStateManager.tryBlendFuncSeparate(775, 769, 1, 0);
        else GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
    }

    private static void done() {
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void vertex(WorldRenderer wr, float x, float y, int c) {
        wr.pos(x, y, 0.0).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    private static void vertex(WorldRenderer wr, float x, float y, float u, float v, int c) {
        wr.pos(x, y, 0.0).tex(u, v).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
    }

    @Override public void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4, boolean invert) {
        blend(invert);
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(7425);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vertex(wr, x1, y1, c1);
        vertex(wr, x2, y2, c2);
        vertex(wr, x3, y3, c3);
        vertex(wr, x4, y4, c4);
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableTexture2D();
        done();
    }

    @Override public void texture(String name, float x, float y, float w, float h, int argb, boolean invert) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation("lunarforge", "textures/crosshair/" + name + ".png"));
        textured(x, y, w, h, argb, argb, argb, argb, invert);
    }

    @Override public void image(String key, BufferedImage image, float x, float y, float w, float h, int tl, int tr, int br, int bl, boolean invert) {
        Object[] held = images.get(key);
        ResourceLocation location;
        if (held == null || held[0] != image) {
            if (held != null) Minecraft.getMinecraft().getTextureManager().deleteTexture((ResourceLocation)held[1]);
            location = new ResourceLocation("lunarforge", "custom_crosshair_" + key);
            Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(image));
            images.put(key, new Object[]{image, location});
        } else {
            location = (ResourceLocation)held[1];
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        textured(x, y, w, h, tl, tr, br, bl, invert);
    }

    void release(String key) {
        Object[] held = images.remove(key);
        if (held != null) Minecraft.getMinecraft().getTextureManager().deleteTexture((ResourceLocation)held[1]);
    }

    private static void textured(float x, float y, float w, float h, int tl, int tr, int br, int bl, boolean invert) {
        blend(invert);
        GlStateManager.enableTexture2D();
        GlStateManager.shadeModel(7425);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        vertex(wr, x, y + h, 0.0f, 1.0f, bl);
        vertex(wr, x + w, y + h, 1.0f, 1.0f, br);
        vertex(wr, x + w, y, 1.0f, 0.0f, tr);
        vertex(wr, x, y, 0.0f, 0.0f, tl);
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(7424);
        done();
    }
}
