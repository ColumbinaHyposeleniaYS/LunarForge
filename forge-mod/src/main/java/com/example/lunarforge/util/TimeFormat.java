package com.example.lunarforge.util;

import com.example.lunarforge.module.setting.ChoiceSetting;

public enum TimeFormat implements ChoiceSetting.Option {
    DEFAULT("12:34:56", "%d:%02d:%02d", false, true, true),
    COMPACT_1("12:34", "%d:%02d", false, false, true),
    COMPACT_2("12:34:56.789", "%d:%02d:%02d.%03d", true, true, true),
    COMPACT_3("12:34:56:789", "%d:%02d:%02d:%03d", true, true, true),
    SPREAD_1("12 : 34 : 56", "%d : %02d : %02d", false, true, true),
    SPREAD_2("12 : 34", "%d : %02d", false, false, true),
    SPREAD_3("12 : 34 : 56 . 789", "%d : %02d : %02d . %03d", true, true, true),
    SPREAD_4("12 : 34 : 56 : 789", "%d : %02d : %02d : %03d", true, true, true),
    EASY_1("12h 34m", "%2dh %2dm", false, false, true),
    EASY_2("12h 34m 56s", "%2dh %2dm %02ds", false, true, true),
    EASY_3("12h 34m 56s 789ms", "%2dh %2dm %2ds %03dms", true, true, true),
    STOPWATCH("123.456s", "%d.%03ds", true, true, false),
    MILLISECONDS("12345ms", "%dms", true, false, false);

    private final String display, pattern;

    private final boolean millis, seconds, clock;

    TimeFormat(String display, String pattern, boolean millis, boolean seconds, boolean clock) {
        this.display = display;
        this.pattern = pattern;
        this.millis = millis;
        this.seconds = seconds;
        this.clock = clock;
    }

    @Override public String langId() { return display; }

    public String format(long ms) {
        if (clock) {
            long h = ms / 3600000L % 24L, m = ms / 60000L % 60L;
            if (seconds) {
                long s = ms / 1000L % 60L;
                return millis ? String.format(pattern, h, m, s, ms % 1000L) : String.format(pattern, h, m, s);
            }
            return String.format(pattern, h, m);
        }
        if (seconds) {
            long s = ms / 1000L;
            return millis ? String.format(pattern, s, ms % 1000L) : String.format(pattern, s);
        }
        return String.format(pattern, ms);
    }
}
