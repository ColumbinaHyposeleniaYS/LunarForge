package com.example.lunarforge.gui.locker;

import com.example.lunarforge.gui.BoxShadow;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

final class LockerStyle {
    static final int SPACE_1 = 0xFF13141A, SPACE_2 = 0xFF1A1B21, SPACE_3 = 0xFF22232C, SPACE_4 = 0xFF282A34, SPACE_5 = 0xFF2F313B;
    static final int SPACE_10 = 0xFF797B87, SPACE_11 = 0xFFB1B3C0, SPACE_12 = 0xFFEDEEF3;
    static final int ALPHA_2 = rgba(243, 244, 250, .03f), ALPHA_3 = rgba(202, 203, 245, .08f), ALPHA_4 = rgba(204, 214, 255, .11f),
        ALPHA_5 = rgba(212, 220, 254, .15f), ALPHA_9 = rgba(232, 234, 255, .42f), ALPHA_12 = rgba(249, 250, 255, .95f);

    static final int WINDOW = rgba(19, 20, 26, .93f);
    static final int GREEN_A3 = rgba(2, 249, 0, .11f), GREEN_A6 = rgba(20, 255, 63, .3f), GREEN_11 = 0xFF07F361, EQUIPPED_BAR = rgba(0, 100, 0, .7f);
    static final int RED_A = rgba(253, 0, 0, .19f), RED_11 = 0xFFFF8F88;
    static final int PURPLE_A3 = rgba(199, 21, 254, .18f), PURPLE_11 = 0xFFE093FF;

    private static final Map<String, BufferedImage> ICONS = new HashMap<String, BufferedImage>();
    private static Font regular, medium, bold;

    static int rgba(int r, int g, int b, float a) { return Math.round(a * 255) << 24 | r << 16 | g << 8 | b; }

    static Font font(int weight, float size) {
        if (regular == null) {
            regular = load("DMSans-Regular.ttf"); medium = load("DMSans-Medium.ttf"); bold = load("DMSans-Bold.ttf");
        }
        return (weight >= 700 ? bold : weight >= 500 ? medium : regular).deriveFont(size);
    }

    private static Font load(String file) {
        try (InputStream in = LockerStyle.class.getResourceAsStream("/assets/lunarforge/ui/fonts/" + file)) {
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) { return new Font("SansSerif", Font.PLAIN, 12); }
    }

    static BufferedImage icon(String path) {
        BufferedImage img = ICONS.get(path);
        if (img == null) {
            try (InputStream in = LockerStyle.class.getResourceAsStream("/assets/lunarforge/ui/locker/" + path)) {
                img = in == null ? null : ImageIO.read(in);
            } catch (Exception ignored) { }
            if (img == null) img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ICONS.put(path, img);
        }
        return img;
    }

    static void color(Graphics2D g, int argb) { g.setColor(new Color(argb, true)); }

    static void box(Graphics2D g, float x, float y, float w, float h, float r, int argb) {
        color(g, argb);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
    }

    static void box(Graphics2D g, float x, float y, float w, float h, float r, int argb, boolean top) {
        color(g, argb);
        java.awt.geom.Area a = new java.awt.geom.Area(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
        float k = Math.min(r, h / 2);
        a.add(new java.awt.geom.Area(top ? new Rectangle2D.Float(x, y + k, w, h - k) : new Rectangle2D.Float(x, y, w, h - k)));
        g.fill(a);
    }

    static void outline(Graphics2D g, float x, float y, float w, float h, float r, float width, int argb) {
        color(g, argb);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(width));

        g.draw(new RoundRectangle2D.Float(x - width / 2, y - width / 2, w + width, h + width, r * 2 + width, r * 2 + width));
        g.setStroke(old);
    }

