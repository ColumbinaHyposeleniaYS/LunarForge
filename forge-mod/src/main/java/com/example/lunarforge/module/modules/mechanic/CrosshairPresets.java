package com.example.lunarforge.module.modules.mechanic;

import java.util.Arrays;
import java.util.List;

final class CrosshairPresets {
    static final String DEFAULT = "LCCH-9-ECBAgPAfAgQIEAA";

    static final String[] PRESETS = {"LCCH-9-ECBAgPAfAgQIEAA", "LCCH-9-EKxJgvAfgiRrEAA", "LCCH-8-AAAAABA4bMY", "LCCH-5-7v/vAA",
        "LCCH-7-HFEwGRRxAA", "LCCH-5-P9b4AQ", "LCCH-7-HETyn0RwAA", "LCCH-7-HERwHERwAA", "LCCH-9-EKxJAnAdgCRrEAA",
        "LCCH-9-EKxJAnAcgCRrEAA", "LCCH-9-AADgIHKdCA4AAAA", "LCCH-7-47uP47uPAQ", "LCCH-7-47sNYLuPAQ", "LCCH-7-CI5tbOMgAA"};

    private static final List<CrosshairGrid> BANNED = Arrays.asList(CrosshairGrid.parse("LCCH-5-vXx6AQ"), CrosshairGrid.parse("LCCH-7-+UTij0Q+AQ"),
        CrosshairGrid.parse("LCCH-5-l/7SAQ"), CrosshairGrid.parse("LCCH-7-TyTyn0jkAQ"), CrosshairGrid.parse("LCCH-7-Ar3Ch3qBAA"),
        CrosshairGrid.parse("LCCH-9-9u3b8O8ftm/fAAA"));

    private CrosshairPresets() {}

    static boolean banned(CrosshairGrid grid) {
        outer:
        for (CrosshairGrid b : BANNED) {
            int left = 3;
            for (int i = 0; i < grid.data.length; i++) {
                if (i < b.data.length && b.data[i] != grid.data[i] && left-- <= 0) continue outer;
            }
            return true;
        }
        return false;
    }
}
