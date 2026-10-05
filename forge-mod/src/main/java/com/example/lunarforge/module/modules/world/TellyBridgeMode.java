package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.util.GameplayUtil;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

/**
 * Ported from Vape v4 (blatant.scaffold.TellyBridgeScaffoldMode, the
 * "TellyBridge" Scaffold mode). After "Activation Blocks" manual placements
 * the module jumps backwards along the bridge while sprinting (telly
 * bridging): airtime is used to keep placing, every landing either continues
 * flat or, with the randomised "Y increase" threshold, stairs up a block.
 * Holding the backward key (and the right button when "Require right click"
 * is on) keeps the bridge alive; releasing either hands control back.
 *
 * Adaptations: control claims become the module's movement-claim flag, the
 * block-count HUD stays unported, and the edge sneak of Vape's
 * ScaffoldEdgeSneakHelper runs through the module's shared activation sneak.
 */
final class TellyBridgeMode {
    private final ModuleScaffold scaffold;
    private final BooleanActivation requireRightClick;
    private final NumberActivation activationBlocks;
    private final NumberActivation yIncrease;

    interface NumberActivation {
        double value();
    }

    interface BooleanActivation {
        boolean value();
    }

    private final ArrayList<double[]> bridgePath = new ArrayList<double[]>();
    private boolean bridgingActive;
    private double[] verticalTransitionTarget;
    private int bridgeLevel;
    private boolean resetPending;
    private boolean wasAirborne;
    private boolean manualActivationComplete;
    private int consecutiveHeightIncreases;
    private int manualBlocksPlaced;
    private int direction;
    private double[] initialPlacement;
    private double[] lastAimTarget;
    private ItemStack activationItemStack;
    private final ArrayList<Integer> recentPlacements = new ArrayList<Integer>();
    private WalkTask movementTask;

    TellyBridgeMode(ModuleScaffold scaffold, BooleanActivation requireRightClick,
                    NumberActivation activationBlocks, NumberActivation yIncrease) {
        this.scaffold = scaffold;
        this.requireRightClick = requireRightClick;
        this.activationBlocks = activationBlocks;
        this.yIncrease = yIncrease;
    }

    void onEnable() {
        resetPending = true;
    }

    void onDisable() {
        scaffold.releaseControls();
        resetBridgeState();
    }

    boolean isResetPending() {
        return resetPending;
    }

    // ===== tick =====

    void onTick(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        scaffold.setMovementClaim(bridgingActive);
        if (!resetPending && bridgingActive && isLookingAtPlacement(mc)) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        if (resetPending) {
            if (bridgingActive) {
                scaffold.resetEdgeSneakForActivation();
                scaffold.releaseControls();
            }
            resetBridgeState();
            resetPending = evaluateManualActivation(mc);
            return;
        }
        if (shouldReset(mc)) {
            scaffold.resetEdgeSneakForActivation();
            return;
        }
        if (!bridgingActive && movementTask != null && movementTask.completed) {
            bridgingActive = true;
        }
        updateBridge(mc);
    }

    private boolean shouldReset(Minecraft mc) {
        if (!ensureBlockSlot(mc)) {
            resetPending = true;
            ScaffoldInput.releaseAllInput();
            return true;
        }
        boolean backwardPressed = GameplayUtil.physicalDown(mc.gameSettings.keyBindBack);
        boolean activationPressed = !requireRightClick.value() || GameplayUtil.physicalDown(mc.gameSettings.keyBindUseItem);
        if (!backwardPressed || !activationPressed) {
            if (bridgingActive) {
                if (!mc.thePlayer.capabilities.isFlying && !isGroundPositionClear(mc)) {
                    return false;
                }
                ScaffoldInput.releaseAllInput();
            }
            resetPending = true;
            return true;
        }
        updatePlacementHistory(mc);
        if (bridgingActive && sumPlacementHistory() >= 10) {
            ScaffoldInput.releaseAllInput();
            resetPending = true;
            return true;
        }
        return false;
    }

