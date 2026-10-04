package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;

final class CrosshairShapes {
    enum Shape implements ChoiceSetting.Option {
        CROSS("cross"), CIRCLE("circle"), ARROW("arrow"), TRIANGLE("triangle"), SQUARE("square"), DOT("dot"), CIRCLE_DOT("circleDot"), X("x");
        private final String id;
        Shape(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final CrosshairChild owner;
    final ChoiceSetting<Shape> crosshairShape;
    final NumberSetting crosshairThickness, crosshairWidth, crosshairHeight, crosshairGap;
    final BoolSetting crosshairDot;
    final NumberSetting dotSize;
    final BoolSetting dotCircle, dynamicDot, customDotColor;
    final ColorSetting dotColor;
    final BoolSetting dotOutline;
    final NumberSetting dotOutlineThickness;

    CrosshairShapes(CrosshairChild owner) {
        this.owner = owner;
        Module m = owner;
        crosshairShape = m.choice("crosshairShape", Shape.CROSS);
        crosshairThickness = m.integer("crosshairThickness", 1, 1, 5);
        crosshairWidth = m.integer("crosshairWidth", 4, 0, 16);
        crosshairHeight = m.integer("crosshairHeight", 4, 0, 16);
        crosshairGap = m.integer("crosshairGap", 0, 0, 8);
        crosshairDot = m.bool("crosshairDot", true);
        dotSize = m.integer("dotSize", 3, 1, 16);
        dotCircle = m.bool("dotCircle", false);
        dynamicDot = m.bool("dynamicDot", true);
        customDotColor = m.bool("customDotColor", false);
        dotColor = m.color("dotColor", -1);
        dotOutline = m.bool("dotOutline", false);
        dotOutlineThickness = m.decimal("dotOutlineThickness", 0.5f, 0.0f, 1.0f);
    }

    private Shape shape() { return crosshairShape.get(); }

    void draw(CrosshairSurface s, float x, float y, float scale, float spread, CrosshairPaint paint) {
        s.push();
        s.translate(x, y);
        s.scale(scale, scale);
        draw(s, 0.0f, 0.0f, spread, paint, owner.outlinePaint(), true);
        s.pop();
    }

    private void draw(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        switch (shape()) {
            case CROSS: cross(s, x, y, spread, paint, outline, fill); break;
            case CIRCLE: circle(s, x, y, spread, paint, outline, fill); break;
            case ARROW: arrow(s, x, y, spread, paint, outline, fill); break;
            case TRIANGLE: triangle(s, x, y, spread, paint, outline, fill); break;
            case SQUARE: square(s, x, y, spread, paint, outline, fill); break;
            case DOT: dotShape(s, x, y, spread, paint, outline, fill); break;
            case CIRCLE_DOT: circleDot(s, x, y, spread, paint, outline, fill); break;
            case X: xShape(s, x, y, spread, paint, outline, fill); break;
        }
        if (crosshairDot.on() && hasDot()) dot(s, x, y, spread, paint, outline, fill);
    }

    private void dot(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        CrosshairPaint dotPaint = customDotColor.on() ? new CrosshairPaint(dotColor, paint.vanilla, paint.vanillaColored) : paint;
        float r = dotSize.intValue() / 2.0f + (dynamicDot.on() ? spread / 2.0f : 0.0f) - 0.35f;
        CrosshairPaint dotOutlinePaint = dotOutline.on() ? outline : null;
        if (dotCircle.on()) {
            if (dotOutlinePaint != null) dotOutlinePaint.ring(s, x + 0.5f, y + 0.5f, r + 1.0f, dotOutlineThickness.value() * 2.0f);
            if (fill) dotPaint.circle(s, x + 0.5f, y + 0.5f, r + 1.0f);
        } else {
            if (dotOutlinePaint != null) dotOutlinePaint.outline(s, x + 0.5f - r / 2.0f, y + 0.5f - r / 2.0f, r, r, dotOutlineThickness.value());
            if (fill) dotPaint.rect(s, x + 0.5f - r / 2.0f, y + 0.5f - r / 2.0f, r, r);
        }
    }

    private void cross(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        int w = crosshairWidth.intValue(), h = crosshairHeight.intValue();
        float gap = crosshairGap.intValue() + spread;
        int t = crosshairThickness.intValue();
        float lo = t / 2.0f - 0.5f, hi = t / 2.0f + 0.5f;
        if (outline != null) {
            float o = owner.outlineWidth();
            outline.outline(s, x - w - gap - lo, y - lo, w, lo + hi, o);
            outline.outline(s, x + gap + hi, y - lo, w, lo + hi, o);
            outline.outline(s, x - lo, y - h - gap - lo, lo + hi, h, o);
            outline.outline(s, x - lo, y + gap + hi, lo + hi, h, o);
        }
        if (!fill) return;
        paint.rect(s, x - w - gap - lo, y - lo, w, lo + hi);
        paint.rect(s, x + gap + hi, y - lo, w, lo + hi);
        paint.rect(s, x - lo, y - h - gap - lo, lo + hi, h);
        paint.rect(s, x - lo, y + gap + hi, lo + hi, h);
    }

    private void circle(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        x += 0.5f;
        y += 0.5f;
        float t = crosshairThickness.intValue();
        float r = crosshairGap.intValue() + spread + 1.0f;
        if (outline != null) outline.ring(s, x, y, r + t, owner.outlineRing());
        if (fill) paint.ring(s, x, y, r, t);
    }

    private void arrow(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        x += 0.5f;
        float t = crosshairThickness.intValue(), half = t / 2.0f;
        float w = crosshairWidth.intValue(), h = crosshairHeight.intValue();
        float gap = crosshairGap.intValue() + spread;
        if (outline != null) {
            float o = owner.outlineRing() + 0.05f, oh = o / 2.0f;
            float oy = y - o;
            outline.line(s, x - w - gap, oy + h + oh, x, oy + oh, o);
            outline.line(s, x, oy + oh, x + w + gap, oy + h + oh, o);
            oy += o + t;
            outline.line(s, x - w - gap, oy + h + oh, x, oy + oh, o);
            outline.line(s, x, oy + oh, x + w + gap, oy + h + oh, o);
            float slope = oh * (-h / (w + gap));
            oy -= t;
            outline.line(s, x - w - gap - oh, oy + h + half - slope, x - w - gap, oy + h + half, o * 2.0f + t);
            outline.line(s, x + w + gap, oy + h + half, x + w + gap + oh, oy + h + half - slope, o * 2.0f + t);
        }
        if (!fill) return;
        paint.line(s, x - w - gap, y + h + half, x, y + half, t);
        paint.line(s, x, y + half, x + w + gap, y + h + half, t);
    }

    private void triangle(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        float t = crosshairThickness.intValue();
        float gap = crosshairGap.intValue() + spread;
        float w = crosshairWidth.intValue() + gap, h = crosshairHeight.intValue() + gap;
        x += 0.5f;
        y -= gap / 4.0f;
        if (outline != null) outline.triangle(s, x, y, w, h, t + owner.outlineRing() * 2.0f);
        if (fill) paint.triangle(s, x, y, w, h, t);
    }

    private void square(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        x += 0.5f;
        y += 0.5f;
        float t = crosshairThickness.intValue() / 2.0f;
        float gap = crosshairGap.intValue() + spread;
        float w = crosshairWidth.intValue() + gap, h = crosshairHeight.intValue() + gap;
        if (outline != null) outline.outline(s, x - w - t / 2.0f, y - h - t / 2.0f, w * 2.0f + t, h * 2.0f + t, owner.outlineRing());
        if (!fill) return;
        paint.rect(s, x - w - t / 2.0f, y - h - t / 2.0f, t, h * 2.0f + t);
        paint.rect(s, x + w - t / 2.0f, y - h - t / 2.0f, t, h * 2.0f + t);
        paint.rect(s, x - w - t / 2.0f, y - h - t / 2.0f, w * 2.0f + t, t);
        paint.rect(s, x - w - t / 2.0f, y + h - t / 2.0f, w * 2.0f + t, t);
    }

    private void dotShape(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        x += 0.5f;
        y += 0.5f;
        float w = crosshairWidth.intValue() + spread, h = crosshairHeight.intValue() + spread;
        if (outline != null) outline.outline(s, x - w / 2.0f, y - h / 2.0f, w, h, owner.outlineRing());
        if (fill) paint.rect(s, x - w / 2.0f, y - h / 2.0f, w, h);
    }

    private void circleDot(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        x += 0.5f;
        y += 0.5f;
        float r = crosshairThickness.intValue() + spread + 1.0f;
        if (outline != null) outline.ring(s, x, y, r, owner.outlineRing());
        if (fill) paint.circle(s, x, y, r);
    }

    private void xShape(CrosshairSurface s, float x, float y, float spread, CrosshairPaint paint, CrosshairPaint outline, boolean fill) {
        s.push();
        s.translate(x + 0.5f, y - 0.15f);
        s.rotate(45.0f);
        cross(s, 0.0f, 0.0f, spread, paint, outline, fill);
        s.pop();
    }

    boolean hasSize() { return shape() != Shape.CIRCLE && shape() != Shape.CIRCLE_DOT; }

    boolean hasThickness() { return shape() != Shape.DOT; }

    boolean hasGap() { return shape() != Shape.DOT && shape() != Shape.CIRCLE_DOT; }

    boolean hasDot() { return shape() != Shape.ARROW && shape() != Shape.DOT && shape() != Shape.CIRCLE_DOT; }

    boolean evenCentered() {
        Shape s = shape();
        return (s == Shape.CROSS || s == Shape.X || s == Shape.CIRCLE || s == Shape.TRIANGLE) && crosshairThickness.intValue() % 2 == 0;
    }
}
