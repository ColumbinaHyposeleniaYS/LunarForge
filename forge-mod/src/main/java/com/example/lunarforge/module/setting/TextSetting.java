package com.example.lunarforge.module.setting;

import java.util.Map;

public final class TextSetting extends Setting<String> {
    private int maxLength;

    public TextSetting(String key, String defaultValue) { super(key, defaultValue); }

    public TextSetting(String key) { this(key, ""); }

    public TextSetting maxLength(int n) { maxLength = n; return this; }

    public boolean isEmpty() { return get().isEmpty(); }

    @Override protected String parse(String raw) { return raw; }
    @Override protected String kind() { return "string"; }

    @Override protected void describe(Map<String, Object> fields) {
        if (maxLength > 0) fields.put("maxLength", maxLength);
    }
}
