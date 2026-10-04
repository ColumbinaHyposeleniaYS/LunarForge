package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.gui.LunarClickGui;
import com.example.lunarforge.gui.LunarMovementScreen;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.PostShader;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.gui.achievement.GuiAchievements;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.stream.GuiStreamOptions;
import net.minecraft.client.gui.stream.GuiStreamUnavailable;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ModuleMenuBlur extends Module {
    private static ModuleMenuBlur instance;

    private final NumberSetting blurStrength = integer("blurStrength", 4, 0, 20);
    private final Kind lunar = new Kind("Lunar", 0);
    private final Kind inventory = new Kind("Inventory", 0x6F000000);
    private final Kind pause = new Kind("Pause", 0x6F000000);
    private final PostShader shader = new PostShader("menu_blur",
        new String[]{"vec2 BlurDir", "float Radius", "vec2 OneTexel", "float Progress"});
    private Framebuffer scratch;

    private boolean idle = true;

    private long startTime;
    private boolean active = true;

    private final class Kind {
        final BoolSetting toggle;
        final ColorSetting background;
        final Set<String> screens = new HashSet<String>();
        final Set<Class<?>> classes = new HashSet<Class<?>>();

        Kind(String name, int color) {
            toggle = bool("blur" + name + "Toggle", true);
            background = ModuleMenuBlur.this.color("blur" + name + "Background", color);
            background.hideIf(() -> !toggle.on());
        }

        int color() {
            int c = background.color(0.0f);
            if ((c >>> 24) / 255.0f > 0.75f) c = c & 0xFFFFFF | Math.round(0.75f * 255) << 24;
            return fade(c);
        }

        boolean matches(GuiScreen screen) {
            if (!isEnabled() || !toggle.on() || screen == null) return false;
            for (Class<?> c = screen.getClass(); c != Object.class; c = c.getSuperclass()) {
                if (classes.contains(c) || screens.contains(c.getName())) return true;
            }
            return false;
        }
    }

    public ModuleMenuBlur() {
        super("MENU_BLUR", true);
        instance = this;
        pause.classes.add(GuiIngameMenu.class);
        pause.classes.add(GuiVideoSettings.class);
        pause.classes.add(GuiOptions.class);
        pause.classes.add(GuiScreenOptionsSounds.class);
        pause.classes.add(ScreenChatOptions.class);
        pause.classes.add(GuiCustomizeSkin.class);
        pause.classes.add(GuiStreamOptions.class);
        pause.classes.add(GuiStreamUnavailable.class);
        pause.classes.add(GuiAchievements.class);
        for (String of : new String[]{"GuiAnimationSettingsOF", "GuiDetailSettingsOF", "GuiQualitySettingsOF",
                "GuiPerformanceSettingsOF", "GuiOtherSettingsOF"}) {
            pause.screens.add("net.optifine.gui." + of);
            pause.screens.add(of);
        }
        inventory.classes.add(GuiInventory.class);
        inventory.classes.add(GuiContainer.class);
        lunar.classes.add(LunarClickGui.class);
        lunar.classes.add(LunarMovementScreen.class);
    }

    @Override protected void layout(Page page) {
        page.add(blurStrength, lunar.toggle, lunar.background, inventory.toggle, inventory.background, pause.toggle, pause.background);
    }

    private float elapsedFraction() { return (float)(System.currentTimeMillis() - startTime) / 125.0f; }

    private float progress() {
        if (startTime == 0L) return 0.0f;
        float t = elapsedFraction();
        return t >= 1.0f ? 1.0f : (float)Math.pow(Math.max(0.0f, t), 2.0);
    }

    private int fade(int c) {
        boolean running = startTime != 0L && elapsedFraction() < 1.0f;
        float f = running ? Math.max(Math.min((float)Math.pow(Math.max(0.0f, elapsedFraction()), 2.0), 0.75f), 0.0f) : 0.75f;
        int a = (int)((c >>> 24) * f), r = (int)((c >> 16 & 255) * f), g = (int)((c >> 8 & 255) * f), b = (int)((c & 255) * f);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private void ensureStarted() {
        if (idle) { startTime = System.currentTimeMillis(); active = true; }
        idle = false;
    }

    private void stop() {
        startTime = 0L;
        active = false;
        idle = true;
    }

    private boolean shouldBlur(GuiScreen s) { return lunar.matches(s) || inventory.matches(s) || pause.matches(s); }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui == null) stop();
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!isEnabled() || mc().theWorld == null) return;
        if (shouldBlur(event.gui)) ensureStarted();

        if (lunar.matches(event.gui)) Gui.drawRect(0, 0, event.gui.width, event.gui.height, lunar.color());
    }

    public static int gradientTop(GuiScreen screen, int color) { return gradient(screen, color); }

    public static int gradientBottom(GuiScreen screen, int color) { return gradient(screen, color); }

    private static int gradient(GuiScreen screen, int color) {
        ModuleMenuBlur m = instance;
        if (m == null || !m.isEnabled()) return color;
        if (m.lunar.matches(screen)) return m.lunar.color();
        if (m.inventory.matches(screen)) return m.inventory.color();
        if (m.pause.matches(screen)) return m.pause.color();
        return color;
    }

    public static void postProcess(Framebuffer main) {
        final ModuleMenuBlur m = instance;
        if (m == null || !m.isEnabled() || !m.active || !PostShader.supported()) return;
        m.scratch = PostShader.match(m.scratch, main);
        final float texelX = 1.0f / main.framebufferWidth, texelY = 1.0f / main.framebufferHeight;
        final float radius = m.blurStrength.intValue();
        final float progress = m.progress();

        if (progress <= 0.0f) return;
        for (int i = 0; i < 2; ++i) {
            m.shader.apply(main, m.scratch, s -> {
                s.set("Progress", progress);
                s.set("Radius", radius);
                s.set("BlurDir", 1.0f, 0.0f);
                s.set("OneTexel", texelX, texelY);
            });
            m.shader.apply(m.scratch, main, s -> {
                s.set("Progress", progress);
                s.set("Radius", radius);
                s.set("BlurDir", 0.0f, 1.0f);
                s.set("OneTexel", texelX, texelY);
            });
        }
    }

    @Override protected void onDisable() {
        stop();
        shader.delete();
        if (scratch != null) { scratch.deleteFramebuffer(); scratch = null; }
    }
}
