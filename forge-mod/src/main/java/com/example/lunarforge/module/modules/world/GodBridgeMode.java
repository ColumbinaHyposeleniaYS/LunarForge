package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.util.GameplayUtil;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

/**
 * Ported from Vape v4 (blatant.scaffold.BlatantScaffoldMode, the "GodBridge"
 * Scaffold mode). Places blocks behind you at full speed without sneaking:
 * after "Activation Blocks" manual placements the module owns the movement
 * keys, walks backwards along the bridge, auto-rotates to the placement
 * point (GodBridge yaw + downward pitch) and right-click places whenever the
 * crosshair sits on the target face. Tapping A/D rotates the bridge 45°
 * between the eight directions; releasing the backward key hands the
 * controls straight back to the player.
 *
 * Vape plumbing that has no LunarForge counterpart: rotation/movement
 * control claims become a simple claim flag on the module (read by Aim
 * Assist), FreeLook gates and the legacy key-repeat suppression are dropped,
 * and the block-count HUD stays unported.
 */
final class GodBridgeMode {
    private static final int[] DIRECTION_CYCLE = {5, 4, 6, 1, 7, 2, 8, 3};

    private final ModuleScaffold scaffold;
    private final NumberActivation activationBlocks;

    private boolean activationPending;
    private boolean rotationPending;
    private boolean taskBlocksUpdate;
    private int taskTicks;
    private int taskTickLimit;
    private boolean atEdge = true;
    private boolean keyIdle = true;
    private boolean switching;
    private boolean reversed;
    private boolean prevLeftDown;
    private boolean prevRightDown;
    private int direction;
    private int pendingDirection;
    private int blocksPlaced;
    private long moveTimer;
    private double[] targetPos;
    private double[] placePos;
    private float[] targetRotation;
    private ItemStack heldBlock;
    private final ArrayList<Integer> placementHistory = new ArrayList<Integer>();
    private WalkTask movementTask;

    GodBridgeMode(ModuleScaffold scaffold, NumberActivation activationBlocks) {
        this.scaffold = scaffold;
        this.activationBlocks = activationBlocks;
    }

    interface NumberActivation {
        double value();
    }

    void onEnable() {
        activationPending = true;
    }

    void onDisable() {
        scaffold.releaseControls();
        resetState();
    }

    private void resetState() {
        activationPending = true;
        targetPos = null;
        movementTask = null;
        taskTicks = 0;
        targetRotation = null;
        prevLeftDown = false;
        prevRightDown = false;
        switching = false;
        atEdge = true;
        direction = 0;
        pendingDirection = 0;
        placementHistory.clear();
        heldBlock = null;
        pendingDirection = 0;
        keyIdle = true;
        scaffold.setMovementClaim(false);
    }

    private int rotateDirection(int from, int offset) {
        int index = indexOf(from);
        int raw = index + offset;
        int size = DIRECTION_CYCLE.length;
        int wrapped = raw < 0 ? raw % size + size : raw % size;
        return DIRECTION_CYCLE[wrapped];
    }

    private int indexOf(int directionValue) {
        for (int i = 0; i < DIRECTION_CYCLE.length; i++) {
            if (DIRECTION_CYCLE[i] == directionValue) return i;
        }
        return 0;
    }

    // ===== tick =====

    void onTick(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        placeIfLooking(mc);
        if (!activationPending && !rotationPending && !taskBlocksUpdate) {
            updateBridgeInput(mc);
        }
        if (activationPending) {
            if (movementTask != null || targetRotation != null) {
                scaffold.releaseControls();
                scaffold.resetEdgeSneakForActivation();
            }
            resetActivationButKeepState();
            activationPending = updatePlacement(mc);
            scaffold.setMovementClaim(false);
            return;
        }
        scaffold.setMovementClaim(true);
        if (shouldReleaseControl(mc)) {
            scaffold.releaseRotation();
            scaffold.resetEdgeSneakForActivation();
            scaffold.setMovementClaim(false);
            return;
        }
        if (handleRotationAndTask(mc)) return;
        if (handleDirectionSwitch(mc)) return;
        if (recoverIfStuck(mc)) return;
        updateRotation(mc);
        updateBridgeInput(mc);
    }

