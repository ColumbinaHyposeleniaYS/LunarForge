package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.gui.LunarGfx;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.setting.KeySetting;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class QuickplayOptionsScreen extends GuiScreen {
    private final LunarGfx gfx = new LunarGfx("quickplay_options");
    private final GuiScreen parent;
    private final QuickplayGame game;
    private final KeySetting key;
    private final QuickplayScreen.Fade doneOuter = new QuickplayScreen.Fade(0x40252525, 0x45FFFFFF);
    private boolean listening;
    private float x, y;
    private static final float W = 200.0f, H = 98.0f;

    QuickplayOptionsScreen(GuiScreen parent, QuickplayGame game) {
        this.parent = parent;
        this.game = game;
        this.key = ModuleQuickplay.instance.keyFor(game.key());
    }

    @Override public void initGui() {
        gfx.setScale(new ScaledResolution(mc).getScaleFactor());
        parent.setWorldAndResolution(mc, width, height);
        x = width / 2.0f - W / 2.0f;
        y = height / 2.0f - H / 2.0f;
    }

    private boolean favorite() { return ModuleQuickplay.instance.favorites().contains(game.key()); }

    @Override public void drawScreen(int mx, int my, float partialTicks) {
        parent.drawScreen(-1, -1, partialTicks);

        LunarGfx.rect(x, y + 24.0f, x + W, y + 24.5f, 0x20FFFFFF);
        gfx.roundRect(x, y + 1.0f, W, 23.0f, 5.0f, 0x25000000);
        LunarGfx.rect(x, y + H - 30.0f, x + W, y + H - 29.5f, 0x20FFFFFF);
        gfx.roundRect(x, y + H - 29.5f, W, 28.5f, 5.0f, 0x45000000);
        gfx.roundOutline(x - 1.0f, y, W + 2.0f, H, 4.0f, 1, 0x40000000);
        gfx.roundOutline(x, y + 1.0f, W, H - 2.0f, 3.0f, 1, 0x20FFFFFF);
        gfx.roundRect(x, y + 1.0f, W, H - 2.0f, 5.0f, 0xBF000000);
        gfx.text(game.name() + " Options", QuickplayScreen.LIGHT, 22, x + 8.0f, y + 6.0f, -1);

        float rx = x + 8.0f, rw = W - 16.0f;
        gfx.text("Keybind", QuickplayScreen.LIGHT, 18, rx, y + 31.5f, 0xFFC1C0BE);
        String shown = listening ? "..." : key.get().equals("NONE") ? "None" : key.get();
        float bw = Math.max(40.0f, gfx.textWidth(shown, QuickplayScreen.MEDIUM, 13) + 10.0f), bx = rx + rw - bw;
        boolean overKey = mx >= bx && mx < bx + bw && my >= y + 30 && my < y + 42;
        gfx.roundRect(bx, y + 30.0f, bw, 12.0f, 4.0f, overKey || listening ? 0x35FFFFFF : 0x20FFFFFF);
        gfx.text(shown, QuickplayScreen.MEDIUM, 13, bx + bw / 2.0f - gfx.textWidth(shown, QuickplayScreen.MEDIUM, 13) / 2.0f, y + 31.5f, 0xFFFFFFFF);
        boolean fav = favorite();
        gfx.roundRect(rx, y + 46.0f, 28.0f, 12.0f, 6.0f, fav ? 0xFF4F94FC : 0x40FFFFFF);
        gfx.roundRect(fav ? rx + 17.0f : rx + 1.0f, y + 47.0f, 10.0f, 10.0f, 5.0f, 0xFFFFFFFF);
        gfx.text(LunarLang.get("settings", "favorite"), QuickplayScreen.LIGHT, 18, rx + 36.0f, y + 46.5f, 0xFFC1C0BE);

        float dx = x + W - 45.0f, dy = y + H - 23.0f;
        boolean overDone = mx >= dx && mx < dx + 40 && my >= dy && my < dy + 15;
        gfx.roundRect(dx, dy, 40.0f, 15.0f, 5.0f, doneOuter.color(overDone));
        gfx.roundOutline(dx, dy, 40.0f, 15.0f, 4.0f, 1, 0x40252525);
        String done = LunarLang.get("gui.components", "done").toUpperCase();
        gfx.text(done, QuickplayScreen.BOLD, 14, dx + 20.0f - gfx.textWidth(done, QuickplayScreen.BOLD, 14) / 2.0f, dy + 3.5f, 0xFFBEC3BD);
    }

    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        float rx = x + 8.0f, rw = W - 16.0f;
        String shown = key.get();
        float bw = Math.max(40.0f, gfx.textWidth(shown, QuickplayScreen.MEDIUM, 13) + 10.0f), bx = rx + rw - bw;
        if (mx >= bx && mx < bx + bw && my >= y + 30 && my < y + 42) { listening = true; return; }
        if (mx >= rx && mx < rx + rw && my >= y + 45 && my < y + 59) {
            boolean on = !favorite();
            ModuleQuickplay.instance.favorite(game.key(), on);
            return;
        }
        float dx = x + W - 45.0f, dy = y + H - 23.0f;
        if (mx >= dx && mx < dx + 40 && my >= dy && my < dy + 15) close();
    }

    private void close() {
        mc.displayGuiScreen(parent instanceof QuickplayScreen ? new QuickplayScreen(null, null) : parent);
    }

    @Override protected void keyTyped(char c, int code) throws IOException {
        if (listening) {
            key.set(code == Keyboard.KEY_ESCAPE ? "NONE" : Keyboard.getKeyName(code));
            listening = false;
            return;
        }
        if (code == Keyboard.KEY_ESCAPE) close();
    }

    @Override public void onGuiClosed() { gfx.release(); }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void handleMouseInput() throws IOException {
        if (listening && Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) {
            key.set("MOUSE" + (Mouse.getEventButton() + 1));
            listening = false;
            return;
        }
        super.handleMouseInput();
    }
}
