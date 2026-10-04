package com.example.lunarforge.gui.home;

import com.example.lunarforge.gui.BoxShadow;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class HomeGfx {
    static final class Tex {
        final DynamicTexture texture; final ResourceLocation location; final float width, height;
        Tex(BufferedImage image, float width, float height, String name) {
            texture = new DynamicTexture(image);
            location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("lunarforge_home_" + name, texture);
            this.width = width; this.height = height;
        }
    }

    private final Map<String, Tex> cache = new HashMap<String, Tex>();
    private final Font medium, bold;

    private Font legacy;
    float dsf = 1;

    HomeGfx() {
        medium = font("DMSans-Medium.ttf"); bold = font("DMSans-Bold.ttf");
    }

    private static Font font(String file) {
        try (InputStream in = HomeGfx.class.getResourceAsStream("/assets/lunarforge/ui/fonts/" + file)) {
            Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
            attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
            attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(attributes);
        } catch (Exception e) { throw new IllegalStateException("Missing Lunar font " + file, e); }
    }

    Font font(boolean isBold) { return isBold ? bold : medium; }

    private Font legacyFont() { if (legacy == null) legacy = font("roboto-bold.ttf"); return legacy; }

    float legacyTextWidth(String text, float cssSize) {
        return (float)legacyFont().deriveFont(cssSize).getStringBounds(text, frc()).getWidth();
    }

    Tex legacyText(String text, float cssSize) {
        String key = "lt|" + cssSize + "|" + dsf + "|" + text;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        Font cssFont = legacyFont().deriveFont(cssSize);
        java.awt.font.LineMetrics lm = cssFont.getLineMetrics(text, frc());
        float ascent = Math.round(lm.getAscent()), boxHeight = ascent + Math.round(lm.getDescent());
        float width = (float)cssFont.getStringBounds(text, frc()).getWidth();
        int pw = Math.max(1, (int)Math.ceil(width * dsf) + 2), ph = Math.max(1, (int)Math.ceil(boxHeight * dsf));
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(legacyFont().deriveFont(cssSize * dsf));
        g.setColor(Color.WHITE);
        g.drawString(text, 0f, ascent * dsf);
        g.dispose();
        tex = new Tex(image, pw / dsf, ph / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    Tex ring(float w, float h, float radius, float inset, float thickness) {
        String key = "r|" + w + "|" + h + "|" + radius + "|" + inset + "|" + thickness + "|" + dsf;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        int pw = Math.max(1, Math.round(w * dsf)), ph = Math.max(1, Math.round(h * dsf));
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        float i = inset * dsf, t = thickness * dsf, r = Math.max(0, radius * dsf - i), r2 = Math.max(0, r - t);
        java.awt.geom.Area a = new java.awt.geom.Area(new RoundRectangle2D.Float(i, i, pw - 2 * i, ph - 2 * i, r * 2, r * 2));
        a.subtract(new java.awt.geom.Area(new RoundRectangle2D.Float(i + t, i + t, pw - 2 * (i + t), ph - 2 * (i + t), r2 * 2, r2 * 2)));
        g.fill(a);
        g.dispose();
        tex = new Tex(image, pw / dsf, ph / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    float textWidth(String text, boolean isBold, float cssSize) {
        return (float)font(isBold).deriveFont(cssSize).getStringBounds(text, frc()).getWidth();
    }

    private static java.awt.font.FontRenderContext frc() {
        return new java.awt.font.FontRenderContext(null, true, true);
    }

    Tex text(String text, boolean isBold, float cssSize) {
        String key = "t|" + isBold + "|" + cssSize + "|" + dsf + "|" + text;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        Font cssFont = font(isBold).deriveFont(cssSize);
        java.awt.font.LineMetrics lm = cssFont.getLineMetrics(text, frc());
        float ascent = Math.round(lm.getAscent()), boxHeight = ascent + Math.round(lm.getDescent());
        float width = (float)cssFont.getStringBounds(text, frc()).getWidth();
        int pw = Math.max(1, (int)Math.ceil(width * dsf) + 2), ph = Math.max(1, (int)Math.ceil(boxHeight * dsf));
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font(isBold).deriveFont(cssSize * dsf));
        g.setColor(Color.WHITE);
        g.drawString(text, 0f, ascent * dsf);
        g.dispose();
        tex = new Tex(image, pw / dsf, ph / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    Tex box(float w, float h, float radius) {
        String key = "b|" + w + "|" + h + "|" + radius + "|" + dsf;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        int pw = Math.max(1, Math.round(w * dsf)), ph = Math.max(1, Math.round(h * dsf));
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        float r = Math.min(radius, Math.min(w, h) / 2) * dsf * 2;
        g.fill(new RoundRectangle2D.Float(0, 0, pw, ph, r, r));
        g.dispose();
        tex = new Tex(image, pw / dsf, ph / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    Tex shadow(float w, float h, float radius, float dy, float blur, float spread) {
        String key = "s|" + w + "|" + h + "|" + radius + "|" + dy + "|" + blur + "|" + spread + "|" + dsf;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        BufferedImage image = BoxShadow.mask(w, h, radius, dy, blur, spread, dsf);
        tex = new Tex(image, image.getWidth() / dsf, image.getHeight() / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    static float shadowPad(float blur) { return BoxShadow.pad(blur); }

    float snap(float v) { return Math.round(v * dsf) / dsf; }

    void draw(Tex tex, float x, float y, int argb) { drawRaw(tex.location, snap(x), snap(y), tex.width, tex.height, argb); }

    void rect(float x, float y, float w, float h, int argb) {
        GlStateManager.enableBlend(); GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        Tessellator tessellator = Tessellator.getInstance(); WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION);
        wr.pos(x, y + h, 0).endVertex(); wr.pos(x + w, y + h, 0).endVertex(); wr.pos(x + w, y, 0).endVertex(); wr.pos(x, y, 0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }

    void draw(ResourceLocation location, float x, float y, float w, float h, int argb) {
        Tex tex = icon(location, w, h);
        if (tex == null) drawRaw(location, x, y, w, h, argb);
        else drawRaw(tex.location, snap(x), snap(y), tex.width, tex.height, argb);
    }

    private final Set<ResourceLocation> missing = new HashSet<ResourceLocation>();

    private Tex icon(ResourceLocation location, float w, float h) {
        String key = "i|" + location + "|" + w + "|" + h + "|" + dsf;
        Tex tex = cache.get(key);
        if (tex != null || missing.contains(location)) return tex;
        BufferedImage src;
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            src = javax.imageio.ImageIO.read(in);
        } catch (Exception e) { missing.add(location); return null; }
        int pw = Math.max(1, Math.round(w * dsf)), ph = Math.max(1, Math.round(h * dsf));
        tex = new Tex(areaResample(src, pw, ph), pw / dsf, ph / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    Tex circleImage(String name, BufferedImage src, float size, int backdrop) {
        String key = "c|" + name + "|" + size + "|" + dsf;
        Tex tex = cache.get(key);
        if (tex != null) return tex;
        int px = Math.max(1, Math.round(size * dsf));
        BufferedImage image = areaResample(src, px, px), mask = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Ellipse2D.Float(0, 0, px, px));
        g.dispose();
        float ba = (backdrop >>> 24) / 255f;
        for (int y = 0; y < px; y++) for (int x = 0; x < px; x++) {
            int c = image.getRGB(x, y); float ia = (c >>> 24) / 255f, oa = ia + ba * (1 - ia);
            float coverage = (mask.getRGB(x, y) >>> 24) / 255f;
            if (oa <= 0 || coverage <= 0) { image.setRGB(x, y, 0); continue; }
            int rgb = 0;
            for (int shift = 0; shift < 24; shift += 8)
                rgb |= Math.min(255, Math.round((((c >> shift) & 0xFF) * ia + ((backdrop >> shift) & 0xFF) * ba * (1 - ia)) / oa)) << shift;
            image.setRGB(x, y, Math.round(oa * coverage * 255) << 24 | rgb);
        }
        tex = new Tex(image, px / dsf, px / dsf, Integer.toHexString(key.hashCode()));
        cache.put(key, tex);
        return tex;
    }

    private static BufferedImage areaResample(BufferedImage src, int dw, int dh) {
        int sw = src.getWidth(), sh = src.getHeight();
        float[] a = new float[sw * sh], r = new float[sw * sh], g = new float[sw * sh], b = new float[sw * sh];
        for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++) {
            int c = src.getRGB(x, y), i = y * sw + x; float al = (c >>> 24) / 255f;
            a[i] = al; r[i] = ((c >> 16) & 0xFF) * al; g[i] = ((c >> 8) & 0xFF) * al; b[i] = (c & 0xFF) * al;
        }
        BufferedImage out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        float fx = (float)sw / dw, fy = (float)sh / dh;
        for (int y = 0; y < dh; y++) {
            float y0 = y * fy, y1 = y0 + fy;
            for (int x = 0; x < dw; x++) {
                float x0 = x * fx, x1 = x0 + fx, sa = 0, sr = 0, sg = 0, sb = 0, area = 0;
                for (int sy = (int)y0; sy < Math.min(sh, (int)Math.ceil(y1)); sy++) {
                    float wy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int)x0; sx < Math.min(sw, (int)Math.ceil(x1)); sx++) {
                        float wgt = wy * (Math.min(x1, sx + 1) - Math.max(x0, sx)); int i = sy * sw + sx;
                        sa += a[i] * wgt; sr += r[i] * wgt; sg += g[i] * wgt; sb += b[i] * wgt; area += wgt;
                    }
                }
                if (area <= 0 || sa <= 0) continue;
                int alpha = Math.min(255, Math.round(sa / area * 255));
                out.setRGB(x, y, alpha << 24 | Math.min(255, Math.round(sr / sa)) << 16 | Math.min(255, Math.round(sg / sa)) << 8 | Math.min(255, Math.round(sb / sa)));
            }
        }
        return out;
    }

    private void drawRaw(ResourceLocation location, float x, float y, float w, float h, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableBlend(); GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.color(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        Tessellator tessellator = Tessellator.getInstance(); WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + h, 0).tex(0, 1).endVertex(); wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y, 0).tex(1, 0).endVertex(); wr.pos(x, y, 0).tex(0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    void roundedRegion(ResourceLocation location, float x, float y, float w, float h, float radius, float u0, float v0, float u1, float v1) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        GlStateManager.enableBlend(); GlStateManager.enableTexture2D(); GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.color(1, 1, 1, 1);
        List<float[]> points = new ArrayList<float[]>();
        float r = Math.min(radius, Math.min(w, h) / 2);
        float[][] centres = {{x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}, {x + r, y + r}};
        for (int corner = 0; corner < 4; corner++)
            for (int i = 0; i <= 12; i++) {
                double angle = Math.toRadians(-90 + corner * 90 + i * 90 / 12.0);
                points.add(new float[]{centres[corner][0] + (float)Math.cos(angle) * r, centres[corner][1] + (float)Math.sin(angle) * r});
            }
        Tessellator tessellator = Tessellator.getInstance(); WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x + w / 2, y + h / 2, 0).tex((u0 + u1) / 2, (v0 + v1) / 2).endVertex();
        for (int i = 0; i <= points.size(); i++) {
            float[] p = points.get(i % points.size());
            wr.pos(p[0], p[1], 0).tex(u0 + (p[0] - x) / w * (u1 - u0), v0 + (p[1] - y) / h * (v1 - v0)).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableCull();
    }

    static void resyncTextureState() { GlStateManager.disableTexture2D(); GlStateManager.enableTexture2D(); }

    void release() {
        for (Tex tex : cache.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(tex.location);
        cache.clear();
    }
}
