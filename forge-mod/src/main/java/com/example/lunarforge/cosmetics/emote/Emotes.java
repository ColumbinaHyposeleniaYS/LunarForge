package com.example.lunarforge.cosmetics.emote;

import com.example.lunarforge.cosmetics.LunarCdn;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.LogManager;

public final class Emotes {
    public static final int WHEEL = 32;
    private static List<Emote> all;
    private static final Map<Integer, Emote> BY_ID = new HashMap<Integer, Emote>();
    private static final Map<String, String> MESH_TEXTURES = new HashMap<String, String>();
    private static final List<String> FILES = new ArrayList<String>();
    private static final List<Integer> EQUIPPED = new ArrayList<Integer>();
    private static boolean equippedLoaded;
    public static int revision;

    private static Future<Bobj[]> loading;
    private static Bobj wide, slim, library;

    private static final Map<UUID, Playing> PLAYING = new HashMap<UUID, Playing>();

    private Emotes() {}

    public static final class Emote {
        public final int id; public final String key, name; public final int duration; public final boolean looping, stopOnMove;
        final List<String[]> meshes = new ArrayList<String[]>();
        Emote(int id, String key, int duration, boolean looping, boolean stopOnMove) {
            this.id = id; this.key = key; this.duration = duration; this.looping = looping; this.stopOnMove = stopOnMove;
            StringBuilder b = new StringBuilder();
            for (String w : key.split("_")) if (!w.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            this.name = b.toString();
        }
        public String icon() { return "emotes/icons/" + id + ".webp"; }
        public boolean hasIcon() { return LunarCdn.has(icon()); }
    }

    static final class Playing {
        final Emote emote; final long startTick; final long startMs;
        Playing(Emote emote, long startTick) { this.emote = emote; this.startTick = startTick; this.startMs = System.currentTimeMillis(); }
    }

    public static synchronized List<Emote> all() {
        if (all != null) return all;
        List<Emote> list = new ArrayList<Emote>();
        try (Reader in = new InputStreamReader(Emotes.class.getResourceAsStream("/assets/lunarforge/emotes/emotes.json"), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(in).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("emotes")) {
                JsonObject o = e.getAsJsonObject();
                Emote em = new Emote(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("duration").getAsInt(),
                    o.get("looping").getAsBoolean(), o.has("stopOnMove") && o.get("stopOnMove").getAsBoolean());
                if (o.has("meshes")) for (JsonElement m : o.getAsJsonArray("meshes"))
                    em.meshes.add(new String[]{m.getAsJsonObject().get("name").getAsString(), m.getAsJsonObject().get("show_at").getAsString()});
                list.add(em); BY_ID.put(em.id, em);
            }
            for (Map.Entry<String, JsonElement> m : root.getAsJsonObject("meshes").entrySet())
                if (m.getValue().getAsJsonObject().has("texture"))
                    MESH_TEXTURES.put(m.getKey(), m.getValue().getAsJsonObject().get("texture").getAsString().replace("lunar:", ""));
            for (String group : new String[]{"props", "actions"})
                for (JsonElement f : root.getAsJsonArray(group)) FILES.add(f.getAsString().replace("lunar:", ""));
            FILES.add("emotes/models/entity/actions-9.bobj");
            FILES.add(0, "emotes/models/props.bobj");
        } catch (Exception ex) {
            LogManager.getLogger("LunarForge/Emotes").error("Could not read emotes.json", ex);
        }
        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return all = Collections.unmodifiableList(list);
    }

    public static Emote get(int id) { all(); return BY_ID.get(id); }

