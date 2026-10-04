package com.example.lunarforge.gui.ui;

public final class HudOptions {
    public static final String SCALE = "scale", TEXT_SHADOW = "textShadow", BRACKETS = "brackets", BRACKET_COLOR = "bracketColor",
        BACKGROUND = "background", STATIC_WIDTH = "staticBackgroundWidth", STATIC_HEIGHT = "staticBackgroundHeight",
        BACKGROUND_WIDTH = "backgroundWidth", BACKGROUND_HEIGHT = "backgroundHeight", BACKGROUND_COLOR = "backgroundColor",
        BORDER = "border", BORDER_THICKNESS = "borderThickness", BORDER_COLOR = "borderColor", TEXT_COLOR = "textColor";

    public static String defaultValue(String id, String key) {
        OptionCatalog.Node n = OptionCatalog.find(id, key);
        return n == null || n.value.isEmpty() ? null : n.value;
    }

    public static float defaultNumber(String id, String key, float fallback) {
        String v = defaultValue(id, key);
        try { return v == null ? fallback : Float.parseFloat(v); } catch (NumberFormatException e) { return fallback; }
    }

    private HudOptions() {}
}
