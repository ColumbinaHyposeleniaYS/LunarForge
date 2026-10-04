package com.example.lunarforge.module.modules.mechanic;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;

final class CrosshairCustom {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Crosshair Outline");
        t.setDaemon(true);
        return t;
    });

    private final CrosshairChild owner;
    private final String key;
    private volatile BufferedImage image, outline;

    private volatile long outlineAsked = -1L;
    private volatile boolean outlineBuilding;

    CrosshairCustom(CrosshairChild owner, String key) {
        this.owner = owner;
        this.key = key.toLowerCase();
    }

    void reload() {
        image = pixels();
        if (owner.outlineOn()) outlineAsked = System.currentTimeMillis();
        else outline = null;
    }

    void reset() {
        image = null;
        outline = null;
        outlineAsked = -1L;
        CrosshairGl.INSTANCE.release(key);
        CrosshairGl.INSTANCE.release(key + "_outline");
    }

    BufferedImage image() {
        if (image == null) image = pixels();
        return image;
    }

    BufferedImage outline() {
        long asked = outlineAsked;
        if (asked != -1L && !outlineBuilding && System.currentTimeMillis() - asked >= 500L) {
            outlineAsked = -1L;
            outlineBuilding = true;
            final BufferedImage base = image();
            WORKER.execute(() -> {
                BufferedImage built = outlineOf(base);
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    outline = built;
                    outlineBuilding = false;
                });
            });
        }
        return outlineAsked == -1L && !outlineBuilding ? outline : null;
    }

    void draw(CrosshairSurface s, float x, float y, float scale, CrosshairPaint paint) {
        BufferedImage img = image();
        if (img == null) return;
        int n = owner.grid().size.size;
        s.push();
        s.translate(x, y);
        float shrink = (n + 1) / 16.0f;
        if (shrink > 1.0f) shrink = 1.0f + 1.0f / shrink;
        scale /= Math.max(1.0f, shrink);
        s.scale(scale, scale);
        float lo = (int)Math.floor(n / 2.0f), hi = (int)Math.ceil(n / 2.0f);
        s.image(key, img, -lo, -lo, hi + lo, hi + lo, paint.colorAt(x - hi, y - hi), paint.colorAt(x + hi, y - hi),
            paint.colorAt(x + hi, y + hi), paint.colorAt(x - lo, y + hi), paint.vanilla);
        BufferedImage line = outline();
        CrosshairPaint outlinePaint = owner.outlinePaint();
        if (outlinePaint != null && line != null) {
            float olo = (int)Math.floor((n + 2) / 2.0f), ohi = (int)Math.ceil((n + 2) / 2.0f);
            s.image(key + "_outline", line, -olo, -olo, ohi + olo, ohi + olo, outlinePaint.colorAt(x - ohi, y - ohi),
                outlinePaint.colorAt(x + ohi, y - ohi), outlinePaint.colorAt(x + ohi, y + ohi), outlinePaint.colorAt(x - olo, y + ohi), false);
        }
        s.pop();
    }

    private BufferedImage pixels() {
        CrosshairGrid grid = owner.grid();
        int n = grid.size.size;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < n * n; i++) if (grid.data[i]) img.setRGB(i % n, i / n, -1);
        return img;
    }

    private BufferedImage outlineOf(BufferedImage base) {
        int n = base.getWidth(), unit = 25, size = (n + 2) * unit, inner = n * unit;
        float f = owner.outlineWidth();
        int a = (int)Math.ceil((1.0f - f) * unit), b = unit + (int)Math.floor(f * unit);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        int[][] at = {{a, a}, {a, b}, {b, a}, {b, b}, {unit, a}, {unit, b}, {a, unit}, {b, unit}};
        for (int[] p : at) g.drawImage(base, p[0], p[1], inner + p[0], inner + p[1], 0, 0, n, n, null);
        g.dispose();
        BufferedImage self = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        g = self.createGraphics();
        g.drawImage(base, unit, unit, inner + unit, inner + unit, 0, 0, n, n, null);
        g.dispose();
        for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) if (self.getRGB(i, j) == -1) out.setRGB(i, j, 0);
        return out;
    }
}
