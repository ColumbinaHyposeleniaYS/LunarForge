package com.example.lunarforge.module.modules.combat;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.combat.JumpReset).
 * Jumps exactly when incoming knockback is applied to cut the knockback
 * taken — the timing technique known as jump resetting, automated.
 *
 * The velocity packet is observed through {@link com.example.lunarforge.module.CombatHooks#velocity};
 * the module then waits until the player's motion matches the packet motion
 * (checked on PlayerTickEvent START, right before the player updates — the
 * one point in the tick loop where the applied velocity is still intact) and
 * presses the jump key for a single tick, releasing it on the same tick's END
 * phase. "Accuracy" is the chance that an accepted reset actually presses the
 * key, leaving the rest as missed timings like Vape does.
 *
 * Vape's 1.21 movement-input branch and its potion check were dropped
 * (LunarForge is 1.8.9 only). Knockback packets absorbed by Knockback Delay
 * are ignored so the two modules don't fight over the same hit.
 */
public final class ModuleJumpReset extends Module {
    private final NumberSetting chance = integer("chance", 40, 0, 100).label(() -> "Chance %");
    private final NumberSetting accuracy = integer("accuracy", 40, 0, 100).label(() -> "Accuracy %");
    private final BoolSetting onlyWhenTargeting = bool("onlyWhenTargeting", false).label(() -> "Only When Targeting");
    private final BoolSetting waterCheck = bool("waterCheck", false).label(() -> "Water Check");

    private final Random random = new Random();
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private boolean waitingForReset;
    private boolean shouldJump;
    private boolean jumping;
    private long waitStart;

    public ModuleJumpReset() {
        super("JUMP_RESET", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(chance, accuracy, onlyWhenTargeting, waterCheck));
    }

    @Override protected void onDisable() {
        waitingForReset = false;
        shouldJump = false;
        if (jumping) {
            jumping = false;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.gameSettings != null) GameplayUtil.updateKeyState(mc.gameSettings.keyBindJump.getKeyCode());
        }
    }

    /**
     * Called from CombatHooks (main thread) for every local-player velocity
     * packet. suppressed = the packet was absorbed by Knockback Delay.
     */
    public void onVelocityPacket(S12PacketEntityVelocity packet, boolean suppressed) {
        if (suppressed || waitingForReset) return;
        int motionX = packet.getMotionX();
        int motionY = packet.getMotionY();
        int motionZ = packet.getMotionZ();
        if (motionX == 0 && motionZ == 0) return;
        if (motionY < 0) return;
        if (!shouldReduce()) return;
        velocityX = motionX / 8000.0D;
        velocityY = motionY / 8000.0D;
        velocityZ = motionZ / 8000.0D;
        waitingForReset = true;
        waitStart = System.currentTimeMillis();
    }

    private boolean shouldReduce() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || jumping) return false;
        if (waterCheck.on() && (mc.thePlayer.isInWater() || mc.thePlayer.isInLava())) return false;
        if (onlyWhenTargeting.on() && GameplayUtil.findTarget(mc, 6.0D, 45.0F) == null) return false;
        return random.nextInt(100) < chance.intValue();
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private boolean motionMatches(Minecraft mc) {
        return round3(mc.thePlayer.motionX) == round3(velocityX)
                && round3(mc.thePlayer.motionY) == round3(velocityY)
                && round3(mc.thePlayer.motionZ) == round3(velocityZ);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.thePlayer || mc.theWorld == null || !isEnabled()) return;
        if (event.phase == TickEvent.Phase.START) {
            if (waitingForReset) {
                if (System.currentTimeMillis() - waitStart > 500L) {
                    waitingForReset = false;
                } else if (motionMatches(mc)) {
                    waitingForReset = false;
                    if (mc.currentScreen == null && !mc.gameSettings.keyBindJump.isKeyDown()) {
                        shouldJump = true;
                    }
                }
            }
            if (shouldJump) {
                shouldJump = false;
                if (random.nextInt(100) < accuracy.intValue()) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                    jumping = true;
                }
            }
        } else if (event.phase == TickEvent.Phase.END && jumping) {
            jumping = false;
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindJump.getKeyCode());
        }
    }
}
