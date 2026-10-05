package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.ClickCounter;
import com.example.lunarforge.util.CombatTimingTracker;
import com.example.lunarforge.util.GameplayUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Ported from Vape v4 (gg.vape.module.combat.BlockHit and the
 * gg.vape.module.combat.blockhit.* mode classes), placed in the legit
 * category as requested. Distinct from the Leader-Lite "Block Hit" module.
 *
 * Manual: on every left click that passes the chance roll the use key is
 * pressed for ~50 ms (blocks per second = your CPS x chance), releasing once
 * the anticipated hit can no longer land. Skipped while Auto Clicker runs.
 * Predict: tracks your own damage intervals; reactively blocks while hurt
 * resistant time enters the early window, and once a stable damage pattern
 * exists blocks ahead of the expected next hit. "Include ping" adds the
 * measured hit delay ({@link CombatTimingTracker}) to the window.
 * Auto: legacy AutoClicker autoblock - every third consecutive attack with
 * the crosshair on an entity sends a block right after the swing.
 * Lag: runs a block cycle near a target and, whenever the stop-blocking
 * packet is sent, holds it back and buffers every following packet for a
 * random delay so the server keeps seeing you block longer.
 *
 * Vape's SubModule per-mode settings became mode-conditional entries
 * (hideIf), its RandomValue ranges became Min/Max setting pairs, and its
 * mouse-event cancellation became an immediate key unpress (1.8.9 Forge
 * mouse events are not cancelable). Keep-alive packets are never buffered.
 */
public final class ModuleBlockHitMode extends Module {

    public enum HitMode implements ChoiceSetting.Option {
        MANUAL("Manual"), PREDICT("Predict"), AUTO("Auto"), LAG("Lag");

        private final String label;

        HitMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    // ===== shared settings =====
    private final ChoiceSetting<HitMode> mode = choice("mode", HitMode.MANUAL);
    private final BoolSetting requireMouseDown = bool("requireMouseDown", true).label(() -> "Require Mouse Down");
    private final BoolSetting ignoreManualBlock = bool("ignoreManualBlock", true).label(() -> "Ignore Manual Block");
    private final NumberSetting targetAngle = integer("targetAngle", 90, 0, 360).label(() -> "Angle");
    private final NumberSetting targetDistance = decimal("targetDistance", 5.0F, 0.0F, 6.0F).label(() -> "Distance");

    // ===== Manual (Vape RandomValue range [70, 90] of [0, 100]) =====
    private final NumberSetting chanceMin = integer("chanceMin", 70, 0, 100).label(() -> "Chance Min %");
    private final NumberSetting chanceMax = integer("chanceMax", 90, 0, 100).label(() -> "Chance Max %");

    // ===== Predict =====
    private final NumberSetting maximumHurtTime = integer("maximumHurtTime", 50, 0, 500).label(() -> "Max Hurt Time (ms)");
    private final BoolSetting includePing = bool("includePing", true).label(() -> "Include Ping");
    private final NumberSetting holdAfter = integer("holdAfter", 2, 0, 10).label(() -> "Hold After (ticks)");

    // ===== Lag (Vape RandomValue range [50, 100] of [0, 500]) =====
    private final NumberSetting delayMin = integer("delayMin", 50, 0, 500).label(() -> "Delay Min (ms)");
    private final NumberSetting delayMax = integer("delayMax", 100, 0, 500).label(() -> "Delay Max (ms)");

    // ===== Manual state =====
    private long manualReleaseTime;

    // ===== Predict state =====
    private static final int DAMAGEABLE_HURT_RESISTANT_TIME = 10;
    private static final int DAMAGE_INTERVAL_CAPACITY = 8;
    private static final int PREDICTION_SAMPLE_COUNT = 3;
    private static final long MIN_DAMAGE_INTERVAL_MILLIS = 250L;
    private static final long MAX_DAMAGE_INTERVAL_MILLIS = 1500L;
    private static final long TICK_MILLIS = 50L;
    private final long[] damageIntervals = new long[DAMAGE_INTERVAL_CAPACITY];
    private int damageIntervalCount;
    private int nextDamageIntervalIndex;
    private long lastDamageTime;
    private boolean damageObserved;
    private boolean holdTimerStarted;
    private long holdUntil;
    private boolean pendingDamageRelease;