    private void resetActivationButKeepState() {
        activationPending = true;
        targetPos = null;
        movementTask = null;
        taskTicks = 0;
        targetRotation = null;
        prevLeftDown = false;
        prevRightDown = false;
        switching = false;
        atEdge = true;
        direction = 0;
        pendingDirection = 0;
        heldBlock = null;
        keyIdle = true;
        scaffold.setMovementClaim(false);
    }

    // ===== placement activation =====

    private boolean updatePlacement(Minecraft mc) {
        double blockX = MathHelper.floor_double(mc.thePlayer.posX);
        double blockZ = MathHelper.floor_double(mc.thePlayer.posZ);
        double placementY = scaffold.getPlacementY(mc);
        if (!scaffold.isUsableBlockStack(mc.thePlayer.getHeldItem())) {
            placePos = null;
            blocksPlaced = 0;
            return true;
        }
        int currentDirection = scaffold.getCardinalDirection(mc);
        if (direction != 0 && currentDirection != direction) {
            placePos = null;
            blocksPlaced = 0;
        }
        direction = currentDirection;
        double[] playerBlock = {blockX, placementY, blockZ};
        double[] oneBlockAhead = scaffold.offsetPosition(playerBlock, 1, direction);
        double[] twoBlocksAhead = scaffold.offsetPosition(playerBlock, 2, direction);
        if (placePos == null && mc.thePlayer.onGround) {
            if (scaffold.isAirBlockAt(playerBlock[0], playerBlock[1], playerBlock[2])) {
                placePos = playerBlock;
            } else if (scaffold.isAirBlockAt(oneBlockAhead[0], oneBlockAhead[1], oneBlockAhead[2])) {
                placePos = oneBlockAhead;
            } else if (scaffold.isAirBlockAt(twoBlocksAhead[0], twoBlocksAhead[1], twoBlocksAhead[2])) {
                placePos = twoBlocksAhead;
            }
        } else if (placePos != null) {
            if (blocksPlaced >= (int) activationBlocks.value()) {
                reversed = shouldReverse(mc, direction);
                targetPos = computePlacementPoint(new double[]{placePos[0], placePos[2]}, direction, reversed);
                heldBlock = mc.thePlayer.getHeldItem();
                if (!GameplayUtil.physicalDown(mc.gameSettings.keyBindJump) && !mc.thePlayer.capabilities.isFlying) {
                    startMoveTaskNoTurn(targetPos, true, false, 40);
                    targetRotation = computeDiagonalRotation(mc, reversed);
                    scaffold.applyFixedRotation(targetRotation, computeRotationSpeed(mc, targetRotation, 15));
                }
                blocksPlaced = 0;
                placePos = null;
                moveTimer = System.currentTimeMillis();
                return false;
            }
            if (!scaffold.isAirBlockAt(placePos[0], placePos[1], placePos[2])) {
                ++blocksPlaced;
                double[] nextPlacement = scaffold.offsetPosition(placePos, 1, direction);
                boolean nextPlacementIsEmpty = scaffold.isAirBlockAt(nextPlacement[0], nextPlacement[1], nextPlacement[2]);
                if (nextPlacementIsEmpty && blocksPlaced < (int) activationBlocks.value()) {
                    placePos = nextPlacement;
                } else if (!nextPlacementIsEmpty) {
                    placePos = null;
                    blocksPlaced = 0;
                }
            } else if (scaffold.hasPlacementDrifted(placePos, playerBlock, direction, activationBlocks.value(), blocksPlaced)) {
                placePos = null;
                blocksPlaced = 0;
            }
        }
        return true;
    }

    private boolean shouldReleaseControl(Minecraft mc) {
        if (!ensureBlockSelected(mc)) {
            activationPending = true;
            ScaffoldInput.releaseMovementKeys();
            return true;
        }
        if (!GameplayUtil.physicalDown(mc.gameSettings.keyBindBack)) {
            if (!mc.thePlayer.capabilities.isFlying && !isNextBlockEmpty(mc)) {
                return false;
            }
            ScaffoldInput.releaseMovementKeys();
            activationPending = true;
            return true;
        }
        updatePlacementHistory(mc);
        if (sumPlacementHistory() >= 10) {
            ScaffoldInput.releaseMovementKeys();
            activationPending = true;
            return true;
        }
        return false;
    }

