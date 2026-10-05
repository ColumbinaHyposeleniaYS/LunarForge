package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.util.GameplayUtil;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

/**
 * Ported from Vape v4 (rotation.FixedRotationController + PointRotationController
 * + ScaffoldPointRotationController), collapsed into one class and stepped once
 * per client tick. Rotation targets are either fixed angles (GodBridge) or a
 * world point recomputed every tick (TellyBridge); steps accumulate as synthetic
 * mouse pixels and are applied through EntityPlayerSP.setAngles so the camera
 * math (including the sensitivity curve and previous-rotation bookkeeping)
 * stays identical to a real mouse move.
 */
final class ScaffoldRotation {
    static final float UNSET = -999.0f;

    private boolean pointMode;
    private final double[] point = new double[3];
    private float targetYaw = UNSET;
    private float targetPitch = UNSET;
    private float speed = 1.0f;
    private float tolerance = 3.0f;
    private boolean clampStepToRemaining;
    private boolean linearAcceleration;
    private boolean scaleAxesProportionally;
    private boolean retainAfterCompletion;
    private boolean complete;
    private float pendingYawDelta;
    private float pendingPitchDelta;
    private boolean speedInitialized;
    private BooleanSupplier yawGate;

    static float mouseScale(Minecraft mc) {
        float base = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        return base * base * base * 8.0f;
    }

    void setSpeed(float value) { speed = Math.min(Math.max(2.0f, value), 12.0f); }

    ScaffoldRotation tolerance(float value) { tolerance = value; return this; }

    ScaffoldRotation clampStepToRemaining() { clampStepToRemaining = true; return this; }

    ScaffoldRotation linearAcceleration() { linearAcceleration = true; return this; }

    ScaffoldRotation scaleAxesProportionally() { scaleAxesProportionally = true; return this; }

    ScaffoldRotation retainAfterCompletion() { retainAfterCompletion = true; return this; }

    ScaffoldRotation yawGate(BooleanSupplier gate) { yawGate = gate; return this; }

    void setTargetRotation(float yaw, float pitch) {
        pointMode = false;
        targetYaw = yaw;
        targetPitch = pitch;
        complete = false;
    }

    void setTargetPoint(double x, double y, double z) {
        pointMode = true;
        point[0] = x;
        point[1] = y;
        point[2] = z;
        complete = false;
    }

    float getTargetYaw() { return targetYaw; }

    boolean isPointMode() { return pointMode; }

    boolean isComplete() { return complete; }

    void setComplete(boolean value) { complete = value; }

    boolean shouldRetain() { return retainAfterCompletion; }

    void setRetainAfterCompletion(boolean value) { retainAfterCompletion = value; }

    /** ScaffoldPointRotationController: one-time speed initialisation once the gate opens. */
    void initSpeedOnce(float value) {
        if (!speedInitialized) {
            setSpeed(value);
            speedInitialized = true;
        }
    }

    private static double wrap180(double angle) {
        angle %= 360.0D;
        if (angle >= 180.0D) angle -= 360.0D;
        if (angle < -180.0D) angle += 360.0D;
        return angle;
    }

    private boolean outsideTolerance(double absoluteError, float rotationPerStep) {
        return Math.round(absoluteError / rotationPerStep) > Math.max(Math.round(tolerance / rotationPerStep), 0L);
    }

    private double acceleration(double step, double absoluteError, float yawFactor, float pitchFactor) {
        if (linearAcceleration) return step + absoluteError * 0.05D;
        return step;
    }

    private float addPendingStep(float pendingDelta, double error, double step, float rotationPerStep) {
        if (!clampStepToRemaining) {
            return (float) (error > 0.0D ? pendingDelta + step : pendingDelta - step);
        }
        double remainingSteps = Math.abs(error / rotationPerStep);
        double appliedStep = Math.min(step, remainingSteps);
        return (float) (error > 0.0D ? pendingDelta + appliedStep : pendingDelta - appliedStep);
    }

    /** One controller update + mouse delta application (Vape MouseRotationController.update/applyPendingMovement). */
    void update(Minecraft mc) {
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (complete && !retainAfterCompletion) return;
        float ms = mouseScale(mc);
        float rotationPerStep = ms * 0.15f;

        if (pointMode) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
            targetYaw = GameplayUtil.aimYaw(eye.xCoord, eye.zCoord, point[0], point[2]);
            targetPitch = GameplayUtil.aimPitch(eye.xCoord, eye.yCoord, eye.zCoord, point[0], point[1], point[2]);
        }
        if (targetYaw == UNSET || targetPitch == UNSET) return;

        // ===== yaw (ScaffoldPointRotationController gates only the yaw axis) =====
        boolean yawDone = true;
        if (yawGate == null || yawGate.getAsBoolean()) {
            float predictedYaw = mc.thePlayer.rotationYaw + (int) pendingYawDelta * ms * 0.15f;
            double yawError = wrap180(targetYaw - predictedYaw);
            double absoluteYawError = Math.abs(yawError);
            if (outsideTolerance(absoluteYawError, rotationPerStep)) {
                double step = speed * 0.25D;
                double pitchError = Math.abs(wrap180(targetPitch - mc.thePlayer.rotationPitch));
                if (scaleAxesProportionally) {
                    double axisRatio = absoluteYawError / Math.max(Math.abs(pitchError), 1.0E-4D);
                    if (axisRatio < 1.0D) step *= axisRatio;
                }
                step = acceleration(step, absoluteYawError, 1.0f, 1.0f);
                pendingYawDelta = addPendingStep(pendingYawDelta, yawError, step, rotationPerStep);
                yawDone = false;
            }
        }

        // ===== pitch =====
        boolean pitchDone = true;
        float currentPitch = mc.thePlayer.rotationPitch == -90.0f ? -89.99f : mc.thePlayer.rotationPitch;
        float predictedPitch = currentPitch - (int) (-pendingPitchDelta) * ms * 0.15f;
        double pitchError = wrap180(targetPitch - predictedPitch);
        double absolutePitchError = Math.abs(pitchError);
        if (outsideTolerance(absolutePitchError, rotationPerStep)) {
            double step = speed * 0.25D;
            double yawErrorAbs = Math.abs(wrap180(targetYaw - mc.thePlayer.rotationYaw));
            if (scaleAxesProportionally) {
                double axisRatio = absolutePitchError / Math.max(yawErrorAbs, 1.0E-4D);
                if (axisRatio < 1.0D) step *= axisRatio;
            }
            step = acceleration(step, absolutePitchError, 1.0f, 1.0f);
            pendingPitchDelta = addPendingStep(pendingPitchDelta, pitchError, step, rotationPerStep);
            pitchDone = false;
        }

        // ===== apply pending mouse pixels =====
        int yawSteps = (int) pendingYawDelta;
        int pitchSteps = (int) pendingPitchDelta;
        float remainingYaw = pendingYawDelta - yawSteps;
        float remainingPitch = pendingPitchDelta - pitchSteps;
        if (Math.abs(yawSteps) == 0) yawSteps = 0;
        if (Math.abs(pitchSteps) == 0) pitchSteps = 0;
        if (yawSteps != 0 || pitchSteps != 0) {
            // applyTrackedMouseDelta: yaw += steps*ms*0.15, pitch += steps*ms*0.15
            mc.thePlayer.setAngles(yawSteps * ms, -pitchSteps * ms);
        }
        pendingYawDelta = remainingYaw;
        pendingPitchDelta = remainingPitch;

        if (yawDone && pitchDone && Math.abs(pendingYawDelta) < 1.0f && Math.abs(pendingPitchDelta) < 1.0f) {
            complete = true;
        }
    }
}
