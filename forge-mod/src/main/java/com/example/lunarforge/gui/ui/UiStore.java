package com.example.lunarforge.gui.ui;

import java.util.Collection;

public interface UiStore {
    String get(String key, String fallback);
    void put(String key, String value);

    void remove(String key);

    Collection<String> keys();
    void save();
}
