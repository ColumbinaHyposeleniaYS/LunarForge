package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import net.minecraft.client.Minecraft;

public final class ModuleFps extends Module {
    private final BoolSetting reverseOrder = bool("reverseOrder", false);

    public ModuleFps() {
        super("FPS", false);
        hud(new TextHud(this, 0, 0, HudAnchor.TOP_LEFT, TextHud.sizes(10, 18, 22, 50, 56, 62)) {
            @Override protected String text(boolean preview) {
                return lang(reverseOrder.on() ? "reverse" : "fps", Minecraft.getDebugFPS());
            }
        });
    }

    @Override protected void layout(Page page) {
        page.add(reverseOrder);
    }
}
