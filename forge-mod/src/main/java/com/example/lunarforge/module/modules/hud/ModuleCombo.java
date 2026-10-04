package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.hud.TextHud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleCombo extends Module {
    private int combo, target = -1, attackTick = -100, confirmedTick = -100;
    public ModuleCombo() {
        super("COMBO", false);
        hud(new TextHud(this, 0, 0, HudAnchor.TOP_RIGHT, TextHud.sizes(10,18,22,46,56,62)) {
            protected String text(boolean preview) { int n = preview ? 3 : combo; return lang(n == 0 ? "noCombo" : "combo", n); }
        });
    }
    @SubscribeEvent public void attack(AttackEntityEvent e) {
        if (!isEnabled() || e.entityPlayer != mc().thePlayer) return;
        target = e.target.getEntityId(); attackTick = e.entityPlayer.ticksExisted;
    }
    public void status(Entity entity, byte status) {
        if (!isEnabled() || mc().thePlayer == null || status != 2) return;
        if (entity == mc().thePlayer) { combo = 0; return; }
        if (entity instanceof EntityPlayer && entity.getEntityId() == target
                && mc().thePlayer.ticksExisted - attackTick <= 4 && attackTick != confirmedTick) {
            confirmedTick = attackTick; combo++;
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!isEnabled() || mc().thePlayer == null) { combo = 0; target = -1; return; }
        if (mc().thePlayer.hurtTime > 0 && mc().thePlayer.hurtTime == mc().thePlayer.maxHurtTime || mc().thePlayer.ticksExisted - attackTick >= 80) combo = 0;
    }
}
