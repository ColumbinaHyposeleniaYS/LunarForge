package com.example.lunarforge.module.modules.hud.armorstatus;

import com.example.lunarforge.module.hud.Draw;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class HotbarStrip {
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");

    private HotbarStrip() {}

    public static float width(boolean vertical, int n) { return 2 + (vertical ? 1 : n) * 20; }

    public static float height(boolean vertical, int n) { return 2 + (vertical ? n : 1) * 20; }

    public static void draw(float x, float y, boolean vertical, int n, boolean rounded, float alpha) {
        GlStateManager.pushMatrix();
        if (vertical) {
            GlStateManager.translate(x + 22, y, 0);
            GlStateManager.rotate(90, 0, 0, 1);
        } else {
            GlStateManager.translate(x, y, 0);
        }
        int c = (int)(alpha * 255) << 24 | 0xFFFFFF;
        row(0, 0, 0, n, rounded, c);
        middle(0, 1, n, 1, 20, c);
        row(0, 21, 21, n, rounded, c);
        if (rounded) corners(n, c);
        GlStateManager.popMatrix();
    }

    private static void blit(float x, float y, float u, float v, float w, float h, int c) {
        Draw.blit(WIDGETS, x, y, u, v, w, h, 256, 256, c);
    }

    private static void row(float x, float y, float v, int n, boolean rounded, int c) {
        if (!rounded) { middle(x, y, n, v, 1, c); return; }
        float body = (n - 1) * 20;
        blit(x + 1, y, 1, v, body, 1, c);
        blit(x + 1 + body, y, 161, v, 20, 1, c);
    }

    private static void middle(float x, float y, int n, float v, float h, int c) {
        float body = 1 + (n - 1) * 20, end = 21;
        blit(x, y, 0, v, body, h, c);
        blit(x + body, y, 182 - end, v, end, h, c);
    }

    private static void corners(int n, int c) {
        float right = width(false, n) - 2;
        blit(1, 1, 0, 0, 1, 1, c);
        blit(right, 1, 181, 0, 1, 1, c);
        blit(1, 20, 0, 21, 1, 1, c);
        blit(right, 20, 181, 21, 1, 1, c);
    }
}
