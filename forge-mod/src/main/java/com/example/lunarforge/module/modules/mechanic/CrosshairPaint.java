package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.setting.ColorSetting;

class CrosshairPaint {
    private final ColorSetting color;

    final boolean vanilla, vanillaColored;

    CrosshairPaint(ColorSetting color, boolean vanilla, boolean vanillaColored) {
        this.color = color;
        this.vanilla = vanilla;
        this.vanillaColored = vanillaColored;
    }

    private boolean colored() { return color != null && (!vanilla || vanillaColored); }

    int colorAt(float x, float y) { return colored() ? color.color((x + y) * 4.0f) : -1; }

    void rect(CrosshairSurface s, float x, float y, float w, float h) {
        if (vanilla) {
            s.quad(x + w, y, colorAt(x + w, y), x, y, colorAt(x, y), x, y + h, colorAt(x, y + h), x + w, y + h, colorAt(x + w, y + h), true);
        } else if (colored()) {
            s.quad(x + w, y, colorAt(x + w, y), x, y, colorAt(x, y), x, y + h, colorAt(x, y + h), x + w, y + h, colorAt(x + w, y + h), false);
        } else {
            s.quad(x + w, y, -1, x, y, -1, x, y + h, -1, x + w, y + h, -1, false);
        }
    }

    void outline(CrosshairSurface s, float x, float y, float w, float h, float t) {
        rect(s, x - t, y - t, w + 2.0f * t, t);
        rect(s, x - t, y + h, w + 2.0f * t, t);
        rect(s, x - t, y, t, h);
        rect(s, x + w, y, t, h);
    }

    void circle(CrosshairSurface s, float x, float y, float size) {
        String tex = size <= 4.0f ? "circle_small" : "circle";
        s.push();
        s.translate(x, y);
        s.scale(0.5f, 0.5f);
        float d = size * 2.0f - 0.75f;
        s.texture(tex, -d / 2.0f, -d / 2.0f, d, d, colorAt(x, y), vanilla);
        s.pop();
    }

    void ring(CrosshairSurface s, float x, float y, float size, float thickness) {
        String tex = size <= 4.0f ? "circle_outline_small" : "circle_outline";
        s.push();
        s.translate(x, y);
        s.scale(0.5f, 0.5f);
        float d = Math.round(size * 2.0f + 0.5f);
        if (tex.equals("circle_outline")) {
            thickness += 1.0f;
            d += 0.25f;
        }
        int n = Math.max(1, (int)Math.round((thickness - 1.0f) * 8.0f / 4.0));
        for (int i = 0; i < n; i++) {
            float e = d + i;
            s.texture(tex, -e / 2.0f, -e / 2.0f, e, e, colorAt(x - e / 2.0f, y - e / 2.0f), vanilla);
        }
        s.pop();
    }

    void line(CrosshairSurface s, float x1, float y1, float x2, float y2, float t) {
        float h = t / 2.0f;
        s.quad(x1, y1 + h, colorAt(x1, y1 + h), x2, y2 + h, colorAt(x2, y2 + h), x2, y2 - h, colorAt(x2, y2 - h), x1, y1 - h, colorAt(x1, y1 - h), vanilla);
    }

    void triangle(CrosshairSurface s, float x, float y, float w, float h, float t) {
        s.push();
        w *= 2.0f;
        h *= 2.0f;
        s.translate(x - w / 2.0f, y - h / 2.0f);
        for (int n = 0; n < t * 2.0f; n++) {
            float grow = n / 3.0f;
            s.texture("triangle", -grow / 2.0f, -grow / 2.0f - n / 20.0f, w + grow, h + grow, colorAt(x, y), vanilla);
        }
        s.pop();
    }
}
