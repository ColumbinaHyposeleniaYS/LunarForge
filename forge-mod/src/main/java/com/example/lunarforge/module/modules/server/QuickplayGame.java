package com.example.lunarforge.module.modules.server;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class QuickplayGame {
    QuickplayGame parent;
    String key, name, icon, command;
    boolean disabled;
    List<QuickplayGame> modes = Collections.emptyList();

    public String key() { return key; }
    public String name() { return name; }
    public QuickplayGame parent() { return parent; }
    public List<QuickplayGame> modes() { return modes; }
    public boolean disabled() { return disabled; }

    public String iconPath() {
        String s = icon == null || icon.isEmpty() ? "unknown" : icon.toLowerCase(Locale.ROOT);
        return "icons/hypixel/" + s + ".png";
    }

    public String command() {
        String prefix = parent == null ? "/l " : "/play ";
        if (command == null || command.isEmpty()) return prefix + key.toLowerCase(Locale.ROOT);
        return command;
    }
}
