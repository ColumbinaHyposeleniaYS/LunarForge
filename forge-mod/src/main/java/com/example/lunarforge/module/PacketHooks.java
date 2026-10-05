package com.example.lunarforge.module;

import com.example.lunarforge.module.modules.legit.ModuleBlockHitMode;
import com.example.lunarforge.util.RotationSpoof;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

/**
 * Hook target for the NetworkManager.sendPacket injection (PortHooksTransformer).
 *
 * Dispatch order for every outgoing packet:
 * 1. {@link RotationSpoof} rewrites the yaw/pitch of rotation packets while
 *    the Scaffold holds a silent-rotation claim (never absorbs).
 * 2. Block Hit Mode's Lag buffering may absorb the packet (queued until the
 *    configured delay elapsed, then flushed back through the same
 *    NetworkManager).
 * Everything else passes through unchanged.
 *
 * Never throws: an exception here would break every outgoing packet.
 */
public final class PacketHooks {
    private PacketHooks() {}

    /** Injected at the head of NetworkManager.sendPacket. Returns true to cancel the send. */
    public static boolean sendPacket(NetworkManager manager, Packet packet) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return false;
            if (RotationSpoof.onSendPacket(packet)) return true;
            boolean absorbed = false;
            Module blockHitMode = ModuleManager.get("block_hit_mode");
            if (blockHitMode instanceof ModuleBlockHitMode) {
                absorbed = ((ModuleBlockHitMode) blockHitMode).onSendPacket(manager, packet);
            }
            return absorbed;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
