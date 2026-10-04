package com.example.lunarforge.cosmetics.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class WingModel extends ModelBase {
    private final ModelRenderer wing, tip;

    public WingModel() {
        textureWidth = 256; textureHeight = 256;
        setTextureOffset("wing.skin", -56, 88);
        setTextureOffset("wingtip.skin", -56, 144);
        setTextureOffset("wing.bone", 112, 88);
        setTextureOffset("wingtip.bone", 112, 136);
        wing = new ModelRenderer(this, "wing");
        wing.setRotationPoint(-12, 5, 2);
        wing.addBox("bone", -56, -4, -4, 56, 8, 8);
        wing.addBox("skin", -56, 0, 2, 56, 0, 56);
        tip = new ModelRenderer(this, "wingtip");
        tip.setRotationPoint(-56, 0, 0);
        tip.addBox("bone", -56, -2, -2, 56, 4, 4);
        tip.addBox("skin", -56, 0, 2, 56, 0, 56);
        wing.addChild(tip);
    }

    public void render(float scale, float boxScale, ResourceLocation texture) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GL11.glRotatef(15, 1, 0, 0);
        GL11.glTranslatef(0, .5f, .25f);
        float t = System.currentTimeMillis() % 2000L / 2000f * (float)Math.PI * 2;
        for (int i = 0; i < 2; ++i) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            wing.rotateAngleX = -.125f - (float)Math.cos(t) * .2f;
            wing.rotateAngleY = .75f;
            wing.rotateAngleZ = (float)(Math.sin(t) + .125) * .8f;
            tip.rotateAngleZ = (float)(Math.sin(t + 2) + .5) * .75f;
            wing.render(boxScale);
            GlStateManager.scale(-1, 1, 1);
            if (i == 0) GL11.glCullFace(GL11.GL_FRONT);
            GlStateManager.shadeModel(GL11.GL_FLAT);
        }
        GlStateManager.popMatrix();

        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }
}
