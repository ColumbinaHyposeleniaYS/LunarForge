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
 * Ported from Vape v4 (gg.vape.module.combat.AimAssist + its "Simple"
 * rotation submodule). Smoothly drags the crosshair towards a valid target
 * while you fight, using a velocity-buffer model that mimics human mouse
 * motion: acceleration, dead zones near the target, side flips when the
 * target crosses the view axis, proximity boost, random drift, and a
 * conversion through the in-game mouse sensitivity curve so the strength
 * follows your settings like a real mouse would.
 *
 * Not ported from Vape: the "Adaptive" targeting submodule (advanced tracking
 * with its own worker threads), the item whitelist UI (here "Limit To Items"
 * simply means swords) and the pickaxe/shovel block-break whitelist (the
 * check applies to every block).
 */
public final class ModuleAimAssist extends Module {

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

    public ModuleAimAssist() {
        super("AIM_ASSIST", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(requireMouseDown, aimVertically);
            s.add(horizontalSpeed, verticalSpeed);
            s.add(maxAngle, distance);
            s.add(targetMode, targetArea);
            s.add(strafeIncrease, checkBlockBreak, limitToItems);
        });
    }

    @Override protected void onDisable() {
        target = null;
        resetRotationState();
    }

    private void resetRotationState() {
        horizontalVelocity = 0.0F;
        verticalVelocity = 0.0F;
        horizontalAccumulator = 0.0F;
        driftX = 0;
        driftY = 0;
    }

    private boolean canAim(Minecraft mc) {
        if (mc.currentScreen != null) return false;
        if (limitToItems.on() && !GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return false;
        if (checkBlockBreak.on()) {
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                blockBreakCooldown = 10;
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (!canAim(mc)) {
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
}
