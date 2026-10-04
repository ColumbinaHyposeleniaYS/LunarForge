package com.example.lunarforge.gui.home;

import com.example.lunarforge.LunarForgeMod;
import com.example.lunarforge.gui.ui.UiStore;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import net.minecraft.util.ResourceLocation;

final class HomeThemes {
    static final class Theme {
        final String id, name, panoramaBase; final Month[] months; final int[] monthDay; final boolean alwaysSelectable;
        Theme(String id, String name, String panoramaBase, Month[] months, int[] monthDay, boolean alwaysSelectable) {
            this.id = id; this.name = name; this.panoramaBase = panoramaBase; this.months = months; this.monthDay = monthDay; this.alwaysSelectable = alwaysSelectable;
        }

        boolean active(LocalDateTime now) {
            if (monthDay != null) return now.getMonthValue() == monthDay[0] && now.getDayOfMonth() == monthDay[1];
            for (Month month : months) if (month == now.getMonth()) return true;
            return false;
        }

        boolean selectable(LocalDateTime now) { return alwaysSelectable || active(now); }
        ResourceLocation panorama(int face) {
            return panoramaBase.startsWith("minecraft:")
                ? new ResourceLocation(panoramaBase.substring(10) + "_" + face + ".png")
                : new ResourceLocation(LunarForgeMod.MOD_ID, "ui/" + panoramaBase + "_" + face + ".png");
        }
    }

    private static final Month[] NONE = new Month[0];

    static final Theme LUNAR = new Theme("lunar", "Lunar", "backgrounds/panorama", NONE, null, false);
    static final List<Theme> THEMES = Collections.unmodifiableList(Arrays.asList(
        new Theme("christmas", "Christmas", "backgrounds/christmas/panorama", new Month[]{Month.DECEMBER}, null, false),
        new Theme("anniversary", "Anniversary", "backgrounds/spring/panorama", NONE, new int[]{4, 6}, false),
        new Theme("spring", "Spring", "backgrounds/spring/panorama", new Month[]{Month.MARCH, Month.APRIL, Month.MAY, Month.JUNE}, null, false),
        new Theme("japan", "Japan", "backgrounds/japan/panorama", new Month[]{Month.JULY}, null, false),
        new Theme("summer", "Summer", "backgrounds/spring/panorama", new Month[]{Month.JULY, Month.AUGUST, Month.SEPTEMBER}, null, false),
        new Theme("vanilla", "Vanilla", "minecraft:textures/gui/title/background/panorama", NONE, null, true),
        new Theme("classic", "Classic", "backgrounds/classic/panorama", NONE, null, true)));

    private final UiStore store;
    HomeThemes(UiStore store) { this.store = store; }
    UiStore store() { return store; }

    Theme current() {
        String selected = store.get("homeTheme.selected", "");
        for (Theme theme : THEMES) if (theme.id.equals(selected)) return theme;
        return LUNAR;
    }

    List<Theme> choices() {
        LocalDateTime now = LocalDateTime.now();
        List<Theme> list = new ArrayList<Theme>();
        list.add(LUNAR);
        for (Theme theme : THEMES) if (theme.selectable(now)) list.add(theme);
        return list;
    }

    void select(String id) {
        store.put("homeTheme.selected", id); store.save();
    }
}
