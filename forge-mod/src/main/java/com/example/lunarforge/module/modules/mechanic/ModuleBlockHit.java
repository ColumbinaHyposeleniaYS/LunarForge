package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.BlockHit).
 * Sword block-hitting assistant.
 *
 * Helper mode: while you hold attack on a blocking sword it stops blocking,
 * swings once and re-blocks after the configured stop ticks.
 * Auto mode: after you attack a target, the use key is pressed for you using
 * one of four timings (Delay / HurtTime / Sag / Smart).
 *
 * Leader-Lite's KeyBindUtil.pressKeyOnce maps to KeyBinding.onTick here, its
 * TimerUtil is reproduced with a timestamp, and the HUD mode suffix was dropped
 * (LunarForge has no module suffix display). Unlike Leader-Lite this module
 * also releases the use key when disabled, so it can never leave the client
 * stuck blocking.
 */
public final class ModuleBlockHit extends Module {

    public enum Mode implements ChoiceSetting.Option {
        HELPER("Helper"), AUTO("Auto");

        private final String label;

        Mode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum AutoBlockTime implements ChoiceSetting.Option {
        DELAY("Delay"), HURT_TIME("HurtTime"), SAG("Sag"), SMART("Smart");

        private final String label;

        AutoBlockTime(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum AutoMode implements ChoiceSetting.Option {
        SPAM("Spam"), HOLD("Hold");

        private final String label;

        AutoMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private final ChoiceSetting<Mode> mode = choice("mode", Mode.HELPER);
    private final NumberSetting stopTime = integer("stopTicks", 2, 1, 5).label(() -> "Stop Ticks");
    private final ChoiceSetting<AutoBlockTime> autoBlockTime = choice("autoBlockTime", AutoBlockTime.DELAY).label(() -> "AutoBlock Time");
    private final ChoiceSetting<AutoMode> autoMode = choice("autoMode", AutoMode.SPAM).label(() -> "Auto Mode");
    private final NumberSetting blockDelay = integer("blockDelay", 100, 0, 1000).label(() -> "Block Delay");
    private final NumberSetting holdTick = integer("holdTicks", 2, 2, 5).label(() -> "Hold Ticks");
    private final NumberSetting minHurtTime = integer("minHurtTime", 10, 1, 10).label(() -> "Min HurtTime");
    private final NumberSetting maxHurtTime = integer("maxHurtTime", 10, 1, 10).label(() -> "Max HurtTime");
    private final BoolSetting onFirstHit = bool("onFirstHit", true).label(() -> "On First Hit");
    private final NumberSetting smartBlockTick = integer("smartBlockTicks", 2, 1, 5).label(() -> "Smart Block Ticks");
    private final BoolSetting releaseAfterHit = bool("releaseAfterHit", true).label(() -> "Release After Hit");
    private final NumberSetting smartBlockHurtTime = integer("smartBlockHurtTime", 2, 0, 10).label(() -> "Smart Block HurtTime");
    private final NumberSetting chance = integer("blockHitChance", 50, 0, 100).label(() -> "Block Hit Chance");
    private final BoolSetting smart = bool("smart", true).label(() -> "Smart");
    private final BoolSetting autoBlockRange = bool("autoBlockRange", true).label(() -> "AutoBlock Range");
    private final NumberSetting range = decimal("range", 3.0F, 1.0F, 4.0F).label(() -> "Range");

    private boolean startBlocking;
    private boolean attacking;
    private boolean canBlock;
    private int stopTick;
    private int holdTicks;
    private int attackTicks;
    private int sagTicks;
    private int getBlockTicks;
    private EntityLivingBase target;
    private long timerStart = System.currentTimeMillis();

    public ModuleBlockHit() {
        super("BLOCK_HIT", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode);
            s.add(stopTime).hideIf(() -> !mode.is(Mode.HELPER));
            s.add(chance, smart).hideIf(() -> !mode.is(Mode.AUTO));
            s.group(autoBlockRange, g -> g.add(range)).hideIf(() -> !mode.is(Mode.AUTO));
            s.group(autoBlockTime, g -> {
                g.add(autoMode, blockDelay).hideIf(() -> !autoBlockTime.is(AutoBlockTime.DELAY));
                g.add(holdTick).hideIf(() -> !(autoBlockTime.is(AutoBlockTime.DELAY) && autoMode.is(AutoMode.HOLD)));
                g.add(minHurtTime, maxHurtTime).hideIf(() -> !autoBlockTime.is(AutoBlockTime.HURT_TIME));
                g.add(onFirstHit, smartBlockTick, releaseAfterHit, smartBlockHurtTime).hideIf(() -> !autoBlockTime.is(AutoBlockTime.SMART));
            }).hideIf(() -> !mode.is(Mode.AUTO));
        });
    }

    @Override protected void onEnable() {
        timerStart = System.currentTimeMillis();
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        startBlocking = attacking = canBlock = false;
        target = null;
        stopTick = holdTicks = attackTicks = sagTicks = getBlockTicks = 0;
    }

    /** Leader-Lite TimerUtil.hasTimeElapsed: elapsed >= ms without auto reset. */
    private boolean timerElapsed(long ms) {
        return System.currentTimeMillis() - timerStart >= ms;
    }

    private void timerReset() {
        timerStart = System.currentTimeMillis();
    }

    /** Mirrors Leader-Lite's RotationUtil.distanceToBox: eye distance to the closest box point. */
    private static double distanceToBox(Minecraft mc, AxisAlignedBB box) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        if (box.isVecInside(eyes)) return 0.0D;
        double x = MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    private void reset(Minecraft mc) {
        attacking = canBlock = false;
        GameplayUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        holdTicks = sagTicks = getBlockTicks = 0;
        timerReset();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int attackCode = mc.gameSettings.keyBindAttack.getKeyCode();
        int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();

        if (mode.is(Mode.HELPER)) {
            if (mc.gameSettings.keyBindAttack.isKeyDown() && mc.thePlayer.isBlocking()) {
                startBlocking = true;
                KeyBinding.setKeyBindState(useCode, false);
            }
            if (startBlocking) stopTick++;
            if (stopTick == 2) KeyBinding.onTick(attackCode);
            if (stopTick > stopTime.intValue()) {
                GameplayUtil.updateKeyState(useCode);
                startBlocking = false;
                stopTick = 0;
            }
            return;
        }

        // AUTO mode
        if (target == null) return;
        if (attacking) attackTicks++;
        if (attackTicks > 10) {
            reset(mc);
            target = null;
            return;
        }
        if (Math.random() * 100.0D > chance.intValue()) {
            reset(mc);
            return;
        }
        if (autoBlockRange.on() && distanceToBox(mc, target.getEntityBoundingBox()) >= range.value()) {
            reset(mc);
            return;
        }
        if (smart.on() && target.hurtTime == 0) {
            reset(mc);
            return;
        }
        if (attacking && GameplayUtil.isSword(mc.thePlayer.getHeldItem())) {
            switch (autoBlockTime.get()) {
                case DELAY: {
                    if (timerElapsed(blockDelay.intValue())) {
                        if (autoMode.is(AutoMode.SPAM)) {
                            KeyBinding.onTick(useCode);
                            timerReset();
                            reset(mc);
                        }
                        if (autoMode.is(AutoMode.HOLD)) startBlocking = true;
                        if (startBlocking) {
                            KeyBinding.setKeyBindState(useCode, true);
                            holdTicks++;
                        }
                        if (holdTicks > holdTick.intValue()) {
                            KeyBinding.setKeyBindState(useCode, false);
                            startBlocking = false;
                            holdTicks = 0;
                            timerReset();
                        }
                    }
                    break;
                }
                case HURT_TIME: {
                    if (mc.thePlayer.hurtTime >= minHurtTime.intValue() && mc.thePlayer.hurtTime <= maxHurtTime.intValue()) {
                        KeyBinding.setKeyBindState(useCode, true);
                        startBlocking = true;
                    } else if (startBlocking) {
                        KeyBinding.setKeyBindState(useCode, false);
                        startBlocking = false;
                    }
                    break;
                }
                case SAG: {
                    if (sagTicks < 10) {
                        KeyBinding.setKeyBindState(useCode, true);
                        sagTicks++;
                    }
                    if (sagTicks >= 10) {
                        GameplayUtil.updateKeyState(useCode);
                        sagTicks = 0;
                    }
                    break;
                }
                case SMART: {
                    if (mc.thePlayer.hurtTime == smartBlockHurtTime.intValue()) canBlock = true;
                    if (canBlock) {
                        getBlockTicks++;
                        KeyBinding.setKeyBindState(useCode, true);
                    }
                    if (mc.thePlayer.hurtTime == 9 && releaseAfterHit.on()) {
                        canBlock = false;
                        GameplayUtil.updateKeyState(useCode);
                        getBlockTicks = 0;
                    }
                    if (getBlockTicks > smartBlockTick.intValue()) {
                        canBlock = false;
                        GameplayUtil.updateKeyState(useCode);
                        getBlockTicks = 0;
                    }
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || event.entityPlayer != mc.thePlayer) return;
        if (!GameplayUtil.isSword(mc.thePlayer.getHeldItem())) return;
        attacking = true;
        attackTicks = 0;
        target = event.target instanceof EntityLivingBase ? (EntityLivingBase) event.target : null;
        if (autoBlockTime.is(AutoBlockTime.SMART) && mc.thePlayer.hurtTime == 0 && onFirstHit.on()) canBlock = true;
    }
}
