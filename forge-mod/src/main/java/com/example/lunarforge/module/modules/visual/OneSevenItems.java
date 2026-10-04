package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class OneSevenItems extends Module {
    final BoolSetting blockHitAnimation = bool("blockHitAnimation", true);
    final BoolSetting itemTransforms = bool("itemTransforms", true);
    final BoolSetting thirdPersonHeldItems = bool("thirdPersonHeldItems", true);
    final BoolSetting firstPersonCarpet = bool("firstPersonCarpet", true);
    final BoolSetting firstPersonFishingRod = bool("firstPersonFishingRod", true);
    final BoolSetting firstPersonSword = bool("firstPersonSword", true);
    final BoolSetting firstPersonPotions = bool("firstPersonPotions", true);
    final BoolSetting firstPersonFood = bool("firstPersonFood", true);
    final BoolSetting firstPersonBow = bool("firstPersonBow", true);

    private static final Set<Class<?>> TOOLS = new HashSet<Class<?>>(Arrays.<Class<?>>asList(ItemSword.class, ItemSpade.class, ItemAxe.class, ItemHoe.class, ItemPickaxe.class));

    private Set<Item> thirdPersonSkip, toolItems;

    private boolean swinging;
    private int swingTicks;
    private float swingNow, swingPrev;
    private boolean fresh = true;

    boolean forcePlacing;

    boolean rendering;

    OneSevenItems() { super("ONE_SEVEN_ITEMS_LEGACY", true); }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(blockHitAnimation, itemTransforms, thirdPersonHeldItems));
        page.section("itemSpecificToggles", s -> s.add(firstPersonSword, firstPersonBow, firstPersonFishingRod, firstPersonCarpet, firstPersonPotions, firstPersonFood));
    }

    public boolean blockHit() { return isEnabled() && blockHitAnimation.on(); }

    private static int swingEnd(EntityLivingBase e) { return OneSevenHooks.swingEnd(e); }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (mc.theWorld == null || player == null || !isEnabled() || !blockHitAnimation.on()) return;
        int end = swingEnd(player);
        float progress = player.getSwingProgress(1.0f);
        int ticks = Math.round(progress * end);
        boolean attack = mc.gameSettings.keyBindAttack.isKeyDown(), use = mc.gameSettings.keyBindUseItem.isKeyDown();
        boolean blockHitting = player.isUsingItem() && attack && mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
        if (attack && use && mc.playerController.func_181040_m()) forcePlacing = true;
        if (ticks > 0) {
            if (ticks >= end / 2 && blockHitting) {
                if (fresh) { swingTicks = -1; swinging = true; }
                fresh = false;
            } else {
                swingTicks = ticks;
                swinging = true;
                swingPrev = player.getSwingProgress(0.0f);
                swingNow = progress;
                fresh = true;
                return;
            }
        } else if (blockHitting) {
            if (fresh || !swinging || swingTicks >= end / 2 || swingTicks < 0) { swingTicks = -1; swinging = true; }
            fresh = false;
        }
        if (swinging) {
            if (++swingTicks >= end) { swingTicks = 0; swinging = false; }
        } else {
            swingTicks = 0;
        }
        swingPrev = swingNow;
        swingNow = (float)swingTicks / end;
    }

    float swing(float original, float partialTicks) {
        if (!blockHit()) return original;
        float d = swingNow - swingPrev;
        if (d < 0) d += 1;
        return swingPrev + d * partialTicks;
    }

    private boolean handles(EnumAction action) {
        switch (action) {
            case NONE: return true;
            case EAT: return firstPersonFood.on();
            case DRINK: return firstPersonPotions.on();
            case BLOCK: return firstPersonSword.on();
            case BOW: return firstPersonBow.on();
            default: return false;
        }
    }

    boolean takesOver(ItemStack stack) {
        if (!isEnabled() || stack == null) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (stack.getItem() == Items.filled_map) return false;
        if (mc.getRenderItem().shouldRenderItemIn3D(stack)) {
            if (!(firstPersonCarpet.on() && Block.getBlockFromItem(stack.getItem()) == Blocks.carpet)) return false;
        }
        if ((stack.getItem() == Items.fishing_rod || stack.getItem() == Items.carrot_on_a_stick) && !firstPersonFishingRod.on()) return false;
        return handles(stack.getItemUseAction());
    }

    void renderFirstPerson(ItemRenderer renderer, ItemStack stack, float partialTicks, float equip) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        boolean carpet = mc.getRenderItem().shouldRenderItemIn3D(stack);
        EnumAction action = stack.getItemUseAction();
        float swing = swing(player.getSwingProgress(partialTicks), partialTicks);
        int inUse = player.getItemInUseCount();
        if (action == EnumAction.BLOCK && swing > 0.01f && inUse > 0) equip = 1.0f;
        GlStateManager.pushMatrix();
        if (inUse > 0 && action != EnumAction.NONE) {
            switch (action) {
                case EAT: case DRINK:
                    eat(inUse, stack.getMaxItemUseDuration(), partialTicks);
                    transform(equip, swing);
                    break;
                case BLOCK:
                    if (swing < 0.01f || blockHitAnimation.on()) { transform(equip, swing); block(); }
                    else { swingTranslate(swing); transform(equip, swing); }
                    break;
                case BOW:
                    transform(equip, swing);
                    bow(inUse, stack.getMaxItemUseDuration(), partialTicks);
                    break;
                default: break;
            }
        } else {
            swingTranslate(swing);
            transform(equip, swing);
        }

        if (!itemTransforms.on()) {
            renderer.renderItem(player, stack, ItemCameraTransforms.TransformType.FIRST_PERSON);
            GlStateManager.popMatrix();
            return;
        }
        if (stack.getItem().shouldRotateAroundWhenRendering()) GlStateManager.rotate(180, 0, 1, 0);
        itemTransform();
        rendering = true;
        try {
            if (carpet) {
                carpet();
                GlStateManager.translate(0, -0.6f, 0);
                renderer.renderItem(player, stack, ItemCameraTransforms.TransformType.FIRST_PERSON);
            } else {
                renderer.renderItem(player, stack, ItemCameraTransforms.TransformType.NONE);
            }
        } finally {
            rendering = false;
        }
        GlStateManager.popMatrix();
    }

    void thirdPerson(EntityLivingBase entity) {
        if (!isEnabled() || entity instanceof EntityArmorStand) return;
        ItemStack stack = entity.getHeldItem();
        if (stack == null || stack.getItem() == null) return;
        if (thirdPersonSkip == null) {
            thirdPersonSkip = new HashSet<Item>(Arrays.asList(Items.carrot_on_a_stick, Items.fishing_rod, Items.name_tag, Items.lead, Items.skull));
            toolItems = new HashSet<Item>(Arrays.asList(Items.blaze_rod, Items.stick));
        }
        Item item = stack.getItem();
        if (thirdPersonSkip.contains(item)) return;
        boolean blocking = entity instanceof EntityPlayer && ((EntityPlayer)entity).isBlocking();
        if (blocking && blockHitAnimation.on()) {
            GlStateManager.translate(-0.05f, 0.1f, -0.12f);
            GlStateManager.rotate(-45, 0, 1, 0);
            GlStateManager.rotate(-30, 1, 0, 0);
            GlStateManager.rotate(-60, 0, 0, 1);
        }
        if (!thirdPersonHeldItems.on()) return;
        boolean tool = toolItems.contains(item);
        if (!tool) for (Class<?> c : TOOLS) if (c.isInstance(item)) { tool = true; break; }
        if (tool) {
            if (!blocking) {
                GlStateManager.rotate(20, -1, 0, 0);
                GlStateManager.rotate(5, 0, 0, 1);
                GlStateManager.translate(-0.01f, 0.03f, 0.1f);
            } else {
                GlStateManager.rotate(15, 0, 1, 0);
                GlStateManager.translate(0.1f, 0.08f, 0);
            }
        } else if (!(item instanceof ItemBlock)) {
            if (item == Items.bow) {
                GlStateManager.translate(0.03f, -0.05f, -0.1f);
                GlStateManager.rotate(10, -1.5f, -1, 1);
            } else {
                GlStateManager.rotate(180, 0, 0, 1);
                GlStateManager.translate(0, -(entity.isSneaking() ? 0.695f : 0.295f), -0.03f);
                GlStateManager.rotate(13.5f, 1, 0.15f, 0);
                GlStateManager.rotate(15, 0, 0.9f, 0.8f);
                GlStateManager.translate(0.01f, 0, 0);
            }
        }
    }

    private static void itemTransform() {
        GlStateManager.translate(0.588f, 0.365f, -0.795f);
        GlStateManager.translate(0, -0.3f, 0);
        GlStateManager.scale(1.5f, 1.5f, 1.5f);
        GlStateManager.rotate(50, 0, 1, 0);
        GlStateManager.rotate(335, 0, 0, 1);
        GlStateManager.translate(-0.9375f, -0.0625f, 0);
        GlStateManager.scale(-1, 1, -1);
    }

    private static void carpet() {
        GlStateManager.scale(1.1764705f, 1.1764705f, 1.1764705f);
        GlStateManager.rotate(-25, 0, 0, 1);
        GlStateManager.rotate(135, 0, 1, 0);
        GlStateManager.translate(0, -0.25f, -0.125f);
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
    }

    private static void transform(float equip, float swing) {
        GlStateManager.translate(0.56f, -0.52f - (1.0f - equip) * 0.6f, -0.72f);
        GlStateManager.rotate(45, 0, 1, 0);
        float a = (float)Math.sin(swing * swing * Math.PI), b = (float)Math.sin(Math.sqrt(swing) * Math.PI);
        GlStateManager.rotate(-a * 20, 0, 1, 0);
        GlStateManager.rotate(-b * 20, 0, 0, 1);
        GlStateManager.rotate(-b * 80, 1, 0, 0);
        GlStateManager.scale(0.4f, 0.4f, 0.4f);
    }

    private static void swingTranslate(float swing) {
        float a = (float)Math.sin(swing * Math.PI), b = (float)Math.sin(Math.sqrt(swing) * Math.PI);
        GlStateManager.translate(-b * 0.4f, (float)Math.sin(Math.sqrt(swing) * Math.PI * 2.0) * 0.2f, -a * 0.2f);
    }

    private static void block() {
        GlStateManager.translate(-0.5f, 0.2f, 0);
        GlStateManager.rotate(30, 0, 1, 0);
        GlStateManager.rotate(-80, 1, 0, 0);
        GlStateManager.rotate(60, 0, 1, 0);
    }

    private static void eat(int inUse, int max, float partialTicks) {
        float left = inUse - partialTicks + 1.0f;
        float done = 1.0f - left / max;
        float f = 1.0f - done;
        f = f * f * f; f = f * f * f; f = f * f * f;
        float g = 1.0f - f;
        GlStateManager.translate(0, (float)Math.abs(Math.cos(left / 4.0f * (float)Math.PI) * 0.1f) * (done > 0.2 ? 1 : 0), 0);
        GlStateManager.translate(g * 0.6f, -g * 0.5f, 0);
        GlStateManager.rotate(g * 90, 0, 1, 0);
        GlStateManager.rotate(g * 10, 1, 0, 0);
        GlStateManager.rotate(g * 30, 0, 0, 1);
    }

    private static void bow(int inUse, int max, float partialTicks) {
        GlStateManager.rotate(-18, 0, 0, 1);
        GlStateManager.rotate(-12, 0, 1, 0);
        GlStateManager.rotate(-8, 1, 0, 0);
        GlStateManager.translate(-0.9f, 0.2f, 0);
        float drawn = max - (inUse - partialTicks + 1.0f);
        float f = drawn / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        if (f > 1) f = 1;
        if (f > 0.1f) GlStateManager.translate(0, (float)Math.sin((drawn - 0.1f) * 1.3f) * 0.01f * (f - 0.1f), 0);
        GlStateManager.translate(0, 0, f * 0.1f);
        GlStateManager.rotate(-335, 0, 0, 1);
        GlStateManager.rotate(-50, 0, 1, 0);
        GlStateManager.translate(0, 0.5f, 0);
        GlStateManager.scale(1, 1, 1 + f * 0.2f);
        GlStateManager.translate(0, -0.5f, 0);
        GlStateManager.rotate(50, 0, 1, 0);
        GlStateManager.rotate(335, 0, 0, 1);
    }
}
