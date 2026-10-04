package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.KeySetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;

public final class ModuleQuickplay extends Module {
    static ModuleQuickplay instance;
    private final KeySetting quickplayUIKeybind = add(new KeySetting("quickplayUIKeybind", "R", true));

    private volatile boolean loaded;
    private volatile List<QuickplayGame> games;

    private final Map<String, KeySetting> gameKeys = new HashMap<String, KeySetting>();
    private boolean loading, bound;

    public ModuleQuickplay() {
        super("QUICKPLAY", false);
        instance = this;
    }

    @Override protected void layout(Page page) { page.add(quickplayUIKeybind); }

    private void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            try (InputStreamReader in = new InputStreamReader(Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("lunarforge", "hypixel/quickplay.json")).getInputStream(), StandardCharsets.UTF_8)) {
                List<QuickplayGame> list = new ArrayList<QuickplayGame>();
                for (JsonElement e : new JsonParser().parse(in).getAsJsonArray()) list.add(parse(e));
                games = Collections.unmodifiableList(list);
            } catch (Exception e) {
                LogManager.getLogger("LunarForge").error("Load QuickPlay Games", e);
            }
            loaded = true;
        }, "QuickPlay Games").start();
    }

    private static QuickplayGame parse(JsonElement element) {
        QuickplayGame g = new QuickplayGame();
        JsonObject o = element.getAsJsonObject();
        g.key = o.get("key").getAsString();
        g.name = o.get("name").getAsString();
        if (o.has("icon") && !o.get("icon").isJsonNull()) g.icon = o.get("icon").getAsString();
        if (o.has("command") && !o.get("command").isJsonNull()) g.command = o.get("command").getAsString();
        if (o.has("modes")) {
            List<QuickplayGame> modes = new ArrayList<QuickplayGame>();
            for (JsonElement m : o.get("modes").getAsJsonArray()) {
                QuickplayGame mode = parse(m);
                modes.add(mode);
                mode.parent = g;
            }
            g.modes = Collections.unmodifiableList(modes);
        }
        return g;
    }

    boolean loaded() { return loaded; }

    List<QuickplayGame> games() { return games; }

    Set<String> favorites() {
        Set<String> out = new LinkedHashSet<String>();
        for (String s : ModuleManager.store(key(), "qpFavorites", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    void favorite(String gameKey, boolean on) {
        Set<String> f = favorites();
        if (on) f.add(gameKey); else f.remove(gameKey);
        ModuleManager.put(key(), "qpFavorites", String.join(",", f));
    }

    KeySetting keyFor(String gameKey) {
        KeySetting k = gameKeys.get(gameKey);
        if (k == null) {
            k = add(new KeySetting(gameKey, "NONE", true));
            gameKeys.put(gameKey, k);
        }
        return k;
    }

    QuickplayGame find(String gameKey) { return games == null ? null : find(gameKey, games); }

    private static QuickplayGame find(String gameKey, List<QuickplayGame> list) {
        for (QuickplayGame g : list) {
            if (g.key.equals(gameKey)) return g;
            QuickplayGame hit = g.modes.isEmpty() ? null : find(gameKey, g.modes);
            if (hit != null) return hit;
        }
        return null;
    }

    static void send(String command) {
        if (Minecraft.getMinecraft().thePlayer != null) Minecraft.getMinecraft().thePlayer.sendChatMessage(command);
    }

    private void bindSaved(List<QuickplayGame> list) {
        for (QuickplayGame g : list) {
            if (!"NONE".equals(ModuleManager.store(key(), g.key, "NONE"))) keyFor(g.key);
            bindSaved(g.modes);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (isEnabled() && !loading) load();
        if (loaded && games != null && !bound) {
            bound = true;
            bindSaved(games);
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean active = isEnabled() && mc.currentScreen == null;
        if (quickplayUIKeybind.pressed(active)) mc.displayGuiScreen(new QuickplayScreen(null, null));
        for (Map.Entry<String, KeySetting> e : gameKeys.entrySet()) {
            if (!e.getValue().pressed(active)) continue;
            QuickplayGame g = find(e.getKey());
            if (g != null) send(g.command());
        }
    }
}
