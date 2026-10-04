package com.example.lunarforge.util;

public final class Anim {
    private final long durationMs;
    private final float exponent;
    private long startTime;
    private boolean active = true, reversed;

    public Anim(long durationMs) { this(durationMs, 2.0f); }

    public Anim(long durationMs, float exponent) {
        this.durationMs = durationMs;
        this.exponent = exponent;
    }

    private long remaining() { return startTime + durationMs - System.currentTimeMillis(); }

    private float raw() {
        float f = (float)(durationMs - remaining()) / (float)durationMs;
        if (reversed) f = 1.0f - f;
        return (float)Math.pow(f, exponent);
    }

    public void start() { startTime = System.currentTimeMillis(); active = true; }

    public void stop() { startTime = 0L; active = false; }

    public boolean started() { return startTime != 0L; }

    public boolean finished() { return remaining() <= 0L && active; }

    public boolean running() { return startTime != 0L && remaining() > 0L; }

    public void reversed(boolean r) { reversed = r; }

    public boolean reversed() { return reversed; }

    public float progress() {
        if (startTime == 0L) return 0.0f;
        if (finished()) return reversed ? 0.0f : 1.0f;
        return raw();
    }
}
