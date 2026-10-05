package com.example.lunarforge.module.modules.combat;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.combat.AimAssist + its rotation
 * submodules). Smoothly drags the crosshair towards a valid target while you
 * fight.
 *
 * Simple: a velocity-buffer model that mimics human mouse motion —
 * acceleration, dead zones near the target, side flips when the target
 * crosses the view axis, proximity boost, random drift, and a conversion
 * through the in-game mouse sensitivity curve.
 *
 * Adaptive: faithful port of Vape's AimAssistTargetingSubModule. A background
 * worker thread (the same 1 ms loop Vape uses) picks the target and runs the
 * adaptive tracking engine: smoothed aim/lead points, air-time aim shifts,
 * overshoot detection, yaw/pitch bias accumulators, error-velocity damping,
 * flick handling, multi-band motion noise and a spring-based pitch drift.
 * The engine accumulates synthetic mouse pixels that a render-tick handler
 * applies through EntityPlayerSP.setAngles, so rotations move exactly like
 * real mouse input through the sensitivity curve.
 *
 * Not ported from Vape: the item whitelist UI ("Limit To Items" simply means
 * swords) and the pickaxe/shovel block-break whitelist (the check applies to
 * every block).
 */
public final class ModuleAimAssist extends Module {

    public enum AimMode implements ChoiceSetting.Option {
        SIMPLE("Simple"), ADAPTIVE("Adaptive");

        private final String label;

        AimMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum TargetMode implements ChoiceSetting.Option {
        YAW("Yaw"), DISTANCE("Distance"), HEALTH("Health"), ARMOR("Armor");

        private final String label;

        TargetMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum TargetArea implements ChoiceSetting.Option {
        CENTER("Center"), CLOSEST("Closest");

        private final String label;

        TargetArea(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private final ChoiceSetting<AimMode> mode = choice("mode", AimMode.SIMPLE).label(() -> "Mode");
    private final ChoiceSetting<TargetMode> targetMode = choice("targetMode", TargetMode.YAW).label(() -> "Target Mode");
    private final ChoiceSetting<TargetArea> targetArea = choice("targetArea", TargetArea.CENTER).label(() -> "Target Area");
    private final BoolSetting requireMouseDown = bool("requireMouseDown", true).label(() -> "Require Mouse Down");
    private final BoolSetting aimVertically = bool("aimVertically", false).label(() -> "Aim Vertically");
    private final BoolSetting strafeIncrease = bool("strafeIncrease", false).label(() -> "Strafe Increase");
    private final BoolSetting checkBlockBreak = bool("checkBlockBreak", false).label(() -> "Check Block Break");
    private final BoolSetting limitToItems = bool("limitToItems", false).label(() -> "Limit To Items (Sword)");
    private final NumberSetting horizontalSpeed = integer("horizontalSpeed", 5, 1, 10).label(() -> "Horizontal Speed");
    private final NumberSetting verticalSpeed = integer("verticalSpeed", 5, 1, 10).label(() -> "Vertical Speed");
    private final NumberSetting maxAngle = integer("maxAngle", 180, 1, 360).label(() -> "Max Angle");
    private final NumberSetting distance = decimal("distance", 5.0F, 1.0F, 8.0F).label(() -> "Distance");

    private final Random random = new Random();
    private final Random sharedRandom = new Random();
    private EntityLivingBase target;

    // Simple rotation engine state (Vape AimAssistRotationSubModule)
    private float horizontalVelocity;
    private float verticalVelocity;
    private float horizontalAccumulator;
    private int retargetCounter;
    private int sampleCounter;
    private int blockBreakCooldown;
    private boolean prevOnLeft;
    private boolean prevAbove;
    private float lastAngleDiff;
    private double prevTargetX;
    private double prevTargetZ;
    private double targetX;
    private double targetY;
    private double targetZ;

    // random drift state (simplified updateDrift)
    private int driftTimer = -150;
    private int driftInterval = 250;
    private int driftStepX;
    private int driftStepY;
    private int driftX;
    private int driftY;

    // ===== Adaptive engine state (Vape AimAssistTargetingSubModule) =====
    private static final float SNAP_ENTER_THRESHOLD = 5.0f;
    private static final float SNAP_EXIT_THRESHOLD = 7.0f;
    private static final float SNAP_MAX_ANGLE = 20.0f;

    private double aimX, aimY, aimZ;
    private double leadX, leadY, leadZ;
    private double predictedX, predictedY, predictedZ;
    private float pendingYaw, pendingPitch;
    private float aimStrength;
    private float yawBias, pitchBias;
    private float yawVelocity, pitchVelocity;
    private float yawAccel, pitchAccel;
    private float overshoot;
    private float lastYawDiff, lastPitchDiff;
    private float lastTargetYaw, lastTargetPitch;
    private float lastPlayerYaw, lastPlayerPitch;
    private float lastYawSign, lastPitchSign;
    private float yawFlickTicks, pitchFlickTicks;
    private float verticalVelocityAdaptive;
    private float airFactor;
    private double lastEyeY, lastGroundEyeY;
    private long lastFrameNanos;
    private long noiseStartNanos;
    private long driftNextNanos;
    private float driftPos, driftVelocity, driftTarget, driftNoise;
    private boolean initialized;
    private boolean aimPointInitialized;
    private boolean predictionInitialized;
    private boolean yawSnapped, pitchSnapped;
    private int targetSwitchTicks;

    // worker thread
    private Thread workerThread;
    private volatile boolean workerStop;

    public ModuleAimAssist() {
        super("AIM_ASSIST", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode);
            s.add(requireMouseDown, aimVertically);
            s.add(horizontalSpeed, verticalSpeed);
            s.add(maxAngle, distance);
            s.add(targetMode, targetArea);
            s.add(strafeIncrease, checkBlockBreak, limitToItems);
        });
    }

    @Override protected void onEnable() {
        startWorker();
    }

    @Override protected void onDisable() {
        target = null;
        resetRotationState();
        stopWorker();
    }

    // ===== worker thread (Vape AimAssistWorkerThread) =====

    private void startWorker() {
        if (workerThread != null && workerThread.isAlive()) return;
        workerStop = false;
        workerThread = new Thread(new Runnable() {
            @Override public void run() {
                while (!workerStop) {
                    try {
                        Thread.sleep(1L);
                        if (isEnabled() && mode.is(AimMode.ADAPTIVE)) {
                            runTargetLoop();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "LunarForge-AimAssist-Adaptive");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void stopWorker() {
        workerStop = true;
        workerThread = null;
    }

    private void resetRotationState() {
        horizontalVelocity = 0.0F;
        verticalVelocity = 0.0F;
        horizontalAccumulator = 0.0F;
        driftX = 0;
        driftY = 0;
        resetAdaptiveState();
    }

    private boolean canAim(Minecraft mc, int blockBreakCooldownTicks) {
        if (mc.currentScreen != null) return false;
        if (limitToItems.on() && !GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return false;
        if (checkBlockBreak.on()) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                blockBreakCooldown = blockBreakCooldownTicks;
            }
            if (blockBreakCooldown > 0) {
                --blockBreakCooldown;
                return false;
            }
        }
        return true;
    }

    private boolean isValidTarget(Minecraft mc, EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity) return false;
        if (entity.isDead || entity.getHealth() <= 0.0F) return false;
        if (mc.thePlayer.getDistanceToEntity(entity) >= distance.value()) return false;
        if (Math.abs(GameplayUtil.horizontalAngleTo(mc.thePlayer, entity.posX, entity.posZ))
                > maxAngle.intValue() / 2.0F) return false;
        return true;
    }

    private EntityLivingBase findBestTarget(final Minecraft mc) {
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity instanceof EntityArmorStand) continue;
            if (!isValidTarget(mc, entity)) continue;
            targets.add(entity);
        }
        if (targets.isEmpty()) return null;
        Collections.sort(targets, new Comparator<EntityLivingBase>() {
            @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                switch (targetMode.get()) {
                    case YAW:
                        return Float.compare(Math.abs(GameplayUtil.horizontalAngleTo(mc.thePlayer, a.posX, a.posZ)),
                                Math.abs(GameplayUtil.horizontalAngleTo(mc.thePlayer, b.posX, b.posZ)));
                    case HEALTH:
                        return Float.compare(a.getHealth(), b.getHealth());
                    case ARMOR:
                        return Float.compare(a.getTotalArmorValue(), b.getTotalArmorValue());
                    default:
                        return Float.compare(mc.thePlayer.getDistanceToEntity(a), mc.thePlayer.getDistanceToEntity(b));
                }
            }
        });
        return targets.get(0);
    }

    /** Worker-thread variant: copies the entity list first (Vape copies worldClient.z()). */
    private EntityLivingBase findBestTargetSafe(final Minecraft mc) {
        List<EntityLivingBase> targets = new ArrayList<EntityLivingBase>();
        List<?> loaded;
        try {
            loaded = new ArrayList<Object>(mc.theWorld.loadedEntityList);
        } catch (Exception e) {
            return null;
        }
        for (Object object : loaded) {
            if (!(object instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity instanceof EntityArmorStand) continue;
            if (!isValidTarget(mc, entity)) continue;
            targets.add(entity);
        }
        if (targets.isEmpty()) return null;
        Collections.sort(targets, new Comparator<EntityLivingBase>() {
            @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                switch (targetMode.get()) {
                    case YAW:
                        return Float.compare(Math.abs(GameplayUtil.horizontalAngleTo(mc.thePlayer, a.posX, a.posZ)),
                                Math.abs(GameplayUtil.horizontalAngleTo(mc.thePlayer, b.posX, b.posZ)));
                    case HEALTH:
                        return Float.compare(a.getHealth(), b.getHealth());
                    case ARMOR:
                        return Float.compare(a.getTotalArmorValue(), b.getTotalArmorValue());
                    default:
                        return Float.compare(mc.thePlayer.getDistanceToEntity(a), mc.thePlayer.getDistanceToEntity(b));
                }
            }
        });
        return targets.get(0);
    }

    // ===== Simple mode (unchanged from the earlier port) =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        if (!mode.is(AimMode.SIMPLE)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (!canAim(mc, 10)) {
            target = null;
            resetRotationState();
            return;
        }
        if (target != null && (target.isDead || mc.thePlayer.getDistanceToEntity(target) > distance.value())) {
            resetRotationState();
            target = null;
        }
        boolean mouseDown = GameplayUtil.physicalDown(mc.gameSettings.keyBindAttack);
        if (requireMouseDown.on() && !mouseDown) {
            target = null;
            resetRotationState();
            return;
        }
        if (requireMouseDown.on()) {
            if (target == null) target = findBestTarget(mc);
        } else {
            ++retargetCounter;
            if (retargetCounter > 700 || target == null || !isValidTarget(mc, target)) {
                target = findBestTarget(mc);
                retargetCounter = 0;
            }
        }
        if (target == null || mc.currentScreen != null) {
            resetRotationState();
            return;
        }
        applyRotation(mc);
    }

