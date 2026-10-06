package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.ClickCounter;
import com.example.lunarforge.util.ClickerTiming;
import com.example.lunarforge.util.Fields;
import com.example.lunarforge.util.GameplayUtil;
import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4's clicker engine (gg.vape.click.ClickEngine plus the
 * right-click half of gg.vape.module.combat.RightClicker), configured as a
 * legit sword blockhit helper:
 *
 * - Right-clicks at the configured CPS range (defaults 8-12). Each click is
 *   the shipped synthetic right click idiom (setKeyBindState(false) + onTick,
 *   single shot via isPressed), it clears rightClickDelayTimer like FastPlace
 *   because vanilla otherwise caps right clicks at one action per 4 ticks,
 *   and it is counted by the CPS/Keystrokes HUD via ClickCounter.
 * - Randomization (Vape ClickEngine.calculateNextClickDelay):
 *   Normal = plain 1000/cps from the range, Extra = Vape's legacy burst and
 *   fast/slow phase model, Extra+ = Vape's humanized timing state
 *   ({@link ClickerTiming}: drifting target, fatigue, bursts, click noise,
 *   pauses). Default Extra+.
 * - Jitter (Vape ClickEngine.generateJitter/updateJitter/applyJitterRotation):
 *   a fresh random pixel offset (up to 7 px) per click, spread over the next
 *   few client ticks and applied through the same synthetic mouse mechanism
 *   as ModuleAimAssist (pixels * (sens*0.6+0.2)^3 * 8 via
 *   EntityPlayerSP.setAngles). Default off.
 * - Sword-only hard gate: right clicks only fire while a sword is held.
 * - "Require Auto Clicker" (default on): right clicks only fire while the
 *   Auto Clicker module is enabled, so the module can never degenerate into
 *   a meaningless standalone right-click spam.
 *
 * Vape's background clicker worker becomes a millisecond-precision client
 * tick scheduler here; jitter application runs on render ticks exactly like
 * ModuleAimAssist's. Key state is restored from the raw input on the tick
 * after each click and never touched inside a GUI (see ModuleBlockHitMode).
 */
public final class ModuleLegitTest extends Module {

    public enum RandomizationMode implements ChoiceSetting.Option {
        NORMAL("Normal"), EXTRA("Extra"), EXTRA_PLUS("Extra+");

        private final String label;

        RandomizationMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final Field RIGHT_CLICK_DELAY =
            Fields.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
    private static final float JITTER_MAX_OFFSET = 7.0F;

    // ===== settings =====
    private final NumberSetting cpsMin = integer("cpsMin", 8, 1, 20).label(() -> "Min CPS");
    private final NumberSetting cpsMax = integer("cpsMax", 12, 1, 20).label(() -> "Max CPS");
    private final ChoiceSetting<RandomizationMode> randomization =
            choice("randomization", RandomizationMode.EXTRA_PLUS).label(() -> "Randomization");
    private final BoolSetting jitter = bool("jitter", false).label(() -> "Jitter");
    private final BoolSetting requireAutoClicker =
            bool("requireAutoClicker", true).label(() -> "Require Auto Clicker");

    // ===== scheduler state =====
    private final Random random = new Random();
    private long nextClickAt;
    private boolean clickRestorePending;

    // ===== Extra state (Vape calculateLegacyRandomizedDelay) =====
    private boolean burstActive;
    private int burstLength;
    private int burstClickCount;
    private boolean fastPhaseActive = true;
    private int fastPhaseClickCount;
    private int slowPhaseLength;
    private int slowPhaseClickCount;
    private int configuredFastPhaseLength;
    private long lastClickDelayMillis;

    // ===== Extra+ state (Vape AutoClickerTimingState) =====
    private final ClickerTiming timing = new ClickerTiming();

    // ===== jitter state (Vape ClickEngine) =====
    private double horizontalJitterOffset;
    private double verticalJitterOffset;
    private double remainingJitterSteps;
    private double jitterStepCount;
    private float pendingYawJitter;
    private float pendingPitchJitter;

    public ModuleLegitTest() {
        super("LEGIT_TEST", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(cpsMin, cpsMax);
            s.add(randomization, jitter, requireAutoClicker);
        });
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        // Mirror ModuleBlockHitMode.onDisable: restoring from the raw mouse/keyboard
        // state is only safe without a GUI (a GUI-open right click must not leak).
        if (mc.gameSettings != null && mc.currentScreen == null) {
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        nextClickAt = 0L;
        clickRestorePending = false;
        resetLegacyTiming();
        horizontalJitterOffset = 0.0D;
        verticalJitterOffset = 0.0D;
        remainingJitterSteps = 0.0D;
        pendingYawJitter = 0.0F;
        pendingPitchJitter = 0.0F;
    }

    /** ModuleBlockHitMode's Auto Clicker probe. */
    private static boolean isAutoClickerActive() {
        Module autoClicker = ModuleManager.get("auto_clicker");
        return autoClicker != null && autoClicker.isEnabled();
    }

