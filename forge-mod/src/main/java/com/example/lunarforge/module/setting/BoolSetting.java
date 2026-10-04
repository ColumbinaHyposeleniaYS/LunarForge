package com.example.lunarforge.module.setting;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String key, boolean defaultValue) { super(key, defaultValue); }

    public boolean on() { return get(); }

    @Override protected Boolean parse(String raw) { return Boolean.parseBoolean(raw.trim()); }
    @Override protected String kind() { return "bool"; }
}
