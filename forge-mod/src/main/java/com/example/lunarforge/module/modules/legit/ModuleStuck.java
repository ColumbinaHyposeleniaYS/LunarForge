package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.PlayerInputHook;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MovementInput;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.movement.Stuck).
 * Freezes the player in place for a clutch window: motion is zeroed every
 * tick, movement input is flattened, outgoing packets are blinked (buffered
 * and flushed on release) and knockback packets are held back so a hit
 * cannot throw the clutch. It runs in freeze/release cycles of the
 * configured length, and Scaffold's clutch hands it over automatically.
 *
 * Adaptations: Leader blinks through a global blink manager and delays
 * knockback through its delay manager - both are reproduced with internal
 * queues here. The velocity hold only covers S12PacketEntityVelocity (S27
 * explosions are not held, same as Knockback Delay), and the per-tick
 * KeyBinding.unPressAllKeys is replaced by input flattening so a GUI can
 * still be used while stuck.
 */
public final class ModuleStuck extends Module {

    private final NumberSetting stuckTicks = integer("stuckTicks", 10, 10, 20).label(() -> "Stuck Ticks");

    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using;
    private boolean knockbackRelease;
    private boolean internalToggle;
    private boolean releasing;
    private EntityPlayerSP motionOwner;
    private boolean internalReleasePending;

    private final Deque<Packet> blinkQueue = new ArrayDeque<Packet>();
    private NetworkManager blinkOwner;
    private boolean blinkFlushing;
    private final List<S12PacketEntityVelocity> heldVelocity = new ArrayList<S12PacketEntityVelocity>();

    private final PlayerInputHook.Modifier inputFreeze = new PlayerInputHook.Modifier() {
        @Override public void modify(MovementInput input) {
            input.moveForward = 0.0F;
            input.moveStrafe = 0.0F;
            input.jump = false;
            input.sneak = false;
        }
    };

    public ModuleStuck() {
        super("STUCK", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(stuckTicks));
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && this.knockbackRelease && !this.internalToggle) return;
        if (!enabled && !this.internalToggle) {
            boolean cleanup = this.using || this.knockbackRelease || this.motionOwner != null;
            this.knockbackRelease = false;
            // Internal release temporarily sets enabled=false while leaving using=true.
            // setEnabled(false) is a no-op in that window, so explicitly stop the restart cycle.
            if (!this.isEnabled()) {
                if (cleanup) this.forceCleanup();
                return;
            }
        }
        super.setEnabled(enabled);
    }

    public boolean isStuckActive() {
        return this.isEnabled() || this.using || this.knockbackRelease || this.releasing;
    }

    private void setEnabledInternal(boolean enabled) {
        this.internalToggle = true;
        try {
            super.setEnabled(enabled);
        } finally {
            this.internalToggle = false;
        }
    }

    @Override protected void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            this.tick = 0;
            this.using = true;
            this.internalReleasePending = false;
            this.savedMotionX = mc.thePlayer.motionX;
            this.savedMotionY = mc.thePlayer.motionY;
            this.savedMotionZ = mc.thePlayer.motionZ;
            this.motionOwner = mc.thePlayer;
        }
        this.heldVelocity.clear();
        this.blinkQueue.clear();
        this.blinkOwner = null;
        this.blinkFlushing = false;
        PlayerInputHook.ensureAttached(null, this.inputFreeze);
    }

    @Override protected void onDisable() {
        forceCleanup();
    }

    /** Leader onDisabled: release every freeze resource exactly once. */
    private void forceCleanup() {
        Minecraft mc = Minecraft.getMinecraft();
        this.using = false;
        if (!this.internalToggle) {
            this.tick = 0;
            this.internalReleasePending = false;
            this.knockbackRelease = false;
        }
        if (mc.thePlayer != null && mc.thePlayer == this.motionOwner) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionZ = this.savedMotionZ;
            mc.thePlayer.motionY = this.savedMotionY;
        }
        if (!this.internalToggle) this.motionOwner = null;
        this.releasing = true;
        try {
            flushBlinkQueue();
            flushHeldVelocity();
        } finally {
            this.releasing = false;
        }
        PlayerInputHook.release(null, this.inputFreeze);
    }

    // ===== events =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        PlayerInputHook.ensureAttached(null, this.inputFreeze);
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer != this.motionOwner || mc.thePlayer.isDead) {
            this.setEnabled(false);
            return;
        }
        if (this.using) {
            int releaseTick = this.stuckTicks.intValue();
            if (this.internalReleasePending) {
                this.internalReleasePending = false;
                this.knockbackRelease = false;
                this.setEnabledInternal(true);
            } else if (this.tick >= releaseTick) {
                this.setEnabledInternal(false);
                this.using = true;
                this.internalReleasePending = true;
            }
            this.tick++;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !this.isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.thePlayer) return;
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
        mc.thePlayer.motionY = 0.0D;
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!this.isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityLiving != mc.thePlayer) return;
        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
    }

    // ===== hooks =====

    /**
     * Called from CombatHooks while enabled for local-player velocity
     * packets. Absorbs the knockback and starts the release cycle, exactly
     * like Leader's PacketEvent handling.
     */
    public boolean onVelocityPacket(S12PacketEntityVelocity packet) {
        this.heldVelocity.add(packet);
        this.knockbackRelease = true;
        this.tick = this.stuckTicks.intValue();
        return true;
    }

    /**
     * Called from PacketHooks. Blinks every outgoing packet (except
     * keep-alives) while enabled; the queue is flushed on release.
     */
    public boolean onSendPacket(NetworkManager manager, Packet packet) {
        if (!this.isEnabled() || this.blinkFlushing) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) return false;
        if (packet instanceof C00PacketKeepAlive) return false;
        this.blinkQueue.addLast(packet);
        this.blinkOwner = manager;
        return true;
    }

    private void flushBlinkQueue() {
        NetworkManager manager = this.blinkOwner;
        this.blinkOwner = null;
        if (manager == null) {
            this.blinkQueue.clear();
            return;
        }
        this.blinkFlushing = true;
        try {
            while (!this.blinkQueue.isEmpty()) {
                Packet packet = this.blinkQueue.pollFirst();
                manager.sendPacket(packet);
            }
        } finally {
            this.blinkFlushing = false;
        }
    }

    /** Applies the held knockback with the vanilla velocity formula (same as Knockback Delay). */
    private void flushHeldVelocity() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            this.heldVelocity.clear();
            return;
        }
        for (S12PacketEntityVelocity packet : this.heldVelocity) {
            mc.thePlayer.motionX = packet.getMotionX() / 8000.0D;
            mc.thePlayer.motionY = packet.getMotionY() / 8000.0D;
            mc.thePlayer.motionZ = packet.getMotionZ() / 8000.0D;
        }
        this.heldVelocity.clear();
    }
}
