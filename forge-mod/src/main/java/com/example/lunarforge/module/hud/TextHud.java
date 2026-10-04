package com.example.lunarforge.module.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;

public abstract class TextHud extends HudElement {
    public enum Alignment implements ChoiceSetting.Option {
        LEFT("left"), CENTER("center"), RIGHT("right");
        private final String id;
        Alignment(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public static int[] sizes(int hMin, int h, int hMax, int wMin, int w, int wMax) { return new int[]{hMin, h, hMax, wMin, w, wMax}; }

    public final BoolSetting background, brackets, staticBackgroundWidth, staticBackgroundHeight, border, textShadow;
    public final NumberSetting backgroundWidth, backgroundHeight, borderThickness;
    public final ColorSetting textColor, bracketColor, backgroundColor, borderColor;

    public final ChoiceSetting<Alignment> alignment;
    public final BoolSetting showHudIcons, showText;

    protected TextHud(Module module, float x, float y, HudAnchor anchor, int[] sizes) {
        this(module, x, y, anchor, sizes, false, false, true);
    }

    protected TextHud(Module module, float x, float y, HudAnchor anchor, int[] sizes, boolean withAlignment, boolean withIcons,
                      boolean backgroundDefault) {
        this(module, x, y, anchor, sizes, withAlignment, withIcons, backgroundDefault, true);
    }

    protected TextHud(Module module, float x, float y, HudAnchor anchor, int[] sizes, boolean withAlignment, boolean withIcons,
                      boolean backgroundDefault, boolean staticWidthDefault) {
        super(module, x, y, anchor);
        background = module.bool("background", backgroundDefault);
        brackets = module.bool("brackets", true);
        staticBackgroundWidth = module.bool("staticBackgroundWidth", staticWidthDefault);
        staticBackgroundHeight = module.bool("staticBackgroundHeight", true);
        backgroundWidth = module.integer("backgroundWidth", sizes[4], sizes[3], sizes[5]);
        backgroundHeight = module.integer("backgroundHeight", sizes[1], sizes[0], sizes[2]);
        border = module.bool("border", false);
        textShadow = module.bool("textShadow", true);
        textColor = module.color("textColor", 0xFFFFFFFF);
        bracketColor = module.color("bracketColor", 0xFFFFFFFF);
        backgroundColor = module.color("backgroundColor", 0x6F000000);
        borderColor = module.color("borderColor", 0x9F000000);
        borderThickness = module.decimal("borderThickness", 0.5f, 0.5f, 3.0f);
        alignment = withAlignment ? module.choice("alignment", Alignment.CENTER) : null;
        showHudIcons = withIcons ? module.bool("showHudIcons", true) : null;
        showText = withIcons ? module.bool("showText", true) : null;
    }

    protected abstract String text(boolean preview);

    protected java.util.List<String> lines(boolean preview) { return null; }

    protected void decorate(float x, float y, boolean preview, boolean after) {}

    private String line;
    private float textWidth;

    private java.util.List<String> block;
    private float blockHeight;

    @Override public boolean visible(boolean preview) {
        block = lines(preview);
        if (block != null) {
            if (block.isEmpty()) return false;
            boolean bg = hasBackground();
            line = null; textWidth = 0; blockHeight = 0;
            for (String l : block) {
                float w = Draw.width(hasBrackets() ? "[" + l + "]" : l);
                if (w > textWidth || line == null) { textWidth = w; line = l; }
                blockHeight += textHeight() + 1.0f;
            }
            size(width(bg), height(bg));
            return true;
        }
        line = text(preview);
        if (line == null || line.isEmpty()) return false;
        boolean bg = hasBackground();
        String shown = hasBrackets() ? "[" + line + "]" : line;
        textWidth = Draw.width(shown);
        size(width(bg), height(bg));
        return true;
    }

    protected float width(boolean bg) {
        Boolean forced = staticWidthFor(line);
        boolean fixed = bg && (forced != null ? forced : staticBackgroundWidth.on());
        return fixed ? backgroundWidth.intValue() : textWidth + (bg ? 8 : 0);
    }

    protected Boolean staticWidthFor(String text) { return null; }

    protected float height(boolean bg) {
        Boolean forced = heightCondition();
        boolean fixed = bg && (forced != null ? forced : staticBackgroundHeight.on());
        return fixed ? backgroundHeight.intValue() : (block != null ? blockHeight : textHeight()) + (bg ? 8 : 0);
    }

    protected Boolean heightCondition() { return null; }

    protected Boolean bracketsCondition() { return null; }

    protected Boolean backgroundCondition() { return null; }

    protected boolean hasBackground() { Boolean c = backgroundCondition(); return c != null ? c : background.on(); }

    protected boolean hasBrackets() { Boolean c = bracketsCondition(); return !hasBackground() && (c != null ? c : brackets.on()); }

    protected boolean backgroundHidden() { return false; }
    protected boolean textShadowHidden() { return false; }
    protected boolean textColorHidden() { return false; }

    protected float textHeight() { return Draw.fontHeight(); }

    @Override public void render(boolean preview) {
        boolean bg = hasBackground(), withBrackets = hasBrackets();
        float w = width(), h = height();
        if (bg) {
            Draw.fill(backgroundColor, 0, 0, w, h);
            if (border.on()) Draw.border(borderColor, 0, 0, w, h, borderThickness.value());
        }
        decorate(0, 0, preview, false);
        if (block != null) {
            float y = h / 2.0f - blockHeight / 2.0f + 1.0f;
            for (String l : block) {
                float lw = Draw.width(withBrackets ? "[" + l + "]" : l);
                drawText(l, align(0, w, lw, bg), y, withBrackets, textShadow.on());
                y += textHeight() + 1.0f;
            }
            decorate(0, 0, preview, true);
            return;
        }
        float y = h / 1.88f - textHeight() / 2 + 0.5f;
        float x = align(0, w, textWidth, bg);
        drawText(line, x, y, withBrackets, textShadow.on());
        decorate(0, 0, preview, true);
    }

    protected void drawText(String text, float x, float y, boolean withBrackets, boolean shadow) {
        if (withBrackets) x = Draw.text(bracketColor, "[", x, y, shadow);
        x = Draw.text(textColor, text, x, y, shadow);
        if (withBrackets) Draw.text(bracketColor, "]", x, y, shadow);
    }

    protected float align(float x, float w, float tw, boolean bg) {
        Alignment a = alignment != null ? alignment.get() : defaultAlignment();
        switch (a) {
            case LEFT: return x + (bg ? 4 : 0);
            case RIGHT: return x + w - (bg ? 4 : 0) - tw;
            default: return x + w / 2 - tw / 2;
        }
    }

    protected Alignment defaultAlignment() { return Alignment.CENTER; }

    protected boolean canHideText() { return false; }

    @Override public void layout(Page page) {
        super.layout(page);
        page.section("generalOptions", s -> {
            s.add(textShadow).hideIf(this::textShadowHidden);
            s.add(brackets).hideIf(() -> background.on() || bracketsCondition() != null);
            s.group(background, g -> {
                g.add(staticBackgroundWidth).hideIf(() -> staticWidthFor(null) != null);
                g.add(staticBackgroundHeight).hideIf(() -> !background.on() || heightCondition() != null);
                g.add(backgroundWidth).hideIf(() -> { Boolean c = staticWidthFor(null); return c != null ? !c : !staticBackgroundWidth.on(); });
                g.add(backgroundHeight).hideIf(() -> { Boolean c = heightCondition(); return c != null ? !c : !staticBackgroundHeight.on(); });
                g.group(border, b -> b.add(borderThickness));
            }).hideIf(() -> backgroundCondition() != null || backgroundHidden());
            if (alignment != null) s.add(alignment);
            if (showHudIcons != null) s.add(showHudIcons);
            if (showText != null) s.add(showText).hideIf(() -> !canHideText());
        });

        page.section("colorOptions", s -> s.add(textColor).hideIf(this::textColorHidden));
        attach(page, brackets, bracketColor);
        attach(page, background, backgroundColor);
        attach(page, border, borderColor);
    }

    private static void attach(Page page, com.example.lunarforge.module.setting.Setting<?> parent, ColorSetting color) {
        page.under(parent, color);
    }
}
