package com.example.lunarforge.module.modules.combat;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.CombatTimingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.combat.HitSelect and its
 * gg.vape.module.combat.wtap.* submodules — the submodes that belong to
 * HitSelect rather than to the WTap module).
 *
 * Use Cancel: while your crosshair sits on a living target that is deep in
 * its invulnerability frames (hurtResistantTime > 12) and you stand on the
 * ground, clicks are cancelled so swings never land during i-frames ("hit
 * selection"). Failing the chance roll puts the module on an 8-tick cooldown
 * instead. Vape implements this by cancelling the click event; 1.8.9 Forge
 * cannot cancel clicks, so the attack is cancelled through AttackEntityEvent
 * and the right-click use through PlayerInteractEvent — same visible result.
 *
 * Sprint Reset: on every qualifying hit the click is cancelled and re-applied
 * manually with the sprint state forced on, so the attack always carries the
 * sprint knockback bonus. Attacks within 7 ticks of receiving knockback are
 * governed by the "Preference": "KB Reduction" skips them entirely, while
 * "Critical Hits" only attacks while the knockback still lifts you
 * (airborne-after-velocity). The AttackPacketTimingTracker (LunarForge's
 * CombatTimingTracker) decides which hits would be wasted: borderline
 * hurt-time windows pass through, deep i-frame clicks are cancelled and
 * replaced by the manual sprint attack.
 *
 * Keep-alive/side-effect notes: the manual attack runs vanilla's
 * attackTargetEntityWithCurrentItem (normal attack packets), a reentrancy
 * guard keeps the nested AttackEntityEvent from recursing, and the damage
 * timing tracker is only fed here when Block Hit Mode is not already feeding
 * it.
 */
public final class ModuleHitSelect extends Module {

    public enum HitMode implements ChoiceSetting.Option {
        USE_CANCEL("Use Cancel"), SPRINT_RESET("Sprint Reset");

        private final String label;

        HitMode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    public enum Preference implements ChoiceSetting.Option {
        KB_REDUCTION("KB Reduction"), CRITICAL_HITS("Critical Hits");

        private final String label;

        Preference(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final int VELOCITY_RESET_TICKS = 7;
    private static final int COOLDOWN_TICKS = 8;

    private final ChoiceSetting<HitMode> mode = choice("mode", HitMode.USE_CANCEL).label(() -> "Mode");
    private final NumberSetting chance = integer("chance", 90, 0, 100).label(() -> "Chance %");
    private final ChoiceSetting<Preference> preference = choice("preference", Preference.KB_REDUCTION)
            .label(() -> "Preference")
            .hideIf(() -> !mode.is(HitMode.SPRINT_RESET));

    // ===== Use Cancel state =====
    private int cooldownTicks;
    private boolean cancelActive;

    // ===== Sprint Reset state =====
    private int velocityTicks;
    private boolean pendingTimestampUpdate;
    private boolean cancelUse;
    private long lastResetTime;
    private boolean inManualAttack;

    public ModuleHitSelect() {
        super("HIT_SELECT", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, chance);
            s.add(preference);
        });
    }

    @Override protected void onDisable() {
        cooldownTicks = 0;
        cancelActive = false;
        velocityTicks = 0;
        pendingTimestampUpdate = false;
        cancelUse = false;
    }

    boolean shouldTrigger() {
        return chance.intValue() >= Math.random() * 100.0D;
    }

    // ===== Use Cancel (WTapRightClickUseCancelMode) =====

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        if (pendingTimestampUpdate) {
            lastResetTime = System.currentTimeMillis();
            pendingTimestampUpdate = false;
        }
        cancelUse = false;
        if (isUseClaimActive()) {
            cancelActive = false;
            return;
        }
        if (!mode.is(HitMode.USE_CANCEL)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            cancelActive = false;
            return;
        }
        if (cooldownTicks > 0) {
            --cooldownTicks;
            cancelActive = false;
            return;
        }
        EntityLivingBase target = crosshairTarget(mc);
        if (target == null && mc.currentScreen == null) {
            cancelActive = false;
            return;
        }
        if (target != null && mc.thePlayer.onGround && target.hurtResistantTime > 12) {
            if (!shouldTrigger()) {
                cooldownTicks = COOLDOWN_TICKS;
            }
            cancelActive = true;
            return;
        }
        cancelActive = false;
    }

    private static EntityLivingBase crosshairTarget(Minecraft mc) {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.ENTITY) {
            return null;
        }
        return mc.objectMouseOver.entityHit instanceof EntityLivingBase
                ? (EntityLivingBase) mc.objectMouseOver.entityHit : null;
    }

    // ===== attack interception (EventClickMouse of both submodules) =====

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled()) return;
        if (isUseClaimActive()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer) return;
        if (!(event.target instanceof EntityLivingBase)) return;
        if (inManualAttack) return;   // the nested event of our own manual attack
        EntityLivingBase target = (EntityLivingBase) event.target;

        // feed the damage timing tracker unless Block Hit Mode already does it
        Module blockHitMode = ModuleManager.get("block_hit_mode");
        boolean blockHitFeeds = blockHitMode instanceof com.example.lunarforge.module.modules.legit.ModuleBlockHitMode
                && blockHitMode.isEnabled();
        if (!blockHitFeeds) {
            CombatTimingTracker.INSTANCE.onAttackPacketSent(target.getEntityId(), target.hurtTime == 0);
        }

        if (mode.is(HitMode.USE_CANCEL)) {
            if (cancelActive) {
                event.setCanceled(true);
            }
            return;
        }

        // ===== Sprint Reset (WTapSprintResetMode.onClickMouse) =====
        if (!shouldTrigger()) return;
        boolean airborneAfterVelocity = false;
        if (velocityTicks > 0) {
            --velocityTicks;
            if (mc.thePlayer.onGround) {
                velocityTicks = 0;
            }
            if (preference.is(Preference.KB_REDUCTION)) {
                return;
            }
            if (mc.thePlayer.motionY > 0.0D) {
                airborneAfterVelocity = true;
            } else if (!mc.thePlayer.onGround) {
                return;
            }
        }
        if (!isMovingTowardTarget(mc, target)) return;
        if (!airborneAfterVelocity) {
            CombatTimingTracker tracker = CombatTimingTracker.INSTANCE;
            int expectedHurtTime = tracker.expectedHurtTimeTicks();
            int upperHurtTime = expectedHurtTime + 1;
            if (target.hurtTime <= expectedHurtTime) {
                if (System.currentTimeMillis() - lastResetTime >= tracker.averageHitDelay() * 2L) {
                    pendingTimestampUpdate = true;
                    return;
                }
            }
            if (target.hurtTime > expectedHurtTime && target.hurtTime <= upperHurtTime) {
                return;
            }
        }
        event.setCanceled(true);
        cancelUse = true;
        applyAttackEffects(mc, target);
    }

    /** Vape isMovingTowardTarget: the player must actually move toward the target on both axes. */
    private static boolean isMovingTowardTarget(Minecraft mc, EntityLivingBase target) {
        double deltaX = target.posX - mc.thePlayer.posX;
        double deltaZ = target.posZ - mc.thePlayer.posZ;
        return deltaX < 0.0D == mc.thePlayer.motionX < 0.0D
                && deltaZ < 0.0D == mc.thePlayer.motionZ < 0.0D;
    }

    /** Vape ClientSettings.applyAttackEffects: force the sprint state so the attack carries sprint knockback. */
    private void applyAttackEffects(Minecraft mc, EntityLivingBase target) {
        mc.thePlayer.setSprinting(true);
        inManualAttack = true;
        try {
            mc.thePlayer.attackTargetEntityWithCurrentItem(target);
        } finally {
            inManualAttack = false;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null
                && EnchantmentHelper.getModifierForCreature(held, target.getCreatureAttribute()) > 0.0F) {
            mc.thePlayer.onEnchantmentCritical(target);
        }
    }

    // ===== velocity observation (WTapSprintResetMode.onPacketReceive, via CombatHooks) =====

    public void onVelocityPacket() {
        velocityTicks = VELOCITY_RESET_TICKS;
    }

    // ===== right-click use suppression (EventPlayerUseItem / EventRightClickMouse) =====

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.world == null || !event.world.isRemote) return;
        boolean useCancelled = mode.is(HitMode.USE_CANCEL) ? cancelActive : cancelUse;
        if (!useCancelled) return;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            event.setCanceled(true);
        }
    }

    /** Right-clicking an entity (riding, trading) is its own event in 1.8.9 Forge. */
    @SubscribeEvent
    public void onEntityInteract(net.minecraftforge.event.entity.player.EntityInteractEvent event) {
        if (!isEnabled()) return;
        if (event.target == null || event.target.worldObj == null || !event.target.worldObj.isRemote) return;
        boolean useCancelled = mode.is(HitMode.USE_CANCEL) ? cancelActive : cancelUse;
        if (useCancelled) event.setCanceled(true);
    }

    /** Vape rightClickUse claim (former Scaffold blatant modes): always inactive since the Scaffold removal. */
    public static boolean isUseClaimActive() {
        return false;
    }
}