    private boolean ensureBlockSelected(Minecraft mc) {
        int blockSlot = scaffold.findBlockHotbarSlot();
        if (blockSlot == -1) return false;
        ItemStack heldNow = mc.thePlayer.getHeldItem();
        if (heldBlock != null && (heldNow == null || heldNow.getItem() != heldBlock.getItem()
                || heldNow.getItemDamage() != heldBlock.getItemDamage())) {
            int heldBlockSlot = scaffold.findMatchingHotbarSlot(mc, heldBlock);
            if (heldBlockSlot != -1) {
                scaffold.selectHotbarSlot(heldBlockSlot);
            } else if (mc.thePlayer.inventory.currentItem != blockSlot) {
                scaffold.selectHotbarSlot(blockSlot);
            }
        }
        return true;
    }

    // ===== movement / task =====

    private void updateBridgeInput(Minecraft mc) {
        boolean edge = isAtEdge(mc, direction);
        boolean diagonalDirection = direction < 5;
        if (!edge) moveTimer = System.currentTimeMillis();
        atEdge = edge;
        KeyBinding backwardKey = mc.gameSettings.keyBindBack;
        KeyBinding leftKey = mc.gameSettings.keyBindLeft;
        KeyBinding rightKey = mc.gameSettings.keyBindRight;
        if (edge) {
            ScaffoldInput.setPressed(backwardKey, false);
            ScaffoldInput.setPressed(leftKey, false);
            ScaffoldInput.setPressed(rightKey, false);
            if (!diagonalDirection) {
                ScaffoldInput.setPressed(reversed ? rightKey : leftKey, false);
            }
        } else {
            ScaffoldInput.setPressed(backwardKey, true);
            if (!diagonalDirection) {
                ScaffoldInput.setPressed(reversed ? rightKey : leftKey, true);
            } else {
                ScaffoldInput.setPressed(leftKey, false);
                ScaffoldInput.setPressed(rightKey, false);
            }
        }
    }

    private boolean advanceTask() {
        if (movementTask != null) {
            if (!movementTask.completed && taskTicks < taskTickLimit) {
                ++taskTicks;
                if (taskBlocksUpdate) return true;
            } else if (movementTask.completed) {
                taskTicks = 0;
                taskBlocksUpdate = false;
            } else {
                movementTask.completed = true;
                movementTask.finish(false);
                movementTask = null;
                taskTicks = 0;
                taskBlocksUpdate = false;
                return true;
            }
        }
        return false;
    }

    private boolean isRotationOffTarget(Minecraft mc) {
        ScaffoldRotation controller = scaffold.rotationController;
        if (controller != null) {
            if (scaffold.angularDistance(normalizedYaw(mc), controller.getTargetYaw()) > 4.0D) {
                if (rotationPending) return true;
            } else if (rotationPending) {
                scaffold.releaseRotation();
                rotationPending = false;
            }
        }
        return false;
    }

    private boolean handleRotationAndTask(Minecraft mc) {
        return isRotationOffTarget(mc) || advanceTask();
    }

    void applyTaskMovement(Minecraft mc) {
        if (movementTask != null && !movementTask.completed) movementTask.applyMovementInput(mc);
    }

    void completeTaskUpdate(Minecraft mc) {
        if (movementTask != null) movementTask.updateCompletion(mc);
    }

    // ===== direction switching =====

