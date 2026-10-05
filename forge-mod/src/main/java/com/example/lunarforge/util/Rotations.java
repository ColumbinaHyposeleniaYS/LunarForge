package com.example.lunarforge.util;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Rotation math and block checks for the Leader-Lite Scaffold port.
 * Mirrors the pieces of leader.util.RotationUtil / BlockUtil / ItemUtil the
 * Scaffold module uses.
 */
public final class Rotations {
    private static final Random RANDOM = new Random();

    private Rotations() {}

    public static float nextFloat(float min, float max) {
        return RANDOM.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(double min, double max) {
        return RANDOM.nextDouble() * (max - min) + min;
    }

    /** Leader RotationUtil.wrapAngleDiff: angle re-expressed around target. */
    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0F, Math.min(180.0F, maxAngle));
        if (angle > maxAngle) return maxAngle;
        if (angle < -maxAngle) return -maxAngle;
        return angle;
    }

    /** Leader RotationUtil.smoothAngle. */
    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5F + 0.5F * (1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + nextFloat(-0.1F, 0.1F)))));
    }

    /** Leader RotationUtil.quantizeAngle: strips sub-0.0096 jitter. */
    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % 0.0096D);
    }

    /** Leader RotationUtil.getRotationsTo: relative target, clamped and lightly smoothed. */
    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0F, 0.0F);
    }

    /** Leader RotationUtil.getRotations(target, currentYaw, currentPitch, maxAngle, smoothFactor). */
    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch,
            float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(targetZ, targetX) * 180.0D / Math.PI) - 90.0F - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(targetY, horizontalDistance) * 180.0D / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta)};
    }

    /** Leader RotationUtil.getRotations(point): from the player eyes to a world point. */
    public static float[] getRotations(double targetX, double targetY, double targetZ) {
        Minecraft mc = Minecraft.getMinecraft();
        return getRotations(targetX, targetY, targetZ,
                mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    /** Leader RotationUtil.getRotations(target, start). */
    public static float[] getRotations(double targetX, double targetY, double targetZ, double startX, double startY, double startZ) {
        double x = targetX - startX;
        double y = targetY - startY;
        double z = targetZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0D / Math.PI));
        return new float[]{yaw, pitch};
    }

    /** Leader RotationUtil.rayTrace: vanilla block trace from the eyes along the given rotation. */
    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = GameplayUtil.lookVector(yaw, pitch);
        return mc.theWorld.rayTraceBlocks(eye, eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance));
    }

    /** Same trace from an explicit eye position (Leader Scaffold.rayTraceFrom). */
    public static MovingObjectPosition rayTrace(Vec3 eye, float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 look = GameplayUtil.lookVector(yaw, pitch);
        return mc.theWorld.rayTraceBlocks(eye, eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance));
    }

    // ===== block checks (Leader BlockUtil) =====

    public static boolean isReplaceable(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        return isReplaceable(mc.theWorld.getBlockState(pos).getBlock());
    }

    public static boolean isReplaceable(Block block) {
        if (!block.getMaterial().isReplaceable()) return false;
        if (!(block instanceof BlockSnow)) return true;
        return !(block.getBlockBoundsMaxY() > 0.125D);
    }

    /** Leader BlockUtil.isInteractable(BlockPos); the block set itself lives in GameplayUtil. */
    public static boolean isInteractable(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        return GameplayUtil.isInteractable(mc.theWorld.getBlockState(pos).getBlock());
    }

    /** Leader BlockUtil.isSolid: full-cube-ish blocks only. */
    public static boolean isSolidBlock(Block block) {
        if (block instanceof BlockStairs) return false;
        if (block instanceof BlockSlab) return false;
        if (block instanceof BlockEndPortalFrame) return false;
        if (block instanceof BlockEndPortal) return false;
        if (block instanceof BlockVine) return false;
        if (block instanceof BlockPumpkin) return false;
        if (block instanceof BlockCactus) return false;
        if (block instanceof BlockBush) return false;
        if (block instanceof BlockFalling) return false;
        if (block instanceof BlockWeb) return false;
        if (block instanceof BlockPane) return false;
        if (block instanceof BlockCarpet) return false;
        if (block instanceof BlockSnow) return false;
        if (block instanceof BlockFence) return false;
        if (block instanceof BlockFenceGate) return false;
        if (block instanceof BlockWall) return false;
        if (block instanceof BlockLadder) return false;
        if (block instanceof BlockTorch) return false;
        if (block instanceof BlockRedstoneWire) return false;
        if (block instanceof BlockRedstoneDiode) return false;
        if (block instanceof BlockBasePressurePlate) return false;
        if (block instanceof BlockTripWire) return false;
        if (block instanceof BlockTripWireHook) return false;
        if (block instanceof BlockRailBase) return false;
        if (block instanceof BlockSlime) return false;
        return !(block instanceof BlockTNT);
    }

    // ===== placement vectors (Leader BlockUtil.getHitVec/getClickVec) =====

    public static Vec3 getHitVec(BlockPos pos, EnumFacing facing, float yaw, float pitch) {
        MovingObjectPosition mop = rayTrace(yaw, pitch, Minecraft.getMinecraft().playerController.getBlockReachDistance());
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(pos) && mop.sideHit == facing) {
            return mop.hitVec;
        }
        return getClickVec(pos, facing);
    }

    public static Vec3 getClickVec(BlockPos pos, EnumFacing facing) {
        Minecraft mc = Minecraft.getMinecraft();
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        double x = Math.min(Math.max(nextDouble(0.0D, 1.0D), block.getBlockBoundsMinX()), block.getBlockBoundsMaxX());
        double y = Math.min(Math.max(nextDouble(0.0D, 1.0D), block.getBlockBoundsMinY()), block.getBlockBoundsMaxY());
        double z = Math.min(Math.max(nextDouble(0.0D, 1.0D), block.getBlockBoundsMinZ()), block.getBlockBoundsMaxZ());
        switch (facing) {
            case UP: return new Vec3((double) pos.getX() + x, (double) pos.getY() + block.getBlockBoundsMaxY(), (double) pos.getZ() + z);
            case NORTH: return new Vec3((double) pos.getX() + x, (double) pos.getY() + y, (double) pos.getZ() + block.getBlockBoundsMinZ());
            case EAST: return new Vec3((double) pos.getX() + block.getBlockBoundsMaxX(), (double) pos.getY() + y, (double) pos.getZ() + z);
            case SOUTH: return new Vec3((double) pos.getX() + x, (double) pos.getY() + y, (double) pos.getZ() + block.getBlockBoundsMaxZ());
            case WEST: return new Vec3((double) pos.getX() + block.getBlockBoundsMinX(), (double) pos.getY() + y, (double) pos.getZ() + z);
            default: return new Vec3((double) pos.getX() + x, (double) pos.getY() + block.getBlockBoundsMinY(), (double) pos.getZ() + z);
        }
    }

    // ===== item checks (Leader ItemUtil.isBlock/isHoldingBlock) =====

    /** Leader ItemUtil.isBlock: a placeable, non-interactable, solid ItemBlock. */
    public static boolean isPlaceableBlock(ItemStack stack) {
        if (stack == null || stack.stackSize < 1) return false;
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).getBlock();
            return !GameplayUtil.isInteractable(block) && isSolidBlock(block);
        }
        return false;
    }

    public static boolean isHoldingPlaceableBlock() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && isPlaceableBlock(mc.thePlayer.getHeldItem());
    }
}
