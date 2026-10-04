package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.PostShader;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.shader.Framebuffer;

public final class ModuleMotionBlur extends Module {
    public enum Type implements ChoiceSetting.Option {
        V1("v1", 0), V2("v2", 1), V3("v3", 2);
        final String lang; final int id;
        Type(String lang, int id) { this.lang = lang; this.id = id; }
        @Override public String langId() { return lang; }
    }

    private static ModuleMotionBlur instance;
    private final NumberSetting value = integer("value", 5, 1, 10);
    private final ChoiceSetting<Type> type = choice("type", Type.V3);
    private final PostShader shader = new PostShader("motion_blur", new String[]{"vec3 Phosphor"}, "PrevSampler");
    private Framebuffer previous, scratch;

    public ModuleMotionBlur() {
        super("MOTION_BLUR", false);
        instance = this;
    }

    @Override protected void layout(Page page) { page.add(type, value); }

    private float accumulation() {
        float v = value.intValue() / 10.0f;
        if (type.get() == Type.V1) {
            v = 0.7f + value.intValue() / 100.0f * 3.0f - 0.01f;
        } else if (type.get() == Type.V2) {
            if (v >= 1.0f) v = 0.99f;
            v = 1.0f - v;
        }
        return v;
    }

    public static void postProcess(Framebuffer main) {
        ModuleMotionBlur m = instance;
        if (m == null || !m.isEnabled() || !PostShader.supported()) return;

        final Framebuffer prev = m.previous == null ? main : m.previous;
        m.previous = PostShader.match(m.previous, main);
        m.scratch = PostShader.match(m.scratch, main);
        final float accumulation = m.accumulation();
        final int id = m.type.get().id;
        m.shader.applyInPlace(main, m.scratch, s -> {
            s.set("Phosphor", accumulation, id, 0.0f);
            s.sampler("PrevSampler", prev);
        });
        PostShader.copy(main, m.previous);
    }

    @Override protected void onDisable() {
        shader.delete();
        if (previous != null) { previous.deleteFramebuffer(); previous = null; }
        if (scratch != null) { scratch.deleteFramebuffer(); scratch = null; }
    }
}
