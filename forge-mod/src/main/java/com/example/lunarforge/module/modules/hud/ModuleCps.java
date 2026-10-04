package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.util.ClickCounter;

public final class ModuleCps extends Module {
    private final BoolSetting rightClick = bool("rightClick", false);
    private final BoolSetting showCPSText = bool("showCPSText", true);
    private final BoolSetting reverseText = bool("reverseText", false);
    private final BoolSetting ignoreCancelledClicks = bool("ignoreCancelledClicks", false);
    private final ColorSetting lineColor = color("lineColor", 0xFF202020);
    private final Hud hud;

    public ModuleCps() {
        super("CPS", false);
        hud = hud(new Hud());
    }

    @Override protected void onDisable() { ClickCounter.clearRight(); }

    @Override protected void layout(Page page) {
        page.section("settings", s -> {
            s.group(rightClick, g -> g.add(lineColor));
            s.add(showCPSText);
            s.add(reverseText);
            s.add(ignoreCancelledClicks);
        });
    }

    static String cpsText() { return LunarLang.get("shared_info", "cps"); }

    private final class Hud extends TextHud {
        Hud() { super(ModuleCps.this, 0, 0, HudAnchor.TOP_LEFT, sizes(10, 18, 22, 40, 56, 62)); }

        @Override protected String text(boolean preview) {
            String gap = rightClick.on() ? "  " : "";
            String space = showCPSText.on() ? " " : "";
            String left = Integer.toString(ClickCounter.left(ignoreCancelledClicks.on()));
            String right = rightClick.on() ? Integer.toString(ClickCounter.right()) : "";
            String label = showCPSText.on() ? cpsText() : "";
            if (reverseText.on()) return label + space + left + gap + right;
            return left + gap + right + space + label;
        }

        @Override protected void decorate(float x, float y, boolean preview, boolean after) {
            if (!after || !rightClick.on()) return;
            boolean ignore = ignoreCancelledClicks.on();
            float total = Draw.width(text(preview));
            float left = Draw.width(ClickCounter.left(ignore) + " ");
            float lineX;
            if (reverseText.on()) {
                float label = showCPSText.on() ? Draw.width(cpsText() + " ") : 0;
                lineX = x + width() / 2.04f - total / 2 + label + left;
            } else {
                lineX = x + width() / 2.04f - total / 2 + left;
            }
            if (textShadow.on()) Draw.rect(lineX + 0.25f, y + height() / 2 - 4, 1, 9, 0x6F000000);
            Draw.rect(lineX - 0.5f, y + height() / 2 - 4.5f, 1, 9, lineColor.color(lineX + y));
        }
    }
}
