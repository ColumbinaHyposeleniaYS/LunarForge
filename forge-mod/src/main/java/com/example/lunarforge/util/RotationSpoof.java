package com.example.lunarforge.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Minimal silent-rotation bridge for the Leader-Lite Scaffold port.
 *
 * Leader swaps the client rotation right before EntityPlayerSP sends its C03
 * and restores it right after; LunarForge has no mixin infrastructure for
 * that. Instead the scaffold claims a rotation here during the player tick
 * and sends one explicit C05 at PlayerTickEvent END, while this class - called
 * from the PacketHooks send injection - absorbs the natural rotation packets
 * vanilla sends afterwards so the server only ever sees the claimed angles:
 * rotation-only C05 packets are dropped (the explicit C05 already reported
 * the claim), position+rotation C06 packets are re-sent with the claimed
 * angles and their original position, and rotation-less packets (C04, bare
 * C03) pass through untouched.
 *
 * 1.8.9 note: C05PacketPlayerLook and C06PacketPlayerPosLook are inner
 * classes of C03PacketPlayer, which exposes no rotation setters - hence the
 * absorb-and-resend instead of rewriting the packets in place.
 */
public final class RotationSpoof {
    private RotationSpoof() {}

    private static boolean claimed;
    private static float claimYaw;
    private static float claimPitch;
    private static int claimPriority = -1;
    private static float smoothedYaw;
    private static float lastReportedYaw;
    private static float lastReportedPitch;
    private static boolean lastReportedValid;
    private static boolean sendingRotation;

    /** Expires last tick's claim; called once per client tick before modules claim again. */
    public static void newTick() {
        claimed = false;
        claimPriority = -1;
    }

    public static void clear() {
        claimed = false;
        claimPriority = -1;
        smoothedYaw = 0.0F;
        lastReportedValid = false;
        sendingRotation = false;
    }

    /** Leader UpdateEvent.setRotation: the higher priority claim of a tick wins. */
    public static void claim(float yaw, float pitch, int priority) {
        if (claimed && claimPriority > priority) return;
        claimYaw = yaw;
        claimPitch = pitch;
        claimPriority = priority;
        claimed = true;
        smoothedYaw = yaw;
    }

    /** Leader UpdateEvent.setPervRotation: yaw used for the strafe fix. */
    public static void setSmoothedYaw(float yaw) {
        smoothedYaw = yaw;
    }

    public static float smoothedYaw() {
        return smoothedYaw;
    }

    public static boolean hasClaim() {
        return claimed;
    }

    public static float claimedYaw() {
        return claimYaw;
    }

    public static float claimedPitch() {
        return claimPitch;
    }

    /** Leader event.getYaw(): the rotation the server currently believes. */
    public static float lastReportedYaw() {
        if (!lastReportedValid) {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        }
        return lastReportedYaw;
    }

    /** Leader event.getPitch(). */
    public static float lastReportedPitch() {
        if (!lastReportedValid) {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0.0F;
        }
        return lastReportedPitch;
    }

    /** One explicit rotation packet per tick (called at PlayerTickEvent END). */
    public static void sendRotationPacket() {
        if (!claimed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        sendingRotation = true;
        try {
            mc.thePlayer.sendQueue.addToSendQueue(
                    new C03PacketPlayer.C05PacketPlayerLook(claimYaw, claimPitch, mc.thePlayer.onGround));
        } finally {
            sendingRotation = false;
        }
    }

    /**
     * Called from PacketHooks for every outgoing packet. Returns true to
     * absorb the packet (natural rotation packets while a claim is active).
     */
    public static boolean onSendPacket(Packet packet) {
        if (!(packet instanceof C03PacketPlayer)) return false;
        if (sendingRotation) {
            trackReported(claimYaw, claimPitch);
            return false;
        }
        if (!claimed) {
            trackReported(currentYaw(), currentPitch());
            return false;
        }
        if (packet instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
            // Position + real rotation: re-send with the claimed angles so the position update survives.
            // The 1.8.9 C06 inner class carries no readable position getters, but vanilla built it from
            // the player state this very tick, so the live player values are identical.
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
                sendingRotation = true;
                try {
                    mc.thePlayer.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(
                            mc.thePlayer.posX, mc.thePlayer.getEntityBoundingBox().minY, mc.thePlayer.posZ,
                            claimYaw, claimPitch, mc.thePlayer.onGround));
                } finally {
                    sendingRotation = false;
                }
            }
            trackReported(claimYaw, claimPitch);
            return true;
        }
        if (packet instanceof C03PacketPlayer.C05PacketPlayerLook) {
            // Rotation-only: the explicit C05 of this tick already reported the claim.
            trackReported(claimYaw, claimPitch);
            return true;
        }
        // C04 (position only) and bare C03 (no rotation flag) carry no angles.
        return false;
    }

    private static float currentYaw() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
    }

    private static float currentPitch() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0.0F;
    }

    private static void trackReported(float yaw, float pitch) {
        lastReportedYaw = yaw;
        lastReportedPitch = pitch;
        lastReportedValid = true;
    }
}
