package com.example.lunarforge.gui.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public interface MenuCanvas {
    String BOLD = "roboto-bold.ttf", MEDIUM = "roboto-medium.ttf", LIGHT = "roboto-light.ttf";

    void rect(float x, float y, float w, float h, int argb);

    void fill(float x, float y, float w, float h, float radius, int argb);

    void fill(float x, float y, float w, float h, float radius, int argb, boolean tl, boolean tr, boolean bl, boolean br);

    void ring(float x, float y, float w, float h, float radius, int argb);

    void text(String text, String font, float lunarSize, float x, float y, int argb);

    float textWidth(String text, String font, float lunarSize);

    void asset(String path, float x, float y, float w, float h, int argb);

    void bitmap(BufferedImage image, float x, float y, float w, float h, int argb);

    void imageRegion(String path, float x, float y, float w, float h, float u0, float v0, float u1, float v1);

    Graphics2D graphics();
}
