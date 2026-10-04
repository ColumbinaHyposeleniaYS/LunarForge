package com.example.lunarforge.gui.home;

import com.example.lunarforge.LunarForgeMod;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

final class ThemePicker {
    private static final int SPACE_1 = 0xFF13141A, SPACE_3 = 0xFF22232C, SPACE_5 = 0xFF2F313B, SPACE_11 = 0xFFB1B3C0, SPACE_12 = 0xFFEDEEF3;
    private static final int PLACEHOLDER = 0xFF757575, DOT_ON_OUTER = 0x2E03FD15, DOT_ON_INNER = 0xFF07F361;
    private static final int GHOST = 0x14CACBF5, GHOST_HOVER = 0x26D4DCFE;
    private static ResourceLocation icon(String name) { return new ResourceLocation(LunarForgeMod.MOD_ID, "ui/home/" + name + ".png"); }
    private static final ResourceLocation LOGO = icon("picker-logo"), CLOSE = icon("picker-close"), SEARCH = icon("picker-search"),
        SORT = icon("picker-sort-arrows"), RESET = icon("picker-reset"), AZ = icon("sort-az"), ZA = icon("sort-za"),
        QUESTION = icon("picker-question"), ERASER = icon("picker-eraser"), CURTAINS = icon("curtains");
    private static final ResourceLocation TRANSITION_SOUND = new ResourceLocation(LunarForgeMod.MOD_ID, "transition");

    private final HomeGfx gfx;
    private final HomeThemes themes;
    private final Map<String, Anim> anims = new HashMap<String, Anim>();
    boolean open;
    private long openedAt;
    private String search = "";
    private boolean searchFocused, sortOpen, sortWasOpen, sortAscending;

    private long curtainStart = -1;
    private String pendingTheme;
    private boolean swapped;
    private final List<Hit> hits = new ArrayList<Hit>();
    private float mx, my, vw, vh, scroll, maxScroll, gridTop, gridBottom;

    private static final class Hit {
        final float x, y, w, h; final Runnable run; final boolean menuOption;
        Hit(float x, float y, float w, float h, Runnable run) { this(x, y, w, h, run, false); }
        Hit(float x, float y, float w, float h, Runnable run, boolean menuOption) { this.x = x; this.y = y; this.w = w; this.h = h; this.run = run; this.menuOption = menuOption; }
    }

    private static final class Anim {
        boolean on; float from; long start; final float ms; final float[] curve;
        Anim(float ms, float[] curve) { this.ms = ms; this.curve = curve; }
        float value(boolean target) {
            long now = System.currentTimeMillis(); float current = sample(now);
            if (target != on) { from = current; on = target; start = now; current = sample(now); }
            return current;
        }
        private float sample(long now) {
            float t = Math.min(1, (now - start) / ms);
            return from + ((on ? 1 : 0) - from) * LunarHomeScreen.cubicBezier(curve[0], curve[1], curve[2], curve[3], t);
        }
    }
    private static final float[] EASE = {0.25f, 0.1f, 0.25f, 1}, EASE_IN_OUT = {0.42f, 0, 0.58f, 1};

    ThemePicker(HomeGfx gfx, HomeThemes themes) {
        this.gfx = gfx; this.themes = themes;
        sortAscending = !"alpha-desc".equals(themes.store().get("homeTheme.sort", "alpha-asc"));
    }

    private float anim(String key, boolean on, float ms, float[] curve) {
        Anim a = anims.get(key); if (a == null) { a = new Anim(ms, curve); anims.put(key, a); }
        return a.value(on);
    }

    void show() { open = true; openedAt = System.currentTimeMillis(); search = ""; searchFocused = false; sortOpen = false; }
    boolean blocksHome() { return open || curtainsRunning(); }
    boolean curtainsRunning() { return curtainStart >= 0; }

