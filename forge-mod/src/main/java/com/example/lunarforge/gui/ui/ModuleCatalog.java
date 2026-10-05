package com.example.lunarforge.gui.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ModuleCatalog {
    public static final class Module {
        public final String id, name, category, icon;
        public final boolean implemented;
        Module(String id, String name, String category, String icon) {
            this.id = id; this.name = name; this.category = category; this.icon = icon;
            implemented = PORTED.contains(id) || CODE.contains(id);
        }
    }

    public static final List<String> PORTED = Collections.<String>emptyList();

    public static final List<String> CODE = Arrays.asList(
        "fps", "cps", "toggle_sneak", "armorstatus", "keystrokes", "clock", "memory", "day_counter",
        "server_address", "potion_counter", "pvp_info",
        "tnt_countdown", "auto_text_hotkey", "auto_text_actions", "time_changer", "weather_changer",
        "lighting", "fog", "damage_tint", "fov",
        "coordinates", "ping", "direction_hud", "one_seven_visuals", "item_physics", "2d_items",
        "combo", "reach_display", "zoom", "freelook", "snaplook", "hurt_cam", "scoreboard", "bossbar",
        "pack_display", "menu_blur", "motion_blur", "color_saturation",
        "hit_color", "shiny_pots", "glint_colorizer", "mob_size", "hitbox", "block_outline", "height_limit", "light_overlay", "particle_changer", "saturation", "titles", "action_bar", "scrollable_tooltips",
        "potion_effects", "stopwatch", "chat", "nick_hider", "quickplay", "crosshair", "tab", "hypixel_mod", "hypixel_bedwars", "3d_skins",
        "auto_clicker", "fast_place", "inventory_clicker", "auto_tool", "eagle", "block_hit_mode",
        "w_tap", "knockback_delay", "aim_assist", "jump_reset", "scaffold");

    public static boolean defaultEnabled(String id) {
        com.example.lunarforge.module.Module m = com.example.lunarforge.module.ModuleManager.get(id);
        if (m != null) return m.defaultEnabled();
        return PORTED.indexOf(id) == 0;
    }
    public static final List<Module> ALL;
    static {
        List<Module> modules = new ArrayList<Module>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ModuleCatalog.class.getResourceAsStream("/assets/lunarforge/ui/modules.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t");
                if (fields.length == 4) modules.add(new Module(fields[0], fields[1], fields[2], fields[3]));
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load module catalog", e); }
        ALL = Collections.unmodifiableList(modules);
    }
    public static Module find(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("Unknown module: " + id);
    }
    private ModuleCatalog() {}
}