    private boolean evaluateManualActivation(Minecraft mc) {
        double blockX = MathHelper.floor_double(mc.thePlayer.posX);
        double blockZ = MathHelper.floor_double(mc.thePlayer.posZ);
        double placementY = scaffold.getPlacementY(mc);
        if (!scaffold.isUsableBlockStack(mc.thePlayer.getHeldItem())) {
            initialPlacement = null;
            manualBlocksPlaced = 0;
            return true;
        }
        int currentDirection = scaffold.getCardinalDirection(mc);
        if (direction != 0 && currentDirection != direction) {
            initialPlacement = null;
            manualBlocksPlaced = 0;
        }
        direction = currentDirection;
        double[] playerBlock = {blockX, placementY, blockZ};
        double[] oneBlockAhead = scaffold.offsetPosition(playerBlock, 1, direction);
        double[] twoBlocksAhead = scaffold.offsetPosition(playerBlock, 2, direction);
        if (initialPlacement == null && mc.thePlayer.onGround) {
            if (scaffold.isAirBlockAt(playerBlock[0], playerBlock[1], playerBlock[2])) {
                initialPlacement = playerBlock;
            } else if (scaffold.isAirBlockAt(oneBlockAhead[0], oneBlockAhead[1], oneBlockAhead[2])) {
                initialPlacement = oneBlockAhead;
            } else if (scaffold.isAirBlockAt(twoBlocksAhead[0], twoBlocksAhead[1], twoBlocksAhead[2])) {
                initialPlacement = twoBlocksAhead;
            }
        } else if (initialPlacement != null) {
            if (manualBlocksPlaced >= (int) activationBlocks.value()) {
                bridgePath.add(initialPlacement);
                manualBlocksPlaced = 0;
                double[] movementAnchor = scaffold.computeElevatedPlacementPoint(
                        scaffold.offsetPosition(initialPlacement, -1, direction), 0.0D, direction);
                submitMovementTask(computeTargetPosition(new double[]{movementAnchor[0], movementAnchor[2]}), false);
                activationItemStack = mc.thePlayer.getHeldItem();
                initialPlacement = null;
                manualActivationComplete = true;
                return false;
            }
            if (!scaffold.isAirBlockAt(initialPlacement[0], initialPlacement[1], initialPlacement[2])) {
                ++manualBlocksPlaced;
                double[] nextPlacement = scaffold.offsetPosition(initialPlacement, 1, direction);
                boolean nextPlacementIsEmpty = scaffold.isAirBlockAt(nextPlacement[0], nextPlacement[1], nextPlacement[2]);
                if (nextPlacementIsEmpty && manualBlocksPlaced < (int) activationBlocks.value()) {
                    initialPlacement = nextPlacement;
                } else if (!nextPlacementIsEmpty) {
                    initialPlacement = null;
                    manualBlocksPlaced = 0;
                }
            } else if (scaffold.hasPlacementDrifted(initialPlacement, playerBlock, direction,
                    activationBlocks.value(), manualBlocksPlaced)) {
                initialPlacement = null;
                manualBlocksPlaced = 0;
            }
        }
        return true;
    }

    private void updateBridge(Minecraft mc) {
        if (!bridgingActive) return;
        if (!mc.thePlayer.onGround) wasAirborne = true;
        pruneAndExtendPath(mc);
        if (mc.thePlayer.onGround && (manualActivationComplete || wasAirborne)) {
            bridgeLevel = manualActivationComplete || (!hasAxisMotion(mc) && hasPassedPathEdge(mc)) ? 0 : 1;
            manualActivationComplete = false;
            lastAimTarget = null;
            verticalTransitionTarget = null;
            if (consecutiveHeightIncreases >= randomHeightIncreaseThreshold() && bridgeLevel == 1) {
                if (yIncrease.value() == 0.0D && !isAxisMotionBelowThreshold(mc)) {
                    submitMovementTask(computeMovementTarget(mc, false), false);
                    repeatLastPathPosition(5);
                    wasAirborne = false;
                    tickTask(mc);
                    return;
                }
                bridgeLevel = 0;
                if (verticalTransitionTarget == null) {
                    verticalTransitionTarget = scaffold.computeElevatedPlacementPoint(
                            scaffold.offsetPosition(bridgePath.get(bridgePath.size() - 1), 1, direction), 0.0D, direction);
                    submitMovementTask(computeTargetPosition(new double[]{verticalTransitionTarget[0], verticalTransitionTarget[2]}), false);
                }
                repeatLastPathPosition(1);
                updateRotationTarget(mc);
                tickTask(mc);
                return;
            }
            if (bridgeLevel == 0) {
                float[] targetRotation = computeBridgeRotation(direction);
                scaffold.applyFixedRotation(targetRotation, scaffold.computeRotationSpeed(mc, targetRotation));
                ScaffoldInput.setPressed(mc.gameSettings.keyBindSprint, true);
                submitMovementTask(computeMovementTarget(mc, true), false);
                repeatLastPathPosition(1);
                wasAirborne = false;
                consecutiveHeightIncreases = 0;
            } else {
                submitMovementTask(computeMovementTarget(mc, false), false);
                repeatLastPathPosition(4);
                ++consecutiveHeightIncreases;
                wasAirborne = false;
            }
        }
        if (!mc.thePlayer.onGround) {
            updateRotationTarget(mc);
        }
        updateMovementKeys(mc);
    }