    private boolean over(float x, float y, float w, float h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    void draw(float vw, float vh, float mouseX, float mouseY) {
        this.vw = vw; this.vh = vh; this.mx = mouseX; this.my = mouseY;
        hits.clear();
        if (open) drawModal();
        if (curtainsRunning()) drawCurtains();
    }

    private void drawModal() {
        float t = Math.min(1, (System.currentTimeMillis() - openedAt) / 200f), eased = LunarHomeScreen.cubicBezier(0.42f, 0, 0.58f, 1, t);
        gfx.rect(0, 0, vw, vh, Math.round(0.5f * eased * 255) << 24);
        hits.add(new Hit(0, 0, vw, vh, new Runnable() { public void run() { close(); } }));

        List<HomeThemes.Theme> shown = visibleThemes();
        HomeThemes.Theme active = themes.current();
        float w = Math.min(880, vw * 0.9f), contentW = w - 40;
        boolean empty = shown.isEmpty();

        float fixed = 152 + 12 + 21 + 20, maxBody = vh * 0.9f - fixed;
        boolean overflow = false; int cols = 1, rows = 1; float colW = 0, contentH = 0;
        for (int pass = 0; pass < 2; pass++) {
            float gridInner = contentW - 12 - (overflow ? 16 : 0);
            int maxCols = Math.max(1, (int)Math.floor((gridInner + 24) / (175 + 24)));
            cols = Math.max(1, Math.min(maxCols, shown.size()));
            colW = (gridInner - (cols - 1) * 24) / cols;
            rows = (shown.size() + cols - 1) / cols;
            contentH = empty ? 286 : 12 + rows * 189 + (rows - 1) * 24;
            if (!empty && contentH > maxBody) overflow = true;
        }
        float bodyH = overflow ? Math.max(0, maxBody) : contentH;
        scroll = overflow ? Math.max(0, Math.min(scroll, contentH - bodyH)) : 0;
        maxScroll = overflow ? contentH - bodyH : 0;
        float h = fixed + bodyH;
        float x = (vw - w) / 2, y = (vh - h) / 2 + 30 * (1 - eased), cx = x + 20;

        float pad = HomeGfx.shadowPad(60);
        gfx.draw(gfx.shadow(w, h, 12, 30, 60, -10), x - pad, y - pad, 0x800A0A0A);
        gfx.draw(gfx.box(w, h, 12), x, y, SPACE_1);
        hits.add(new Hit(x, y, w, h, new Runnable() { public void run() { sortOpen = false; searchFocused = false; } }));

        gfx.draw(LOGO, cx, y + 22, 24, 24, 0xFFFFFFFF);
        gfx.draw(gfx.text("Select Theme", true, 24), x + 52, y + 20, SPACE_12);
        boolean closeHover = over(x + w - 52, y + 21.5f, 32, 29);
        gfx.draw(CLOSE, x + w - 48, y + 21.5f, 24, 25, LunarHomeScreen.lerp(SPACE_11, SPACE_12, anim("close", closeHover, 200, EASE)));
        hits.add(new Hit(x + w - 52, y + 21.5f, 32, 29, new Runnable() { public void run() { close(); } }));

        float sy = y + 68;
        gfx.draw(gfx.box(contentW, 36, 10), cx, sy, SPACE_3);
        gfx.draw(SEARCH, cx + 12, sy + 10, 16, 16, SPACE_11);
        boolean blink = (System.currentTimeMillis() / 530) % 2 == 0;
        if (search.isEmpty()) gfx.draw(gfx.text("Search...", false, 14), cx + 36, sy + 8.8f, PLACEHOLDER);
        else gfx.draw(gfx.text(search, false, 14), cx + 36, sy + 8.8f, SPACE_11);
        if (searchFocused && blink) gfx.rect(gfx.snap(cx + 36 + (search.isEmpty() ? 0 : gfx.textWidth(search, false, 14))), sy + 10, 1, 16, SPACE_11);
        hits.add(new Hit(cx, sy, contentW, 36, new Runnable() { public void run() { searchFocused = true; sortOpen = false; } }));

        float ty = y + 117.5f;
        String label = "Themes (" + themes.choices().size() + ")";
        float labelW = gfx.textWidth(label, false, 16);
        boolean sortHover = over(cx, ty, labelW + 24, 21);
        int sortColor = LunarHomeScreen.lerp(SPACE_11, SPACE_12, anim("sort", sortHover, 200, EASE));
        gfx.draw(gfx.text(label, false, 16), cx, ty, sortColor);
        gfx.draw(SORT, cx + labelW + 8, ty + 2.5f, 16, 16, sortColor);
        hits.add(new Hit(cx, ty, labelW + 24, 21, new Runnable() { public void run() { sortOpen = !sortWasOpen; searchFocused = false; } }));

        float gy = y + 152;
        gridTop = gy; gridBottom = gy + bodyH;
        if (empty) drawEmpty(cx, gy, contentW);
        else {
            if (overflow) scissor(cx, gy, contentW, bodyH);
            for (int i = 0; i < shown.size(); i++) {
                final HomeThemes.Theme theme = shown.get(i);
                float px = cx + 6 + (i % cols) * (colW + 24), py = gy + 6 + (i / cols) * (189 + 24) - scroll;
                if (py + 189 < gy || py > gy + bodyH) continue;
                drawCard(theme, theme.id.equals(active.id), px, py, colW);
            }
            if (overflow) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                float thumbH = Math.max(36, bodyH * bodyH / contentH), thumbY = gy + (bodyH - thumbH) * (scroll / maxScroll);
                boolean thumbHover = over(cx + contentW - 16, thumbY, 16, thumbH);
                gfx.draw(gfx.box(6, thumbH - 10, 3), cx + contentW - 11, thumbY + 5, thumbHover ? 0x26D4DCFE : 0x14CACBF5);
            }
        }

        float fy = gy + bodyH + 12;
        gfx.draw(gfx.text("Active:", false, 16), cx, fy, SPACE_11);
        gfx.draw(gfx.text(active.name, false, 16), cx + gfx.textWidth("Active:", false, 16) + 8, fy, SPACE_12);
        drawButtonStyle(cx + contentW / 2, fy - 3.5f);
        float resetW = gfx.textWidth("Reset to default", false, 16), rx = cx + contentW - resetW;
        boolean resetHover = over(rx - 20, fy, resetW + 20, 21);
        int resetColor = LunarHomeScreen.lerp(SPACE_11, SPACE_12, anim("reset", resetHover, 200, EASE));
        gfx.draw(RESET, rx - 20, fy + 2.5f, 16, 16, resetColor);
        gfx.draw(gfx.text("Reset to default", false, 16), rx, fy, resetColor);
        hits.add(new Hit(rx - 20, fy, resetW + 20, 21, new Runnable() { public void run() { choose(""); } }));

        drawSortMenu(cx, ty + 27.5f);
    }

