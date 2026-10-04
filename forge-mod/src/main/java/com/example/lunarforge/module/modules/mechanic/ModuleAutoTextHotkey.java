package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ModuleAutoTextHotkey extends Module {
    private static final int MAX_HOTKEYS = 50;

    private static final int MIN_HOTKEYS = 3;

    private static final String DEFAULT_TEXT = "/Command";

    private static final String NO_KEY = "KEY_NONE";

    private final BoolSetting notifyOnBlockedInput = bool("notifyOnBlockedInput", true);

    private long blockedAt;

    private final Map<String, Integer> presses = new HashMap<String, Integer>();
    private final Map<String, Long> lastPress = new HashMap<String, Long>();

    private static final Comparator<Entry> ORDER = new Comparator<Entry>() {
        @Override public int compare(Entry a, Entry b) { return index(a.optionKey) - index(b.optionKey); }
    };

    public ModuleAutoTextHotkey() {
        super("AUTO_TEXT_HOTKEY", false);
    }

    @Override protected void layout(Page page) {
        page.add(notifyOnBlockedInput).hideIf(() -> blockedTextInputs().isEmpty());
    }

    static final class Entry {
        String optionKey;
        String text = DEFAULT_TEXT;
        String key = NO_KEY;
        boolean shift, control, alt;
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!isEnabled() || Minecraft.getMinecraft().currentScreen != null) return;
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE || !Keyboard.getEventKeyState()) return;
        for (Entry entry : entries()) {
            if (keyCode(entry.key) != key || !modifiersHeld(entry)) continue;
            press(entry);
        }
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled() || Minecraft.getMinecraft().currentScreen != null) return;
        int button = Mouse.getEventButton();
        if (button < 0 || !Mouse.getEventButtonState()) return;
        String name = "KEY_MOUSE" + (button + 1);
        for (Entry entry : entries()) {
            if (!name.equals(entry.key) || !modifiersHeld(entry)) continue;
            press(entry);
        }
    }

    private static boolean modifiersHeld(Entry entry) {
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean alt = Keyboard.isKeyDown(Keyboard.KEY_LMENU);
        return (!entry.shift || shift) && (!entry.control || control) && (!entry.alt || alt);
    }

    private static int keyCode(String name) {
        if (name == null || !name.startsWith("KEY_") || name.startsWith("KEY_MOUSE")) return Keyboard.KEY_NONE;
        return Keyboard.getKeyIndex(name.substring(4));
    }

    private void press(Entry entry) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isUsingItem() || player.isBlocking()
                || System.currentTimeMillis() - blockedAt < 100L) return;
        String text = entry.text;
        if (text == null || text.trim().isEmpty() || text.equalsIgnoreCase(DEFAULT_TEXT)) return;
        long now = System.currentTimeMillis();
        Long last = lastPress.get(entry.optionKey);
        if (last != null && now - last < 1000L) {
            int count = presses.containsKey(entry.optionKey) ? presses.get(entry.optionKey) : 0;
            presses.put(entry.optionKey, count + 1);
            if (count + 1 > 2) return;
        } else {
            presses.remove(entry.optionKey);
        }
        lastPress.put(entry.optionKey, now);
        if (blockedTextInput(text)) {
            notifyBlocked();
            return;
        }
        if (text.startsWith("/")) {
            player.sendChatMessage(text);
        } else {
            if (blockChatMessageTextInputs()) {
                notifyBlocked();
                return;
            }
            player.sendChatMessage(text);
        }
    }

    private void notifyBlocked() {
        if (notifyOnBlockedInput.on()) LunarNotifications.info(LunarLang.get("popups", "hotkeyBlockedByServerPopup"));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && player.isBlocking()) blockedAt = System.currentTimeMillis();
    }

    private static boolean blockChatMessageTextInputs() {
        return false;
    }

    private static List<String> blockedTextInputs() {
        return Collections.emptyList();
    }

    private static boolean blockedTextInput(String text) {
        for (String prefix : blockedTextInputs()) {
            if (text.toLowerCase().startsWith(prefix.toLowerCase())) return true;
        }
        return false;
    }

    List<Entry> entries() {
        List<Entry> list = storedEntries();
        int max = 0;
        for (Entry entry : list) max = Math.max(max, index(entry.optionKey));

        while (list.size() < MIN_HOTKEYS) {
            max++;
            Entry entry = new Entry();
            entry.optionKey = max + "hotkey";
            list.add(entry);
        }
        Collections.sort(list, ORDER);
        return list;
    }

    private List<Entry> storedEntries() {
        List<Entry> list = new ArrayList<Entry>();
        UiModel model = ModuleManager.model();
        if (model != null) {
            String prefix = model.key(key(), "");
            for (String stored : new ArrayList<String>(model.store.keys())) {
                if (!stored.startsWith(prefix)) continue;
                String name = stored.substring(prefix.length());
                if (index(name) <= 0) continue;
                Entry entry = new Entry();
                entry.optionKey = name;
                read(entry, ModuleManager.store(key(), name, null));
                list.add(entry);
            }
        }
        Collections.sort(list, ORDER);
        return list;
    }

    Entry addEntry() {
        List<Entry> list = entries();
        int max = 0;
        for (Entry entry : list) max = Math.max(max, index(entry.optionKey));
        if (max >= MAX_HOTKEYS) return null;
        Entry entry = new Entry();
        entry.optionKey = (max + 1) + "hotkey";
        write(entry);
        return entry;
    }

    void removeEntry(Entry entry) {
        if (entries().size() <= MIN_HOTKEYS) return;
        ModuleManager.remove(key(), entry.optionKey);
        renumber();
    }

    private void renumber() {
        List<Entry> list = storedEntries();
        for (int i = 0; i < list.size(); i++) {
            Entry entry = list.get(i);
            String wanted = (i + 1) + "hotkey";
            if (!entry.optionKey.equals(wanted)) {
                ModuleManager.remove(key(), entry.optionKey);
                entry.optionKey = wanted;
            }
            write(entry);
        }
    }

    private static int index(String name) {
        if (name == null || !name.endsWith("hotkey")) return -1;
        try {
            return Integer.parseInt(name.substring(0, name.length() - "hotkey".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void read(Entry entry, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(raw);
        } catch (RuntimeException e) {
            entry.text = raw;
            return;
        }
        if (!parsed.isJsonObject()) {
            entry.text = raw;
            return;
        }
        JsonObject object = parsed.getAsJsonObject();
        if (object.has("value")) entry.text = object.get("value").getAsString();
        JsonObject keybind = object.has(entry.optionKey) && object.get(entry.optionKey).isJsonObject()
                ? object.getAsJsonObject(entry.optionKey) : null;
        if (keybind == null) return;
        if (keybind.has("value")) entry.key = keybind.get("value").getAsString();
        entry.shift = keybind.has("shift") && keybind.get("shift").getAsBoolean();
        entry.control = keybind.has("control") && keybind.get("control").getAsBoolean();
        entry.alt = keybind.has("alt") && keybind.get("alt").getAsBoolean();
    }

    private void write(Entry entry) {
        JsonObject keybind = new JsonObject();
        keybind.addProperty("alt", entry.alt);
        keybind.addProperty("shift", entry.shift);
        keybind.addProperty("control", entry.control);
        keybind.addProperty("value", entry.key);
        JsonObject object = new JsonObject();
        object.addProperty("value", entry.text);
        object.add(entry.optionKey, keybind);
        ModuleManager.put(key(), entry.optionKey, object.toString());
    }
}