    private boolean handleDirectionSwitch(Minecraft mc) {
        if (movementTask != null && movementTask.completed && switching) {
            atEdge = true;
            movementTask = null;
            targetPos = pendingPos;
            direction = pendingDirection;
            reversed = pendingReversed;
            switching = false;
            ScaffoldInput.releaseMovementKeys();
            moveTimer = System.currentTimeMillis();
            if (direction < 5) {
                float[] rotation = computePlacementRotation(mc,
                        computePlacementPoint(new double[]{targetPos[0], targetPos[1]}, direction, reversed), direction);
                scaffold.applyRotation(rotation, computeRotationSpeed(mc, rotation, 15));
            } else {
                float[] rotation = computeDiagonalRotation(mc, reversed);
                scaffold.applyRotation(rotation, computeRotationSpeed(mc, rotation, 12));
            }
            rotationPending = true;
            return true;
        }

        boolean leftPressed = GameplayUtil.physicalDown(mc.gameSettings.keyBindLeft);
        boolean rightPressed = GameplayUtil.physicalDown(mc.gameSettings.keyBindRight);
        KeyBinding leftKey = mc.gameSettings.keyBindLeft;
        KeyBinding rightKey = mc.gameSettings.keyBindRight;
        if (!switching) {
            boolean leftTransition;
            boolean rightTransition;
            if (keyIdle) {
                leftTransition = leftPressed && !prevLeftDown;
                rightTransition = rightPressed && !prevRightDown;
            } else {
                leftTransition = !leftPressed && prevLeftDown;
                rightTransition = !rightPressed && prevRightDown;
            }
            prevLeftDown = leftPressed;
            prevRightDown = rightPressed;

            if (keyIdle) {
                keyIdle = !leftTransition && !rightTransition;
                if (leftTransition) pendingDirection = rotateDirection(direction, 1);
                else if (rightTransition) pendingDirection = rotateDirection(direction, -1);
            } else {
                keyIdle = leftTransition || rightTransition;
                if (leftTransition) pendingDirection = rotateDirection(direction, -1);
                else if (rightTransition) pendingDirection = rotateDirection(direction, 1);
            }
            switching = leftTransition || rightTransition;
        }

        if (switching) {
            movementTask = null;
            double[] playerBlock = {MathHelper.floor_double(mc.thePlayer.posX), scaffold.getPlacementY(mc), MathHelper.floor_double(mc.thePlayer.posZ)};
            boolean playerBlockAir = scaffold.isAirBlockAt(playerBlock[0], playerBlock[1], playerBlock[2]);
            if (!(playerBlockAir || isFrontClear(mc) || isPastCorner(mc, direction))) {
                pendingReversed = shouldReverse(mc, pendingDirection);
                pendingPos = computePlacementPoint(new double[]{playerBlock[0], playerBlock[2]}, pendingDirection, pendingReversed);
                scaffold.releaseControls();
                if (direction > 4 && !reversed && pendingDirection == rotateDirection(direction, -1)
                        || direction > 4 && reversed && pendingDirection == rotateDirection(direction, 1)) {
                    movementTask = new WalkTask(0.0D, 0.0D);
                    movementTask.completed = true;
                } else if (Math.abs(mc.thePlayer.posX - pendingPos[0]) > 0.15D
                        || Math.abs(mc.thePlayer.posZ - pendingPos[1]) > 0.15D) {
                    startMoveTaskNoTurn(pendingPos, true, false, 40);
                } else {
                    movementTask = new WalkTask(0.0D, 0.0D);
                    movementTask.completed = true;
                }
            } else if (!reversed) {
                if (leftPressed && isAtEdge(mc, direction)) ScaffoldInput.setPressed(leftKey, false);
                else if (rightPressed) ScaffoldInput.setPressed(rightKey, false);
            } else if (rightPressed && isAtEdge(mc, direction)) {
                ScaffoldInput.setPressed(rightKey, false);
            } else if (leftPressed) {
                ScaffoldInput.setPressed(leftKey, false);
            }
        }
        return false;
    }