    boolean legacyButtons() { return "legacy".equals(themes.store().get("homeTheme.buttons", "modern")); }

    private void drawButtonStyle(float centreX, float y) {
        String label = "Buttons";
        String[] options = {"Modern", "Legacy"};
        float labelW = gfx.textWidth(label, false, 16), optW = 76, trackW = optW * 2 + 4, total = labelW + 12 + trackW;
        float x = centreX - total / 2, tx = x + labelW + 12;
        gfx.draw(gfx.text(label, false, 16), x, y + 3.5f, SPACE_11);
        gfx.draw(gfx.box(trackW, 28, 8), tx, y, SPACE_3);
        for (int i = 0; i < 2; i++) {
            final boolean legacy = i == 1;
            boolean selected = legacy == legacyButtons();
            float ox = tx + 2 + i * optW;
            boolean hover = over(ox, y + 2, optW, 24);
            if (selected) gfx.draw(gfx.box(optW, 24, 6), ox, y + 2, SPACE_5);
            int color = selected ? SPACE_12 : LunarHomeScreen.lerp(SPACE_11, SPACE_12, anim("style" + i, hover, 200, EASE));
            float w = gfx.textWidth(options[i], false, 14);
            gfx.draw(gfx.text(options[i], false, 14), ox + (optW - w) / 2, y + 5, color);
            hits.add(new Hit(ox, y + 2, optW, 24, new Runnable() { public void run() {
                themes.store().put("homeTheme.buttons", legacy ? "legacy" : "modern"); themes.store().save();
            } }));
        }
    }

