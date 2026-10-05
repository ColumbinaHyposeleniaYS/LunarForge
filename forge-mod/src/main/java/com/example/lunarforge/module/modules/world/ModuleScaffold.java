package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.gui.ui.ItemListWidget;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ItemListSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.WidgetSetting;
import com.example.lunarforge.util.Fields;
import com.example.lunarforge.util.GameplayUtil;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.function.BooleanSupplier;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.blatant.Scaffold + its scaffold modes).
 *
 * Legit mode is a faithful port of Vape's LegitScaffoldMode: it only presses
 * sneak for you at the edge of a block while you bridge backwards
 * (fastbridge/ninja/eagle style), with the randomized sneak delay grace
 * window. Placing blocks stays manual, exactly like Vape.
 *
 * Normal mode keeps the simplified auto placement from the earlier port.
 *
 * GodBridge ({@link GodBridgeMode}) and TellyBridge ({@link TellyBridgeMode})
 * are faithful ports of Vape's blatant modes: after "Activation Blocks"
 * manual placements they own the movement keys, auto-rotate to the placement
 * point and right-click place while you walk backwards (TellyBridge jumps
 * instead, optionally stairs up with "Y increase"). Direction changes on
 * GodBridge are driven by tapping A/D.
 *
 * Block choice uses Vape's real blacklist/whitelist lists (editable in the
 * settings UI) instead of the earlier "full cube except ice" stand-in: an
 * ItemBlock is usable when it is not blacklisted and, with the whitelist
 * enabled, matches the whitelist. The blacklist ships with Vape's default
 * 50 entries. Not ported from Vape: the center-screen block count display.
 */
public final class ModuleScaffold extends Module {

    public enum Mode implements ChoiceSetting.Option {
        LEGIT("Legit"), NORMAL("Normal"), GOD_BRIDGE("GodBridge"), TELLY_BRIDGE("TellyBridge");

        private final String label;

        Mode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final EnumFacing[] SUPPORT_FACES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN};

    private static final Field RIGHT_CLICK_DELAY = Fields.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");

    private final ChoiceSetting<Mode> mode = choice("mode", Mode.LEGIT).label(() -> "Mode");
    private final NumberSetting sneakDelay = integer("sneakDelay", 100, 0, 500).label(() -> "Sneak Delay (ms)");
    private final BoolSetting requireSneak = bool("requireSneak", false).label(() -> "Require Sneak")
            .hideIf(() -> !mode.is(Mode.LEGIT));
    private final BoolSetting autoSwitch = bool("autoSwitch", true).label(() -> "Auto Switch")
            .hideIf(() -> !mode.is(Mode.NORMAL));
    private final BoolSetting rotations = bool("rotations", true).label(() -> "Rotations")
            .hideIf(() -> !mode.is(Mode.NORMAL));
    private final BoolSetting pitchCheck = bool("pitchCheck", false).label(() -> "Pitch Check");
    private final NumberSetting pitchValue = decimal("pitchValue", 45.0F, 0.0F, 90.0F).label(() -> "Pitch")
            .hideIf(() -> !pitchCheck.on());
    private final BoolSetting blacklistToggle = bool("blacklist", true).label(() -> "Blacklist");
    private final ItemListSetting blacklist = new ItemListSetting("scaffoldBlacklist", ItemListSetting.SCAFFOLD_BLACKLIST_DEFAULT);
    private final BoolSetting whitelistToggle = bool("whitelist", false).label(() -> "Whitelist");
    private final ItemListSetting whitelist = new ItemListSetting("scaffoldWhitelist", java.util.Collections.singletonList("blocks"));
    private final NumberSetting activationBlocks = integer("activationBlocks", 2, 1, 4).label(() -> "Activation Blocks")
            .hideIf(() -> !mode.is(Mode.GOD_BRIDGE) && !mode.is(Mode.TELLY_BRIDGE));
    private final BoolSetting requireRightClick = bool("requireRightClick", true).label(() -> "Require Right Click")
            .hideIf(() -> !mode.is(Mode.TELLY_BRIDGE));
    private final NumberSetting yIncrease = integer("yIncrease", 1, 0, 3).label(() -> "Y Increase")
            .hideIf(() -> !mode.is(Mode.TELLY_BRIDGE));