    private boolean recoverIfStuck(Minecraft mc) {
        if (System.currentTimeMillis() - moveTimer >= 800L) {
            double[] anchorTarget = computeAnchorTarget(mc, direction);
            targetPos = computePlacementPoint(new double[]{anchorTarget[0], anchorTarget[2]}, direction, reversed);
            targetRotation = computeDiagonalRotation(mc, reversed);
            scaffold.applyFixedRotation(targetRotation, computeRotationSpeed(mc, targetRotation, 15));
            startMoveTaskNoTurn(targetPos, true, false, 40);
            atEdge = true;
            moveTimer = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    // ===== rotation =====

    private void updateRotation(Minecraft mc) {
        if (direction == 0) {
            targetRotation = new float[]{mc.thePlayer.rotationYaw, 90.0f};
        } else if (direction < 5) {
            float[] previousRotation = targetRotation;
            targetRotation = computePlacementRotation(mc,
                    computePlacementPoint(new double[]{targetPos[0], targetPos[1]}, direction, reversed), direction);
            if (scaffold.rotationController == null || previousRotation == null
                    || previousRotation[0] != targetRotation[0] || previousRotation[1] != targetRotation[1]) {
                scaffold.applyFixedRotation(targetRotation, computeRotationSpeed(mc, targetRotation, 15));
            }
        } else {
            float[] previousRotation = targetRotation;
            targetRotation = computeDiagonalRotation(mc, reversed);
            if (scaffold.rotationController == null || previousRotation == null
                    || previousRotation[0] != targetRotation[0] || previousRotation[1] != targetRotation[1]) {
                scaffold.applyRotation(targetRotation, computeRotationSpeed(mc, targetRotation, 15));
            }
        }
    }

    private float[] computeDiagonalRotation(Minecraft mc, boolean reversedFlag) {
        double yaw = mc.thePlayer.rotationYaw;
        double playerX = mc.thePlayer.posX;
        double playerZ = mc.thePlayer.posZ;
        double reversedYaw = yaw;
        double forwardYaw = yaw;
        if (direction == 6) {
            reversedYaw = 135.0D + 20.0D * (targetPos[1] - playerZ);
            forwardYaw = 45.0D + 20.0D * (targetPos[1] - playerZ);
        } else if (direction == 8) {
            reversedYaw = -45.0D - 20.0D * (targetPos[1] - playerZ);
            forwardYaw = -135.0D - 20.0D * (targetPos[1] - playerZ);
        } else if (direction == 7) {
            reversedYaw = -135.0D - 20.0D * (targetPos[0] - playerX);
            forwardYaw = 135.0D + 20.0D * (playerX - targetPos[0]);
        } else if (direction == 5) {
            reversedYaw = 45.0D + 20.0D * (targetPos[0] - playerX);
            forwardYaw = -45.0D - 20.0D * (playerX - targetPos[0]);
        }
        yaw = reversedFlag ? reversedYaw : forwardYaw;
        return new float[]{(float) yaw, System.currentTimeMillis() - moveTimer > 300L ? 80.0f : 78.0f};
    }

    private float[] computePlacementRotation(Minecraft mc, double[] placementPoint, int directionValue) {
        float pathOffset = 0.1f * computeSideOfPath(placementPoint, new double[]{mc.thePlayer.posX, mc.thePlayer.posZ}, directionValue);
        float yaw = directionValue == 1 ? 135.0f - pathOffset
                : directionValue == 2 ? -135.0f - pathOffset
                : directionValue == 3 ? -45.0f - pathOffset
                : 45.0f - pathOffset;
        return new float[]{yaw, System.currentTimeMillis() - moveTimer > 500L ? 83.0f : 81.0f};
    }

    private float computeSideOfPath(double[] pathPoint, double[] playerPosition, int directionValue) {
        int relativeDirection = (int) (distance(pathPoint[0], pathPoint[1], playerPosition[0], playerPosition[1])
                + scaffold.countBlocks(1));
        double[] adjacentPoint = scaffold.offsetPosition(new double[]{pathPoint[0], 0.0D, pathPoint[1]},
                relativeDirection, directionValue);
        return (float) ((adjacentPoint[0] - pathPoint[0]) * (playerPosition[1] - pathPoint[1])
                - (adjacentPoint[2] - pathPoint[1]) * (playerPosition[0] - pathPoint[0]));
    }

    private static double distance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private float computeRotationSpeed(Minecraft mc, float[] rotation, int divisor) {
        return (float) Math.min(2.0D + scaffold.angularDistance(normalizedYaw(mc), rotation[0]) / divisor, 12.0D);
    }

    private static float normalizedYaw(Minecraft mc) {
        float yaw = mc.thePlayer.rotationYaw % 360.0f;
        return yaw < 0.0f ? 360.0f + yaw : yaw;
    }

    // ===== geometry =====

    private boolean isAtEdge(Minecraft mc, int directionValue) {
        if (directionValue > 4) {
            double sampleX = mc.thePlayer.posX;
            double sampleZ = mc.thePlayer.posZ;
            if (directionValue == 6) {
                sampleX += -0.15D;
                sampleZ = targetPos[1];
            } else if (directionValue == 8) {
                sampleX -= -0.15D;
                sampleZ = targetPos[1];
            } else if (directionValue == 7) {
                sampleX = targetPos[0];
                sampleZ += -0.15D;
            } else if (directionValue == 5) {
                sampleX = targetPos[0];
                sampleZ -= -0.15D;
            }
            sampleX = MathHelper.floor_double(sampleX);
            double placementY = scaffold.getPlacementY(mc);
            sampleZ = MathHelper.floor_double(sampleZ);
            return scaffold.isAirBlockAt(sampleX, placementY, sampleZ);
        }
        return !shrunkBoxCollides(mc, -0.16D);
    }

    private boolean isFrontClear(Minecraft mc) {
        return !shrunkBoxCollides(mc, -0.2D);
    }

    /** Player bounding box shrunk by the amount and offset by (motionX, -1, motionZ). */
    private boolean shrunkBoxCollides(Minecraft mc, double shrinkAmount) {
        net.minecraft.util.AxisAlignedBB checkBox = mc.thePlayer.getEntityBoundingBox()
                .expand(shrinkAmount, 0.0D, shrinkAmount)
                .offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, checkBox).isEmpty();
    }

    private boolean isNextBlockEmpty(Minecraft mc) {
        double sampleX = mc.thePlayer.posX;
        double sampleZ = mc.thePlayer.posZ;
        if (direction == 1) {
            sampleX += 0.2D;
            sampleZ += 0.2D;
        } else if (direction == 2) {
            sampleX -= 0.2D;
            sampleZ += 0.2D;
        } else if (direction == 3) {
            sampleX -= 0.2D;
            sampleZ -= 0.2D;
        } else if (direction == 4) {
            sampleX += 0.2D;
            sampleZ -= 0.2D;
        } else if (direction == 6) {
            sampleX += 0.25D;
            sampleZ = targetPos[1];
        } else if (direction == 8) {
            sampleX -= 0.25D;
            sampleZ = targetPos[1];
        } else if (direction == 7) {
            sampleX = targetPos[0];
            sampleZ += 0.25D;
        } else if (direction == 5) {
            sampleX = targetPos[0];
            sampleZ -= 0.25D;
        } else {
            return true;
        }
        sampleX = MathHelper.floor_double(sampleX);
        double placementY = scaffold.getPlacementY(mc);
        sampleZ = MathHelper.floor_double(sampleZ);
        return !scaffold.isAirBlockAt(sampleX, placementY, sampleZ);
    }

    private boolean isPastCorner(Minecraft mc, int directionValue) {
        double playerX = mc.thePlayer.posX;
        double playerZ = mc.thePlayer.posZ;
        double blockX = MathHelper.floor_double(playerX);
        double blockZ = MathHelper.floor_double(playerZ);
        if (directionValue == 1) return playerX - blockX + (playerZ - blockZ) > 1.0D;
        if (directionValue == 2) return blockX - playerX + (playerZ - blockZ) > 1.0D;
        if (directionValue == 3) return blockX - playerX + (blockZ - playerZ) > 1.0D;
        if (directionValue == 4) return playerX - blockX + (blockZ - playerZ) > 1.0D;
        if (directionValue == 6) return playerX - blockX > 0.5D;
        if (directionValue == 8) return blockX - playerX > 0.5D;
        if (directionValue == 7) return playerZ - blockZ > 0.5D;
        if (directionValue == 5) return blockZ - playerZ > 0.5D;
        return false;
    }

    private boolean shouldReverse(Minecraft mc, int directionValue) {
        if (directionValue > 4) {
            double[] yawBounds = getDirectionYawBounds(directionValue);
            double currentYaw = normalizedYaw(mc);
            return scaffold.angularDistance(currentYaw, yawBounds[0])
                    <= scaffold.angularDistance(currentYaw, yawBounds[1]);
        }
        return !reversed;
    }

    private static double[] getDirectionYawBounds(int directionValue) {
        if (directionValue == 6) return new double[]{135.0D, 45.0D};
        if (directionValue == 8) return new double[]{315.0D, 225.0D};
        if (directionValue == 7) return new double[]{225.0D, 135.0D};
        if (directionValue == 5) return new double[]{45.0D, 315.0D};
        return new double[0];
    }

    private double[] computeAnchorTarget(Minecraft mc, int directionValue) {
        double placementY = mc.thePlayer.motionY > 0.0D ? scaffold.getPlacementY(mc) + 1.0D : scaffold.getPlacementY(mc);
        double[] anchorTarget = {MathHelper.floor_double(mc.thePlayer.posX), placementY, MathHelper.floor_double(mc.thePlayer.posZ)};
        if (scaffold.isAirBlockAt(anchorTarget[0], anchorTarget[1], anchorTarget[2])
                && isAir(scaffold.offsetPosition(anchorTarget, -1, directionValue))
                && isAir(scaffold.offsetPosition(scaffold.offsetPosition(anchorTarget, -1, directionValue), 1, rotateDirection(directionValue, 2)))) {
            anchorTarget = scaffold.offsetPosition(
                    scaffold.offsetPosition(anchorTarget, -1, directionValue), -2, rotateDirection(directionValue, 2));
        }
        return anchorTarget;
    }

    private boolean isAir(double[] position) {
        return scaffold.isAirBlockAt(position[0], position[1], position[2]);
    }

    // Vape computePlacementPoint (x/z placement anchor per direction and reversal)
    private double[] computePlacementPoint(double[] position, int directionValue, boolean reversedFlag) {
        double x = position[0];
        double z = position[1];
        if (directionValue == 1) {
            x = floorAdd(x, reversedFlag ? 0.65D : 0.35D);
            z = floorAdd(z, reversedFlag ? 0.35D : 0.65D);
        } else if (directionValue == 2) {
            x = floorAdd(x, reversedFlag ? 0.65D : 0.35D);
            z = floorAdd(z, reversedFlag ? 0.65D : 0.35D);
        } else if (directionValue == 3) {
            x = floorAdd(x, reversedFlag ? 0.35D : 0.65D);
            z = floorAdd(z, reversedFlag ? 0.65D : 0.35D);
        } else if (directionValue == 4) {
            x = floorAdd(x, reversedFlag ? 0.35D : 0.65D);
            z = floorAdd(z, reversedFlag ? 0.35D : 0.65D);
        } else if (directionValue == 6) {
            x = floorAdd(x, 0.8D);
            z = floorAdd(z, reversedFlag ? 0.8D : 0.2D);
        } else if (directionValue == 8) {
            x = floorAdd(x, 0.2D);
            z = floorAdd(z, reversedFlag ? 0.2D : 0.8D);
        } else if (directionValue == 7) {
            x = floorAdd(x, reversedFlag ? 0.2D : 0.8D);
            z = floorAdd(z, 0.8D);
        } else if (directionValue == 5) {
            x = floorAdd(x, reversedFlag ? 0.8D : 0.2D);
            z = floorAdd(z, 0.2D);
        }
        return new double[]{x, z};
    }

    private static double floorAdd(double value, double add) {
        return MathHelper.floor_double(value) + add;
    }

    // ===== placement look / history =====

    private boolean isLookingAtPlacement(Minecraft mc) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        int side = mc.objectMouseOver.sideHit.getIndex();
        double verticalMotion = mc.thePlayer.motionY;
        if (verticalMotion > 0.1D || verticalMotion < -0.1D || switching) return side != 0;
        return side > 1;
    }

    void placeIfLooking(Minecraft mc) {
        if (!activationPending && isLookingAtPlacement(mc)) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
    }

    private void updatePlacementHistory(Minecraft mc) {
        placementHistory.add(0, Math.abs(mc.mouseHelper.deltaX));
        placementHistory.add(0, Math.abs(mc.mouseHelper.deltaY));
        while (placementHistory.size() > 6) placementHistory.remove(placementHistory.size() - 1);
    }

    private long sumPlacementHistory() {
        long total = 0;
        for (Integer value : placementHistory) total += value;
        return total;
    }

    // ===== task plumbing =====

    private void startMoveTaskNoTurn(double[] targetPosition, boolean blockUpdates, boolean restoreInput, int tickLimit) {
        movementTask = new WalkTask(targetPosition[0], targetPosition[1]);
        taskBlocksUpdate = blockUpdates;
        movementTask.restoreInputOnCompletion(restoreInput);
        taskTicks = 0;
        taskTickLimit = tickLimit;
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

    boolean isActivationPending() {
        return activationPending;
    }

    // ===== pending switch state =====
    private double[] pendingPos;
    private boolean pendingReversed;
}
