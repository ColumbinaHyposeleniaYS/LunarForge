package com.example.lunarforge.cosmetics;

import com.example.lunarforge.gui.ui.LunarLang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public enum CosmeticType {
    CLOAK("cloak", null, Render.CLOAK, Display.CLOAK, "cloak-40x40"),
    HEADWEAR("headwearui", null, Render.HAT, Display.HAT, "hat-40x40"),
    HAT("hat", HEADWEAR, Render.HAT, Display.HAT, "hat-40x40"),
    BANDANNA("bandanna", HEADWEAR, Render.HAT, Display.HAT, "bandana-28x28"),
    GLASSES("glasses", HEADWEAR, Render.HAT, Display.HAT, "glasses-40x40"),
    MASK("mask", HEADWEAR, Render.HAT, Display.HAT, "mask-28x28"),
    PET("pet", null, Render.GECKOLIB, null, "pet-40x40"),
    COMPANION("companion", null, Render.GECKOLIB, null, "companion-40x40"),
    BODYWEAR_UI("bodywearui", null, Render.BODYWEAR, Display.BUST, "bodywear-40x40"),
    BACKPACK("backpack", BODYWEAR_UI, Render.BODYWEAR, Display.CLOAK, "backpack-40x40"),
    BODYWEAR("bodywear", BODYWEAR_UI, Render.BODYWEAR, Display.BUST, "bodywear-40x40"),
    NECKWEAR("neckwear", BODYWEAR_UI, Render.BODYWEAR, Display.BUST, "necklace-64x64"),
    WINGS("wings", BODYWEAR_UI, Render.WINGS, Display.WING, "wings-40x40"),
    BELTS("belts", BODYWEAR_UI, Render.BODYWEAR, Display.BELT, "belts-40x40"),
    SHOES("shoes", BODYWEAR_UI, Render.BODYWEAR, Display.SHOES, "shoes-40x40"),
    WRISTWEAR("wristwear", BACKPACK, Render.GECKOLIB, null, "wristwear-40x40"),
    SHIELDS("shields", BACKPACK, Render.GECKOLIB, null, "shields-40x40"),
    AURAS("auras", null, Render.BODYWEAR, Display.DEFAULT, "auras-40x40"),
    SUITS("suits", null, Render.BODYWEAR, Display.SUITS, "suits-40x40"),
    ITEM("items", null, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    SWORD("sword", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    PICKAXE("pickaxe", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    AXE("axe", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    SHOVEL("shovel", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    HOE("hoe", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40"),
    HAND("hand", ITEM, Render.GECKOLIB, Display.ITEM, "item-40x40");

    public enum Render { HAT, CLOAK, BODYWEAR, WINGS, GECKOLIB }

    public final String id;
    public final CosmeticType parent;
    public final Render render;

    public final Display display;
    public final String icon;
    private final List<CosmeticType> children = new ArrayList<CosmeticType>(0);

    CosmeticType(String id, CosmeticType parent, Render render, Display display, String icon) {
        this.id = id; this.parent = parent; this.render = render; this.display = display; this.icon = icon;
        if (parent != null) parent.children.add(this);
    }

    public List<CosmeticType> children() { return Collections.unmodifiableList(children); }

    public String displayName() { return LunarLang.get("settings", id); }

    public boolean covers(CosmeticType type) {
        for (CosmeticType t = type; t != null; t = t.parent) if (t == this) return true;
        return false;
    }

    public static CosmeticType from(String name) {
        if (name == null) return null;
        if (name.equalsIgnoreCase("dragon_wings")) return WINGS;
        String n = name.toLowerCase(Locale.ROOT);
        for (CosmeticType t : values()) if (t.id.equalsIgnoreCase(n)) return t;
        return null;
    }

    public static List<CosmeticType> roots() {
        List<CosmeticType> out = new ArrayList<CosmeticType>();
        for (CosmeticType t : values()) if (t.parent == null && t != COMPANION && t != ITEM) out.add(t);
        return out;
    }
}
