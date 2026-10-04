package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Modules;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.entity.EntityLivingBase;

public final class ModuleHitColor extends Module {
    private static ModuleHitColor instance;
    private final ColorSetting hitArmorColor = color("hitArmorColor", 0x66990000);
    private final BoolSetting shouldColorArmor = bool("shouldColorArmor", true);

    public ModuleHitColor() {
        super("HIT_COLOR", false);
        instance = this;
    }

    @Override protected void layout(Page page) { page.add(hitArmorColor, shouldColorArmor); }

    private static boolean overlayDisablesDamage() { return Modules.enabledAnd("overlay_mod", "disableDamageOverlay"); }

    private static boolean active() { return instance != null && instance.isEnabled(); }

    private static int color() { return instance.hitArmorColor.color(0.0f); }

    public static float red(float value) {
        if (overlayDisablesDamage()) return 1.0f;
        return !active() ? value : (color() >> 16 & 255) / 255.0f;
    }

    public static float green(float value) {
        if (overlayDisablesDamage()) return 1.0f;
        return !active() ? value : (color() >> 8 & 255) / 255.0f;
    }

    public static float blue(float value) {
        if (overlayDisablesDamage()) return 1.0f;
        return !active() ? value : (color() & 255) / 255.0f;
    }

    public static float alpha(float value) {
        if (overlayDisablesDamage()) return 0.0f;
        return !active() ? value : (color() >>> 24) / 255.0f;
    }

    private static MethodHandle shadersEntityColor;
    private static boolean shadersLooked;

    public static void shadersEntityColor(float r, float g, float b, float a) {
        if (r == 1.0f && g == 0.0f && b == 0.0f && a == 0.3f) {
            r = red(r); g = green(g); b = blue(b); a = alpha(a);
        }
        if (!shadersLooked) {
            shadersLooked = true;
            try {
                shadersEntityColor = MethodHandles.publicLookup().findStatic(Class.forName("net.optifine.shaders.Shaders"),
                    "setEntityColor", MethodType.methodType(void.class, float.class, float.class, float.class, float.class));
            } catch (Throwable ignored) {}
        }
        if (shadersEntityColor != null) {
            try { shadersEntityColor.invokeExact(r, g, b, a); } catch (Throwable ignored) {}
        }
    }

    public static boolean combineArmor() { return active() && instance.shouldColorArmor.on(); }

    public static boolean skipArmorGlint(EntityLivingBase entity) {
        return active() && entity != null && instance.shouldColorArmor.on() && (entity.hurtTime > 0 || entity.deathTime > 0);
    }
}
