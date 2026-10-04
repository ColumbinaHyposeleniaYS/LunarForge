package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.modules.hud.ModuleTitles;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ButtonSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleHypixelBedwars extends Module {
    private static ModuleHypixelBedwars instance;

    public enum BedColor implements ChoiceSetting.Option {
        BLUE("blue"), CYAN("cyan"), GRAY("gray"), GREEN("green"), PINK("pink"), RED("red"), WHITE("white"), YELLOW("yellow");
        final String id;
        BedColor(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ButtonSetting resetSession = add(new ButtonSetting("resetSession", this::resetStats));
    final BoolSetting coloredBeds = bool("coloredBeds", true);
    final BoolSetting enablePracticeColor = bool("enablePracticeColor", false);
    final ChoiceSetting<BedColor> practiceBedColor = choice("practiceBedColor", BedColor.WHITE);
    private final BoolSetting bwHardcoreHearts = bool("bwHardcoreHearts", true);
    private final BoolSetting bwHideFoodBar = bool("bwHideFoodBar", false);
    private final BoolSetting bwHideArmorBar = bool("bwHideArmorBar", false);
    private final BoolSetting customTrapAlert = bool("customTrapAlert", false);
    private final BoolSetting muteAlertSound = bool("muteAlertSound", false);
    private final ColorSetting alertPrimaryColor = color("alertPrimaryColor", -1);
    private final ColorSetting alertSubColor = color("alertSubColor", -1);

    final BedwarsStats stats;
    final BedwarsResources resources;
    final BedwarsUpgrades upgrades;
    final BedwarsTeam team;
    final BedwarsTimers timers;
    final BedwarsBeds.Layout beds = new BedwarsBeds.Layout(this);

    JsonObject bedLocations;
    private boolean loaded;

    private boolean inBedwars, inLobby, onHypixel;

    private boolean alarm;
    private long alarmSince = -1L;

    private boolean builtColored, builtPractice;
    private BedColor builtColor;

    public ModuleHypixelBedwars() {
        super("HYPIXEL_BEDWARS", true);
        instance = this;
        rowShownWhen(Server::hypixel);
        stats = child(new BedwarsStats(this), null);
        resources = child(new BedwarsResources(this), null);
        upgrades = child(new BedwarsUpgrades(this), null);
        team = child(new BedwarsTeam(this), null);
        timers = child(new BedwarsTimers(this), null);
        HypixelLocation.listen((before, now) -> onLocation(now));
        ModuleTitles.COLORS.add(this::alertColor);
        coloredBeds.onChange(this::updateBeds);
        enablePracticeColor.onChange(this::updateBeds);
        practiceBedColor.onChange(this::updateBeds);
        updateBeds();
    }

    @Override protected void layout(Page page) {
        page.add(resetSession);
        page.section("bedwarsOptions", s -> {
            s.group(coloredBeds, c -> c.group(enablePracticeColor, p -> p.add(practiceBedColor)));
            s.add(bwHardcoreHearts, bwHideFoodBar, bwHideArmorBar);
            s.group(customTrapAlert, c -> c.add(muteAlertSound, alertPrimaryColor, alertSubColor));
        });
    }

    static ModuleHypixelBedwars get() { return instance; }

    private void resetStats() { stats.resetSession(); }

    boolean inBedwars() { return inBedwars; }
    boolean inLobby() { return inLobby; }
    boolean onHypixel() { return onHypixel; }

    boolean inGame() { return inBedwars && !inLobby; }

    boolean practice() {
        if (!enablePracticeColor.on()) return false;
        HypixelLocation.Location l = HypixelLocation.get();
        return l != null && "BEDWARS_PRACTICE".equals(l.mode);
    }

    @Override protected void onEnable() { updateBeds(); }
    @Override protected void onDisable() { updateBeds(); }

    private void loadData() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = ModuleHypixelBedwars.class.getResourceAsStream("/assets/lunarforge/hypixel/bedwars.json")) {
            if (in != null) bedLocations = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("bed_locations");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void updateBeds() {
        loadData();
        boolean colored = isEnabled() && coloredBeds.on();
        boolean practice = colored && enablePracticeColor.on();
        BedColor color = practice ? practiceBedColor.get() : null;
        if (colored != builtColored || practice != builtPractice || color != builtColor) {
            reloadChunks();
            beds.update();
        }
        builtColored = colored;
        builtPractice = practice;
        builtColor = color;
    }

    static void reloadChunks() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        mc.addScheduledTask(() -> { if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers(); });
    }

    private void onLocation(HypixelLocation.Location l) {
        boolean hypixel = Server.hypixel();
        if (hypixel != onHypixel) {
            onHypixel = hypixel;
            if (!hypixel) { inBedwars = false; inLobby = false; }
        }
        if (l != null && !l.empty()) {
            inBedwars = "BEDWARS".equalsIgnoreCase(l.gametype);
            inLobby = l.server != null && l.server.contains("lobby");
        } else {
            inBedwars = inLobby = false;
        }
        if (!isEnabled()) return;
        if (l == null || l.empty()) {
            beds.update();
            stats.location(null);
            resources.location(null);
        } else {
            boolean wasActive = beds.active();
            int[] wasOrder = beds.order();
            beds.update();
            beds.read();
            if (beds.used() && (wasActive != beds.active() || !java.util.Arrays.equals(wasOrder, beds.order()))) reloadChunks();
            stats.location(l);
            resources.location(l);
        }
    }

    public static boolean hardcore(boolean vanilla) {
        ModuleHypixelBedwars m = instance;
        return vanilla || m != null && m.isEnabled() && m.bwHardcoreHearts.on() && m.stats.bedLost();
    }

    @SubscribeEvent
    public void onBars(RenderGameOverlayEvent.Pre event) {
        if (!isEnabled() || !inGame()) return;
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD && bwHideFoodBar.on()) event.setCanceled(true);
        if (event.type == RenderGameOverlayEvent.ElementType.ARMOR && bwHideArmorBar.on()) event.setCanceled(true);
    }

    void trapTriggered() {
        alarmSince = Minecraft.getSystemTime();
        alarm = true;
    }

    private Integer alertColor(String title, String subtitle) {
        if (!isEnabled() || !customTrapAlert.on() || !inGame()) return null;
        if (title != null) {
            String s = StringUtils.stripControlCodes(title);
            if (s.startsWith("TRAP TRIGGERED!") || s.startsWith("ALARM!!!")) return alertPrimaryColor.color(0.0f);
        }
        if (subtitle != null) {
            String s = StringUtils.stripControlCodes(subtitle);
            if (s.startsWith("Your") && s.endsWith("Trap has been set off!") || s.startsWith("Reveal trap set off by")) return alertSubColor.color(0.0f);
        }
        return null;
    }

    public static boolean drawsTitle() {
        ModuleHypixelBedwars m = instance;
        if (m == null || !m.isEnabled() || !m.customTrapAlert.on() || !m.inGame()) return false;
        if ((m.alertPrimaryColor.color(0.0f) & 0xFFFFFF) == 0xFFFFFF && (m.alertSubColor.color(0.0f) & 0xFFFFFF) == 0xFFFFFF) return false;
        Module titles = com.example.lunarforge.module.ModuleManager.get("titles");
        if (titles != null && titles.isEnabled()) return false;
        String[] t = ModuleTitles.gameTitle();
        return t != null && (m.alertColor(t[0], null) != null || m.alertColor(null, t[1]) != null);
    }

    @SubscribeEvent
    public void onTitle(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !drawsTitle()) return;
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        ModuleTitles.drawGameTitle(res.getScaledWidth(), res.getScaledHeight());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !alarm || Minecraft.getSystemTime() - alarmSince < 6500L) return;
        String[] t = ModuleTitles.gameTitle();
        if (t == null) {
            alarm = false;
        } else {
            String s = StringUtils.stripControlCodes(t[0] == null ? "" : t[0]);
            if (s.trim().isEmpty() || !s.equals("ALARM!!!") && !s.equals("TRAP TRIGGERED!")) alarm = false;
        }
    }

    @SubscribeEvent
    public void onSound(PlaySoundEvent event) {
        if (!alarm || !customTrapAlert.on() || !muteAlertSound.on() || event.sound == null) return;
        String path = event.sound.getSoundLocation().getResourcePath();
        if (path.equals("note.pling") || path.equals("mob.endermen.portal")) event.result = null;
    }
}
