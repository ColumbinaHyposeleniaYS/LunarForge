package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.*;

public final class ModuleHurtCam extends Module {
    private final BoolSetting disable = bool("disableHurtCam", false);
    private final NumberSetting intensity = decimal("hurtShakingIntensity", 1, 0, 2);
    private final BoolSetting oldTilt = bool("oldCameraTilt", false);
    public ModuleHurtCam() { super("HURT_CAM", false); }
    protected void layout(Page p) { p.add(disable); p.add(intensity).hideIf(disable::on); p.add(oldTilt); }
    public static float shake(float original) {
        Module m = ModuleManager.get("hurt_cam");
        if (!(m instanceof ModuleHurtCam) || !m.isEnabled()) return original;
        ModuleHurtCam cam = (ModuleHurtCam)m;
        return original * (cam.disable.on() ? 0 : cam.intensity.value());
    }
}
