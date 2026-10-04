package com.example.lunarforge.module.modules.hud.keystrokes;

import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.setting.ChoiceSetting;
import java.util.BitSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;

public final class KeyAnimations {
    private KeyAnimations() {}

    public interface Shape {
        void draw(float w, float h, float f, int c);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    public enum Animation implements ChoiceSetting.Option {
        FILL("Fill", Fill::new), SMOOTH_FILL("Smooth Fill", SmoothFill::new), RIPPLE("Ripple", Ripple::new),
        COLLAPSE("Collapse", Collapse::new), ZIPPER("Zipper", Zipper::new), TRIANGULATE("Triangulate", Triangulate::new),
        SPIRAL("Spiral", Spiral::new), SAND("Sand", Sand::new), CIRCULAR_FILL("Circular Fill", CircularFill::new),
        MULTI_SQUARE_FILL("Multi Square Fill", MultiSquareFill::new), HEAD_FILL("Head Fill", HeadFill::new),
        CROSS_COLLAPSE("Cross Collapse", CrossCollapse::new), CROSS_GROW("Cross Grow", CrossGrow::new),
        HORIZONTAL_COLLAPSE("Horizontal Collapse", HorizontalCollapse::new), HORIZONTAL_GROW("Horizontal Grow", HorizontalGrow::new),
        VERTICAL_COLLAPSE("Vertical Collapse", VerticalCollapse::new), VERTICAL_GROW("Vertical Grow", VerticalGrow::new),
        DIAGONAL_COLLAPSE("Diagonal Collapse", DiagonalCollapse::new), DIAGONAL_GROW("Diagonal Grow", DiagonalGrow::new);

        private final String id;
        private final Supplier<Shape> factory;
        Animation(String id, Supplier<Shape> factory) { this.id = id; this.factory = factory; }
        public Shape create() { return factory.get(); }
        @Override public String langId() { return id; }
    }

    public enum Type implements ChoiceSetting.Option {
        SYNCED("synced"), STACKED("stacked");
        private final String id;
        Type(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum Timer implements ChoiceSetting.Option {
        HALF("half", Half::new), FULL("complete", Full::new);
        private final String id;
        private final Function<Double, Progress> factory;
        Timer(String id, Function<Double, Progress> factory) { this.id = id; this.factory = factory; }
        public Progress create(double seconds) { return factory.apply(seconds); }
        @Override public String langId() { return id; }
    }

    public enum Timing implements ChoiceSetting.Option {
        EASE(0.25f, 0.1f, 0.25f, 1.0f, "ease"), LINEAR(0, 0, 1, 1, "linear"), EASE_IN(0.42f, 0, 1, 1, "ease_in"),
        EASE_OUT(0, 0, 0.58f, 1, "ease_out"), EASE_IN_OUT(0.42f, 0, 0.58f, 1, "ease_in_out");
        private final float x1, y1, x2, y2;
        private final String id;
        Timing(float x1, float y1, float x2, float y2, String id) { this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.id = id; }

        public float compute(float t) {
            float u = 1 - t;
            double y = u * u * 3 * t * y1 + u * 3 * t * t * y2 + t * t * t;
            return clamp((float)y, 0, 1);
        }
        @Override public String langId() { return id; }
    }

    public interface Progress {
        float progress();
        float raw();
        void press();
        void release();
        default boolean done() { return raw() == 2.0f; }
    }

    static final class Full implements Progress {
        private final long start = System.nanoTime(), length;
        Full(double seconds) { length = (long)(seconds * 1.0E9); }
        @Override public float progress() { float f = raw(); return f > 1 ? 2 - f : f; }
        @Override public float raw() { return clamp((float)((double)(System.nanoTime() - start) / length * 2.0), 0, 2); }
        @Override public void press() {}
        @Override public void release() {}
    }

    static final class Half implements Progress {
        private final long start = System.nanoTime(), length;
        Half(double seconds) { length = (long)(seconds * 1.0E9); }
        @Override public float progress() { return clamp((float)((double)(System.nanoTime() - start) / length), 0, 1); }
        @Override public float raw() { return progress(); }
        @Override public void press() {}
        @Override public void release() {}
        @Override public boolean done() { return progress() == 1.0f; }
    }

    static final class Held implements Progress {
        private final long length;
        private long start;
        private double offset;
        private boolean pressed;
        Held(double seconds) { length = (long)(seconds * 1.0E9); }
        @Override public void press() { offset = progress(); pressed = true; start = System.nanoTime(); }
        @Override public void release() { offset = 1.0 + (1.0f - progress()); pressed = false; start = System.nanoTime(); }
        @Override public float progress() { float f = raw(); return f > 1 ? 2 - f : f; }
        @Override public float raw() {
            double d = (double)(System.nanoTime() - start) / length * 2.0 + offset;
            if (d > 1.0 && pressed) return 1.0f;
            if (d > 2.0) return 2.0f;
            return (float)d;
        }
    }

    static final class Fill implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f, fh = h * f;
            Draw.rect((w - fw) / 2, (h - fh) / 2, fw, fh, c);
        }
    }