    /** Faithful port of Vape's applyRotation + queue*Adjustment + drift mouse model. */
    private void applyRotation(Minecraft mc) {
        targetX = target.posX;
        targetZ = target.posZ;
        targetY = target.posY + target.height * 0.5D;
        if (targetArea.is(TargetArea.CLOSEST)) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
            targetX = MathHelper.clamp_double(eye.xCoord, target.getEntityBoundingBox().minX, target.getEntityBoundingBox().maxX);
            targetY = MathHelper.clamp_double(eye.yCoord, target.getEntityBoundingBox().minY, target.getEntityBoundingBox().maxY);
            targetZ = MathHelper.clamp_double(eye.zCoord, target.getEntityBoundingBox().minZ, target.getEntityBoundingBox().maxZ);
        }
        double targetMotionX = targetX - prevTargetX;
        double targetMotionZ = targetZ - prevTargetZ;
        prevTargetX = targetX;
        prevTargetZ = targetZ;
        double predictedX = targetX + targetMotionX * 1.7D;
        double predictedZ = targetZ + targetMotionZ * 1.7D;

        float yawDiff = GameplayUtil.horizontalAngleTo(mc.thePlayer, predictedX, predictedZ);
        boolean targetOnLeft = yawDiff < 0.0F;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        float pitchDiff = MathHelper.wrapAngleTo180_float(
                GameplayUtil.aimPitch(eye.xCoord, eye.yCoord, eye.zCoord, targetX, targetY, targetZ) - mc.thePlayer.rotationPitch);
        boolean targetAbove = pitchDiff < 0.0F;
        float verticalDeadZone = Math.abs(pitchDiff) - 10.0F;

        updateDrift();

        float horizontalForce = 1.0F + sharedRandom.nextFloat() * 2.0F + Math.abs(yawDiff) / 50.0F;
        if (Math.abs(yawDiff - lastAngleDiff) > 6.0F) horizontalForce += Math.abs(yawDiff) / 35.0F;
        float proximityBoost = (float) Math.max(0.0D, (9.0F - mc.thePlayer.getDistanceToEntity(target)) / 2.5F - 2.0F);
        horizontalForce += proximityBoost;
        boolean strafingAway = targetOnLeft ? mc.thePlayer.moveStrafing < 0.0F : mc.thePlayer.moveStrafing > 0.0F;
        if (strafeIncrease.on() && strafingAway) horizontalForce *= 1.6F;
        if (mc.thePlayer.getDistanceToEntity(target) < 0.5F) horizontalForce /= 5.0F;
        float verticalForce = 1.0F + sharedRandom.nextFloat() * 2.0F + Math.abs(verticalDeadZone) / 50.0F;