    // ===== Auto state =====
    private int targetStreak = 1;
    private boolean autoPendingBlock;
    private int autoReleaseTick;

    // ===== Lag state =====
    private final Deque<Packet> queuedPackets = new ArrayDeque<Packet>();
    private int delayMillis;
    private boolean blockCycleCompleted;
    private long bufferTimerStart;
    private boolean bufferingPackets;
    private NetworkManager bufferedManager;
    private boolean flushing;

    // ===== shared block state =====
    private boolean blocking;
    private boolean useKeyForced;
    private final Random random = new Random();

    public ModuleBlockHitMode() {
        super("BLOCK_HIT_MODE", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, requireMouseDown);
            s.add(targetAngle, targetDistance).hideIf(() -> !mode.is(HitMode.PREDICT) && !mode.is(HitMode.LAG));
            s.add(ignoreManualBlock).hideIf(() -> !mode.is(HitMode.LAG));
            s.add(chanceMin, chanceMax).hideIf(() -> !mode.is(HitMode.MANUAL));
            s.group(maximumHurtTime, g -> g.add(includePing, holdAfter)).hideIf(() -> !mode.is(HitMode.PREDICT));
            s.add(delayMin, delayMax).hideIf(() -> !mode.is(HitMode.LAG));
        });
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        // With a GUI open vanilla has already unpressed the binding, and restoring
        // from the raw keyboard state would press use for a right-click held in the GUI.
        if (mc.gameSettings != null && mc.currentScreen == null) {
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        blocking = false;
        useKeyForced = false;
        manualReleaseTime = 0L;
        damageIntervalCount = 0;
        nextDamageIntervalIndex = 0;
        lastDamageTime = 0L;
        damageObserved = false;
        holdTimerStarted = false;
        holdUntil = 0L;
        pendingDamageRelease = false;
        targetStreak = 1;
        autoPendingBlock = false;
        autoReleaseTick = 0;
        if (bufferingPackets) flushPackets();
        bufferingPackets = false;
        blockCycleCompleted = false;
        queuedPackets.clear();
        bufferedManager = null;
        flushing = false;
    }

    // ===== helpers =====

    private static boolean isHoldingSword(Minecraft mc) {
        if (mc.currentScreen != null) return false;
        return GameplayUtil.isSword(mc.thePlayer.getHeldItem());
    }

    private boolean useButtonDown(Minecraft mc) {
        return GameplayUtil.physicalDown(mc.gameSettings.keyBindUseItem);
    }

    private EntityLivingBase findTarget(Minecraft mc) {
        return GameplayUtil.findTarget(mc, targetDistance.value(), targetAngle.intValue() / 2.0F);
    }

    /** Vape RandomValue.getRandomValue: uniform double in [min, max]. */
    private double randomInRange(int lo, int hi) {
        int min = Math.min(lo, hi);
        int max = Math.max(lo, hi);
        return min + (max - min) * random.nextDouble();
    }

    /** Same effect as Vape's KeyBindingInputState synthetic right click, with CPS counting. */
    private void forceUseKey(Minecraft mc) {
        int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (!useKeyForced && !mc.gameSettings.keyBindUseItem.isKeyDown()) ClickCounter.register(1);
        KeyBinding.setKeyBindState(useCode, true);
        useKeyForced = true;
    }

    private void releaseUseKey(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        useKeyForced = false;
    }

    private void setBlocking(Minecraft mc, boolean value) {
        if (blocking == value) return;
        blocking = value;
        if (value) forceUseKey(mc); else releaseUseKey(mc);
    }

    private boolean bufferElapsed(long ms) {
        return System.currentTimeMillis() - bufferTimerStart >= ms;
    }

    /** Vape checks LeftClicker and SilentAura; LunarForge's equivalent clicker is Auto Clicker. */
    private static boolean isAutoClickerActive() {
        Module autoClicker = ModuleManager.get("auto_clicker");
        return autoClicker != null && autoClicker.isEnabled();
    }

    // ===== events =====

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        if (!Mouse.getEventButtonState()) return;
        int keyCode = -100 + Mouse.getEventButton();

