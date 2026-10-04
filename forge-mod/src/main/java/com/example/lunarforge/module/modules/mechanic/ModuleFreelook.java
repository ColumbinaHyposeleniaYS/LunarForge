package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.*;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleFreelook extends Module {
    public enum Mode implements ChoiceSetting.Option {
        THIRD("thirdPerson", 1), FORWARD("forward", 2), FIRST("firstPerson", 0);
        final String id; final int view;
        Mode(String id, int view) { this.id = id; this.view = view; }
        public String langId() { return id; }
    }
    final KeySetting key = keybind("freelook", "LMENU");
    private final ChoiceSetting<Mode> mode = choice("mode", Mode.THIRD);
    private final BoolSetting invertYaw = bool("invertYaw", false), invertPitch = bool("invertPitch", false);
    private final BoolSetting toggle = bool("toggleKeyFreelook", false), smooth = bool("smoothCamera", true);
    private boolean active, down;
    private int savedView;
    private float yaw, pitch;
    public ModuleFreelook() { super("FREELOOK", true); }
    protected void layout(Page p) { p.add(mode, invertPitch, invertYaw, toggle, smooth, key); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean available = isEnabled() && mc().thePlayer != null && mc().currentScreen == null;
        boolean pressed = available && key.isDown();
        boolean next = toggle.on() ? (pressed && !down ? !active : active) : pressed;
        if (!available) next = false;
        down = pressed;
        if (next == active) return;
        active = next;
        if (active) {
            yaw = mc().thePlayer.rotationYaw; pitch = mc().thePlayer.rotationPitch;
            savedView = mc().gameSettings.thirdPersonView; mc().gameSettings.thirdPersonView = mode.get().view;
        } else mc().gameSettings.thirdPersonView = savedView;
        mc().renderGlobal.setDisplayListEntitiesDirty();
    }
    private static ModuleFreelook current(Entity entity) {
        Module m = ModuleManager.get("freelook");
        return m instanceof ModuleFreelook && m.isEnabled() && ((ModuleFreelook)m).active && entity == mc().thePlayer
                ? (ModuleFreelook)m : null;
    }
    public static void turn(Entity entity, float x, float y) {
        ModuleFreelook m = current(entity);
        if (m == null) { entity.setAngles(x, y); return; }
        m.yaw = (m.yaw + x * (m.invertYaw.on() ? -1 : 1) / 8 + 360) % 360;
        m.pitch = Math.max(-90, Math.min(90, m.pitch + y * (m.invertPitch.on() ? 1 : -1) / 8));
        mc().renderGlobal.setDisplayListEntitiesDirty();
    }
    public static float yaw(Entity e) { ModuleFreelook m = current(e); return m == null ? e.rotationYaw : m.yaw; }
    public static float pitch(Entity e) { ModuleFreelook m = current(e); return m == null ? e.rotationPitch : m.pitch; }
    public static float previousYaw(Entity e) { ModuleFreelook m = current(e); return m == null ? e.prevRotationYaw : m.yaw; }
    public static float previousPitch(Entity e) { ModuleFreelook m = current(e); return m == null ? e.prevRotationPitch : m.pitch; }
}
