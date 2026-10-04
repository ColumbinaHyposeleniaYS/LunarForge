package com.example.lunarforge.gui.ui;

public final class LunarSlider {
    public static final float HEIGHT = 14;

    private static final long DURATION = 500;

    private LunarSlider() {}

    public static float track(float width) { return track(width, HEIGHT); }
    public static float track(float width, float height) { return width - height / 3 * 2; }

    public static float pad(float width) { return pad(width, HEIGHT); }
    public static float pad(float width, float height) { return (width - track(width, height)) / 2; }

    public static double valueAt(float mouseX, float x, float width, float min, float max, boolean integer, boolean shift) {
        return valueAt(mouseX, x, width, HEIGHT, min, max, integer, shift);
    }

    public static double valueAt(float mouseX, float x, float width, float height, float min, float max, boolean integer, boolean shift) {
        float f = (mouseX - x - pad(width, height)) / track(width, height);
        double step = 0.01, v;
        if (f <= 0.01) v = min;
        else if (f >= 0.99) v = max;
        else {
            v = min + f * (max - min);
            if (shift) {
                double d = Math.log10(max - min) + 0.001;
                if (d < 0) d = 0;
                step = Math.pow(10, (int)d) / 10;
            }
        }
        v = Math.round(v / step) * step;
        return integer ? Math.round(v) : (double)(float)v;
    }

    public static int[] ticks(float min, float max, boolean wholeNumbers, int roundTo) {
        int marks = 1, sub = 0;
        if (Math.round((max - min) * 100f) % 100 == 0) {
            if (roundTo >= 2) { marks = Math.round(max - min); sub = roundTo; }
            else if (wholeNumbers) marks = Math.round(max - min);
        }
        if (marks > 20 || marks < 1) marks = 1;
        return new int[]{marks, sub};
    }

    public static final class Knob {
        double from, to;

        long start;

        float fade;
        private long fadeTime;

        public Knob(double value) { from = value; to = value; }

        float progress(long now) {
            if (start == 0) return 0;
            if (start + DURATION - now <= 0) return 1;
            float t = (now - start) / (float)DURATION;
            return t < .5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
        }

        boolean finished(long now) { return start + DURATION - now <= 0; }

        void retarget(double value, long now) {
            float f = progress(now), f2 = f;
            if (finished(now)) { start = now; from = to; to = value; return; }
            if (f > .5f) { start = now - DURATION / 2; f = .5f; }
            double d = from + (to - from) * f2;
            from = (d - value * f) / (1 - f);
            to = value;
        }

        public float position(double value, float min, float max, long now) {
            if (max <= min) return 0;
            if (Math.abs(value - to) > 0.01f) retarget(value, now);
            float target = (float)((value - min) / (max - min)), origin = (float)((from - min) / (max - min));
            return origin - (origin - target) * progress(now);
        }

        public boolean moving(long now) { return start != 0 && !finished(now); }

        public int fade(boolean held, long now) {
            if (fadeTime != 0) fade = Math.max(0, Math.min(255, fade + (held ? 1 : -1) * 40 * (now - fadeTime) / 50f));
            fadeTime = now;
            return (int)fade;
        }
    }
}
