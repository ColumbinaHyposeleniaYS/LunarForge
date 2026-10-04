package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

final class BedwarsTimers extends Module {
    private final ModuleHypixelBedwars bedwars;
    private final BoolSetting showTitle = bool("showTitle", true);
    private final BoolSetting showName = bool("showName", true);
    private final BoolSetting showIcons = bool("showIcons", true);
    private final BoolSetting reverseText = bool("reverseText", false);
    private final BoolSetting reverseOrder = bool("reverseOrder", false);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ColorSetting titleColor = color("titleColor", -171);
    private final ColorSetting timerNameColor = color("timerNameColor", -1);
    private final ColorSetting durationColor = color("durationColor", -1);

    private static final class Timer {
        String name;
        ItemStack item;

        private JsonObject itemJson;
        private boolean itemSet;
        boolean repeating;
        long duration;
        long end = -1L;

        Timer(String name, JsonObject item, boolean repeating, long duration) {
            this.name = name;
            item(item);
            this.repeating = repeating;
            this.duration = duration;
        }

        long left() { return end - Minecraft.getSystemTime(); }

        String text() { return format(end < 0 ? duration : left() + 1550L); }

        void start(long ms) { end = Minecraft.getSystemTime() + ms; }

        void item(JsonObject json) { itemJson = json; itemSet = false; }

        ItemStack icon() {
            if (!itemSet) { itemSet = true; makeIcon(itemJson); }
            return item;
        }

        private void makeIcon(JsonObject json) {
            if (json == null) { this.item = new ItemStack(Blocks.stone); return; }
            String type = required(json, "type").getAsString().toLowerCase(Locale.ROOT);
            Item i = Item.getByNameOrId(type);
            if (i == null) { Block b = Block.getBlockFromName(type); i = b == null ? null : Item.getItemFromBlock(b); }
            this.item = i == null ? new ItemStack(Blocks.stone) : new ItemStack(i);
        }
    }

    private final TreeMap<Long, Timer> timers = new TreeMap<Long, Timer>();
    private final Set<Long> unsynced = new HashSet<Long>();
    private final TreeMap<Long, Timer> sample = new TreeMap<Long, Timer>();
    private final Cache<String, Map<Long, Timer>> saved = CacheBuilder.newBuilder().expireAfterWrite(15L, TimeUnit.MINUTES).build();
    private long changedWorld;