        float horizontalAcceleration = horizontalForce / 90.0F * (targetOnLeft ? -1.0F : 1.0F);
        float verticalAcceleration = verticalForce / 90.0F * (targetAbove ? -1.0F : 1.0F);
        if (Math.abs(yawDiff) < 5.0F) {
            horizontalAcceleration = 0.0F;
            horizontalVelocity *= 0.7F;
            boolean strafingToward = targetOnLeft ? mc.thePlayer.moveStrafing > 0.0F : mc.thePlayer.moveStrafing < 0.0F;
            if (strafingToward) horizontalVelocity *= 0.5F;
        }
        if (targetOnLeft != prevOnLeft) {
            horizontalVelocity = -horizontalVelocity;
            horizontalAccumulator = 0.0F;
        }
        if (targetAbove != prevAbove) {
            verticalVelocity = -verticalVelocity;
        }
        if (verticalDeadZone < 5.0F) {
            verticalAcceleration = 0.0F;
            verticalVelocity *= 0.7F;
        }
        horizontalVelocity += horizontalAcceleration;
        if (aimVertically.on()) verticalVelocity += verticalAcceleration;
        if (Math.abs(horizontalVelocity) > 10.0F) {
            horizontalVelocity = 0.0F;
            return;
        }

        float horizontalAdjustment = horizontalVelocity * 0.15F;
        if (Math.abs(yawDiff) <= 9.0F) horizontalAdjustment /= 10.0F - Math.abs(yawDiff);
        if (Float.isNaN(horizontalAdjustment)) {
            horizontalVelocity = 0.0F;
            return;
        }

        float sens = mc.gameSettings.mouseSensitivity;
        float sensScale = sens * 0.6F + 0.2F;
        sensScale = sensScale * sensScale * sensScale * 8.0F;
        // accumulator conversion as in Vape's pre-render mouse delta application (setAngles: yaw += d * 0.15)
        float yawDelta = horizontalSpeed.intValue() * (horizontalAccumulator + horizontalAdjustment) * 5.0F * sensScale * 0.15F;
        horizontalAccumulator = 0.0F;
        float pitchDelta = 0.0F;
        if (aimVertically.on()) {
            pitchDelta = verticalSpeed.intValue() * verticalVelocity * 5.0F * sensScale * 0.15F;
        }
        yawDelta += driftX * sensScale * 0.15F;
        pitchDelta += driftY * sensScale * 0.15F;
        driftX = 0;
        driftY = 0;
        if (Float.isNaN(yawDelta) || Float.isNaN(pitchDelta)) {
            resetRotationState();
            return;
        }
        mc.thePlayer.rotationYaw += yawDelta;
        if (aimVertically.on() || pitchDelta != 0.0F) {
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + pitchDelta, -90.0F, 90.0F);
        }