        if (mode.is(HitMode.MANUAL)) {
            if (keyCode != mc.gameSettings.keyBindAttack.getKeyCode()) return;
            if (isAutoClickerActive()) return;
            if (!shouldBlockManual(mc)) return;
            if (!blocking && !mc.thePlayer.isUsingItem()) {
                setBlocking(mc, true);
                manualReleaseTime = System.currentTimeMillis() + 50L;
            }
            return;
        }

        if (mode.is(HitMode.AUTO) || mode.is(HitMode.PREDICT)) {
            // Vape cancels the use-item mouse press while attacking; 1.8.9 Forge
            // mouse events are not cancelable, so unpress the binding instead -
            // vanilla never reaches rightClickMouse for this press.
            if (keyCode != mc.gameSettings.keyBindUseItem.getKeyCode()) return;
            if (!requireMouseDown.on()) return;
            if (!GameplayUtil.physicalDown(mc.gameSettings.keyBindAttack)) return;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
    }

    private boolean shouldBlockManual(Minecraft mc) {
        if (!isHoldingSword(mc)) return false;
        if (requireMouseDown.on() && !useButtonDown(mc)) return false;
        return randomInRange(chanceMin.intValue(), chanceMax.intValue()) >= Math.random() * 100.0D;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer) return;
        // Vape records attack timing from the outgoing C02 ATTACK packet; the
        // AttackEntityEvent fires in the same tick, milliseconds apart.
        CombatTimingTracker.INSTANCE.onAttackPacketSent(event.target.getEntityId(),
                !(event.target instanceof EntityLivingBase) || ((EntityLivingBase) event.target).hurtTime == 0);
        if (!mode.is(HitMode.AUTO)) return;
        if (!shouldBlockSwordUse(mc)) return;
        autoPendingBlock = true;
    }

    /** Vape AutoBlockHitMode.shouldBlockSwordUse: block every third consecutive targeted hit. */
    private boolean shouldBlockSwordUse(Minecraft mc) {
        if (requireMouseDown.on() && !useButtonDown(mc)) return false;
        if (!GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return false;
        // Vape traces with extended reach; 6 blocks approximates that here.
        boolean targetingEntity = false;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) continue;
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity == mc.thePlayer || entity.isDead || entity.getHealth() <= 0.0F) continue;
            if (mc.thePlayer.getDistanceToEntity(entity) > 6.0D) continue;
            if (GameplayUtil.rayTraceIntersects(entity.getEntityBoundingBox(), mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch, 6.0D)) {
                targetingEntity = true;
                break;
            }
        }
        boolean shouldBlock = true;
        if (targetStreak != 1 || !targetingEntity) shouldBlock = false;
        if (targetStreak >= 3) targetStreak = 0;
        targetStreak = targetingEntity ? ++targetStreak : 1;
        return shouldBlock;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        switch (mode.get()) {
            case MANUAL:
                onManualTick(mc);
                break;
            case PREDICT:
                onPredictTick(mc);
                break;
            case AUTO:
                onAutoTick(mc);
                break;
            case LAG:
                onLagTick(mc);
                break;
        }
    }

    // ===== Manual =====

    private void onManualTick(Minecraft mc) {
        if (isAutoClickerActive()) return;
        boolean hurtTimeExpired = mc.thePlayer.hurtTime > CombatTimingTracker.INSTANCE.expectedHurtTimeTicks() + 1;
        boolean releaseTimeReached = manualReleaseTime > 0L && System.currentTimeMillis() >= manualReleaseTime;
        if (hurtTimeExpired || releaseTimeReached) {
            manualReleaseTime = 0L;
            setBlocking(mc, false);
        }
    }

    // ===== Predict =====

    /** Packet-precise counterpart of Vape's onDamaged (hurt status of the local player). */
    public void onSelfDamaged() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc.thePlayer == null || !mode.is(HitMode.PREDICT)) return;
        damageObserved = true;
        holdTimerStarted = false;
        holdUntil = 0L;
        recordDamageInterval();
        pendingDamageRelease = true;
    }

    private void onPredictTick(Minecraft mc) {
        if (pendingDamageRelease) {
            pendingDamageRelease = false;
            setBlocking(mc, false);
        }
        if (!canPredict(mc)) {
            resetPrediction(mc);
            return;
        }
        int hurtResistantTime = mc.thePlayer.hurtResistantTime;
        if (damageObserved && hasStableDamagePattern()) {
            updatePredictedBlocking(mc, hurtResistantTime);
        } else {
            updateReactiveBlocking(mc, hurtResistantTime);
        }
    }

    private boolean canPredict(Minecraft mc) {
        if (!isHoldingSword(mc)) return false;
        if (requireMouseDown.on() && !useButtonDown(mc)) return false;
        return GameplayUtil.findTarget(mc, 5.0D, 45.0F) != null;
    }

    private void updateReactiveBlocking(Minecraft mc, int hurtResistantTime) {
        if (hurtResistantTime > DAMAGEABLE_HURT_RESISTANT_TIME) {
            holdTimerStarted = false;
            holdUntil = 0L;
            setBlocking(mc, hurtResistantTime <= DAMAGEABLE_HURT_RESISTANT_TIME + earlyWindowTicks());
            return;
        }
        if (blocking) releaseAfterConfiguredHold(mc);
    }

    private void updatePredictedBlocking(Minecraft mc, int hurtResistantTime) {
        long now = System.currentTimeMillis();
        long expectedDamageTime = lastDamageTime + averageDamageInterval();
        long earlyWindow = earlyWindowMillis();
        long lateWindow = holdAfter.intValue() * TICK_MILLIS;
        boolean insidePredictionWindow = now >= expectedDamageTime - earlyWindow
                && now <= expectedDamageTime + lateWindow;
        boolean canTakeDamageSoon = hurtResistantTime
                <= DAMAGEABLE_HURT_RESISTANT_TIME + earlyWindowTicks();
        setBlocking(mc, insidePredictionWindow && canTakeDamageSoon);
    }

    private void releaseAfterConfiguredHold(Minecraft mc) {
        int holdTicks = holdAfter.intValue();
        if (holdTicks <= 0) {
            setBlocking(mc, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!holdTimerStarted) {
            holdTimerStarted = true;
            holdUntil = now + holdTicks * TICK_MILLIS;
        }
        if (now >= holdUntil) setBlocking(mc, false);
    }

    private void recordDamageInterval() {
        long now = System.currentTimeMillis();
        if (lastDamageTime > 0L) {
            long interval = now - lastDamageTime;
            if (interval >= MIN_DAMAGE_INTERVAL_MILLIS && interval <= MAX_DAMAGE_INTERVAL_MILLIS) {
                damageIntervals[nextDamageIntervalIndex] = interval;
                nextDamageIntervalIndex = (nextDamageIntervalIndex + 1) % DAMAGE_INTERVAL_CAPACITY;
                if (damageIntervalCount < DAMAGE_INTERVAL_CAPACITY) ++damageIntervalCount;
            } else {
                damageIntervalCount = 0;
                nextDamageIntervalIndex = 0;
            }
        }
        lastDamageTime = now;
    }

    private long averageDamageInterval() {
        int sampleCount = Math.min(damageIntervalCount, PREDICTION_SAMPLE_COUNT);
        if (sampleCount <= 0) return 0L;
        long total = 0L;
        for (int offset = 0; offset < sampleCount; ++offset) {
            int index = nextDamageIntervalIndex - 1 - offset;
            if (index < 0) index += DAMAGE_INTERVAL_CAPACITY;
            total += damageIntervals[index];
        }
        return total / sampleCount;
    }

    private long earlyWindowMillis() {
        long window = maximumHurtTime.intValue();
        if (includePing.on()) window += CombatTimingTracker.INSTANCE.averageHitDelay();
        return window + TICK_MILLIS;
    }

    private int earlyWindowTicks() {
        return (int) Math.ceil(earlyWindowMillis() / (double) TICK_MILLIS);
    }

    private boolean hasStableDamagePattern() {
        return damageIntervalCount >= PREDICTION_SAMPLE_COUNT && lastDamageTime > 0L;
    }

    private void resetPrediction(Minecraft mc) {
        damageObserved = false;
        holdTimerStarted = false;
        holdUntil = 0L;
        lastDamageTime = 0L;
        damageIntervalCount = 0;
        nextDamageIntervalIndex = 0;
        setBlocking(mc, false);
    }

    // ===== Auto =====

    private void onAutoTick(Minecraft mc) {
        if (autoPendingBlock) {
            autoPendingBlock = false;
            forceUseKey(mc);
            autoReleaseTick = 2;
            return;
        }
        if (autoReleaseTick > 0 && --autoReleaseTick == 0) setBlocking(mc, false);
    }

    // ===== Lag =====

    private void onLagTick(Minecraft mc) {
        if (!Display.isActive()) return;
        EntityLivingBase target = findTarget(mc);
        if (target != null) {
            boolean shouldBlock = isHoldingSword(mc);
            if (requireMouseDown.on() && !useButtonDown(mc)) shouldBlock = false;
            if (mc.thePlayer.hurtTime > CombatTimingTracker.INSTANCE.expectedHurtTimeTicks() + 1) {
                setBlocking(mc, false);
                blockCycleCompleted = false;
                return;
            }
            if (shouldBlock) {
                if (bufferingPackets && bufferElapsed(delayMillis - 50L)) {
                    setBlocking(mc, true);
                    return;
                }
                if (!blockCycleCompleted) {
                    if (!blocking) setBlocking(mc, true);
                    else {
                        setBlocking(mc, false);
                        blockCycleCompleted = true;
                    }
                } else if (!blocking && !mc.thePlayer.isUsingItem()) {
                    blockCycleCompleted = false;
                }
            } else if (blocking) {
                setBlocking(mc, false);
                blockCycleCompleted = false;
            }
            return;
        }
        if (isHoldingSword(mc) && requireMouseDown.on() && ignoreManualBlock.on() && !bufferingPackets
                && useButtonDown(mc) && mc.thePlayer.isUsingItem()) {
            // No target nearby: stop the manual block the user is holding
            // (Vape sends a synthetic right-button-up here).
            releaseUseKey(mc);
            blocking = false;
            blockCycleCompleted = false;
            return;
        }
        if (mc.thePlayer.hurtTime > CombatTimingTracker.INSTANCE.expectedHurtTimeTicks() + 1) {
            setBlocking(mc, false);
            blockCycleCompleted = false;
            return;
        }
        if (blocking) {
            setBlocking(mc, false);
            blockCycleCompleted = false;
        }
    }

    /**
     * Called from {@link com.example.lunarforge.module.PacketHooks} at the head
     * of NetworkManager.sendPacket. Returns true to absorb the packet.
     */
    public boolean onSendPacket(NetworkManager manager, Packet packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || !mode.is(HitMode.LAG)) return false;
        if (flushing) return false;
        if (packet instanceof C00PacketKeepAlive) return false;
        if (!mc.isCallingFromMinecraftThread()) return false;
        if (bufferingPackets) {
            boolean targetLost = findTarget(mc) == null || !isHoldingSword(mc);
            if (targetLost || bufferElapsed(delayMillis)) {
                flushPackets();
                bufferingPackets = false;
                blockCycleCompleted = false;
            } else {
                queuedPackets.addLast(packet);
                return true;
            }
            return false;
        }
        if (isReleaseUseItemPacket(packet)) {
            queuedPackets.addLast(packet);
            bufferingPackets = true;
            bufferedManager = manager;
            delayMillis = (int) randomInRange(delayMin.intValue(), delayMax.intValue());
            bufferTimerStart = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private static boolean isReleaseUseItemPacket(Packet packet) {
        if (!(packet instanceof C07PacketPlayerDigging)) return false;
        return ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM;
    }

    /** Re-sends the buffered packets through the original connection (hook passes through while flushing). */
    private void flushPackets() {
        NetworkManager manager = bufferedManager;
        bufferedManager = null;
        if (manager == null) {
            queuedPackets.clear();
            return;
        }
        flushing = true;
        try {
            while (!queuedPackets.isEmpty()) {
                Packet packet = queuedPackets.pollFirst();
                manager.sendPacket(packet);
            }
        } finally {
            flushing = false;
        }
    }
}