    private final WidgetSetting blacklistUi = new WidgetSetting("scaffoldBlacklistUi", "",
            new ItemListWidget(blacklist, "Block Blacklist", false));
    private final WidgetSetting whitelistUi = new WidgetSetting("scaffoldWhitelistUi", "",
            new ItemListWidget(whitelist, "Block Whitelist", true));

    private long sneakDelayMs = 100L;
    private long edgeSneakTimer;
    private long standDelayTimer;
    private int placeWaitTicks;

    // ===== GodBridge / TellyBridge state =====
    ScaffoldRotation rotationController;
    private boolean movementClaim;
    private final GodBridgeMode godBridge = new GodBridgeMode(this, new GodBridgeMode.NumberActivation() {
        @Override public double value() { return activationBlocks.value(); }
    });
    private final TellyBridgeMode tellyBridge = new TellyBridgeMode(this,
            new TellyBridgeMode.BooleanActivation() {
                @Override public boolean value() { return requireRightClick.on(); }
            },
            new TellyBridgeMode.NumberActivation() {
                @Override public double value() { return activationBlocks.value(); }
            },
            new TellyBridgeMode.NumberActivation() {
                @Override public double value() { return yIncrease.value(); }
            });

    // ===== activation edge sneak (ScaffoldEdgeSneakHelper) =====
    private long activationSneakTimer;
    private long activationSneakDelayMs = 100L;
    private boolean activationSneakKeyWasDown;

