package com.example.lunarforge.cosmetics;

public enum Display {
    FRONT(new float[0][], .5f, 0f, 1f),
    BACK(new float[][]{{180, 0, 1, 0}, {0, 0, 0, 0}}, .5f, 0f, 1f),
    DEFAULT(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {135, 0, 1, 0}}, .5f, 0f, .9f),
    SUITS(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {135, 0, 1, 0}}, .5f, 0f, .75f),
    HAT(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {135, 0, 1, 0}}, .5f, .4f, 1.35f),
    CLOAK(new float[][]{{20, 1, 0, 0}, {135, 0, 1, 0}}, .55f, -.15f, 1.35f),
    WING(new float[][]{{20, 1, 0, 0}, {135, 0, 1, 0}}, .6f, 0f, .8f),
    SHOES(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {115, 0, 1, 0}}, .5f, -.75f, 1.6f),
    BELT(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {115, 0, 1, 0}}, .5f, -.275f, 1.75f),
    NECK(new float[0][], .5f, 0f, 1f),
    BUST(new float[][]{{20, 1, 0, 0}, {-90, 0, 1, 0}, {115, 0, 1, 0}}, .5f, .2f, 1.65f),
    DUMMY(new float[][]{{20, 1, 0, 0}, {-180, 0, 1, 0}, {145, 0, 1, 0}}, .5f, 0f, .9f),
    WRIST(new float[][]{{70.5f, 0, 1, 0}}, .65f, -.25f, 2f),
    ITEM(new float[][]{{70.5f, 0, 1, 0}}, .35f, 0f, .9f);

    public final float[][] rotation;
    public final float xOffset, yOffset, zoom;

    Display(float[][] rotation, float xOffset, float yOffset, float zoom) {
        this.rotation = rotation; this.xOffset = xOffset; this.yOffset = yOffset; this.zoom = zoom;
    }
}
