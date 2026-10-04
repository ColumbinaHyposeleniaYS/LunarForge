package com.example.lunarforge.module;

import com.example.lunarforge.module.modules.combat.ModuleJumpReset;
import com.example.lunarforge.module.modules.combat.ModuleKnockbackDelay;
import com.example.lunarforge.module.modules.hud.ModuleCombo;
import com.example.lunarforge.module.modules.hud.ModuleReachDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.SPacketEntityVelocity;

public final class CombatHooks {
    private CombatHooks() {}
    public static void status(S19PacketEntityStatus packet) {
        if (Minecraft.getMinecraft().theWorld == null) return;
        Entity entity = packet.getEntity(Minecraft.getMinecraft().theWorld);
        if (entity == null) return;
        Module combo = ModuleManager.get("combo"), reach = ModuleManager.get("reach_display");
        if (combo instanceof ModuleCombo) ((ModuleCombo)combo).status(entity, packet.getOpCode());
        if (reach instanceof ModuleReachDisplay) ((ModuleReachDisplay)reach).status(entity, packet.getOpCode());
    }

    /**
     * Injected at the head of NetHandlerPlayClient.handleEntityVelocity (after
     * its thread check, so always on the main thread). Returns true when the
     * packet was absorbed (Knockback Delay) and vanilla must skip it; Jump
     * Reset only observes. Local-player packets only.
     */
    public static boolean velocity(SPacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return false;
        boolean suppressed = false;
        Module knockbackDelay = ModuleManager.get("knockback_delay");
        if (knockbackDelay instanceof ModuleKnockbackDelay) {
            suppressed = ((ModuleKnockbackDelay) knockbackDelay).onVelocityPacket(packet);
        }
        Module jumpReset = ModuleManager.get("jump_reset");
        if (jumpReset instanceof ModuleJumpReset) {
            ((ModuleJumpReset) jumpReset).onVelocityPacket(packet, suppressed);
        }
        return suppressed;
    }
}