    BedwarsTimers(ModuleHypixelBedwars bedwars) {
        super("HYPIXEL_BEDWARS_TIMERS_CHILD_HUD", false);
        this.bedwars = bedwars;
        sample.put(0L, new Timer("Timer 1", null, false, 1500L));
        sample.put(1L, new Timer("Timer 2", null, false, 1500L));
        hud(new Hud());
        try {
            for (String channel : new String[]{"BLC|T", "badlion:timers"}) {
                FMLEventChannel c = NetworkRegistry.INSTANCE.newEventDrivenChannel(channel);
                c.register(this);
            }
        } catch (Throwable t) {
        }
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(showTitle, showName, showIcons, reverseText, reverseOrder, background, textShadow, border, borderThickness));
        page.section("colorOptions", s -> s.add(backgroundColor, borderColor, titleColor, timerNameColor, durationColor));
    }

    static String format(long ms) {
        if (ms < 0L) return "now";
        String s = " " + DurationFormatUtils.formatDuration(ms, "d'd 'H'h 'm'm 's's'");
        String t = StringUtils.replaceOnce(s, " 0d", "");
        if (t.length() != s.length()) {
            s = t;
            t = StringUtils.replaceOnce(s, " 0h", "");
            if (t.length() != s.length()) {
                s = t;
                t = StringUtils.replaceOnce(s, " 0m", "");
                if (t.length() != s.length()) s = StringUtils.replaceOnce(t, " 0s", "");
            }
        }
        s = s.trim();
        return s.isEmpty() ? "0s" : s;
    }

    private static long ticksToMs(long ticks) { return (long)(ticks / 20.0 * 1000.0); }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Iterator<Map.Entry<Long, Timer>> it = timers.entrySet().iterator(); it.hasNext(); ) {
            Timer t = it.next().getValue();
            if (t.left() > 0L) continue;
            if (t.repeating) t.start(ticksToMs(t.duration));
            else it.remove();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> { clear(); changedWorld = 0L; });
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.entity != Minecraft.getMinecraft().thePlayer) return;
        if (Minecraft.getSystemTime() - changedWorld > 1000L) {
            String map = map();
            if (map != null && !timers.isEmpty()) saved.put(map, new TreeMap<Long, Timer>(timers));
            clear();
        }
    }

    @SubscribeEvent
    public void onPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        String channel = event.packet.channel();
        if (!"BLC|T".equals(channel) && !"badlion:timers".equals(channel)) return;
        ByteBuf buf = event.packet.payload();
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        final String message = new String(data, StandardCharsets.UTF_8);
        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                handle(message);
            } catch (Throwable t) {
                org.apache.logging.log4j.LogManager.getLogger("LunarForge").warn("Failed to process Timer item: " + t.getMessage() + ", " + message);
            }
        });
    }

    private void handle(String message) {
        if (!bedwars.inGame()) return;
        int bar = message.indexOf('|');
        if (bar == -1) return;
        String kind = message.substring(0, bar);
        JsonElement e = new JsonParser().parse(message.substring(bar + 1));
        if (e.isJsonNull() || !e.isJsonObject()) return;
        JsonObject json = e.getAsJsonObject();
        switch (kind) {
            case "REGISTER":
                clear();
                if (map() != null) saved.invalidateAll();
                break;
            case "CHANGE_WORLD":
                changedWorld = Minecraft.getSystemTime();
                break;
            case "ADD_TIMER": {
                forget(map());
                long id = required(json, "id").getAsLong();
                Timer t = new Timer(required(json, "name").getAsString(), required(json, "item").getAsJsonObject(),
                    required(json, "repeating").getAsBoolean(), required(json, "time").getAsLong());
                t.start(ticksToMs(t.duration));
                timers.put(id, t);
                break;
            }
            case "REMOVE_TIMER": {
                forget(map());
                timers.remove(required(json, "id").getAsLong());
                break;
            }
            case "REMOVE_ALL_TIMERS":
                forget(map());
                clear();
                break;
            case "UPDATE_TIMER": {
                restore(map());
                Timer t = timers.get(required(json, "id").getAsLong());
                if (t == null) break;
                t.name = required(json, "name").getAsString();
                t.item(required(json, "item").getAsJsonObject());
                t.repeating = required(json, "repeating").getAsBoolean();
                t.duration = required(json, "time").getAsLong();
                t.start(ticksToMs(required(json, "currentTime").getAsLong()));
                break;
            }
            case "SYNC_TIMERS": {
                if (restore(map())) unsynced.addAll(timers.keySet());
                long id = required(json, "id").getAsLong();
                long time = required(json, "time").getAsLong();
                Timer t;
                if (!unsynced.remove(id) || (t = timers.get(id)) == null) break;
                t.start(ticksToMs(time) - 550L);
                break;
            }
            default: break;
        }
    }

    private void forget(String map) { if (map != null) saved.invalidate(map); }

    private boolean restore(String map) {
        Map<Long, Timer> m = map == null ? null : saved.getIfPresent(map);
        if (m == null) return false;
        saved.invalidate(map);
        timers.putAll(m);
        return true;
    }

    private void clear() { timers.clear(); unsynced.clear(); }

    private String map() {
        if (!bedwars.inGame()) return null;
        HypixelLocation.Location l = HypixelLocation.get();
        return l == null ? null : l.map;
    }

    private static JsonElement required(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) throw new NoSuchElementException("Timers message doesn't have required '" + key + "'");
        return json.get(key);
    }

    private final class Hud extends HudElement {
        Hud() { super(BedwarsTimers.this, 0.0f, 0.0f, HudAnchor.TOP_LEFT); }

        @Override public boolean visible(boolean preview) {
            boolean shows = bedwars.isEnabled() && bedwars.inBedwars() && (preview || !timers.isEmpty());
            if (!shows) { size(0, 0); return false; }
            if (width() == 0.0f) size(60.0f, 40.0f);
            return true;
        }

        private void icon(ItemStack item, float x, float y) {
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            Draw.item(item, x, y + 1.0f);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
        }

        @Override public void render(boolean preview) {
            TreeMap<Long, Timer> shown = preview && timers.isEmpty() ? sample : timers;
            if (background.on()) Draw.fill(backgroundColor, 0, 0, width(), height());
            if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            boolean shadow = textShadow.on(), reverse = reverseText.on();
            float width = 16.0f, x, y = 2.0f;
            if (showTitle.on()) {
                String title = "§l" + lang("timers");
                Draw.text(titleColor, title, 2.0f, y, shadow);
                width = Draw.width(title) + 4.0f;
                y += 12.0f;
            }
            Iterator<Timer> it = reverseOrder.on() ? shown.descendingMap().values().iterator() : shown.values().iterator();
            while (it.hasNext()) {
                Timer t = it.next();
                x = 2.0f;
                float top = y, w = x;
                if (showIcons.on()) {
                    w += 14.0f;
                    if (!reverse) { icon(t.icon(), x - 2.0f, y - 1.0f); x += 16.0f; }
                }
                float textWidth = 0.0f;
                if (showName.on()) {
                    textWidth = Draw.width(t.name);
                    Draw.text(timerNameColor, t.name, x, y, shadow);
                    y += 5.0f;
                }
                String time = t.text();
                textWidth = Math.max(textWidth, Draw.width(time));
                y += 4.0f;
                Draw.text(durationColor, time, x, y, shadow);
                if (showIcons.on() && reverse) {
                    w += 1.0f;
                    x += textWidth + 2.0f;
                    icon(t.icon(), x, top - 1.0f);
                }
                w += textWidth + 2.0f;
                width = Math.max(width, w + 2.0f);
                y += 12.0f;
            }
            size(width, y - (showName.on() ? 2 : 0));
        }
    }
}
