package com.example.lunarforge.module.modules.combat;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.combat.WTap).
 * Briefly releases the forward key (W) when you hit a target and re-presses it
 * after the configured delays — the classic sprint reset that makes your
 * follow-up hit deal more knockback.
 *
 * Vape's 1.16-specific hurt-time branch was dropped because LunarForge only
 * targets 1.8.9. "Select Hits" follows Vape's 1.8-1.12 path: the tap only
 * fires while the target's hurtResistantTime is 14 or less, so hits that
 * bounce off invulnerability frames don't waste a sprint reset. Opening any
 * GUI or disabling the module instantly restores the physical key state.
 */
public final class ModuleWTap extends Module {
    private final NumberSetting chance = integer("chance", 90, 0, 100).label(() -> "Chance %");
    private final BoolSetting selectHits = bool("selectHits", true).label(() -> "Select Hits");
    private final NumberSetting releaseDelay = integer("releaseDelay", 0, 0, 500).label(() -> "Release Delay (ms)");
    private final NumberSetting rePressDelay = integer("rePressDelay", 0, 0, 500).label(() -> "Re-press Delay (ms)");

    private final Random random = new Random();
    private boolean releasePending;
    private boolean rePressPending;
    private long releaseTimer;
    private long rePressTimer;

    public ModuleWTap() {
        super("W_TAP", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(chance, selectHits);
            s.add(releaseDelay, rePressDelay);
        });
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.gameSettings != null) restore(mc);
    }

    private void restore(Minecraft mc) {
        KeyBinding forward = mc.gameSettings.keyBindForward;
        KeyBinding.setKeyBindState(forward.getKeyCode(), GameplayUtil.physicalDown(forward));
        releasePending = false;
        rePressPending = false;
    }

    /** Vape EventPreAttack: on every qualifying hit, arm the release (delay 0 releases immediately). */
    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || event.entityPlayer != mc.thePlayer) return;
        if (!(event.target instanceof EntityLivingBase)) return;
        if (releasePending || rePressPending) return;
        if (selectHits.on() && ((EntityLivingBase) event.target).hurtResistantTime > 14) return;
        if (random.nextInt(100) >= chance.intValue()) return;
        releasePending = true;
        releaseTimer = System.currentTimeMillis();
        tryRelease();
    }

    private void tryRelease() {
        if (System.currentTimeMillis() - releaseTimer < releaseDelay.intValue()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        }
        releasePending = false;
        rePressPending = true;
        rePressTimer = System.currentTimeMillis();
    }

    private void tryRePress() {
        if (System.currentTimeMillis() - rePressTimer < rePressDelay.intValue()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) {
            KeyBinding forward = mc.gameSettings.keyBindForward;
            if (GameplayUtil.physicalDown(forward)) {
                KeyBinding.setKeyBindState(forward.getKeyCode(), true);
            }
        }
        rePressPending = false;
    }

    /** Vape EventPreTick: release/re-press on the client tick, restore when a GUI opens. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null) return;
        if (mc.currentScreen != null) {
            if (releasePending || rePressPending) restore(mc);
            return;
        }
        if (releasePending) {
            tryRelease();
        } else if (rePressPending) {
            tryRePress();
        }
    }
}
