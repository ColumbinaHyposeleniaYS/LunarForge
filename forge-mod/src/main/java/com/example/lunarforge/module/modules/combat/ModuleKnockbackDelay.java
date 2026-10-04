package com.example.lunarforge.module.modules.combat;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.combat.KnockbackDelay).
 * Holds incoming knockback velocity packets for a short moment and applies
 * them afterwards, which softens combos and makes your hit timing harder to
 * read for the opponent.
 *
 * Packet interception runs through {@link com.example.lunarforge.module.CombatHooks#velocity},
 * injected by PortHooksTransformer right after NetHandlerPlayClient's thread
 * check — so the hook always executes on the main thread exactly where
 * vanilla would apply the velocity. Only velocity packets for the local
 * player are held; while a delay is running every further velocity packet is
 * queued behind it (Vape behaviour) and the whole queue is flushed on time,
 * when a GUI opens, or on disable.
 *
 * Vape's swing-based combo counter was simplified to the module's namesake
 * split: airborne knockback uses "Air Delay", grounded knockback uses
 * "Ground Delay", both plain values instead of random ranges. The 475 ms
 * guard between two delayed hits (Vape's anti-spam check) is kept, as is the
 * "only while a target is within 5 blocks in a 90° cone" gate.
 */
public final class ModuleKnockbackDelay extends Module {
    private final NumberSetting chance = integer("chance", 40, 0, 100).label(() -> "Chance %");
    private final NumberSetting airDelay = integer("airDelay", 50, 0, 500).label(() -> "Air Delay (ms)");
    private final NumberSetting groundDelay = integer("groundDelay", 200, 0, 500).label(() -> "Ground Delay (ms)");
    private final BoolSetting waterCheck = bool("waterCheck", false).label(() -> "Water Check");

    private final Random random = new Random();
    private final Deque<SPacketEntityVelocity> held = new ArrayDeque<SPacketEntityVelocity>();
    private long releaseTime;
    private long lastDelayed;

    public ModuleKnockbackDelay() {
        super("KNOCKBACK_DELAY", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(chance, airDelay, groundDelay, waterCheck));
    }

    @Override protected void onDisable() {
        held.clear();
    }

    /**
     * Called from CombatHooks (main thread) for local-player velocity packets.
     * Returns true when the packet was absorbed and must not be applied by vanilla.
     */
    public synchronized boolean onVelocityPacket(SPacketEntityVelocity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (!held.isEmpty()) {
            held.addLast(packet);
            if (held.size() > 40) flush();
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastDelayed < 475L) return false;
        if (waterCheck.on() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) return false;
        if (GameplayUtil.findTarget(mc, 5.0D, 45.0F) == null) return false;
        if (random.nextInt(100) >= chance.intValue()) return false;
        long delay = mc.thePlayer.onGround ? groundDelay.intValue() : airDelay.intValue();
        if (delay <= 0L) return false;
        releaseTime = now + delay;
        lastDelayed = now;
        held.addLast(packet);
        return true;
    }

    /** Same motion application as NetHandlerPlayClient.handleEntityVelocity for the local player. */
    private synchronized void flush() {
        Minecraft mc = Minecraft.getMinecraft();
        while (!held.isEmpty()) {
            SPacketEntityVelocity packet = held.pollFirst();
            if (mc.thePlayer == null) break;
            mc.thePlayer.motionX = packet.getMotionX() / 8000.0D;
            mc.thePlayer.motionY = packet.getMotionY() / 8000.0D;
            mc.thePlayer.motionZ = packet.getMotionZ() / 8000.0D;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            held.clear();
            return;
        }
        if (held.isEmpty()) return;
        if (System.currentTimeMillis() >= releaseTime || mc.currentScreen != null) flush();
    }
}
