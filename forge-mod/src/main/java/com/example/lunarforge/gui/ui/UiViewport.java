package com.example.lunarforge.gui.ui;

public final class UiViewport {
    public float left, top, scale;
    public void resize(int width, int height) {
        scale = Math.max(.1f, Math.min(1, Math.min((width - 16f) / UiRenderer.WIDTH, (height - 16f) / UiRenderer.HEIGHT)));
        left = (width - UiRenderer.WIDTH * scale) / 2;
        top = (height - UiRenderer.HEIGHT * scale) / 2;
    }
    public float x(float mouseX) { return (mouseX - left) / scale; }
    public float y(float mouseY) { return (mouseY - top) / scale; }
}
