package com.example.lunarforge.cosmetics.render;

import com.example.lunarforge.cosmetics.Display;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

public final class ModelView {
    private ModelView() {}

    public static void draw(Entity entity, Display display, float x, float y, float w, float h, float zoom,
                            float yaw, float pitch, float panX, float panY) {
        Minecraft mc = Minecraft.getMinecraft();
        float size = h / 1.9f * display.zoom * zoom;
        GlStateManager.pushMatrix();
        GlStateManager.enableColorMaterial();
        GlStateManager.enableDepth();
        GlStateManager.translate(x + w * display.xOffset + panX, y - panY + h / 2f + display.yOffset * h, 150f);
        GlStateManager.scale(-size, size, size);
        GlStateManager.rotate(180f, 0, 0, 1);
        if (pitch != 0) GlStateManager.rotate(pitch, 1, 0, 0);
        for (float[] r : display.rotation) if (r[0] != 0) GlStateManager.rotate(r[0], r[1], r[2], r[3]);
        if (yaw != 0) GlStateManager.rotate(yaw, 0, 1, 0);
        RenderHelper.enableStandardItemLighting();

        RenderManager rm = mc.getRenderManager();
        boolean noWorld = mc.theWorld == null;

        if (noWorld) { rm.worldObj = entity.worldObj; rm.livingPlayer = null; }
        float oldViewY = rm.playerViewY;
        rm.setPlayerViewY(180f);
        rm.setRenderShadow(false);
        try {
            rm.renderEntityWithPosYaw(entity, 0, -.95, 0, 0, 1f);
        } finally {
            rm.setRenderShadow(true);
            rm.setPlayerViewY(oldViewY);
            if (noWorld) rm.worldObj = null;
        }
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
