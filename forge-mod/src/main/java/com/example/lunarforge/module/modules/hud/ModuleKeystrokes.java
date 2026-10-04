package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.modules.hud.keystrokes.KeyAnimations;
import com.example.lunarforge.module.modules.hud.keystrokes.KeystrokeKey;
import com.example.lunarforge.module.modules.hud.keystrokes.KeystrokeKey.Key;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;

public final class ModuleKeystrokes extends Module {
    public enum Mode implements ChoiceSetting.Option {
        GROUPED("grouped"), INDIVIDUAL("individual");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public final ChoiceSetting<Mode> keystrokesMode = choice("keystrokesMode", Mode.GROUPED);
    private final BoolSetting keyStrokesClicks = bool("keyStrokesClicks", true);
    private final BoolSetting leftCPS = bool("leftCPS", false);
    private final BoolSetting rightCPS = bool("rightCPS", false);
    private final BoolSetting keyStrokesMovement = bool("keyStrokesMovement", true);
    private final BoolSetting keyStrokesSpacebar = bool("keyStrokesSpacebar", true);
    public final BoolSetting useArrows = bool("useArrows", false);
    private final BoolSetting textShadow = bool("textShadow", false);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting innerBorder = bool("innerBorder", false);
    private final ColorSetting borderColor = color("borderColor", 0xFFFFFFFF);
    private final ColorSetting textColor = color("textColor", 0xFFFFFFFF);
    private final ColorSetting textPressedColor = color("textPressedColor", 0xFF000000);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting backgroundPressedColor = color("backgroundPressedColor", 0x6FFFFFFF);
    private final NumberSetting boxSize = decimal("boxSize", 18.0f, 10.0f, 32.0f);
    private final NumberSetting borderThickness = decimal("borderThickness", 1.0f, 0.5f, 3.0f);
    private final NumberSetting keyFadeDelay = integer("keyFadeDelay", 75, 0, 500);
    public final NumberSetting spacebarThickness = decimal("spacebarThickness", 1.0f, 1.0f, 4.25f);
    private final BoolSetting animate = bool("animate", false);
    private final ChoiceSetting<KeyAnimations.Type> animationType = choice("animationType", KeyAnimations.Type.STACKED);
    private final ChoiceSetting<KeyAnimations.Timer> timerType = choice("timerType", KeyAnimations.Timer.HALF);
    private final ChoiceSetting<KeyAnimations.Animation> animation = choice("animation", KeyAnimations.Animation.RIPPLE);
    private final BoolSetting animateColor = bool("animateColor", false);
    private final ColorSetting animationStartColor = color("animationStartColor", 0xC0FFFFFF);
    private final ColorSetting animationCenterColor = color("animationCenterColor", 0x6FFFFFFF);
    private final ColorSetting animationEndColor = color("animationEndColor", 0x6FFFFFFF);
    private final NumberSetting duration = decimal("duration", 0.5f, 0.1f, 1.0f);
    private final ChoiceSetting<KeyAnimations.Timing> timingFunction = choice("timingFunction", KeyAnimations.Timing.LINEAR);

    private final Map<Key, KeystrokeKey> keys = new EnumMap<Key, KeystrokeKey>(Key.class);
    private final Group group;
    private float groupWidth, groupHeight;

    private final KeystrokeKey.Style style = new KeystrokeKey.Style() {
        @Override public boolean textShadow() { return textShadow.on(); }
        @Override public boolean border() { return false; }
        @Override public ColorSetting borderColor() { return borderColor; }
        @Override public ColorSetting textColor() { return textColor; }
        @Override public ColorSetting textPressedColor() { return textPressedColor; }
        @Override public ColorSetting backgroundColor() { return backgroundColor; }
        @Override public ColorSetting backgroundPressedColor() { return backgroundPressedColor; }
        @Override public float boxSize() { return boxSize.value(); }
        @Override public float borderThickness() { return borderThickness.value(); }
        @Override public long fadeDelay() { return keyFadeDelay.intValue(); }
        @Override public boolean animate() { return animate.on(); }
        @Override public KeyAnimations.Type animationType() { return animationType.get(); }
        @Override public KeyAnimations.Timer timerType() { return timerType.get(); }
        @Override public KeyAnimations.Animation animation() { return animation.get(); }
        @Override public boolean animateColor() { return animateColor.on(); }
        @Override public ColorSetting startColor() { return animationStartColor; }
        @Override public ColorSetting centerColor() { return animationCenterColor; }
        @Override public ColorSetting endColor() { return animationEndColor; }
        @Override public float duration() { return duration.value(); }
        @Override public KeyAnimations.Timing timing() { return timingFunction.get(); }
        @Override public boolean showCps(Key key) { return key == Key.MOUSE1 ? leftCPS.on() : key == Key.MOUSE2 && rightCPS.on(); }
    };

    public ModuleKeystrokes() {
        super("KEYSTROKES", false);
        group = hud(new Group());
        childRowsLast = true;
        for (Key key : Key.values()) keys.put(key, child(new KeystrokeKey(this, key), null));
    }

    public boolean individual() { return keystrokesMode.is(Mode.INDIVIDUAL); }

