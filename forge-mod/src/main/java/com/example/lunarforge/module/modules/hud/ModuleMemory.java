package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;

public final class ModuleMemory extends Module {
    public enum DisplayMode implements ChoiceSetting.Option {
        PERCENTAGE("percentage"), MEGABYTES("megabytes"), GIGABYTES("gigabytes");
        private final String id;
        DisplayMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ChoiceSetting<DisplayMode> displayMode = choice("displayMode", DisplayMode.PERCENTAGE);
    private final BoolSetting colorBasedOnUsage = bool("colorBasedOnUsage", false);
    private final ColorSetting lowMemColor = color("lowMemColor", 0xFFFF0000);
    private final ColorSetting medMemColor = color("medMemColor", 0xFFFFFF00);
    private final ColorSetting highMemColor = color("highMemColor", 0xFF00FF00);

    public ModuleMemory() {
        super("MEMORY", false);
        hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("settings", s -> s.add(displayMode));
        page.section("colorOptions", s -> s.group(colorBasedOnUsage, g -> g.add(lowMemColor, medMemColor, highMemColor)));
    }

    private final class Hud extends TextHud {
        private int memoryUsage;

        Hud() { super(ModuleMemory.this, 0, 0, HudAnchor.TOP_RIGHT, sizes(10, 18, 22, 46, 56, 62)); }

        @Override protected String text(boolean preview) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            long max = runtime.maxMemory();
            memoryUsage = (int)(used * 100L / max);
            String value;
            switch (displayMode.get()) {
                case MEGABYTES: value = used / 0x100000L + "/" + max / 0x100000L + " MB"; break;
                case GIGABYTES: value = String.format("%.2f/%.2f GB", used / 1073741824.0, max / 1073741824.0); break;
                default: value = memoryUsage + "%"; break;
            }
            return lang("memory", value);
        }

        @Override protected Boolean staticWidthFor(String text) { return Boolean.FALSE; }

        @Override protected boolean textColorHidden() { return colorBasedOnUsage.on(); }

        @Override protected void drawText(String text, float x, float y, boolean withBrackets, boolean shadow) {
            ColorSetting color = textColor;
            if (colorBasedOnUsage.on()) {
                color = memoryUsage >= 75 ? lowMemColor : memoryUsage >= 50 ? medMemColor : highMemColor;
            }
            if (withBrackets) x = Draw.text(bracketColor, "[", x, y, shadow);
            x = Draw.text(color, text, x, y, shadow);
            if (withBrackets) Draw.text(bracketColor, "]", x, y, shadow);
        }
    }
}
