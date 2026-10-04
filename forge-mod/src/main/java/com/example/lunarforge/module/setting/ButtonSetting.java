package com.example.lunarforge.module.setting;

import java.util.Map;

public final class ButtonSetting extends Setting<String> {
    private final Runnable action;
    private float width = 70.0f;

    public ButtonSetting(String key, Runnable action) {
        super(key, "");
        this.action = action;
    }

    public ButtonSetting width(float w) { width = w; return this; }

    public void press() { action.run(); }

    @Override protected String parse(String raw) { return raw; }
    @Override protected String kind() { return "button"; }

    @Override protected void describe(Map<String, Object> fields) {
        fields.put("width", width);
    }
}
