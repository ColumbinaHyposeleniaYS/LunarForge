package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.gui.LunarGfx;
import com.example.lunarforge.gui.ui.LunarLang;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

public final class QuickplayScreen extends GuiScreen {
    static final String BOLD = "roboto-bold.ttf", MEDIUM = "roboto-medium.ttf", LIGHT = "roboto-light.ttf";

    static final class Fade {
        private final int off, on;
        private boolean hovered;
        private long changed;
        Fade(int off, int on) { this.off = off; this.on = on; }
        int color(boolean hover) {
            long now = System.currentTimeMillis();
            if (hover != hovered) { hovered = hover; changed = now; }
            float t = Math.min(1.0f, (now - changed) / 125.0f);
            return hover ? LunarGfx.mix(off, on, t) : LunarGfx.mix(on, off, t);
        }
    }

    abstract class Element {
        float x, y, w, h;
        final Fade outer = new Fade(0x60252525, 0x99FFFFFF), fill = new Fade(0x80000000, 0xA0000000);
        boolean over(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }

        void body(boolean hover) {
            gfx.roundRect(x, y, w, h, 5, fill.color(hover));
            gfx.roundOutline(x, y, w, h, 4, 1, outer.color(hover));
            gfx.roundOutline(x + 1, y + 1, w - 2, h - 2, 2.75f, 1, 0x20FFFFFF);
        }
        abstract void draw(boolean hover);
        abstract void click(int button);
    }

    final class Tile extends Element {
        final QuickplayGame game;
        final boolean noModes;
        Tile(QuickplayGame game) {
            this.game = game;
            noModes = game.modes() == null || game.modes().isEmpty();
        }
        @Override void draw(boolean hover) {
            body(hover);
            gfx.image(game.iconPath(), x, y, 22, 22, -1);
            shadowed(game.name(), MEDIUM, 13, x + 30, y + 4, 0xBFFFFFFF);
            shadowed(noModes ? "> Go To Lobby" : "Select Mode", BOLD, 10, x + 30, y + 12, 0x7FFFFFFF);
        }
        @Override void click(int button) {
            if (button == 1) { mc.displayGuiScreen(new QuickplayOptionsScreen(QuickplayScreen.this, game)); return; }
            if (noModes) ModuleQuickplay.send(game.command());
            else mc.displayGuiScreen(new QuickplayScreen(new QuickplayScreen(null, null), game));
        }
    }

    final class Button extends Element {
        final GuiScreen back;
        final QuickplayGame parent, game;
        final String command, label;
        final boolean opensModes;
        Button(GuiScreen back, QuickplayGame parent, QuickplayGame game, String command, String label) {
            this.back = back;
            this.parent = parent;
            this.game = game;
            this.command = command;
            this.label = label;
            opensModes = game != null && game.modes() != null && !game.modes().isEmpty() && !command.isEmpty();
        }
        @Override void draw(boolean hover) {
            body(hover);
            String text = opensModes ? "> " + label : label;
            if (gfx.textWidth(text, MEDIUM, 13) + 12.0f > w) shadowed(text, MEDIUM, 10, x + 6, y + 5, 0xBFFFFFFF);
            else shadowed(text, MEDIUM, 13, x + 6, y + 4, 0xBFFFFFFF);
        }
        @Override void click(int button) {
            if (game != null && !opensModes && button == 1) { mc.displayGuiScreen(new QuickplayOptionsScreen(QuickplayScreen.this, game)); return; }
            if (parent == null) {
                if (command.isEmpty()) mc.displayGuiScreen(back);
                else ModuleQuickplay.send(command);
                return;
            }
            if (opensModes) { mc.displayGuiScreen(new QuickplayScreen(QuickplayScreen.this, game, parent.iconPath())); return; }
            ModuleQuickplay.send(command);
        }
    }

    private final LunarGfx gfx = new LunarGfx("quickplay");
    private final GuiScreen back;

    private final QuickplayGame game;
    private final String icon;
    private final List<Element> elements = new ArrayList<Element>();

    private boolean loading;

    QuickplayScreen(GuiScreen back, QuickplayGame game) { this(back, game, game == null ? null : game.iconPath()); }