    public static synchronized boolean ready() {
        if (library != null) return true;
        all();
        if (loading == null) {
            ExecutorService ex = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "LunarForge emote load"); t.setDaemon(true); return t; });
            loading = ex.submit(() -> {
                Bobj w = Bobj.parse(LunarCdn.fetch("emotes/models/entity/default.bobj").get());
                Bobj s = Bobj.parse(LunarCdn.fetch("emotes/models/entity/slim.bobj").get());
                Bobj lib = new Bobj(w);
                List<Future<byte[]>> files = new ArrayList<Future<byte[]>>();
                for (String f : FILES) files.add(LunarCdn.fetch(f));
                for (Future<byte[]> f : files) { byte[] d = f.get(); if (d != null) lib.read(d); }
                return new Bobj[]{w, s, lib};
            });
            ex.shutdown();
        }
        if (!loading.isDone()) return false;
        try {
            Bobj[] b = loading.get();
            wide = b[0]; slim = b[1]; library = b[2];
            return true;
        } catch (Exception e) {
            LogManager.getLogger("LunarForge/Emotes").warn("Could not load emotes: {}", e.toString());
            loading = null;
            return false;
        }
    }

    static Bobj body(boolean slimArms) { return slimArms ? slim : wide; }
    static Bobj library() { return library; }

    static String meshTexture(String mesh) {
        String t = MESH_TEXTURES.get(mesh);
        return t != null ? t : LunarCdn.has("emotes/textures/" + mesh + ".webp") ? "emotes/textures/" + mesh + ".webp" : null;
    }

    private static long now() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld == null ? 0 : mc.theWorld.getTotalWorldTime();
    }

    public static void play(EntityPlayer p, Emote e) { PLAYING.put(p.getUniqueID(), new Playing(e, now())); }

    public static void stop(EntityPlayer p) { PLAYING.remove(p.getUniqueID()); }

    public static Emote playing(EntityPlayer p) { Playing pl = PLAYING.get(p.getUniqueID()); return pl == null ? null : pl.emote; }

    static float frame(EntityPlayer p, float partialTicks, boolean preview) {
        Playing pl = PLAYING.get(p.getUniqueID());
        if (pl == null) return -1;
        float f = preview || Minecraft.getMinecraft().theWorld == null ? (System.currentTimeMillis() - pl.startMs) / 50f : now() - pl.startTick + partialTicks;
        if (f >= pl.emote.duration) {
            if (pl.emote.looping || preview) f %= Math.max(1, pl.emote.duration);
            else return -1;
        }
        return f;
    }

    static void finish(EntityPlayer p) { PLAYING.remove(p.getUniqueID()); }

    private static File file() { return com.example.lunarforge.cosmetics.CosmeticFolder.config("emotes.json"); }

    private static void loadEquipped() {
        if (equippedLoaded) return;
        equippedLoaded = true;
        File f = file();
        if (!f.isFile()) return;
        try (Reader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            for (JsonElement e : new JsonParser().parse(in).getAsJsonObject().getAsJsonArray("equipped"))
                if (get(e.getAsInt()) != null && EQUIPPED.size() < WHEEL) EQUIPPED.add(e.getAsInt());
        } catch (Exception ex) {
            LogManager.getLogger("LunarForge/Emotes").warn("Could not read {}: {}", f, ex.toString());
        }
    }

    private static void save() {
        JsonObject root = new JsonObject(); JsonArray ids = new JsonArray();
        for (Integer id : EQUIPPED) ids.add(new JsonPrimitive(id));
        root.add("equipped", ids);
        File f = file(); f.getParentFile().mkdirs();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { out.write(root.toString()); }
        catch (IOException ex) { LogManager.getLogger("LunarForge/Emotes").warn("Could not save {}: {}", f, ex.toString()); }
    }

    public static synchronized List<Emote> equipped() {
        loadEquipped();
        List<Emote> out = new ArrayList<Emote>();
        for (Integer id : EQUIPPED) out.add(get(id));
        return out;
    }

    public static synchronized boolean isEquipped(Emote e) { loadEquipped(); return EQUIPPED.contains(e.id); }

    public static synchronized boolean toggle(Emote e) {
        loadEquipped();
        if (EQUIPPED.remove((Integer)e.id)) { revision++; save(); return true; }
        if (EQUIPPED.size() >= WHEEL) return false;
        EQUIPPED.add(e.id); revision++; save();
        return true;
    }
}
