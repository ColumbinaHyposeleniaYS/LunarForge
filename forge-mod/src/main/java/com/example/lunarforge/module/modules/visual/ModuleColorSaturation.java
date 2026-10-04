package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.PostShader;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.shader.Framebuffer;

public final class ModuleColorSaturation extends Module {
    private static ModuleColorSaturation instance;
    private final NumberSetting saturation = decimal("colorSaturationSaturation", 5.0f, 0.0f, 10.0f);
    private final NumberSetting hue = decimal("colorSaturationHue", 0.0f, 0.0f, 10.0f);
    private final NumberSetting contrast = decimal("colorSaturationContrast", 5.0f, 0.0f, 10.0f);
    private final NumberSetting brightness = decimal("colorSaturationBrightness", 5.0f, 0.0f, 10.0f);
    private final BoolSetting grayscale = bool("grayscale", false);
    private final PostShader shader = new PostShader("color_saturation",
        new String[]{"float Hue", "float Brightness", "float Contrast", "float Saturation"});
    private Framebuffer scratch;

    public ModuleColorSaturation() {
        super("COLOR_SATURATION", false);
        instance = this;
    }

    @Override protected void layout(Page page) { page.add(hue, saturation, brightness, contrast, grayscale); }

    private float uniform(NumberSetting option) {
        float v = option == saturation && grayscale.on() ? 0.0f : option.value();
        if (v <= 0.0f) return 0.0f;
        float f = v / 10.0f;
        return f >= 1.0f ? 0.99f : f;
    }

    public static void postProcess(Framebuffer main) {
        final ModuleColorSaturation m = instance;
        if (m == null || !m.isEnabled() || !PostShader.supported()) return;
        m.scratch = PostShader.match(m.scratch, main);
        m.shader.applyInPlace(main, m.scratch, s -> {
            s.set("Hue", m.uniform(m.hue));
            s.set("Saturation", m.uniform(m.saturation));
            s.set("Brightness", m.uniform(m.brightness));
            s.set("Contrast", m.uniform(m.contrast));
        });
    }

    @Override protected void onDisable() {
        shader.delete();
        if (scratch != null) { scratch.deleteFramebuffer(); scratch = null; }
    }
}
