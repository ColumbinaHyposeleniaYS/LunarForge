package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ModulePvpInfo extends Module {
    public enum TimePeriod implements ChoiceSetting.Option {
        SESSION("session"), DAY("day"), WEEK("week"), MONTH("month"), YEAR("year"), ALL_TIME("allTime");

        private final String id;

        TimePeriod(String id) { this.id = id; }

        @Override public String langId() { return id; }
    }

    public static final class Stats {
        int meleeHits, meleeMisses;
        int hitsTaken;
        int longestCombo;
        int wTapHits, wTapMisses;
        float lostHealth, recoveredHealth;
        int gapplesUsed, potionsUsed;
        float hungerReplenished;
        int bowHits, bowMisses;
        int rodHits, rodMisses;
        int eggsAndSnowballsUsed, pearlsUsed;

        private static String accuracy(int successes, int failures) {
            if (failures + successes == 0) return "0%";
            return (int)((float) successes / (float)(successes + failures) * 100.0f) + "%";
        }

        public String meleeAccuracy() { return accuracy(meleeHits, meleeMisses); }
        public String wTapAccuracy() { return accuracy(wTapHits, wTapMisses); }
        public String bowAccuracy() { return accuracy(bowHits, bowMisses); }
        public String rodAccuracy() { return accuracy(rodHits, rodMisses); }
        public String lostHealth() { return String.valueOf(lostHealth); }
        public String recoveredHealth() { return String.valueOf(recoveredHealth); }
        public String gapplesUsed() { return String.valueOf(gapplesUsed); }
        public String potionsUsed() { return String.valueOf(potionsUsed); }
        public String hitsTaken() { return String.valueOf(hitsTaken); }
        public String longestCombo() { return String.valueOf(longestCombo); }
        public String eggsAndSnowballsUsed() { return String.valueOf(eggsAndSnowballsUsed); }
        public String pearlsUsed() { return String.valueOf(pearlsUsed); }

        private Stats merge(Stats other) {
            Stats out = new Stats();
            out.meleeHits = meleeHits + other.meleeHits;
            out.meleeMisses = meleeMisses + other.meleeMisses;
            out.hitsTaken = hitsTaken + other.hitsTaken;
            out.longestCombo = Math.max(longestCombo, other.longestCombo);
            out.wTapHits = wTapHits + other.wTapHits;
            out.wTapMisses = wTapMisses + other.wTapMisses;
            out.lostHealth = lostHealth + other.lostHealth;
            out.recoveredHealth = recoveredHealth + other.recoveredHealth;
            out.gapplesUsed = gapplesUsed + other.gapplesUsed;
            out.potionsUsed = potionsUsed + other.potionsUsed;
            out.hungerReplenished = hungerReplenished + other.hungerReplenished;
            out.bowHits = bowHits + other.bowHits;
            out.bowMisses = bowMisses + other.bowMisses;
            out.rodHits = rodHits + other.rodHits;
            out.rodMisses = rodMisses + other.rodMisses;
            out.eggsAndSnowballsUsed = eggsAndSnowballsUsed + other.eggsAndSnowballsUsed;
            out.pearlsUsed = pearlsUsed + other.pearlsUsed;
            return out;
        }
    }

    private final ChoiceSetting<TimePeriod> timePeriod = choice("timePeriod", TimePeriod.SESSION);
    private final BoolSetting resetOnWorldChange = bool("resetOnWorldChange", false);

    private final Map<LocalDate, Stats> daily = new ConcurrentHashMap<LocalDate, Stats>();
    private volatile Stats session = new Stats();

    private int combo;

    private static boolean wTapFlag;

    private final Set<EntityArrow> trackedArrows =
            Collections.newSetFromMap(new IdentityHashMap<EntityArrow, Boolean>());

    private static final Field ARROW_IN_GROUND =
            Fields.find(EntityArrow.class, "inGround", "field_70254_i");

    private float lastHealth = Float.NaN;

    public ModulePvpInfo() {
        super("PVP_INFO", false);

        child(new ModulePvpInfoMelee(this), null);
        child(new ModulePvpInfoHealth(this), null);
        child(new ModulePvpInfoProjectile(this), null);
    }

    @Override protected void layout(Page page) {
        page.add(timePeriod);
        page.add(resetOnWorldChange).hideIf(() -> !timePeriod.is(TimePeriod.SESSION));
    }

    public String periodText() { return LunarLang.get("settings", timePeriod.get().langId()); }

    public Stats stats() {
        LocalDate today = LocalDate.now();
        switch (timePeriod.get()) {
            case DAY: return today();
            case WEEK: return today().merge(range(today.minusWeeks(1), today.minusDays(1)));
            case MONTH: return today().merge(range(today.minusMonths(1), today.minusDays(1)));
            case YEAR: return today().merge(range(today.minusYears(1), today.minusDays(1)));
            case ALL_TIME: return today().merge(range(null, today.minusDays(1)));
            case SESSION:
            default: return session;
        }
    }

    private Stats today() { return daily.computeIfAbsent(LocalDate.now(), date -> new Stats()); }

    private Stats range(LocalDate from, LocalDate to) {
        Stats out = new Stats();
        for (Map.Entry<LocalDate, Stats> entry : daily.entrySet()) {
            LocalDate date = entry.getKey();
            if (from != null && date.isBefore(from)) continue;
            if (to != null && date.isAfter(to)) continue;
            out = out.merge(entry.getValue());
        }
        return out;
    }

    private void update(Consumer<Stats> change) {
        change.accept(session);
        change.accept(today());
    }

    private void resetSession() { session = new Stats(); }

    @SubscribeEvent
    public void onServerJoin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (resetOnWorldChange.on()) resetSession();
    }

    public static void arrowImpact(EntityArrow arrow) {
        Module module = com.example.lunarforge.module.ModuleManager.get("PVP_INFO");
        if (!(module instanceof ModulePvpInfo)) return;
        ModulePvpInfo info = (ModulePvpInfo) module;
        if (info.trackedArrows.remove(arrow)) info.update(stats -> stats.bowHits++);
    }

    private void hurtUpdates(EntityPlayer player) {
        if (player.hurtTime > 0 && player.hurtTime == player.maxHurtTime) {
            final int ended = combo;
            update(stats -> {
                stats.hitsTaken++;
                if (ended > stats.longestCombo) stats.longestCombo = ended;
            });
            combo = 0;
        }

        Entity target = player.getLastAttacker();
        if (target instanceof EntityPlayer && ((EntityPlayer) target).hurtTime > 0
                && ((EntityPlayer) target).hurtTime == ((EntityPlayer) target).maxHurtTime
                && Math.abs(player.getLastAttackerTime() - player.ticksExisted) <= 4) {
            combo++;
            final int current = combo;
            update(stats -> {
                if (current > stats.longestCombo) stats.longestCombo = current;
            });
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || event.entityPlayer != mc.thePlayer) return;

        if (event.target instanceof EntityPlayer) update(stats -> stats.meleeHits++);
        if (wTapFlag && event.target instanceof EntityPlayer) update(stats -> stats.wTapHits++);
        wTapFlag = false;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.buttonstate || event.button != 0 || mc.currentScreen != null) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        MovingObjectPosition over = mc.objectMouseOver;
        boolean missed = over == null || over.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                || over.typeOfHit == MovingObjectPosition.MovingObjectType.MISS;
        if (!missed) return;
        update(stats -> stats.meleeMisses++);
        if (wTapFlag) update(stats -> stats.wTapMisses++);
        wTapFlag = false;
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || event.entityPlayer != player || !player.worldObj.isRemote) return;
        ItemStack stack = player.getHeldItem();
        if (stack == null) return;

        if (stack.getItem() == Items.egg || stack.getItem() == Items.snowball) {
            update(stats -> stats.eggsAndSnowballsUsed++);
        } else if (stack.getItem() == Items.ender_pearl && !player.capabilities.isCreativeMode) {
            update(stats -> stats.pearlsUsed++);
        }

        if (stack.getItem() instanceof ItemPotion && ItemPotion.isSplash(stack.getMetadata())) {
            for (PotionEffect effect : ((ItemPotion) stack.getItem()).getEffects(stack)) {
                if (effect.getPotionID() == Potion.heal.getId()) {
                    update(stats -> stats.potionsUsed++);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public void onUseItemFinish(PlayerUseItemEvent.Finish event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || event.entityPlayer != mc.thePlayer) return;
        ItemStack stack = event.item;
        if (stack == null || !(stack.getItem() instanceof ItemFood)) return;
        if (stack.getItem() instanceof ItemAppleGold) update(stats -> stats.gapplesUsed++);
        update(stats -> stats.hungerReplenished += ((ItemFood) stack.getItem()).getHealAmount(stack));
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote || !(event.entity instanceof EntityArrow)) return;
        EntityArrow arrow = (EntityArrow) event.entity;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || arrow.shootingEntity != player) return;
        if (Math.abs(arrow.posX - player.posX) > 4.0 || Math.abs(arrow.posZ - player.posZ) > 4.0) return;
        trackedArrows.add(arrow);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.START) {
            if (mc.thePlayer != null && mc.theWorld != null) hurtUpdates(mc.thePlayer);
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            trackedArrows.clear();
            lastHealth = Float.NaN;
            return;
        }

        float health = mc.thePlayer.getHealth();
        if (!Float.isNaN(lastHealth) && health != lastHealth) {
            float old = lastHealth, now = health;
            if (now < old) update(stats -> stats.lostHealth += old - now);
            else update(stats -> stats.recoveredHealth += now - old);
        }
        lastHealth = health;

        for (Iterator<EntityArrow> it = trackedArrows.iterator(); it.hasNext(); ) {
            EntityArrow arrow = it.next();
            if (Fields.getBoolean(ARROW_IN_GROUND, arrow)) {
                update(stats -> stats.bowMisses++);
                it.remove();
            } else if (arrow.isDead || !mc.theWorld.loadedEntityList.contains(arrow)) {
                it.remove();
            }
        }
    }
}
