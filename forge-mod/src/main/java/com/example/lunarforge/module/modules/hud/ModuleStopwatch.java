package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.ButtonSetting;
import com.example.lunarforge.util.DesktopNotify;
import java.awt.TrayIcon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleStopwatch extends Module {
    static final ResourceLocation PING = new ResourceLocation("random.orb");

    final Map<String, StopwatchTimer> timers = new LinkedHashMap<String, StopwatchTimer>();
    final Map<String, StopwatchChild> stopwatches = new LinkedHashMap<String, StopwatchChild>();

    private final Set<String> titleQueue = new LinkedHashSet<String>();
    private String title;
    private int titleTicks = -1;

    private final ButtonSetting addTimer = add(new ButtonSetting("addTimer", () -> {
        StopwatchTimer t = StopwatchTimer.create(this, "STOPWATCH_TIMER_" + Minecraft.getSystemTime());
        t.minutes.set(1.0f);
        addTimer(t, true);
    }));
    private final ButtonSetting addStopwatch = add(new ButtonSetting("addStopwatch", () ->
        addStopwatch(StopwatchChild.create(this, "STOPWATCH_" + Minecraft.getSystemTime()), true)));

    public ModuleStopwatch() {
        super("STOPWATCH", false);
        load(false);
    }

    @Override protected void layout(Page page) {
        page.add(addTimer, addStopwatch);
    }

    @Override protected void onDisable() {
        for (StopwatchTimer t : timers.values()) t.stop();
    }

    private static List<String> ids(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void save() {
        ModuleManager.put(key(), "timers", String.join(",", timers.keySet()));
        ModuleManager.put(key(), "stopwatches", String.join(",", stopwatches.keySet()));
    }

    private void load(boolean live) {
        if (!timers.isEmpty() || !stopwatches.isEmpty()) clear();
        for (String id : ids(ModuleManager.store(key(), "timers", ""))) addTimer(StopwatchTimer.create(this, id), false, live);
        for (String id : ids(ModuleManager.store(key(), "stopwatches", ""))) addStopwatch(StopwatchChild.create(this, id), false, live);
    }

    private void clear() {
        for (StopwatchChild s : new ArrayList<StopwatchChild>(stopwatches.values())) removeChild(s);
        stopwatches.clear();
        for (StopwatchTimer t : new ArrayList<StopwatchTimer>(timers.values())) removeChild(t);
        timers.clear();
        titleQueue.clear();
        title = null;
        titleTicks = -1;
    }

    void addTimer(StopwatchTimer t, boolean save) { addTimer(t, save, true); }

    private void addTimer(StopwatchTimer t, boolean save, boolean live) {
        if (timers.containsKey(t.id)) return;
        timers.put(t.id, t);
        if (live) addChild(t, "timers"); else child(t, "timers");
        if (save) save();
    }

    void addStopwatch(StopwatchChild s, boolean save) { addStopwatch(s, save, true); }

    private void addStopwatch(StopwatchChild s, boolean save, boolean live) {
        if (stopwatches.containsKey(s.id)) return;
        stopwatches.put(s.id, s);
        if (live) addChild(s, "stopwatches"); else child(s, "stopwatches");
        if (save) save();
    }

    void remove(StopwatchTimer t) {
        timers.remove(t.id);
        removeChild(t);
        save();
    }

    void remove(StopwatchChild s) {
        stopwatches.remove(s.id);
        removeChild(s);
        save();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ids(ModuleManager.store(key(), "timers", "")).equals(new ArrayList<String>(timers.keySet()))
                || !ids(ModuleManager.store(key(), "stopwatches", "")).equals(new ArrayList<String>(stopwatches.keySet()))) {
            load(true);
        }
        Minecraft mc = Minecraft.getMinecraft();
        for (StopwatchTimer t : new ArrayList<StopwatchTimer>(timers.values())) t.keys(mc.currentScreen == null);
        for (StopwatchChild s : new ArrayList<StopwatchChild>(stopwatches.values())) s.keys(mc.currentScreen == null && isEnabled());
        if (!isEnabled()) return;
        finished();
        if (titleTicks >= 0) titleTicks--;
        if (titleQueue.isEmpty()) return;
        if (mc.thePlayer == null) {
            titleQueue.clear();
            title = null;
            titleTicks = -1;
            return;
        }
        if (titleTicks < 0) {
            String next = titleQueue.iterator().next();
            titleQueue.remove(next);
            titleTicks = 10 + 40 + 10;
            title = next.replace('&', '§');
        }
    }

    private void finished() {
        Minecraft mc = Minecraft.getMinecraft();
        for (StopwatchTimer t : timers.values()) {
            if (!t.finished()) continue;
            final String description = lang("notificationDescription", t.timerName.get());
            if (t.playSound.on()) mc.getSoundHandler().playSound(PositionedSoundRecord.create(PING, 1.0f));
            if (DesktopNotify.supported() && t.desktopNotification.on()) {
                final String head = lang("notificationTitle");
                CompletableFuture.runAsync(() -> DesktopNotify.send(head, description, TrayIcon.MessageType.INFO));
            }
            if (DesktopNotify.supported() && t.ingameNotification.on()) {
                LunarNotifications.push(LunarNotifications.Type.INFO, lang("notificationTitle"), description);
            }
            String text;
            if (t.showTitleAction.on() && (text = t.titleText.get()) != null && !(text = text.trim()).isEmpty()) titleQueue.add(text);
            t.stop();
            if (t.loopOnEnd.on()) t.start();
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !isEnabled() || title == null || titleTicks < 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        float age = titleTicks - event.partialTicks;
        int alpha = 255;
        if (titleTicks > 10 + 40) alpha = (int)((10 + 40 + 10 - age) * 255.0f / 10);
        if (titleTicks <= 10) alpha = (int)(age * 255.0f / 10);
        alpha = Math.max(0, Math.min(255, alpha));
        if (alpha <= 8) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(res.getScaledWidth() / 2.0f, res.getScaledHeight() / 2.0f, 0.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.scale(4.0f, 4.0f, 4.0f);
        mc.fontRendererObj.drawString(title, -mc.fontRendererObj.getStringWidth(title) / 2.0f, -8.75f, 0xFFFFFF | alpha << 24, true);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
