package com.example.lunarforge.module.render;

import com.example.lunarforge.module.modules.visual.ModuleGlintColorizer;
import com.example.lunarforge.module.modules.visual.ModuleHitColor;
import com.example.lunarforge.module.modules.visual.ModuleShinyPots;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class ItemRenderHooks {
    public static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    public enum Target { GUI, ITEM, EQUIPPED_ARMOR }

    private static MethodHandle renderModelStack, renderModelColor;

    private static int gui;

    private ItemRenderHooks() {}

    private static MethodHandle handle(String mcp, String srg, Class<?>... args) {
        Method m = ReflectionHelper.findMethod(RenderItem.class, null, new String[]{mcp, srg}, args);
        try { return MethodHandles.lookup().unreflect(m); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    private static void init() {
        if (renderModelColor != null) return;
        renderModelStack = handle("renderModel", "func_175036_a", IBakedModel.class, ItemStack.class);
        renderModelColor = handle("renderModel", "func_175035_a", IBakedModel.class, int.class);
    }

    public static void renderModel(RenderItem ri, IBakedModel model, ItemStack stack) {
        try { renderModelStack.invoke(ri, model, stack); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void renderModel(RenderItem ri, IBakedModel model, int color) {
        try { renderModelColor.invoke(ri, model, color); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void guiStart() { gui++; }

    public static void guiEnd() { if (gui > 0) gui--; }

    public static boolean inGui() { return gui > 0; }

    public static boolean renderItem(RenderItem ri, ItemStack stack, IBakedModel model) {
        if (stack == null || model.isBuiltInRenderer()) return false;
        boolean shiny = ModuleShinyPots.applies(stack, inGui());
        Target target = inGui() ? Target.GUI : Target.ITEM;
        if (!shiny && !(stack.hasEffect() && ModuleGlintColorizer.handles(target))) return false;
        init();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        boolean behind = shiny && ModuleShinyPots.glintBehind();
        if (!behind) renderModel(ri, model, stack);
        if (shiny) {
            GlStateManager.pushMatrix();
            ModuleShinyPots.renderPotion(ri, model, ModuleShinyPots.color(stack));
            GlStateManager.popMatrix();
        }
        if (behind) renderModel(ri, model, stack);
        if (!shiny && stack.hasEffect()) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(GLINT);
            final IBakedModel m = model;
            boolean cancelled = glint(target, c -> renderModel(ri, m, c), null, 0.0f);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

            if (!cancelled) com.example.lunarforge.module.modules.visual.OneSevenHooks.renderEffect(ri, model);
        }
        GlStateManager.popMatrix();
        return true;
    }

    public static boolean glint(Target target, IntConsumer render, EntityLivingBase wearer, float time) {
        boolean cancelled = target == Target.EQUIPPED_ARMOR && ModuleHitColor.skipArmorGlint(wearer);
        if (!cancelled) cancelled = ModuleGlintColorizer.glint(target, render, wearer, time);
        return cancelled;
    }

    public static boolean armorGlint(EntityLivingBase entity, final ModelBase model, final float limbSwing, final float limbSwingAmount,
                                     float partialTicks, final float ageInTicks, final float netHeadYaw, final float headPitch, final float scale) {
        if (!ModuleHitColor.skipArmorGlint(entity) && !ModuleGlintColorizer.handles(Target.EQUIPPED_ARMOR)) return false;
        final EntityLivingBase e = entity;
        return glint(Target.EQUIPPED_ARMOR, c -> model.render(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale),
            entity, entity.ticksExisted + partialTicks);
    }

    public static boolean potion(ItemStack stack) { return stack.getItem() instanceof ItemPotion; }
}
