package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ModuleReachDisplay extends Module {
    private final BoolSetting reverse = bool("reverseOrder", false), hideZero = bool("hideZero", false);
    private final BoolSetting highlight = bool("highlightAttackablePlayers", false);
    private final ColorSetting highlightColor = color("highlightColor", 1291910912);
    private final DecimalFormat format = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ENGLISH));
    private int target = -1, attackTick = -100;
    private double pending, distance;
    public ModuleReachDisplay() {
        super("REACH_DISPLAY", false);
        hud(new TextHud(this, 0, 0, HudAnchor.TOP_LEFT, TextHud.sizes(10,18,22,40,65,72)) {
            protected String text(boolean preview) {
                if (hideZero.on() && distance == 0) return null;
                String n = format.format(distance);
                String blocks = com.example.lunarforge.gui.ui.LunarLang.get("shared_info", "blocks");
                String label = blocks.trim();
                return reverse.on() ? Character.toUpperCase(label.charAt(0)) + label.substring(1) + ": " + n : n + blocks;
            }
        });
    }
    protected void layout(Page p) { p.section("generalOptions", s -> { s.add(reverse, hideZero); s.group(highlight, c -> c.add(highlightColor)); }); }
    @SubscribeEvent public void attack(AttackEntityEvent e) {
        if (!isEnabled() || e.entityPlayer != mc().thePlayer) return;
        Vec3 eye = e.entityPlayer.getPositionEyes(1);

        if (mc().objectMouseOver == null || mc().objectMouseOver.entityHit != e.target || mc().objectMouseOver.hitVec == null) return;
        target = e.target.getEntityId(); attackTick = e.entityPlayer.ticksExisted;
        pending = eye.distanceTo(mc().objectMouseOver.hitVec);
    }
    public void status(Entity e, byte status) {
        if (isEnabled() && mc().thePlayer != null && status == 2 && e.getEntityId() == target
                && mc().thePlayer.ticksExisted - attackTick <= 4) distance = Math.min(3, pending);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && (mc().thePlayer == null || !isEnabled() || mc().thePlayer.ticksExisted - attackTick >= 80)) { distance = 0; target = -1; }
    }
}