    private boolean grouped() { return keystrokesMode.is(Mode.GROUPED); }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(keystrokesMode);
            s.add(group.scale).hideIf(() -> !grouped());
            s.group(keyStrokesClicks, g -> g.add(leftCPS, rightCPS)).hideIf(() -> !grouped());
            s.add(useArrows);
            s.add(keyStrokesMovement, keyStrokesSpacebar, textShadow).hideIf(() -> !grouped());
            s.group(border, g -> g.add(innerBorder)).hideIf(() -> !grouped());
            s.group(animate, g -> {
                g.add(animationType, timerType, animation, duration, timingFunction);
                g.group(animateColor, c -> c.add(animationStartColor, animationCenterColor, animationEndColor));
            }).hideIf(() -> !grouped());
            s.add(borderThickness, boxSize, borderColor, textColor, textPressedColor, backgroundColor, backgroundPressedColor).hideIf(() -> !grouped());
            s.add(keyFadeDelay).hideIf(animate::on);
            s.add(spacebarThickness);
        });
    }

    private boolean shown(Key key) {
        if (key.movement() && !keyStrokesMovement.on()) return false;
        if (key.mouse() && !keyStrokesClicks.on()) return false;
        return key != Key.SPACE || keyStrokesSpacebar.on();
    }

    private float box(Key key) { return grouped() ? boxSize.value() : keys.get(key).boxSize.value(); }
    private boolean enabled(Key key) { return grouped() ? shown(key) : keys.get(key).isEnabled(); }

    private float advance(Key key) {
        if (key == Key.SPACE) return keys.get(Key.A).width(box(Key.A)) + 1 + keys.get(Key.S).width(box(Key.S)) + 1 + keys.get(Key.D).width(box(Key.D));
        if (key.mouse()) return (keys.get(Key.SPACE).width(box(Key.SPACE)) - 1) / 2;
        return box(key);
    }

    private float rowHeight(Key key) { return keys.get(key).height(box(key), enabled(key)); }

    public void arrange() {
        for (KeystrokeKey k : keys.values()) {
            Key key = k.key;
            float x = 0, y = 0;
            if (key == Key.W) x += advance(Key.A) + 1;
            if (key == Key.A || key == Key.S || key == Key.D || key == Key.SPACE || key.mouse()) {
                y += rowHeight(Key.W) + 1;
                if (key == Key.S || key == Key.D) x += advance(Key.A) + 1;
                if (key == Key.D) x += advance(Key.S) + 1;
                if (key == Key.SPACE || key.mouse()) {
                    y += Math.max(rowHeight(Key.A), Math.max(rowHeight(Key.S), rowHeight(Key.D))) + 1;
                    if (key.mouse()) {
                        y += rowHeight(Key.SPACE) + 1;
                        if (key == Key.MOUSE2) x += advance(Key.MOUSE1) + 1;
                    }
                }
            }
            k.layoutX = x;
            k.layoutY = y;
        }
    }

    private final class Group extends HudElement {
        Group() { super(ModuleKeystrokes.this, 0, 0, HudAnchor.TOP_LEFT); }

        @Override public void layout(Page page) {}

        @Override public boolean editable() { return grouped(); }

        @Override public boolean visible(boolean preview) {
            if (individual()) { size(0, 0); return false; }
            measure();
            return keyStrokesMovement.on() || keyStrokesClicks.on() || keyStrokesSpacebar.on();
        }

        private void measure() {
            arrange();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = 0, maxY = 0;
            for (KeystrokeKey k : keys.values()) {
                float box = box(k.key), w = k.width(box), h = k.height(box, shown(k.key));
                minX = Math.min(minX, k.layoutX); minY = Math.min(minY, k.layoutY);
                maxX = Math.max(maxX, k.layoutX + w); maxY = Math.max(maxY, k.layoutY + h);
            }
            groupWidth = maxX - minX;
            groupHeight = maxY - minY;
            size(groupWidth, groupHeight);
        }

        @Override public void render(boolean preview) {
            measure();
            float box = boxSize.value(), t = borderThickness.value();
            for (KeystrokeKey k : keys.values()) {
                if (!shown(k.key)) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(k.layoutX, k.layoutY, 0);
                k.draw(style, k.width(box), k.height(box, true), k.layoutX + k.layoutY, true);
                GlStateManager.popMatrix();
            }
            boolean outer = border.on(), inner = innerBorder.on();
            if (outer && !inner) {
                float top = 0, height = groupHeight;
                boolean clicks = keyStrokesClicks.on(), space = keyStrokesSpacebar.on();
                if (keyStrokesMovement.on()) {
                    if (!clicks && !space) height -= 2;
                    else if (!clicks) height -= 1;
                    top += box + 1;
                    height -= box + 1;
                } else {
                    top += clicks && space || space ? 2 : 3;
                    height -= clicks && space ? 2 : 3;
                }
                float w = groupWidth;
                if (!keyStrokesMovement.on()) {
                    Draw.border(borderColor, 0, top, w, height, t);
                } else {
                    Draw.fill(borderColor, -t, top, t, height);
                    Draw.fill(borderColor, w, top, t, height);
                    Draw.fill(borderColor, -t, top + height, w + t * 2, t);
                    Draw.fill(borderColor, -t, top - t, box + t / 2 + 1, t);
                    Draw.fill(borderColor, w - box - 1, top - t, box + t + 1, t);
                    Draw.fill(borderColor, box - t + 1, -t, t, box + 1 + t);
                    Draw.fill(borderColor, box * 2 + 1, -t, t, box + 1 + t);
                    Draw.fill(borderColor, box + 1, -t, box + 1, t);
                }
            }
            if (outer && inner) {
                for (KeystrokeKey k : keys.values()) {
                    if (!shown(k.key)) continue;
                    Draw.border(borderColor, k.layoutX, k.layoutY, k.width(box), k.height(box, true), t);
                }
            }
        }
    }
}
