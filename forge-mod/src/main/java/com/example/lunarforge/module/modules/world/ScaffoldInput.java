package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;

/**
 * Ported from Vape v4 (gg.vape.movement.MovementInputHelper), the movement
 * input side of GodBridge/TellyBridge. The module temporarily owns the
 * movement keys: "pressed" becomes a programmatic state (setKeyBindState +
 * onTick, like a real key press), releases go through setKeyBindState(false),
 * and restorePhysicalInput hands every key back to the raw keyboard state.
 */
final class ScaffoldInput {
    private ScaffoldInput() {}

    static void setPressed(KeyBinding key, boolean pressed) {
        if (key == null) return;
        int code = key.getKeyCode();
        if (pressed) {
            KeyBinding.setKeyBindState(code, true);
            KeyBinding.onTick(code);
        } else {
            KeyBinding.setKeyBindState(code, false);
        }
    }

    static void releaseMovementKeys() {
        GameSettings s = Minecraft.getMinecraft().gameSettings;
        setPressed(s.keyBindForward, false);
        setPressed(s.keyBindBack, false);
        setPressed(s.keyBindLeft, false);
        setPressed(s.keyBindRight, false);
    }

    static void releaseAllInput() {
        GameSettings s = Minecraft.getMinecraft().gameSettings;
        releaseMovementKeys();
        setPressed(s.keyBindSprint, false);
        setPressed(s.keyBindSneak, false);
        setPressed(s.keyBindJump, false);
    }

    static void restorePhysicalInput() {
        GameSettings s = Minecraft.getMinecraft().gameSettings;
        setPressed(s.keyBindLeft, GameplayUtil.physicalDown(s.keyBindLeft));
        setPressed(s.keyBindRight, GameplayUtil.physicalDown(s.keyBindRight));
        setPressed(s.keyBindForward, GameplayUtil.physicalDown(s.keyBindForward));
        setPressed(s.keyBindBack, GameplayUtil.physicalDown(s.keyBindBack));
        setPressed(s.keyBindSprint, GameplayUtil.physicalDown(s.keyBindSprint));
        setPressed(s.keyBindSneak, GameplayUtil.physicalDown(s.keyBindSneak));
        setPressed(s.keyBindJump, GameplayUtil.physicalDown(s.keyBindJump));
    }

    /** Vape getMovementStep: 0.2 normal, 0.06 sneaking, 0.3 sprint-forward, halved hundredfold in air. */
    private static double movementStep(Minecraft mc, KeyBinding key) {
        double step = 0.2D;
        if (mc.thePlayer.isSneaking() && mc.thePlayer.onGround) {
            step = 0.06D;
        } else if (mc.thePlayer.isSprinting() && key == mc.gameSettings.keyBindForward) {
            step = 0.3D;
        }
        if (!mc.thePlayer.onGround) step *= 0.02D;
        return step;
    }

    private static double distanceToTargetAfterMove(Minecraft mc, double[] projected,
                                                    double[] delta, double offsetX, double offsetZ) {
        double targetX = mc.thePlayer.posX + offsetX;
        double targetZ = mc.thePlayer.posZ + offsetZ;
        double candidateX = projected[0] + delta[0];
        double candidateZ = projected[1] + delta[1];
        double dx = targetX - candidateX;
        double dz = targetZ - candidateZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double[] movementVector(Minecraft mc, double heading, KeyBinding key) {
        double step = movementStep(mc, key);
        double radians = Math.toRadians(heading);
        return new double[]{step * -Math.sin(radians), step * Math.cos(radians)};
    }

    /**
     * Vape applyMovementToward: greedy per-tick selection over the four
     * movement keys — a key stays pressed only while pressing it (with the
     * vanilla movement step estimate) brings the projected position closer
     * to the target offset.
     */
    static void applyMovementToward(double offsetX, double offsetZ) {
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings s = mc.gameSettings;
        double movementYaw = mc.thePlayer.rotationYaw % 360.0D;
        double[] headings = {movementYaw, movementYaw + 90.0D, movementYaw + 180.0D, movementYaw + 270.0D};
        KeyBinding[] keys = {s.keyBindForward, s.keyBindRight, s.keyBindBack, s.keyBindLeft};

        double[] projected = {mc.thePlayer.posX + mc.thePlayer.motionX, mc.thePlayer.posZ + mc.thePlayer.motionZ};
        boolean[] selected = new boolean[keys.length];
        double[] baseDelta = {0.0D, 0.0D};
        double bestDistance = distanceToTargetAfterMove(mc, projected, baseDelta, offsetX, offsetZ);
        for (int i = 0; i < keys.length; i++) {
            double[] delta = movementVector(mc, headings[i], keys[i]);
            double[] candidate = {projected[0] + delta[0], projected[1] + delta[1]};
            double candidateDistance = distanceToTargetAfterMove(mc, projected, delta, offsetX, offsetZ);
            if (candidateDistance < bestDistance) {
                selected[i] = true;
                projected[0] += delta[0];
                projected[1] += delta[1];
                bestDistance = candidateDistance;
            }
        }
        for (int i = 0; i < keys.length; i++) {
            if (selected[i] && !keys[i].isKeyDown()) setPressed(keys[i], true);
            else if (!selected[i] && keys[i].isKeyDown()) setPressed(keys[i], false);
        }
    }

    /** Vape hasSupportingCollision, without mutating the player: offset a box copy instead. */
    static boolean hasSupportingCollision(Minecraft mc, double projectedX, double projectedZ) {
        double dx = projectedX - mc.thePlayer.posX;
        double dz = projectedZ - mc.thePlayer.posZ;
        AxisAlignedBB bounds = mc.thePlayer.getEntityBoundingBox().offset(dx, 0.0D, dz);
        AxisAlignedBB support = bounds.expand(-0.15D, 0.0D, -0.15D)
                .offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, support).isEmpty();
    }
}
