package com.example.lunarforge.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class LunarNotifications {
    public enum Type {
        INFO("info", 0x00C2FF), SUCCESS("success", 0x3BBE54),
        WARNING("warning", 0xF9D323), ERROR("error", 0xFF4D4F);
        final String icon;
        final int color;
        Type(String name, int color) {
            icon = "ui/icons/notifications/web-" + name + ".png";
            this.color = color;
        }
    }

    private static final long DURATION = 2500, ENTER = 350, EXIT = 400;
    private static final float MARGIN = 8, GAP = 4, PADDING = 5, ICON = 8, TEXT_X = 18;
    private static final String FONT = "DMSans-Regular.ttf";
    private static final LunarGfx GFX = new LunarGfx("popup");
    private static final ConcurrentLinkedQueue<Popup> PENDING = new ConcurrentLinkedQueue<Popup>();
    private static final List<Popup> POPUPS = new ArrayList<Popup>();

    private static final class Popup {
        final Type type;
        final String title, message;
        final long duration;
        long shownAt;
        List<String> titles, lines;
        float width, height, offset, fromOffset, targetOffset;
        long movedAt;
        int layoutWidth;
        Popup(Type type, String title, String message, long duration) {
            this.type = type == null ? Type.INFO : type;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.duration = Math.max(1, duration);
        }
    }

    private LunarNotifications() { }

    public static void push(Type type, String title, String message) {
        push(type, title, message, DURATION);
    }

    public static void push(Type type, String title, String message, long durationMs) {
        PENDING.add(new Popup(type, title, message, durationMs));
    }

    public static void info(String message) { push(Type.INFO, null, message); }

    public static void render() {
        if (PENDING.isEmpty() && POPUPS.isEmpty()) return;
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        GFX.setScale(res.getScaleFactor());
        long now = System.nanoTime() / 1000000L;
        Popup next;
        while ((next = PENDING.poll()) != null) {
            next.shownAt = now;
            POPUPS.add(0, next);
            if (POPUPS.size() > 20) POPUPS.remove(20);
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 500);
        GlStateManager.disableDepth();
        try {
            float offset = 0;
            for (Iterator<Popup> it = POPUPS.iterator(); it.hasNext();) {
                Popup p = it.next();
                long age = now - p.shownAt;
                if (age >= p.duration + EXIT) { it.remove(); continue; }
                if (p.lines == null || p.layoutWidth != res.getScaledWidth()) layout(p, res.getScaledWidth());
                if (offset != p.targetOffset) {
                    p.fromOffset = p.offset;
                    p.targetOffset = offset;
                    p.movedAt = now;
                }
                p.offset = p.fromOffset + (p.targetOffset - p.fromOffset)
                    * bezier((now - p.movedAt) / 230f, .21f, 1.02f, .73f, 1);
                float alpha = 1, scale = 1, translate = 0;
                if (age < ENTER && age < p.duration) {
                    float t = bezier(age / (float) ENTER, .21f, 1.02f, .73f, 1);
                    alpha = .5f + .5f * t;
                    scale = .6f + .4f * t;
                    translate = -2 * p.height * (1 - t);
                } else if (age >= p.duration) {
                    float t = bezier((age - p.duration) / (float) EXIT, .06f, .71f, .55f, 1);
                    alpha = 1 - t;
                    scale = 1 - .4f * t;
                    translate = -1.5f * p.height * t;
                }
                float x = res.getScaledWidth() - MARGIN - 5 - p.width;
                float y = MARGIN + p.offset + 2 + translate;
                GlStateManager.pushMatrix();
                try {
                    GlStateManager.translate(x + p.width / 2, y + p.height / 2, 0);
                    GlStateManager.scale(scale, scale, 1);
                    draw(p, -p.width / 2, -p.height / 2, alpha);
                } finally { GlStateManager.popMatrix(); }
                if (age < p.duration) offset += p.height + 4 + GAP;
            }
        } finally {
            if (depth) GlStateManager.enableDepth();
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
        }
        if (POPUPS.isEmpty()) GFX.release();
    }

    private static float bezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        float lo = 0, hi = 1, u = t;
        for (int i = 0; i < 16; i++) {
            u = (lo + hi) / 2;
            if (curve(u, x1, x2) < t) lo = u; else hi = u;
        }
        return curve(u, y1, y2);
    }

    private static float curve(float t, float a, float b) {
        return 3 * (1-t) * (1-t) * t * a + 3 * (1-t) * t * t * b + t*t*t;
    }

    private static void layout(Popup p, int screenWidth) {
        p.layoutWidth = screenWidth;
        float room = Math.max(1, Math.min(175, screenWidth - 2 * MARGIN) - 10 - TEXT_X - PADDING);
        p.titles = wrap(p.title, 14, room);
        p.lines = wrap(p.message, 16, room);
        float width = 0;
        for (String line : p.titles) width = Math.max(width, GFX.textWidth(line, FONT, 14));
        for (String line : p.lines) width = Math.max(width, GFX.textWidth(line, FONT, 16));
        p.width = TEXT_X + width + PADDING;
        p.height = PADDING * 2 + Math.max(ICON, p.titles.size() * 9.1f + p.lines.size() * 10.4f);
    }

    private static List<String> wrap(String text, float size, float room) {
        List<String> result = new ArrayList<String>();
        if (text.isEmpty()) return result;
        for (String paragraph : text.replace("\r", "").split("\n", -1)) {
            String remaining = paragraph;
            while (GFX.textWidth(remaining, FONT, size) > room) {
                int end = 0, space = -1;
                while (end < remaining.length()) {
                    int n = remaining.offsetByCodePoints(end, 1);
                    if (end > 0 && GFX.textWidth(remaining.substring(0, n), FONT, size) > room) break;
                    if (remaining.charAt(end) == ' ') space = end;
                    end = n;
                }
                if (space > 0) end = space;
                result.add(remaining.substring(0, end));
                remaining = remaining.substring(end);
                if (remaining.startsWith(" ")) remaining = remaining.substring(1);
            }
            result.add(remaining);
        }
        return result;
    }

    private static int tint(int rgb, float alpha) {
        return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
    }

    private static void draw(Popup p, float x, float y, float alpha) {
        GFX.roundRect(x, y, p.width, p.height, 3.5f, tint(0x1A1818, .7f * alpha));
        GFX.image(p.type.icon, x + PADDING, y + (p.height - ICON) / 2, ICON, ICON, tint(p.type.color, alpha));
        float ty = y + PADDING;
        for (String line : p.titles) {
            GFX.text(line, FONT, 14, x + TEXT_X, ty, tint(0xEEEEF0, alpha));
            ty += 9.1f;
        }
        for (String line : p.lines) {
            GFX.text(line, FONT, 16, x + TEXT_X, ty, tint(0xEEEEF0, alpha * (p.title.isEmpty() ? 1 : .8f)));
            ty += 10.4f;
        }
    }
}
