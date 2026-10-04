package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.*;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleZoom extends Module {
    private final KeySetting key = keybind("zoomKeybind", "C");
    private final BoolSetting toggle = bool("toggleKeyZoom", false);
    private final BoolSetting smoothCamera = bool("smoothCamera", true);
    private final BoolSetting smoothZoom = bool("smoothZoom", true);
    private final BoolSetting variable = bool("variableZoom", false);
    private final NumberSetting scrollSpeed = decimal("zoomScrollSpeed", 1, .25f, 5);
    private final NumberSetting divisor = integer("zoomDivisor", 4, 2, 10);
    private final NumberSetting sensitivity = decimal("cameraSensitivity", 1, .1f, 2);
    private boolean active, down, savedSmooth;
    private float variableFactor = 1, previous = 1, current = 1;
    public ModuleZoom() { super("ZOOM", true); }
    protected void layout(Page p) { p.add(key, toggle, smoothCamera, smoothZoom, variable, scrollSpeed, divisor, sensitivity); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean pressed = isEnabled() && mc().theWorld != null && mc().currentScreen == null && key.isDown();
        boolean next = toggle.on() ? (pressed && !down ? !active : active) : pressed;
        if (!isEnabled() || mc().theWorld == null || mc().currentScreen != null) next = false;
        down = pressed;
        if (next != active) {
            if (next) savedSmooth = mc().gameSettings.smoothCamera;
            else { mc().gameSettings.smoothCamera = savedSmooth; variableFactor = 1; }
            active = next;
            mc().renderGlobal.setDisplayListEntitiesDirty();
        }
        if (active) mc().gameSettings.smoothCamera = smoothCamera.on() || savedSmooth;
        previous = current;
        float target = active ? 1 / zoomDivisor() : 1;
        current = smoothZoom.on() ? current + (target - current) * .75f : target;
    }
    private float zoomDivisor() { return divisor.intValue() * (variable.on() ? variableFactor : 1); }
    @SubscribeEvent public void scroll(MouseEvent e) {
        if (!isEnabled() || !active || !variable.on() || e.dwheel == 0 || mc().currentScreen != null) return;
        variableFactor = Math.max(1.4f / divisor.intValue(), Math.min(10, variableFactor + Math.signum(e.dwheel) * scrollSpeed.value() * .5f));
        e.setCanceled(true);
    }
    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier e) {
        if (!isEnabled()) return;
        float factor = smoothZoom.on() ? previous + (current - previous) * (float)e.renderPartialTicks : (active ? 1 / zoomDivisor() : 1);
        e.setFOV(e.getFOV() * factor);
    }

    public static float sensitivity(float original) {
        Module m = com.example.lunarforge.module.ModuleManager.get("zoom");
        return m instanceof ModuleZoom && m.isEnabled() && ((ModuleZoom)m).active
                ? original * ((ModuleZoom)m).sensitivity.value() : original;
    }
}
