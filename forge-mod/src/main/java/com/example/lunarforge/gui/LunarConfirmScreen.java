package com.example.lunarforge.gui;

import com.example.lunarforge.gui.ui.LunarLang;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

public final class LunarConfirmScreen extends GuiScreen {
    private final LunarGfx gfx = new LunarGfx("confirm");
    private final String name;
    private final Consumer<Boolean> answer;
    private long flip = System.currentTimeMillis();
    private boolean red;

    public LunarConfirmScreen(String name, Consumer<Boolean> answer) {
        this.name = name;
        this.answer = answer;
    }

    private String text(String key) { return LunarLang.get("gui." + name, key); }

    @Override public void initGui() { gfx.setScale(new ScaledResolution(mc).getScaleFactor()); }

    private float[] understood() { return new float[]{width / 2.0f - 100.0f, height / 2.0f - 50.0f + 60.0f, 200.0f, 16.0f}; }

    private float[] cancel() { return new float[]{width / 2.0f - 100.0f, height / 2.0f - 50.0f + 80.0f, 200.0f, 16.0f}; }

    @Override public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        long now = System.currentTimeMillis();
        if (now - flip >= 2000L) { flip = now; red = !red; }
        float t = Math.min(1.0f, (now - flip) / 2000.0f);
        int warning = red ? LunarGfx.mix(0xFFFFFFFF, 0xFFFF3333, t) : LunarGfx.mix(0xFFFF3333, 0xFFFFFFFF, t);
        float x = width / 2.0f - 160.0f, y = height / 2.0f - 60.0f, w = 320.0f, h = 118.0f;
        LunarGfx.rect(x, y + 24.0f, x + w, y + 24.5f, 0x20FFFFFF);
        gfx.roundRect(x, y + 1.0f, w, 23.0f, 5.0f, 0x25000000);
        gfx.roundOutline(x - 1.0f, y, w + 2.0f, h, 4.0f, 1, 0x40000000);
        gfx.roundOutline(x, y + 1.0f, w, h - 2.0f, 3.0f, 1, 0x20FFFFFF);
        gfx.roundRect(x, y + 1.0f, w, h - 2.0f, 5.0f, 0x80000000);
        float cx = width / 2.0f, cy = height / 2.0f - 54.0f;
        centered(text("warning"), LunarGfx.ROBOTO_LIGHT, 22, cx, cy, warning);
        centered(text("lineOne"), LunarGfx.ROBOTO_MEDIUM, 13, cx, cy + 30.0f, -1);
        if (!text("lineTwo").equals("lineTwo")) centered(text("lineTwo"), LunarGfx.ROBOTO_MEDIUM, 13, cx, cy + 40.0f, -1);
        button(cancel(), "cancel", true, mx, my);
        button(understood(), "understood", false, mx, my);
    }

    private void centered(String s, String font, float size, float cx, float y, int color) {
        gfx.text(s, font, size, cx - gfx.textWidth(s, font, size) / 2.0f, y, color);
    }

    private void button(float[] r, String key, boolean active, int mx, int my) {
        boolean lit = active || mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        gfx.roundRect(r[0], r[1], r[2], r[3], 5.0f, lit ? 0x45FFFFFF : 0x20FFFFFF);
        gfx.roundOutline(r[0], r[1], r[2], r[3], 4.0f, 1, 0x40252525);
        gfx.roundOutline(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 2.75f, 1, 0x20FFFFFF);
        String label = LunarLang.get("gui.components", key).toUpperCase(Locale.ROOT).replace("", " ").trim();
        float lw = gfx.textWidth(label, "roboto-bold.ttf", 14);
        float ty = r[1] + r[3] / 2.0f - gfx.textHeight("roboto-bold.ttf", 14) / 2.0f;
        gfx.text(label, "roboto-bold.ttf", 14, r[0] + r[2] / 2.0f - lw / 2.0f + 1, ty + 1, 0x20000000);
        gfx.text(label, "roboto-bold.ttf", 14, r[0] + r[2] / 2.0f - lw / 2.0f, ty, 0xFFBEC3BD);
    }

    private static boolean in(float[] r, int x, int y) { return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]; }

    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        if (in(cancel(), mx, my)) answer.accept(false);
        else if (in(understood(), mx, my)) answer.accept(true);
    }

    @Override protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) answer.accept(false);
    }

    @Override public void onGuiClosed() { gfx.release(); }

    @Override public boolean doesGuiPauseGame() { return false; }
}
