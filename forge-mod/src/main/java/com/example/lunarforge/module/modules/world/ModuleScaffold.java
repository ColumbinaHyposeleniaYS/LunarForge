package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.modules.legit.ModuleStuck;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import com.example.lunarforge.util.MoveMath;
import com.example.lunarforge.util.PlayerInputHook;
import com.example.lunarforge.util.RotationSpoof;
import com.example.lunarforge.util.Rotations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Ported from Leader-Lite (leader.module.modules.player.Scaffold), replacing
 * the earlier Vape-based port. Four modes (Normal / Telly / Legit /
 * LegitTelly), seven rotation modes (None / Vanilla / Backwards / Prediction
 * / Strict / GodBridge / Snap), edge-limit Legit sneaking, LegitTelly phase
 * bridging, Snap mode with cell solving, per-mode rotation profiles,
 * right-click Telly override, block trail rendering, counter/BPS HUD and a
 * clutch that hands over to the Stuck module.
 *
 * Framework adaptations (Leader has mixins/events LunarForge lacks):
 * - Silent rotations: Leader swaps the client rotation right before the C03
 *   send. Here the module claims a rotation in {@link RotationSpoof} and the
 *   PacketHooks send injection rewrites the outgoing rotation packets, plus
 *   one explicit C05 per tick (see RotationSpoof javadoc).
 * - MoveInputEvent becomes the {@link PlayerInputHook} wrapper (same timing:
 *   after MovementInput.updatePlayerMoveState, before movement applies).
 * - StrafeEvent is covered by the same wrapper; LivingUpdateEvent maps to
 *   Forge LivingEvent.LivingUpdateEvent.
 * - The placement gates on Leader's BedNuker/LongJump are dropped (no such
 *   modules here), CancelSwap is replaced by restoring the hotbar slot on
 *   disable, and HitBlock/click cancellation is replaced by unpressing the
 *   attack/use bindings on press (1.8.9 Forge mouse events are not
 *   cancelable).
 * - The counter/BPS cards render as plain text+bars (no font/shader/HUD
 *   element manager), the low-blocks notification is a chat message, the
 *   rotation profiles live in memory per session, and Leader's Item Spoof
 *   (client render mixins) is not ported.
 */
public final class ModuleScaffold extends Module {

    public enum ScaffoldMode implements ChoiceSetting.Option {
        NORMAL("Normal"), TELLY("Telly"), LEGIT("Legit"), LEGIT_TELLY("LegitTelly");

        private final String label;

        ScaffoldMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum RotateMode implements ChoiceSetting.Option {
        NONE("None"), VANILLA("Vanilla"), BACKWARDS("Backwards"), PREDICTION("Prediction"),
        STRICT("Strict"), GOD_BRIDGE("GodBridge"), SNAP("Snap");

        private final String label;

        RotateMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum MoveFixMode implements ChoiceSetting.Option {
        NONE("None"), SILENT("Silent");

        private final String label;

        MoveFixMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
            0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
            0.78125, 0.84375, 0.90625, 0.96875
    };
    private static final double FACE_DEPTH = 0.001;
    private static final double[] SNAP_OFFSETS = new double[]{0.5, 0.35, 0.65, 0.2, 0.8, 0.05, 0.95};

    // ===== settings =====
    private final ChoiceSetting<ScaffoldMode> mode = choice("mode", ScaffoldMode.TELLY);
    private final ChoiceSetting<RotateMode> rotationMode = choice("rotationMode", RotateMode.PREDICTION);
    private final BoolSetting noUpdateWhenCanPlace = bool("noUpdateWhenCanPlace", false).label(() -> "Do Not Update Rotation When Can Place");
    private final BoolSetting edgeLimit = bool("edgeLimit", false).label(() -> "Edge Limit");
    private final NumberSetting godBridgeTolerance = decimal("godBridgeTolerance", 5.0F, 0.0F, 10.0F).label(() -> "GodBridge Yaw Tolerance");
    private final ChoiceSetting<MoveFixMode> moveFix = choice("moveFix", MoveFixMode.SILENT);
    private final NumberSetting jumpDelay = integer("jumpDelay", 2, 0, 5).label(() -> "Jump Delay");
    private final NumberSetting placeDelay = integer("placeDelay", 1, 0, 5).label(() -> "Place Delay");
    private final NumberSetting startRotSpeed = decimal("startRotSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Start Rotate Speed");
    private final NumberSetting normalRotSpeed = decimal("normalRotSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Normal Rotate Speed");
    private final NumberSetting normalModeSpeed = decimal("normalModeSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Normal Mode Speed");
    private final NumberSetting legitModeSpeed = decimal("legitModeSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Legit Mode Speed");
    private final BoolSetting swing = bool("swing", true).label(() -> "Swing");
    private final BoolSetting clutch = bool("clutch", true).label(() -> "Clutch");
    private final BoolSetting onlyInVoid = bool("onlyInVoid", false).label(() -> "Only Void");
    private final BoolSetting bPSRender = bool("bPSRender", true).label(() -> "Render BPS");
    private final BoolSetting blockCounter = bool("blockCounter", false).label(() -> "Block Counter");
    private final BoolSetting airRescue = bool("airRescue", true).label(() -> "Air Rescue");
    private final BoolSetting strictRaytrace = bool("strictRaytrace", false).label(() -> "Strict Raytrace");
    private final BoolSetting rightClickToSwitchTelly = bool("rightClickToSwitchTelly", false).label(() -> "Right Click To Switch Telly");
    private final BoolSetting warningLowBlocks = bool("warningLowBlocks", false).label(() -> "Warning Low Blocks");
    private final NumberSetting lowBlocksThreshold = integer("lowBlocksThreshold", 16, 0, 64).label(() -> "Low Blocks Threshold");
    private final NumberSetting edgeThreshold = decimal("edgeThreshold", 0.15F, 0.01F, 0.5F).label(() -> "Edge Threshold");
    private final NumberSetting snapForwardSpeed = decimal("snapForwardSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Forward Speed");
    private final NumberSetting snapBackSpeed = decimal("snapBackSpeed", 180.0F, 1.0F, 180.0F).label(() -> "Back Speed");
    private final BoolSetting earlySnap = bool("earlySnap", true).label(() -> "Early Snap");
    private final NumberSetting snapForwardPitch = decimal("snapForwardPitch", 80.0F, 0.0F, 90.0F).label(() -> "Forward Pitch");
    private final NumberSetting snapHoldTicks = integer("snapHoldTicks", 1, 0, 5).label(() -> "Snap Hold Ticks");
    private final BoolSetting delayPlacement = bool("delayPlacement", false).label(() -> "Delay Placement");
    private final BoolSetting speedLimit = bool("speedLimit", false).label(() -> "Speed Limit");
    private final NumberSetting speedLimitTicks = integer("speedLimitTicks", 3, 0, 5).label(() -> "Speed Limit Ticks");
    private final NumberSetting forwardRotationTicks = integer("forwardRotationTicks", 1, 1, 5).label(() -> "Forward Rotation Ticks");
    private final NumberSetting legitSneakDelay = integer("legitSneakDelay", 4, 1, 5).label(() -> "Legit Sneak Delay");
    private final NumberSetting forwardSpeed = decimal("forwardSpeed", 180.0F, 1.0F, 180.0F).label(() -> "ForwardSpeed");
    private final NumberSetting backSpeed = decimal("backSpeed", 180.0F, 1.0F, 180.0F).label(() -> "BackSpeed");
    private final NumberSetting placeSpeed = decimal("placeSpeed", 180.0F, 1.0F, 180.0F).label(() -> "PlaceSpeed");
    private final NumberSetting tellyTicks = integer("tellyTicks", 3, 1, 6).label(() -> "TellyTicks");

    // ===== state =====
    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int tellyJumpDelayTimer = 0;
    private int jumpDelayOverride = -1;
    private boolean wasInAir = false;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private boolean clutchActive = false;
    private boolean clutchOwnsStuck = false;
    private EnumFacing targetFacing = null;
    private int placeDelayCounter = 0;
    private double prevBpsX;
    private double prevBpsZ;
    private float currentBps;
    private int counterMax = 0;
    private int hudCount = 0;
    private float animBps = 0.0F;
    private float animPercent = 0.0F;
    private long lastHudFrame = 0L;
    private int airTicks = 0;
    private boolean pendingSpeedLimitRot = false;
    private int forwardRotateTicksLeft = 0;
    private int legitEdgeState = 0;
    private int legitEdgeTimer = 0;
    private boolean legitWasOnEdge = false;
    private int legitTellyPhase = 0;
    private int legitTellyPhaseTicks = 0;
    private boolean legitTellyWasAirborne = false;
    private boolean legitTellyPlacedFirstBlock = false;
    private float legitTellySilentYaw;
    private float legitTellySilentPitch;
    private BlockData legitTellyLockedBlockData;
    private boolean rightClickTellyActive = false;
    private int rightClickSavedMode = -1;
    private int rightClickSavedRotationMode = -1;
    private boolean lowBlocksWarned = false;
    private boolean rightClickBlockedUntilRelease;
    private static boolean movementClaimed;
    private int profileMode;
    private int lastSeenMode;
    private boolean changingTellyOverride;
    private float godBridgeDiag = Float.NaN;
    private int snapHoldCounter = 0;
    private float snapLastYaw = Float.NaN;
    private float snapLastPitch = 0.0F;
    private int snapDelayCounter = 0;
    private SnapTarget snapPendingTarget = null;
    private final List<PlacedBlock> placedTrail = new ArrayList<PlacedBlock>();
    private final Map<Integer, RotationProfile> rotationProfiles = new HashMap<Integer, RotationProfile>();

    private final PlayerInputHook.Modifier inputModifier = new PlayerInputHook.Modifier() {
        @Override public void modify(MovementInput input) {
            applyMoveInput(input);
        }
    };

    public ModuleScaffold() {
        super("SCAFFOLD", false);
        this.profileMode = mode.get().ordinal();
        this.lastSeenMode = this.profileMode;
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, rotationMode, moveFix, placeDelay, swing, clutch);
            s.add(noUpdateWhenCanPlace, edgeLimit, godBridgeTolerance).hideIf(() -> !rotationMode.is(RotateMode.GOD_BRIDGE));
            s.add(jumpDelay).hideIf(() -> !mode.is(ScaffoldMode.TELLY) && !mode.is(ScaffoldMode.LEGIT_TELLY));
            s.add(startRotSpeed, normalRotSpeed).hideIf(() -> !mode.is(ScaffoldMode.TELLY));
            s.add(normalModeSpeed).hideIf(() -> !mode.is(ScaffoldMode.NORMAL));
            s.add(legitModeSpeed).hideIf(() -> !mode.is(ScaffoldMode.LEGIT));
            s.add(speedLimit).hideIf(() -> !mode.is(ScaffoldMode.TELLY));
            s.add(speedLimitTicks, forwardRotationTicks).hideIf(() -> !mode.is(ScaffoldMode.TELLY) || !speedLimit.on());
            s.add(legitSneakDelay).hideIf(() -> !mode.is(ScaffoldMode.LEGIT));
            s.add(forwardSpeed, backSpeed, placeSpeed, tellyTicks).hideIf(() -> !mode.is(ScaffoldMode.LEGIT_TELLY));
            s.add(onlyInVoid).hideIf(() -> !clutch.on());
            s.add(edgeThreshold, snapForwardSpeed, snapBackSpeed, earlySnap, snapForwardPitch, snapHoldTicks, delayPlacement)
                    .hideIf(() -> !rotationMode.is(RotateMode.SNAP));
            s.add(airRescue, strictRaytrace, rightClickToSwitchTelly, bPSRender, blockCounter, warningLowBlocks);
            s.add(lowBlocksThreshold).hideIf(() -> !warningLowBlocks.on());
        });
    }

    // ===== rotation profiles (Leader verifyValue / per-mode persistence, in-memory) =====

    private void handleModeChange() {
        int selected = mode.get().ordinal();
        if (selected == this.lastSeenMode) return;
        this.lastSeenMode = selected;
        if (this.changingTellyOverride) return;
        if (this.rightClickTellyActive) {
            // A manual mode change while the override is active takes precedence.
            this.rotationProfiles.put(1, captureRotationProfile());
            this.rightClickTellyActive = false;
            this.rightClickSavedMode = -1;
            this.rightClickSavedRotationMode = -1;
            this.rightClickBlockedUntilRelease = Mouse.isButtonDown(1);
            this.profileMode = selected;
            applyRotationProfile(selected);
            resetSwitchRotation();
            return;
        }
        if (selected != this.profileMode) {
            this.rotationProfiles.put(this.profileMode, captureRotationProfile());
            this.profileMode = selected;
            applyRotationProfile(selected);
            resetSwitchRotation();
        }
    }

    private RotationProfile captureRotationProfile() {
        RotationProfile profile = new RotationProfile();
        profile.rotationMode = rotationMode.get();
        profile.noUpdateWhenCanPlace = noUpdateWhenCanPlace.get();
        profile.edgeLimit = edgeLimit.get();
        profile.godBridgeTolerance = godBridgeTolerance.value();
        profile.moveFix = moveFix.get();
        profile.startRotSpeed = startRotSpeed.value();
        profile.normalRotSpeed = normalRotSpeed.value();
        profile.normalModeSpeed = normalModeSpeed.value();
        profile.legitModeSpeed = legitModeSpeed.value();
        profile.strictRaytrace = strictRaytrace.get();
        profile.airRescue = airRescue.get();
        profile.edgeThreshold = edgeThreshold.value();
        profile.snapForwardSpeed = snapForwardSpeed.value();
        profile.snapBackSpeed = snapBackSpeed.value();
        profile.earlySnap = earlySnap.get();
        profile.snapForwardPitch = snapForwardPitch.value();
        profile.snapHoldTicks = snapHoldTicks.intValue();
        profile.delayPlacement = delayPlacement.get();
        profile.forwardSpeed = forwardSpeed.value();
        profile.backSpeed = backSpeed.value();
        profile.placeSpeed = placeSpeed.value();
        return profile;
    }

    private void applyRotationProfile(int selectedMode) {
        RotationProfile profile = this.rotationProfiles.get(selectedMode);
        if (profile == null) return;
        rotationMode.set(profile.rotationMode);
        noUpdateWhenCanPlace.set(profile.noUpdateWhenCanPlace);
        edgeLimit.set(profile.edgeLimit);
        godBridgeTolerance.set(profile.godBridgeTolerance);
        moveFix.set(profile.moveFix);
        startRotSpeed.set(profile.startRotSpeed);
        normalRotSpeed.set(profile.normalRotSpeed);
        normalModeSpeed.set(profile.normalModeSpeed);
        legitModeSpeed.set(profile.legitModeSpeed);
        strictRaytrace.set(profile.strictRaytrace);
        airRescue.set(profile.airRescue);
        edgeThreshold.set(profile.edgeThreshold);
        snapForwardSpeed.set(profile.snapForwardSpeed);
        snapBackSpeed.set(profile.snapBackSpeed);
        earlySnap.set(profile.earlySnap);
        snapForwardPitch.set(profile.snapForwardPitch);
        snapHoldTicks.set((float) profile.snapHoldTicks);
        delayPlacement.set(profile.delayPlacement);
        forwardSpeed.set(profile.forwardSpeed);
        backSpeed.set(profile.backSpeed);
        placeSpeed.set(profile.placeSpeed);
    }

    // ===== right click telly override =====

    private void updateRightClickTelly() {
        // Raw input is intentional: the bindings may be unpressed, the physical button still speaks.
        boolean down = Mouse.isButtonDown(1);
        if (!down) this.rightClickBlockedUntilRelease = false;
        boolean allowed = isEnabled() && rightClickToSwitchTelly.on() && mc.thePlayer != null
                && mc.theWorld != null && !mc.thePlayer.isDead && mc.currentScreen == null
                && mc.inGameHasFocus && Display.isActive();
        if (!allowed) {
            if (down) this.rightClickBlockedUntilRelease = true;
            restoreRightClickTelly();
            return;
        }
        if (down && !this.rightClickBlockedUntilRelease && !this.rightClickTellyActive && mode.is(ScaffoldMode.NORMAL)) {
            this.rightClickSavedMode = mode.get().ordinal();
            this.rightClickSavedRotationMode = rotationMode.get().ordinal();
            this.rotationProfiles.put(this.rightClickSavedMode, captureRotationProfile());
            this.rightClickTellyActive = true;
            this.changingTellyOverride = true;
            try { mode.set(ScaffoldMode.TELLY); } finally { this.changingTellyOverride = false; }
            this.lastSeenMode = mode.get().ordinal();
            applyRotationProfile(1);
            resetSwitchRotation();
        } else if (!down && this.rightClickTellyActive) {
            restoreRightClickTelly();
        }
    }

    private void resetSwitchRotation() {
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.stage = 0;
        this.rotationTick = 1;
        this.godBridgeDiag = Float.NaN;
        this.pendingSpeedLimitRot = false;
        this.forwardRotateTicksLeft = 0;
        this.tellyJumpDelayTimer = 0;
    }

    private void restoreRightClickTelly() {
        if (!this.rightClickTellyActive) return;
        if (mode.is(ScaffoldMode.TELLY)) {
            this.rotationProfiles.put(1, captureRotationProfile());
            this.changingTellyOverride = true;
            try {
                if (this.rightClickSavedMode >= 0) mode.set(ScaffoldMode.values()[this.rightClickSavedMode]);
            } finally {
                this.changingTellyOverride = false;
            }
            this.lastSeenMode = mode.get().ordinal();
            applyRotationProfile(this.rightClickSavedMode);
            if (!this.rotationProfiles.containsKey(this.rightClickSavedMode) && this.rightClickSavedRotationMode >= 0) {
                rotationMode.set(RotateMode.values()[this.rightClickSavedRotationMode]);
            }
        }
        this.rightClickTellyActive = false;
        this.profileMode = mode.get().ordinal();
        this.rightClickSavedMode = -1;
        this.rightClickSavedRotationMode = -1;
        resetSwitchRotation();
    }

    // ===== events =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        RotationSpoof.newTick();
        if (isEnabled()) PlayerInputHook.ensureAttached(this.inputModifier, null);
        updateRightClickTelly();
        handleModeChange();
        if (isEnabled() && (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead)) {
            clutchReset();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player != mc.thePlayer || !isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) return;
        updateRightClickTelly();
        updateLowBlockWarning();
        boolean tellyMode = mode.is(ScaffoldMode.TELLY);
        boolean legitTellyMode = isLegitTellyMode();
        boolean tellyLikeMode = tellyMode || legitTellyMode;
        boolean legitMode = mode.is(ScaffoldMode.LEGIT);

        if (this.rotationTick > 0) this.rotationTick--;
        if (this.forwardRotateTicksLeft > 0) this.forwardRotateTicksLeft--;
        if (mc.thePlayer.onGround) {
            if (this.stage > 0) this.stage--;
            if (this.stage < 0) this.stage++;
            this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
            this.shouldKeepY = false;
            this.towering = false;
            if (this.wasInAir) {
                this.tellyJumpDelayTimer = tellyLikeMode
                        ? (this.jumpDelayOverride >= 0 ? this.jumpDelayOverride : this.jumpDelay.intValue()) : 0;
                this.wasInAir = false;
            }
            if (this.tellyJumpDelayTimer > 0) this.tellyJumpDelayTimer--;
            if (speedLimit.on()) {
                this.pendingSpeedLimitRot = false;
                this.airTicks = 0;
            }
        } else {
            if (speedLimit.on()) this.airTicks++;
            this.wasInAir = true;
        }
        if (tellyLikeMode && mc.thePlayer.onGround && MoveMath.isForwardPressed()
                && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) {
            this.stage = 1;
        }
        if (tellyLikeMode) {
            this.jumpDelayOverride = mc.gameSettings.keyBindJump.isKeyDown() ? 2 : -1;
        } else {
            this.jumpDelayOverride = -1;
            this.tellyJumpDelayTimer = 0;
        }
        this.updateClutch();
        if (this.clutchActive) {
            Module stuckModule = ModuleManager.get("stuck");
            if (stuckModule instanceof ModuleStuck) {
                ModuleStuck stuck = (ModuleStuck) stuckModule;
                if (!stuck.isStuckActive()) {
                    stuck.setEnabled(true);
                    this.clutchOwnsStuck = stuck.isStuckActive();
                }
            }
        }

        if (legitTellyMode) {
            this.selectScaffoldBlock();
            this.updateLegitTelly();
            return;
        }

        if (legitMode) {
            boolean onGround = mc.thePlayer.onGround;
            boolean atEdge = onGround && this.isLegitOnEdge();
            boolean holdingBlock = Rotations.isHoldingPlaceableBlock();
            boolean justReachedEdge = atEdge && !this.legitWasOnEdge;
            if (!onGround) {
                this.legitEdgeState = 0;
                this.legitEdgeTimer = 0;
            } else if (atEdge && holdingBlock) {
                switch (this.legitEdgeState) {
                    case 0:
                        if (justReachedEdge || this.legitEdgeTimer == 0) {
                            this.legitEdgeState = 1;
                            this.legitEdgeTimer = this.legitSneakDelay.intValue();
                        }
                        break;
                    case 1:
                        this.legitEdgeTimer--;
                        if (this.legitEdgeTimer <= 0) {
                            this.legitEdgeState = 2;
                            this.legitEdgeTimer = 0;
                        }
                        break;
                    case 2:
                        break;
                    default:
                        this.legitEdgeState = 0;
                        this.legitEdgeTimer = 0;
                        break;
                }
            } else {
                this.legitEdgeState = 0;
                this.legitEdgeTimer = 0;
            }
            this.legitWasOnEdge = atEdge;
        }

        if (this.canPlace()) {
            ItemStack stack = mc.thePlayer.getHeldItem();
            int count = Rotations.isPlaceableBlock(stack) ? stack.stackSize : 0;
            this.blockCount = Math.min(this.blockCount, count);
            if (this.blockCount <= 0) {
                int slot = mc.thePlayer.inventory.currentItem;
                if (this.blockCount == 0) slot--;
                for (int i = slot; i > slot - 9; i--) {
                    int hotbarSlot = (i % 9 + 9) % 9;
                    ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                    if (Rotations.isPlaceableBlock(candidate)) {
                        mc.thePlayer.inventory.currentItem = hotbarSlot;
                        this.blockCount = candidate.stackSize;
                        break;
                    }
                }
            }

            if (rotationMode.is(RotateMode.SNAP)) {
                boolean tellyGround = tellyLikeMode && mc.thePlayer.onGround && this.stage > 0;
                boolean legitBlocked = legitMode && mc.thePlayer.onGround && this.legitEdgeState == 1;
                this.updateSnap(!tellyGround && !legitBlocked);
                return;
            }

            float currentYaw = this.getCurrentYaw();
            float yawDiffTo180 = Rotations.wrapAngleDiff(currentYaw - 180.0F, RotationSpoof.lastReportedYaw());
            float diagonalYaw = this.isDiagonal(currentYaw) ? yawDiffTo180
                    : Rotations.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F),
                            RotationSpoof.lastReportedYaw());

            if (!this.canRotate) {
                switch (this.rotationMode.get()) {
                    case VANILLA:
                        this.yaw = Rotations.quantizeAngle(diagonalYaw);
                        break;
                    case BACKWARDS:
                        if (this.yaw == -180.0F && this.pitch == 0.0F) {
                            this.yaw = Rotations.quantizeAngle(yawDiffTo180);
                            this.pitch = Rotations.quantizeAngle(85.0F);
                        } else {
                            this.yaw = Rotations.quantizeAngle(yawDiffTo180);
                        }
                        break;
                    case PREDICTION:
                        if (this.yaw == -180.0F && this.pitch == 0.0F) {
                            this.yaw = Rotations.quantizeAngle(diagonalYaw);
                            this.pitch = Rotations.quantizeAngle(85.0F);
                        }
                        break;
                    case GOD_BRIDGE:
                        if (this.yaw == -180.0F && this.pitch == 0.0F) {
                            this.yaw = Rotations.quantizeAngle(
                                    Rotations.wrapAngleDiff(this.quantizeDiagonal(currentYaw + 180.0F), RotationSpoof.lastReportedYaw()));
                            this.pitch = Rotations.quantizeAngle(85.0F);
                        }
                        break;
                    default:
                        break;
                }
            }

            BlockData blockData = this.getBlockData();
            Vec3 hitVec = null;

            if (blockData != null) {
                if (rotationMode.is(RotateMode.STRICT)) {
                    double centerX = blockData.blockPos().getX() + 0.5 + blockData.facing().getDirectionVec().getX() * 0.5;
                    double centerY = blockData.blockPos().getY() + 0.5 + blockData.facing().getDirectionVec().getY() * 0.5;
                    double centerZ = blockData.blockPos().getZ() + 0.5 + blockData.facing().getDirectionVec().getZ() * 0.5;
                    float[] strictRot = Rotations.getRotations(centerX, centerY, centerZ);
                    MovingObjectPosition strictMop = Rotations.rayTrace(strictRot[0], strictRot[1], mc.playerController.getBlockReachDistance());
                    if (this.isValidHit(strictMop, blockData.blockPos(), blockData.facing())) {
                        this.yaw = Rotations.wrapAngleDiff(strictRot[0], RotationSpoof.lastReportedYaw());
                        this.pitch = strictRot[1];
                        this.canRotate = true;
                        hitVec = strictMop.hitVec;
                    }
                } else if (rotationMode.is(RotateMode.GOD_BRIDGE)) {
                    float moveBack = this.getCurrentYaw() + 180.0F;
                    if (Float.isNaN(this.godBridgeDiag)
                            || Math.abs(MathHelper.wrapAngleTo180_float(moveBack - this.godBridgeDiag)) > 60.0F) {
                        this.godBridgeDiag = this.quantizeDiagonal(moveBack);
                    }
                    float diagYaw = this.godBridgeDiag;
                    float tolerance = this.godBridgeTolerance.value();
                    List<BlockData> options = this.getPlaceOptions(blockData);

                    float lastOff = MathHelper.wrapAngleTo180_float(this.yaw - diagYaw);
                    if (Math.abs(lastOff) > tolerance) {
                        lastOff = (float) ((Math.random() * 2.0D - 1.0D) * tolerance * 0.8D);
                    }
                    float realOff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - diagYaw);
                    List<Float> yawCandidates = new ArrayList<Float>();
                    if (Math.abs(realOff) <= tolerance) yawCandidates.add(diagYaw + realOff);
                    yawCandidates.add(diagYaw + lastOff);
                    for (float step = 1.0F; step <= tolerance * 2.0F; step += 1.0F) {
                        float up = lastOff + step;
                        float down = lastOff - step;
                        if (Math.abs(up) <= tolerance) yawCandidates.add(diagYaw + up);
                        if (Math.abs(down) <= tolerance) yawCandidates.add(diagYaw + down);
                    }

                    float bestPitch = Float.NaN;
                    float bestYaw = diagYaw;
                    Vec3 bestHitVec = null;
                    BlockData bestOption = blockData;
                    double reach = mc.playerController.getBlockReachDistance();
                    for (float candidateYaw : yawCandidates) {
                        double bestScore = Double.MAX_VALUE;
                        for (BlockData option : options) {
                            float centerPitch = this.faceCenterPitch(option);
                            float penalty = option == blockData ? 0.0F : 2.0F;
                            for (float p = centerPitch + 30.0F; p >= centerPitch - 30.0F; p -= 0.5F) {
                                if (p > 89.0F || p < -89.0F) continue;
                                MovingObjectPosition mop = Rotations.rayTrace(candidateYaw, p, reach);
                                if (this.isValidHit(mop, option.blockPos(), option.facing())) {
                                    double score = Math.abs(p - centerPitch) + penalty;
                                    if (score < bestScore) {
                                        bestScore = score;
                                        bestPitch = p;
                                        bestHitVec = mop.hitVec;
                                        bestYaw = candidateYaw;
                                        bestOption = option;
                                    }
                                }
                            }
                        }
                        if (bestHitVec != null) break;
                    }
                    if (bestHitVec != null) {
                        blockData = bestOption;
                        hitVec = bestHitVec;
                        this.canRotate = true;
                        boolean updateRotation = true;
                        float keepYaw = RotationSpoof.lastReportedYaw();
                        float keepPitch = RotationSpoof.lastReportedPitch();
                        MovingObjectPosition keepMop = Rotations.rayTrace(keepYaw, keepPitch, reach);
                        boolean keepHits = this.isValidHit(keepMop, blockData.blockPos(), blockData.facing());
                        if (this.noUpdateWhenCanPlace.on() && keepHits) updateRotation = false;
                        if (this.edgeLimit.on() && !this.isGodBridgeOnEdge()) updateRotation = false;
                        if (updateRotation) {
                            this.yaw = Rotations.wrapAngleDiff(bestYaw, RotationSpoof.lastReportedYaw());
                            this.pitch = bestPitch;
                        } else {
                            this.yaw = keepYaw;
                            this.pitch = keepPitch;
                            hitVec = keepHits ? keepMop.hitVec : null;
                        }
                    } else if (this.airRescue.on() && (!mc.thePlayer.onGround || this.isGodBridgeOnEdge())) {
                        for (BlockData option : options) {
                            Vec3 rescue = this.applyRescueRotation(option);
                            if (rescue != null) {
                                blockData = option;
                                hitVec = rescue;
                                break;
                            }
                        }
                    }
                } else if (rotationMode.is(RotateMode.PREDICTION)) {
                    double[] offsets = {0.1, 0.3, 0.5, 0.7, 0.9};
                    double[] x = offsets, y = offsets, z = offsets;
                    switch (blockData.facing()) {
                        case NORTH: z = new double[]{0.02}; break;
                        case EAST: x = new double[]{0.98}; break;
                        case SOUTH: z = new double[]{0.98}; break;
                        case WEST: x = new double[]{0.02}; break;
                        case DOWN: y = new double[]{0.02}; break;
                        case UP: y = new double[]{0.98}; break;
                        default: break;
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    double bestDist = Double.MAX_VALUE;
                    Vec3 bestHitVec = null;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double targetX = blockData.blockPos().getX() + dx;
                                double targetY = blockData.blockPos().getY() + dy;
                                double targetZ = blockData.blockPos().getZ() + dz;
                                float[] rot = Rotations.getRotations(targetX, targetY, targetZ);
                                MovingObjectPosition mop = Rotations.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance());
                                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                    float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                    float pitchDiff = Math.abs(rot[1] - this.pitch);
                                    double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                    if (dist < bestDist) {
                                        bestDist = dist;
                                        bestYaw = rot[0];
                                        bestPitch = rot[1];
                                        bestHitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        bestYaw += Rotations.nextFloat(-0.5F, 0.5F);
                        bestPitch += Rotations.nextFloat(-0.3F, 0.3F);
                        this.yaw = Rotations.wrapAngleDiff(bestYaw, RotationSpoof.lastReportedYaw());
                        this.pitch = bestPitch;
                        this.canRotate = true;
                        hitVec = bestHitVec;
                    }
                } else {
                    double[] x = placeOffsets, y = placeOffsets, z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH: z = new double[]{0.0}; break;
                        case EAST: x = new double[]{1.0}; break;
                        case SOUTH: z = new double[]{1.0}; break;
                        case WEST: x = new double[]{0.0}; break;
                        case DOWN: y = new double[]{0.0}; break;
                        case UP: y = new double[]{1.0}; break;
                        default: break;
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = Rotations.wrapAngleDiff(this.yaw, RotationSpoof.lastReportedYaw());
                                float[] rotations = Rotations.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = Rotations.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance());
                                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        this.yaw = bestYaw;
                        this.pitch = bestPitch;
                        this.canRotate = true;
                    }
                }
            }

            if (blockData != null && hitVec == null && !mc.thePlayer.onGround && this.airRescue.on()) {
                hitVec = this.applyRescueRotation(blockData);
            }

            if (this.canRotate && MoveMath.isForwardPressed()
                    && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                if (rotationMode.is(RotateMode.BACKWARDS)) this.yaw = Rotations.quantizeAngle(yawDiffTo180);
            }

            if (!legitMode && !rotationMode.is(RotateMode.NONE)) {
                float targetYaw = this.yaw;
                float targetPitch = this.pitch;
                if (!tellyMode) {
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - RotationSpoof.lastReportedYaw());
                    float tolerance = this.normalModeSpeed.value();
                    if (Math.abs(yawDiff) > tolerance) {
                        targetYaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + Rotations.clampAngle(yawDiff, tolerance));
                        this.rotationTick = Math.max(this.rotationTick, 1);
                    }
                } else {
                    if (speedLimit.on() && this.forwardRotateTicksLeft > 0) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - RotationSpoof.lastReportedYaw());
                        this.yaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + yawDelta * Rotations.nextFloat(0.98F, 0.99F));
                        this.pitch = Rotations.quantizeAngle(Rotations.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 0;
                    } else if (this.towering && (mc.thePlayer.motionY > 0.0D || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - RotationSpoof.lastReportedYaw());
                        float tolerance = this.rotationTick >= 2 ? this.startRotSpeed.value() : this.normalRotSpeed.value();
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = Rotations.clampAngle(yawDiff, tolerance);
                            targetYaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (this.isTowering() && this.tellyJumpDelayTimer <= 0 && this.forwardRotateTicksLeft <= 0) {
                        if (!speedLimit.on()) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - RotationSpoof.lastReportedYaw());
                            targetYaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + yawDelta * Rotations.nextFloat(0.98F, 0.99F));
                            targetPitch = Rotations.quantizeAngle(Rotations.nextFloat(30.0F, 80.0F));
                            this.rotationTick = 3;
                            this.towering = true;
                        } else {
                            this.pendingSpeedLimitRot = true;
                            this.airTicks = 0;
                        }
                    } else if (this.tellyJumpDelayTimer > 0) {
                        targetYaw = this.yaw != -180.0F ? this.yaw
                                : Rotations.quantizeAngle(MathHelper.wrapAngleTo180_float(
                                        mc.thePlayer.rotationYaw - RotationSpoof.lastReportedYaw()) + RotationSpoof.lastReportedYaw());
                        targetPitch = Math.abs(this.pitch) > 10.0F ? this.pitch : 60.0F;
                    }
                    if (speedLimit.on() && this.pendingSpeedLimitRot && !mc.thePlayer.onGround
                            && this.airTicks >= this.speedLimitTicks.intValue()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - RotationSpoof.lastReportedYaw());
                        this.yaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + yawDelta * Rotations.nextFloat(0.98F, 0.99F));
                        this.pitch = Rotations.quantizeAngle(Rotations.nextFloat(30.0F, 80.0F));
                        this.forwardRotateTicksLeft = this.forwardRotationTicks.intValue();
                        this.rotationTick = 0;
                        this.towering = true;
                        this.pendingSpeedLimitRot = false;
                        this.airTicks = 0;
                    }
                }
                RotationSpoof.claim(targetYaw, targetPitch, 3);
                if (this.moveFix.get() == MoveFixMode.SILENT) RotationSpoof.setSmoothedYaw(targetYaw);
            } else if (legitMode && !rotationMode.is(RotateMode.NONE) && this.canRotate) {
                float targetYaw = this.yaw;
                float targetPitch = this.pitch;
                float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - RotationSpoof.lastReportedYaw());
                float tolerance = this.legitModeSpeed.value();
                if (Math.abs(yawDiff) > tolerance) {
                    float clampedYaw = Rotations.clampAngle(yawDiff, tolerance);
                    targetYaw = Rotations.quantizeAngle(RotationSpoof.lastReportedYaw() + clampedYaw);
                    this.rotationTick = Math.max(this.rotationTick, 1);
                }
                RotationSpoof.claim(targetYaw, targetPitch, 3);
                if (this.moveFix.get() == MoveFixMode.SILENT) RotationSpoof.setSmoothedYaw(targetYaw);
            }

            boolean legitCanPlace = !legitMode || !mc.thePlayer.onGround || this.legitEdgeState == 0 || this.legitEdgeState == 2;

            if (blockData != null && hitVec != null && this.rotationTick <= 0 && legitCanPlace) {
                if (this.placeDelayCounter > 0) {
                    this.placeDelayCounter--;
                } else {
                    MovingObjectPosition finalCheck = Rotations.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance());
                    if (finalCheck != null && finalCheck.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && finalCheck.getBlockPos().equals(blockData.blockPos()) && finalCheck.sideHit == blockData.facing()) {
                        this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec);
                        this.placeDelayCounter = this.placeDelay.intValue();
                    } else if (this.canRotate) {
                        this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        this.placeDelayCounter = this.placeDelay.intValue();
                    }
                }
            }

            if (this.targetFacing != null && this.rotationTick <= 0) {
                int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                Vec3 belowHit = Rotations.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                this.place(belowPlayer, this.targetFacing, belowHit);
                this.targetFacing = null;
            }
        }
    }

    /** One explicit rotation packet per tick so silent angles never go stale (see RotationSpoof). */
    @SubscribeEvent
    public void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player != mc.thePlayer) return;
        this.movementClaimed = isEnabled() && RotationSpoof.hasClaim();
        if (!isEnabled()) return;
        if (RotationSpoof.hasClaim()) RotationSpoof.sendRotationPacket();
    }

    /** HitSelect/AimAssist suppression hook: the scaffold is actively bridging/rotating. */
    public static boolean isMovementClaimed() {
        return movementClaimed;
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!isEnabled() || event.entityLiving != mc.thePlayer) return;
        double dx = mc.thePlayer.posX - this.prevBpsX;
        double dz = mc.thePlayer.posZ - this.prevBpsZ;
        this.currentBps = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0D);
        this.prevBpsX = mc.thePlayer.posX;
        this.prevBpsZ = mc.thePlayer.posZ;
        if (this.shouldStopSprint()) mc.thePlayer.setSprinting(false);
    }

    /** Leader cancels both mouse clicks while enabled; 1.8.9 Forge cannot, so the bindings are unpressed. */
    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled()) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (!Mouse.getEventButtonState()) return;
        int keyCode = -100 + Mouse.getEventButton();
        if (keyCode == mc.gameSettings.keyBindAttack.getKeyCode()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        } else if (keyCode == mc.gameSettings.keyBindUseItem.getKeyCode()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
    }

    private void applyMoveInput(MovementInput input) {
        if (!isEnabled()) return;
        if (moveFix.get() == MoveFixMode.SILENT && RotationSpoof.hasClaim() && MoveMath.isForwardPressed()) {
            MoveMath.fixStrafe(RotationSpoof.smoothedYaw(), input);
        }
        if (mode.is(ScaffoldMode.TELLY) && mc.thePlayer.onGround
                && this.stage > 0 && MoveMath.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
            input.jump = true;
        }
        if (isLegitTellyMode() && mc.thePlayer.onGround && MoveMath.isForwardPressed()) {
            input.jump = true;
        }
        if (mode.is(ScaffoldMode.LEGIT) && mc.currentScreen == null && !this.clutchActive) {
            if (mc.thePlayer.onGround && (this.legitEdgeState == 1 || this.legitEdgeState == 2)) {
                input.sneak = true;
                input.moveStrafe *= 0.3F;
                input.moveForward *= 0.3F;
            }
        }
    }

    // ===== placement helpers =====

    /** Leader gates placement on its BedNuker/LongJump modules; LunarForge has neither. */
    private boolean canPlace() {
        return true;
    }

    private boolean shouldStopSprint() {
        return !this.isTowering() && this.stage <= 0 && !mode.is(ScaffoldMode.LEGIT) && !rotationMode.is(RotateMode.SNAP);
    }

    private boolean isLegitTellyMode() {
        return mode.is(ScaffoldMode.LEGIT_TELLY);
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0D;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5D,
                            (double) blockPos3.getY() + 0.5D, (double) blockPos3.getZ() + 0.5D);
                    if (enumFacing == null || distance < offset || (distance == offset && facing == EnumFacing.UP)) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private boolean isValidHit(MovingObjectPosition mop, BlockPos blockPos, EnumFacing facing) {
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockPos) && mop.sideHit == facing;
    }

    private Vec3 facePoint(BlockPos blockPos, EnumFacing facing, double a, double b) {
        double n = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0D - FACE_DEPTH : FACE_DEPTH;
        switch (facing.getAxis()) {
            case X: return new Vec3(blockPos.getX() + n, blockPos.getY() + a, blockPos.getZ() + b);
            case Y: return new Vec3(blockPos.getX() + a, blockPos.getY() + n, blockPos.getZ() + b);
            default: return new Vec3(blockPos.getX() + a, blockPos.getY() + b, blockPos.getZ() + n);
        }
    }

    private Vec3 applyRescueRotation(BlockData blockData) {
        BlockPos pos = blockData.blockPos();
        EnumFacing facing = blockData.facing();
        float bestYaw = -180.0F;
        float bestPitch = 0.0F;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestHit = null;
        for (double a : placeOffsets) {
            for (double b : placeOffsets) {
                Vec3 target = this.facePoint(pos, facing, a, b);
                float[] rot = Rotations.getRotations(target.xCoord, target.yCoord, target.zCoord);
                rot[1] = Math.max(-90.0F, Math.min(90.0F, rot[1]));
                MovingObjectPosition mop = Rotations.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance());
                if (!this.isValidHit(mop, pos, facing)) continue;
                float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - RotationSpoof.lastReportedYaw()));
                float pitchDiff = rot[1] - RotationSpoof.lastReportedPitch();
                double dist = yawDiff * yawDiff + pitchDiff * pitchDiff;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestYaw = rot[0];
                    bestPitch = rot[1];
                    bestHit = mop.hitVec;
                }
            }
        }
        if (bestHit == null) return null;
        this.yaw = Rotations.wrapAngleDiff(bestYaw, RotationSpoof.lastReportedYaw());
        this.pitch = bestPitch;
        this.canRotate = true;
        return bestHit;
    }

    private BlockData getBlockData() {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(playerY, this.startY) : playerY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        return this.getBlockData(targetPos);
    }

    private BlockData getBlockData(BlockPos targetPos) {
        if (!Rotations.isReplaceable(targetPos)) return null;
        ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!Rotations.isReplaceable(pos) && !Rotations.isInteractable(pos)
                            && mc.thePlayer.getDistance((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D)
                                    <= (double) mc.playerController.getBlockReachDistance()) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN && Rotations.isReplaceable(pos.offset(facing))) {
                                positions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) return null;
        positions.sort(Comparator.comparingDouble(o -> o.distanceSqToCenter(
                (double) targetPos.getX() + 0.5D, (double) targetPos.getY() + 0.5D, (double) targetPos.getZ() + 0.5D)));
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private boolean place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (!Rotations.isHoldingPlaceableBlock() || this.blockCount <= 0) return false;
        if (this.strictRaytrace.on()) {
            MovingObjectPosition mop = Rotations.rayTrace(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    mc.playerController.getBlockReachDistance());
            if (!this.isValidHit(mop, blockPos, enumFacing)) return false;
            vec3 = mop.hitVec;
        }
        if (!mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(),
                blockPos, enumFacing, vec3)) {
            return false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) this.blockCount--;
        if (this.swing.on()) mc.thePlayer.swingItem();
        else mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        return true;
    }

    private void selectScaffoldBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = Rotations.isPlaceableBlock(held) ? held.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, heldCount);
        if (this.blockCount > 0) return;

        int slot = mc.thePlayer.inventory.currentItem;
        if (this.blockCount == 0) slot--;
        for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (Rotations.isPlaceableBlock(candidate)) {
                mc.thePlayer.inventory.currentItem = hotbarSlot;
                this.blockCount = candidate.stackSize;
                return;
            }
        }
    }

    private float getCurrentYaw() {
        return MoveMath.adjustYaw(mc.thePlayer.rotationYaw, MoveMath.getForwardValue(), MoveMath.getLeftValue());
    }

    private float getLegitTellyRotationStep(float speed) {
        return Math.max(1.0F, Math.min(180.0F, speed));
    }

    private float[] smoothLegitTellyRotation(float fromYaw, float fromPitch, float targetYaw, float targetPitch, float speed) {
        float yawStep = this.getLegitTellyRotationStep(speed);
        float pitchStep = Math.max(1.0F, yawStep * 0.55F);
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - fromYaw);
        float pitchDelta = targetPitch - fromPitch;
        float nextYaw = fromYaw + Rotations.clampAngle(yawDelta, yawStep);
        float nextPitch = fromPitch + Rotations.clampAngle(pitchDelta, pitchStep);
        return new float[]{Rotations.quantizeAngle(nextYaw),
                Rotations.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F))};
    }

    private void resetLegitTellyCycle() {
        this.legitTellyPhase = 0;
        this.legitTellyPhaseTicks = 0;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
    }

    private void updateLegitTelly() {
        if (this.placeDelayCounter > 0) this.placeDelayCounter--;

        boolean onGround = mc.thePlayer.onGround;

        if (onGround && this.legitTellyWasAirborne) this.resetLegitTellyCycle();
        if (!onGround && !this.legitTellyWasAirborne && this.legitTellyPhase == 0) {
            this.legitTellyPhase = 1;
            this.legitTellyPhaseTicks = 0;
        }

        if (this.legitTellyPhase == 1) {
            this.legitTellyPhaseTicks++;
            if (this.legitTellyPhaseTicks >= this.tellyTicks.intValue()) {
                this.legitTellyPhase = 2;
                this.legitTellyPhaseTicks = 0;
            }
        }

        float speed = this.forwardSpeed.value();
        if (this.legitTellyPhase == 2) speed = this.backSpeed.value();
        else if (this.legitTellyPhase == 3) speed = this.placeSpeed.value();

        float targetYaw = mc.thePlayer.rotationYaw;
        float targetPitch = mc.thePlayer.rotationPitch;

        if (this.legitTellyPhase == 1) {
            targetYaw = this.getCurrentYaw();
            targetPitch = 0.0F;
        }

        if (this.legitTellyPhase >= 2) {
            if (this.legitTellyLockedBlockData != null
                    && Rotations.isReplaceable(this.legitTellyLockedBlockData.blockPos().offset(this.legitTellyLockedBlockData.facing()))) {
                // keep the locked placement while its face is still free
            } else {
                this.legitTellyLockedBlockData = null;
            }
            if (this.legitTellyLockedBlockData == null) {
                BlockPos targetPos = new BlockPos(
                        MathHelper.floor_double(mc.thePlayer.posX),
                        this.startY,
                        MathHelper.floor_double(mc.thePlayer.posZ));
                this.legitTellyLockedBlockData = this.getBlockData(targetPos);
            }

            if (this.legitTellyLockedBlockData != null) {
                BlockData bd = this.legitTellyLockedBlockData;
                double[] offsets = {0.15, 0.35, 0.5, 0.65, 0.85};
                double[] xOff = offsets, yOff = offsets, zOff = offsets;
                switch (bd.facing()) {
                    case NORTH: zOff = new double[]{0.02}; break;
                    case EAST: xOff = new double[]{0.98}; break;
                    case SOUTH: zOff = new double[]{0.98}; break;
                    case WEST: xOff = new double[]{0.02}; break;
                    case DOWN: yOff = new double[]{0.02}; break;
                    case UP: yOff = new double[]{0.98}; break;
                    default: break;
                }
                double bestDist = Double.MAX_VALUE;
                for (double dx : xOff) {
                    for (double dy : yOff) {
                        for (double dz : zOff) {
                            float[] rot = Rotations.getRotations(
                                    bd.blockPos().getX() + dx,
                                    bd.blockPos().getY() + dy,
                                    bd.blockPos().getZ() + dz);
                            MovingObjectPosition mop = Rotations.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance());
                            if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                                    || !mop.getBlockPos().equals(bd.blockPos()) || mop.sideHit != bd.facing()) continue;
                            double dist = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.legitTellySilentYaw))
                                    + Math.abs(rot[1] - this.legitTellySilentPitch);
                            if (dist < bestDist) {
                                bestDist = dist;
                                targetYaw = rot[0];
                                targetPitch = rot[1];
                            }
                        }
                    }
                }
            }
        }

        float[] smoothed = this.smoothLegitTellyRotation(
                this.legitTellySilentYaw, this.legitTellySilentPitch, targetYaw, targetPitch, speed);
        this.legitTellySilentYaw = smoothed[0];
        this.legitTellySilentPitch = smoothed[1];
        this.yaw = smoothed[0];
        this.pitch = smoothed[1];
        this.canRotate = true;

        applyRotation(smoothed[0], smoothed[1]);

        if (this.legitTellyPhase >= 2 && this.legitTellyLockedBlockData != null && this.placeDelayCounter <= 0) {
            MovingObjectPosition trace = Rotations.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance());
            if (trace != null && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    && trace.getBlockPos().equals(this.legitTellyLockedBlockData.blockPos())
                    && trace.sideHit == this.legitTellyLockedBlockData.facing()) {
                if (this.place(this.legitTellyLockedBlockData.blockPos(), this.legitTellyLockedBlockData.facing(), trace.hitVec)) {
                    this.placeDelayCounter = this.placeDelay.intValue();
                    if (this.legitTellyPhase == 2) {
                        this.legitTellyPlacedFirstBlock = true;
                        this.legitTellyPhase = 3;
                        this.legitTellyLockedBlockData = null;
                    }
                }
            }
        }

        this.legitTellyWasAirborne = !onGround;
    }

    private void applyRotation(float rotationYaw, float rotationPitch) {
        RotationSpoof.claim(rotationYaw, rotationPitch, 3);
        if (this.moveFix.get() == MoveFixMode.SILENT) RotationSpoof.setSmoothedYaw(rotationYaw);
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (!MoveMath.isForwardPressed()) return false;
        if (MoveMath.isAirAbove()) return false;
        if (mc.thePlayer.onGround) {
            if (this.stage > 0 || mc.gameSettings.keyBindJump.isKeyDown()) return true;
        }
        return this.tellyJumpDelayTimer > 0;
    }

    private boolean isLegitOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        int by = MathHelper.floor_double(mc.thePlayer.posY) - 1;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), by, MathHelper.floor_double(mc.thePlayer.posZ));
        if (Rotations.isReplaceable(below)) return true;
        double[] next = this.predictPosition();
        return Rotations.isReplaceable(new BlockPos(MathHelper.floor_double(next[0]), by, MathHelper.floor_double(next[1])));
    }

    private double[] predictPosition() {
        double[] move = MoveMath.predictMovement();
        return new double[]{mc.thePlayer.posX + mc.thePlayer.motionX + move[0],
                mc.thePlayer.posZ + mc.thePlayer.motionZ + move[1]};
    }

    // ===== snap mode =====

    private SnapTarget solveFace(Vec3 eye, BlockPos support, EnumFacing face) {
        SnapTarget best = null;
        double bestCenter = Double.MAX_VALUE;
        for (double a : SNAP_OFFSETS) {
            for (double b : SNAP_OFFSETS) {
                double center = (a - 0.5D) * (a - 0.5D) + (b - 0.5D) * (b - 0.5D);
                if (center >= bestCenter) continue;
                Vec3 point = this.facePoint(support, face, a, b);
                float[] rot = Rotations.getRotations(point.xCoord, point.yCoord, point.zCoord,
                        eye.xCoord, eye.yCoord, eye.zCoord);
                rot[1] = MathHelper.clamp_float(rot[1], -90.0F, 90.0F);
                MovingObjectPosition mop = Rotations.rayTrace(eye, rot[0], rot[1], mc.playerController.getBlockReachDistance());
                if (!this.isValidHit(mop, support, face)) continue;
                bestCenter = center;
                best = new SnapTarget(support, face, rot[0], rot[1], eye.squareDistanceTo(mop.hitVec));
            }
        }
        return best;
    }

    private SnapTarget solveCell(Vec3 eye, BlockPos cell) {
        if (!Rotations.isReplaceable(cell)) return null;
        SnapTarget best = null;
        for (EnumFacing dir : EnumFacing.VALUES) {
            if (dir == EnumFacing.UP) continue;
            BlockPos support = cell.offset(dir);
            if (Rotations.isReplaceable(support) || Rotations.isInteractable(support)) continue;
            SnapTarget target = this.solveFace(eye, support, dir.getOpposite());
            if (target != null && (best == null || target.distance < best.distance)) best = target;
        }
        return best;
    }

    private SnapTarget solveBridge(Vec3 eye, BlockPos cell) {
        SnapTarget best = null;
        double bestDist = Double.MAX_VALUE;
        for (EnumFacing dir : EnumFacing.HORIZONTALS) {
            BlockPos neighbor = cell.offset(dir);
            double dist = neighbor.distanceSqToCenter(mc.thePlayer.posX, neighbor.getY() + 0.5D, mc.thePlayer.posZ);
            if (dist >= bestDist) continue;
            SnapTarget target = this.solveCell(eye, neighbor);
            if (target != null) {
                best = target;
                bestDist = dist;
            }
        }
        return best;
    }

    private SnapTarget findSnapTarget(Vec3 eye) {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        int y = (this.stage != 0 && !this.shouldKeepY ? Math.min(playerY, this.startY) : playerY) - 1;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), y, MathHelper.floor_double(mc.thePlayer.posZ));
        if (Rotations.isReplaceable(below)) {
            SnapTarget target = this.solveCell(eye, below);
            return target != null ? target : this.solveBridge(eye, below);
        }
        if (!this.earlySnap.on() || !mc.thePlayer.onGround) return null;
        double[] next = this.predictPosition();
        BlockPos edge = this.edgeCell(next[0], next[1], y);
        return edge == null ? null : this.solveCell(eye, edge);
    }

    private BlockPos edgeCell(double x, double z, int y) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        double threshold = this.edgeThreshold.value();
        double xOff = x - bx;
        double zOff = z - bz;
        int dx = xOff < threshold ? -1 : (xOff > 1.0D - threshold ? 1 : 0);
        int dz = zOff < threshold ? -1 : (zOff > 1.0D - threshold ? 1 : 0);
        int[][] candidates = {{dx, 0}, {0, dz}, {dx, dz}};
        for (int[] c : candidates) {
            if (c[0] == 0 && c[1] == 0) continue;
            BlockPos pos = new BlockPos(bx + c[0], y, bz + c[1]);
            if (Rotations.isReplaceable(pos)) return pos;
        }
        return null;
    }

    private float[] stepRotation(float fromYaw, float fromPitch, float toYaw, float toPitch, float yawSpeed, float pitchSpeed) {
        float yawDiff = MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
        float pitchDiff = toPitch - fromPitch;
        float nextYaw = fromYaw + Rotations.clampAngle(yawDiff, yawSpeed);
        float nextPitch = fromPitch + Rotations.clampAngle(pitchDiff, pitchSpeed);
        return new float[]{Rotations.quantizeAngle(nextYaw),
                Rotations.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F))};
    }

    private void updateSnap(boolean allowPlace) {
        if (this.placeDelayCounter > 0) this.placeDelayCounter--;
        if (this.snapHoldCounter > 0) this.snapHoldCounter--;
        if (this.snapDelayCounter > 0) this.snapDelayCounter--;
        if (!this.canPlace() || !Rotations.isHoldingPlaceableBlock()) return;

        SnapTarget target = allowPlace ? this.findSnapTarget(mc.thePlayer.getPositionEyes(1.0F)) : null;
        float targetYaw;
        float targetPitch;
        float speed;

        if (this.delayPlacement.on() && target != null) {
            if (this.snapPendingTarget == null || !this.snapPendingTarget.blockPos().equals(target.blockPos())) {
                this.snapPendingTarget = target;
                this.snapDelayCounter = 1;
            }
            targetYaw = Rotations.wrapAngleDiff(target.yaw, RotationSpoof.lastReportedYaw());
            targetPitch = target.pitch;
            speed = this.snapBackSpeed.value();
            this.snapHoldCounter = this.snapHoldTicks.intValue();
            this.snapLastYaw = target.yaw;
            this.snapLastPitch = target.pitch;
        } else if (target != null) {
            targetYaw = Rotations.wrapAngleDiff(target.yaw, RotationSpoof.lastReportedYaw());
            targetPitch = target.pitch;
            speed = this.snapBackSpeed.value();
            this.snapHoldCounter = this.snapHoldTicks.intValue();
            this.snapLastYaw = target.yaw;
            this.snapLastPitch = target.pitch;
        } else if (this.snapHoldCounter > 0 && !Float.isNaN(this.snapLastYaw)) {
            targetYaw = Rotations.wrapAngleDiff(this.snapLastYaw, RotationSpoof.lastReportedYaw());
            targetPitch = this.snapLastPitch;
            speed = this.snapBackSpeed.value();
        } else {
            targetYaw = Rotations.wrapAngleDiff(this.getCurrentYaw(), RotationSpoof.lastReportedYaw());
            targetPitch = this.snapForwardPitch.value();
            speed = this.snapForwardSpeed.value();
            this.snapLastYaw = Float.NaN;
            this.snapPendingTarget = null;
        }

        float[] next = this.stepRotation(RotationSpoof.lastReportedYaw(), RotationSpoof.lastReportedPitch(),
                targetYaw, targetPitch, speed, speed);
        this.yaw = next[0];
        this.pitch = next[1];
        this.canRotate = true;
        applyRotation(next[0], next[1]);

        SnapTarget placeTarget = this.delayPlacement.on() && this.snapDelayCounter == 0 ? this.snapPendingTarget : target;
        if (placeTarget == null || this.placeDelayCounter > 0) return;
        MovingObjectPosition mop = Rotations.rayTrace(next[0], next[1], mc.playerController.getBlockReachDistance());
        if (!this.isValidHit(mop, placeTarget.blockPos(), placeTarget.facing())) return;
        if (this.place(placeTarget.blockPos(), placeTarget.facing(), mop.hitVec)) {
            this.placeDelayCounter = this.placeDelay.intValue();
            this.recordPlacement(placeTarget.blockPos().offset(placeTarget.facing()));
            this.snapPendingTarget = null;
        }
    }

    private void recordPlacement(BlockPos cell) {
        this.placedTrail.add(new PlacedBlock(cell, System.currentTimeMillis()));
        while (this.placedTrail.size() > 32) this.placedTrail.remove(0);
    }

    // ===== god bridge support =====

    private boolean isGodBridgeOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        if (Rotations.isReplaceable(below)) return true;
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < 0.15D || xOff > 0.85D || zOff < 0.15D || zOff > 0.85D) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < 0.15D ? -1 : (xOff > 0.85D ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < 0.15D ? -1 : (zOff > 0.85D ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (Rotations.isReplaceable(adjacentBelow)) return true;
            }
        }
        return false;
    }

    private float quantizeDiagonal(float yaw) {
        return 45.0F + 90.0F * Math.round((yaw - 45.0F) / 90.0F);
    }

    private List<BlockData> getPlaceOptions(BlockData primary) {
        List<BlockData> options = new ArrayList<BlockData>();
        options.add(primary);
        BlockPos cell = primary.blockPos().offset(primary.facing());
        for (EnumFacing dir : EnumFacing.VALUES) {
            EnumFacing face = dir.getOpposite();
            if (face == EnumFacing.DOWN) continue;
            BlockPos support = cell.offset(dir);
            if (support.equals(primary.blockPos())) continue;
            if (Rotations.isReplaceable(support) || Rotations.isInteractable(support)) continue;
            options.add(new BlockData(support, face));
        }
        return options;
    }

    private float faceCenterPitch(BlockData data) {
        double x = data.blockPos().getX() + 0.5D + data.facing().getDirectionVec().getX() * 0.5D;
        double y = data.blockPos().getY() + 0.5D + data.facing().getDirectionVec().getY() * 0.5D;
        double z = data.blockPos().getZ() + 0.5D + data.facing().getDirectionVec().getZ() * 0.5D;
        return Math.max(-89.0F, Math.min(89.0F, Rotations.getRotations(x, y, z)[1]));
    }

    // ===== clutch =====

    private void updateClutch() {
        if (!clutch.on() || mc.thePlayer.onGround || this.bbUnC()) {
            this.clutchReset();
            return;
        }
        double fallDistance = mc.thePlayer.fallDistance;
        boolean shouldClutch = fallDistance > 2.0D && !MoveMath.isAirAbove() && !mc.thePlayer.isCollidedHorizontally
                && (!this.onlyInVoid.on() || this.isFallingIntoVoid());
        if (shouldClutch && !this.clutchActive) this.clutchActive = true;
    }

    private void clutchReset() {
        Module stuckModule = ModuleManager.get("stuck");
        if (this.clutchOwnsStuck && stuckModule instanceof ModuleStuck) {
            ((ModuleStuck) stuckModule).setEnabled(false);
        }
        this.clutchActive = false;
        this.clutchOwnsStuck = false;
    }

    private boolean isFallingIntoVoid() {
        if (mc.thePlayer == null) return false;
        for (int i = 0; i <= 128; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY) - i, MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) return false;
        }
        return true;
    }

    private boolean bbUnC() {
        if (mc.thePlayer == null) return false;
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        for (int i = 1; i <= 2; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), playerY - i, MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) return true;
        }
        return false;
    }

    // ===== low blocks warning =====

    private void updateLowBlockWarning() {
        if (!warningLowBlocks.on()) {
            this.lowBlocksWarned = false;
            return;
        }
        int total = 0;
        // Count inventory + hotbar independently of the block-counter HUD.
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (Rotations.isPlaceableBlock(stack)) total += Math.max(0, stack.stackSize);
        }
        int threshold = this.lowBlocksThreshold.intValue();
        if (total >= threshold) {
            this.lowBlocksWarned = false;
        } else if (!this.lowBlocksWarned) {
            this.lowBlocksWarned = true;
            mc.thePlayer.addChatMessage(new ChatComponentText("\u00a77Scaffold: \u00a7f" + total + " blocks remaining"));
        }
    }

    // ===== rendering =====

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        long now = System.currentTimeMillis();
        Iterator<PlacedBlock> iterator = this.placedTrail.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().time > 600L) iterator.remove();
        }
        if (mode.is(ScaffoldMode.LEGIT) || mode.is(ScaffoldMode.LEGIT_TELLY)) return;
        if (this.placedTrail.isEmpty()) return;

        float partial = event.partialTicks;
        double ox = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partial;
        double oy = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * partial;
        double oz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partial;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);
        for (PlacedBlock b : this.placedTrail) {
            float life = 1.0F - (now - b.time) / 600.0F;
            float alpha = Math.max(0.0F, Math.min(1.0F, life)) * 0.78F;
            double height = 0.15D + 0.85D * life;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            double minX = b.pos.getX() - ox;
            double maxX = b.pos.getX() + 1.0D - ox;
            double minY = b.pos.getY() - oy;
            double maxY = b.pos.getY() + height - oy;
            double minZ = b.pos.getZ() - oz;
            double maxZ = b.pos.getZ() + 1.0D - oz;
            drawBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);
        }
        GL11.glEnd();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void drawBoxEdges(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
    }

    /** Simplified counter/BPS overlay (plain text + bars instead of Leader's shader cards). */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!isEnabled() || mc.thePlayer == null) return;

        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (!GameplayUtil.isInteractable(block) && Rotations.isSolidBlock(block)) {
                        count += stack.stackSize;
                    }
                }
            }
        }
        this.hudCount = count;
        if (this.hudCount > this.counterMax) this.counterMax = this.hudCount;
        if (this.hudCount <= 0) this.counterMax = 0;

        long now = System.currentTimeMillis();
        float dt = this.lastHudFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastHudFrame) / 1000.0F);
        this.lastHudFrame = now;
        float k = 1.0F - (float) Math.exp(-dt * 12.0F);
        float percent = this.counterMax > 0 ? this.hudCount / (float) this.counterMax : 0.0F;
        this.animBps += (this.currentBps - this.animBps) * k;
        this.animPercent += (percent - this.animPercent) * k;

        ScaledResolution sr = new ScaledResolution(mc);
        int cx = sr.getScaledWidth() / 2;
        int y = sr.getScaledHeight() / 2 + 12;

        if (blockCounter.on()) drawCountHud(cx, y);
        if (bPSRender.on()) drawBpsHud(cx, y + 16);
    }

    private void drawCountHud(int cx, int y) {
        int color = this.hudCount <= 16 ? 0xFF5454 : this.hudCount <= 48 ? 0xFFB040 : 0x00BEFF;
        String text = this.hudCount + (this.hudCount == 1 ? " block" : " blocks");
        int width = mc.fontRendererObj.getStringWidth(text);
        int x = cx - width / 2;
        Gui.drawRect(x - 3, y - 3, x + width + 3, y + 9, 0x66000000);
        mc.fontRendererObj.drawStringWithShadow(text, x, y, 0xF4F7FC);
        int barY = y + 11;
        Gui.drawRect(cx - 40, barY, cx + 40, barY + 1, 0x28FFFFFF);
        int fill = (int) (80.0F * Math.max(0.0F, Math.min(1.0F, this.animPercent)));
        Gui.drawRect(cx - 40, barY, cx - 40 + Math.max(2, fill), barY + 1, 0xF0000000 | color);
    }

    private void drawBpsHud(int cx, int y) {
        boolean over = this.animBps > 5.92F;
        int color = over ? 0xFF5454 : 0x00BEFF;
        String label = "SPEED " + String.format("%.2f", this.animBps) + " b/s";
        int width = mc.fontRendererObj.getStringWidth(label);
        int x = cx - width / 2;
        Gui.drawRect(x - 3, y - 3, x + width + 3, y + 9, 0x66000000);
        mc.fontRendererObj.drawStringWithShadow(label, x, y, over ? 0xFF5454 : 0xF4F7FC);
        int barY = y + 11;
        Gui.drawRect(cx - 40, barY, cx + 40, barY + 1, 0x28FFFFFF);
        float ratio = Math.max(0.0F, Math.min(1.0F, this.animBps / 10.0F));
        Gui.drawRect(cx - 40, barY, cx - 40 + Math.max(2, (int) (80.0F * ratio)), barY + 1, 0xF0000000 | color);
        int markerX = cx - 40 + (int) (80.0F * (5.92F / 10.0F));
        Gui.drawRect(markerX, barY - 1, markerX + 1, barY + 2, 0xC8FFFFFF);
    }

    // ===== enable / disable =====

    @Override protected void onEnable() {
        clutchReset();
        restoreRightClickTelly();
        this.rightClickBlockedUntilRelease = Mouse.isButtonDown(1);
        this.lowBlocksWarned = false;
        this.lastSlot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towering = false;
        this.godBridgeDiag = Float.NaN;
        this.placeDelayCounter = 0;
        this.prevBpsX = mc.thePlayer != null ? mc.thePlayer.posX : 0.0D;
        this.prevBpsZ = mc.thePlayer != null ? mc.thePlayer.posZ : 0.0D;
        this.currentBps = 0.0F;
        this.animBps = 0.0F;
        this.animPercent = 0.0F;
        this.lastHudFrame = 0L;
        this.airTicks = 0;
        this.pendingSpeedLimitRot = false;
        this.forwardRotateTicksLeft = 0;
        this.legitEdgeState = 0;
        this.legitEdgeTimer = 0;
        this.legitWasOnEdge = false;
        this.legitTellyPhase = 0;
        this.legitTellyPhaseTicks = 0;
        this.legitTellyWasAirborne = false;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
        this.legitTellySilentYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        this.legitTellySilentPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 82.0F;
        this.snapHoldCounter = 0;
        this.snapLastYaw = Float.NaN;
        this.snapLastPitch = 0.0F;
        this.snapDelayCounter = 0;
        this.snapPendingTarget = null;
        this.placedTrail.clear();
        this.startY = mc.thePlayer != null ? MathHelper.floor_double(mc.thePlayer.posY) : 0;
        this.profileMode = mode.get().ordinal();
        this.lastSeenMode = this.profileMode;
        PlayerInputHook.ensureAttached(this.inputModifier, null);
    }

    @Override protected void onDisable() {
        try {
            this.clutchReset();
        } finally {
            this.restoreRightClickTelly();
            this.lowBlocksWarned = false;
            this.snapHoldCounter = 0;
            this.snapLastYaw = Float.NaN;
            this.snapLastPitch = 0.0F;
            this.snapPendingTarget = null;
            this.placedTrail.clear();
            this.startY = mc.thePlayer != null ? MathHelper.floor_double(mc.thePlayer.posY) : 0;
            if (mc.thePlayer != null && this.lastSlot != -1) mc.thePlayer.inventory.currentItem = this.lastSlot;
            this.movementClaimed = false;
            RotationSpoof.clear();
            PlayerInputHook.release(this.inputModifier, null);
        }
    }

    // ===== inner classes =====

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() { return this.blockPos; }

        public EnumFacing facing() { return this.facing; }
    }

    private static final class SnapTarget extends BlockData {
        private final float yaw;
        private final float pitch;
        private final double distance;

        private SnapTarget(BlockPos blockPos, EnumFacing facing, float yaw, float pitch, double distance) {
            super(blockPos, facing);
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
        }
    }

    private static final class PlacedBlock {
        private final BlockPos pos;
        private final long time;

        private PlacedBlock(BlockPos pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }

    /** In-memory snapshot of the rotation settings, captured per scaffold mode. */
    private static final class RotationProfile {
        private RotateMode rotationMode;
        private boolean noUpdateWhenCanPlace;
        private boolean edgeLimit;
        private float godBridgeTolerance;
        private MoveFixMode moveFix;
        private float startRotSpeed;
        private float normalRotSpeed;
        private float normalModeSpeed;
        private float legitModeSpeed;
        private boolean strictRaytrace;
        private boolean airRescue;
        private float edgeThreshold;
        private float snapForwardSpeed;
        private float snapBackSpeed;
        private boolean earlySnap;
        private float snapForwardPitch;
        private int snapHoldTicks;
        private boolean delayPlacement;
        private float forwardSpeed;
        private float backSpeed;
        private float placeSpeed;
    }
}
