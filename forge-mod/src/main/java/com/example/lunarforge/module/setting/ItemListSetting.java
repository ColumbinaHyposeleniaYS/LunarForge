package com.example.lunarforge.module.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * A persisted list of item entries (Vape v4's LimitValue / ItemLimitData).
 * One entry per line in the stored string, each line "name|meta|enabled";
 * meta is -1 when any metadata matches. Entries match a stack by registry
 * name ("minecraft:stone"), numeric id, or display name, all case
 * insensitive. An empty list matches everything for matchesOrEmpty (Vape
 * semantics) and nothing for isValid(stack, requireEntry=true).
 */
public final class ItemListSetting extends Setting<String> {

    public static final class Entry {
        public String name;
        public int metadata;
        public boolean enabled;

        public Entry(String name, int metadata, boolean enabled) {
            this.name = name;
            this.metadata = metadata;
            this.enabled = enabled;
        }
    }

    /** Vape Scaffold's default "Block Blacklist" (ItemLimitData.DEFAULT_BLOCK_BLACKLIST). */
    public static final List<String> SCAFFOLD_BLACKLIST_DEFAULT = Arrays.asList(
            "Dispenser", "Note Block", "Cobweb", "TNT", "Monster Spawner", "Enchantment Table",
            "Oak Fence", "Jukebox", "Melon", "Command Block", "Anvil", "Glass Pane",
            "White Stained Glass Pane", "Iron Bars", "Ice", "Packed Ice", "Block of Redstone",
            "Gold Ore", "Iron Ore", "Coal Ore", "Lapis Lazuli Ore", "Redstone Ore",
            "Acacia Wood Stairs", "Wooden Pressure Plate", "Stone Pressure Plate", "Beacon",
            "Oak Sapling", "Powered Rail", "Detector Rail", "Shrub", "Dead Bush", "Dandelion",
            "Poppy", "Mushroom", "Ladder", "Rail", "Wooden Trapdoor", "Lily Pad",
            "Tripwire Hook", "Carpet", "Snow", "Trapped Chest", "Daylight Sensor", "Hopper",
            "Chest", "Torch", "Lever", "Redstone Torch", "Button", "Cactus");

    public ItemListSetting(String key, List<String> defaultEntries) {
        super(key, serializeDefaults(defaultEntries));
    }

    private static String serializeDefaults(List<String> entries) {
        StringBuilder out = new StringBuilder();
        for (String name : entries) {
            if (out.length() > 0) out.append('\n');
            out.append(name.replace("|", " ")).append("|-1|true");
        }
        return out.toString();
    }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<Entry>();
        for (String line : get().split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|");
            String name = parts.length > 0 ? parts[0].trim() : "";
            int metadata = -1;
            boolean enabled = true;
            if (parts.length > 1) {
                try { metadata = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
            }
            if (parts.length > 2) enabled = parts[2].trim().equals("true");
            if (!name.isEmpty()) out.add(new Entry(name, metadata, enabled));
        }
        return out;
    }

    private void store(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry e : entries) {
            if (out.length() > 0) out.append('\n');
            out.append(e.name.replace("|", " ")).append('|').append(e.metadata).append('|').append(e.enabled);
        }
        set(out.toString());
        changed();
    }

    public void add(String name, int metadata, boolean enabled) {
        name = name.trim();
        if (name.isEmpty()) return;
        for (Entry e : entries()) {
            if (e.name.equalsIgnoreCase(name) && e.metadata == metadata) return;
        }
        List<Entry> entries = entries();
        entries.add(new Entry(name, metadata, enabled));
        store(entries);
    }

    public void addHeld(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return;
        String id = Item.itemRegistry.getNameForObject(stack.getItem()).toString();
        int metadata = stack.getHasSubtypes() ? stack.getItemDamage() : -1;
        add(id, metadata, true);
    }

    public void remove(int index) {
        List<Entry> entries = entries();
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            store(entries);
        }
    }

    public void setEnabled(int index, boolean enabled) {
        List<Entry> entries = entries();
        if (index >= 0 && index < entries.size()) {
            entries.get(index).enabled = enabled;
            store(entries);
        }
    }

    /** Vape LimitValue.matches: any enabled entry matches the stack. */
    public boolean matches(ItemStack stack) {
        return isValid(stack, false);
    }

    /** Vape LimitValue.matchesOrEmpty: an empty list lets everything through. */
    public boolean matchesOrEmpty(ItemStack stack) {
        List<Entry> entries = entries();
        if (entries.isEmpty()) return true;
        for (Entry e : entries) {
            if (e.enabled && matchesEntry(e, stack)) return true;
        }
        return false;
    }

    /** Vape LimitValue.doesNotMatch: true when no enabled entry matches. */
    public boolean doesNotMatch(ItemStack stack) {
        List<Entry> entries = entries();
        for (Entry e : entries) {
            if (e.enabled && matchesEntry(e, stack)) return false;
        }
        return true;
    }

    private boolean isValid(ItemStack stack, boolean requireEntryWhenEmpty) {
        List<Entry> entries = entries();
        if (entries.isEmpty()) return !requireEntryWhenEmpty;
        for (Entry e : entries) {
            if (e.enabled && matchesEntry(e, stack)) return true;
        }
        return false;
    }

    /** Vape ItemLimitData.matches: registry id, numeric id or display name, optional metadata. */
    private static boolean matchesEntry(Entry entry, ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String needle = entry.name.toLowerCase(Locale.ROOT);
        String registryName = null;
        try {
            Object raw = Item.itemRegistry.getNameForObject(stack.getItem());
            if (raw instanceof ResourceLocation) registryName = ((ResourceLocation) raw).toString();
        } catch (IllegalArgumentException ignored) {}
        if (needle.startsWith("minecraft:") && registryName != null && registryName.equals(needle)) return true;
        if (registryName != null && registryName.toLowerCase(Locale.ROOT).endsWith(":" + needle)) return true;
        if (needle.matches("\\d+") && Integer.parseInt(needle) == Item.getIdFromItem(stack.getItem())) return true;
        if (stack.getDisplayName().toLowerCase(Locale.ROOT).equals(needle)) return true;
        if (entry.metadata != -1 && entry.metadata == stack.getItemDamage()
                && registryName != null && registryName.toLowerCase(Locale.ROOT).endsWith(":" + needle)) return true;
        return false;
    }

    @Override protected String parse(String raw) { return raw == null ? "" : raw; }

    @Override protected String kind() { return "string"; }

    @Override protected void describe(Map<String, Object> fields) {
        fields.put("maxLength", 0);
    }
}
