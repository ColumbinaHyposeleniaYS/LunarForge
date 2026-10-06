package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * The single clicker module (Vape v4 click engine, gg.vape.click.ClickEngine).
 * It replaced the old Leader-Lite AutoClicker and the temporary LegitTest split:
 * both click halves now live here under one "Auto Clicker" entry.
 *
 * - Attack clicking (left half): while the attack key is held (and the player is
 *   not using an item), left clicks fire at the half's own CPS range and
 *   randomization mode. "Weapons Only" gates the half to weapons (swords/knockback
 *   items, plus tools with "Allow Tools") and "Don't Break Blocks" keeps it from
 *   mining blocks when no player is targeted. A physical left press pushes the
 *   synthetic cadence one delay back (Leader-Lite LeftClickMouseEvent precedent).
 * - Block hit clicking (right half): right clicks fire at the half's own CPS range
 *   and randomization mode. "Sword Only" (default on) keeps the legit sword
 *   blockhit semantics; turning it off turns the half into a generic right-click
 *   clicker. "Require Auto Clicker" (default on) only lets the right half run
 *   while the attack half is actively clicking, so the module can not degenerate
 *   into a meaningless standalone right-click spam; turning it off restores
 *   free-running right clicks.
 * - Randomization, one independent set per half (Vape
 *   ClickEngine.calculateNextClickDelay): Normal = plain 1000/cps from the
 *   half's range, Extra = Vape's legacy burst and fast/slow phase model, Extra+ =
 *   Vape's humanized timing state ({@link ClickerTiming}: drifting target,
 *   fatigue, bursts, click noise, pauses). "Delay" ignores the CPS engine
 *   entirely and clicks on a plain millisecond interval picked between the
 *   half's "Delay Min" and "Delay Max" (equal values = a strictly fixed
 *   interval). Both halves default to Extra+; delays default to 80-120 ms.
 * - Jitter (Vape ClickEngine.generateJitter/updateJitter/applyJitterRotation):
 *   the one setting shared by both halves: a fresh random pixel offset (up to
 *   7 px) per click, spread over the next few client ticks and applied through
 *   the same synthetic mouse mechanism as ModuleAimAssist
 *   (pixels * (sens*0.6+0.2)^3 * 8 via EntityPlayerSP.setAngles). Default off.
 *
 * The right half clears rightClickDelayTimer like FastPlace because vanilla
 * otherwise caps right clicks at one action per 4 ticks. Both halves are
 * counted by the CPS/Keystrokes HUD via ClickCounter. Key state is restored
 * from the raw input on the tick after each click and never touched inside a
 * GUI (see ModuleBlockHitMode).
 */
public final class ModuleAutoClicker extends Module {

    public enum RandomizationMode implements ChoiceSetting.Option {
        NORMAL("Normal"), EXTRA("Extra"), EXTRA_PLUS("Extra+"), DELAY("Delay");

        private final String label;

        RandomizationMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final Field RIGHT_CLICK_DELAY =
            Fields.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");
    private static final float JITTER_MAX_OFFSET = 7.0F;

    // ===== left half (attack) settings =====
    private final NumberSetting cpsMin = integer("cpsMin", 8, 1, 20).label(() -> "Min CPS");
    private final NumberSetting cpsMax = integer("cpsMax", 12, 1, 20).label(() -> "Max CPS");
    private final ChoiceSetting<RandomizationMode> randomization =
            choice("randomization", RandomizationMode.EXTRA_PLUS).label(() -> "Randomization");
    private final NumberSetting delayMin = integer("delayMin", 80, 1, 2000).label(() -> "Delay Min (ms)");
    private final NumberSetting delayMax = integer("delayMax", 120, 1, 2000).label(() -> "Delay Max (ms)");
    private final BoolSetting weaponsOnly = bool("weaponsOnly", true).label(() -> "Weapons Only");
    private final BoolSetting allowTools = bool("allowTools", false).label(() -> "Allow Tools");
    private final BoolSetting breakBlocks = bool("breakBlocks", true).label(() -> "Don't Break Blocks");
    private final NumberSetting range = decimal("range", 3.0f, 3.0f, 8.0f).label(() -> "Player Range");
    private final NumberSetting hitBoxVertical = decimal("hitBoxVertical", 0.1f, 0.0f, 1.0f).label(() -> "Hit Box Vertical");
    private final NumberSetting hitBoxHorizontal = decimal("hitBoxHorizontal", 0.2f, 0.0f, 1.0f).label(() -> "Hit Box Horizontal");

    // ===== right half (block hit) settings =====
    private final BoolSetting blockHit = bool("blockHit", true).label(() -> "Block Hit");
    private final NumberSetting rCpsMin = integer("rCpsMin", 8, 1, 20).label(() -> "Min CPS");
    private final NumberSetting rCpsMax = integer("rCpsMax", 12, 1, 20).label(() -> "Max CPS");
    private final ChoiceSetting<RandomizationMode> rRandomization =
            choice("rRandomization", RandomizationMode.EXTRA_PLUS).label(() -> "Randomization");
    private final NumberSetting rDelayMin = integer("rDelayMin", 80, 1, 2000).label(() -> "Delay Min (ms)");
    private final NumberSetting rDelayMax = integer("rDelayMax", 120, 1, 2000).label(() -> "Delay Max (ms)");
    private final BoolSetting swordOnly = bool("swordOnly", true).label(() -> "Sword Only");
    private final BoolSetting requireAutoClicker =
            bool("requireAutoClicker", true).label(() -> "Require Auto Clicker");