        prevAbove = targetAbove;
        prevOnLeft = targetOnLeft;
        ++sampleCounter;
        if (sampleCounter > 10) {
            lastAngleDiff = yawDiff;
            sampleCounter = 0;
        }
    }

    /** Simplified Vape drift: occasional 1px-per-tick mouse nudges between randomized cooldowns. */
    private void updateDrift() {
        if (driftTimer >= driftInterval) {
            driftInterval = 250 + random.nextInt(50);
            driftTimer = -random.nextInt(100) - 50;
            driftStepX = random.nextInt(3) - 1;
            driftStepY = random.nextInt(3) - 1;
        }
        ++driftTimer;
        if (driftTimer < 0) return;
        if (random.nextInt(20) == 0) {
            driftX += driftStepX;
            driftY += driftStepY;
        }
        if ((horizontalAccumulator > 0.0F && driftX < 0) || (horizontalAccumulator < 0.0F && driftX > 0)) {
            driftX = 0;
        }
    }

    // ===== Adaptive mode (Vape AimAssistTargetingSubModule, faithful port) =====

    /** Vape runTargetLoop: target picking + engine update on the worker thread. */
    private void runTargetLoop() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!canAim(mc, 250)) {
            resetAdaptiveState();
            target = null;
            return;
        }
        if (target != null && target.isDead) {
            target = null;
        }
        boolean mouseDown = GameplayUtil.physicalDown(mc.gameSettings.keyBindAttack);
        if (requireMouseDown.on() && !mouseDown) {
            target = null;
            resetAdaptiveState();
            return;
        }
        if (target != null && (target.isDead || mc.thePlayer.getDistanceToEntity(target) > distance.value())) {
            resetAdaptiveState();
            target = null;
        }
        if (requireMouseDown.on() && mouseDown && target == null || !requireMouseDown.on()) {
            EntityLivingBase candidateTarget = findBestTargetSafe(mc);
            if (!requireMouseDown.on()) {
                ++targetSwitchTicks;
                if (targetSwitchTicks > 700 || target == null) {
                    if (target == null || target != candidateTarget) {
                        resetAdaptiveState();
                    }
                    target = candidateTarget;
                    targetSwitchTicks = 0;
                }
            } else {
                if (target == null || target != candidateTarget) {
                    resetAdaptiveState();
                }
                target = candidateTarget;
            }
        }
        if (mc.theWorld == null) return;
        if (target != null && mc.currentScreen == null) {
            updateAim(mc);
        } else {
            target = null;
            resetAdaptiveState();
        }
    }

    private static double[] closestPointOnBox(net.minecraft.util.AxisAlignedBB box, Vec3 point) {
        return new double[]{
                MathHelper.clamp_double(point.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(point.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ)};
    }

    /** Vape resolveAimTarget: eye-height aligned aim point, blending toward the 65% body center while airborne. */
    private double[] resolveAimTarget(Minecraft mc, EntityLivingBase targetEntity, double airFactorValue) {
        double playerEyeY = mc.thePlayer.getPositionEyes(1.0F).yCoord;
        double targetMinY = targetEntity.getEntityBoundingBox().minY;
        double targetMaxY = targetEntity.getEntityBoundingBox().maxY;
        double targetHeight = targetMaxY - targetMinY;
        double targetCenterY = targetMinY + targetHeight * 0.65D;
        double airborneOffset = Math.min(0.85D, airFactorValue);
        double eyeOffset = 0.1D + airborneOffset;
        double eyeAlignedY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, playerEyeY - eyeOffset));
        double centerBlend = Math.max(0.0D, Math.min(1.0D, airborneOffset / 0.55D));
        double resolvedTargetY = eyeAlignedY + (targetCenterY - eyeAlignedY) * centerBlend;
        resolvedTargetY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, resolvedTargetY));
        if (targetArea.is(TargetArea.CLOSEST)) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
            double[] closest = closestPointOnBox(targetEntity.getEntityBoundingBox(), eye);
            double closestY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, closest[1] - eyeOffset));
            closestY += (targetCenterY - closestY) * centerBlend;
            closestY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, closestY));
            return new double[]{closest[0], closestY, closest[2]};
        }
        return new double[]{targetEntity.posX, resolvedTargetY, targetEntity.posZ};
    }

    /** Vape resolveSnapPoint: the snap renderer aims here when a snap is active. */
    private double[] resolveSnapPoint(Minecraft mc, EntityLivingBase targetEntity) {
        double playerEyeY = mc.thePlayer.getPositionEyes(1.0F).yCoord;
        double targetMinY = targetEntity.getEntityBoundingBox().minY;
        double targetMaxY = targetEntity.getEntityBoundingBox().maxY;
        double targetHeight = targetMaxY - targetMinY;
        double targetCenterY = targetMinY + targetHeight * 0.65D;
        double airborneOffset = Math.min(0.85D, airFactor);
        double eyeOffset = 0.1D + airborneOffset;
        double eyeAlignedY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, playerEyeY - eyeOffset));
        double centerBlend = Math.max(0.0D, Math.min(1.0D, airborneOffset / 0.55D));
        double targetY = eyeAlignedY + (targetCenterY - eyeAlignedY) * centerBlend;
        targetY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, targetY));
        if (targetArea.is(TargetArea.CLOSEST)) {
            Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
            double[] closestPoint = closestPointOnBox(targetEntity.getEntityBoundingBox(), eye);
            double closestY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, closestPoint[1] - eyeOffset));
            closestY += (targetCenterY - closestY) * centerBlend;
            closestY = Math.max(targetMinY + 0.01D, Math.min(targetMaxY - 0.01D, closestY));
            return new double[]{closestPoint[0], closestY, closestPoint[2]};
        }
        return new double[]{targetEntity.posX, targetY, targetEntity.posZ};
    }

    /** Vape updateAim: the full adaptive tracking engine. */
    private void updateAim(Minecraft mc) {
        if (target == null || target.isDead) {
            resetAdaptiveState();
            return;
        }
        boolean yawSnapEnabled = horizontalSpeed.intValue() > 20;
        boolean pitchSnapEnabled = aimVertically.on() && verticalSpeed.intValue() > 20;
        boolean snapYaw = yawSnapEnabled && yawSnapped;
        boolean snapPitch = pitchSnapEnabled && pitchSnapped;

        long nowNanos = System.nanoTime();
        float deltaTime;
        if (!initialized || lastFrameNanos == 0L) {
            deltaTime = 0.016666668f;
        } else {
            deltaTime = (float) (nowNanos - lastFrameNanos) / 1.0E9f;
            deltaTime = Math.max(0.008333334f, Math.min(0.12f, deltaTime));
        }
        lastFrameNanos = nowNanos;

        double[] resolvedAimPoint = resolveAimTarget(mc, target, airFactor);
        double resolvedAimX = resolvedAimPoint[0];
        double resolvedAimY = resolvedAimPoint[1];
        double resolvedAimZ = resolvedAimPoint[2];
        double targetMotionX = target.motionX;
        double targetMotionY = target.motionY;
        double targetMotionZ = target.motionZ;
        double relativeMotionX = targetMotionX - mc.thePlayer.motionX;
        double relativeMotionY = targetMotionY - mc.thePlayer.motionY;
        double relativeMotionZ = targetMotionZ - mc.thePlayer.motionZ;
        double targetLeadX = targetMotionX + relativeMotionX * 0.08D;
        double targetLeadY = targetMotionY + relativeMotionY * 0.1D;
        double targetLeadZ = targetMotionZ + relativeMotionZ * 0.08D;
        if (!aimPointInitialized) {
            aimX = resolvedAimX;
            aimY = resolvedAimY;
            aimZ = resolvedAimZ;
            leadX = targetLeadX;
            leadY = targetLeadY;
            leadZ = targetLeadZ;
            aimPointInitialized = true;
        }
        double targetHorizontalSpeed = Math.sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ);
        double relativeHorizontalSpeed = Math.sqrt(relativeMotionX * relativeMotionX + relativeMotionZ * relativeMotionZ);
        double targetDistance = mc.thePlayer.getDistanceToEntity(target);
        double baseAimSmoothing = targetArea.is(TargetArea.CLOSEST) ? 0.3D : 0.28D;
        double aimSmoothing = Math.max(0.08D, Math.min(0.75D, baseAimSmoothing + Math.min(0.35D, relativeHorizontalSpeed * 0.5D)));
        double leadMotionScale = targetHorizontalSpeed + relativeHorizontalSpeed * 0.25D;
        double leadSmoothing = Math.max(0.12D, Math.min(0.68D, 0.18D + Math.min(0.42D, leadMotionScale * 0.72D)));
        double frameScale = Math.max(0.45D, Math.min(2.4D, (double) deltaTime * 60.0D));
        double aimBlend = 1.0D - Math.pow(1.0D - aimSmoothing, frameScale);
        double leadBlend = 1.0D - Math.pow(1.0D - leadSmoothing, frameScale);
        aimX += (resolvedAimX - aimX) * aimBlend;
        aimY += (resolvedAimY - aimY) * aimBlend;
        aimZ += (resolvedAimZ - aimZ) * aimBlend;
        leadX += (targetLeadX - leadX) * leadBlend;
        leadY += (targetLeadY - leadY) * leadBlend;
        leadZ += (targetLeadZ - leadZ) * leadBlend;

        double aimLagX = resolvedAimX - aimX;
        double aimLagZ = resolvedAimZ - aimZ;
        double horizontalAimLag = Math.sqrt(aimLagX * aimLagX + aimLagZ * aimLagZ);
        double maximumAimLag = 0.3D + targetHorizontalSpeed * 1.3D + relativeHorizontalSpeed * 0.4D;
        if (horizontalAimLag > maximumAimLag) {
            aimX = resolvedAimX;
            aimY = resolvedAimY;
            aimZ = resolvedAimZ;
            leadX = targetLeadX;
            leadY = targetLeadY;
            leadZ = targetLeadZ;
        }

        double leadAmount = 0.35D + targetDistance * 0.045D + targetHorizontalSpeed * 0.95D + Math.min(0.18D, relativeHorizontalSpeed * 0.12D);
        leadAmount = Math.max(0.1D, Math.min(1.05D, leadAmount));
        double distanceLeadScale = Math.max(0.0D, Math.min(1.0D, (targetDistance - 0.8D) / 2.5D));
        leadAmount *= 0.3D + 0.7D * distanceLeadScale;
        leadAmount = Math.max(0.05D, leadAmount);
        double playerX = mc.thePlayer.posX;
        double playerZ = mc.thePlayer.posZ;
        double playerEyeY = mc.thePlayer.getPositionEyes(1.0F).yCoord;
        float measuredVerticalVelocity = 0.0f;
        if (initialized && lastEyeY != 0.0D) {
            measuredVerticalVelocity = (float) ((playerEyeY - lastEyeY) / (double) deltaTime);
        }
        verticalVelocityAdaptive += (measuredVerticalVelocity - verticalVelocityAdaptive) * 0.65f;
        lastEyeY = playerEyeY;
        if (mc.thePlayer.onGround) {
            lastGroundEyeY = playerEyeY;
            airFactor *= 0.35f;
        } else {
            if (lastGroundEyeY == 0.0D) {
                lastGroundEyeY = playerEyeY;
            }
            double airborneHeight = Math.max(0.0D, playerEyeY - lastGroundEyeY);
            float velocityAirFactor = (float) Math.min(0.7D, Math.max(0.0D, (double) verticalVelocityAdaptive) * 0.34D);
            float heightAirFactor = (float) Math.min(0.82D, airborneHeight * 0.58D);
            airFactor = Math.max(airFactor * 0.92f, Math.max(velocityAirFactor, heightAirFactor));
        }

        double adjustedAimX = aimX;
        double adjustedAimZ = aimZ;
        double verticalAimDelta = aimY - playerEyeY;
        double verticalOffsetScale = Math.max(0.0D, Math.min(1.0D, Math.abs(verticalAimDelta) / 0.7D));
        double verticalLeadAmount = Math.min(leadAmount, 0.7D + targetDistance * 0.04D);
        double verticalLeadScale = 0.28D + 0.52D * verticalOffsetScale;
        double verticalLead = leadY * verticalLeadAmount * verticalLeadScale;
        double verticalLeadLimit = 0.16D + targetDistance * 0.055D;
        verticalLead = Math.max(-verticalLeadLimit, Math.min(verticalLeadLimit, verticalLead));
        double adjustedAimY = aimY + verticalLead;
        if (snapYaw) {
            adjustedAimX = resolvedAimX;
            adjustedAimZ = resolvedAimZ;
        }
        if (snapPitch) {
            adjustedAimY = resolvedAimY;
        }
        double aimDeltaX = adjustedAimX - playerX;
        double aimDeltaZ = adjustedAimZ - playerZ;
        double aimDeltaY = adjustedAimY - playerEyeY;
        double horizontalAimDistance = Math.sqrt(aimDeltaX * aimDeltaX + aimDeltaZ * aimDeltaZ);
        float targetYawValue = (float) (Math.toDegrees(Math.atan2(aimDeltaZ, aimDeltaX)) - 90.0D);
        float targetPitchValue = (float) (-Math.toDegrees(Math.atan2(aimDeltaY, Math.max(horizontalAimDistance, 1.0E-4D))));
        if (initialized) {
            float yawTargetChange = Math.abs(MathHelper.wrapAngleTo180_float(targetYawValue - lastTargetYaw));
            float pitchTargetChange = Math.abs(MathHelper.wrapAngleTo180_float(targetPitchValue - lastTargetPitch));
            float abruptChangeThreshold = targetArea.is(TargetArea.CLOSEST) ? 20.0f : 12.0f;
            float maximumTargetChange = Math.max(yawTargetChange, pitchTargetChange);
            float abruptChangeFactor = Math.max(0.0f, Math.min(1.0f, (maximumTargetChange - abruptChangeThreshold) / 50.0f));
            overshoot = Math.max(overshoot, abruptChangeFactor);
            if (abruptChangeFactor > 0.3f) {
                aimStrength *= 0.3f;
                aimX = resolvedAimX;
                aimY = resolvedAimY;
                aimZ = resolvedAimZ;
            }
            float yawRateScale = 1.0f + Math.min(2.0f, yawTargetChange / 60.0f);
            float pitchRateScale = 1.0f + Math.min(2.0f, pitchTargetChange / 60.0f);
            float maximumYawRate = (120.0f + (float) (relativeHorizontalSpeed * 700.0D)) * yawRateScale;
            float maximumPitchRate = (95.0f + (float) (Math.abs(leadY) * 550.0D)) * pitchRateScale;
            float viewYawDifference = Math.abs(MathHelper.wrapAngleTo180_float(targetYawValue - mc.thePlayer.rotationYaw));
            float wideTurnScale = smoothStep(10.0f, 35.0f, viewYawDifference);
            maximumYawRate *= 1.0f + wideTurnScale * 1.5f;
            maximumYawRate = Math.max(90.0f, Math.min(1080.0f, maximumYawRate));
            maximumPitchRate = Math.max(70.0f, Math.min(500.0f, maximumPitchRate));
            float yawStep = MathHelper.wrapAngleTo180_float(targetYawValue - lastTargetYaw);
            float pitchStep = MathHelper.wrapAngleTo180_float(targetPitchValue - lastTargetPitch);
            if (!snapYaw) {
                yawStep = Math.max(-maximumYawRate * deltaTime, Math.min(maximumYawRate * deltaTime, yawStep));
            }
            if (!snapPitch) {
                pitchStep = Math.max(-maximumPitchRate * deltaTime, Math.min(maximumPitchRate * deltaTime, pitchStep));
            }
            targetYawValue = lastTargetYaw + yawStep;
            targetPitchValue = lastTargetPitch + pitchStep;
        }
        float overshootDecay = (float) Math.pow(0.02D, deltaTime);
        overshoot *= overshootDecay;
        if (overshoot < 0.01f) {
            overshoot = 0.0f;
        }
        float overshootMultiplier = 1.0f + 3.0f * overshoot;
        float playerYaw = mc.thePlayer.rotationYaw;
        float playerPitch = mc.thePlayer.rotationPitch;
        float yawError = MathHelper.wrapAngleTo180_float(targetYawValue - playerYaw);
        float pitchError = MathHelper.wrapAngleTo180_float(targetPitchValue - playerPitch);
        if (!aimVertically.on()) {
            pitchError = 0.0f;
        }
        float absoluteYawError = Math.abs(yawError);
        float absolutePitchError = Math.abs(pitchError);
        float combinedAngleError = (float) Math.sqrt(absoluteYawError * absoluteYawError + absolutePitchError * absolutePitchError);
        yawSnapped = shouldSnap(yawSnapEnabled, yawSnapped, absoluteYawError);
        pitchSnapped = shouldSnap(pitchSnapEnabled, pitchSnapped, absolutePitchError);
        snapYaw = yawSnapEnabled && yawSnapped;
        snapPitch = pitchSnapEnabled && pitchSnapped;
        float desiredAimStrength = 1.0f - smoothStep(1.5f, 8.0f, combinedAngleError);
        if (initialized) {
            float yawClosingSpeed = -(absoluteYawError - Math.abs(lastYawDiff)) / deltaTime;
            float closingAdjustment = Math.max(-0.3f, Math.min(0.3f, yawClosingSpeed / 20.0f));
            desiredAimStrength += closingAdjustment;
            desiredAimStrength = Math.max(0.0f, Math.min(1.0f, desiredAimStrength));
        }
        float aimStrengthBlend = desiredAimStrength > aimStrength
                ? Math.max(0.01f, Math.min(0.25f, deltaTime * 3.0f))
                : Math.max(0.05f, Math.min(0.8f, deltaTime * 20.0f));
        aimStrength += (desiredAimStrength - aimStrength) * aimStrengthBlend;
        float currentAimStrength = aimStrength;

        float smoothedTargetYawRate = 0.0f;
        float smoothedTargetPitchRate = 0.0f;
        float playerYawRate = 0.0f;
        float playerPitchRate = 0.0f;
        if (initialized) {
            float targetYawRate = MathHelper.wrapAngleTo180_float(targetYawValue - lastTargetYaw) / deltaTime;
            float targetPitchRate = MathHelper.wrapAngleTo180_float(targetPitchValue - lastTargetPitch) / deltaTime;
            playerYawRate = MathHelper.wrapAngleTo180_float(playerYaw - lastPlayerYaw) / deltaTime;
            playerPitchRate = MathHelper.wrapAngleTo180_float(playerPitch - lastPlayerPitch) / deltaTime;
            float accelerationBlend = Math.max(0.05f, Math.min(0.45f, deltaTime * 12.0f));
            yawAccel += (targetYawRate - yawAccel) * accelerationBlend;
            pitchAccel += (targetPitchRate - pitchAccel) * accelerationBlend;
            smoothedTargetYawRate = yawAccel;
            smoothedTargetPitchRate = pitchAccel;
        }
        float horizontalSpeedValue = horizontalSpeed.intValue() * 0.75f;
        float verticalSpeedValue = verticalSpeed.intValue() * 0.75f;
        float horizontalSpeedFactor = Math.max(0.0f, Math.min(1.0f, (horizontalSpeedValue - 10.0f) / 90.0f));
        float verticalSpeedFactor = Math.max(0.0f, Math.min(1.0f, (verticalSpeedValue - 10.0f) / 90.0f));
        if (Math.signum(yawError) != Math.signum(lastYawDiff) && Math.abs(yawError) > 0.1f && Math.abs(lastYawDiff) > 0.1f) {
            yawBias *= 0.3f;
        }
        if (Math.signum(pitchError) != Math.signum(lastPitchDiff) && Math.abs(pitchError) > 0.1f && Math.abs(lastPitchDiff) > 0.1f) {
            pitchBias *= 0.3f;
        }
        float squaredAimStrength = currentAimStrength * currentAimStrength;
        yawBias += yawError * deltaTime * squaredAimStrength * (1.0f - horizontalSpeedFactor);
        pitchBias += pitchError * deltaTime * squaredAimStrength * (1.0f - verticalSpeedFactor);
        float biasRetention = 1.0f - (1.0f - currentAimStrength) * Math.max(0.0f, Math.min(0.5f, deltaTime * 5.0f));
        yawBias *= biasRetention;
        pitchBias *= biasRetention;
        float yawBiasLimit = 15.0f * (1.0f - horizontalSpeedFactor * 0.9f);
        float pitchBiasLimit = 10.0f * (1.0f - verticalSpeedFactor * 0.9f);
        yawBias = Math.max(-yawBiasLimit, Math.min(yawBiasLimit, yawBias));
        pitchBias = Math.max(-pitchBiasLimit, Math.min(pitchBiasLimit, pitchBias));

        float yawErrorVelocity = initialized ? (yawError - lastYawDiff) / deltaTime : 0.0f;
        float pitchErrorVelocity = initialized ? (pitchError - lastPitchDiff) / deltaTime : 0.0f;
        float velocityBlend = 0.15f;
        yawVelocity = yawVelocity * (1.0f - velocityBlend) + yawErrorVelocity * velocityBlend;
        pitchVelocity = pitchVelocity * (1.0f - velocityBlend) + pitchErrorVelocity * velocityBlend;

        float flickThreshold = targetArea.is(TargetArea.CLOSEST) ? 1.5f : 0.5f;
        float yawSign = Math.signum(yawError);
        yawFlickTicks = yawSign != lastYawSign && Math.abs(yawError) > flickThreshold
                ? Math.min(yawFlickTicks + 1.0f, 8.0f)
                : Math.max(0.0f, yawFlickTicks - deltaTime * 3.0f);
        lastYawSign = yawSign;
        float pitchSign = Math.signum(pitchError);
        pitchFlickTicks = pitchSign != lastPitchSign && Math.abs(pitchError) > flickThreshold
                ? Math.min(pitchFlickTicks + 1.0f, 8.0f)
                : Math.max(0.0f, pitchFlickTicks - deltaTime * 3.0f);
        lastPitchSign = pitchSign;
        float yawFlickFactor = Math.max(0.0f, Math.min(1.0f, yawFlickTicks / 5.0f));
        float pitchFlickFactor = Math.max(0.0f, Math.min(1.0f, pitchFlickTicks / 5.0f));

        float limitedHorizontalSpeed = Math.min(horizontalSpeedValue, 10.0f);
        float limitedVerticalSpeed = Math.min(verticalSpeedValue, 10.0f);
        float horizontalGainInput = (limitedHorizontalSpeed - 1.0f) / 9.0f;
        float verticalGainInput = (limitedVerticalSpeed - 1.0f) / 9.0f;
        horizontalGainInput *= horizontalGainInput;
        verticalGainInput *= verticalGainInput;
        float horizontalGainScale = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, horizontalGainInput));
        float verticalGainScale = 0.15f + 0.85f * Math.max(0.0f, Math.min(1.0f, verticalGainInput));
        float yawErrorGain = lerp(currentAimStrength, 8.0f, 2.5f + limitedHorizontalSpeed * 0.15f) * horizontalGainScale;
        float pitchErrorGain = lerp(currentAimStrength, 7.0f, 2.2f + limitedVerticalSpeed * 0.13f) * verticalGainScale;
        float yawBiasGain = lerp(currentAimStrength, 0.15f, 0.8f + limitedHorizontalSpeed * 0.04f) * horizontalGainScale;
        float pitchBiasGain = lerp(currentAimStrength, 0.12f, 0.65f + limitedVerticalSpeed * 0.035f) * verticalGainScale;
        float yawVelocityGain = lerp(currentAimStrength, 0.08f, 0.25f) * horizontalGainScale;
        float pitchVelocityGain = lerp(currentAimStrength, 0.06f, 0.2f) * verticalGainScale;
        float targetYawRateGain = (0.85f + limitedHorizontalSpeed * 0.015f) * horizontalGainScale;
        float targetPitchRateGain = (0.82f + limitedVerticalSpeed * 0.013f) * verticalGainScale;
        float playerYawCompensation = lerp(currentAimStrength, 0.1f, 0.3f);
        float playerPitchCompensation = lerp(currentAimStrength, 0.08f, 0.25f);
        yawErrorGain *= 1.0f - 0.6f * yawFlickFactor;
        yawVelocityGain *= 1.0f + 2.0f * yawFlickFactor;
        pitchErrorGain *= 1.0f - 0.6f * pitchFlickFactor;
        pitchVelocityGain *= 1.0f + 2.0f * pitchFlickFactor;

        float yawRate = yawErrorGain * yawError + yawBiasGain * yawBias + yawVelocityGain * yawVelocity
                + targetYawRateGain * smoothedTargetYawRate - playerYawCompensation * playerYawRate;
        float pitchRate = 0.0f;
        if (aimVertically.on()) {
            pitchRate = pitchErrorGain * pitchError + pitchBiasGain * pitchBias + pitchVelocityGain * pitchVelocity
                    + targetPitchRateGain * smoothedTargetPitchRate - playerPitchCompensation * playerPitchRate;
        } else {
            pitchBias = 0.0f;
            pitchVelocity = 0.0f;
        }
        if (!snapPitch) {
            pitchRate += computePitchDrift(playerPitch, deltaTime, nowNanos);
        }
        float strafeMultiplier = 1.0f;
        float strafeInput = mc.thePlayer.moveStrafing;
        if (strafeIncrease.on() && Math.abs(strafeInput) > 0.01f) {
            boolean targetToRight = yawError > 0.0f;
            boolean strafingAway = targetToRight && strafeInput < 0.0f || !targetToRight && strafeInput > 0.0f;
            if (strafingAway) {
                strafeMultiplier = 1.15f;
            }
        }
        yawRate *= strafeMultiplier;

        float wideYawScale = smoothStep(8.0f, 40.0f, absoluteYawError);
        float wideTurnMultiplier = 1.0f + wideYawScale * 2.5f;
        float yawBaseLimit = (22.0f + limitedHorizontalSpeed * 15.0f) * horizontalGainScale * overshootMultiplier * wideTurnMultiplier;
        float pitchBaseLimit = (18.0f + limitedVerticalSpeed * 13.0f) * verticalGainScale * overshootMultiplier;
        float yawMotionLimit = (Math.abs(smoothedTargetYawRate) * 0.4f + 18.0f) * (0.35f + horizontalGainScale * 0.65f) * wideTurnMultiplier;
        float pitchMotionLimit = (Math.abs(smoothedTargetPitchRate) * 0.38f + 14.0f) * (0.35f + verticalGainScale * 0.65f);
        float maximumYawOutput = Math.min(400.0f * overshootMultiplier * wideTurnMultiplier, Math.max(yawBaseLimit, yawMotionLimit));
        float maximumPitchOutput = Math.min(300.0f * overshootMultiplier, Math.max(pitchBaseLimit, pitchMotionLimit));
        yawRate = Math.max(-maximumYawOutput, Math.min(maximumYawOutput, yawRate));
        pitchRate = Math.max(-maximumPitchOutput, Math.min(maximumPitchOutput, pitchRate));

        float distanceScale = smoothStep(0.5f, 3.0f, (float) targetDistance);
        float wideTurnBaseScale = lerp(wideYawScale, 0.15f, 0.65f);
        float yawDistanceMultiplier = lerp(currentAimStrength,
                wideTurnBaseScale + (1.0f - wideTurnBaseScale) * distanceScale, 0.4f + 0.6f * distanceScale);
        float pitchDistanceMultiplier = lerp(currentAimStrength,
                0.2f + 0.8f * distanceScale, 0.45f + 0.55f * distanceScale);
        yawRate *= yawDistanceMultiplier;
        pitchRate *= pitchDistanceMultiplier;
        float noiseElapsedSeconds = (float) (nowNanos - noiseStartNanos) / 1.0E9f;
        float highFrequencyYawNoise = (float) (Math.sin((double) noiseElapsedSeconds * 62.83D) * 0.4D
                + Math.sin((double) noiseElapsedSeconds * 47.12D) * 0.25D
                + Math.sin((double) noiseElapsedSeconds * 78.54D) * 0.15D);
        float highFrequencyPitchNoise = (float) (Math.sin((double) noiseElapsedSeconds * 56.55D + 1.3D) * 0.35D
                + Math.sin((double) noiseElapsedSeconds * 43.98D + 0.7D) * 0.2D
                + Math.sin((double) noiseElapsedSeconds * 72.26D + 2.1D) * 0.12D);
        float mediumFrequencyYawNoise = (float) (Math.sin((double) noiseElapsedSeconds * 12.57D) * 0.8D
                + Math.sin((double) noiseElapsedSeconds * 7.85D) * 0.5D);
        float mediumFrequencyPitchNoise = (float) (Math.sin((double) noiseElapsedSeconds * 10.47D + 0.9D) * 0.6D
                + Math.sin((double) noiseElapsedSeconds * 5.65D + 1.8D) * 0.4D);
        float lowFrequencyYawNoise = (float) (Math.sin((double) noiseElapsedSeconds * 1.26D) * 0.3D);
        float lowFrequencyPitchNoise = (float) (Math.sin((double) noiseElapsedSeconds * 0.94D + 0.5D) * 0.2D);
        float noiseStrength = 0.15f + 0.85f * currentAimStrength;
        float speedNoiseScale = 0.5f + 0.5f * (1.0f - horizontalGainInput);
        float yawNoise = (highFrequencyYawNoise + mediumFrequencyYawNoise + lowFrequencyYawNoise)
                * noiseStrength * speedNoiseScale * 1.5f;
        float pitchNoise = (highFrequencyPitchNoise + mediumFrequencyPitchNoise + lowFrequencyPitchNoise)
                * noiseStrength * speedNoiseScale;
        if (!snapYaw) {
            yawRate += yawNoise;
        }
        if (aimVertically.on() && !snapPitch) {
            pitchRate += pitchNoise;
        }

        float yawFrameDelta = yawRate * deltaTime;
        float pitchFrameDelta = pitchRate * deltaTime;
        float sensitivity = mc.gameSettings.mouseSensitivity;
        float sensitivityBase = sensitivity * 0.6f + 0.2f;
        float sensitivityScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float mouseAngleUnit = sensitivityScale * 0.15f;
        if (mouseAngleUnit > 1.0E-5f) {
            float yawVelocityUnits = yawFrameDelta / mouseAngleUnit;
            float pitchVelocityUnits = pitchFrameDelta / mouseAngleUnit;
            float pitchBlendFactor = aimVertically.on() ? verticalSpeedFactor : 0.0f;
            if (snapYaw) {
                pendingYaw = 0.0f;
            }
            if (snapPitch) {
                pendingPitch = 0.0f;
            }
            if (!snapYaw) {
                if (horizontalSpeedFactor > 0.0f) {
                    float targetYawUnits = yawError / mouseAngleUnit;
                    float yawCorrection = targetYawUnits - pendingYaw;
                    float maximumYawCorrection = horizontalSpeedValue * 2.0f;
                    yawCorrection = Math.max(-maximumYawCorrection, Math.min(maximumYawCorrection, yawCorrection));
                    pendingYaw += yawVelocityUnits * (1.0f - horizontalSpeedFactor) + yawCorrection * horizontalSpeedFactor;
                } else {
                    pendingYaw += yawVelocityUnits;
                }
            }
            if (!snapPitch) {
                if (pitchBlendFactor > 0.0f) {
                    float targetPitchUnits = aimVertically.on() ? pitchError / mouseAngleUnit : 0.0f;
                    float pitchCorrection = targetPitchUnits - pendingPitch;
                    float maximumPitchCorrection = verticalSpeedValue * 2.0f;
                    pitchCorrection = Math.max(-maximumPitchCorrection, Math.min(maximumPitchCorrection, pitchCorrection));
                    pendingPitch += pitchVelocityUnits * (1.0f - pitchBlendFactor) + pitchCorrection * pitchBlendFactor;
                } else {
                    pendingPitch += pitchVelocityUnits;
                }
            }
        }
        if (!initialized) {
            initialized = true;
        }
        lastYawDiff = yawError;
        lastPitchDiff = pitchError;
        lastTargetYaw = targetYawValue;
        lastTargetPitch = targetPitchValue;
        lastPlayerYaw = playerYaw;
        lastPlayerPitch = playerPitch;
    }

    /** Vape onPreRenderTick: apply the accumulated synthetic mouse pixels, snapping straight at the snap point. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled() || !mode.is(AimMode.ADAPTIVE)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || target == null || mc.thePlayer == null) return;
        boolean yawSnapEnabled = horizontalSpeed.intValue() > 20;
        boolean pitchSnapEnabled = aimVertically.on() && verticalSpeed.intValue() > 20;
        boolean snapYaw = yawSnapEnabled && yawSnapped;
        boolean snapPitch = pitchSnapEnabled && pitchSnapped;
        int yawSteps = snapYaw ? Math.round(pendingYaw) : (int) pendingYaw;
        int pitchSteps = snapPitch ? Math.round(pendingPitch) : (int) pendingPitch;
        float remainingYaw = snapYaw ? 0.0f : pendingYaw - yawSteps;
        float remainingPitch = snapPitch ? 0.0f : pendingPitch - pitchSteps;
        if (Math.abs(yawSteps) == 0) yawSteps = 0;
        if (Math.abs(pitchSteps) == 0) pitchSteps = 0;
        float sensitivity = mc.gameSettings.mouseSensitivity;
        float sensitivityBase = sensitivity * 0.6f + 0.2f;
        float sensitivityScale = sensitivityBase * sensitivityBase * sensitivityBase * 8.0f;
        float mouseDeltaX = (float) yawSteps * sensitivityScale;
        float mouseDeltaY = -(float) pitchSteps * sensitivityScale;
        if ((snapYaw || snapPitch)) {
            double[] snapPoint = resolveSnapPoint(mc, target);
            double deltaX = snapPoint[0] - mc.thePlayer.posX;
            double deltaZ = snapPoint[2] - mc.thePlayer.posZ;
            double deltaY = snapPoint[1] - mc.thePlayer.getPositionEyes(1.0F).yCoord;
            double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float targetYawValue = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
            float targetPitchValue = (float) (-Math.toDegrees(Math.atan2(deltaY, Math.max(horizontalDistance, 1.0E-4D))));
            if (snapYaw) {
                mouseDeltaX = MathHelper.wrapAngleTo180_float(targetYawValue - mc.thePlayer.rotationYaw) / 0.15f;
            }
            if (snapPitch) {
                mouseDeltaY = -MathHelper.wrapAngleTo180_float(targetPitchValue - mc.thePlayer.rotationPitch) / 0.15f;
            }
        }
        if (mouseDeltaX != 0.0f || mouseDeltaY != 0.0f) {
            // PlayerMouseRotationApplier.applyTrackedMouseDelta via setAngles (both scale by 0.15)
            mc.thePlayer.setAngles(mouseDeltaX, mouseDeltaY);
        }
        pendingYaw = remainingYaw;
        pendingPitch = remainingPitch;
    }

    private float computePitchDrift(float playerPitch, float deltaTime, long nowNanos) {
        float strength = 0.65f + 0.35f * aimStrength;
        if (driftNextNanos == 0L || nowNanos >= driftNextNanos) {
            float randomAmplitude = lerp(strength, 0.05f, 0.15f);
            float pitchCorrection = playerPitch > 22.0f ? -0.18f : (playerPitch < -22.0f ? 0.18f : 0.0f);
            driftTarget = (random.nextFloat() * 2.0f - 1.0f) * randomAmplitude + pitchCorrection;
            driftTarget = Math.max(-0.5f, Math.min(0.5f, driftTarget));
            long intervalMillis = 300L + random.nextInt(420);
            driftNextNanos = nowNanos + intervalMillis * 1000000L;
        }
        float springStrength = lerp(strength, 2.0f, 5.0f);
        float damping = (float) Math.pow(0.04D, deltaTime);
        driftVelocity += (driftTarget - driftPos) * springStrength * deltaTime;
        driftVelocity *= damping;
        driftPos += driftVelocity * deltaTime;
        float positionLimit = lerp(strength, 0.45f, 0.8f);
        if (driftPos > positionLimit) {
            driftPos = positionLimit;
            driftVelocity = Math.min(0.0f, driftVelocity);
        } else if (driftPos < -positionLimit) {
            driftPos = -positionLimit;
            driftVelocity = Math.max(0.0f, driftVelocity);
        }
        driftNoise += (random.nextFloat() * 2.0f - 1.0f - driftNoise) * Math.max(0.02f, Math.min(0.18f, deltaTime * 5.0f));
        float elapsedSeconds = (float) (nowNanos - noiseStartNanos) / 1.0E9f;
        float noise = (float) (Math.sin((double) elapsedSeconds * 8.7D + 0.4D) * 0.35D
                + Math.sin((double) elapsedSeconds * 13.1D + 2.2D) * 0.22D
                + Math.sin((double) elapsedSeconds * 19.6D + 1.1D) * 0.12D
                + (double) (driftNoise * 0.3f));
        float restoringForce = -driftPos * lerp(strength, 1.4f, 3.0f);
        float output = driftVelocity * 0.25f + restoringForce + noise * lerp(strength, 0.35f, 0.9f);
        float pitchLimitScale = 1.0f - 0.85f * smoothStep(72.0f, 88.0f, Math.abs(playerPitch));
        float outputLimit = lerp(strength, 0.25f, 0.55f) * pitchLimitScale;
        return Math.max(-outputLimit, Math.min(outputLimit, output));
    }

    private static boolean shouldSnap(boolean enabled, boolean currentlySnapped, float angleDifference) {
        if (!enabled) return false;
        return currentlySnapped ? angleDifference <= SNAP_EXIT_THRESHOLD : angleDifference <= SNAP_ENTER_THRESHOLD;
    }

    private static float smoothStep(float edgeStart, float edgeEnd, float value) {
        float normalized = Math.max(0.0f, Math.min(1.0f, (value - edgeStart) / (edgeEnd - edgeStart)));
        return normalized * normalized * (3.0f - 2.0f * normalized);
    }

    private static float lerp(float factor, float start, float end) {
        return start + factor * (end - start);
    }

    private void resetAdaptiveState() {
        pendingYaw = 0.0f;
        pendingPitch = 0.0f;
        yawSnapped = false;
        pitchSnapped = false;
        yawBias = 0.0f;
        pitchBias = 0.0f;
        yawVelocity = 0.0f;
        pitchVelocity = 0.0f;
        lastYawDiff = 0.0f;
        lastPitchDiff = 0.0f;
        lastTargetYaw = 0.0f;
        lastTargetPitch = 0.0f;
        yawAccel = 0.0f;
        pitchAccel = 0.0f;
        lastPlayerYaw = 0.0f;
        lastPlayerPitch = 0.0f;
        initialized = false;
        lastFrameNanos = 0L;
        aimPointInitialized = false;
        aimX = 0.0D;
        aimY = 0.0D;
        aimZ = 0.0D;
        leadX = 0.0D;
        leadY = 0.0D;
        leadZ = 0.0D;
        aimStrength = 0.0f;
        yawFlickTicks = 0.0f;
        pitchFlickTicks = 0.0f;
        lastYawSign = 0.0f;
        lastPitchSign = 0.0f;
        overshoot = 0.0f;
        lastEyeY = 0.0D;
        lastGroundEyeY = 0.0D;
        verticalVelocityAdaptive = 0.0f;
        airFactor = 0.0f;
        predictionInitialized = false;
        predictedX = 0.0D;
        predictedY = 0.0D;
        predictedZ = 0.0D;
        noiseStartNanos = System.nanoTime();
        driftPos = 0.0f;
        driftVelocity = 0.0f;
        driftTarget = 0.0f;
        driftNoise = 0.0f;
        driftNextNanos = 0L;
    }
}
