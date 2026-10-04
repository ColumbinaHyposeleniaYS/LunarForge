package com.example.lunarforge.module.setting;

import java.util.Map;

public final class NumberSetting extends Setting<Float> {
    public final float min, max;
    public final boolean integer;

    private int roundTo;

    private NumberSetting(String key, float defaultValue, float min, float max, boolean integer) {
        super(key, defaultValue);
        this.min = min; this.max = max; this.integer = integer;
    }

    public static NumberSetting integer(String key, int defaultValue, int min, int max) {
        return new NumberSetting(key, defaultValue, min, max, true);
    }

    public static NumberSetting decimal(String key, float defaultValue, float min, float max) {
        return new NumberSetting(key, defaultValue, min, max, false);
    }

    public NumberSetting roundTo(int n) { roundTo = n; return this; }

    public float value() { return Math.max(min, Math.min(max, get())); }

    public int intValue() { return Math.round(value()); }

    @Override protected Float parse(String raw) {
        float f = Float.parseFloat(raw.trim());
        return Float.isNaN(f) || Float.isInfinite(f) ? null : f;
    }

    @Override protected String format(Float value) {
        return integer ? String.valueOf(Math.round(value)) : String.valueOf(value);
    }

    @Override protected String kind() { return "number"; }

    @Override protected void describe(Map<String, Object> fields) {
        fields.put("min", min);
        fields.put("max", max);
        fields.put("integer", integer);
        if (roundTo != 0) fields.put("roundTo", roundTo);
    }
}
