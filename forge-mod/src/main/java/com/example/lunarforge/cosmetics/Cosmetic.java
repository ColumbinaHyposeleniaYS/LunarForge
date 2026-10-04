package com.example.lunarforge.cosmetics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Cosmetic {
    public final int id;
    public final String name;

    public final String resource;
    public final CosmeticType type;
    public final boolean animated, geckolib, scalable;
    public final List<String> colors, tags;

    public final String released;

    Cosmetic(int id, String name, String resource, CosmeticType type, boolean animated, boolean geckolib,
             String colors, String tags, boolean scalable, String released) {
        this.id = id; this.name = name; this.type = type; this.animated = animated; this.geckolib = geckolib;
        this.resource = resource.startsWith("lunar:") ? resource.substring(6) : resource;
        this.colors = split(colors); this.tags = split(tags); this.scalable = scalable; this.released = released;
    }

    private static List<String> split(String s) {
        return s.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(s.split("\\|"));
    }

    public boolean renderable() {
        if (geckolib) return com.example.lunarforge.cosmetics.render.GeckoCosmetics.has(this);
        if (type == CosmeticType.CLOAK || type == CosmeticType.WINGS) return true;
        return com.example.lunarforge.cosmetics.render.ObjCosmetics.has(this);
    }

    public CosmeticType slot() { return type; }

    @Override public String toString() { return id + ":" + name; }
}
