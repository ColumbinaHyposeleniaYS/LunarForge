package com.example.lunarforge.module.hud;

import com.example.lunarforge.gui.LunarGfx;
import com.example.lunarforge.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public final class Draw {
    private Draw() {}

    public static FontRenderer font() { return Minecraft.getMinecraft().fontRendererObj; }

    public static int fontHeight() { return font().FONT_HEIGHT; }

    public static float width(String text) { return font().getStringWidth(text); }

    public static void rect(float x, float y, float w, float h, int argb) {
        if (w <= 0 || h <= 0) return;
        LunarGfx.rect(x, y, x + w, y + h, argb);
    }

    public static void fill(ColorSetting color, float x, float y, float w, float h) {
        rect(x, y, w, h, color.color(x + y));
    }

    public static void border(ColorSetting color, float x, float y, float w, float h, float t) {
        int c = color.color(x + y);
        rect(x - t, y - t, t, h + t * 2, c);
        rect(x + w, y - t, t, h + t * 2, c);
        rect(x, y - t, w, t, c);
        rect(x, y + h, w, t, c);
    }

    public static float text(String text, float x, float y, int argb, boolean shadow) {
        GlStateManager.enableBlend();
        return font().drawString(text, x, y, argb, shadow);
    }

    public static float text(ColorSetting color, String text, float x, float y, boolean shadow) {
        return text(text, x, y, color.color(x + y), shadow);
    }

    public static void centered(ColorSetting color, String text, float cx, float y, boolean shadow) {
        text(color, text, cx - width(text) / 2, y, shadow);
    }

    public static void gradient(float x1, float y1, float x2, float y2, int from, int to, boolean horizontal) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        int tl = from, tr = horizontal ? to : from, br = to, bl = horizontal ? from : to;
        vertex(wr, x2, y1, tr);
        vertex(wr, x1, y1, tl);
        vertex(wr, x1, y2, bl);
        vertex(wr, x2, y2, br);
        Tessellator.getInstance().draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static void vertex(WorldRenderer wr, float x, float y, int argb) {
        wr.pos(x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    public static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        begin(argb);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(4, DefaultVertexFormats.POSITION);
        wr.pos(x1, y1, 0).endVertex();
        wr.pos(x2, y2, 0).endVertex();
        wr.pos(x3, y3, 0).endVertex();
        Tessellator.getInstance().draw();
        end();
    }

    public static void roundedRect(float x, float y, float w, float h, float r, int argb) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        begin(argb);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(9, DefaultVertexFormats.POSITION);
        corner(wr, x + r, y + r, r, 180);
        corner(wr, x + w - r, y + r, r, 270);
        corner(wr, x + w - r, y + h - r, r, 0);
        corner(wr, x + r, y + h - r, r, 90);
        Tessellator.getInstance().draw();
        end();
    }

    private static void corner(WorldRenderer wr, float cx, float cy, float r, int from) {
        for (int a = from; a <= from + 90; a += 10) {
            double rad = Math.toRadians(a);
            wr.pos(cx + Math.cos(rad) * r, cy + Math.sin(rad) * r, 0).endVertex();
        }
    }

    private static void begin(int argb) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static void end() {
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void textureRegion(ResourceLocation texture, float x, float y, float w, float h, float u1, float v1, float u2, float v2) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(u1, v2).endVertex();
        wr.pos(x + w, y + h, 0).tex(u2, v2).endVertex();
        wr.pos(x + w, y, 0).tex(u2, v1).endVertex();
        wr.pos(x, y, 0).tex(u1, v1).endVertex();
        Tessellator.getInstance().draw();
    }

    public static void item(net.minecraft.item.ItemStack stack, float x, float y) {
        if (stack == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void blit(ResourceLocation texture, float x, float y, float u, float v, float w, float h, float texW, float texH, int argb) {
        if (w <= 0 || h <= 0) return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(u / texW, (v + h) / texH).endVertex();
        wr.pos(x + w, y + h, 0).tex((u + w) / texW, (v + h) / texH).endVertex();
        wr.pos(x + w, y, 0).tex((u + w) / texW, v / texH).endVertex();
        wr.pos(x, y, 0).tex(u / texW, v / texH).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static int textAlpha(int argb) { return (argb >>> 24) < 4 ? 4 << 24 | argb & 0xFFFFFF : argb; }

    public static int shadow(int argb) { return (argb & 0xFCFCFC) >> 2 | argb & 0xFF000000; }

    public static void texture(ResourceLocation texture, float x, float y, float w, float h, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 1).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.color(1, 1, 1, 1);
    }
}
