package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleSnaplook extends Module {
    private final KeySetting third = keybind("thirdPersonKey", "LMENU"), forward = keybind("forwardPersonKey");
    private final BoolSetting smooth = bool("smoothCamera", true), toggle = bool("snaplookToggleMode", false);
    private boolean active, lastThird, lastForward, savedSmooth;
    private int saved;
    public ModuleSnaplook() { super("SNAPLOOK", false); }
    protected void layout(Page p) { p.add(third, forward, smooth, toggle); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean available = isEnabled() && mc().theWorld != null && mc().currentScreen == null;
        boolean t = available && third.isDown(), f = available && forward.isDown();
        Module freelook = com.example.lunarforge.module.ModuleManager.get("freelook");
        if (freelook instanceof ModuleFreelook && freelook.isEnabled()
                && (third.code() == ((ModuleFreelook)freelook).key.code() || forward.code() == ((ModuleFreelook)freelook).key.code())) t = f = false;
        if (!available) stop();
        else if (toggle.on()) { if (t && !lastThird) start(1); if (f && !lastForward) start(2); }
        else if (t || f) { if (!active || (t && !lastThird) || (f && !lastForward)) start(f && !lastForward ? 2 : t ? 1 : 2); }
        else stop();
        lastThird = t; lastForward = f;
    }
    private void start(int view) {
        if (active && toggle.on() && mc().gameSettings.thirdPersonView == view) { stop(); return; }
        if (!active) { saved = mc().gameSettings.thirdPersonView; savedSmooth = mc().gameSettings.smoothCamera; active = true; }
        mc().gameSettings.thirdPersonView = view;

        if (smooth.on()) mc().gameSettings.smoothCamera = true;
        mc().renderGlobal.setDisplayListEntitiesDirty();
    }
    private void stop() {
        if (!active) return;
        mc().gameSettings.thirdPersonView = saved; mc().gameSettings.smoothCamera = savedSmooth;
        active = false; mc().renderGlobal.setDisplayListEntitiesDirty();
    }
}
