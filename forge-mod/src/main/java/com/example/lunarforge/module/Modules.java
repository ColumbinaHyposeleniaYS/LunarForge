package com.example.lunarforge.module;

import com.example.lunarforge.module.setting.Setting;

public final class Modules {
    private Modules() {}

    public static boolean enabled(String id) {
        Module m = ModuleManager.get(id);
        return m != null && m.isEnabled();
    }

    @SuppressWarnings("unchecked")
    public static <T> T option(String id, String key, T fallback) {
        Module m = ModuleManager.get(id);
        if (m == null) return fallback;
        for (Setting<?> s : m.settings()) if (s.key.equals(key)) return (T)s.get();
        return fallback;
    }

    public static boolean enabledAnd(String id, String key) {
        return enabled(id) && Boolean.TRUE.equals(option(id, key, Boolean.FALSE));
    }
}
