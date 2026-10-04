package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public final class ModuleCoordinatesChild extends Module {
    public enum Kind { X, Y, Z, C, DIRECTION, BIOME }

    final Kind kind;
    private final ModuleCoordinates parent;

    private final String tag;
    private final Supplier<String> value;

    private final String sample;

    final BoolSetting showLabel = bool("showLabel", true);
    final ColorSetting labelColor = color("labelColor", -1);

    final BoolSetting cardinalDirection, directionAffect;
    final ColorSetting directionAffectXColor, directionAffectZColor;

    final BoolSetting presetBiomeColor;
    final Hud hud;

    ModuleCoordinatesChild(ModuleCoordinates parent, Kind kind, String tag, Supplier<String> value, String sample) {
        super("COORDINATES_" + kind.name() + "_CHILD", true);
        this.parent = parent;
        this.kind = kind;
        this.tag = tag;
        this.value = value;
        this.sample = sample;
        boolean direction = kind == Kind.DIRECTION;
        cardinalDirection = direction ? bool("cardinalDirection", true) : null;
        directionAffect = direction ? bool("directionAffect", true) : null;
        directionAffectXColor = direction ? color("directionAffectXColor", -1) : null;
        directionAffectZColor = direction ? color("directionAffectZColor", -1) : null;
        presetBiomeColor = kind == Kind.BIOME ? bool("presetBiomeColor", true) : null;
        hud = hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(showLabel);
            if (cardinalDirection != null) s.add(cardinalDirection, directionAffect);
        });
        page.section("colorOptions", s -> {
            s.add(labelColor);
            if (directionAffectXColor != null) s.add(directionAffectXColor, directionAffectZColor).hideIf(() -> !directionAffect.on());
            if (presetBiomeColor != null) s.add(presetBiomeColor);
        });
    }

    ColorSetting valueColor() { return hud.textColor; }

    String value(boolean preview) {
        if (preview) return sample;
        return Minecraft.getMinecraft().thePlayer == null ? sample : value.get();
    }

    final class Hud extends TextHud {
        Hud() { super(ModuleCoordinatesChild.this, 0, 0, HudAnchor.TOP_LEFT, sizes(10, 10, 10, 0, 0, 0)); }

        @Override protected boolean backgroundHidden() { return !parent.moveChildrenIndividually.on(); }
        @Override protected boolean textShadowHidden() { return !parent.moveChildrenIndividually.on(); }

        @Override public boolean editable() { return parent.moveChildrenIndividually.on(); }

        @Override protected String text(boolean preview) { return value(preview); }

        @Override public boolean visible(boolean preview) {
            if (!isEnabled() || !parent.isEnabled() || !parent.moveChildrenIndividually.on()) { size(0, 0); return false; }
            if (!parent.showWhileTyping.on() && Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatOpen()) return false;
            measure(preview);
            return width() > 0;
        }

        private float pad() { return background.on() ? 4 : 2; }
        private boolean withBrackets(boolean shown) { return shown && !background.on() && brackets.on(); }

        private void measure(boolean preview) {
            String text = value(preview);
            float pad = pad();
            if (kind == Kind.DIRECTION) {
                boolean cardinal = cardinalDirection.on(), affect = directionAffect.on();
                if (!cardinal && !affect) { size(0, 0); return; }
                int n = Draw.fontHeight();
                float h = 0;
                if (affect) h += n * 2 + 2;
                if (cardinal) { h += n; if (affect) h += pad; }
                float w = cardinal ? Draw.width(text) : Draw.width("+");
                if (cardinal && showLabel.on()) w += Draw.width(tag + ": ");
                if (withBrackets(cardinal)) w += 9;
                size(w + pad * 2, h + pad * 2);
                return;
            }
            String line = showLabel.on() ? tag + ": " + text : text;
            float w = Draw.width(line) + (withBrackets(true) ? 9 : 0);
            size(w + pad * 2, Draw.fontHeight() + pad * 2);
        }

        @Override public void render(boolean preview) {
            measure(preview);
            if (width() <= 0) return;
            boolean bg = background.on(), shadow = textShadow.on();
            if (bg) {
                Draw.fill(backgroundColor, 0, 0, width(), height());
                if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            }
            String text = value(preview);
            float pad = pad();
            if (kind == Kind.DIRECTION) {
                renderDirection(text, pad, shadow);
                return;
            }
            boolean brackets = withBrackets(true);
            float x = pad, y = height() / 1.88f - Draw.fontHeight() / 2.0f + 0.5f;
            if (brackets) x = Draw.text(bracketColor, "[", x, y, shadow);
            if (showLabel.on()) x = Draw.text(labelColor, tag + ": ", x, y, shadow);
            int color = kind == Kind.BIOME && presetBiomeColor.on() ? parent.biomeColor() : textColor.color(0);
            x = Draw.text(text, x, y, color, shadow);
            if (brackets) Draw.text(bracketColor, "]", x, y, shadow);
        }

        private void renderDirection(String text, float pad, boolean shadow) {
            int n = Draw.fontHeight();
            boolean cardinal = cardinalDirection.on(), affect = directionAffect.on();
            boolean label = cardinal && showLabel.on(), brackets = withBrackets(cardinal);
            float x = pad, y = pad, cx = width() / 2;
            if (affect) {
                if (text.contains("W") || text.contains("E")) Draw.centered(directionAffectXColor, text.contains("W") ? "-" : "+", cx, y, shadow);
                y += n + pad;
            }
            if (cardinal) {
                if (brackets) x = Draw.text(bracketColor, "[", x, y, shadow);
                if (label) x = Draw.text(labelColor, tag + ": ", x, y, shadow);
                x = Draw.text(textColor, text, x, y, shadow);
                if (brackets) Draw.text(bracketColor, "]", x, y, shadow);
                y += n + pad;
            }

            if (affect && (text.contains("N") || text.contains("S"))) Draw.centered(directionAffectXColor, text.contains("N") ? "-" : "+", cx, y, shadow);
        }
    }
}
