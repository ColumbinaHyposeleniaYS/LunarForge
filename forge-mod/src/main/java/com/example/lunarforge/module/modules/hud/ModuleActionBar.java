package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.TriState;
import com.example.lunarforge.util.Fields;
import java.awt.Color;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;

public final class ModuleActionBar extends Module {
    private static ModuleActionBar instance;
    private static final Field MESSAGE = Fields.find(GuiIngame.class, "recordPlaying", "field_73838_g");
    private static final Field TIME = Fields.find(GuiIngame.class, "recordPlayingUpFor", "field_73845_h");
    private static final Field ANIMATE = Fields.find(GuiIngame.class, "recordIsPlaying", "field_73844_j");

    private final ChoiceSetting<TriState> textShadow = choice("textShadow", TriState.DEFAULT);

    public ModuleActionBar() {
        super("ACTION_BAR", false);
        instance = this;
        hud(new Hud());
    }

    @Override protected void layout(Page page) { page.add(textShadow); }

    public static boolean replacesVanilla() { return instance != null && instance.isEnabled(); }

    private final class Hud extends HudElement {
        Hud() {
            super(ModuleActionBar.this, 0.0f, -61.0f, HudAnchor.BOTTOM_CENTER_R);
        }

        @Override public boolean visible(boolean preview) {
            size(60.0f, Draw.fontHeight());
            if (preview) return true;
            GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
            return gui != null && Fields.get(MESSAGE, gui) != null && Fields.getInt(TIME, gui) > 0;
        }

        @Override public void render(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            String text;
            float time;
            boolean animate;
            if (preview) {
                text = "Action Bar";
                time = 20.0f;
                animate = false;
            } else {
                text = (String)Fields.get(MESSAGE, mc.ingameGUI);
                if (text == null) return;
                time = Fields.getInt(TIME, mc.ingameGUI) - partialTicks(mc);
                animate = Fields.getBoolean(ANIMATE, mc.ingameGUI);
            }
            int alpha = Math.min(255, (int)(time * 255.0f / 20.0f));
            if (alpha <= 8) return;
            float width = Draw.width(text);
            float x = width() / 2.0f - (int)width / 2;
            int rgb = animate ? Color.HSBtoRGB(time / 50.0f, 0.7f, 0.6f) & 0xFFFFFF : 0xFFFFFF;
            Draw.text(text, x, 0, alpha << 24 | rgb, textShadow.get().orElse(false));
        }
    }

    private static Field timer;

    static float partialTicks(Minecraft mc) {
        try {
            if (timer == null) timer = Fields.find(Minecraft.class, "timer", "field_71428_T");
            return ((net.minecraft.util.Timer)timer.get(mc)).renderPartialTicks;
        } catch (Exception e) {
            return 0.0f;
        }
    }
}
