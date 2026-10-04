package com.example.lunarforge.util;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Shared helpers for the gameplay (mechanic) modules ported from
 * Leader-Lite (AutoClicker / FastPlace / InventoryClicker / AutoTool).
 */
public final class GameplayUtil {
    private static final Random RANDOM = new Random();

    private GameplayUtil() {}

    // ===== random =====

    public static long nextLong(long min, long max) {
        if (max < min) return min;
        return (long) (RANDOM.nextDouble() * (max + 1L - min) + min);
    }

    // ===== key/mouse state =====

    public static boolean physicalDown(KeyBinding key) {
        int code = key.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    public static boolean physicalDown(int code) {
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    /** Restores a KeyBinding's pressed state to the actual physical mouse/keyboard state. */
    public static void updateKeyState(int code) {
        KeyBinding.setKeyBindState(code, physicalDown(code));
    }

    // ===== look / ray trace =====

    /** Direction vector for arbitrary yaw/pitch (same math as Entity.getVectorForRotation). */
    public static Vec3 lookVector(float yaw, float pitch) {
        double d0 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        double d1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        double d2 = -MathHelper.cos(-pitch * 0.017453292F);
        double d3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(d1 * d2, d3, d0 * d2);
    }

    /** Block ray trace from the eyes along arbitrary yaw/pitch (partial ticks fixed at 1.0). */
    public static MovingObjectPosition rayTraceBlocks(float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eye, end);
    }

    /** Whether a view ray from the eyes hits the given (expanded) bounding box. */
    public static boolean rayTraceIntersects(AxisAlignedBB box, float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = lookVector(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return box.calculateIntercept(eye, end) != null;
    }

    // ===== block properties =====

    public static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer) return true;
        if (block instanceof BlockWorkbench) return true;
        if (block instanceof BlockAnvil) return true;
        if (block instanceof BlockBed) return true;
        if (block instanceof BlockDoor && block.getMaterial() != Material.iron) return true;
        if (block instanceof BlockTrapDoor) return true;
        if (block instanceof BlockFenceGate) return true;
        if (block instanceof BlockFence) return true;
        if (block instanceof BlockButton) return true;
        if (block instanceof BlockLever) return true;
        return block instanceof BlockJukebox;
    }

    // ===== item helpers =====

    public static boolean isSword(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    public static boolean isTool(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemTool;
    }

    /**
     * Leader-Lite's hasRawUnbreakingEnchant: true when the held item behaves like a
     * weapon for auto-click purposes (swords, knockback-enchanted items, certain
     * Hypixel/UHC items that look like shovels but are weapons).
     */
    public static boolean hasRawUnbreakingEnchant(ItemStack stack) {
        if (stack == null) return false;
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("ExtraAttributes")) {
                NBTTagCompound extra = tag.getCompoundTag("ExtraAttributes");
                if (extra.hasKey("UHCid")) {
                    long id = extra.getLong("UHCid");
                    if (id == 50006L || id == 50009L) return true;
                }
            }
            if (tag.hasKey("HideFlags")
                    && stack.getItem() instanceof net.minecraft.item.ItemSpade
                    && ((net.minecraft.item.ItemSpade) stack.getItem()).getToolMaterial() == Item.ToolMaterial.EMERALD) {
                return true;
            }
        }
        if (stack.getItem() instanceof net.minecraft.item.ItemEnchantedBook) return false;
        if (EnchantmentHelper.getEnchantments(stack).containsKey(19)) return true;
        return isSword(stack);
    }

    /** Best hotbar slot (0-8) for breaking the given block, current slot kept if nothing better. */
    public static int findToolSlot(int currentSlot, Block block) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack current = mc.thePlayer.inventory.getStackInSlot(currentSlot);
        int bestSlot = currentSlot;
        float bestStrength = current != null ? current.getStrVsBlock(block) : 1.0F;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null) continue;
            float strength = stack.getStrVsBlock(block);
            if (strength > bestStrength) {
                bestSlot = i;
                bestStrength = strength;
            }
        }
        return bestSlot;
    }
}
