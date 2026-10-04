package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.TextSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.GlStateManager;

public final class ModuleTitles extends Module {
    private static ModuleTitles instance;
    private static final Field TIMER = Fields.find(GuiIngame.class, "field_175195_w");
    private static final Field TITLE = Fields.find(GuiIngame.class, "field_175201_x");
    private static final Field SUBTITLE = Fields.find(GuiIngame.class, "field_175200_y");
    private static final Field FADE_IN = Fields.find(GuiIngame.class, "field_175199_z");
    private static final Field STAY = Fields.find(GuiIngame.class, "field_175192_A");
    private static final Field FADE_OUT = Fields.find(GuiIngame.class, "field_175193_B");

    private final BoolSetting showInHudEditor = bool("showInHudEditor", false);
    private final BoolSetting keepTitleCentered = bool("keepTitleCentered", true);
    private final TextSetting title = add(new TextSetting("title", "BED DESTROYED!"));
    private final ColorSetting titleColor = color("titleColor", 0xFFFF5555);
    private final TextSetting subtitle = add(new TextSetting("subtitle", "You will no longer respawn!"));
    private final ColorSetting subtitleColor = color("subtitleColor", 0xFFFFFFFF);
    private final BoolSetting useMinecraftGUIScale = bool("useMinecraftGUIScale", true);
    private final BoolSetting showTitle = bool("showTitle", true);
    private final BoolSetting showSubtitle = bool("showSubtitle", true);

    public ModuleTitles() {
        super("TITLES", true);
        instance = this;
        hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.group(showInHudEditor, c -> c.add(title, titleColor, subtitle, subtitleColor, useMinecraftGUIScale));
        page.add(keepTitleCentered, showTitle, showSubtitle);
    }

    public static final List<BiFunction<String, String, Integer>> COLORS = new ArrayList<BiFunction<String, String, Integer>>();

    public static boolean replacesVanilla() {
        return instance != null && instance.isEnabled() || com.example.lunarforge.module.modules.server.ModuleHypixelBedwars.drawsTitle();
    }

    private static int color(String title, String subtitle) {
        for (BiFunction<String, String, Integer> c : COLORS) {
            Integer n = c.apply(title, subtitle);
            if (n != null) return n & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    private static int alpha(Minecraft mc) {
        GuiIngame gui = mc.ingameGUI;
        int timer = Fields.getInt(TIMER, gui);
        float age = timer - ModuleActionBar.partialTicks(mc);
        int fadeIn = Fields.getInt(FADE_IN, gui), stay = Fields.getInt(STAY, gui), fadeOut = Fields.getInt(FADE_OUT, gui);
        int alpha = 255;
        if (timer > fadeOut + stay) alpha = (int)((fadeIn + stay + fadeOut - age) * 255.0f / fadeIn);
        if (timer <= fadeOut) alpha = (int)(age * 255.0f / fadeOut);
        return Math.max(0, Math.min(255, alpha));
    }

    public static String[] gameTitle() {
        GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
        if (gui == null || Fields.get(TITLE, gui) == null || Fields.getInt(TIMER, gui) <= 0) return null;
        return new String[]{(String)Fields.get(TITLE, gui), (String)Fields.get(SUBTITLE, gui)};
    }

    public static void draw(String[] t, float cx, float cy, int alpha, boolean showTitle, boolean showSubtitle, int titleRgb, int subtitleRgb) {
        if (alpha <= 8) return;
        int a = alpha << 24 & 0xFF000000;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        if (showSubtitle && t[1] != null) {
            int rgb = subtitleRgb >= 0 ? subtitleRgb : color(null, t[1]);
            String s = rgb != 0xFFFFFF ? net.minecraft.util.StringUtils.stripControlCodes(t[1]) : t[1];
            Draw.text(s, -Draw.width(s) / 2.0f, 7.0f, rgb | a, true);
        }
        if (showTitle && t[0] != null) {
            GlStateManager.scale(2.0f, 2.0f, 2.0f);
            int rgb = titleRgb >= 0 ? titleRgb : color(t[0], null);
            String s = rgb != 0xFFFFFF ? net.minecraft.util.StringUtils.stripControlCodes(t[0]) : t[0];
            Draw.text(s, -Draw.width(s) / 2.0f, -8.75f, rgb | a, true);
        }
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void drawGameTitle(float screenWidth, float screenHeight) {
        String[] t = gameTitle();
        if (t != null) draw(t, screenWidth / 2.0f, screenHeight / 2.0f, alpha(Minecraft.getMinecraft()), true, true, -1, -1);
    }

    private final class Hud extends HudElement {
        Hud() { super(ModuleTitles.this, 0.0f, 0.0f, HudAnchor.MIDDLE_CENTER, 0.5f, 1.5f); }

        @Override public boolean editable() { return showInHudEditor.on(); }

        private String[] text(boolean preview) {
            if (preview) return new String[]{title.get(), subtitle.get()};
            GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
            return new String[]{(String)Fields.get(TITLE, gui), (String)Fields.get(SUBTITLE, gui)};
        }

        @Override public boolean visible(boolean preview) {
            if (preview) {
                if (!showInHudEditor.on()) return false;
            } else {
                GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
                if (gui == null || Fields.get(TITLE, gui) == null || Fields.getInt(TIMER, gui) <= 0) return false;
            }
            String[] t = text(preview);
            float width = Math.max(Draw.width(t[0]) * 4.0f, Draw.width(t[1]) * 2.0f);
            keepCentered(width);
            size(width, 72.0f);
            return true;
        }

        private void keepCentered(float width) {
            float old = width();
            if (!keepTitleCentered.on() || old == width || old == 0.0f) return;
            UiModel model = ModuleManager.model();
            HudAnchor anchor = currentAnchor();
            if (model == null || anchor.horizontal == HudAnchor.Side.MIDDLE) return;
            float x = model.number(getId(), "x", Float.parseFloat(UiModel.defaultOption(getId(), "x")));
            float shift = old / 2.0f - width / 2.0f;
            model.set(getId(), "x", "" + (anchor.horizontal == HudAnchor.Side.START ? x + shift : x - shift));
        }

        @Override public void render(boolean preview) {
            String[] t = text(preview);
            int alpha = preview ? 255 : alpha(Minecraft.getMinecraft());
            draw(t, width() / 2.0f, height() / 2.0f, alpha, showTitle.on(), showSubtitle.on(),
                preview ? titleColor.color(0.0f) & 0xFFFFFF : -1, preview ? subtitleColor.color(0.0f) & 0xFFFFFF : -1);
        }
    }
}
