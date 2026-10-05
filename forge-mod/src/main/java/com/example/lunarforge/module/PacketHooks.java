package com.example.lunarforge.module;

import com.example.lunarforge.module.modules.legit.ModuleBlockHitMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

/**
 * Hook target for the NetworkManager.sendPacket injection (PortHooksTransformer).
 *
 * Every outgoing packet is handed to Block Hit Mode's Lag buffering, which may
 * absorb it (queued until the configured delay elapsed, then flushed back
 * through the same NetworkManager). Everything else passes through unchanged.
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
            Module blockHitMode = ModuleManager.get("block_hit_mode");
            if (blockHitMode instanceof ModuleBlockHitMode) {
                return ((ModuleBlockHitMode) blockHitMode).onSendPacket(manager, packet);
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