    public ModuleScaffold() {
        super("SCAFFOLD", false);
        add(blacklist);
        add(whitelist);
        add(blacklistUi);
        add(whitelistUi);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, sneakDelay, pitchCheck);
            s.add(pitchValue);
            s.add(requireSneak);
            s.add(autoSwitch, rotations);
            s.add(activationBlocks, requireRightClick, yIncrease);
            s.add(blacklistToggle);
            s.add(blacklistUi).hideIf(() -> !blacklistToggle.on());
            s.add(whitelistToggle);
            s.add(whitelistUi).hideIf(() -> !whitelistToggle.on());
        });
    }

    @Override protected void onEnable() {
        sneakDelayMs = rollSneakDelay();
        resetEdgeSneakForActivation();
        if (mode.is(Mode.GOD_BRIDGE)) godBridge.onEnable();
        if (mode.is(Mode.TELLY_BRIDGE)) tellyBridge.onEnable();
        lastMode = mode.get();
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        // Vanilla unpresses every binding when a GUI opens, so with a screen open
        // there is nothing to restore — and restoring from the raw keyboard state
        // would press sneak for Shift held in the GUI (shift-click).
        if (mc.thePlayer != null && mc.gameSettings != null && mc.currentScreen == null) {
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
        releaseControls();
        godBridge.onDisable();
        tellyBridge.onDisable();
        placeWaitTicks = 0;
    }

    // ===== mode change handling =====

    private Mode lastMode;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (lastMode != mode.get()) {
            // switching modes hands the controls back like a fresh enable
            godBridge.onDisable();
            tellyBridge.onDisable();
            releaseControls();
            if (mode.is(Mode.GOD_BRIDGE)) godBridge.onEnable();
            if (mode.is(Mode.TELLY_BRIDGE)) tellyBridge.onEnable();
            lastMode = mode.get();
        }

        if (mode.is(Mode.GOD_BRIDGE)) {
            godBridge.tickTask(mc);
            godBridge.onTick(mc);
        } else if (mode.is(Mode.TELLY_BRIDGE)) {
            tellyBridge.tickTask(mc);
            tellyBridge.onTick(mc);
        } else if (mode.is(Mode.NORMAL)) {
            normalModeTick(mc);
        }

        if (rotationController != null) {
            rotationController.update(mc);
            if (rotationController.isComplete() && !rotationController.shouldRetain()) {
                rotationController = null;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.thePlayer || mc.theWorld == null || !isEnabled()) return;
        if (event.phase == TickEvent.Phase.START) {
            if (mode.is(Mode.LEGIT)) {
                if (shouldScaffold(mc)) edgeSneak(mc);
            } else if (activationSneakActive()) {
                activationEdgeSneak(mc);
            }
        } else if (event.phase == TickEvent.Phase.END) {
            if (mode.is(Mode.LEGIT)) {
                // Never touch the sneak binding while a GUI is open: Shift there belongs
                // to shift-clicks, and restoring from the raw keyboard state would sneak
                // the player in the world while the inventory is open.
                if (mc.currentScreen == null) {
                    GameplayUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
                }
            } else if (activationSneakActive() && mc.currentScreen == null) {
                ScaffoldInput.setPressed(mc.gameSettings.keyBindSneak, activationSneakKeyWasDown);
            }
        }
    }

    private boolean activationSneakActive() {
        return (mode.is(Mode.GOD_BRIDGE) && godBridge.isActivationPending())
                || (mode.is(Mode.TELLY_BRIDGE) && tellyBridge.isResetPending());
    }

    /** Normal mode: pick the block slot, find the placement target, optionally rotate, right-click place. */
    private void normalModeTick(Minecraft mc) {
        if (!shouldScaffold(mc)) return;
        if (RIGHT_CLICK_DELAY != null && Fields.getInt(RIGHT_CLICK_DELAY, mc) > 0) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (!isUsableBlockStack(held)) {
            if (!autoSwitch.on()) return;
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                if (isUsableBlockStack(mc.thePlayer.inventory.getStackInSlot(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) return;
            mc.thePlayer.inventory.currentItem = slot;
            held = mc.thePlayer.getHeldItem();
        }

        BlockPos target = findTarget(mc);
        if (target == null) {
            placeWaitTicks = 0;
            return;
        }
        if (place(mc, target)) placeWaitTicks = 0; else placeWaitTicks++;
    }

    private boolean shouldScaffold(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null) return false;
        if (requireSneak.on() && mode.is(Mode.LEGIT) && !GameplayUtil.physicalDown(mc.gameSettings.keyBindSneak)) return false;
        if (mc.thePlayer.isSpectator()) return false;
        if (pitchCheck.on() && mc.thePlayer.rotationPitch < pitchValue.value()) return false;
        return true;
    }

    // ===== shared rotation plumbing (Vape Scaffold.applyFixedRotation/applyPointRotation/releaseRotation) =====

    /** Scaffold.applyFixedRotation flavour: tolerance 0.5, no axis scaling (GodBridge activation, Telly flat). */
    void applyFixedRotation(float[] rotation, float speed) {
        if (rotationController != null && rotationController.isPointMode()) releaseRotation();
        if (rotationController == null) {
            rotationController = new ScaffoldRotation()
                    .tolerance(0.5f)
                    .clampStepToRemaining()
                    .linearAcceleration()
                    .retainAfterCompletion();
            rotationController.setSpeed(speed);
            rotationController.setTargetRotation(rotation[0], rotation[1]);
        } else {
            rotationController.setSpeed(speed);
            rotationController.setTargetRotation(rotation[0], rotation[1]);
        }
    }

    /** BlatantScaffoldMode.applyRotation flavour: tolerance 0, axes scaled proportionally (GodBridge running). */
    void applyRotation(float[] rotation, float speed) {
        if (rotationController != null && rotationController.isPointMode()) releaseRotation();
        if (rotationController == null) {
            rotationController = new ScaffoldRotation()
                    .tolerance(0.0f)
                    .clampStepToRemaining()
                    .linearAcceleration()
                    .scaleAxesProportionally()
                    .retainAfterCompletion();
            rotationController.setSpeed(speed);
            rotationController.setTargetRotation(rotation[0], rotation[1]);
        } else {
            rotationController.setSpeed(speed);
            rotationController.setTargetRotation(rotation[0], rotation[1]);
        }
    }

    /** Scaffold.applyPointRotation (TellyBridge): world point target with the facing yaw gate. */
    void applyPointRotation(double[] target, float speed, int direction, double[] placementPosition) {
        if (rotationController != null && !rotationController.isPointMode()) releaseRotation();
        if (rotationController == null) {
            rotationController = new ScaffoldRotation()
                    .tolerance(0.0f)
                    .clampStepToRemaining()
                    .linearAcceleration()
                    .retainAfterCompletion();
            rotationController.setSpeed(speed);
            rotationController.setTargetPoint(target[0], target[1], target[2]);
            rotationController.yawGate(facingGate(direction, placementPosition));
        } else {
            rotationController.setSpeed(speed);
            rotationController.setTargetPoint(target[0], target[1], target[2]);
        }
    }

    /** ScaffoldPointRotationController: yaw only rotates while the predicted block has not passed the placement block. */
    private BooleanSupplier facingGate(final int direction, final double[] placementPosition) {
        return new BooleanSupplier() {
            @Override public boolean getAsBoolean() {
                Minecraft mc = Minecraft.getMinecraft();
                EnumFacing facing = facingForDirection(direction);
                if (facing == null) return false;
                int blockX = MathHelper.floor_double(mc.thePlayer.posX - mc.thePlayer.motionX);
                int blockY = MathHelper.floor_double(mc.thePlayer.posY - mc.thePlayer.motionY);
                int blockZ = MathHelper.floor_double(mc.thePlayer.posZ - mc.thePlayer.motionZ);
                int placementX = MathHelper.floor_double(placementPosition[0]);
                int placementY = MathHelper.floor_double(placementPosition[1]);
                int placementZ = MathHelper.floor_double(placementPosition[2]);
                if (facing == EnumFacing.NORTH) return blockZ > placementZ;
                if (facing == EnumFacing.SOUTH) return blockZ < placementZ;
                if (facing == EnumFacing.WEST) return blockX > placementX;
                if (facing == EnumFacing.EAST) return blockX < placementX;
                return false;
            }
        };
    }

    void releaseRotation() {
        if (rotationController != null) {
            rotationController.setRetainAfterCompletion(false);
            rotationController.setComplete(true);
            rotationController = null;
        }
    }

    void releaseControls() {
        releaseRotation();
        ScaffoldInput.releaseAllInput();
        ScaffoldInput.restorePhysicalInput();
        setMovementClaim(false);
    }

    public static boolean isMovementClaimed() {
        Module scaffold = com.example.lunarforge.module.ModuleManager.get("scaffold");
        return scaffold instanceof ModuleScaffold && ((ModuleScaffold) scaffold).movementClaim;
    }

    void setMovementClaim(boolean value) {
        movementClaim = value;
    }

    // ===== block selection (Vape isValidBlockStack + LimitValue lists) =====

    boolean isUsableBlockStack(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        if (blacklistToggle.on() && !blacklist.doesNotMatch(stack)) return false;
        if (whitelistToggle.on() && !whitelist.matchesOrEmpty(stack)) return false;
        return true;
    }

    /** Vape canActivate: the whitelist only gates the legit edge sneak, not the blatant modes. */
    boolean canActivate() {
        return mode.is(Mode.TELLY_BRIDGE) || mode.is(Mode.GOD_BRIDGE)
                || !whitelistToggle.on() || whitelist.matchesOrEmpty(Minecraft.getMinecraft().thePlayer.getHeldItem());
    }

    boolean isPitchCheckEnabled() {
        return pitchCheck.on();
    }

    double getPitchThreshold() {
        return pitchValue.value();
    }

    int findBlockHotbarSlot() {
        Minecraft mc = Minecraft.getMinecraft();
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (isUsableBlockStack(stack)) return slot;
        }
        return -1;
    }

    int findMatchingHotbarSlot(Minecraft mc, ItemStack targetStack) {
        for (int slot = 0; slot < 9; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == targetStack.getItem()) return slot;
        }
        return -1;
    }

    void selectHotbarSlot(int slot) {
        Minecraft.getMinecraft().thePlayer.inventory.currentItem = slot;
    }

    /** Vape countBlocks: 0 = held, 1 = hotbar, 2 = main inventory + hotbar. */
    int countBlocks(int inventorySection) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean creativeMode = mc.thePlayer.capabilities.isCreativeMode;
        int blockCount = 0;
        if (inventorySection == 0) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            if (isUsableBlockStack(stack)) blockCount += creativeMode ? 64 : stack.stackSize;
            return blockCount;
        }
        if (inventorySection == 1) {
            for (int slot = 0; slot < 9; ++slot) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (isUsableBlockStack(stack)) blockCount += creativeMode ? 64 : stack.stackSize;
            }
            return blockCount;
        }
        for (int slot = 0; slot < 36; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (isUsableBlockStack(stack)) blockCount += creativeMode ? 64 : stack.stackSize;
        }
        return blockCount;
    }

    // ===== geometry (Vape Scaffold helpers) =====

    boolean isAirBlockAt(double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld.isAirBlock(new BlockPos(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)));
    }

    /** Vape getPlacementY: precise half-block positions floor to their own block. */
    double getPlacementY(Minecraft mc) {
        double playerY = mc.thePlayer.posY;
        BigDecimal preciseY = new BigDecimal(String.valueOf(playerY));
        if (Math.abs(preciseY.doubleValue() - (double) preciseY.intValue()) == 0.5D) {
            return MathHelper.floor_double(playerY);
        }
        return MathHelper.floor_double(playerY - 1.0D);
    }

    double[] offsetPosition(double[] position, int distance, int direction) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        if (direction == 1) {
            x += distance;
            z += distance;
        } else if (direction == 2) {
            x -= distance;
            z += distance;
        } else if (direction == 3) {
            x -= distance;
            z -= distance;
        } else if (direction == 4) {
            x += distance;
            z -= distance;
        } else if (direction == 6) {
            x += distance;
        } else if (direction == 8) {
            x -= distance;
        } else if (direction == 7) {
            z += distance;
        } else if (direction == 5) {
            z -= distance;
        }
        return new double[]{x, y, z};
    }

    int getCardinalDirection(Minecraft mc) {
        double yaw = normalizedYaw(mc);
        if (yaw > 315.0D || yaw <= 45.0D) return 7;
        if (yaw > 45.0D && yaw <= 135.0D) return 8;
        if (yaw > 135.0D && yaw <= 225.0D) return 5;
        if (yaw > 225.0D && yaw <= 315.0D) return 6;
        return 0;
    }

    private static double normalizedYaw(Minecraft mc) {
        double yaw = (mc.thePlayer.rotationYaw + 180.0D) % 360.0D;
        if (yaw < 0.0D) yaw += 360.0D;
        return yaw;
    }

    double angularDistance(double firstAngle, double secondAngle) {
        double directDistance = Math.abs(firstAngle - secondAngle);
        double wrappedDistance = Math.abs(360.0D - firstAngle + secondAngle);
        return directDistance <= wrappedDistance ? directDistance : wrappedDistance;
    }

    float computeRotationSpeed(Minecraft mc, float[] rotation) {
        double yaw = mc.thePlayer.rotationYaw % 360.0D;
        if (yaw < 0.0D) yaw += 360.0D;
        return (float) Math.min(2.0D + angularDistance(yaw, rotation[0]) / 8.0D, 12.0D);
    }

    float getDirectionRotationSpeed(Minecraft mc, int direction) {
        double currentYaw = mc.thePlayer.rotationYaw % 360.0D;
        if (currentYaw < 0.0D) currentYaw += 360.0D;
        double targetYaw = currentYaw;
        if (direction == 6) targetYaw = 90.0D;
        else if (direction == 8) targetYaw = 270.0D;
        else if (direction == 7) targetYaw = 0.0D;
        else if (direction == 5) targetYaw = 180.0D;
        return (float) Math.min(2.0D + angularDistance(currentYaw, targetYaw) / 8.0D, 12.0D);
    }

    boolean hasPlacementDrifted(double[] placementPosition, double[] playerPosition,
                                int direction, double activationDistance, int blocksPlaced) {
        if (direction > 4 && placementPosition[perpendicularAxisIndex(direction)] != playerPosition[perpendicularAxisIndex(direction)]) {
            return true;
        }
        if (direction < 5 && Math.abs(placementPosition[0] - playerPosition[0]) >= 4.0D
                || Math.abs(placementPosition[2] - playerPosition[2]) >= 4.0D) {
            return true;
        }
        if (placementPosition[1] != playerPosition[1]) {
            return true;
        }
        double[] activationOrigin = offsetPosition(placementPosition, -blocksPlaced, direction);
        double dx = activationOrigin[0] - playerPosition[0];
        double dz = activationOrigin[2] - playerPosition[2];
        return Math.sqrt(dx * dx + dz * dz) > activationDistance + 2.0D;
    }

    private static int perpendicularAxisIndex(int direction) {
        return direction % 2 == 0 ? 2 : 0;
    }

    /** Vape computeElevatedPlacementPoint: the aim point one block above a path position. */
    double[] computeElevatedPlacementPoint(double[] position, double lateralOffset, int direction) {
        double x = position[0];
        double y = position[1];
        double z = position[2];
        double randomOffset = 0.45D + Math.random() * 0.2D;
        if (direction == 6) {
            x = new BigDecimal(String.valueOf(x + randomOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5D - lateralOffset)).doubleValue();
        } else if (direction == 8) {
            x = new BigDecimal(String.valueOf(x + 1.0D - randomOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5D + lateralOffset)).doubleValue();
        } else if (direction == 7) {
            x = new BigDecimal(String.valueOf(x + 0.5D + lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + randomOffset)).doubleValue();
        } else if (direction == 5) {
            x = new BigDecimal(String.valueOf(x + 0.5D - lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 1.0D - randomOffset)).doubleValue();
        }
        y = new BigDecimal(String.valueOf(y + 1.0D)).doubleValue();
        return new double[]{x, y, z};
    }

    /** Vape computePlacementAimPoint: aim point on the side face of a path block. */
    double[] computePlacementAimPoint(double[] position, double lateralOffset, double verticalOffset, int direction) {
        double x = position[0];
        double y = new BigDecimal(String.valueOf(position[1] + 0.5D + verticalOffset)).doubleValue();
        double z = position[2];
        if (direction == 6) {
            x = new BigDecimal(String.valueOf(x + 1.0D)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5D - lateralOffset)).doubleValue();
        } else if (direction == 8) {
            x = new BigDecimal(String.valueOf(x)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 0.5D + lateralOffset)).doubleValue();
        } else if (direction == 7) {
            x = new BigDecimal(String.valueOf(x + 0.5D + lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z + 1.0D)).doubleValue();
        } else if (direction == 5) {
            x = new BigDecimal(String.valueOf(x + 0.5D - lateralOffset)).doubleValue();
            z = new BigDecimal(String.valueOf(z)).doubleValue();
        }
        return new double[]{x, y, z};
    }

    private EnumFacing facingForDirection(int direction) {
        switch (direction) {
            case 5: return EnumFacing.getFront(3);
            case 6: return EnumFacing.getFront(4);
            case 7: return EnumFacing.getFront(2);
            case 8: return EnumFacing.getFront(5);
        }
        return null;
    }

    // ===== Legit mode edge sneak (LegitScaffoldMode port, unchanged) =====

    /** Vape RandomValue("Sneak delay"): delay until standing after sneaking, kept loose around the setting. */
    private long rollSneakDelay() {
        int delay = sneakDelay.intValue();
        return delay <= 0L ? 0L : GameplayUtil.nextLong(Math.max(1L, delay - 50L), delay + 50L);
    }

    /**
     * Vape LegitScaffoldMode/ScaffoldEdgeSneakHelper edge detection: the
     * bounding box shrunk by 0.2 and offset by (motionX, -1, motionZ) must
     * collide with nothing, i.e. there is no floor where we are heading.
     */
    private static boolean atEdge(Minecraft mc) {
        net.minecraft.util.AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().expand(-0.2D, 0.0D, -0.2D)
                .offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty();
    }

    /** LegitScaffoldMode port: force sneak for this tick when heading off an edge, honouring the delay grace window. */
    private void edgeSneak(Minecraft mc) {
        KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
        int sneakCode = sneakKey.getKeyCode();
        boolean sneakKeyWasDown = GameplayUtil.physicalDown(sneakKey);
        boolean shouldSneak = false;
        float forwardInput = 0.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindBack)) forwardInput += 1.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindForward)) forwardInput -= 1.0F;
        boolean notMovingForward = forwardInput <= 0.0F;
        if (notMovingForward && mc.thePlayer.onGround && atEdge(mc)) shouldSneak = true;
        boolean skipTimerReset = false;
        if (!shouldSneak && sneakDelayMs > 30L
                && System.currentTimeMillis() - edgeSneakTimer < sneakDelayMs) {
            shouldSneak = true;
            skipTimerReset = true;
        }
        if (mc.thePlayer.onGround) {
            if (shouldSneak) {
                if (!mc.thePlayer.isSneaking()) sneakDelayMs = rollSneakDelay();
                KeyBinding.setKeyBindState(sneakCode, true);
                standDelayTimer = System.currentTimeMillis();
                if (!skipTimerReset) edgeSneakTimer = System.currentTimeMillis();
            } else if (!sneakKeyWasDown) {
                KeyBinding.setKeyBindState(sneakCode, false);
            }
        }
    }

    // ===== activation edge sneak (ScaffoldEdgeSneakHelper port for GodBridge/TellyBridge) =====

    void resetEdgeSneakForActivation() {
        activationSneakTimer = System.currentTimeMillis();
        activationSneakDelayMs = rollSneakDelay();
    }

    private void activationEdgeSneak(Minecraft mc) {
        if (mc.currentScreen != null) return;
        if (!canActivate()) return;
        if (mc.thePlayer.isOnLadder()) {
            releaseRotation();
            return;
        }
        if (isPitchCheckEnabled() && mc.thePlayer.rotationPitch < getPitchThreshold()) {
            releaseRotation();
            return;
        }
        KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
        activationSneakKeyWasDown = sneakKey.isKeyDown();
        boolean shouldSneak = false;
        float forwardInput = 0.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindBack)) forwardInput += 1.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindForward)) forwardInput -= 1.0F;
        boolean notMovingForward = forwardInput <= 0.0F;
        if (notMovingForward && mc.thePlayer.onGround && atEdge(mc)) {
            shouldSneak = true;
        }
        if (forwardInput > 0.0F || (!shouldSneak && System.currentTimeMillis() - activationSneakTimer >= 500L)) {
            releaseRotation();
        }
        boolean skipTimerReset = false;
        if (!shouldSneak && System.currentTimeMillis() - activationSneakTimer < activationSneakDelayMs
                && activationSneakDelayMs > 30L) {
            shouldSneak = true;
            skipTimerReset = true;
        }
        if (shouldSneak && mc.thePlayer.onGround) {
            if (!mc.thePlayer.isSneaking()) activationSneakDelayMs = rollSneakDelay();
            ScaffoldInput.setPressed(sneakKey, true);
            if (!skipTimerReset) activationSneakTimer = System.currentTimeMillis();
        } else if (!activationSneakKeyWasDown) {
            ScaffoldInput.setPressed(sneakKey, false);
        }
    }

    // ===== Normal mode placement (unchanged from the earlier port) =====

    private static boolean replaceable(Minecraft mc, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 256) return false;
        return mc.theWorld.getBlockState(pos).getBlock().getMaterial().isReplaceable();
    }

    private static boolean solidSupport(Minecraft mc, BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block.getMaterial() == net.minecraft.block.material.Material.air) return false;
        return !GameplayUtil.isInteractable(block) && block.getMaterial().isSolid();
    }

    /** Hit vector on the support block's face pointing at the target, with a small random jitter like Vape's aim points. */
    private static Vec3 hitVec(BlockPos support, EnumFacing face) {
        double x = support.getX() + 0.5D + face.getFrontOffsetX() * 0.5D;
        double y = support.getY() + 0.5D + face.getFrontOffsetY() * 0.5D;
        double z = support.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D;
        double jitter = (Math.random() - 0.5D) * 0.5D;
        if (face.getFrontOffsetX() != 0) { y += jitter; z += (Math.random() - 0.5D) * 0.5D; }
        else if (face.getFrontOffsetY() != 0) { x += jitter; z += (Math.random() - 0.5D) * 0.5D; }
        else { x += jitter; y += (Math.random() - 0.5D) * 0.5D; }
        return new Vec3(x, y, z);
    }

    private BlockPos findTarget(Minecraft mc) {
        int baseY = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1;
        BlockPos under = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), baseY, MathHelper.floor_double(mc.thePlayer.posZ));
        double dx = mc.thePlayer.motionX;
        double dz = mc.thePlayer.motionZ;
        int ox = 0;
        int oz = 0;
        if (dx * dx + dz * dz > 0.0025D) {
            ox = Math.abs(dx) > 0.03D ? (dx > 0.0D ? 1 : -1) : 0;
            oz = Math.abs(dz) > 0.03D ? (dz > 0.0D ? 1 : -1) : 0;
        } else {
            Vec3 look = GameplayUtil.lookVector(mc.thePlayer.rotationYaw, 0.0F);
            ox = Math.abs(look.xCoord) > 0.3D ? (look.xCoord > 0.0D ? 1 : -1) : 0;
            oz = Math.abs(look.zCoord) > 0.3D ? (look.zCoord > 0.0D ? 1 : -1) : 0;
        }
        BlockPos[] candidates = {under.add(ox, 0, oz), under.add(ox, 0, 0), under.add(0, 0, oz), under};
        for (BlockPos candidate : candidates) {
            if (!replaceable(mc, candidate)) continue;
            for (EnumFacing facing : SUPPORT_FACES) {
                BlockPos support = candidate.offset(facing);
                if (!solidSupport(mc, support)) continue;
                EnumFacing clickFace = facing.getOpposite();
                Vec3 hit = hitVec(support, clickFace);
                if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(hit) > 4.5D) continue;
                return candidate;   // placement details are re-derived in place()
            }
        }
        return null;
    }

    private boolean place(Minecraft mc, BlockPos target) {
        // re-derive support + face for the chosen target
        BlockPos support = null;
        EnumFacing clickFace = null;
        Vec3 hit = null;
        for (EnumFacing facing : SUPPORT_FACES) {
            BlockPos neighbour = target.offset(facing);
            if (!solidSupport(mc, neighbour)) continue;
            EnumFacing face = facing.getOpposite();
            Vec3 candidateHit = hitVec(neighbour, face);
            if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(candidateHit) > 4.5D) continue;
            support = neighbour;
            clickFace = face;
            hit = candidateHit;
            break;
        }
        if (support == null) return false;

        float aimYaw = GameplayUtil.aimYaw(mc.thePlayer.posX, mc.thePlayer.posZ, hit.xCoord, hit.zCoord);
        float aimPitch = GameplayUtil.aimPitch(mc.thePlayer.posX,
                mc.thePlayer.getPositionEyes(1.0F).yCoord, mc.thePlayer.posZ, hit.xCoord, hit.yCoord, hit.zCoord);
        if (rotations.on()) {
            float yawDiff = MathHelper.wrapAngleTo180_float(aimYaw - mc.thePlayer.rotationYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(aimPitch - mc.thePlayer.rotationPitch);
            float step = (float) Math.min(2.0F + Math.abs(yawDiff) / 8.0F, 12.0F);
            mc.thePlayer.rotationYaw += MathHelper.clamp_float(yawDiff, -step, step);
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(
                    mc.thePlayer.rotationPitch + MathHelper.clamp_float(pitchDiff, -step, step), -90.0F, 90.0F);
            if (Math.abs(yawDiff) > 5.0F || Math.abs(pitchDiff) > 5.0F) {
                if (placeWaitTicks < 8) return false;   // keep rotating; force the place after 8 ticks so we never stall
            }
        }
        placeWaitTicks = 0;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, support, clickFace, hit);
        if (placed) {
            if (RIGHT_CLICK_DELAY != null) Fields.setInt(RIGHT_CLICK_DELAY, mc, 4);
            mc.thePlayer.swingItem();
        }
        return placed;
    }
}
