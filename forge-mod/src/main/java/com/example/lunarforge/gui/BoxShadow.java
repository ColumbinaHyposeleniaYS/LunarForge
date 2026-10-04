package com.example.lunarforge.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public final class BoxShadow {
    private BoxShadow() {}

    public static float pad(float blur) { return (float)Math.ceil(blur * 1.5f) + 2; }

    public static BufferedImage mask(float w, float h, float radius, float dy, float blur, float spread, float scale) {
        float sigma = blur / 2 * scale, pad = pad(blur) * scale;
        int pw = (int)Math.ceil(w * scale + pad * 2 + Math.max(0, spread) * scale);
        int ph = (int)Math.ceil(h * scale + pad * 2 + Math.max(0, dy + spread) * scale);
        float[] alpha = new float[pw * ph];
        BufferedImage shape = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = shape.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        float sx = pad - spread * scale, sy = pad - spread * scale + dy * scale;
        float sw = (w + spread * 2) * scale, sh = (h + spread * 2) * scale, sr = Math.max(0, radius + spread) * scale * 2;
        g.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, sr, sr));
        g.dispose();
        for (int i = 0; i < alpha.length; i++) alpha[i] = (shape.getRGB(i % pw, i / pw) >>> 24) / 255f;
        alpha = blur(blur(alpha, pw, ph, sigma, true), pw, ph, sigma, false);
        BufferedImage image = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < alpha.length; i++) image.setRGB(i % pw, i / pw, (Math.min(255, Math.round(alpha[i] * 255)) << 24) | 0xFFFFFF);
        return image;
    }

    private static float[] blur(float[] src, int w, int h, float sigma, boolean horizontal) {
        if (sigma <= 0) return src;
        int radius = (int)Math.ceil(sigma * 3);
        float[] kernel = new float[radius * 2 + 1]; float sum = 0;
        for (int i = -radius; i <= radius; i++) { kernel[i + radius] = (float)Math.exp(-(i * i) / (2 * sigma * sigma)); sum += kernel[i + radius]; }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= sum;
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float acc = 0;
            for (int k = -radius; k <= radius; k++) {
                int sx = horizontal ? x + k : x, sy = horizontal ? y : y + k;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
                acc += src[sy * w + sx] * kernel[k + radius];
            }
            out[y * w + x] = acc;
        }
        return out;
    }
}
