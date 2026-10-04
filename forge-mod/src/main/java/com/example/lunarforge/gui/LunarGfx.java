package com.example.lunarforge.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public final class LunarGfx {
    public static final String ROBOTO_LIGHT = "roboto-light.ttf";
    public static final String ROBOTO_MEDIUM = "roboto-medium.ttf";
    public static final String RALEWAY_EXTRABOLD = "raleway-extrabold.ttf";
    public static final String RALEWAY_LIGHT = "raleway-light.ttf";

    public static final class Tex {
        final ResourceLocation location;
        public final float width, height, ascent;

        Tex(ResourceLocation location, float width, float height, float ascent) {
            this.location = location; this.width = width; this.height = height; this.ascent = ascent;
        }
    }

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final Map<String, Font> FONTS = new HashMap<String, Font>();

    private final Map<String, Tex> cache = new HashMap<String, Tex>();
    private final String name;
    private float dsf = 1;

    public LunarGfx(String name) { this.name = name; }

    public void setScale(float dsf) {
        if (dsf != this.dsf) { release(); this.dsf = dsf; }
    }

    private static Font font(String file, float lunarSize) {
        Font font = FONTS.get(file);
        if (font == null) {
            try (InputStream in = LunarGfx.class.getResourceAsStream("/assets/lunarforge/ui/fonts/" + file)) {
                font = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                throw new IllegalStateException("Missing Lunar font " + file, e);
            }
            FONTS.put(file, font);
        }
        return font.deriveFont(lunarSize / 2f);
    }

    public float textWidth(String text, String font, float size) {
        return (float)font(font, size).getStringBounds(text, FRC).getWidth();
    }

    public float textHeight(String font, float size) {
        LineMetrics lm = font(font, size).getLineMetrics("Ag", FRC);
        return lm.getAscent() + lm.getDescent();
    }

    public void text(String text, String font, float size, float x, float y, int argb) {
        if (text.isEmpty() || (argb >>> 24) == 0) return;
        Tex tex = textMask(text, font, size);
        draw(tex, x, y, tex.width, tex.height, argb);
    }

    private Tex textMask(String text, String file, float size) {
        String key = "t|" + file + "|" + size + "|" + text;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        Font f = font(file, size);
        LineMetrics lm = f.getLineMetrics(text, FRC);
        float width = (float)f.getStringBounds(text, FRC).getWidth(), height = lm.getAscent() + lm.getDescent();
        int pw = Math.max(1, (int)Math.ceil(width * dsf) + 2), ph = Math.max(1, (int)Math.ceil(height * dsf) + 1);
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(f.deriveFont(f.getSize2D() * dsf));
        g.setColor(Color.WHITE);
        g.drawString(text, 0f, lm.getAscent() * dsf);
        g.dispose();
        return put(key, image, lm.getAscent());
    }

    public void roundRect(float x, float y, float w, float h, float radius, int argb) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        String key = "r|" + w + "|" + h + "|" + radius;
        Tex tex = cache.get(key);
        if (tex == null) {
            BufferedImage image = canvas(w, h);
            Graphics2D g = graphics(image);
            float r = Math.min(radius, Math.min(w, h) / 2) * dsf * 2;
            g.fill(new RoundRectangle2D.Float(0, 0, image.getWidth(), image.getHeight(), r, r));
            g.dispose();
            tex = put(key, image, 0);
        }
        draw(tex, x, y, w, h, argb);
    }

    public void roundOutline(float x, float y, float w, float h, float radius, float line, int argb) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        String key = "o|" + w + "|" + h + "|" + radius + "|" + line;
        Tex tex = cache.get(key);
        if (tex == null) {
            BufferedImage image = canvas(w, h);
            Graphics2D g = graphics(image);
            float l = line * dsf, r = Math.max(0, Math.min(radius, Math.min(w, h) / 2) * dsf * 2 - l);
            g.setStroke(new BasicStroke(l));
            g.draw(new RoundRectangle2D.Float(l / 2, l / 2, image.getWidth() - l, image.getHeight() - l, r, r));
            g.dispose();
            tex = put(key, image, 0);
        }
        draw(tex, x, y, w, h, argb);
    }

    public void image(String path, float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0) return;
        String key = "i|" + path;
        Tex tex = cache.get(key);
        if (tex == null) {
            try (InputStream in = LunarGfx.class.getResourceAsStream("/assets/lunarforge/" + path)) {
                BufferedImage image = ImageIO.read(in);
                tex = put(key, image, 0);
            } catch (Exception e) {
                throw new IllegalStateException("Missing Lunar texture " + path, e);
            }
        }
        draw(tex, x, y, w, h, argb);
    }

    public static void rect(float x1, float y1, float x2, float y2, int argb) {
        if ((argb >>> 24) == 0) return;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        color(argb);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(x1, y2, 0).endVertex();
        wr.pos(x2, y2, 0).endVertex();
        wr.pos(x2, y1, 0).endVertex();
        wr.pos(x1, y1, 0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void outline(float x, float y, float w, float h, float line, int argb) {
        rect(x, y, x + w, y + line, argb);
        rect(x, y + h - line, x + w, y + h, argb);
        rect(x, y + line, x + line, y + h - line, argb);
        rect(x + w - line, y + line, x + w, y + h - line, argb);
    }

    public static int alpha(int argb, float factor) {
        int a = Math.round((argb >>> 24) * Math.max(0, Math.min(1, factor)));
        return a << 24 | argb & 0xFFFFFF;
    }

    public static int mix(int from, int to, float t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    private static void color(int argb) {
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private void draw(Tex tex, float x, float y, float w, float h, int argb) {
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex.location);
        color(argb);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 1).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    private BufferedImage canvas(float w, float h) {
        return new BufferedImage(Math.max(1, Math.round(w * dsf)), Math.max(1, Math.round(h * dsf)), BufferedImage.TYPE_INT_ARGB);
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);
        return g;
    }

    private Tex put(String key, BufferedImage image, float ascent) {
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
            .getDynamicTextureLocation("lunarforge_" + name + "_" + Integer.toHexString(key.hashCode()), texture);

        GlStateManager.bindTexture(texture.getGlTextureId());
        texture.setBlurMipmap(true, false);
        Tex tex = new Tex(location, image.getWidth() / dsf, image.getHeight() / dsf, ascent);
        cache.put(key, tex);
        return tex;
    }

    public void release() {
        for (Tex tex : cache.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(tex.location);
        cache.clear();
    }
}