    QuickplayScreen(GuiScreen back, QuickplayGame game, String icon) {
        this.back = back;
        this.game = game;
        this.icon = icon;
        ModuleQuickplay m = ModuleQuickplay.instance;
        if (game == null) {
            if (m == null || !m.loaded() || m.games() == null || m.games().isEmpty()) { loading = true; return; }
            for (QuickplayGame g : m.games()) elements.add(new Tile(g));
            for (String key : m.favorites()) {
                QuickplayGame g = m.find(key);
                if (g == null) continue;
                String label = g.parent() == null ? g.name() : g.parent().name() + ": " + g.name();
                elements.add(new Button(this, g.parent(), g, g.command(), label));
            }
        } else {
            elements.add(new Button(back, null, null, "", "< " + gui("returnMenu")));
            if (!(back instanceof QuickplayScreen && ((QuickplayScreen)back).game != null)) {
                elements.add(new Button(back, null, game, "/l " + game.key(), gui("lobby")));
            }
            for (QuickplayGame mode : game.modes()) if (!mode.disabled()) elements.add(new Button(this, game, mode, mode.command(), mode.name()));
        }
    }

    private static String gui(String key) { return LunarLang.get("gui.quickPlay", key); }

    @Override public void initGui() {
        gfx.setScale(new ScaledResolution(mc).getScaleFactor());
        if (loading) return;
        float w = 100.0f, gap = 5.0f;
        int across = 4;
        float x0 = width / 2.0f - (w + gap) * across / 2.0f;
        if (game == null) {
            float h = 22.0f, y = height / 2.0f - 120.0f;
            int col = 0;
            for (Element e : elements) {
                if (!(e instanceof Tile)) continue;
                place(e, x0 + col * (w + gap), y, w, h);
                if (col == across - 1) { col = 0; y += h + gap; } else col++;
            }
            col = 0;
            y += 44.0f;
            h = 16.0f;
            for (Element e : elements) {
                if (!(e instanceof Button)) continue;
                place(e, x0 + col * (w + gap), y, w, h);
                if (col == across - 1) { col = 0; y += h + gap; } else col++;
            }
        } else {
            float h = 16.0f, y = height / 2.0f - 60.0f;
            int col = 0;
            for (Element e : elements) {
                place(e, x0 + col * (w + gap), y, w, h);
                if (col == across - 1) { col = 0; y += h + gap; } else col++;
            }
        }
    }

    private static void place(Element e, float x, float y, float w, float h) { e.x = x; e.y = y; e.w = w; e.h = h; }

    @Override public void updateScreen() {
        ModuleQuickplay m = ModuleQuickplay.instance;
        if (loading && m != null && m.loaded()) mc.displayGuiScreen(new QuickplayScreen(null, null));
    }

    @Override public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        float cx = width / 2.0f;
        if (game == null) {
            if (loading) { centered(gui("loadingGames"), BOLD, 18, cx, height / 2.0f - 10.0f, -1); return; }
            centered(gui("chooseGame"), BOLD, 18, cx, height / 2.0f - 140.0f, -1);
            ModuleQuickplay m = ModuleQuickplay.instance;
            if (m != null && !m.favorites().isEmpty()) {
                int n = m.games().size();
                centered("Favorites", BOLD, 18, cx, height / 2.0f - 120.0f + n / 4.0f * 27.0f + 16.0f, -1);
            }
        } else {
            float top = height / 2.0f - 100.0f;
            gfx.image(icon, cx - 16.0f, top - 46.0f, 32, 32, -1);
            centered(game.name(), LIGHT, 38, cx, top - 10.0f, -1);
            centered(gui("chooseGame"), BOLD, 18, cx, top + 15.0f, -1);
            centered(gui("keyBindHint"), MEDIUM, 13, cx, top + 25.0f, 0xE0FFFFFF);
        }
        for (Element e : elements) e.draw(e.over(mx, my));
    }

    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        for (Element e : new ArrayList<Element>(elements)) {
            if (e.over(mx, my)) { e.click(button); return; }
        }
    }

    @Override public void onGuiClosed() { gfx.release(); }

    @Override public boolean doesGuiPauseGame() { return false; }

    void shadowed(String text, String font, float size, float x, float y, int color) {
        gfx.text(text, font, size, x + 1, y + 1, 0x30000000);
        gfx.text(text, font, size, x, y, color);
    }

    void centered(String text, String font, float size, float cx, float y, int color) {
        gfx.text(text, font, size, cx - gfx.textWidth(text, font, size) / 2.0f, y, color);
    }
}
