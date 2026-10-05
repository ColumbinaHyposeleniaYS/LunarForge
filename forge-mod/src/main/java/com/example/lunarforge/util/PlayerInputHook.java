package com.example.lunarforge.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;

/**
 * Wraps the local player's {@link MovementInput} so modules can modify the
 * movement input after vanilla reads the keys but before the movement is
 * applied - the same timing Leader-Lite's MoveInputEvent provides (its mixin
 * injects right after MovementInput.updatePlayerMoveState, and 1.8.9 Forge
 * has no equivalent event).
 *
 * Two modifier slots keep the application order deterministic: the scaffold
 * registers as primary (jump/sneak injection and the silent-rotation strafe
 * fix) and Stuck registers as override (freezing zeroes everything last).
 * The wrapper re-attaches itself whenever the player instance changes
 * (respawn, dimension change).
 */
public final class PlayerInputHook extends MovementInput {
    public interface Modifier {
        void modify(MovementInput input);
    }

    private static PlayerInputHook active;
    private final List<Modifier> primaries = new ArrayList<Modifier>();
    private final List<Modifier> overrides = new ArrayList<Modifier>();

    private PlayerInputHook() {}

    /** Makes sure the wrapper sits on the current player and the modifiers are registered. */
    public static synchronized void ensureAttached(Modifier primary, Modifier override) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (active == null) {
            active = new PlayerInputHook();
            active.takeOver(mc.thePlayer.movementInput);
        } else if (mc.thePlayer.movementInput != active) {
            active.takeOver(mc.thePlayer.movementInput);
        }
        if (primary != null && !active.primaries.contains(primary)) active.primaries.add(primary);
        if (override != null && !active.overrides.contains(override)) active.overrides.add(override);
    }

    /** Unregisters modifiers; restores vanilla input once nothing needs it anymore. */
    public static synchronized void release(Modifier primary, Modifier override) {
        PlayerInputHook hook = active;
        if (hook == null) return;
        if (primary != null) hook.primaries.remove(primary);
        if (override != null) hook.overrides.remove(override);
        if (hook.primaries.isEmpty() && hook.overrides.isEmpty()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && mc.thePlayer.movementInput == hook) {
                mc.thePlayer.movementInput = hook.backing;
            }
            active = null;
        }
    }

    private MovementInput backing;

    private void takeOver(MovementInput vanillaInput) {
        backing = vanillaInput;
        Minecraft mc = Minecraft.getMinecraft();
        mc.thePlayer.movementInput = this;
    }

    @Override
    public void updatePlayerMoveState() {
        PlayerInputHook hook = active;
        if (hook == null || hook.backing == null) return;
        hook.backing.updatePlayerMoveState();
        hook.moveStrafe = hook.backing.moveStrafe;
        hook.moveForward = hook.backing.moveForward;
        hook.jump = hook.backing.jump;
        hook.sneak = hook.backing.sneak;
        for (Modifier modifier : hook.primaries) modifier.modify(hook);
        for (Modifier modifier : hook.overrides) modifier.modify(hook);
    }
}
