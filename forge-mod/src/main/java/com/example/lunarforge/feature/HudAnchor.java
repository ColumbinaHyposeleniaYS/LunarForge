package com.example.lunarforge.feature;

public enum HudAnchor {
    TOP_LEFT("topLeft", Side.START, Side.START),
    TOP_CENTER("topCenter", Side.MIDDLE, Side.START),
    TOP_RIGHT("topRight", Side.END, Side.START),
    MIDDLE_LEFT("middleLeft", Side.START, Side.MIDDLE),
    MIDDLE_CENTER("middleCenter", Side.MIDDLE, Side.MIDDLE),
    MIDDLE_RIGHT("middleRight", Side.END, Side.MIDDLE),
    BOTTOM_LEFT("bottomLeft", Side.START, Side.END),
    BOTTOM_CENTER_L("bottomCenterLeft", Side.END, Side.END),
    BOTTOM_CENTER_R("bottomCenterRight", Side.START, Side.END),
    BOTTOM_RIGHT("bottomRight", Side.END, Side.END);

    public enum Side { START, MIDDLE, END }

    public static final float PAD = 2;
    private static final float BOTTOM_CENTER_REACH = 30;

    public final String id;
    public final Side horizontal, vertical;

    HudAnchor(String id, Side horizontal, Side vertical) {
        this.id = id;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static HudAnchor fromId(String id) {
        for (HudAnchor anchor : values()) if (anchor.id.equals(id)) return anchor;
        return null;
    }

    public static HudAnchor at(float x, float y, float screenWidth, float screenHeight) {
        float w3 = screenWidth / 3, h3 = screenHeight / 3;
        if (y < h3) return x < w3 ? TOP_LEFT : x > w3 * 2 ? TOP_RIGHT : TOP_CENTER;
        if (y < h3 * 2) return x < w3 ? MIDDLE_LEFT : x > w3 * 2 ? MIDDLE_RIGHT : MIDDLE_CENTER;
        if (x < w3) return BOTTOM_LEFT;
        if (x < w3 * 2) return x > w3 + w3 / 2 ? BOTTOM_CENTER_R : BOTTOM_CENTER_L;
        return BOTTOM_RIGHT;
    }

    public float originX(float screenWidth, float width) {
        switch (this) {
            case TOP_CENTER: case MIDDLE_CENTER: return screenWidth / 2 - width / 2;
            case BOTTOM_CENTER_L: return screenWidth / 2 - width + BOTTOM_CENTER_REACH;
            case BOTTOM_CENTER_R: return screenWidth / 2 - BOTTOM_CENTER_REACH;
            case TOP_RIGHT: case MIDDLE_RIGHT: case BOTTOM_RIGHT: return screenWidth - width - PAD;
            default: return PAD;
        }
    }

    public float originY(float screenHeight, float height) {
        switch (vertical) {
            case MIDDLE: return screenHeight / 2 - height / 2;
            case END: return screenHeight - height - PAD;
            default: return PAD;
        }
    }
}
