package com.example.lunarforge.module.setting;

public enum TriState implements ChoiceSetting.Option {
    FALSE("false"), DEFAULT("minecraftDefault"), TRUE("true");

    private final String lang;

    TriState(String lang) { this.lang = lang; }

    @Override public String langId() { return lang; }

    public boolean orElse(boolean fallback) { return this == DEFAULT ? fallback : this == TRUE; }
}
