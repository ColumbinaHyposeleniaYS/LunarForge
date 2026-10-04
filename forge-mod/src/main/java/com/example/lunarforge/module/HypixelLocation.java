package com.example.lunarforge.module;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class HypixelLocation {
    public static final class Location {
        @SerializedName("server") public String server;
        @SerializedName("gametype") public String gametype;
        @SerializedName("mode") public String mode;
        @SerializedName("map") public String map;
        @SerializedName("lobbyname") public String lobbyname;

        Location(String server, String gametype) { this.server = server; this.gametype = gametype; }

        public boolean empty() { return (server == null || server.isEmpty()) && (gametype == null || gametype.isEmpty()); }

        public boolean lobby() { return lobbyname != null; }
    }

    private static final Location NONE = new Location("", "");
    private static final HypixelLocation INSTANCE = new HypixelLocation();
    private static final Gson GSON = new Gson();
    private static final List<BiConsumer<Location, Location>> LISTENERS = new ArrayList<BiConsumer<Location, Location>>();

    private Location location = NONE;

    private long sentAt, typedAt;

    private int countdown, retries, startDelay = 50;
    private int sentHistory = -1;

    private HypixelLocation() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    public static Location get() { return INSTANCE.location; }

    public static void listen(BiConsumer<Location, Location> listener) { LISTENERS.add(listener); }

    private void set(Location next) {
        Location previous = location;
        location = next;
        for (BiConsumer<Location, Location> l : LISTENERS) l.accept(previous, next);
    }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) {
        if (!event.world.isRemote) return;
        if (!location.empty()) set(NONE);
        if (!Server.hypixel()) {
            countdown = 0;
            return;
        }
        arm();
    }

    private void arm() {
        retries = 3;
        countdown = Math.max(startDelay, 20);
    }

    private boolean armedForBrand;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) { armedForBrand = false; return; }
        if (!armedForBrand && Server.hypixel()) { armedForBrand = true; if (location.empty()) arm(); }
        List<String> sent = mc.ingameGUI.getChatGUI().getSentMessages();
        if (sentHistory >= 0 && sent.size() > sentHistory) {
            String last = sent.get(sent.size() - 1);
            if (last.startsWith("/locraw")) {
                typedAt = System.currentTimeMillis();
                if (countdown > 0) {
                    if (retries > 0) { countdown = 10 * (4 - retries); --retries; } else countdown = 0;
                }
            } else if (countdown > 0 && countdown < 4) {
                countdown = 4;
            }
        }
        sentHistory = sent.size();
        if (startDelay > 0) --startDelay;
        if (countdown > 0 && --countdown == 0) {
            mc.thePlayer.sendChatMessage("/locraw");
            typedAt = -1L;
            sentAt = System.currentTimeMillis();
            if (retries > 0) { countdown = 10 * (4 - retries); --retries; }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2 || !Server.hypixel()) return;
        String text = event.message.getUnformattedText();
        long now = System.currentTimeMillis();
        if (now - sentAt < 1000L && text.equalsIgnoreCase("You are sending commands too fast! Please slow down.")) {
            event.setCanceled(true);
        }
        if (!text.startsWith("{\"")) return;
        try {
            Location parsed = GSON.fromJson(text, Location.class);
            if (typedAt < 0L || now - typedAt > 5000L) event.setCanceled(true);
            if (parsed.gametype == null || parsed.server == null) {
                if (location.empty()) return;
                parsed = NONE;
            } else {
                countdown = 0;
                retries = 0;
            }
            set(parsed);
        } catch (Exception ignored) {}
    }
}
