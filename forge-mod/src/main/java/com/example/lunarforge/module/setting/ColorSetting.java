package com.example.lunarforge.module.setting;

import com.example.lunarforge.module.ModuleManager;
import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public final class ColorSetting extends Setting<Integer> {
    private boolean alpha = true, chroma = true, chromaOn;

    public ColorSetting(String key, int argb) { super(key, argb); }

    public ColorSetting noAlpha() { alpha = false; return this; }

    public ColorSetting noChroma() { chroma = false; return this; }

    public ColorSetting chromaOn() { chromaOn = true; return this; }

    public int argb() {
        int c = get();
        return alpha ? c : c | 0xFF000000;
    }

    public boolean chroma() {
        String raw = ModuleManager.store(owner.key(), key + "Chroma", null);
        return chroma && (raw == null ? chromaOn : Boolean.parseBoolean(raw));
    }

    public int color(float position) {
        int base = argb();
        if (!chroma()) return base;
        float speed;
        try { speed = Math.max(1, Math.min(100, Float.parseFloat(ModuleManager.store(owner.key(), key + "ChromaSpeed", "40")))); }
        catch (NumberFormatException e) { speed = 40; }
        float[] hsb = Color.RGBtoHSB(base >> 16 & 255, base >> 8 & 255, base & 255, null);
        double d = (100.1f - speed) / 100.0f;
        long now = System.nanoTime();
        float hue;
        if ("SHIFT".equals(ModuleManager.store(owner.key(), key + "ChromaType", "WAVE"))) {
            hue = (float)(now / (1.0E10 * d) % 1.0);
        } else {
            ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
            float offset = res.getScaledWidth() + res.getScaledHeight() - position;
            hue = (float)((now + (double)offset * (2.0E7 * d)) / (1.0E10 * d) % 1.0);
        }
        return base & 0xFF000000 | Color.HSBtoRGB(hue, hsb[1], hsb[2]) & 0xFFFFFF;
    }

    public int color() { return color(0); }

    @Override protected Integer parse(String raw) {
        String v = raw.trim();
        return v.matches("[0-9A-Fa-f]{8}") ? (int)Long.parseLong(v, 16) : null;
    }

    @Override protected String format(Integer value) { return String.format(Locale.ROOT, "%08X", value); }

    @Override protected String kind() { return "color"; }

    @Override protected void describe(Map<String, Object> fields) {
        fields.put("alpha", alpha);
        fields.put("chroma", chroma);
        fields.put("chromaOn", chromaOn);
    }
}
