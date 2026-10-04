package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.TextSetting;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.commons.lang3.text.WordUtils;
import org.lwjgl.opengl.GL11;

public final class ModuleTntCountdown extends Module {
    public enum Type implements ChoiceSetting.Option {
        NEVER("never"), HYPIXEL_ONLY("hypixelOnly"), ALWAYS("always");
        private final String id;
        Type(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private static final DecimalFormat FORMAT = new DecimalFormat("0.00");

    private static final float TEXT_SCALE = 0.016666668f * 1.6f;

    private static final Pattern BRAND_PATTERN = Pattern.compile("(.+) (?:<- .+)?");

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");

    private final Map<String, Tracked> tracked = new HashMap<String, Tracked>();

    private final Map<String, Hologram> holograms = new HashMap<String, Hologram>();

    private final TextSetting textPrefix = text("textPrefix");

    private final BoolSetting textShadow = bool("textShadow", false);

    private final BoolSetting staticCountdownColor = bool("staticCountdownColor", false);

    private final BoolSetting background = bool("background", true);

    private final ColorSetting color = color("color", 0xFF00FF00);

    private final ColorSetting prefixColor = color("prefixColor", 0xFFFFFFFF);

    private final NumberSetting adjustFuseTime = integer("adjustFuseTime", 0, -80, 80);

    private final ChoiceSetting<Type> adjustForBedwars = choice("adjustForBedwars", Type.HYPIXEL_ONLY);

    private int maxFuse = 80;

    private int fuseAdjust;

    private boolean sulfurRescan;

    public ModuleTntCountdown() {
        super("TNT_COUNTDOWN", false);
        adjustFuseTime.onChange(this::applyFuseAdjust);
        applyFuseAdjust();
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(adjustFuseTime, adjustForBedwars));
        page.section("extraRenderOptions", s -> s.add(background, textPrefix, textShadow));
        page.section("colorOptions", s -> {
            s.group(staticCountdownColor, g -> g.add(color));
            s.add(prefixColor);
        });
    }

    private void applyFuseAdjust() {
        fuseAdjust = adjustFuseTime.intValue();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!isEnabled() || !event.world.isRemote) return;
        if (event.entity == mc().thePlayer) cleanup();
        if (!(event.entity instanceof EntityTNTPrimed)) return;

        EntityTNTPrimed entity = (EntityTNTPrimed)event.entity;

        entity.fuse = maxFuse;
        String id = UUID.randomUUID().toString();
        Tracked entry = new Tracked(entity, maxFuse);
        tracked.put(id, entry);
        holograms.put(id, hologram(entity, maxFuse));
    }

