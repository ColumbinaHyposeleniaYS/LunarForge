package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Ported from Vape v4 (movement.PlayerMovementTask + TargetPositionMovementTask
 * + PlayerMovementTaskManager). A single active walk order that presses the
 * movement keys (through {@link ScaffoldInput}) until the projected position
 * is within tolerance of the target; when it completes, movement input is
 * either restored to the physical keys or released.
 */
final class WalkTask {
    double targetX;
    double targetZ;
    double tolerance = 0.2D;
    boolean completed;
    boolean targetReached;
    boolean waitForGroundAfterArrival;
    boolean restoreInputOnCompletion;

    WalkTask(double targetX, double targetZ) {
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    WalkTask tolerance(double value) { tolerance = value; return this; }

    WalkTask restoreInputOnCompletion(boolean value) { restoreInputOnCompletion = value; return this; }

    WalkTask waitForGroundAfterArrival(boolean value) { waitForGroundAfterArrival = value; return this; }

    boolean hasReachedTarget(Minecraft mc) {
        double remainingX = targetX - mc.thePlayer.posX;
        double remainingZ = targetZ - mc.thePlayer.posZ;
        return Math.abs(remainingX) <= tolerance && Math.abs(remainingZ) <= tolerance;
    }

    /** PlayerMovementTaskManager.onPreLocalPlayerTick: completion + input hand-back. */
    void updateCompletion(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer == null) return;
        if (hasReachedTarget(mc)) {
            targetReached = true;
            completed = true;
            return;
        }
        if (targetReached && (!waitForGroundAfterArrival || mc.thePlayer.onGround)) {
            completed = true;
        }
    }

    void finish(boolean restoreInput) {
        if (restoreInput) ScaffoldInput.restorePhysicalInput();
        else ScaffoldInput.releaseMovementKeys();
    }

    /** PlayerMovementTask.applyMovementInput: keep the sneak key physical, then walk. */
    void applyMovementInput(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        double remainingX = targetX - mc.thePlayer.posX;
        double remainingZ = targetZ - mc.thePlayer.posZ;
        if (remainingX != 0.0D || remainingZ != 0.0D) {
            KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
            if (GameplayUtil.physicalDown(sneakKey)) {
                ScaffoldInput.setPressed(sneakKey, true);
            } else {
                ScaffoldInput.setPressed(sneakKey, false);
            }
            ScaffoldInput.applyMovementToward(remainingX, remainingZ);
        }
    }
}
