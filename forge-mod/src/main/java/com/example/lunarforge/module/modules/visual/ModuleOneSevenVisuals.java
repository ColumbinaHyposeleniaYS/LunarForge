package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;

public final class ModuleOneSevenVisuals extends Module {
    final BoolSetting useItemWhileDigging = bool("useItemWhileDigging", true);
    final BoolSetting alwaysSwing = bool("alwaysSwing", true);
    public final OneSevenAnimations animations;
    public final OneSevenItems items;

    public ModuleOneSevenVisuals() {
        super("ONE_SEVEN_VISUALS", true);
        animations = child(new OneSevenAnimations(), null);
        items = child(new OneSevenItems(), null);
    }

    @Override protected void layout(Page page) { page.add(useItemWhileDigging, alwaysSwing); }

    public boolean alwaysSwing() { return isEnabled() && alwaysSwing.on(); }

    public boolean useItemWhileDigging() { return isEnabled() && useItemWhileDigging.on(); }
}
