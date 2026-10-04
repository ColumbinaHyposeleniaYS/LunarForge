package com.example.lunarforge.module.modules.hud.keystrokes;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.modules.hud.ModuleKeystrokes;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.ClickCounter;
import java.awt.Color;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class KeystrokeKey extends Module {
    public enum Key {
        W("forward"), A("left"), S("back"), D("right"), SPACE("jump"), MOUSE1("attack"), MOUSE2("use");
        final String nameId;
        Key(String nameId) { this.nameId = nameId; }
        public boolean mouse() { return this == MOUSE1 || this == MOUSE2; }
        public boolean movement() { return this == W || this == A || this == S || this == D; }
        KeyBinding binding(GameSettings gs) {
            switch (this) {
                case W: return gs.keyBindForward;
                case A: return gs.keyBindLeft;
                case S: return gs.keyBindBack;
                case D: return gs.keyBindRight;
                case SPACE: return gs.keyBindJump;
                case MOUSE1: return gs.keyBindAttack;
                default: return gs.keyBindUseItem;
            }
        }
    }

    public interface Style {
        boolean textShadow();
        boolean border();
        ColorSetting borderColor();
        ColorSetting textColor();
        ColorSetting textPressedColor();
        ColorSetting backgroundColor();
        ColorSetting backgroundPressedColor();
        float boxSize();
        float borderThickness();
        long fadeDelay();
        boolean animate();
        KeyAnimations.Type animationType();
        KeyAnimations.Timer timerType();
        KeyAnimations.Animation animation();
        boolean animateColor();
        ColorSetting startColor();
        ColorSetting centerColor();
        ColorSetting endColor();
        float duration();
        KeyAnimations.Timing timing();
        boolean showCps(Key key);
    }

    public final Key key;
    private final ModuleKeystrokes parent;
    final BoolSetting showCps = bool("showCps", false);
    final BoolSetting textShadow = bool("textShadow", false);
    final BoolSetting border = bool("border", false);
    final ColorSetting borderColor = color("borderColor", 0xFFFFFFFF);
    final ColorSetting textColor = color("textColor", 0xFFFFFFFF);
    final ColorSetting textPressedColor = color("textPressedColor", 0xFF000000);
    final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    final ColorSetting backgroundPressedColor = color("backgroundPressedColor", 0x6FFFFFFF);
    public final NumberSetting boxSize = decimal("boxSize", 18.0f, 10.0f, 32.0f);
    final NumberSetting borderThickness = decimal("borderThickness", 1.0f, 0.5f, 3.0f);
    final NumberSetting keyFadeDelay = integer("keyFadeDelay", 75, 0, 500);
    final BoolSetting animate = bool("animate", true);
    final ChoiceSetting<KeyAnimations.Type> animationType = choice("animationType", KeyAnimations.Type.STACKED);
    final ChoiceSetting<KeyAnimations.Timer> timerType = choice("timerType", KeyAnimations.Timer.HALF);
    final ChoiceSetting<KeyAnimations.Animation> animation = choice("animation", KeyAnimations.Animation.RIPPLE);
    final BoolSetting animateColor = bool("animateColor", false);
    final ColorSetting animationStartColor = color("animationStartColor", 0xC0FFFFFF);
    final ColorSetting animationCenterColor = color("animationCenterColor", 0x6FFFFFFF);
    final ColorSetting animationEndColor = color("animationEndColor", 0x6FFFFFFF);
    final NumberSetting duration = decimal("duration", 0.5f, 0.1f, 1.0f);
    final ChoiceSetting<KeyAnimations.Timing> timingFunction = choice("timingFunction", KeyAnimations.Timing.LINEAR);

    private final Style own = new Style() {
        @Override public boolean textShadow() { return textShadow.on(); }
        @Override public boolean border() { return border.on(); }
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
        @Override public boolean showCps(Key k) { return showCps.on(); }
    };

    public final Hud hud;

    public float layoutX, layoutY;

    public KeystrokeKey(ModuleKeystrokes parent, Key key) {
        super("KEYSTROKE_KEY_" + key.name(), true);
        this.parent = parent;
        this.key = key;
        hud = hud(new Hud());
        rowShownWhen(() -> parent.individual());
        LunarLang.registerName(id, () -> LunarLang.get("features.KEYSTROKES.info", key.nameId));
    }

    public Style style() { return own; }

    @Override protected void layout(Page page) {
        if (key.mouse()) page.add(showCps);
        page.add(textShadow, border);
        page.group(animate, g -> {
            g.add(animationType, timerType, animation, duration, timingFunction);
            g.group(animateColor, c -> c.add(animationStartColor, animationCenterColor, animationEndColor));
        });
        page.add(borderThickness, boxSize, borderColor, textColor, textPressedColor, backgroundColor, backgroundPressedColor);
        page.add(keyFadeDelay).hideIf(animate::on);
    }

    String label(float box) {
        Minecraft mc = Minecraft.getMinecraft();
        String text;
        if (key == Key.MOUSE1) text = LunarLang.get("features.KEYSTROKES.info", "lmb");
        else if (key == Key.MOUSE2) text = LunarLang.get("features.KEYSTROKES.info", "rmb");
        else text = GameSettings.getKeyDisplayString(key.binding(mc.gameSettings).getKeyCode()).toUpperCase(Locale.ROOT);
        if (key.movement() && parent.useArrows.on()) {
            switch (key) {
                case W: text = "▲"; break;
                case A: text = "◀"; break;
                case S: text = "▼"; break;
                default: text = "▶"; break;
            }
        }
        if (box < 14 && text.length() > 1) text = text.substring(0, 1);
        return text;
    }

    boolean pressed() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return false;
        int code = key.binding(mc.gameSettings).getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    int cps() { return ClickCounter.of(key == Key.MOUSE1, false); }

    private boolean wasPressed;
    private KeyAnimations.Progress synced;
    private KeyAnimations.Shape syncedShape;
    private final List<Object[]> stacked = new LinkedList<Object[]>();

    private long fadeStart;
    private boolean fadeState;

    private void pressChanged(boolean now, Style style) {
        if (!style.animate()) return;
        if (style.animationType() == KeyAnimations.Type.SYNCED) {
            if (synced == null || synced.done()) {
                synced = new KeyAnimations.Held(style.duration());
                syncedShape = style.animation().create();
            }
            if (now) synced.press(); else synced.release();
        } else if (now) {
            stacked.add(new Object[]{style.timerType().create(style.duration()), style.animation().create(), style.timing()});
        }
    }

    private static int animationColor(Style style, float f, float position) {
        if (!style.animateColor()) return style.backgroundPressedColor().color(position);
        if (f < 1) return lerpHsb(style.startColor().color(position), style.centerColor().color(position), Math.max(0, Math.min(1, f)));
        return lerpHsb(style.centerColor().color(position), style.endColor().color(position), Math.max(0, Math.min(1, f - 1)));
    }

    private static int lerpHsb(int from, int to, float f) {
        float[] a = Color.RGBtoHSB(from >> 16 & 255, from >> 8 & 255, from & 255, null);
        float[] b = Color.RGBtoHSB(to >> 16 & 255, to >> 8 & 255, to & 255, null);
        float h1 = a[0], h2 = b[0];
        if (h2 - h1 > 0.5f) h1 += 1; else if (h2 - h1 < -0.5f) h2 += 1;
        float h = (h1 + (h2 - h1) * f) % 1.0f, s = a[1] + (b[1] - a[1]) * f, v = a[2] + (b[2] - a[2]) * f;
        int rgb = Color.HSBtoRGB(h, s, v);
        int alpha = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * f);
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private int fadeColor(Style style, boolean pressed, float position) {
        long now = System.currentTimeMillis();
        if (pressed != fadeState) { fadeState = pressed; fadeStart = now; }
        long length = style.fadeDelay();
        if (fadeStart != 0 && now - fadeStart < length) {
            float f = (now - fadeStart) / (float)length;
            int from = (fadeState ? style.backgroundColor() : style.backgroundPressedColor()).color(position);
            int to = (fadeState ? style.backgroundPressedColor() : style.backgroundColor()).color(position);
            int out = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int c = (int)Math.abs(f * (to >>> shift & 255) + (1 - f) * (from >>> shift & 255));
                out |= (c & 255) << shift;
            }
            return out;
        }
        return (pressed ? style.backgroundPressedColor() : style.backgroundColor()).color(position);
    }

    public void draw(Style style, float w, float h, float position, boolean grouped) {
        boolean down = pressed();
        if (down != wasPressed) { wasPressed = down; pressChanged(down, style); }
        if (style.animate()) Draw.rect(0, 0, w, h, style.backgroundColor().color(position));
        else Draw.rect(0, 0, w, h, fadeColor(style, down, position));

        Iterator<Object[]> it = stacked.iterator();
        while (it.hasNext()) {
            Object[] a = it.next();
            KeyAnimations.Progress timer = (KeyAnimations.Progress)a[0];
            if (timer.done()) { it.remove(); continue; }
            float f = ((KeyAnimations.Timing)a[2]).compute(Math.max(0, Math.min(1, timer.progress())));
            ((KeyAnimations.Shape)a[1]).draw(w, h, f, animationColor(style, timer.raw(), position));
        }
        if (style.animationType() == KeyAnimations.Type.SYNCED && synced != null && syncedShape != null) {
            float f = Math.max(0, Math.min(1, synced.progress()));
            if (f != 0) syncedShape.draw(w, h, f, animationColor(style, synced.raw(), position));
        }
        ColorSetting color = down ? style.textPressedColor() : style.textColor();
        boolean shadow = style.textShadow();
        GlStateManager.enableBlend();
        if (key == Key.SPACE) {
            float thickness = parent.spacebarThickness.value();
            int c = color.color(w / 2 + 3);
            float x = w / 2 - w / 6, bw = w / 3;
            if (shadow) Draw.rect(x + 1, 3 + 1, bw, thickness, Draw.shadow(c));
            Draw.rect(x, 3, bw, thickness, c);
        } else {
            float fh = Draw.fontHeight(), cx = w / 2, y;
            if (style.showCps(key)) {
                y = h - fh + 2;
                float s = 0.6f;
                GlStateManager.pushMatrix();
                GlStateManager.scale(s, s, 1);
                String cps = cps() + " " + LunarLang.get("shared_info", "cps");
                Draw.text(cps, cx / s - Draw.width(cps) / 2, y / s, Draw.textAlpha(color.color(cx + y)), shadow);
                GlStateManager.popMatrix();
                y = h / 4 - fh / 4;
            } else {
                y = h / 2 - fh / 2 + 1;
            }
            String text = label(style.boxSize());
            Draw.text(text, cx - Draw.width(text) / 2, y, Draw.textAlpha(color.color(cx + y)), shadow);
        }
        if (!grouped && style.border()) Draw.border(style.borderColor(), 0, 0, w, h, style.borderThickness());
    }

    public float width(float box) {
        if (key == Key.SPACE) return box * 3 + 2;
        if (key.mouse()) return (box * 3 + 1) / 2;
        return box;
    }

    public float height(float box, boolean shown) {
        if (!shown) return 0;
        return key == Key.SPACE ? box / 2 : box;
    }

    public final class Hud extends HudElement {
        Hud() { super(KeystrokeKey.this, 0, 0, HudAnchor.TOP_LEFT); }

        @Override public float defaultX() { parent.arrange(); return layoutX; }
        @Override public float defaultY() { parent.arrange(); return layoutY; }

        @Override public boolean visible(boolean preview) {
            if (!isEnabled() || !parent.individual()) { size(0, 0); return false; }
            float box = boxSize.value();
            size(KeystrokeKey.this.width(box), KeystrokeKey.this.height(box, true));
            return true;
        }

        @Override public boolean editable() { return parent.individual(); }

        @Override public void render(boolean preview) {
            draw(own, width(), height(), 0, false);
        }
    }
}
