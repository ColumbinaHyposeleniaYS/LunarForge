package com.example.lunarforge.gui.ui;

public interface MenuWidget {
    float height(float width);

    void paint(MenuCanvas canvas, float x, float y, float w, float h, float mouseX, float mouseY);

    void press(float mouseX, float mouseY, int button);

    default boolean key(char character, int code) { return false; }

    default boolean animating() { return false; }
}