    private void drawCard(final HomeThemes.Theme theme, boolean isActive, float x, float y, float w) {
        boolean imageHover = over(x, y, w, 160), cardHover = over(x, y, w, 189);

        float zoom = 1 + 0.1f * anim("zoom:" + theme.id, imageHover, 500, EASE);
        float vSpan = Math.min(1, 160 / w) / zoom, uSpan = Math.min(1, w / 160) / zoom;
        gfx.roundedRegion(theme.panorama(0), x, y, w, 160, 8, 0.5f - uSpan / 2, 0.5f - vSpan / 2, 0.5f + uSpan / 2, 0.5f + vSpan / 2);
        int labelColor = LunarHomeScreen.lerp(SPACE_11, SPACE_12, anim("label:" + theme.id, cardHover, 200, EASE));
        gfx.draw(gfx.text(theme.name, false, 16), x, y + 168, labelColor);
        gfx.draw(gfx.box(20, 20, 10), x + w - 20, y + 168, isActive ? DOT_ON_OUTER : SPACE_3);
        gfx.draw(gfx.box(10, 10, 5), x + w - 15, y + 173, isActive ? DOT_ON_INNER : SPACE_11);
        float top = Math.max(y, gridTop), bottom = Math.min(y + 189, gridBottom);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, new Runnable() { public void run() { choose(theme.id); } }));
    }

    private void scissor(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.round(x * gfx.dsf), Math.round(mc.displayHeight - (y + h) * gfx.dsf), Math.round(w * gfx.dsf), Math.round(h * gfx.dsf));
    }

    void mouseWheel(int notches) { if (open && maxScroll > 0) scroll = Math.max(0, Math.min(maxScroll, scroll - notches * 100)); }

    private void drawEmpty(float cx, float gy, float contentW) {
        float centre = cx + contentW / 2;
        gfx.draw(QUESTION, centre - 60, gy, 120, 120, SPACE_11);
        String line = "Couldn't find ‘" + search + "’";
        gfx.draw(gfx.text(line, false, 20), centre - gfx.textWidth(line, false, 20) / 2, gy + 144, SPACE_12);
        String hint = "Are you sure you spelt it correctly?";
        gfx.draw(gfx.text(hint, false, 16), centre - gfx.textWidth(hint, false, 16) / 2, gy + 178, SPACE_11);
        float bw = 12 + 16 + 8 + gfx.textWidth("Clear search", false, 16) + 12, bx = centre - bw / 2, by = gy + 223;
        boolean hover = over(bx, by, bw, 39);
        float t = anim("clear", hover, 200, EASE);
        gfx.draw(gfx.box(bw, 39, 8), bx, by, LunarHomeScreen.lerp(GHOST, GHOST_HOVER, t));
        int color = LunarHomeScreen.lerp(SPACE_11, SPACE_12, t);
        gfx.draw(ERASER, bx + 12, by + 11.5f, 16, 16, color);
        gfx.draw(gfx.text("Clear search", false, 16), bx + 36, by + 10, color);
        hits.add(new Hit(bx, by, bw, 39, new Runnable() { public void run() { search = ""; } }));
    }

    private void drawSortMenu(float x, float y) {
        float t = anim("sortMenu", sortOpen, 150, EASE);
        if (t <= 0.001f) return;
        int alpha = Math.round(t * 255);
        y -= 10 * (1 - t);
        float pad = HomeGfx.shadowPad(24);
        gfx.draw(gfx.shadow(200, 88, 8, 8, 24, 0), x - pad, y - pad, withAlpha(0x4D0A0A0A, t));
        gfx.draw(gfx.shadow(200, 88, 8, 2, 8, 0), x - HomeGfx.shadowPad(8), y - HomeGfx.shadowPad(8), withAlpha(0x260A0A0A, t));
        gfx.draw(gfx.box(200, 88, 8), x, y, (alpha << 24) | (SPACE_3 & 0xFFFFFF));
        String[] labels = {"Alphabetical (A-Z)", "Alphabetical (Z-A)"};
        ResourceLocation[] icons = {AZ, ZA};
        for (int i = 0; i < 2; i++) {
            final boolean ascending = i == 0;
            float oy = y + 8 + i * 38;
            boolean selected = sortAscending == ascending, hover = sortOpen && over(x + 8, oy, 184, 34);
            float h = anim("opt" + i, hover || selected, 200, EASE_IN_OUT);
            if (h > 0) gfx.draw(gfx.box(184, 34, 8), x + 8, oy, withAlpha(SPACE_5, h * t));
            int color = withAlpha(LunarHomeScreen.lerp(SPACE_11, SPACE_12, h), t);
            gfx.draw(icons[i], x + 16, oy + 9, 16, 16, color);
            gfx.draw(gfx.text(labels[i], false, 14), x + 36, oy + 8, color);
            if (sortOpen) hits.add(new Hit(x + 8, oy, 184, 34, new Runnable() { public void run() {
                sortAscending = ascending; sortOpen = false;
                themes.store().put("homeTheme.sort", ascending ? "alpha-asc" : "alpha-desc"); themes.store().save();
            } }, true));
        }
    }

    private static int withAlpha(int argb, float factor) {
        return (Math.round(((argb >>> 24) & 0xFF) * factor) << 24) | (argb & 0xFFFFFF);
    }

    private List<HomeThemes.Theme> visibleThemes() {
        List<HomeThemes.Theme> list = new ArrayList<HomeThemes.Theme>();
        for (HomeThemes.Theme theme : themes.choices())
            if (theme.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) list.add(theme);
        Collections.sort(list, new Comparator<HomeThemes.Theme>() {
            public int compare(HomeThemes.Theme a, HomeThemes.Theme b) {
                int c = a.name.compareToIgnoreCase(b.name);
                return sortAscending ? c : -c;
            }
        });
        return list;
    }

    private void close() {
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        open = false;
    }

    private void choose(String id) {
        if (curtainsRunning()) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(TRANSITION_SOUND, 1.0F));
        pendingTheme = id; swapped = false; curtainStart = System.currentTimeMillis();
    }

    boolean mouseClicked(float x, float y) {
        if (!open || curtainsRunning()) return blocksHome();
        mx = x; my = y;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x && y >= hit.y && x < hit.x + hit.w && y < hit.y + hit.h) {
                sortWasOpen = sortOpen;
                if (!hit.menuOption) sortOpen = false;
                hit.run.run();
                return true;
            }
        }
        return true;
    }

    boolean keyTyped(char c, int key) {
        if (!open) return curtainsRunning();
        if (key == Keyboard.KEY_ESCAPE) { if (sortOpen) sortOpen = false; else close(); return true; }
        if (!searchFocused) return true;
        if (key == Keyboard.KEY_BACK) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); }
        else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 64) search += c;
        return true;
    }

    private void drawCurtains() {
        long elapsed = System.currentTimeMillis() - curtainStart;
        float closing = Math.min(1, elapsed / 1200f);
        float p;
        if (elapsed < 1200) p = LunarHomeScreen.cubicBezier(0.37f, 0.33f, 0.15f, 1, closing);
        else {
            if (!swapped) {
                swapped = true;
                Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(TRANSITION_SOUND, 1.0F));
                themes.select(pendingTheme);
            }
            float opening = Math.min(1, (elapsed - 1200) / 1200f);
            p = 1 - LunarHomeScreen.cubicBezier(0.37f, 0.33f, 0.15f, 1, opening);
            if (opening >= 1) { curtainStart = -1; return; }
        }
        float w = vw / 2, h = vh * 1.5f;
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1 : 1;
            float x = lerp(sign * 1.25f * w, -sign * 20, p), y = lerp(-0.1f * h, 0, p), rotation = lerp(-sign * 20, sign * 3, p);
            GlStateManager.pushMatrix();
            GlStateManager.translate(side * w + w / 2 + x, h / 2 + y, 0);
            GlStateManager.rotate(rotation, 0, 0, 1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(CURTAINS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager.enableBlend(); GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
            float repeats = 1 / 0.85f;
            Tessellator tessellator = Tessellator.getInstance(); WorldRenderer wr = tessellator.getWorldRenderer();
            wr.begin(7, DefaultVertexFormats.POSITION_TEX);
            wr.pos(-w / 2, h / 2, 0).tex(0, 1).endVertex(); wr.pos(w / 2, h / 2, 0).tex(repeats, 1).endVertex();
            wr.pos(w / 2, -h / 2, 0).tex(repeats, 0).endVertex(); wr.pos(-w / 2, -h / 2, 0).tex(0, 0).endVertex();
            tessellator.draw();
            GlStateManager.popMatrix();
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
