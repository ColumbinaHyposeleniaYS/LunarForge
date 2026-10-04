package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderEye;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemExpBottle;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class OneSevenHooks {
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    private static final Set<Class<?>> ALLOW_USE = new HashSet<Class<?>>(Arrays.<Class<?>>asList(
        ItemEnderPearl.class, ItemEnderEye.class, ItemSnowball.class, ItemEgg.class, ItemPotion.class, ItemExpBottle.class, ItemFishingRod.class));

    private static Field itemToRender, equippedProgress, prevEquippedProgress, leftClickCounter, isHittingBlock, blockHitDelay,
        currentItemHittingBlock, alwaysEdible;
    private static Method rotateArroundXAndY, setLightMapFromPlayer, rotateWithPlayerRotations, swingEnd, renderModel, renderEffect;
    private static ModuleOneSevenVisuals module;

    private static boolean leftClickPressed;

    private static boolean renderingGui;

    private OneSevenHooks() {}

    private static ModuleOneSevenVisuals module() {
        if (module == null) {
            Module m = ModuleManager.get("one_seven_visuals");
            if (m instanceof ModuleOneSevenVisuals) module = (ModuleOneSevenVisuals)m;
        }
        return module;
    }

    private static synchronized void reflect() {
        if (itemToRender != null) return;
        prevEquippedProgress = Fields.find(ItemRenderer.class, "prevEquippedProgress", "field_78451_d");
        equippedProgress = Fields.find(ItemRenderer.class, "equippedProgress", "field_78454_c");
        leftClickCounter = Fields.find(Minecraft.class, "leftClickCounter", "field_71429_W");
        isHittingBlock = Fields.find(PlayerControllerMP.class, "isHittingBlock", "field_78778_j");
        blockHitDelay = Fields.find(PlayerControllerMP.class, "blockHitDelay", "field_78781_i");
        currentItemHittingBlock = Fields.find(PlayerControllerMP.class, "currentItemHittingBlock", "field_85183_f");
        alwaysEdible = Fields.find(ItemFood.class, "alwaysEdible", "field_77852_bZ");
        rotateArroundXAndY = method(ItemRenderer.class, new String[] {"func_178101_a"}, float.class, float.class);
        setLightMapFromPlayer = method(ItemRenderer.class, new String[] {"func_178109_a"}, net.minecraft.client.entity.AbstractClientPlayer.class);
        rotateWithPlayerRotations = method(ItemRenderer.class, new String[] {"func_178110_a"}, EntityPlayerSP.class, float.class);
        swingEnd = method(EntityLivingBase.class, new String[] {"getArmSwingAnimationEnd", "func_82166_i"});
        renderModel = method(RenderItem.class, new String[] {"renderModel", "func_175035_a"}, IBakedModel.class, int.class);
        renderEffect = method(RenderItem.class, new String[] {"renderEffect", "func_180451_a"}, IBakedModel.class);
        itemToRender = Fields.find(ItemRenderer.class, "itemToRender", "field_78453_b");
    }

    private static Method method(Class<?> owner, String[] names, Class<?>... params) {
        return ReflectionHelper.findMethod((Class<Object>)owner, null, names, params);
    }

    private static Object call(Method m, Object target, Object... args) {
        try { return m.invoke(target, args); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static int swingEnd(EntityLivingBase entity) {
        reflect();
        return (Integer)call(swingEnd, entity);
    }

    public static boolean renderFirstPerson(ItemRenderer renderer, float partialTicks) {
        ModuleOneSevenVisuals m = module();
        if (m == null || !m.items.isEnabled()) return false;
        reflect();
        ItemStack stack = (ItemStack)Fields.get(itemToRender, renderer);
        if (stack == null || !m.items.takesOver(stack)) return false;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        float prev = Fields.getFloat(prevEquippedProgress, renderer), now = Fields.getFloat(equippedProgress, renderer);
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        call(rotateArroundXAndY, renderer, pitch, yaw);
        call(setLightMapFromPlayer, renderer, player);
        call(rotateWithPlayerRotations, renderer, player, partialTicks);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        m.items.renderFirstPerson(renderer, stack, partialTicks, prev + (now - prev) * partialTicks);
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        return true;
    }

    public static float hurtSin(float f) {
        ModuleOneSevenVisuals m = module();
        if (m != null && m.animations.hurtShake()) {
            float g = (float)Math.sqrt(Math.sqrt(f / Math.PI)) / 1.15f;
            f = (float)(g * g * g * g * Math.PI);
        }
        return MathHelper.sin(f);
    }

    public static void eyeTranslate(float x, float y, float z, float partialTicks) {
        ModuleOneSevenVisuals m = module();
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();
        if (m != null && entity != null && entity == mc.thePlayer) {
            float eye = entity.getEyeHeight();
            float smoothed = m.animations.eyeHeight(eye, 0.08f, partialTicks);
            float h = Float.isNaN(smoothed) ? eye : smoothed;
            if (((EntityLivingBase)entity).isPlayerSleeping()) h += 1.0f;
            y = -h;
        }
        GlStateManager.translate(x, y, z);
    }

    public static float swingProgress(float progress, EntityLivingBase entity, float partialTicks) {
        ModuleOneSevenVisuals m = module();
        if (m == null || entity != Minecraft.getMinecraft().thePlayer || !m.items.isEnabled()) return progress;
        return m.items.swing(progress, partialTicks);
    }

    public static void thirdPerson(EntityLivingBase entity) {
        ModuleOneSevenVisuals m = module();
        if (m != null && entity.getHeldItem() != null) m.items.thirdPerson(entity);
    }

    public static int lastHealth(int last) {
        ModuleOneSevenVisuals m = module();
        return m != null && m.animations.health() ? 0 : last;
    }

    public static void guiItem(boolean rendering) { renderingGui = rendering; }

    public static void renderEffect(RenderItem renderItem, IBakedModel model) {
        reflect();
        ModuleOneSevenVisuals m = module();
        if (m == null || !m.animations.glint()) {
            call(renderEffect, renderItem, model);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean gui = renderingGui;
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(768, 1);
        mc.getTextureManager().bindTexture(GLINT);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(768, 1, 1, 0);
        GlStateManager.matrixMode(5890);
        float scale = gui ? 8.0f : 4.0f;
        int color = gui ? -7309112 : -11062661;
        float f = (float)(Minecraft.getSystemTime() % 3000L) / 3000.0f / scale;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.translate(f, 0, 0);
        GlStateManager.rotate(-50, 0, 0, 1);
        call(renderModel, renderItem, model, color);
        GlStateManager.popMatrix();
        f = (float)(Minecraft.getSystemTime() % 4873L) / 4873.0f / scale;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        if (!gui) {
            GlStateManager.translate(-f, 0, 0);
            GlStateManager.rotate(10, 0, 0, 1);
        } else {
            GlStateManager.translate(f, 0, 0);
            GlStateManager.rotate(-50, 0, 0, 1);
        }
        call(renderModel, renderItem, model, color);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }

    public static void runTickStart() { leftClickPressed = false; }

    public static void runTickInput() {
        if (module() == null) return;
        KeyBinding attack = Minecraft.getMinecraft().gameSettings.keyBindAttack;
        if (attack.isPressed()) {
            KeyBinding.onTick(attack.getKeyCode());
            leftClickPressed = true;
        }
    }

    public static boolean hittingBlock(PlayerControllerMP controller) {
        ModuleOneSevenVisuals m = module();
        boolean vanilla = controller.getIsHittingBlock();
        if (m == null) return vanilla;
        if (m.items.forcePlacing) return false;
        reflect();
        if (leftClickPressed || !Fields.getBoolean(isHittingBlock, controller) || !m.useItemWhileDigging()) return vanilla;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        ItemStack stack = player.getHeldItem();
        if (stack == null) return vanilla;
        Item item = stack.getItem();
        if (item instanceof ItemFood) {
            if (!Fields.getBoolean(alwaysEdible, item) && !player.getFoodStats().needFood() || player.capabilities.disableDamage) return vanilla;
        } else if (stack.getItemUseAction() == EnumAction.NONE && !ALLOW_USE.contains(item.getClass())) {
            return vanilla;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mc.theWorld.getBlockState(hit.getBlockPos()).getBlock().getMaterial() != Material.air) {
            controller.resetBlockRemoving();
            Fields.setInt(blockHitDelay, controller, 5);
            Fields.set(currentItemHittingBlock, controller, null);
            return false;
        }
        return vanilla;
    }

    public static MovingObjectPosition.MovingObjectType blockType() {
        ModuleOneSevenVisuals m = module();
        return m != null && m.items.forcePlacing ? MovingObjectPosition.MovingObjectType.MISS : MovingObjectPosition.MovingObjectType.BLOCK;
    }

    public static void clickBlockDone() {
        ModuleOneSevenVisuals m = module();
        if (m != null) m.items.forcePlacing = false;
    }

    public static Material clickMaterial(net.minecraft.block.Block block) {
        ModuleOneSevenVisuals m = module();
        Minecraft mc = Minecraft.getMinecraft();
        if (m != null && m.items.blockHit() && mc.gameSettings.keyBindUseItem.isKeyDown() && !mc.playerController.getIsHittingBlock()) return Material.air;
        return block.getMaterial();
    }

    public static void clickStart() {
        ModuleOneSevenVisuals m = module();
        if (m == null || !m.alwaysSwing()) return;
        Minecraft mc = Minecraft.getMinecraft();
        reflect();
        if (Fields.getInt(leftClickCounter, mc) <= 0 || mc.thePlayer == null) return;
        EntityPlayerSP p = mc.thePlayer;

        if (!p.isSwingInProgress || p.swingProgressInt >= swingEnd(p) / 2 || p.swingProgressInt < 0) {
            p.swingProgressInt = -1;
            p.isSwingInProgress = true;
        }
    }
}
