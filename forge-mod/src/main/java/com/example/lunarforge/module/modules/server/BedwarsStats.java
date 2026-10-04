package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.ChatEvent;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.RowHud;
import com.example.lunarforge.module.modules.mechanic.ModuleNickHider;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.StringUtils;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

final class BedwarsStats extends Module {
    private final ModuleHypixelBedwars bedwars;

    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting autoAlign = bool("autoAlign", true);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ChoiceSetting<RowHud.Alignment> alignment = choice("alignment", RowHud.Alignment.LEFT);
    private final BoolSetting showGame = bool("showGame", true);
    private final BoolSetting bedwarsSession = bool("bedwarsSession", true);

    private static final boolean TOTAL = false;
    private final BoolSetting winstreak = bool("winstreak", true);
    private final BoolSetting sessionGames = bool("sessionGames", true);
    private final BoolSetting sessionTime = bool("sessionTime", true);
    private final BoolSetting gameTime = bool("gameTime", true);
    private final BoolSetting avgGameTime = bool("avgGameTime", false);
    private final BoolSetting finals = bool("finals", true);
    private final BoolSetting finalsRatio = bool("finalsRatio", true);
    private final BoolSetting bedwarsBeds = bool("bedwarsBeds", true);
    private final BoolSetting bedsRatio = bool("bedsRatio", true);
    private final BoolSetting kills = bool("kills", true);
    private final BoolSetting killsRatio = bool("killsRatio", true);
    private final BoolSetting wins = bool("wins", true);
    private final BoolSetting winsRatio = bool("winsRatio", true);
    private final ColorSetting headingColor = color("headingColor", -171);
    private final ColorSetting statColor = color("statColor", -1);
    private final ColorSetting numberColor = color("numberColor", -11141291);
    private final ColorSetting dividerColor = color("dividerColor", -8355712);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);

    private final Tracker tracker = new Tracker();

    private long resetAt;

    private boolean findWinstreak = true;
    private int sidebarBedwars = -1;
    private static final Pattern WINSTREAK = Pattern.compile("^Current Winstreak: ([0-9,]+)$");

    BedwarsStats(ModuleHypixelBedwars bedwars) {
        super("HYPIXEL_BEDWARS_STATS_CHILD", true);
        this.bedwars = bedwars;
        hud(new Hud());
        ChatEvent.listen(e -> { if (active()) tracker.message(e.plain); });
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(textShadow);
            s.group(background, b -> b.group(border, t -> t.add(borderThickness)));
            s.add(autoAlign);
            s.add(alignment).hideIf(autoAlign::on);
        });
        page.section("renderOptions", s -> s.add(showGame, bedwarsSession, winstreak, sessionGames, sessionTime, gameTime, avgGameTime));
        page.section("extraRenderOptions", s -> s.add(finals, finalsRatio, bedwarsBeds, bedsRatio, kills, killsRatio, wins, winsRatio));
        page.section("colorOptions", s -> {
            s.add(headingColor, statColor, numberColor, dividerColor);
            s.add(backgroundColor).hideIf(() -> !background.on());
            s.add(borderColor).hideIf(() -> !border.on());
        });
    }

    private boolean active() { return isEnabled() && bedwars.isEnabled() && bedwars.onHypixel(); }

    boolean bedLost() { return tracker.bedLost; }

    void resetSession() {
        tracker.session = new Counts();
        tracker.resetSessionTime();
        tracker.games = 0;
        resetAt = Minecraft.getSystemTime();
    }

    void location(HypixelLocation.Location l) {
        if (!bedwars.onHypixel()) { tracker.leave(); return; }
        if (l == null) l = HypixelLocation.get();
        if (l != null && l.lobbyname == null && (l.server == null || !l.server.contains("lobby"))) {
            if (bedwars.inBedwars()) tracker.join(l.server);
            else tracker.leave();
        } else {
            tracker.join(null);
        }
    }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) {
        tracker.newWorld();
        sidebarBedwars = -1;
        findWinstreak = true;
    }

    private boolean sidebarBedwars() {
        if (sidebarBedwars >= 0) return sidebarBedwars == 1;
        Minecraft mc = Minecraft.getMinecraft();
        ScoreObjective o = mc.theWorld == null ? null : mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (o != null) {
            boolean bw = StringUtils.stripControlCodes(o.getDisplayName()).trim().equalsIgnoreCase("BED WARS");
            sidebarBedwars = bw ? 1 : 0;
            return bw;
        }
        findWinstreak = false;
        sidebarBedwars = 0;
        return false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tracker.tick();
        Minecraft mc = Minecraft.getMinecraft();
        if (!findWinstreak || mc.theWorld == null) return;
        HypixelLocation.Location l = HypixelLocation.get();
        boolean lobby = l != null && l.lobby() || bedwars.inLobby();
        if (!lobby || !(bedwars.inBedwars() || sidebarBedwars())) return;
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof EntityArmorStand) || !e.hasCustomName()) continue;
            Matcher m = WINSTREAK.matcher(StringUtils.stripControlCodes(e.getCustomNameTag()));
            if (!m.find()) continue;
            try {
                tracker.winstreak = Integer.parseInt(m.group(1).replace(",", ""));
                findWinstreak = false;
            } catch (NumberFormatException ignored) {}
            break;
        }
    }

    private static String time(long ms) {
        long h = ms / 1000L / 60L / 60L, m = ms / 1000L / 60L % 60L, s = ms / 1000L % 60L;
        return h == 0L ? String.format("%02d:%02d", m, s) : String.format("%d:%02d:%02d", h, m, s);
    }

    private final class Hud extends RowHud {
        Hud() { super(BedwarsStats.this, 0.0f, 0.0f, HudAnchor.TOP_RIGHT); }

        @Override protected boolean autoAlign() { return autoAlign.on(); }
        @Override protected Alignment alignment() { return alignment.get(); }
        @Override protected boolean background() { return background.on(); }
        @Override protected ColorSetting backgroundColor() { return backgroundColor; }
        @Override protected boolean border() { return border.on(); }
        @Override protected float borderThickness() { return borderThickness.value(); }
        @Override protected ColorSetting borderColor() { return borderColor; }

        @Override public boolean visible(boolean preview) {
            if (!bedwars.isEnabled() || !bedwars.inBedwars()) { size(0, 0); return false; }
            return super.visible(preview);
        }

        private Text t(String s, ColorSetting c) { return text(s, c, textShadow.on()); }

        private float ratio(int a, int b) { return (float)a / (float)Math.max(1, b); }

        private void stat(List<Piece> list, String name, String ratioName, int n, int d, boolean count, boolean ratio, int pad) {
            if (count && ratio) {
                list.add(row(pad, t(name + ": ", statColor), t("" + n, numberColor), t(" / ", dividerColor), t(ratioName + ": ", statColor),
                    t(String.format("%.2f", ratio(n, d)), numberColor)));
            } else if (count) {
                list.add(row(pad, t(name + ": ", statColor), t("" + n, numberColor)));
            } else if (ratio) {
                list.add(row(pad, t(ratioName + ": ", statColor), t(String.format("%.2f", ratio(n, d)), numberColor)));
            }
        }

        private void block(String title, int headPad, int pad, Counts c, List<Piece> list) {
            list.add(row(headPad, t("§l" + title, headingColor)));
            stat(list, "Finals", "FKDR", c.finals, c.finalDeaths, finals.on(), finalsRatio.on(), pad);
            stat(list, "Beds", "BBLR", c.beds, c.bedsLost, bedwarsBeds.on(), bedsRatio.on(), pad);
            stat(list, "Kills", "KDR", c.kills, c.deaths, kills.on(), killsRatio.on(), pad);
            stat(list, "Wins", "WLR", c.wins, c.losses, wins.on(), winsRatio.on(), pad);
        }

        @Override protected List<Piece> rows(boolean preview) {
            long now = Minecraft.getSystemTime();
            int n = 0, head = 0, pad = 0;
            switch (effective(autoAlign.on(), currentAnchor(), alignment.get())) {
                case LEFT: pad = 4; break;
                case RIGHT: head = 4; break;
                default: break;
            }
            List<Piece> list = new ArrayList<Piece>();
            boolean any = finals.on() || finalsRatio.on() || kills.on() || killsRatio.on() || wins.on() || winsRatio.on();
            if (showGame.on() && (finals.on() || kills.on())) {
                list.add(row(head, t("§lGame", headingColor)));
                if (finals.on()) list.add(row(pad, t("Finals: ", statColor), t("" + tracker.game.finals, numberColor)));
                if (bedwarsBeds.on()) list.add(row(pad, t("Beds: ", statColor), t("" + tracker.game.beds, numberColor)));
                if (kills.on()) list.add(row(pad, t("Kills: ", statColor), t("" + tracker.game.kills, numberColor)));
            }
            if (any) {
                if (bedwarsSession.on()) {
                    if (now - resetAt < 600L) {
                        list.add(row(head, t("§lSession", headingColor)));
                        list.add(row(pad, t(now - resetAt < 200L ? "Resetting." : now - resetAt < 400L ? "Resetting.." : "Resetting...", statColor)));
                        int blank = -1;
                        if (finals.on() || finalsRatio.on()) blank++;
                        if (bedwarsBeds.on() || bedsRatio.on()) blank++;
                        if (kills.on() || killsRatio.on()) blank++;
                        if (wins.on() || winsRatio.on()) blank++;
                        for (int i = 0; i < blank; i++) list.add(row(pad, t("", statColor)));
                    } else {
                        block("Session", head, pad, tracker.session, list);
                    }
                }
                if (TOTAL) block("Total", head, pad, tracker.total, list);
            }
            if (winstreak.on() || sessionGames.on() || sessionTime.on() || gameTime.on() || avgGameTime.on()) {
                list.add(row(0, t("", statColor)));
                if (winstreak.on()) list.add(row(n, t("Winstreak: ", statColor), t("" + tracker.winstreak, numberColor)));
                if (sessionGames.on()) list.add(row(n, t("Session Games: ", statColor), t("" + tracker.games, numberColor)));
                if (sessionTime.on()) list.add(row(n, t("Session Time: ", statColor), t(time(tracker.sessionTime(now)), numberColor)));
                if (gameTime.on()) list.add(row(n, t("Game Time: ", statColor), t(time(tracker.gameTime(now)), numberColor)));
                if (avgGameTime.on()) list.add(row(n, t("AVG Game Time: ", statColor), t(time(tracker.averageGameTime(now)), numberColor)));
            }
            return list;
        }
    }

    static final class Counts { int finals, kills, beds, wins, finalDeaths, deaths, bedsLost, losses; }

    private enum Event { FINAL_KILL, KILL, BED_BREAK, WIN, FINAL_DEATH, DEATH, BED_LOSS, LOSS, NONE }

    private static final ExecutorService THREAD = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "lunar-bedwars-stats-thread");
        t.setDaemon(true);
        return t;
    });

    private final class Tracker {
        Counts game = new Counts(), session = new Counts(), total = new Counts();
        int winstreak, games;
        private long sessionTime, gameTime, since = -1L;

        private boolean ended;
        private String playing, server;

        private boolean inGame;
        private final List<String> names = new ArrayList<String>(), current = new ArrayList<String>(), previous = new ArrayList<String>();
        private String playerName;
        boolean bedLost;
        private long lastUpdate;
        private final Map<Pattern, Event> patterns = new LinkedHashMap<Pattern, Event>();

        private void players() {
            previous.clear();
            previous.addAll(current);
            current.clear();
            for (NetworkPlayerInfo info : Minecraft.getMinecraft().thePlayer.sendQueue.getPlayerInfoMap()) {
                String name = info.getGameProfile().getName();
                if (!name.equalsIgnoreCase(playerName)) current.add(name);
            }
            previous.removeAll(current);
            names.addAll(previous);
            names.addAll(current);
        }

        void resetSessionTime() {
            sessionTime = 0L;
            if (since > 0L) {
                long now = Minecraft.getSystemTime();
                gameTime += now - since;
                since = now;
            }
        }

        long sessionTime(long now) { return sessionTime + (since > 0L ? now - since : 0L); }
        long gameTime(long now) { return gameTime + (since > 0L ? now - since : 0L); }
        long averageGameTime(long now) { return games == 0 ? gameTime(now) : sessionTime / games; }

        void newWorld() {
            if (ended) {
                ended = false;
                game = new Counts();
                gameTime = 0L;
            }
            bedLost = false;
        }

        void join(String s) {
            if (s == null) {
                stopClock();
                inGame = false;
                return;
            }
            inGame = true;
            server = s;
            if (playing != null && !playing.equals(server)) {
                playing = null;
                lost();
            }
        }

        void leave() {
            if (playing != null) {
                playing = null;
                lost();
            }
            stopClock();
            server = null;
            inGame = false;
        }

        private void stopClock() {
            if (since > 0L) {
                long now = Minecraft.getSystemTime();
                sessionTime += now - since;
                gameTime += now - since;
            }
            since = -1L;
        }

        private void lost() {
            game = new Counts();
            gameTime = 0L;
            session.losses++;
            total.losses++;
            winstreak = 0;
            games++;
        }

        private void wearing() {
            if (server == null || playing != null) return;
            for (ItemStack s : Minecraft.getMinecraft().thePlayer.inventory.armorInventory) {
                if (s != null) { playing = server; break; }
            }
        }

        void tick() {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            wearing();
            if (mc.theWorld != null && inGame && playing != null) {
                if (since <= 0L) since = Minecraft.getSystemTime();
            } else if (since > 0L) {
                sessionTime += Minecraft.getSystemTime() - since;
                since = -1L;
            }
        }

        void message(final String text) {
            if (!inGame) return;
            wearing();
            if (playing == null) return;
            THREAD.submit(() -> {
                long now = Minecraft.getSystemTime();
                if (now - lastUpdate > 1000L) {
                    lastUpdate = now;
                    players();
                    playerName = Minecraft.getMinecraft().thePlayer.getName();
                    patterns();
                }
                switch (match(text)) {
                    case FINAL_KILL: game.finals++; session.finals++; total.finals++; break;
                    case FINAL_DEATH: session.finalDeaths++; total.finalDeaths++; break;
                    case KILL: game.kills++; session.kills++; total.kills++; break;
                    case DEATH: game.deaths++; session.deaths++; total.deaths++; break;
                    case BED_BREAK: game.beds++; session.beds++; total.beds++; break;
                    case BED_LOSS: game.bedsLost++; session.bedsLost++; total.bedsLost++; break;
                    case WIN:
                        ended = true;
                        session.wins++; total.wins++;
                        winstreak++; games++;
                        playing = null; server = null;
                        break;
                    case LOSS:
                        ended = true;
                        session.losses++; total.losses++;
                        winstreak = 0; games++;
                        playing = null; server = null;
                        break;
                    default: break;
                }
            });
        }

        private void patterns() {
            String self = playerName;
            String nick = ModuleNickHider.hypixelNick();
            if (nick != null && nick.length() > 1) self = "(?:" + playerName + "|" + nick + ")";
            String team = "(?:Red|Blue|Green|Yellow|Aqua|White|Pink|Gray)";
            String selfEnd = self + "[^A-Za-z0-9_]";
            StringBuilder others = new StringBuilder("(?:");
            for (int i = 0; i < names.size(); i++) { if (i > 0) others.append('|'); others.append(names.get(i)); }
            String any = others.append(')').toString();
            String fin = "FINAL KILL!";
            patterns.clear();
            patterns.put(Pattern.compile("^ {2}.*" + team + " - .*[^A-Za-z0-9_]." + self + "[^A-Za-z0-9_].*"), Event.WIN);
            patterns.put(Pattern.compile("^ {2}.*" + team + " - .*"), Event.LOSS);
            patterns.put(Pattern.compile("^" + any + "[^A-Za-z0-9_].*" + selfEnd + ".*" + fin), Event.FINAL_KILL);
            patterns.put(Pattern.compile("^" + self + "[^A-Za-z0-9_].*" + fin), Event.FINAL_DEATH);
            patterns.put(Pattern.compile("^" + any + "[^A-Za-z0-9_].*" + selfEnd + ".*"), Event.KILL);
            patterns.put(Pattern.compile("^" + self + "[^A-Za-z0-9_].*"), Event.DEATH);
            patterns.put(Pattern.compile("^BED DESTRUCTION > Your Bed .*"), Event.BED_LOSS);
            patterns.put(Pattern.compile("^BED DESTRUCTION > .*" + selfEnd + ".*"), Event.BED_BREAK);
        }

        private Event match(String text) {
            if (text.contains(":")) return Event.NONE;
            String line = text.replace("\n", "");
            for (Map.Entry<Pattern, Event> e : patterns.entrySet()) {
                if (!e.getKey().matcher(line).matches()) continue;
                if (e.getValue() == Event.BED_LOSS) bedLost = true;
                return e.getValue();
            }
            return Event.NONE;
        }
    }
}