    // ===== shared settings =====
    private final BoolSetting jitter = bool("jitter", false).label(() -> "Jitter");

    // ===== per-half engine state (each half reads its own settings) =====
    private final TimingEngine attackEngine =
            new TimingEngine(cpsMin, cpsMax, randomization, delayMin, delayMax);
    private final TimingEngine blockHitEngine =
            new TimingEngine(rCpsMin, rCpsMax, rRandomization, rDelayMin, rDelayMax);
    private long nextAttackAt;
    private boolean attackRestorePending;
    private long nextBlockHitAt;
    private boolean blockHitRestorePending;

    // ===== jitter state (Vape ClickEngine) =====
    private final Random random = new Random();
    private double horizontalJitterOffset;
    private double verticalJitterOffset;
    private double remainingJitterSteps;
    private double jitterStepCount;
    private float pendingYawJitter;
    private float pendingPitchJitter;

    public ModuleAutoClicker() {
        super("AUTO_CLICKER", false);
    }

    @Override protected void layout(Page page) {
        page.section("leftClicker", s -> {
            s.add(cpsMin, cpsMax);
            s.add(randomization);
            s.add(delayMin, delayMax).hideIf(() -> !randomization.is(RandomizationMode.DELAY));
            s.group(weaponsOnly, g -> {
                g.add(allowTools);
                g.group(breakBlocks, b -> b.add(range, hitBoxVertical, hitBoxHorizontal));
            });
        });
        page.section("rightClicker", s -> {
            s.group(blockHit, b -> {
                b.add(rCpsMin, rCpsMax);
                b.add(rRandomization);
                b.add(rDelayMin, rDelayMax).hideIf(() -> !rRandomization.is(RandomizationMode.DELAY));
                b.add(swordOnly, requireAutoClicker);
            });
        });
        page.section("generalOptions", s -> s.add(jitter));
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        // Mirror ModuleBlockHitMode.onDisable: restoring from the raw mouse/keyboard
        // state is only safe without a GUI (a GUI-open click must not leak).
        if (mc.gameSettings != null && mc.currentScreen == null) {
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        resetSchedule();
        resetJitter();
    }

    private void resetSchedule() {
        nextAttackAt = 0L;
        attackRestorePending = false;
        nextBlockHitAt = 0L;
        blockHitRestorePending = false;
        attackEngine.reset();
        blockHitEngine.reset();
    }

    // ===== gates =====

    private static boolean isBreakingBlock(Minecraft mc) {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    /** Attack-half gate: weapons filter plus the Don't Break Blocks rule. */
    private boolean canAttack(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean allowed = !weaponsOnly.on() || GameplayUtil.hasRawUnbreakingEnchant(held)
                || (allowTools.on() && GameplayUtil.isTool(held));
        if (!allowed) return false;
        if (breakBlocks.on() && isBreakingBlock(mc) && !hasValidTarget(mc)) {
            GameType gameType = mc.playerController.getCurrentGameType();
            return gameType != GameType.SURVIVAL && gameType != GameType.CREATIVE;
        }
        return true;
    }

    private boolean isValidTarget(Minecraft mc, EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.thePlayer.ridingEntity) return false;
        if (player == mc.getRenderViewEntity() || player == mc.getRenderViewEntity().ridingEntity) return false;
        if (player.deathTime > 0) return false;
        float borderSize = player.getCollisionBorderSize();
        AxisAlignedBB box = player.getEntityBoundingBox().expand(
                borderSize + hitBoxHorizontal.value(),
                borderSize + hitBoxVertical.value(),
                borderSize + hitBoxHorizontal.value());
        return GameplayUtil.rayTraceIntersects(box, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, range.value());
    }

    private boolean hasValidTarget(Minecraft mc) {
        @SuppressWarnings("unchecked")
        java.util.Iterator<EntityPlayer> iterator = mc.theWorld.playerEntities.iterator();
        while (iterator.hasNext()) {
            if (isValidTarget(mc, iterator.next())) return true;
        }
        return false;
    }

    /** Block-hit half gate: optional sword filter, and when "Require Auto Clicker" is on the attack half must be actively clicking. */
    private boolean canBlockHit(Minecraft mc) {
        if (swordOnly.on() && !GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return false;
        return !requireAutoClicker.on() || attackHalfActive(mc);
    }

    /** The attack half is "active" while it actually clicks: attack key held and the attack gate passed. */
    private boolean attackHalfActive(Minecraft mc) {
        return mc.gameSettings.keyBindAttack.isKeyDown() && canAttack(mc);
    }

    // ===== tick scheduling (millisecond precision, Vape's background worker as a client tick scheduler) =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        updateJitter();

        // One-tick restore of the synthetic clicks; skipped inside GUIs,
        // where vanilla has already unpressed the bindings.
        if (attackRestorePending) {
            attackRestorePending = false;
            if (mc.currentScreen == null) {
                GameplayUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
            }
        }
        if (blockHitRestorePending) {
            blockHitRestorePending = false;
            if (mc.currentScreen == null) {
                GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
            }
        }

        if (mc.currentScreen != null) {
            nextAttackAt = 0L;
            nextBlockHitAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();

        // attack half
        if (isEnabled() && mc.gameSettings.keyBindAttack.isKeyDown() && !mc.thePlayer.isUsingItem() && canAttack(mc)) {
            if (nextAttackAt == 0L) nextAttackAt = now + attackEngine.nextDelay();
            if (now >= nextAttackAt) {
                fireAttack(mc);
                nextAttackAt = Math.max(now, nextAttackAt) + attackEngine.nextDelay();
            }
        } else {
            nextAttackAt = 0L;
        }

        // block hit half
        if (isEnabled() && blockHit.on() && canBlockHit(mc)) {
            if (nextBlockHitAt == 0L) nextBlockHitAt = now + blockHitEngine.nextDelay();
            if (now >= nextBlockHitAt) {
                fireBlockHit(mc);
                nextBlockHitAt = Math.max(now, nextBlockHitAt) + blockHitEngine.nextDelay();
            }
        } else {
            nextBlockHitAt = 0L;
        }
    }

    private void fireAttack(Minecraft mc) {
        int attackCode = mc.gameSettings.keyBindAttack.getKeyCode();
        // Same synthetic single-shot idiom as the block hit half: keep the held
        // path off, fire once via isPressed(), restore from raw input next tick.
        KeyBinding.setKeyBindState(attackCode, false);
        KeyBinding.onTick(attackCode);
        ClickCounter.register(0);
        generateJitter();
        attackRestorePending = true;
    }

    private void fireBlockHit(Minecraft mc) {
        int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        // Same synthetic right click idiom: keep the held path off, fire a
        // single shot via isPressed().
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
        blockHitRestorePending = true;
    }

    /** Physical clicks participate in the same CPS cadence (mirrors Leader-Lite's LeftClickMouseEvent). */
    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled() || nextAttackAt == 0L) return;
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        nextAttackAt = Math.max(nextAttackAt, System.currentTimeMillis() + attackEngine.nextDelay());
    }

