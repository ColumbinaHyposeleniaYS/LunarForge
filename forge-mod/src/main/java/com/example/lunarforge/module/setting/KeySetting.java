package com.example.lunarforge.module.setting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeySetting extends Setting<String> {
    private final boolean combo;

    public KeySetting(String key, String defaultKey, boolean combo) {
        super(key, defaultKey);
        this.combo = combo;
    }

    public KeySetting(String key) { this(key, "NONE", false); }

    public boolean isDown() {
        int code = code();
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    private boolean wasDown;

    public boolean pressed(boolean active) {
        boolean down = isDown();
        boolean press = down && !wasDown && active;
        wasDown = down;
        return press;
    }

    public int code() { return code(get()); }

    public static int code(String name) {
        if (name == null || name.isEmpty() || name.equals("NONE")) return Keyboard.KEY_NONE;
        if (name.startsWith("MOUSE")) {
            try { return Integer.parseInt(name.substring(5)) - 1 - 100; } catch (NumberFormatException e) { return Keyboard.KEY_NONE; }
        }
        return Keyboard.getKeyIndex(name);
    }

    @Override protected String parse(String raw) { return raw.trim(); }
    @Override protected String kind() { return combo ? "keycombo" : "key"; }
}
