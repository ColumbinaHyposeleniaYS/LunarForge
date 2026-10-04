package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.time.DurationFormatUtils;

public final class ModulePotionEffects extends Module {
    public enum Mode implements ChoiceSetting.Option {
        NORMAL("normal"), MINIMAL("minimal"), VANILLA("vanilla");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum BarPosition implements ChoiceSetting.Option {
        LEFT("left"), RIGHT("right"), TOP("top"), BOTTOM("bottom"), BORDER("border");
        private final String id;
        BarPosition(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum Corner implements ChoiceSetting.Option {
        TOP_LEFT("topLeft"), BOTTOM_LEFT("bottomLeft"), TOP_RIGHT("topRight"), BOTTOM_RIGHT("bottomRight");
        private final String id;
        Corner(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private enum Side { TOP, BOTTOM, RIGHT, LEFT; boolean vertical() { return ordinal() < 2; } }

    private static ModulePotionEffects instance;
    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");

    private static final ResourceLocation INVENTORY_LEGACY = new ResourceLocation("lunarforge", "textures/misc/potions_inventory_legacy.png");

    private static final Map<Integer, Integer> COLORS = new HashMap<Integer, Integer>();
    static {
        int[] c = {-11141121, -10851199, -2506685, -11910633, -7134173, -515037, -5675670, -1, -11199158, -3318613, -7387667,
            -1795526, -13741415, -8420462, -14737629, -14737503, -10979757, -12038840, -11627727, -13293017, -492253, -14331227,
            -515037, -7036831, -3211265, -10895098, -4152243};
        for (int i = 0; i < c.length; i++) COLORS.put(i + 1, c[i]);
    }

    private final ChoiceSetting<Mode> potionEffectsMode = choice("potionEffectsMode", Mode.NORMAL);
    private final BoolSetting minimalModeHorizontal = bool("minimalModeHorizontal", false);
    private final NumberSetting minimalModeTilesPerLine = integer("minimalModeTilesPerLine", 4, 1, 10);
    private final BoolSetting vanillaGroupEffects = bool("vanillaGroupEffects", true);
    private final BoolSetting vanillaModeHorizontal = bool("vanillaModeHorizontal", true);
    private final NumberSetting vanillaModeTilesPerLine = integer("vanillaModeTilesPerLine", 10, 1, 10);
    private final BoolSetting vanillaSpacing = bool("vanillaSpacing", false);
    private final BoolSetting effectName = bool("effectName", true);
    private final BoolSetting effectDuration = bool("effectDuration", true);
    private final BoolSetting effectAmplifier = bool("effectAmplifier", true);
    private final BoolSetting showInInventory = bool("showInInventory", true);
    private final BoolSetting showWhileTyping = bool("showWhileTyping", true);
    private final BoolSetting potionBlink = bool("potionBlink", true);
    private final BoolSetting potionBlinkIcon = bool("potionBlinkIcon", false);
    private final NumberSetting blinkDuration = integer("blinkDuration", 10, 2, 20);
    private final BoolSetting uppercasePotionNames = bool("uppercasePotionNames", false);
    private final BoolSetting reversedText = bool("reversedText", false);
    private final BoolSetting formattedDurations = bool("formattedDurations", false);
    private final BoolSetting hidePotionStatus = bool("hidePotionStatus", false);
    private final BoolSetting background = bool("background", true);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting borderColor = color("borderColor", 0x9F000000);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting colorNameBasedOnEffect = bool("colorNameBasedOnEffect", false);
    private final BoolSetting colorInfoBasedOnEffect = bool("colorInfoBasedOnEffect", false);
    private final ColorSetting textColor = color("textColor", 0xFFFFFFFF);
    private final ColorSetting infoColor = color("infoColor", 0xFFFFFFFF);
    private final BoolSetting excludePerm = bool("excludePerm", false);
    private final BoolSetting effectBars = bool("effectBars", false);
    private final ChoiceSetting<BarPosition> effectBarPosition = choice("effectBarPosition", BarPosition.BOTTOM);
    private final BoolSetting effectBarVanillaHud = bool("effectBarVanillaHud", true);
    private final BoolSetting effectBarInventory = bool("effectBarInventory", true);
    private final BoolSetting effectBarLunarHud = bool("effectBarLunarHud", false);
    private final BoolSetting effectBarGradient = bool("effectBarGradient", true);
    private final BoolSetting effectBarCustomColor = bool("effectBarCustomColor", false);
    private final ColorSetting effectBarColor = color("effectBarColor", 0xFFFFFFFF).noAlpha();
    private final BoolSetting showEffectBackground = bool("showEffectBackground", true);
    private final ChoiceSetting<Corner> durationPosition = choice("durationPosition", Corner.BOTTOM_LEFT);
    private final ChoiceSetting<Corner> amplifierPosition = choice("amplifierPosition", Corner.TOP_RIGHT);
    private final NumberSetting vanillaIconScale = decimal("vanillaIconScale", 1.0f, 0.5f, 1.25f);
    private final NumberSetting vanillaTextScale = decimal("vanillaTextScale", 1.0f, 0.25f, 2.0f);
    private final BoolSetting useMinecraftGUIScale = bool("useMinecraftGUIScale", false);
    private final ColorSetting vanillaBlinkColor = color("vanillaBlinkColor", 0xFFFFFFFF);

    private static final boolean HIDE_AMBIENT_DURATION = false, EFFECT_BAR_HIDE_AMBIENT = true;

    private final Map<Integer, BoolSetting> excludes = new LinkedHashMap<Integer, BoolSetting>();

    private final Hud hud;

    private final Map<Integer, int[]> maxDurations = new HashMap<Integer, int[]>();
    private final Map<Integer, PotionEffect> trackedEffects = new HashMap<Integer, PotionEffect>();

    public ModulePotionEffects() {
        super("POTION_EFFECTS", true);
        instance = this;
        String[] ids = {"excludeSpeed", "excludeSlowness", "excludeHaste", "excludeMiningFatigue", "excludeStrength",
            "excludeInstantHealth", "excludeInstantDamage", "excludeJumpBoost", "excludeNausea", "excludeRegen",
            "excludeResistance", "excludeFireRes", "excludeWaterBreathing", "excludeInvis", "excludeBlindness",
            "excludeNightVision", "excludeHunger", "excludeWeakness", "excludePoison", "excludeWither",
            "excludeHealthBoost", "excludeAbsorption", "excludeSaturation"};
        for (int i = 0; i < ids.length; i++) excludes.put(i + 1, bool(ids[i], false));
        hud = hud(new Hud());
    }

    private boolean mode(Mode m) { return potionEffectsMode.get() == m; }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(potionEffectsMode);
            s.add(vanillaGroupEffects, vanillaModeHorizontal, vanillaModeTilesPerLine, vanillaSpacing).hideIf(() -> !mode(Mode.VANILLA));
            s.add(minimalModeHorizontal, minimalModeTilesPerLine).hideIf(() -> !mode(Mode.MINIMAL));
            s.add(effectName).hideIf(() -> !mode(Mode.NORMAL));
            s.add(effectDuration);
            s.add(effectAmplifier).hideIf(() -> mode(Mode.MINIMAL));
            s.add(formattedDurations).hideIf(() -> mode(Mode.VANILLA));
            s.add(uppercasePotionNames, reversedText).hideIf(() -> !mode(Mode.NORMAL));
            s.add(showWhileTyping);
            s.group(potionBlink, b -> {
                b.add(potionBlinkIcon, blinkDuration);
                b.add(vanillaBlinkColor).hideIf(() -> !mode(Mode.VANILLA));
            });
            s.add(showInInventory);
            s.add(hidePotionStatus);
            s.group(effectBars, b -> {
                b.add(effectBarPosition, effectBarVanillaHud, effectBarInventory, effectBarLunarHud);
                b.add(effectBarGradient).hideIf(() -> effectBarCustomColor.on() && effectBarColor.chroma());
                b.group(effectBarCustomColor, c -> c.add(effectBarColor));
            });
        });
        page.section("hudOptions", s -> {
            s.add(durationPosition, amplifierPosition).hideIf(() -> !mode(Mode.VANILLA));
            s.group(background, b -> b.add(backgroundColor));
            s.group(border, b -> b.add(borderThickness, borderColor));
            s.add(textShadow);
            s.add(colorNameBasedOnEffect).hideIf(() -> !mode(Mode.NORMAL));
            s.add(colorInfoBasedOnEffect);
            s.add(showEffectBackground).hideIf(() -> !mode(Mode.VANILLA));
            s.add(textColor).hideIf(() -> colorNameBasedOnEffect.on() || !mode(Mode.NORMAL));
            s.add(infoColor).hideIf(colorInfoBasedOnEffect::on);
            s.add(vanillaIconScale, vanillaTextScale, useMinecraftGUIScale).hideIf(() -> !mode(Mode.VANILLA));
            s.add(hud.scale).hideIf(this::minecraftScale);
        });

        page.section("excludePotionEffects", s -> {
            s.add(excludePerm);
            List<BoolSetting> sorted = new ArrayList<BoolSetting>(excludes.values());
            Collections.sort(sorted, Comparator.comparing(b -> LunarLang.get("settings", b.key)));
            for (BoolSetting b : sorted) s.add(b);
        });
    }

    private boolean minecraftScale() { return mode(Mode.VANILLA) && useMinecraftGUIScale.on(); }

    private boolean barsOnHud() { return effectBars.on() && effectBarLunarHud.on(); }

    private boolean excluded(PotionEffect effect) {
        if (excludePerm.on() && permanent(effect)) return true;
        BoolSetting b = excludes.get(effect.getPotionID());
        return b != null && b.on();
    }

    static boolean permanent(PotionEffect effect) {
        return effect.getIsPotionDurationMax() || effect.getDuration() == -1 || effect.getDuration() > 72000;
    }

    static int effectColor(PotionEffect effect) {
        Integer c = COLORS.get(effect.getPotionID());
        if (c != null) return c;
        Potion potion = potion(effect);
        return potion == null || !potion.isBadEffect() ? -15691760 : -7335920;
    }

    private static Potion potion(PotionEffect effect) {
        int id = effect.getPotionID();
        return id >= 0 && id < Potion.potionTypes.length ? Potion.potionTypes[id] : null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        trackMaxDuration();
        if (isEnabled()) hud.tick();
    }

    private void trackMaxDuration() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) { maxDurations.clear(); trackedEffects.clear(); return; }
        Map<Integer, PotionEffect> now = new HashMap<Integer, PotionEffect>();
        for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) {
            int id = effect.getPotionID();
            now.put(id, effect);
            int[] max = maxDurations.get(id);
            if (max == null || trackedEffects.get(id) != effect || effect.getAmplifier() > max[1]) {
                max = new int[]{0, effect.getAmplifier()};
                maxDurations.put(id, max);
            }
            max[0] = Math.max(max[0], effect.getDuration());
            max[1] = effect.getAmplifier();
        }
        trackedEffects.clear();
        trackedEffects.putAll(now);
        maxDurations.keySet().retainAll(now.keySet());
    }

    private int maxDuration(PotionEffect effect) {
        int[] max = maxDurations.get(effect.getPotionID());
        return max == null || trackedEffects.get(effect.getPotionID()) != effect ? 0 : max[0];
    }

    public static boolean hideInInventory() {
        return instance != null && instance.isEnabled() && !instance.showInInventory.on();
    }

    public static void inventoryRow(InventoryEffectRenderer gui, int x, int y, int u, int v, int w, int h, PotionEffect effect) {
        gui.drawTexturedModalRect(x, y, u, v, w, h);
        ModulePotionEffects m = instance;
        if (m != null && m.isEnabled() && m.effectBars.on() && m.effectBarInventory.on() && effect != null) {
            m.bar(effect, x + 3, y + 3, 114.0f, 26.0f);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            Minecraft.getMinecraft().getTextureManager().bindTexture(INVENTORY);
        }
    }

    private void bar(PotionEffect effect, float x, float y, float w, float h) {
        if (effect.getIsAmbient() && EFFECT_BAR_HIDE_AMBIENT || permanent(effect)) return;
        float progress = progress(effect);
        if (progress <= 0.0f) return;
        float t = 1.0f;
        switch (effectBarPosition.get()) {
            case LEFT: vertical(effect, x, y, t, h, progress); break;
            case RIGHT: vertical(effect, x + w - t, y, t, h, progress); break;
            case TOP: horizontal(effect, x, y, w, t, progress); break;
            case BOTTOM: horizontal(effect, x, y + h - t, w, t, progress); break;
            case BORDER: around(effect, x, y, w, h, t, progress); break;
        }
    }

    private float progress(PotionEffect effect) {
        float max = maxDuration(effect);
        return max <= 0.0f ? 1.0f : Math.max(0.0f, Math.min(1.0f, effect.getDuration() / max));
    }

    private void horizontal(PotionEffect e, float x, float y, float w, float h, float p) {
        segment(e, x, y, w * p, h, Side.LEFT, p, 0.0f);
    }

    private void vertical(PotionEffect e, float x, float y, float w, float h, float p) {
        float fill = h * p;
        segment(e, x, y + h - fill, w, fill, Side.BOTTOM, p, 0.0f);
    }

    private void around(PotionEffect e, float x, float y, float w, float h, float t, float p) {
        float inner = h - t * 2.0f;
        float total = (w + inner) * 2.0f;
        float a = w / total, b = (w + inner) / total, c = (w * 2.0f + inner) / total;
        float left = p * total;
        float len = Math.min(left, w);
        if (len > 0.0f) segment(e, x, y + h - t, len, t, Side.LEFT, len / total, 0.0f);
        left -= w;
        if ((len = Math.min(left, inner)) > 0.0f) segment(e, x + w - t, y + h - t - len, t, len, Side.BOTTOM, a + len / total, a);
        left -= inner;
        if ((len = Math.min(left, w)) > 0.0f) segment(e, x + w - len, y, len, t, Side.RIGHT, b + len / total, b);
        left -= w;
        if ((len = Math.min(left, inner)) > 0.0f) segment(e, x, y + t, t, len, Side.TOP, c + len / total, c);
    }

    private void segment(PotionEffect effect, float x, float y, float w, float h, Side side, float p0, float p1) {
        int base = effectColor(effect);
        boolean custom = effectBarCustomColor.on();
        boolean chroma = custom && effectBarColor.chroma();
        boolean pulse = !chroma && effectBarGradient.on();
        if (!chroma && !pulse) {
            Draw.rect(x, y, w, h, custom ? effectBarColor.argb() : base);
            return;
        }
        boolean along = !side.vertical();
        float length = along ? w : h, thick = along ? h : w;
        float start = along ? x : y, cross = along ? y : x;
        float mid = cross + thick / 2.0f;
        boolean forward = side == Side.LEFT || side == Side.TOP;
        float from = forward ? p1 : p0, to = forward ? p0 : p1;
        int plain = custom ? effectBarColor.color(mid + start) : base;
        int previous = pulse ? pulse(plain, from) : plain;
        float pos = start;
        for (int i = 1; i <= 6; i++) {
            float f = i / 6.0f;
            float last = pos;
            pos = start + length * f;
            int c = chroma ? effectBarColor.color(mid + pos) : plain;
            if (pulse) c = pulse(c, from + (to - from) * f);
            if (along) Draw.gradient(last, cross, pos, cross + thick, previous, c, true);
            else Draw.gradient(cross, last, cross + thick, pos, previous, c, false);
            previous = c;
        }
    }

    private static int pulse(int argb, float at) {
        double d = at + System.nanoTime() / 2.5E9;
        float f = (float)(d - Math.floor(d));
        f = 0.4f + 0.6f * (f < 0.5f ? f * 2.0f : 2.0f - f * 2.0f);
        if (f >= 1.0f) return argb;
        if (f <= 0.0f) return argb & 0xFF000000;
        return argb & 0xFF000000 | (int)((argb >> 16 & 255) * f) << 16 | (int)((argb >> 8 & 255) * f) << 8 | (int)((argb & 255) * f);
    }

    private final class Hud extends HudElement {
        private List<PotionEffect> samples;
        private final List<PotionEffect> effects = new ArrayList<PotionEffect>();

        private final int[] blinkTicks = new int[3];

        private final List<PotionEffect> good = new ArrayList<PotionEffect>(), bad = new ArrayList<PotionEffect>();
        private boolean preview;

        Hud() { super(ModulePotionEffects.this, 0.0f, 0.0f, HudAnchor.MIDDLE_LEFT); }

        private List<PotionEffect> samples() {
            if (samples == null) samples = Arrays.asList(new PotionEffect(1, 1200, 3), new PotionEffect(5, 30, 3));
            return samples;
        }

        @Override public void layout(Page page) {}

        @Override public float scale() { return minecraftScale() ? 1.0f : super.scale(); }

        void tick() {
            effects.clear();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                for (PotionEffect effect : mc.thePlayer.getActivePotionEffects()) if (!excluded(effect)) effects.add(effect);
            }
            preview = mc.currentScreen instanceof com.example.lunarforge.gui.LunarMovementScreen;
            if (effects.isEmpty() && preview) effects.addAll(samples());
            blinkTicks[potionEffectsMode.get().ordinal()]++;
            if (mode(Mode.VANILLA)) group();
        }

        @Override public boolean visible(boolean preview) {
            if (hidePotionStatus.on()) { size(0, 0); return false; }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && !showWhileTyping.on() && mc.ingameGUI.getChatGUI().getChatOpen()) { size(0, 0); return false; }
            if (!preview && effects.isEmpty()) { size(0, 0); return false; }
            List<PotionEffect> list = effects.isEmpty() && preview ? samples() : effects;
            if (mode(Mode.VANILLA) && list == samples) group(list);
            draw(list, false);
            return true;
        }

        @Override public void render(boolean preview) {
            draw(effects.isEmpty() && preview ? samples() : effects, true);
        }

        private void draw(List<PotionEffect> list, boolean paint) {
            switch (potionEffectsMode.get()) {
                case NORMAL: normal(list, paint); break;
                case MINIMAL: minimal(list, paint); break;
                case VANILLA: vanilla(paint); break;
            }
        }

        private void box(float x, float y, float w, float h) {
            if (background.on()) Draw.fill(backgroundColor, x, y, w, h);
            if (border.on()) Draw.border(borderColor, x, y, w, h, borderThickness.value());
        }

        private void icon(PotionEffect effect, float x, float y) {
            Potion potion = potion(effect);
            if (potion == null || !potion.hasStatusIcon()) return;
            int n = potion.getStatusIconIndex();
            Draw.blit(INVENTORY, x, y, n % 8 * 18, 198 + n / 8 * 18, 18, 18, 256, 256, (int)(iconAlpha(effect) * 255.0f) << 24 | 0xFFFFFF);
        }

        private void text(PotionEffect effect, String s, float x, float y, ColorSetting color, boolean byEffect) {
            if (byEffect) Draw.text(s, x, y, effectColor(effect), textShadow.on());
            else Draw.text(color, s, x, y, textShadow.on());
        }

        private String duration(PotionEffect effect) {
            if (mode(Mode.VANILLA)) {
                if (permanent(effect)) return "∞";
                int s = effect.getDuration() / 20;
                if (s >= 3600) return s / 3600 + "h";
                if (s >= 600) return s / 60 + "m";
                if (s >= 60) return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
                return Integer.toString(s);
            }
            if (formattedDurations.on()) return permanent(effect) ? "**:**" : formatted((long)(effect.getDuration() / 20.0f) * 1000L);
            return effect.getIsPotionDurationMax() ? "**:**" : StringUtils.ticksToElapsedTime(effect.getDuration());
        }

        private String formatted(long ms) {
            if (ms < 0L) return "now";
            String s = " " + DurationFormatUtils.formatDuration(ms, "d'd 'H'h 'm'm 's's'");
            String t = org.apache.commons.lang3.StringUtils.replaceOnce(s, " 0d", "");
            if (t.length() != s.length()) {
                s = t;
                t = org.apache.commons.lang3.StringUtils.replaceOnce(s, " 0h", "");
                if (t.length() != s.length()) {
                    s = t;
                    t = org.apache.commons.lang3.StringUtils.replaceOnce(s, " 0m", "");
                    if (t.length() != s.length()) s = org.apache.commons.lang3.StringUtils.replaceOnce(t, " 0s", "");
                }
            }
            s = s.trim();
            return s.isEmpty() ? "0s" : s;
        }

        private String amplifier(PotionEffect effect) {
            int n = effect.getAmplifier();
            switch (n) {
                case 1: return "II";
                case 2: return "III";
                case 3: return "IV";
                case 4: return "V";
                case 5: return "VI";
                case 6: return "VII";
                case 7: return "VIII";
                case 8: return "IX";
                case 9: return "X";
                default: return n > 9 ? String.valueOf(n + 1) : "";
            }
        }

        private boolean shown(PotionEffect effect) {
            if (HIDE_AMBIENT_DURATION && effect.getIsAmbient()) return !mode(Mode.VANILLA);
            if (!blinking(effect)) return true;
            int i = potionEffectsMode.get().ordinal();
            if (blinkTicks[i] > 20) blinkTicks[i] = 0;
            return blinkTicks[i] <= 10;
        }

        private boolean blinking(PotionEffect effect) {
            if (!potionBlink.on() || effect.getIsAmbient()) return false;
            int n = effect.getDuration();
            return n >= 0 && n <= blinkDuration.intValue() * 20;
        }

        private float iconAlpha(PotionEffect effect) {
            if (!potionBlinkIcon.on() || !blinking(effect)) return 1.0f;
            int n = effect.getDuration();
            int window = blinkDuration.intValue() * 20;
            float base = clamp(n * 2.0f / window, 0.0f, 0.5f);
            float swing = clamp((1.0f - (float)n / window) * 0.25f, 0.0f, 0.25f);
            return clamp(base + (float)Math.sin(n * Math.PI / 5.0) * swing, 0.0f, 1.0f);
        }

        private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

        private HudAnchor.Side horizontal() { return currentAnchor().horizontal; }

        private void normal(List<PotionEffect> list, boolean paint) {
            float x0 = 0.0f, y0 = 0.0f;
            if (paint) box(x0, y0, width(), height());
            HudAnchor.Side side = horizontal();
            if (reversedText.on()) {
                if (side == HudAnchor.Side.START) side = HudAnchor.Side.END;
                else if (side == HudAnchor.Side.END) side = HudAnchor.Side.START;
            }
            float x = x0, y = y0;
            if (side != HudAnchor.Side.MIDDLE) x += side == HudAnchor.Side.END ? -2.0f : 3.0f;
            y += 3.0f;
            float widest = 0.0f, rowY = 0.0f;
            for (PotionEffect effect : list) {
                if (paint && barsOnHud() && shown(effect)) bar(effect, x0, y0 + rowY, width(), 22.0f);
                float nameWidth = 0.0f;
                boolean name = effectName.on(), amp = effectAmplifier.on();
                if (name || amp) {
                    StringBuilder b = new StringBuilder();
                    if (name) {
                        String s = I18n.format(effect.getEffectName());
                        b.append(uppercasePotionNames.on() ? s.toUpperCase(Locale.ROOT) : s);
                        if (amp) b.append(" ");
                    }
                    if (amp) b.append(amplifier(effect));
                    String s = b.toString();
                    nameWidth = Draw.width(s) + 20.0f;
                    if (paint) text(effect, s, x + textX(side, nameWidth), y + rowY, textColor, colorNameBasedOnEffect.on());
                    widest = Math.max(widest, nameWidth);
                }
                if (effectDuration.on()) {
                    String s = duration(effect);
                    float w = Draw.width(s) + 20.0f;
                    if (paint && shown(effect)) {
                        text(effect, s, x + textX(side, w), y + rowY + (name || amp ? 10 : 5), infoColor, colorInfoBasedOnEffect.on());
                    }
                    widest = Math.max(widest, w);
                }
                if (paint) icon(effect, x + iconX(side, nameWidth), y + rowY);
                rowY += 23.0f;
            }
            if (!paint) size(Math.max(widest, 20.0f) + 7.0f, rowY - 1.0f);
        }

        private float textX(HudAnchor.Side side, float w) {
            switch (side) {
                case END: return width() - w;
                case MIDDLE: return width() / 2.0f - w / 2.0f + 20.0f;
                default: return 20.0f;
            }
        }

        private float iconX(HudAnchor.Side side, float w) {
            switch (side) {
                case END: return width() - 20.0f;
                case MIDDLE: return width() / 2.0f - w / 2.0f;
                default: return 0.0f;
            }
        }

        private void minimal(List<PotionEffect> list, boolean paint) {
            float gap = border.on() ? borderThickness.value() * 2.0f + 3.0f : 3.0f;
            float x = Math.max(0.0f, gap - 6.0f), y = Math.max(0.0f, gap - 6.0f);
            boolean horizontal = minimalModeHorizontal.on();
            HudAnchor anchor = currentAnchor();
            boolean reverse = horizontal ? anchor.vertical == HudAnchor.Side.END : anchor.horizontal == HudAnchor.Side.END;
            int tile = background.on() || border.on() ? 41 : 30;
            int perLine = minimalModeTilesPerLine.intValue();
            float step = tile + gap;
            int count = list.size();
            float shift = reverse && count > perLine ? (float)(count / perLine) * step : 0.0f;
            if (!paint) {
                int lines = (count - 1) / perLine + 1;
                int across = Math.min(count, perLine);
                float along = tile + (across - 1) * step + gap - 3.0f;
                float down = tile + (lines - 1) * step + gap - 3.0f;
                if (horizontal) size(along, down); else size(down, along);
                return;
            }
            for (int i = 0; i < count; i++) {
                int line = i / perLine, at = i % perLine;
                float a = at * step;
                float b = shift + (reverse ? -line : line) * step;
                minimalTile(list.get(i), x + (horizontal ? a : b), y + (horizontal ? b : a), tile);
            }
        }

        private void minimalTile(PotionEffect effect, float x, float y, int n) {
            box(x, y, n, n);
            if (shown(effect)) {
                if (barsOnHud()) bar(effect, x, y, n, n);
                if (effectDuration.on()) {
                    String s = duration(effect);
                    float w = Draw.width(s);
                    text(effect, s, x + n / 2.0f - w / 2.0f, y + n - n / 3.0f + 1.0f, infoColor, colorInfoBasedOnEffect.on());
                }
            }
            icon(effect, x + n / 2.0f - 9.5f, y + n / 2.0f - 13.0f);
        }

        private void group() { group(effects); }

        private void group(List<PotionEffect> list) {
            good.clear();
            bad.clear();
            boolean grouped = vanillaGroupEffects.on();
            for (PotionEffect effect : list) {
                Potion potion = potion(effect);
                (grouped && potion != null && potion.isBadEffect() ? bad : good).add(effect);
            }
            good.sort(this::compare);
            bad.sort(this::compare);
        }

        private void vanilla(boolean paint) {
            if (good.isEmpty() && bad.isEmpty()) { if (!paint) size(0.0f, 0.0f); return; }
            boolean horizontal = vanillaModeHorizontal.on();
            int perLine = vanillaModeTilesPerLine.intValue();
            int goodLines = lines(good.size(), perLine), badLines = lines(bad.size(), perLine);
            int across = Math.max(Math.min(good.size(), perLine), Math.min(bad.size(), perLine));
            float along = spanAlong(across), down = spanAcross(goodLines + badLines);
            float w = horizontal ? along : down, h = horizontal ? down : along;
            if (!paint) { size(w, h); return; }
            box(0.0f, 0.0f, w, h);
            tiles(good, horizontal, perLine, 0, along);
            tiles(bad, horizontal, perLine, goodLines, along);
        }

        private void tiles(List<PotionEffect> list, boolean horizontal, int perLine, int firstLine, float along) {
            for (int i = 0; i < list.size(); i++) {
                int line = i / perLine, at = i % perLine;
                int inLine = Math.min(list.size() - line * perLine, perLine);
                float a = alongOffset(horizontal, at, inLine, along);
                float b = (firstLine + line) * acrossStep();
                vanillaTile(list.get(i), horizontal ? a : b, horizontal ? b : a);
            }
        }

        private float alongOffset(boolean horizontal, int at, int inLine, float along) {
            float span = spanAlong(inLine);
            if (horizontal) {
                float end;
                switch (currentAnchor().horizontal) {
                    case START: end = span; break;
                    case MIDDLE: end = (along + span) / 2.0f; break;
                    default: end = along;
                }
                return end - 24.0f - at * alongStep();
            }
            float start;
            switch (currentAnchor().vertical) {
                case START: start = 0.0f; break;
                case MIDDLE: start = (along - span) / 2.0f; break;
                default: start = along - span;
            }
            return start + at * alongStep();
        }

        private int alongStep() { return vanillaSpacing.on() ? 25 : 27; }
        private int acrossStep() { return vanillaSpacing.on() ? 26 : 27; }
        private float spanAlong(int n) { return n * alongStep() - (alongStep() - 24); }
        private float spanAcross(int n) { return n * acrossStep() - (acrossStep() - 24); }
        private int lines(int n, int perLine) { return (n + perLine - 1) / perLine; }

        private void vanillaTile(PotionEffect effect, float x, float y) {
            boolean ambient = effect.getIsAmbient();
            if (showEffectBackground.on()) {
                Draw.blit(INVENTORY_LEGACY, x, y, ambient ? 165 : 141, 166, 24, 24, 256, 256, -1);
            }
            float iconScale = vanillaIconScale.value();
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 12.0f - 9.0f * iconScale, y + 12.0f - 9.0f * iconScale, 0.0f);
            GlStateManager.scale(iconScale, iconScale, 1.0f);
            icon(effect, 0.0f, 0.0f);
            GlStateManager.popMatrix();
            if (shown(effect)) {
                if (barsOnHud()) bar(effect, x + 2.0f, y + 2.0f, 20.0f, 20.0f);
                if (effectDuration.on()) {
                    boolean blink = !ambient && blinking(effect);
                    corner(effect, duration(effect), durationPosition.get(), x, y, blink ? vanillaBlinkColor : infoColor,
                        !blink && colorInfoBasedOnEffect.on());
                }
            }
            String amp;
            if (effectAmplifier.on() && !(amp = amplifier(effect)).isEmpty()) {
                corner(effect, amp, amplifierPosition.get(), x, y, infoColor, colorInfoBasedOnEffect.on());
            }
        }

        private void corner(PotionEffect effect, String s, Corner corner, float x, float y, ColorSetting color, boolean byEffect) {
            float gui = guiScale();
            float scale = vanillaTextScale.value() * 0.5f;
            if (gui > 0.0f) scale = Math.max(1, Math.round(scale * gui)) / gui;
            float w = Draw.width(s) * scale, h = Draw.fontHeight() * scale;
            boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
            boolean bottom = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT;
            float tx = x + (right ? 21.0f - w : 3.25f), ty = y + (bottom ? 22.0f - h : 3.0f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(gui > 0.0f ? Math.round(tx * gui) / gui : tx, gui > 0.0f ? Math.round(ty * gui) / gui : ty, 0.0f);
            GlStateManager.scale(scale, scale, 1.0f);
            text(effect, s, 0.0f, 0.0f, color, byEffect);
            GlStateManager.popMatrix();
        }

        private float guiScale() {
            return minecraftScale() ? new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor() : 0.0f;
        }

        private int compare(PotionEffect a, PotionEffect b) {
            boolean ambA = a.getIsAmbient(), ambB = b.getIsAmbient();
            int da = a.getDuration(), db = b.getDuration();
            if (da > 32147 && db > 32147 || ambA && ambB) {
                int n = Boolean.compare(ambB, ambA);
                return n != 0 ? n : Integer.compare(colorKey(b), colorKey(a));
            }
            int n = Boolean.compare(ambB, ambA);
            if (n == 0) n = Boolean.compare(db < 0, da < 0);
            if (n == 0) n = Integer.compare(db, da);
            if (n == 0) n = Integer.compare(colorKey(b), colorKey(a));
            return n;
        }

        private int colorKey(PotionEffect effect) {
            Integer c = COLORS.get(effect.getPotionID());
            return c != null ? c & 0xFFFFFF : 0;
        }
    }
}