    static final class SmoothFill implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f, fh = h * f, m = Math.min(fw, fh);
            float r = f > 0.5 ? m / 2 * (1 - f) / 0.5f : m / 2;
            Draw.roundedRect((w - fw) / 2, (h - fh) / 2, fw, fh, r, c);
        }
    }

    static final class Ripple implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f, fh = h * f;
            Draw.roundedRect((w - fw) / 2, (h - fh) / 2, fw, fh, Math.min(fw, fh) / 2, c);
        }
    }

    static final class Collapse implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f, fh = h * f;
            Draw.rect(0, 0, fw / 2, h, c);
            Draw.rect(w - fw / 2, 0, fw / 2, h, c);
            Draw.rect(fw / 2, 0, w - fw, fh / 2, c);
            Draw.rect(fw / 2, h - fh / 2, w - fw, fh / 2, c);
        }
    }

    static final class Zipper implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            boolean wide = w / 1.5 > h;
            float left = wide ? w : h, x = 0, y = 0;
            int n = (int)(8 + (wide ? w / h : h / w));
            float step = left / n, part = 1.0f / n;
            for (int i = 0; i < n; i++) {
                float p = clamp(f / (part * (i + 1)), 0, 1);
                if (wide) {
                    float half = h * p / 2;
                    Draw.rect(x, y, Math.min(step, left), half, c);
                    Draw.rect(x, h - half, Math.min(step, left), half, c);
                    x += step;
                } else {
                    float half = w * p / 2;
                    Draw.rect(x, y, half, Math.min(step, left), c);
                    Draw.rect(w - half, y, half, Math.min(step, left), c);
                    y += step;
                }
                left -= step;
            }
        }
    }

    static final class Triangulate implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float x = w / 2, y = h / 2;
            Draw.triangle(x, y, x, y - h / 2, x - w / 2, y - h / 2, c);
            if (f > 0.125f) Draw.triangle(x, y, x + w / 2, y - h / 2, x, y - h / 2, c);
            if (f > 0.25f) Draw.triangle(x, y, x + w / 2, y, x + w / 2, y - h / 2, c);
            if (f > 0.375f) Draw.triangle(x, y, x + w / 2, y + h / 2, x + w / 2, y, c);
            if (f > 0.5f) Draw.triangle(x, y, x, y + h / 2, x + w / 2, y + h / 2, c);
            if (f > 0.675f) Draw.triangle(x, y, x - w / 2, y + h / 2, x, y + h / 2, c);
            if (f > 0.75f) Draw.triangle(x, y, x - w / 2, y, x - w / 2, y + h / 2, c);
            if (f > 0.875f) Draw.triangle(x, y, x - w / 2, y - h / 2, x - w / 2, y, c);
        }
    }

    static final class Spiral implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            GlStateManager.pushMatrix();
            float cols = 5 * Math.round(w / h), rowsF = 5;
            GlStateManager.scale(w / cols, h / rowsF, 1);
            int n3 = (int)cols, n4 = (int)rowsF, total = n3 * n4;
            int left = (int)Math.ceil(total * f), all = left, y = 0, x = 0, step = 0;
            while (left > 0) {
                int done = all - left;
                switch (step % 4) {
                    case 0: {
                        float p = clamp((f - (float)done / total) / ((float)n3 / total), 0, 1);
                        Draw.rect(x, y, n3 * p, 1, c);
                        ++y; --n4; left -= n3;
                        break;
                    }
                    case 1: {
                        float p = clamp((f - (float)done / total) / ((float)n4 / total), 0, 1);
                        Draw.rect(x + n3 - 1, y, 1, n4 * p, c);
                        --n3; left -= n4;
                        break;
                    }
                    case 2: {
                        float p = clamp((f - (float)done / total) / ((float)n3 / total), 0, 1);
                        Draw.rect(x, y + n4 - 1, n3 * p, 1, c);
                        --n4; left -= n3;
                        break;
                    }
                    default: {
                        float p = clamp((f - (float)done / total) / ((float)n4 / total), 0, 1);
                        Draw.rect(x, y, 1, n4 * p, c);
                        ++x; --n3; left -= n4;
                        break;
                    }
                }
                ++step;
                if (n3 <= 0 || n4 <= 0) break;
            }
            GlStateManager.popMatrix();
        }
    }

    static final class Sand implements Shape {
        private final BitSet grid = new BitSet(64);
        private long last = System.nanoTime();
        private int count;

        private static int index(int x, int y) { return y * 8 + x; }

        @Override public void draw(float w, float h, float f, int c) {
            long now = System.nanoTime();
            while (now - last > 10000000L) {
                fall();
                if (count < 64) {
                    int x = ThreadLocalRandom.current().nextInt(2) + 3;
                    if (grid.get(index(x, 7))) {
                        do x = ThreadLocalRandom.current().nextInt(8); while (grid.get(index(x, 7)) && grid.cardinality() < 64);
                    }
                    grid.set(index(x, 7));
                    ++count;
                }
                last += 10000000L;
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(w / 8, h / 8, 1);
            GlStateManager.rotate(180, 0, 0, 1);
            GlStateManager.translate(-8, -8, 0);
            for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) if (grid.get(index(i, j))) Draw.rect(i, j, 1, 1, c);
            GlStateManager.popMatrix();
        }

        private void fall() {
            for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
                if (!grid.get(index(i, j)) || j == 0) continue;
                if (!grid.get(index(i, j - 1))) { grid.set(index(i, j - 1)); grid.clear(index(i, j)); continue; }
                if (i > 0 && !grid.get(index(i - 1, j - 1))) { grid.set(index(i - 1, j - 1)); grid.clear(index(i, j)); continue; }
                if (i < 7 && !grid.get(index(i + 1, j - 1))) { grid.set(index(i + 1, j - 1)); grid.clear(index(i, j)); }
            }
        }
    }

    static final class CircularFill implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float cx = w / 2, cy = h / 2;
            Draw.triangle(cx, cy, clamp(w * f * 4, 0, w), 0, 0, 0, c);
            if (f > 0.25f) Draw.triangle(cx, cy, w, clamp(h * (f - 0.25f) * 4, 0, h), w, 0, c);
            if (f > 0.5f) Draw.triangle(cx, cy, w - clamp(w * (f - 0.5f) * 4, 0, w), h, w, h, c);
            if (f > 0.75f) Draw.triangle(cx, cy, 0, h - clamp(h * (f - 0.75f) * 4, 0, h), 0, h, c);
        }
    }

    static final class MultiSquareFill implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            Draw.rect(0, 0, w / 2 * f, h / 2, c);
            Draw.rect(w / 2, 0, w / 2, h / 2 * f, c);
            float fw = w / 2 * f;
            Draw.rect(w - fw, h / 2, fw, h / 2, c);
            float fh = h / 2 * f;
            Draw.rect(0, h - fh, w / 2, fh, c);
        }
    }

    static final class HeadFill implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return;
            ResourceLocation skin = player.getLocationSkin();
            if (skin == null) return;
            float fw = w * f, fh = h * f, x = (w - fw) / 2, y = (h - fh) / 2;
            Draw.textureRegion(skin, x, y, fw, fh, 0.125f, 0.125f, 0.25f, 0.25f);
            if (player.isWearing(EnumPlayerModelParts.HAT)) Draw.textureRegion(skin, x, y, fw, fh, 0.625f, 0.125f, 0.75f, 0.25f);
        }
    }

    static final class CrossCollapse implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            if (f == 1.0f) { Draw.rect(0, 0, w, h, c); return; }
            float fw = w * f / 2, fh = h * f / 2;
            Draw.rect(0, 0, fw, fh, c);
            Draw.rect(w - fw, 0, fw, fh, c);
            Draw.rect(0, h - fh, fw, fh, c);
            Draw.rect(w - fw, h - fh, fw, fh, c);
        }
    }

    static final class CrossGrow implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f, fh = h * f, rw = w - fw, rh = h - fh;
            Draw.rect(rw / 2, 0, fw, h, c);
            if (f == 1.0f) return;
            Draw.rect(0, rh / 2, rw / 2, fh, c);
            Draw.rect(w - rw / 2, rh / 2, rw / 2, fh, c);
        }
    }

    static final class HorizontalCollapse implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f;
            Draw.rect(0, 0, fw / 2, h, c);
            Draw.rect(w - fw / 2, 0, fw / 2, h, c);
        }
    }

    static final class HorizontalGrow implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = w * f;
            Draw.rect((w - fw) / 2, 0, fw, h, c);
        }
    }

    static final class VerticalCollapse implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fh = h * f;
            Draw.rect(0, 0, w, fh / 2, c);
            Draw.rect(0, h - fh / 2, w, fh / 2, c);
        }
    }

    static final class VerticalGrow implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fh = h * f;
            Draw.rect(0, (h - fh) / 2, w, fh, c);
        }
    }

    static final class DiagonalCollapse implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float fw = f * w, fh = f * h;
            Draw.triangle(0, 0, 0, fh, fw, 0, c);
            Draw.triangle(w, h - fh, w - fw, h, w, h, c);
        }
    }

    static final class DiagonalGrow implements Shape {
        @Override public void draw(float w, float h, float f, int c) {
            float rw = (1 - f) * w, rh = (1 - f) * h;
            Draw.triangle(rw, 0, w, h - rh, w, 0, c);
            Draw.triangle(0, h, w - rw, h, 0, rh, c);
            if (f != 1.0f) {
                Draw.triangle(rw, 0, 0, rh, w, h - rh, c);
                Draw.triangle(0, rh, w - rw, h, w, h - rh, c);
            }
        }
    }
}
