package com.example.lunarforge.module.setting;

import com.example.lunarforge.gui.ui.MenuWidget;
import java.util.Map;

public final class WidgetSetting extends Setting<String> {
    private final MenuWidget widget;

    public WidgetSetting(String key, String defaultValue, MenuWidget widget) {
        super(key, defaultValue);
        this.widget = widget;
    }

    public MenuWidget widget() { return widget; }

    public int intValue() {
        try { return Integer.parseInt(get().trim()); } catch (NumberFormatException e) { return Integer.parseInt(defaultValue); }
    }

    @Override protected String parse(String raw) { return raw; }
    @Override protected String kind() { return "widget"; }

    @Override protected void describe(Map<String, Object> fields) { fields.put("widget", widget); }
}
