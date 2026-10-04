package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleWeatherChanger extends Module {
    public enum Type implements ChoiceSetting.Option {
        NATURAL("natural"), CLEAR("clear"), RAIN("rain"), SNOW("snow");
        private final String id;
        Type(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ChoiceSetting<Type> weatherMode = choice("weatherMode", Type.CLEAR);

    private final NumberSetting rainStrength = decimal("rainStrength", 1.0f, 0.0f, 1.0f);

    private final ColorSetting rainColor = color("rainColor", 0xFFFFFFFF);

    private final BoolSetting thunderStorm = bool("thunderStorm", false);

    private final BoolSetting playThunderSound = bool("playThunderSound", true);

    private final NumberSetting lightningFreq = decimal("lightningFreq", 1.0f, 1.0f, 20.0f);

    private final NumberSetting lightningRadiusXZ = decimal("lightningRadiusXZ", 128.0f, 8.0f, 512.0f);

    private final NumberSetting lightningOffsetY = decimal("lightningOffsetY", 0.0f, -64.0f, 64.0f);

    private static final Field LIGHTNING_STATE =
        Fields.find(EntityLightningBolt.class, "lightningState", "field_70262_b");

    private static ModuleWeatherChanger instance;

    public ModuleWeatherChanger() {
        super("WEATHER_CHANGER", false);
        instance = this;
    }

    private static ModuleWeatherChanger active() { return instance != null && instance.isEnabled() ? instance : null; }

    public static float worldRain(float vanilla, World world) {
        ModuleWeatherChanger m = active();
        return m != null && world.isRemote && m.weatherMode.get() == Type.RAIN ? m.rainStrength.get() : vanilla;
    }

    public static boolean clear() {
        ModuleWeatherChanger m = active();
        return m != null && m.weatherMode.get() == Type.CLEAR;
    }

    public static float renderStrength(float natural) {
        ModuleWeatherChanger m = active();
        return m != null && m.showRain(natural) ? m.rainStrength() : natural;
    }

    public static boolean canRain(boolean vanilla) {
        ModuleWeatherChanger m = active();
        if (m == null) return vanilla;
        switch (m.weatherMode.get()) {
            case RAIN: case SNOW: return true;
            case CLEAR: return false;
            default: return vanilla;
        }
    }

    public static float temperature(float vanilla) {
        ModuleWeatherChanger m = active();
        if (m == null) return vanilla;
        switch (m.weatherMode.get()) {
            case RAIN: return 0.15f;
            case SNOW: return 0.0f;
            default: return vanilla;
        }
    }

    public static WorldRenderer color(WorldRenderer wr, float r, float g, float b, float a) {
        ModuleWeatherChanger m = active();
        if (m == null) return wr.color(r, g, b, a);
        int c = m.rainColor.color(0.0f);
        return wr.color((c >> 16 & 255) / 255.0f, (c >> 8 & 255) / 255.0f, (c & 255) / 255.0f, a);
    }

    @Override protected void layout(Page page) {
        page.add(weatherMode);
        page.add(rainStrength, rainColor).hideIf(() -> weatherMode.get() == Type.CLEAR);
        page.group(thunderStorm, g -> g.add(playThunderSound, lightningFreq, lightningRadiusXZ, lightningOffsetY));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        Minecraft mc = mc();
        World world = mc.theWorld;
        if (world == null || !world.isRemote) return;
        spawnLightning(mc, world);
    }

    public float rainStrength() {
        return weatherMode.get() == Type.CLEAR ? 0.0f : rainStrength.get();
    }

    public boolean showRain(float natural) {
        if (!isEnabled()) return false;
        switch (weatherMode.get()) {
            case RAIN:
            case SNOW:
                return true;
            case NATURAL:
                return natural > 0.0f;
            default:
                return false;
        }
    }

    private void spawnLightning(Minecraft mc, World world) {
        if (!thunderStorm.on()) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextFloat() >= 0.002f * lightningFreq.get()) return;
        float radius = lightningRadiusXZ.get();
        float offsetY = lightningOffsetY.get();
        double x = player.posX + (random.nextFloat() - 0.5f) * 2.0f * radius;
        double y = player.posY + (random.nextFloat() - 0.5f) * 2.0f * offsetY;
        double z = player.posZ + (random.nextFloat() - 0.5f) * 2.0f * radius;

        EntityLightningBolt bolt = silentBolt(world, x, y, z);
        world.addWeatherEffect(bolt);
        if (playThunderSound.on()) {
            float pitch = 0.8f + random.nextFloat() * 0.2f;
            float impactPitch = 0.5f + random.nextFloat() * 0.2f;
            world.playSound(x, y, z, "ambient.weather.thunder", 10000.0f, pitch, false);
            world.playSound(x, y, z, "random.explode", 2.0f, impactPitch, false);
        }
    }

    private static EntityLightningBolt silentBolt(World world, double x, double y, double z) {
        EntityLightningBolt bolt = new EntityLightningBolt(world, x, y, z);
        Fields.setInt(LIGHTNING_STATE, bolt, 1);
        return bolt;
    }
}
