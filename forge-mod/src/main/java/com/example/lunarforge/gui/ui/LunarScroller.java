package com.example.lunarforge.gui.ui;

public final class LunarScroller {
    public float offset;

    public double velocity;

    public float content, height;

    public boolean dragging;

    public boolean wheel(int dWheel) {
        if (dWheel == 0 || content < height) return false;
        velocity += dWheel / 3f;
        return true;
    }

    public void frame(boolean pointerInside) {
        if (!pointerInside && velocity != 0) velocity = 0;
        double d = Math.round(velocity / 5.0);
        velocity -= d;
        if (velocity != 0) offset -= d;
        clamp();
    }

    public void clamp() {
        if (content <= height) { if (offset != 0) offset = 0; return; }
        if (offset > content - height) { offset = content - height; velocity = 0; }
        if (offset < 0) { offset = 0; velocity = 0; }
    }

    public boolean gliding() { return Math.round(velocity / 5.0) != 0; }

    public boolean overflows() { return content > height; }

    public float thumb() {
        if (content <= 0) return height;
        return Math.max(Math.min(height * height / content, height), Math.min(16, height / 2));
    }

    public float thumbY() {
        return content <= height ? 0 : offset / (content - height) * (height - thumb());
    }

    public void drag(float y) {
        float thumb = thumb(), room = height - thumb;
        float f = room <= 0 ? 0 : Math.max(0, Math.min(1, (y - thumb / 2) / room));
        offset = f * (content - height);
        clamp();
    }

    public void reset() { offset = 0; velocity = 0; dragging = false; }
}
