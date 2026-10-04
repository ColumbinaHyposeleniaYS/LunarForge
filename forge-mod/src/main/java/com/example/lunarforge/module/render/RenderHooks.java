package com.example.lunarforge.module.render;

import com.example.lunarforge.module.modules.visual.ModuleColorSaturation;
import com.example.lunarforge.module.modules.visual.ModuleMenuBlur;
import com.example.lunarforge.module.modules.visual.ModuleMotionBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

public final class RenderHooks {
    private RenderHooks() {}

    public static void postProcess(float partialTicks) {
        if (!OpenGlHelper.isFramebufferEnabled()) return;
        Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
        ModuleMotionBlur.postProcess(main);
        ModuleColorSaturation.postProcess(main);
        ModuleMenuBlur.postProcess(main);
        main.bindFramebuffer(true);
    }
}
