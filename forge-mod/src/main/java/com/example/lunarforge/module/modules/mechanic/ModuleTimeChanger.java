package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.Date;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleTimeChanger extends Module {
    public enum Sky implements ChoiceSetting.Option {
        DEFAULT("default"), NETHER("nether"), END("end");
        private final String id;
        Sky(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final NumberSetting timeChangerTime = integer("timeChangerTime", 12000, 0, 24000);

    private final NumberSetting horizonYLevel = integer("horizonYLevel", 63, 0, 63);

    private final BoolSetting useRealTime = bool("useRealTime", false);

    private final KeySetting increaseTime = add(new KeySetting("increaseTime", "RBRACKET", true));

    private final KeySetting decreaseTime = add(new KeySetting("decreaseTime", "LBRACKET", true));

    private final BoolSetting timePassage = bool("timePassage", false);

    private final NumberSetting speed = integer("speed", 1, 0, 20);

    private final ChoiceSetting<Sky> overworldSky = choice("overworldSky", Sky.DEFAULT);

    private final Date date = new Date();

    private int realTime;

    private long realTimeAt;

    private static ModuleTimeChanger instance;

    public ModuleTimeChanger() {
        super("TIME_CHANGER", false);
        instance = this;

        timeChangerTime.onChange(this::onTimeChanged);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(timeChangerTime).hideIf(useRealTime::on);
            s.add(overworldSky, horizonYLevel, useRealTime, increaseTime, decreaseTime);
            s.group(timePassage, g -> g.add(speed)).hideIf(useRealTime::on);
        });
    }

    @Override protected void onEnable() {
        applyWorldTime();
    }

    @Override protected void onDisable() {
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!isEnabled() || !event.world.isRemote) return;
        event.world.setWorldTime(currentTime());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        if (mc().theWorld == null) return;
        if (keyDown(increaseTime)) setTime(clampTime(time() + 100));
        if (keyDown(decreaseTime)) setTime(clampTime(time() - 100));
        if (timePassage.on() && !useRealTime.on()) {
            if (speed.intValue() != 0) setTime(time() + speed.intValue());

            if (time() >= (int)timeChangerTime.max) setTime((int)timeChangerTime.min);
        }
        applyWorldTime();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        applyWorldTime();
    }

    private static boolean keyDown(KeySetting key) {
        return key.isDown() && mc().currentScreen == null;
    }

    private void applyWorldTime() {
        World world = mc().theWorld;
        if (world == null) return;
        world.setWorldTime(currentTime());
    }

    private void onTimeChanged() {
        if (isEnabled()) applyWorldTime();
    }

    private int currentTime() {
        return useRealTime.on() ? realTime() : shiftedTime();
    }

    private int shiftedTime() {
        int n = time() + 18000;
        if (n >= (int)timeChangerTime.max) n -= 24000;
        return n;
    }

    private int realTime() {
        if (System.currentTimeMillis() - realTimeAt < 3600L) return realTime;
        date.setTime(System.currentTimeMillis());
        int n = date.getHours() - 6;
        if (n <= 0) n += 24;
        n *= 1000;
        n = (int)((double)n + (double)(date.getSeconds() + date.getMinutes() * 60) / 3.6);
        realTime = n;
        realTimeAt = System.currentTimeMillis();
        return realTime;
    }

    private int time() {
        return Math.round(timeChangerTime.get());
    }

    private void setTime(int value) {
        timeChangerTime.set((float)value);
    }

    private int clampTime(int value) {
        return Math.max((int)timeChangerTime.min, Math.min((int)timeChangerTime.max, value));
    }

    private static Sky sky() {
        ModuleTimeChanger m = instance;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (m == null || !m.isEnabled() || mc.theWorld == null || mc.theWorld.provider.getDimensionId() != 0) return Sky.DEFAULT;
        return m.overworldSky.get();
    }

    public static int skyDimension(int vanilla) { return sky() == Sky.END ? 1 : vanilla; }

    public static boolean skySurface(boolean vanilla) { return sky() == Sky.NETHER ? false : vanilla; }

    @SubscribeEvent
    public void onFog(net.minecraftforge.client.event.EntityViewRenderEvent.FogColors event) {
        switch (sky()) {
            case NETHER: event.red = 0.2f; event.green = 0.03137255f; event.blue = 0.03137255f; break;
            case END: event.red = 0.627451f; event.green = 0.5019608f; event.blue = 0.627451f; break;
            default: break;
        }
    }
}