    private void updateMovementKeys(Minecraft mc) {
        int pathSize = bridgePath.size();
        if (pathSize == 0) return;
        double[] lastPosition = bridgePath.get(pathSize - 1);
        if (mc.thePlayer.onGround && (bridgeLevel != 0 || mc.thePlayer.isSprinting())
                && hasReachedPathEdge(mc, lastPosition)) {
            ScaffoldInput.setPressed(mc.gameSettings.keyBindJump, true);
            return;
        }
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            ScaffoldInput.setPressed(mc.gameSettings.keyBindJump, false);
        }
        if (!mc.gameSettings.keyBindSprint.isKeyDown()) {
            ScaffoldInput.setPressed(mc.gameSettings.keyBindSprint, true);
        }
    }

    private void updateRotationTarget(Minecraft mc) {
        double[] aimTarget = computeNextPlacementPosition(mc);
        if (aimTarget == null) return;
        if (lastAimTarget == null
                || MathHelper.floor_double(lastAimTarget[0]) != MathHelper.floor_double(aimTarget[0])
                || MathHelper.floor_double(lastAimTarget[1]) != MathHelper.floor_double(aimTarget[1])
                || MathHelper.floor_double(lastAimTarget[2]) != MathHelper.floor_double(aimTarget[2])) {
            lastAimTarget = aimTarget;
            double[] placementPosition = bridgePath.get(bridgePath.size() - 1);
            float rotationSpeed = scaffold.getDirectionRotationSpeed(mc, direction);
            scaffold.applyPointRotation(aimTarget, rotationSpeed, direction, placementPosition);
        }
    }

    // ===== path =====

    private void pruneAndExtendPath(Minecraft mc) {
        int index;
        for (index = bridgePath.size() - 1;
             index >= Math.max(0, bridgePath.size() - 3)
                     && scaffold.isAirBlockAt(bridgePath.get(index)[0], bridgePath.get(index)[1], bridgePath.get(index)[2]);
             --index) {
            lastAimTarget = null;
            bridgePath.remove(index);
        }
        int pathSize = bridgePath.size();
        if (pathSize == 0) return;
        double[] nextPosition = advancePathPosition(bridgePath.get(pathSize - 1), pathSize);
        if (!scaffold.isAirBlockAt(nextPosition[0], nextPosition[1], nextPosition[2])) {
            if (pathSize == 6) bridgePath.clear();
            bridgePath.add(new double[]{nextPosition[0], nextPosition[1], nextPosition[2]});
            scaffold.releaseRotation();
        }
    }

    private double[] advancePathPosition(double[] position, int pathSize) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        if (bridgeLevel != 0 && pathSize == 4) {
            y += 1.0D;
        } else if (direction == 6) {
            x += 1.0D;
        } else if (direction == 8) {
            x -= 1.0D;
        } else if (direction == 7) {
            z += 1.0D;
        } else if (direction == 5) {
            z -= 1.0D;
        }
        return new double[]{x, y, z};
    }

    private double[] computeNextPlacementPosition(Minecraft mc) {
        int pathSize = bridgePath.size();
        if (pathSize == 0) return null;
        if (bridgeLevel != 0 && pathSize == 4) {
            return scaffold.computeElevatedPlacementPoint(bridgePath.get(pathSize - 1), 0.2D, direction);
        }
        return scaffold.computePlacementAimPoint(bridgePath.get(pathSize - 1), 0.3D, 0.2D, direction);
    }

    private void repeatLastPathPosition(int targetSize) {
        double[] lastPosition = bridgePath.get(bridgePath.size() - 1);
        bridgePath.clear();
        while (bridgePath.size() != targetSize) bridgePath.add(lastPosition);
    }

    private double[] computeMovementTarget(Minecraft mc, boolean initialMove) {
        double[] offsetPosition = initialMove
                ? scaffold.offsetPosition(bridgePath.get(bridgePath.size() - 1), manualActivationComplete ? 4 : 3, direction)
                : scaffold.offsetPosition(bridgePath.get(bridgePath.size() - 1), 2, direction);
        return computeTargetPosition(new double[]{offsetPosition[0], offsetPosition[2]});
    }

    private double[] computeTargetPosition(double[] horizontalPosition) {
        double x = horizontalPosition[0];
        double z = horizontalPosition[1];
        if (direction == 6) {
            x = MathHelper.floor_double(x) + 0.3D;
            z = MathHelper.floor_double(z) + 0.6D;
        } else if (direction == 8) {
            x = MathHelper.floor_double(x) + 0.7D;
            z = MathHelper.floor_double(z) + 0.4D;
        } else if (direction == 7) {
            x = MathHelper.floor_double(x) + 0.4D;
            z = MathHelper.floor_double(z) + 0.3D;
        } else if (direction == 5) {
            x = MathHelper.floor_double(x) + 0.6D;
            z = MathHelper.floor_double(z) + 0.7D;
        }
        return new double[]{x, z};
    }

    private float[] computeBridgeRotation(int directionValue) {
        double yaw = 90.0D;
        double pitch = 90.0D;
        if (directionValue == 6) yaw = 230.0D;
        else if (directionValue == 8) yaw = 50.0D;
        else if (directionValue == 7) yaw = 320.0D;
        else if (directionValue == 5) yaw = 140.0D;
        yaw += Math.random() < 0.5D ? Math.random() * -4.0D : Math.random() * 4.0D;
        pitch += Math.random() * -5.0D;
        return new float[]{(float) yaw, (float) pitch};
    }

    private int randomHeightIncreaseThreshold() {
        double randomValue = Math.random();
        int configuredIncrease = (int) yIncrease.value();
        if (configuredIncrease != 0) {
            int lowerValue = configuredIncrease - 1;
            int upperValue = configuredIncrease + 1;
            if (randomValue < 0.15D) return upperValue;
            if (randomValue < 0.25D) return lowerValue;
            return configuredIncrease;
        }
        return 0;
    }

    // ===== look / geometry =====

    private boolean isLookingAtPlacement(Minecraft mc) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                || bridgePath.size() < 1) {
            return false;
        }
        double[] placementPosition = bridgePath.get(bridgePath.size() - 1);
        double[] hitPosition = {
                MathHelper.floor_double(mc.objectMouseOver.hitVec.xCoord),
                MathHelper.floor_double(mc.objectMouseOver.hitVec.yCoord),
                MathHelper.floor_double(mc.objectMouseOver.hitVec.zCoord)};
        boolean positionsMatch = placementPosition[0] == hitPosition[0]
                && placementPosition[1] == hitPosition[1]
                && placementPosition[2] == hitPosition[2];
        int side = mc.objectMouseOver.sideHit.getIndex();
        boolean enoughAttempts = bridgeLevel != 0 && bridgePath.size() == 4 ? side == 1 : side > 1;
        return positionsMatch && enoughAttempts;
    }

    private boolean isGroundPositionClear(Minecraft mc) {
        double sampleX = mc.thePlayer.posX;
        double sampleZ = mc.thePlayer.posZ;
        if (direction == 6) {
            sampleX += 0.15D;
        } else if (direction == 8) {
            sampleX -= 0.15D;
        } else if (direction == 7) {
            sampleZ += 0.15D;
        } else if (direction == 5) {
            sampleZ -= 0.15D;
        } else {
            return true;
        }
        sampleX = MathHelper.floor_double(sampleX);
        double placementY = scaffold.getPlacementY(mc);
        sampleZ = MathHelper.floor_double(sampleZ);
        return !scaffold.isAirBlockAt(sampleX, placementY, sampleZ) && mc.thePlayer.onGround;
    }

    private boolean hasReachedPathEdge(Minecraft mc, double[] pathPosition) {
        double targetX = pathPosition[0];
        double targetZ = pathPosition[2];
        double edgeOffset = 0.8D;
        if (direction == 6) {
            double distance = mc.thePlayer.posX - (targetX += edgeOffset);
            return distance >= -0.05D;
        }
        if (direction == 8) {
            double distance = mc.thePlayer.posX - (targetX -= 1.0D - edgeOffset);
            return distance <= 0.05D;
        }
        if (direction == 7) {
            double distance = mc.thePlayer.posZ - (targetZ += edgeOffset);
            return distance >= -0.05D;
        }
        if (direction == 5) {
            double distance = mc.thePlayer.posZ - (targetZ -= 1.0D - edgeOffset);
            return distance <= 0.05D;
        }
        return false;
    }

    private boolean hasPassedPathEdge(Minecraft mc) {
        double targetX = bridgePath.get(bridgePath.size() - 1)[0];
        double targetZ = bridgePath.get(bridgePath.size() - 1)[2];
        double fullBlockOffset = 1.0D;
        double threshold = 0.1D;
        if (direction == 6) {
            double distance = mc.thePlayer.posX - (targetX += fullBlockOffset);
            return distance < -threshold && Math.abs(mc.thePlayer.posZ - (targetZ += 0.6D)) <= 0.15D;
        }
        if (direction == 8) {
            double distance = mc.thePlayer.posX - (targetX -= 1.0D - fullBlockOffset);
            return distance > threshold && Math.abs(mc.thePlayer.posZ - (targetZ += 0.4D)) <= 0.15D;
        }
        if (direction == 7) {
            double distance = mc.thePlayer.posZ - (targetZ += fullBlockOffset);
            return distance < -threshold && Math.abs(mc.thePlayer.posX - (targetX += 0.4D)) <= 0.15D;
        }
        if (direction == 5) {
            double distance = mc.thePlayer.posZ - (targetZ -= 1.0D - fullBlockOffset);
            return distance > threshold && Math.abs(mc.thePlayer.posX - (targetX += 0.6D)) <= 0.15D;
        }
        return false;
    }

    private boolean isAxisMotionBelowThreshold(Minecraft mc) {
        if (direction % 2 == 0) return Math.abs(mc.thePlayer.motionX) < 0.6D;
        return Math.abs(mc.thePlayer.motionZ) < 0.6D;
    }

    private boolean hasAxisMotion(Minecraft mc) {
        if (direction % 2 == 0) return Math.abs(mc.thePlayer.motionX) >= 0.1D;
        return Math.abs(mc.thePlayer.motionZ) >= 0.1D;
    }

    // ===== inventory / history =====

    private boolean ensureBlockSlot(Minecraft mc) {
        int blockSlot = scaffold.findBlockHotbarSlot();
        if (blockSlot == -1 || scaffold.countBlocks(1) < 5) return false;
        ItemStack heldNow = mc.thePlayer.getHeldItem();
        if (activationItemStack != null && (heldNow == null || heldNow.getItem() != activationItemStack.getItem()
                || heldNow.getItemDamage() != activationItemStack.getItemDamage())) {
            int originalItemSlot = scaffold.findMatchingHotbarSlot(mc, activationItemStack);
            if (originalItemSlot != -1) {
                scaffold.selectHotbarSlot(originalItemSlot);
            } else if (mc.thePlayer.inventory.currentItem != blockSlot) {
                scaffold.selectHotbarSlot(blockSlot);
            }
        }
        return true;
    }

    private void updatePlacementHistory(Minecraft mc) {
        recentPlacements.add(0, Math.abs(mc.mouseHelper.deltaX));
        recentPlacements.add(0, Math.abs(mc.mouseHelper.deltaY));
        while (recentPlacements.size() > 6) recentPlacements.remove(recentPlacements.size() - 1);
    }

    private long sumPlacementHistory() {
        long total = 0;
        for (Integer value : recentPlacements) total += value;
        return total;
    }

    // ===== state =====

    private void resetBridgeState() {
        resetPending = true;
        consecutiveHeightIncreases = 0;
        movementTask = null;
        bridgingActive = false;
        direction = 0;
        bridgePath.clear();
        activationItemStack = null;
    }

    private void submitMovementTask(double[] targetPosition, boolean restoreInput) {
        movementTask = new WalkTask(targetPosition[0], targetPosition[1]);
        movementTask.restoreInputOnCompletion(restoreInput);
        movementTask.waitForGroundAfterArrival(true);
    }

    void tickTask(Minecraft mc) {
        if (movementTask != null && !movementTask.completed) movementTask.applyMovementInput(mc);
        if (movementTask != null) {
            movementTask.updateCompletion(mc);
            if (movementTask.completed) {
                movementTask.finish(movementTask.restoreInputOnCompletion);
            }
        }
    }
}