    static void icon(Graphics2D g, String path, float x, float y, float w, float h, int argb, float opacity) {
        BufferedImage img = icon(path);
        if (argb != 0) img = tint(path, img, argb);
        Composite old = g.getComposite();
        if (opacity < 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform();
        t.translate(x, y);
        t.scale(w / img.getWidth(), h / img.getHeight());
        g.drawImage(img, t, null);
        g.setComposite(old);
    }

    private static final Map<String, BufferedImage> TINTED = new HashMap<String, BufferedImage>();

    private static BufferedImage tint(String path, BufferedImage src, int argb) {
        String key = path + "#" + Integer.toHexString(argb);
        BufferedImage out = TINTED.get(key);
        if (out != null) return out;
        out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int rgb = argb & 0xFFFFFF, a = argb >>> 24;
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = (src.getRGB(x, y) >>> 24) * a / 255;
                out.setRGB(x, y, alpha << 24 | rgb);
            }
        TINTED.put(key, out);
        return out;
    }

    private static final Map<String, BufferedImage> SHADOWS = new HashMap<String, BufferedImage>();

    static void shadow(Graphics2D g, float x, float y, float w, float h, float radius, float dy, float blur, float spread, int argb) {
        AffineTransform t = g.getTransform();
        float scale = (float)t.getScaleX(), pad = BoxShadow.pad(blur);
        Point2D p = t.transform(new Point2D.Float(x - pad, y - pad), null);
        int ix = (int)Math.floor(p.getX()), iy = (int)Math.floor(p.getY());

        float fx = Math.round((p.getX() - ix) * 16) / 16f, fy = Math.round((p.getY() - iy) * 16) / 16f;
        String key = w + "|" + h + "|" + radius + "|" + dy + "|" + blur + "|" + spread + "|" + argb + "|" + scale + "|" + fx + "|" + fy;
        BufferedImage img = SHADOWS.get(key);
        if (img == null) {
            float q = Math.min(scale, 16 / Math.max(blur, 1));
            BufferedImage mask = BoxShadow.mask(w, h, radius, dy, blur, spread, q);
            int rgb = argb & 0xFFFFFF, a = argb >>> 24;
            for (int my = 0; my < mask.getHeight(); my++)
                for (int mx = 0; mx < mask.getWidth(); mx++)
                    mask.setRGB(mx, my, ((mask.getRGB(mx, my) >>> 24) * a / 255) << 24 | rgb);
            img = new BufferedImage((int)Math.ceil(mask.getWidth() / q * scale) + 2, (int)Math.ceil(mask.getHeight() / q * scale) + 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D s = img.createGraphics();
            s.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            s.translate(fx, fy);
            s.scale(scale / q, scale / q);
            s.drawImage(mask, 0, 0, null);
            s.setTransform(new AffineTransform());
            s.translate(fx, fy);
            s.scale(scale, scale);
            s.setComposite(AlphaComposite.DstOut);
            s.fill(new RoundRectangle2D.Float(pad, pad, w, h, radius * 2, radius * 2));
            s.dispose();
            if (SHADOWS.size() > 16) SHADOWS.clear();
            SHADOWS.put(key, img);
        }
        g.setTransform(new AffineTransform());
        g.drawImage(img, ix, iy, null);
        g.setTransform(t);
    }

    static float text(Graphics2D g, String s, float x, float top, int weight, float size, int argb) {
        Font f = font(weight, size);
        g.setFont(f);
        color(g, argb);
        FontMetrics m = g.getFontMetrics();
        g.drawString(s, x, top + m.getLeading() / 2f + m.getAscent());
        return (float)f.getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    static float width(Graphics2D g, String s, int weight, float size) {
        return (float)font(weight, size).getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    static float lineHeight(Graphics2D g, int weight, float size) {
        return g.getFontMetrics(font(weight, size)).getHeight();
    }

    static String ellipsize(Graphics2D g, String s, int weight, float size, float max) {
        if (width(g, s, weight, size) <= max) return s;
        String dots = "…";
        int n = s.length();
        while (n > 0 && width(g, s.substring(0, n) + dots, weight, size) > max) n--;
        return s.substring(0, n) + dots;
    }

    private LockerStyle() {}
}
