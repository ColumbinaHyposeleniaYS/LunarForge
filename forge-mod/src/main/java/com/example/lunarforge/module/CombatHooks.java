package com.example.lunarforge.module;

import com.example.lunarforge.module.modules.combat.ModuleJumpReset;
import com.example.lunarforge.module.modules.combat.ModuleKnockbackDelay;
import com.example.lunarforge.module.modules.legit.ModuleBlockHitMode;
import com.example.lunarforge.module.modules.hud.ModuleCombo;
import com.example.lunarforge.module.modules.hud.ModuleReachDisplay;
import com.example.lunarforge.util.CombatTimingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public final class CombatHooks {
    private CombatHooks() {}
    public static void status(S19PacketEntityStatus packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity == null) return;
        if (packet.getOpCode() == 2) {
            CombatTimingTracker.INSTANCE.onDamageConfirmed(entity.getEntityId());
            Module blockHitMode = ModuleManager.get("block_hit_mode");
            if (blockHitMode instanceof ModuleBlockHitMode && entity == mc.thePlayer) {
                ((ModuleBlockHitMode) blockHitMode).onSelfDamaged();
            }
        }
        Module combo = ModuleManager.get("combo"), reach = ModuleManager.get("reach_display");
        if (combo instanceof ModuleCombo) ((ModuleCombo)combo).status(entity, packet.getOpCode());
        if (reach instanceof ModuleReachDisplay) ((ModuleReachDisplay)reach).status(entity, packet.getOpCode());
    }

    /**
     * Injected at the head of NetHandlerPlayClient.handleAnimation (after its
     * thread check). Observation only: PredictV2 records opponents' arm swings
     * to predict incoming hits. Never cancels anything.
     */
    public static void animation(S0BPacketAnimation packet) {
        Module blockHitMode = ModuleManager.get("block_hit_mode");
        if (blockHitMode instanceof ModuleBlockHitMode) {
            ((ModuleBlockHitMode) blockHitMode).onEntityAnimation(packet);
        }
    }

    /**
     * Injected at the head of NetHandlerPlayClient.handleEntityVelocity (after
     * its thread check, so always on the main thread). Returns true when the
     * packet was absorbed (Knockback Delay) and vanilla must skip it; Jump
     * Reset only observes. Local-player packets only, and both modules are
     * only consulted while enabled — a disabled module must never swallow a
     * packet, otherwise the knockback would be lost entirely.
     */
    public static boolean velocity(S12PacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return false;
        boolean suppressed = false;
        Module knockbackDelay = ModuleManager.get("knockback_delay");
        if (knockbackDelay instanceof ModuleKnockbackDelay && knockbackDelay.isEnabled()) {
            suppressed = ((ModuleKnockbackDelay) knockbackDelay).onVelocityPacket(packet);
        }
        Module jumpReset = ModuleManager.get("jump_reset");
        if (jumpReset instanceof ModuleJumpReset && jumpReset.isEnabled()) {
            ((ModuleJumpReset) jumpReset).onVelocityPacket(packet, suppressed);
        }
        Module hitSelect = ModuleManager.get("hit_select");
        if (hitSelect instanceof com.example.lunarforge.module.modules.combat.ModuleHitSelect && hitSelect.isEnabled()) {
            ((com.example.lunarforge.module.modules.combat.ModuleHitSelect) hitSelect).onVelocityPacket();
        }
        return suppressed;
    }
}
