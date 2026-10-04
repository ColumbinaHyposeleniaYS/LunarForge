package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.TextSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.network.OldServerPinger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ModulePing extends Module {
    public enum Mode implements ChoiceSetting.Option {
        LATEST("latest"), AVERAGE("averaged");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "lunar-ping-mod-thread");
        thread.setDaemon(true);
        return thread;
    });

    private final NumberSetting updateIntervalSec = integer("updateIntervalSec", 5, 1, 120);
    private final ChoiceSetting<Mode> pingMode = choice("pingMode", Mode.LATEST);
    private final NumberSetting averageSamples = integer("averageSamples", 3, 2, 20);
    private final BoolSetting pingSpikeDetection = bool("pingSpikeDetection", false);
    private final NumberSetting mediumSpikeThreshold = integer("mediumSpikeThreshold", 20, 1, 200);
    private final NumberSetting largeSpikeThreshold = integer("largeSpikeThreshold", 50, 1, 200);
    private final ColorSetting mediumSpikeColor = color("mediumSpikeColor", -28416);
    private final ColorSetting largeSpikeColor = color("largeSpikeColor", -65536);
    private final BoolSetting pingShowMs = bool("pingShowMs", true);
    final BoolSetting showPingPrefix = bool("showPingPrefix", true);
    final TextSetting pingPrefix = add(new TextSetting("pingPrefix", "Ping: ").maxLength(10));
    final ColorSetting pingPrefixColor = color("pingPrefixColor", -1);
    private final BoolSetting dynamicPingColor = bool("dynamicPingColor", true);
    private final ColorSetting pingNumberColor = color("pingNumberColor", -171);
    private final ColorSetting lowPingNumberColor = color("lowPingNumberColor", -11141291);
    private final ColorSetting mediumPingNumberColor = color("mediumPingNumberColor", -171);
    private final ColorSetting highPingNumberColor = color("highPingNumberColor", -43691);
    private final ColorSetting extremePingNumberColor = color("extremePingNumberColor", -5636096);

    private final OldServerPinger pinger = new OldServerPinger();
    private int seconds;
    private int latest = -1;
    private final List<Integer> samples = new ArrayList<Integer>();
    private int average = -1;
    private int spike = -1;
    private long spikeAt = -1L;
    private int lastServerPing = -1;
    private long lastSecond = -1L;

    public ModulePing() {
        super("PING", false);
        child(new ModulePingHud(this), "generalOptions");
        child(new ModulePingNametag(this), "generalOptions");
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(updateIntervalSec, pingMode);
            s.add(averageSamples).hideIf(() -> !pingMode.is(Mode.AVERAGE));
            s.group(pingSpikeDetection, g -> g.add(mediumSpikeThreshold, mediumSpikeColor, largeSpikeThreshold, largeSpikeColor));
        });
        page.section("displayOptions", s -> {
            s.add(pingShowMs);
            s.group(showPingPrefix, g -> g.add(pingPrefix, pingPrefixColor));
            s.add(dynamicPingColor);
            s.add(pingNumberColor).hideIf(dynamicPingColor::on);
            s.add(lowPingNumberColor, mediumPingNumberColor, highPingNumberColor, extremePingNumberColor).hideIf(() -> !dynamicPingColor.on());
        });
    }

    @Override protected void onEnable() { clear(); }
    @Override protected void onDisable() { clear(); }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (isEnabled()) clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        EXECUTOR.execute(pinger::pingPendingNetworks);
        ServerData data = Minecraft.getMinecraft().getCurrentServerData();
        if (data != null) {
            int ping = (int)data.pingToServer;
            spikeCheck(lastServerPing, ping);
            lastServerPing = ping;
        }
        long second = System.currentTimeMillis() / 1000L;
        if (second == lastSecond) return;
        lastSecond = second;
        if (latest == -1 && seconds == 0 || seconds >= (latest == 0 ? 1 : updateIntervalSec.intValue())) {
            seconds = 0;
            update();
        } else {
            ++seconds;
        }
    }

    private void update() {
        final ServerData data = Minecraft.getMinecraft().getCurrentServerData();
        if (data == null) return;
        EXECUTOR.execute(() -> {
            try {
                pinger.ping(data);
            } catch (Exception ignored) {
            }
        });
        int ping = (int)data.pingToServer;
        if (ping >= 0) record(ping);
    }

    private void record(int ping) {
        spikeCheck(latest, ping);
        latest = ping;
        samples.add(ping);
        int n = samples.size();
        if (n >= averageSamples.intValue()) {
            int sum = 0;
            for (Iterator<Integer> it = samples.iterator(); it.hasNext(); ) {
                sum += it.next();
                it.remove();
            }
            average = sum / n;
        }
    }

    private void spikeCheck(int before, int now) {
        long time = Minecraft.getSystemTime();
        if (spikeAt != -1L && time - spikeAt >= 5000L) {
            spike = -1;
            spikeAt = -1L;
        }
        if (before == -1 || now == -1) return;
        float change = now - before;
        int percent = before <= 0 ? 0 : (int)(change / before * 100.0f);
        if (Math.abs(percent) >= mediumSpikeThreshold.intValue()) {
            spike = percent;
            spikeAt = time;
            latest = now;
        }
    }

    private void clear() {
        seconds = 0;
        latest = -1;
        average = -1;
        spike = -1;
        spikeAt = -1L;
        samples.clear();
    }

    int ping() {
        if (pingMode.is(Mode.AVERAGE) && average != -1) return average;
        return latest;
    }

    ColorSetting numberColor(int ping) {
        if (ping < 0 || !dynamicPingColor.on()) return pingNumberColor;
        if (ping < 65) return lowPingNumberColor;
        if (ping < 120) return mediumPingNumberColor;
        if (ping < 250) return highPingNumberColor;
        return extremePingNumberColor;
    }

    static final class Part {
        final String text;
        final int color;
        Part(String text, int color) { this.text = text; this.color = color; }
    }

    List<Part> text(int ping, boolean withSpike, ColorSetting override) {
        List<Part> parts = new ArrayList<Part>();
        String value = (ping < 0 ? "..." : String.valueOf(ping)) + (pingShowMs.on() ? LunarLang.get("shared_info", "ms") : "");
        parts.add(new Part(value, (override != null ? override : numberColor(ping)).color(0)));
        if (withSpike && spike != -1 && pingSpikeDetection.on()) {
            ColorSetting color = Math.abs(spike) >= largeSpikeThreshold.intValue() ? largeSpikeColor : mediumSpikeColor;
            parts.add(new Part(" (" + (spike > 0 ? "+" : "") + spike + "%)", color.color(0)));
        }
        return parts;
    }

    NetworkPlayerInfo playerInfo(UUID id) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.sendQueue == null) return null;
        return mc.thePlayer.sendQueue.getPlayerInfo(id);
    }
}
