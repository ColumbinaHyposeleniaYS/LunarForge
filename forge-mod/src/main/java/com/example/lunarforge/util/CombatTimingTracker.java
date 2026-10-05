package com.example.lunarforge.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ported from Vape v4 (gg.vape.combat.AttackPacketTimingTracker).
 *
 * Tracks how long it takes for our own attacks to confirm as damage on the
 * victim (attack packet sent -> hurt status packet received), which behaves
 * like a measured hit delay of the connection. The average is consumed by
 * Block Hit Mode:
 * - expectedHurtTimeTicks() gates when an anticipated hit can no longer land
 *   (Manual/Predict/Lag release their block once passed),
 * - averageHitDelay() feeds Predict's "Include ping" early window.
 *
 * LunarForge difference: Vape also accepted the victim's swing animation
 * packet as a damage confirmation; here the authoritative hurt status packet
 * (S19 opcode 2) is used on its own. Hurt confirmations are tapped in
 * {@link com.example.lunarforge.module.CombatHooks#status}; attack timestamps
 * are recorded from Block Hit Mode's AttackEntityEvent handler (same tick as
 * Vape's packet tap).
 */
public final class CombatTimingTracker {
    public static final CombatTimingTracker INSTANCE = new CombatTimingTracker();

    private static final int MAX_SAMPLES = 20;

    private final Deque<Long> hitDelays = new ArrayDeque<Long>();
    private long lastHitTime;
    private long lastAttackTime;
    private int targetId = -1;

    private CombatTimingTracker() {}

    /**
     * Called for every outgoing C02 ATTACK packet. Mirrors Vape's
     * recordAttack: a hit timestamp is only recorded when the target was not
     * already hurt (so the next damage confirmation is attributable to us)
     * and the previous attack is not still pending confirmation.
     */
    public synchronized void onAttackPacketSent(int targetEntityId, boolean targetNotHurt) {
        long now = System.currentTimeMillis();
        if (targetEntityId >= 0 && targetNotHurt && now - lastHitTime > 400L
                && now - lastAttackTime > averageHitDelay() * 2L) {
            lastHitTime = now;
        }
        targetId = targetEntityId;
        lastAttackTime = now;
    }

    /** Called when a hurt status packet (opcode 2) confirms damage on an entity. */
    public synchronized void onDamageConfirmed(int entityId) {
        if (entityId != targetId) return;
        long delay = System.currentTimeMillis() - lastHitTime;
        if (delay >= 500L) return;
        hitDelays.addLast(delay);
        if (hitDelays.size() >= MAX_SAMPLES) hitDelays.removeFirst();
    }

    public synchronized long averageHitDelay() {
        if (hitDelays.isEmpty()) return 0L;
        long total = 0L;
        for (long delay : hitDelays) total += delay;
        return total / hitDelays.size();
    }

    /** Ticks we still expect an anticipated hit to arrive within. */
    public synchronized int expectedHurtTimeTicks() {
        return (int) Math.floor(averageHitDelay() / 50.0D);
    }

    public synchronized void reset() {
        hitDelays.clear();
        lastHitTime = 0L;
        lastAttackTime = 0L;
        targetId = -1;
    }
}