    @SubscribeEvent
    public void onServerConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (!isEnabled()) return;
        cleanup();
    }

    @SubscribeEvent
    public void onServerDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (!isEnabled()) return;
        cleanup();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        List<String> gone = new ArrayList<String>();
        for (Map.Entry<String, Tracked> entry : tracked.entrySet()) {
            Tracked tnt = entry.getValue();
            int fuse = tnt.entity.fuse;
            int maximum = Math.max(1, tnt.maxFuse + fuseAdjust);
            if (fuse <= 0 || tnt.entity.isDead || fuse > maximum) {
                gone.add(entry.getKey());
                continue;
            }
            holograms.put(entry.getKey(), hologram(tnt.entity, tnt.maxFuse));
        }
        for (String id : gone) {
            tracked.remove(id);
            holograms.remove(id);
        }
    }

    @Override protected void onDisable() {
        cleanup();
        sulfurRescan = false;
    }

    @Override protected void onEnable() {
        sulfurRescan = true;
    }

    private void cleanup() {
        maxFuse = 80;
        if (tracked == null) return;
        tracked.clear();
        holograms.clear();
        sulfurRescan = true;
    }

    public void setMaximumFuse(int n) {
        maxFuse = Math.max(1, n);
    }

    public void setMaximumFuse(UUID id, int n) {
        for (Tracked tnt : tracked.values()) {
            if (!tnt.entity.getUniqueID().equals(id)) continue;
            tnt.maxFuse = n;
            tnt.entity.fuse = n;
            return;
        }
    }

    public void setMaximumFuse(int entityId, int n) {
        for (Tracked tnt : tracked.values()) {
            if (tnt.entity.getEntityId() != entityId) continue;
            tnt.maxFuse = n;
            tnt.entity.fuse = n;
            return;
        }
    }

    private Hologram hologram(EntityTNTPrimed entity, int maximumFuse) {
        int fuse = entity.fuse;
        if (adjustForBedwars.get() != Type.NEVER) {
            String server = serverName();
            if (server != null && server.contains("Bed Wars")
                && (adjustForBedwars.get() == Type.ALWAYS || hypixelBrand(mc().thePlayer))) {
                fuse -= 30;
            }
        }
        return hologram(entity, maximumFuse, Math.max(1.0E-4f, fuse), 0.0f);
    }

    private Hologram hologram(EntityTNTPrimed entity, int maximumFuse, float fuse, float position) {
        float seconds = (fuse - position) / 20.0f;
        int limit = Math.max(1, maximumFuse + fuseAdjust);
        float fraction = Math.min(fuse / (float)limit, 1.0f);
        int red = (int)((1.0f - fraction) * 255.0f) & 0xFF;
        int green = (int)(fraction * 255.0f) & 0xFF;

        int countdownColor = staticCountdownColor.on()
            ? color.color(fuse) & 0xFFFFFF
            : 0xFF000000 | red << 16 | green << 8;
        return new Hologram(entity.posX, entity.posY + 0.5, entity.posZ,
            textShadow.on(), background.on(), textPrefix.get(), prefixColor.color(fuse) & 0xFFFFFF,
            FORMAT.format(seconds), countdownColor);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft client = mc();
        if (client.thePlayer == null || client.theWorld == null || holograms.isEmpty()) return;
        RenderManager renderManager = client.getRenderManager();
        for (int pass = 0; pass < 2; pass++) {
            boolean text = pass == 1;
            for (Hologram hologram : holograms.values()) {
                if (!text && !hologram.background) continue;
                draw(hologram, text, renderManager);
            }
        }
    }

    private void draw(Hologram hologram, boolean text, RenderManager renderManager) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(
            hologram.x - renderManager.viewerPosX,
            hologram.y + 1.0 - renderManager.viewerPosY,
            hologram.z - renderManager.viewerPosZ);
        GL11.glNormal3f(0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(-renderManager.playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(renderManager.playerViewX, 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        float width = Draw.width(hologram.prefix) + Draw.width(hologram.countdown);
        if (text) {
            float x = Draw.text(hologram.prefix, -width / 2.0f, 0.0f, hologram.prefixColor, hologram.textShadow);
            Draw.text(hologram.countdown, x, 0.0f, hologram.countdownColor, hologram.textShadow);
        } else {
            int half = (int)(width / 2.0f);
            Draw.rect(-half - 1.0f, -1.0f, half * 2.0f + 2.0f, 9.0f, 0x40000000);
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static String serverName() {
        Minecraft client = mc();
        EntityPlayerSP player = client.thePlayer;
        if (player == null || client.getCurrentServerData() == null || client.theWorld == null) return null;
        if (!hypixelBrand(player)) return null;
        ScoreObjective objective = client.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) return null;
        String name = STRIP_COLOR_PATTERN.matcher(objective.getDisplayName()).replaceAll("");
        if (name.equalsIgnoreCase("skyblock co-op")) name = "SKYBLOCK";
        return WordUtils.capitalize(name.toLowerCase(), null);
    }

    private static boolean hypixelBrand(EntityPlayerSP player) {
        if (player == null) return false;
        String brand = player.getClientBrand();
        if (brand == null) return false;
        Matcher matcher = BRAND_PATTERN.matcher(brand);
        return matcher.find() && matcher.group(1).startsWith("Hypixel BungeeCord");
    }

    private static final class Tracked {
        final EntityTNTPrimed entity;
        int maxFuse;

        Tracked(EntityTNTPrimed entity, int maxFuse) {
            this.entity = entity;
            this.maxFuse = maxFuse;
        }
    }

    private static final class Hologram {
        final double x, y, z;
        final boolean textShadow, background;
        final String prefix;
        final int prefixColor;
        final String countdown;
        final int countdownColor;

        Hologram(double x, double y, double z, boolean textShadow, boolean background, String prefix, int prefixColor,
                 String countdown, int countdownColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.textShadow = textShadow;
            this.background = background;
            this.prefix = prefix;
            this.prefixColor = prefixColor;
            this.countdown = countdown;
            this.countdownColor = countdownColor;
        }
    }
}