    // ===== randomization engine (Vape ClickEngine.calculateNextClickDelay) =====

    /**
     * One clicker half's timing state. Each half is constructed with its own
     * settings, so the left (attack) and right (block hit) halves run fully
     * independent CPS ranges, randomization modes and delay intervals.
     */
    private final class TimingEngine {
        private final Random random = new Random();
        private final ClickerTiming timing = new ClickerTiming();
        private final NumberSetting minCps;
        private final NumberSetting maxCps;
        private final ChoiceSetting<RandomizationMode> mode;
        private final NumberSetting minDelay;
        private final NumberSetting maxDelay;

        // Extra state (Vape calculateLegacyRandomizedDelay)
        private boolean burstActive;
        private int burstLength;
        private int burstClickCount;
        private boolean fastPhaseActive = true;
        private int fastPhaseClickCount;
        private int slowPhaseLength;
        private int slowPhaseClickCount;
        private int configuredFastPhaseLength;
        private long lastClickDelayMillis;

        TimingEngine(NumberSetting minCps, NumberSetting maxCps,
                ChoiceSetting<RandomizationMode> mode,
                NumberSetting minDelay, NumberSetting maxDelay) {
            this.minCps = minCps;
            this.maxCps = maxCps;
            this.mode = mode;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        long nextDelay() {
            // "Delay" randomization: a plain millisecond interval between the
            // half's Delay Min and Delay Max, ignoring the CPS engine entirely.
            // Equal bounds degenerate to a strictly fixed interval.
            if (mode.is(RandomizationMode.DELAY)) {
                int lo = Math.max(1, minDelay.intValue());
                int hi = Math.max(maxDelay.intValue(), lo);
                return lo + random.nextInt(hi - lo + 1);
            }

            int min = minCps.intValue();
            int max = Math.max(maxCps.intValue(), min);
            int size = max - min;
            int selectedCps = size <= 0 ? min : random.nextInt(size) + min + 1;

            if (mode.is(RandomizationMode.NORMAL)) {
                return 1000L / selectedCps;
            }
            if (selectedCps == 0) selectedCps = 1;
            if (mode.is(RandomizationMode.EXTRA)) {
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

        void reset() {
            burstActive = false;
            burstLength = 0;
            burstClickCount = 0;
            fastPhaseActive = true;
            fastPhaseClickCount = 0;
            slowPhaseLength = 0;
            slowPhaseClickCount = 0;
            configuredFastPhaseLength = 0;
            lastClickDelayMillis = 0L;
            timing.reset();
        }
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

    private void resetJitter() {
        horizontalJitterOffset = 0.0D;
        verticalJitterOffset = 0.0D;
        remainingJitterSteps = 0.0D;
        pendingYawJitter = 0.0F;
        pendingPitchJitter = 0.0F;
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
