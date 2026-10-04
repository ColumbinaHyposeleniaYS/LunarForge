package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.ItemRenderHooks;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;

public final class ModuleShinyPots extends Module {
    private static ModuleShinyPots instance;
    private final Map<Integer, Integer> colors = new HashMap<Integer, Integer>();
    private final BoolSetting coloredPotions = bool("coloredPotions", false);
    private final BoolSetting renderGlintBehindPotion = bool("renderGlintBehindPotion", false);
    private final BoolSetting renderEntireSlot = bool("renderEntireSlot", false);

    public ModuleShinyPots() {
        super("SHINY_POTS", false);
        instance = this;
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(coloredPotions, renderGlintBehindPotion, renderEntireSlot));
    }

    public static boolean applies(ItemStack stack, boolean gui) {
        ModuleShinyPots m = instance;
        return m != null && m.isEnabled() && gui && stack.getItem() != null && ItemRenderHooks.potion(stack) && stack.hasEffect();
    }

    public static boolean glintBehind() { return instance.renderGlintBehindPotion.on(); }

    public static int color(ItemStack stack) {
        ModuleShinyPots m = instance;
        if (!m.coloredPotions.on()) return -8372020;
        Integer c = m.colors.get(stack.getItemDamage());
        if (c == null) {
            c = stack.getItem().getColorFromItemStack(stack, 0) | 0xFF000000;
            m.colors.put(stack.getItemDamage(), c);
        }
        return c;
    }

    public static void renderPotion(RenderItem ri, IBakedModel model, int color) {
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(768, 1);
        if (instance.renderEntireSlot.on()) {
            GlStateManager.scale(1.25, 1.25, 1.25);
            GlStateManager.translate(-0.1, -0.1, 0.0);
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(ItemRenderHooks.GLINT);
        GlStateManager.matrixMode(5890);
        GlStateManager.pushMatrix();
        GlStateManager.scale(8.0F, 8.0F, 8.0F);
        float f = (float)(Minecraft.getSystemTime() % 3000L) / 3000.0F / 8.0F;
        GlStateManager.translate(f, 0.0F, 0.0F);
        GlStateManager.rotate(-50.0F, 0.0F, 0.0F, 1.0F);
        ItemRenderHooks.renderModel(ri, model, color);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scale(8.0F, 8.0F, 8.0F);
        float f1 = (float)(Minecraft.getSystemTime() % 4873L) / 4873.0F / 8.0F;
        GlStateManager.translate(-f1, 0.0F, 0.0F);
        GlStateManager.rotate(10.0F, 0.0F, 0.0F, 1.0F);
        ItemRenderHooks.renderModel(ri, model, color);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }
}
