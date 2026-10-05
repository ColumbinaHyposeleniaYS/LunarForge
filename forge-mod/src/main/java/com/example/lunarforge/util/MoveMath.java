package com.example.lunarforge.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;

/**
 * Movement-input math for the Leader-Lite Scaffold port (the targetless
 * pieces of leader.util.MoveUtil; TargetStrafe has no LunarForge
 * counterpart and is omitted). fixStrafe writes into the passed
 * {@link MovementInput} so it can run inside {@link PlayerInputHook}.
 */
public final class MoveMath {
    private MoveMath() {}

    public static boolean isForwardPressed() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.keyBindForward.isKeyDown() != mc.gameSettings.keyBindBack.isKeyDown()) return true;
        return mc.gameSettings.keyBindLeft.isKeyDown() != mc.gameSettings.keyBindRight.isKeyDown();
    }

    public static int getForwardValue() {
        Minecraft mc = Minecraft.getMinecraft();
        int forward = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) ++forward;
        if (mc.gameSettings.keyBindBack.isKeyDown()) --forward;
        return forward;
    }

    public static int getLeftValue() {
        Minecraft mc = Minecraft.getMinecraft();
        int left = 0;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) ++left;
        if (mc.gameSettings.keyBindRight.isKeyDown()) --left;
        return left;
    }

    /** Leader MoveUtil.adjustYaw: yaw the current key input points towards. */
    public static float adjustYaw(float yaw, int forward, int strafe) {
        if (forward < 0) yaw += 180.0F;
        if (strafe != 0) {
            float multiplier = forward == 0 ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return MathHelper.wrapAngleTo180_float(yaw);
    }

    private static float getAllowedHorizontalDistance() {
        Minecraft mc = Minecraft.getMinecraft();
        float slipperiness = mc.theWorld.getBlockState(new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ))).getBlock().slipperiness * 0.91F;
        return mc.thePlayer.getAIMoveSpeed() * (0.16277136F / (slipperiness * slipperiness * slipperiness));
    }

    /** Leader MoveUtil.predictMovement: next-tick ground input delta. */
    public static double[] predictMovement() {
        Minecraft mc = Minecraft.getMinecraft();
        float strafeInput = (float) getLeftValue() * 0.98F;
        float forwardInput = (float) getForwardValue() * 0.98F;
        float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude >= 1.0E-4F) {
            inputMagnitude = MathHelper.sqrt_float(inputMagnitude);
            if (inputMagnitude < 1.0F) inputMagnitude = 1.0F;
            inputMagnitude = getAllowedHorizontalDistance() / inputMagnitude;
            float sinYaw = MathHelper.sin(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
            float cosYaw = MathHelper.cos(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
            strafeInput *= inputMagnitude;
            forwardInput *= inputMagnitude;
            return new double[]{
                    (double) (strafeInput * cosYaw - forwardInput * sinYaw),
                    (double) (forwardInput * cosYaw + strafeInput * sinYaw)};
        }
        return new double[]{0.0D, 0.0D};
    }

    /**
     * Leader MoveUtil.fixStrafe: quantized 8-direction input re-projection so
     * client movement matches the silently reported yaw.
     */
    public static void fixStrafe(float targetYaw, MovementInput input) {
        Minecraft mc = Minecraft.getMinecraft();
        float angle = MathHelper.wrapAngleTo180_float(
                adjustYaw(mc.thePlayer.rotationYaw, getForwardValue(), getLeftValue()) - targetYaw + 22.5F);
        switch ((int) (angle + 180.0F) / 45 % 8) {
            case 0: input.moveForward = -1.0F; input.moveStrafe = 0.0F; break;
            case 1: input.moveForward = -1.0F; input.moveStrafe = 1.0F; break;
            case 2: input.moveForward = 0.0F; input.moveStrafe = 1.0F; break;
            case 3: input.moveForward = 1.0F; input.moveStrafe = 1.0F; break;
            case 4: input.moveForward = 1.0F; input.moveStrafe = 0.0F; break;
            case 5: input.moveForward = 1.0F; input.moveStrafe = -1.0F; break;
            case 6: input.moveForward = 0.0F; input.moveStrafe = -1.0F; break;
            case 7: input.moveForward = -1.0F; input.moveStrafe = -1.0F; break;
        }
        if (input.sneak) {
            input.moveForward *= 0.3F;
            input.moveStrafe *= 0.3F;
        }
    }

    /** Leader PlayerUtil.isAirAbove: whether collidable blocks sit right above the player. */
    public static boolean isAirAbove() {
        Minecraft mc = Minecraft.getMinecraft();
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().offset(0.0D, 1.0D, 0.0D)).isEmpty();
    }
}