    private boolean canClick(Minecraft mc) {
        if (!GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return false;
        return !requireAutoClicker.on() || isAutoClickerActive();
    }

    // ===== click scheduling =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        updateJitter();

        // One-tick restore of the synthetic right click; skipped inside GUIs,
        // where vanilla has already unpressed the binding.
        if (clickRestorePending) {
            clickRestorePending = false;
            if (mc.currentScreen == null) {
                GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
        }

        if (mc.currentScreen != null || !canClick(mc)) {
            nextClickAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (nextClickAt == 0L) nextClickAt = now + nextClickDelay();
        if (now < nextClickAt) return;
        fireClick(mc);
        nextClickAt = Math.max(now, nextClickAt) + nextClickDelay();
    }

    private void fireClick(Minecraft mc) {
        int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        // Same synthetic right click idiom as ModuleAutoClicker.blockHit: keep the
        // held path off, fire a single shot via isPressed().
        KeyBinding.setKeyBindState(useCode, false);
        if (!mc.thePlayer.isUsingItem()) {
            // FastPlace precedent: vanilla ignores right clicks while
            // rightClickDelayTimer > 0, which would cap us at ~5 CPS.
            if (Fields.getInt(RIGHT_CLICK_DELAY, mc) > 0) {
                Fields.setInt(RIGHT_CLICK_DELAY, mc, 0);
            }
            KeyBinding.onTick(useCode);
            ClickCounter.register(1);
            generateJitter();
        }
        clickRestorePending = true;
    }

    /** Vape ClickEngine.calculateNextClickDelay. */
    private long nextClickDelay() {
        int min = cpsMin.intValue();
        int max = Math.max(cpsMax.intValue(), min);
        int size = max - min;
        int selectedCps = size <= 0 ? min : random.nextInt(size) + min + 1;

        if (randomization.is(RandomizationMode.NORMAL)) {
            return 1000L / selectedCps;
        }
        if (selectedCps == 0) selectedCps = 1;
        if (randomization.is(RandomizationMode.EXTRA)) {
            return legacyRandomizedDelay(selectedCps);
        }
        timing.configureCpsRange(min, max);
        return timing.nextDelayMillis();
    }

    /** Vape ClickEngine.calculateLegacyRandomizedDelay: burst and fast/slow phases. */
    private long legacyRandomizedDelay(int selectedCps) {
        if (!burstActive) {
            lastClickDelayMillis = 1000L / selectedCps;
            if (random.nextInt(4) == 1) {
                burstActive = true;
                burstLength = 1 + random.nextInt(5);
            } else if (random.nextInt(10) != 1 && random.nextInt(10) == 1) {
                burstActive = true;
                burstLength = 5 + random.nextInt(10);
            }
        }
        if (burstActive && ++burstClickCount >= burstLength) {
            burstClickCount = 0;
            burstActive = false;
        }
        if (random.nextInt(48) % (fastPhaseActive ? 6 : 10) == 0 && !burstActive) {
            lastClickDelayMillis += random.nextInt(45) + 40L;
        }
        if (fastPhaseActive) {
            if (++fastPhaseClickCount >= configuredFastPhaseLength) {
                slowPhaseLength = 75 + random.nextInt(125);
                fastPhaseActive = false;
                fastPhaseClickCount = 0;
            }
            long phaseDelay = random.nextInt(5) == 3 ? 50L : 25L;
            return lastClickDelayMillis + phaseDelay;
        }
        if (++slowPhaseClickCount >= slowPhaseLength) {
            fastPhaseActive = true;
            configuredFastPhaseLength = 7 + random.nextInt(8);
            slowPhaseClickCount = 0;
        }
        return lastClickDelayMillis;
    }

    private void resetLegacyTiming() {
        burstActive = false;
        burstLength = 0;
        burstClickCount = 0;
        fastPhaseActive = true;
        fastPhaseClickCount = 0;
        slowPhaseLength = 0;
        slowPhaseClickCount = 0;
        configuredFastPhaseLength = 0;
        lastClickDelayMillis = 0L;
    }

    // ===== jitter =====

    /** Vape ClickEngine.generateJitter: a fresh random pixel offset per click. */
    private void generateJitter() {
        if (!jitter.on()) return;
        horizontalJitterOffset = -JITTER_MAX_OFFSET
                + random.nextDouble() * JITTER_MAX_OFFSET * 2.0D;
        verticalJitterOffset = -JITTER_MAX_OFFSET
                + random.nextDouble() * JITTER_MAX_OFFSET * 2.0D;
        jitterStepCount = (Math.abs(horizontalJitterOffset) + Math.abs(verticalJitterOffset)) * 0.45D;
        remainingJitterSteps = jitterStepCount;
    }

    /** Vape ClickEngine.updateJitter: spreads the offset over the next client ticks. */
    private void updateJitter() {
        if (!jitter.on()) return;
        if (remainingJitterSteps > 0.0D) {
            pendingYawJitter += (float) (horizontalJitterOffset / jitterStepCount);
            pendingPitchJitter += (float) (verticalJitterOffset / jitterStepCount);
            remainingJitterSteps -= 1.0D;
        } else {
            // Vape: floor(c(pending, 1)) - fractions never survive a tick boundary.
            pendingYawJitter = 0.0F;
            pendingPitchJitter = 0.0F;
        }
    }

    /**
     * Vape ClickEngine.applyJitterRotation through ModuleAimAssist's mouse
     * mechanism: accumulated synthetic mouse pixels are converted with the
     * vanilla sensitivity curve and fed to EntityPlayerSP.setAngles.
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled() || !jitter.on()) return;
        if (pendingYawJitter == 0.0F && pendingPitchJitter == 0.0F) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        int yawPixels = (int) pendingYawJitter;
        int pitchPixels = -(int) pendingPitchJitter;
        float sensitivity = mc.gameSettings.mouseSensitivity;
        float base = sensitivity * 0.6F + 0.2F;
        float scale = base * base * base * 8.0F;
        mc.thePlayer.setAngles(yawPixels * scale, pitchPixels * scale);
        pendingYawJitter = 0.0F;
        pendingPitchJitter = 0.0F;
    }
}
