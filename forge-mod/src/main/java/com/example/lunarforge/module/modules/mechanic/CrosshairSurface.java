package com.example.lunarforge.module.modules.mechanic;

import java.awt.image.BufferedImage;

interface CrosshairSurface {
    void push();

    void pop();

    void translate(float x, float y);

    void scale(float x, float y);

    void rotate(float degrees);

    void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4, boolean invert);

    void texture(String name, float x, float y, float w, float h, int argb, boolean invert);

    void image(String key, BufferedImage image, float x, float y, float w, float h, int tl, int tr, int br, int bl, boolean invert);
}
