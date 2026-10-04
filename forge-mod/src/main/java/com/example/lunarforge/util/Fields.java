package com.example.lunarforge.util;

import java.lang.reflect.Field;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public final class Fields {
    private static final Field KEY_PRESSED = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");

    private Fields() {}

    public static void setPressed(KeyBinding key, boolean pressed) {
        try { KEY_PRESSED.setBoolean(key, pressed); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static Field find(Class<?> owner, String... names) {
        return ReflectionHelper.findField(owner, names);
    }

    public static float getFloat(Field f, Object o) {
        try { return f.getFloat(o); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static boolean getBoolean(Field f, Object o) {
        try { return f.getBoolean(o); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static int getInt(Field f, Object o) {
        try { return f.getInt(o); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static void setInt(Field f, Object o, int v) {
        try { f.setInt(o, v); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static void setFloat(Field f, Object o, float v) {
        try { f.setFloat(o, v); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static Object get(Field f, Object o) {
        try { return f.get(o); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    public static void set(Field f, Object o, Object v) {
        try { f.set(o, v); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
