package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

public final class ModulePingHud extends Module {
    private final ModulePing parent;
    private final ColorSetting pingIconColor = color("pingIconColor", -16711909);
    private final ColorSetting pingIconBackgroundColor = color("pingIconBackgroundColor", -10461088);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting brackets = bool("brackets", true);
    private final BoolSetting staticBackgroundWidth = bool("staticBackgroundWidth", false);
    private final BoolSetting staticBackgroundHeight = bool("staticBackgroundHeight", false);
    private final NumberSetting backgroundWidth = integer("backgroundWidth", 56, 40, 120);
    private final NumberSetting backgroundHeight = integer("backgroundHeight", 18, 10, 32);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting iconShadow = bool("iconShadow", true);
    private final BoolSetting overridePingTextColor = bool("overridePingTextColor", false);
    private final ColorSetting pingPrefixColor = color("pingPrefixColor", -1);
    private final ColorSetting pingTextColor = color("pingTextColor", -1);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting bracketColor = color("bracketColor", -1);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final BoolSetting iconMode = bool("iconMode", false);
    private final BoolSetting dynamicIconColor = bool("dynamicIconColor", false);

    ModulePingHud(ModulePing parent) {
        super("PING_HUD", true);
        this.parent = parent;
        hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.group(iconMode, g -> {
                g.add(iconShadow);
                g.add(pingIconColor).hideIf(dynamicIconColor::on);
                g.add(pingIconBackgroundColor, dynamicIconColor);
            });
            s.add(textShadow);
            s.group(background, g -> {
                g.group(staticBackgroundWidth, w -> w.add(backgroundWidth));
                g.group(staticBackgroundHeight, h -> h.add(backgroundHeight));
                g.group(border, b -> b.add(borderThickness));
            });
            s.add(brackets).hideIf(background::on);
            s.group(overridePingTextColor, g -> g.add(pingTextColor));
            s.add(pingPrefixColor);
        });

        page.under(background, backgroundColor);
        page.under(border, borderColor);
        page.under(brackets, bracketColor);
    }

    private final class Hud extends HudElement {
        Hud() {
            super(ModulePingHud.this, 0, 0, HudAnchor.TOP_LEFT);
            size(56, height());
        }

        private float content() { return iconMode.on() ? 28 : background.on() ? 18 : 10; }

        private float boxHeight() {
            return background.on() && staticBackgroundHeight.on() ? backgroundHeight.intValue() : content();
        }

        @Override public boolean visible(boolean preview) {
            size(width() <= 0 ? 56 : width(), boxHeight());
            return preview || Minecraft.getMinecraft().getCurrentServerData() != null;
        }

        @Override public void render(boolean preview) {
            int ping = parent.ping();
            boolean fixedWidth = background.on() && staticBackgroundWidth.on();
            float boxWidth = fixedWidth ? backgroundWidth.intValue() : width();
            float boxHeight = boxHeight();
            if (background.on()) Draw.fill(backgroundColor, 0, 0, boxWidth, boxHeight);
            if (border.on()) Draw.border(borderColor, 0, 0, boxWidth, boxHeight, borderThickness.value());
            float y = (boxHeight - content()) / 2.0f;
            float total = 0;
            if (iconMode.on()) {
                ColorSetting bar = dynamicIconColor.on() ? parent.numberColor(ping) : pingIconColor;
                GlStateManager.pushMatrix();
                GlStateManager.translate(boxWidth / 2.0f, y, 0);
                GlStateManager.scale(2, 2, 1);
                GlStateManager.translate(-5, 0, 0);
                int lit = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
                boolean shadow = iconShadow.on();
                for (int i = 0; i < 5; i++) bar(i < lit ? bar : pingIconBackgroundColor, i * 2, 7, 1, -(2 + i), shadow);
                GlStateManager.popMatrix();
                total += background.on() ? 4 : 8;
                y += 18;
            } else if (background.on()) {
                total += 4;
                y += 5;
            } else {
                y += 1;
            }
            String prefix = parent.pingPrefix.get();
            List<ModulePing.Part> value = parent.text(ping, true, overridePingTextColor.on() ? pingTextColor : null);
            float valueWidth = 0;
            for (ModulePing.Part part : value) valueWidth += Draw.width(part.text);
            boolean showPrefix = parent.showPingPrefix.on();
            float prefixWidth = showPrefix ? Draw.width(prefix) : 0;
            boolean withBrackets = !background.on() && brackets.on();
            if (withBrackets) {
                if (showPrefix) prefixWidth += Draw.width("[");
                else valueWidth += Draw.width("[");
                valueWidth += Draw.width("]");
            }
            total += prefixWidth + valueWidth;
            total += withBrackets || background.on() ? 4 : 1;
            boolean shadow = textShadow.on();
            float x = background.on() || iconMode.on() ? boxWidth / 2.0f - total / 2.0f + 4.0f : 0;
            if (showPrefix) {
                float px = x;
                if (withBrackets) px = Draw.text("[", px, y, bracketColor.color(0), shadow);
                Draw.text(prefix, px, y, pingPrefixColor.color(0), shadow);
            }
            float vx = x + prefixWidth;
            if (withBrackets && !showPrefix) vx = Draw.text("[", vx, y, bracketColor.color(0), shadow);
            for (ModulePing.Part part : value) vx = Draw.text(part.text, vx, y, part.color, shadow);
            if (withBrackets) Draw.text("]", vx, y, bracketColor.color(0), shadow);
            size(fixedWidth ? backgroundWidth.intValue() : total, boxHeight);
            GlStateManager.color(1, 1, 1, 1);
        }

        private void bar(ColorSetting color, float x, float y, float w, float h, boolean shadow) {
            float top = Math.min(y, y + h), height = Math.abs(h);
            int c = color.color(0);
            if (shadow) Draw.rect(x + 1, top + 1, w, height, Draw.shadow(c));
            Draw.rect(x, top, w, height, c);
        }
    }
}
