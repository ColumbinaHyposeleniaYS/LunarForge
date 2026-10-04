package com.example.lunarforge.gui.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class UiModel {
    public final UiStore store;
    public String tab = "MODS", category = "ALL", settingsTab = "GENERAL", selected = "";
    public String search = "", input = "", toast = "";
    public boolean searchFocused, closeRequested, editHudRequested, captureKey;

    public String captureTarget = "menu";

    public final LunarScroller scroller = new LunarScroller();
    public long revision, toastUntil;
    public final List<String> profiles = new ArrayList<String>();

    public String keybindPopup = "";

    public String keyListening = "";

    public String textEditing = "";
    public int textMax;

    public String hexEditing = "";
    public boolean hexAlpha = true;

    public String sliderHeld = "";

    public boolean inWorld;

    public float clickX, clickY;

    public String editor = "";
    public String editorName = "", editorServer = "", editorIcon = "", editorFocus = "";
    public boolean editorNameError, editorServerError;

    public float[] iconMenu;

    public String confirm = "";

    public static final String DEFAULT_PROFILE = "Default";

    private static final List<String> OLD_PRESETS = Arrays.asList("UHC", "Hypixel Skyblock", "Arena PvP");

    public static final String[][] PROFILE_ICONS = {{"", "None"}, {"clock", "Clock"}, {"apple", "Apple"},
        {"hypixel", "Hypixel"}, {"crossed-swords", "Swords"}, {"mouse", "Mouse"}};

    public static final String[] SORTS = {"last_modified", "alphabetical", "custom"};
    private static final String[] SORT_KEYS = {"lastModified", "alphabetical", "custom"};

    public UiModel(UiStore store) {
        this.store = store;
        String[] names = store.get("profiles", DEFAULT_PROFILE).split("\\|", -1);
        for (String name : names) if (!name.trim().isEmpty() && !profiles.contains(name)) profiles.add(name);
        if (!profiles.contains(DEFAULT_PROFILE)) profiles.add(0, DEFAULT_PROFILE);
        for (String preset : OLD_PRESETS) {
            if (store.get("bundled." + preset, "").isEmpty()) continue;
            store.remove("bundled." + preset);
            if (!profiles.remove(preset)) continue;
            resetProfile(preset);
            for (String key : new String[]{"profileDisplay.", "profileIcon.", "profileServer."}) store.remove(key + preset);
        }
        saveProfiles();
        if (!profiles.contains(profile())) store.put("activeProfile", DEFAULT_PROFILE);
    }

    public String profile() { return store.get("activeProfile", DEFAULT_PROFILE); }
    public String displayName(String profile) { return store.get("profileDisplay." + profile, profile); }

    public String profileIcon(String profile) { return store.get("profileIcon." + profile, ""); }
    public String server(String profile) { return store.get("profileServer." + profile, ""); }
    private void saveProfiles() { store.put("profiles", String.join("|", profiles)); }

    public void resetProfile(String profile) {
        String prefix = "profile." + profile + ".";
        for (String key : new ArrayList<String>(store.keys())) if (key.startsWith(prefix)) store.remove(key);
        revision++;
    }

    private void openEditor(String profile) {
        editor = profile; editorName = displayName(profile); editorServer = server(profile); editorIcon = profileIcon(profile);
        editorFocus = ""; editorNameError = false; editorServerError = false; iconMenu = null; confirm = "";
    }
    private void closeEditor() { editor = ""; editorFocus = ""; iconMenu = null; confirm = ""; }

    public boolean canDelete(String profile) { return !profile.equals(DEFAULT_PROFILE); }

    private void confirmed(boolean yes) {
        String which = confirm;
        confirm = "";
        if (which.equals("backConfirm")) { closeEditor(); return; }
        if (!yes) return;
        String profile = editor;
        if (which.equals("deleteConfirm")) {
            if (profile.equals(profile())) store.put("activeProfile", DEFAULT_PROFILE);
            resetProfile(profile);
            for (String key : new String[]{"profileDisplay.", "profileIcon.", "profileServer."}) store.remove(key + profile);
            profiles.remove(profile); saveProfiles();
            closeEditor(); tab = "MODS"; selected = "";
        } else if (which.equals("resetProfile")) {
            resetProfile(profile);
            closeEditor(); tab = "MODS"; selected = "";
        }
    }

    public final Set<String> expanded = new HashSet<String>();
    public String key(String id, String option) { return "profile." + profile() + "." + id + "." + option; }
    public String value(String id, String option, String fallback) { return store.get(key(id, option), fallback); }
    public boolean flag(String id, String option, boolean fallback) { return Boolean.parseBoolean(value(id, option, "" + fallback)); }
    public float number(String id, String option, float fallback) {
        try { float f = Float.parseFloat(value(id, option, "" + fallback)); return Float.isNaN(f) || Float.isInfinite(f) ? fallback : f; }
        catch (NumberFormatException e) { return fallback; }
    }
    public void set(String id, String option, String value) {
        store.put(key(id, option), value);
        revision++;
        com.example.lunarforge.module.ModuleManager.changed(id, option);
    }
    public boolean enabled(ModuleCatalog.Module m) {
        com.example.lunarforge.module.Module code = com.example.lunarforge.module.ModuleManager.get(m.id);
        if (code != null) return code.isEnabled();
        return m.implemented && flag(m.id, "enabled", ModuleCatalog.defaultEnabled(m.id));
    }
    public boolean favorite(ModuleCatalog.Module m) { return Boolean.parseBoolean(store.get("favorite." + m.id, "false")); }
    public boolean compact() { return Boolean.parseBoolean(store.get("compact", "false")); }

    public int sort() {
        String v = store.get("sortingOptions", "custom");
        for (int i = 0; i < SORTS.length; i++) if (SORTS[i].equals(v)) return i;
        return 2;
    }
    public static String sortName(int index) { return LunarLang.get("settings", SORT_KEYS[index]); }
    public void notify(String message) { toast = message; toastUntil = System.currentTimeMillis() + 4500; revision++; }

    public List<ModuleCatalog.Module> visibleModules() {
        List<ModuleCatalog.Module> all = new ArrayList<ModuleCatalog.Module>(ModuleCatalog.ALL);
        final int sort = sort();
        if (sort != 2) Collections.sort(all, (a, b) -> {
            int fav = Boolean.compare(favorite(b), favorite(a));
            if (fav != 0) return fav;
            if (sort == 1) return name(a).compareTo(name(b));
            return Long.compare(modified(b), modified(a));
        });
        List<ModuleCatalog.Module> result = new ArrayList<ModuleCatalog.Module>();
        if (!search.isEmpty()) {
            String q = UiRenderer.normalise(search);
            for (ModuleCatalog.Module m : all) if (inCategory(m) && nameMatch(m, q)) result.add(m);
            if (Boolean.parseBoolean(store.get("searchOptions", "true")))
                for (ModuleCatalog.Module m : all) if (inCategory(m) && !result.contains(m) && optionMatch(m, q)) result.add(m);
            return result;
        }
        for (ModuleCatalog.Module m : all) if (inCategory(m)) result.add(m);
        return result;
    }
    private boolean inCategory(ModuleCatalog.Module m) { return category.equals("ALL") || category.equals(m.category); }
    private static String name(ModuleCatalog.Module m) { return LunarLang.featureName(m.id, m.name); }
    private static boolean nameMatch(ModuleCatalog.Module m, String q) {
        String name = name(m);
        if (UiRenderer.normalise(name).startsWith(q) || UiRenderer.normalise(m.id).startsWith(q)) return true;
        for (String word : name.split(" ")) if (UiRenderer.normalise(word).startsWith(q)) return true;
        for (OptionCatalog.Node child : children(OptionCatalog.page(m.id).options))
            for (String word : LunarLang.featureName(child.key, child.key).split(" ")) if (UiRenderer.normalise(word).startsWith(q)) return true;
        return false;
    }
    private static boolean optionMatch(ModuleCatalog.Module m, String q) {
        List<OptionCatalog.Node> options = new ArrayList<OptionCatalog.Node>(OptionCatalog.flatten(OptionCatalog.page(m.id).options));
        for (OptionCatalog.Node child : children(OptionCatalog.page(m.id).options)) options.addAll(OptionCatalog.flatten(child.children));
        for (OptionCatalog.Node o : options)
            for (String word : LunarLang.get("settings", o.key).split(" ")) if (UiRenderer.normalise(word).startsWith(q)) return true;
        return false;
    }
    private static List<OptionCatalog.Node> children(List<OptionCatalog.Node> nodes) {
        List<OptionCatalog.Node> out = new ArrayList<OptionCatalog.Node>();
        for (OptionCatalog.Node n : nodes) {
            if (n.type.equals("child")) out.add(n);
            else out.addAll(children(n.children));
        }
        return out;
    }
    private long modified(ModuleCatalog.Module m) {
        try { return Long.parseLong(store.get("modified." + m.id, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    void openOptions(String id) {
        com.example.lunarforge.module.Module code = com.example.lunarforge.module.ModuleManager.get(id);
        List<String> rows = new ArrayList<String>();
        while (code != null && code.parent() != null) {
            rows.add(code.parent().key() + "." + code.id);
            code = code.parent();
        }
        String top = code != null ? code.key() : id;
        for (ModuleCatalog.Module m : ModuleCatalog.ALL) {
            if (!m.id.equals(top)) continue;
            selected = top;
            expanded.addAll(rows);
            return;
        }
    }

    public void toggleModule(String id) {
        ModuleCatalog.Module m = ModuleCatalog.find(id);
        if (!m.implemented) return;
        com.example.lunarforge.module.Module code = com.example.lunarforge.module.ModuleManager.get(id);
        if (code != null) code.setEnabled(!code.isEnabled());
        else set(id, "enabled", "" + !enabled(m));
        touch(id);
        store.save();
    }

    private void touch(String id) { store.put("modified." + id, "" + System.currentTimeMillis()); }

    public void action(String action) {
        revision++;
        if (!editor.isEmpty()) { editorAction(action); store.save(); return; }
        if (action.equals("close")) { closeRequested = true; return; }
        if (action.equals("search")) { searchFocused = true; return; }
        searchFocused = false;
        if (action.startsWith("tab:")) { tab = action.substring(4); selected = ""; resetSearch(); }
        else if (action.startsWith("category:")) { category = action.substring(9); scroller.reset(); }
        else if (action.startsWith("settings:")) { settingsTab = action.substring(9); resetSearch(); }
        else if (action.startsWith("toggle:")) toggleModule(action.substring(7));
        else if (action.startsWith("options:")) { openOptions(action.substring(8)); resetSearch(); }
        else if (action.startsWith("favorite:")) {
            String id = action.substring(9); store.put("favorite." + id, "" + !favorite(ModuleCatalog.find(id)));
        }
        else if (action.equals("compact")) { store.put("compact", "" + !compact()); scroller.reset(); }

        else if (action.equals("sort")) { store.put("sortingOptions", SORTS[(sort() + 1) % SORTS.length]); scroller.reset(); }
        else if (action.equals("sort:prev")) { store.put("sortingOptions", SORTS[(sort() + SORTS.length - 1) % SORTS.length]); scroller.reset(); }
        else if (action.equals("compact:prev")) { store.put("compact", "" + !compact()); scroller.reset(); }
        else if (action.equals("back")) { selected = ""; resetSearch(); expanded.clear(); keybindPopup = ""; }
        else if (action.startsWith("expand:")) { String key = action.substring(7); if (!expanded.remove(key)) expanded.add(key); }
        else if (action.startsWith("profile:")) store.put("activeProfile", action.substring(8));
        else if (action.startsWith("editProfile:")) openEditor(action.substring(12));
        else if (action.equals("editHud")) editHudRequested = true;
        else if (action.startsWith("set:")) {
            String[] parts = action.split(":", 4);
            set(parts[1], parts[2], parts[3]); touch(parts[1]);
        }
        else if (action.startsWith("bool:")) {
            String[] parts = action.split(":");
            set(parts[1], parts[2], "" + !flag(parts[1], parts[2], Boolean.parseBoolean(parts[3]))); touch(parts[1]);
        }

        else if (action.startsWith("boolOpen:")) {
            String[] parts = action.split(":");
            boolean on = !flag(parts[1], parts[2], Boolean.parseBoolean(parts[3]));
            set(parts[1], parts[2], "" + on); touch(parts[1]);
            if (on) expanded.add(parts[1] + "." + parts[2]); else expanded.remove(parts[1] + "." + parts[2]);
        }

        else if (action.startsWith("enum:")) {
            String[] parts = action.split(":");
            OptionCatalog.Node n = OptionCatalog.find(parts[1], parts[2]);
            if (n != null && !n.values.isEmpty()) {
                int i = 0, size = n.values.size();
                String current = value(parts[1], parts[2], n.value);
                for (int j = 0; j < size; j++) if (n.values.get(j)[0].equals(current)) i = j;
                i = parts[3].equals("prev") ? (i + size - 1) % size : (i + 1) % size;
                set(parts[1], parts[2], n.values.get(i)[0]); touch(parts[1]);
            }
        }

        else if (action.startsWith("button:")) {
            String[] parts = action.split(":");
            if (OptionCatalog.buttons != null) OptionCatalog.buttons.accept(parts[1], parts[2]);
        }
        else if (action.startsWith("listenKey:")) keyListening = action.substring(10);
        else if (action.startsWith("editText:")) {
            String[] parts = action.split(":");
            textEditing = parts[1] + ":" + parts[2]; textMax = Integer.parseInt(parts[3]);
            OptionCatalog.Node n = OptionCatalog.find(parts[1], parts[2]);
            input = value(parts[1], parts[2], n == null ? "" : n.value);
        }
        else if (action.startsWith("keybindPopup:")) keybindPopup = action.substring(13);
        else if (action.equals("keybindPopupDone")) { keybindPopup = ""; keyListening = ""; }

        else if (action.startsWith("colorOpen:")) { String key = "color|" + action.substring(10).replace(':', '.'); if (!expanded.remove(key)) expanded.add(key); }
        else if (action.startsWith("hex:")) {
            String[] p = action.split(":");
            hexEditing = p[1] + ":" + p[2]; hexAlpha = p.length < 4 || p[3].equals("a");
            OptionCatalog.Node n = OptionCatalog.find(p[1], p[2]);
            input = color(p[1], p[2], n == null ? "FFFFFFFF" : n.value);
        }
        else if (action.startsWith("swatch:")) { String[] p = action.split(":"); setColor(p[1], p[2], p[3]); addRecent(p[3]); touch(p[1]); }
        else if (action.startsWith("colorFav:")) {
            String[] p = action.split(":"); String current = color(p[1], p[2], p[3]);
            List<String> favorites = colorList("favoriteColors");
            if (!favorites.remove(current)) { if (favorites.size() >= 12) favorites.remove(0); favorites.add(current); }
            store.put("favoriteColors", String.join("|", favorites));
        }
        else if (action.startsWith("chromaType:")) {
            String[] p = action.split(":");
            set(p[1], p[2] + "ChromaType", value(p[1], p[2] + "ChromaType", "WAVE").equals("WAVE") ? "SHIFT" : "WAVE");
        }
        else if (action.equals("searchOptions")) store.put("searchOptions", "" + !Boolean.parseBoolean(store.get("searchOptions", "true")));
        else if (action.equals("rebind")) { captureKey = true; captureTarget = "menu"; }
        else if (action.equals("rebindEmote")) { captureKey = true; captureTarget = "emote"; }
        else if (action.equals("resetModule")) resetModule(selected);
        else if (action.equals("resetPosition")) {
            List<String> ids = new ArrayList<String>(Collections.singletonList(selected));
            com.example.lunarforge.module.Module code = com.example.lunarforge.module.ModuleManager.get(selected);
            if (code != null) for (com.example.lunarforge.module.Module child : code.children()) ids.add(child.key());
            for (String id : ids) for (String option : new String[]{"anchor", "x", "y"}) store.remove(key(id, option));
        }
        store.save();
    }

    public void resetModule(String id) {
        for (OptionCatalog.Node n : OptionCatalog.flatten(OptionCatalog.options(id))) {
            store.remove(key(id, n.key));
            for (String extra : new String[]{"Chroma", "ChromaSpeed", "ChromaType", "Hue"}) store.remove(key(id, n.key + extra));
        }
        revision++;
    }

    private void editorAction(String action) {
        if (!confirm.isEmpty()) {
            if (action.equals("confirmYes")) confirmed(true);
            else if (action.equals("confirmNo")) confirmed(false);
            return;
        }
        if (iconMenu != null) {
            if (action.startsWith("editorIcon:")) editorIcon = action.substring(11);
            iconMenu = null;
            return;
        }
        editorFocus = "";
        if (action.equals("editorName")) editorFocus = "name";
        else if (action.equals("editorServer")) editorFocus = "server";
        else if (action.equals("editorIconMenu")) iconMenu = new float[]{clickX, clickY};
        else if (action.equals("editorSave")) {
            if (editorName.isEmpty()) { editorNameError = true; return; }
            store.put("profileDisplay." + editor, editorName);
            store.put("profileIcon." + editor, editorIcon);
            store.put("profileServer." + editor, editorServer);
            closeEditor();
        }
        else if (action.equals("editorBack")) {
            if (editorName.equalsIgnoreCase(displayName(editor)) && editorIcon.equalsIgnoreCase(profileIcon(editor))) closeEditor();
            else confirm = "backConfirm";
        }
        else if (action.equals("editorDelete") && canDelete(editor)) confirm = "deleteConfirm";
        else if (action.equals("editorReset") && inWorld) confirm = "resetProfile";
    }

    public void editorType(String text) {
        if (editorFocus.equals("name")) { editorName = text.length() > 20 ? text.substring(0, 20) : text; editorNameError = false; }
        else if (editorFocus.equals("server")) { editorServer = text.length() > 60 ? text.substring(0, 60) : text; editorServerError = false; }
        revision++;
    }

    public void editorEscape() {
        if (!confirm.isEmpty()) confirmed(false);
        else if (iconMenu != null) iconMenu = null;
        else editorAction("editorBack");
        revision++;
    }

    public static String defaultOption(String id, String option) {
        com.example.lunarforge.module.hud.HudElement hud = com.example.lunarforge.module.ModuleManager.hud(id);
        if (hud != null && option.equals("anchor")) return hud.anchor.id;
        if (hud != null && option.equals("x")) return "" + hud.defaultX();
        if (hud != null && option.equals("y")) return "" + hud.defaultY();
        if (option.equals("anchor")) return "";
        if (option.equals("x")) return "6";
        if (option.equals("y")) return "" + (6 + 14 * Math.max(0, ModuleCatalog.PORTED.indexOf(id)));
        OptionCatalog.Node n = OptionCatalog.find(id, option);
        return n == null ? "" : n.value;
    }

    public String color(String id, String option, String fallback) {
        String v = value(id, option, fallback).toUpperCase(Locale.ROOT);
        return v.matches("[0-9A-F]{8}") ? v : fallback.toUpperCase(Locale.ROOT);
    }
    public void setColor(String id, String option, String argb) { set(id, option, argb.toUpperCase(Locale.ROOT)); }

    public float hue(String id, String option, int argb) {
        float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
        return hsb[1] > 0 && hsb[2] > 0 ? hsb[0] : number(id, option + "Hue", hsb[0]);
    }
    public void setHsb(String id, String option, float h, float s, float b, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, b) & 0xFFFFFF;
        set(id, option + "Hue", "" + h);
        setColor(id, option, String.format(Locale.ROOT, "%08X", alpha << 24 | rgb));
    }
    public List<String> colorList(String key) {
        List<String> out = new ArrayList<String>();
        for (String c : store.get(key, "").split("\\|")) if (c.matches("[0-9A-F]{8}")) out.add(c);
        return out;
    }

    public void addRecent(String argb) {
        List<String> recent = colorList("recentColors");
        if (!recent.isEmpty() && recent.get(recent.size() - 1).equals(argb)) return;
        if (recent.size() >= 6) recent.remove(0);
        recent.add(argb);
        store.put("recentColors", String.join("|", recent));
    }
    public void resetSearch() { search = ""; scroller.reset(); searchFocused = false; }
    public void setSearch(String text) { search = text; scroller.reset(); revision++; }
}
