package com.example.lunarforge.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C05PacketPlayerLook;
import net.minecraft.network.play.client.C06PacketPlayerPosLook;

/**
 * Minimal silent-rotation bridge for the Leader-Lite Scaffold port.
 *
 * Leader swaps the client rotation right before EntityPlayerSP sends its C03
 * and restores it right after; LunarForge has no mixin infrastructure for
 * that. Instead a claim made during the player tick rewrites every outgoing
 * rotation packet (C03/C05/C06) inside the PacketHooks send injection, and
 * the scaffold additionally sends one explicit C05 per tick at
 * PlayerTickEvent END, so the server always receives the spoofed angles even
 * when vanilla decides it does not need a rotation packet. The claim lives
 * until the next client tick, so it also covers the natural C03 sent at the
 * end of the same tick.
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

    /** One explicit rotation packet per tick so silent angles never go stale. */
    public static void sendRotationPacket() {
        if (!claimed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return;
        mc.thePlayer.sendQueue.addToSendQueue(new C05PacketPlayerLook(claimYaw, claimPitch, mc.thePlayer.onGround));
    }

    /**
     * Called from PacketHooks for every outgoing packet; rewrites rotation
     * packets while a claim is active and keeps the last-reported bookkeeping
     * the scaffold rotates from. Never absorbs anything.
     */
    public static void onSendPacket(Packet packet) {
        if (packet instanceof C05PacketPlayerLook) {
            if (claimed) ((C05PacketPlayerLook) packet).setRotation(claimYaw, claimPitch);
            trackReported(claimed ? claimYaw : currentYaw(), claimed ? claimPitch : currentPitch());
        } else if (packet instanceof C06PacketPlayerPosLook) {
            if (claimed) ((C06PacketPlayerPosLook) packet).setRotation(claimYaw, claimPitch);
            trackReported(claimed ? claimYaw : currentYaw(), claimed ? claimPitch : currentPitch());
        } else if (packet.getClass() == C03PacketPlayer.class) {
            if (claimed) ((C03PacketPlayer) packet).setRotation(claimYaw, claimPitch);
            trackReported(claimed ? claimYaw : currentYaw(), claimed ? claimPitch : currentPitch());
        }
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
