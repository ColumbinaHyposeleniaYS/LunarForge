package com.example.lunarforge.cosmetics;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.logging.log4j.LogManager;

public final class Loadout {
    private static final Map<CosmeticType, Integer> EQUIPPED = new LinkedHashMap<CosmeticType, Integer>();
    private static boolean loaded;

    public static int revision;

    private Loadout() {}

    private static File file() { return CosmeticFolder.config("equipped.json"); }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.isFile()) return;
        try (Reader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(in).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("equipped")) {
                Cosmetic c = CosmeticCatalog.get(e.getAsInt());
                if (c != null) EQUIPPED.put(c.slot(), c.id);
            }
        } catch (Exception ex) {
            LogManager.getLogger("LunarForge/Cosmetics").warn("Could not read {}: {}", f, ex.toString());
        }
    }

    private static synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray ids = new JsonArray();
        for (Integer id : EQUIPPED.values()) ids.add(new JsonPrimitive(id));
        root.add("equipped", ids);
        File f = file();
        f.getParentFile().mkdirs();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, out);
        } catch (IOException ex) {
            LogManager.getLogger("LunarForge/Cosmetics").warn("Could not save {}: {}", f, ex.toString());
        }
    }

    public static synchronized boolean isEquipped(Cosmetic c) { load(); Integer id = EQUIPPED.get(c.slot()); return id != null && id == c.id; }

    public static synchronized Cosmetic inSlot(CosmeticType slot) { load(); Integer id = EQUIPPED.get(slot); return id == null ? null : CosmeticCatalog.get(id); }

    public static synchronized List<Cosmetic> equipped() {
        load();
        List<Cosmetic> out = new ArrayList<Cosmetic>();
        for (Integer id : EQUIPPED.values()) { Cosmetic c = CosmeticCatalog.get(id); if (c != null) out.add(c); }
        return out;
    }

    public static synchronized void toggle(Cosmetic c) {
        load();
        if (isEquipped(c)) EQUIPPED.remove(c.slot()); else { EQUIPPED.remove(c.slot()); EQUIPPED.put(c.slot(), c.id); }
        revision++; save();
    }

    public static synchronized void unequipAll() { load(); EQUIPPED.clear(); revision++; save(); }
}
