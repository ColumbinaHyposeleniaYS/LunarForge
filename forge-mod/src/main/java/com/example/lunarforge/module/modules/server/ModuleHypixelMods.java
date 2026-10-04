package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.ChatEvent;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.TextSetting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ModuleHypixelMods extends Module {
    private static ModuleHypixelMods instance;
    private static final Pattern PRIVATE = Pattern.compile("^From .*: .*");
    private static final Pattern WELL = Pattern.compile("^You found .*in the well!");
    private static final Pattern TEAM = Pattern.compile(".*\\[TEAM] .*: .*");
    private static final int GG_MAX = 256 - "/achat ".length();
    private static final ImmutableList<String> WINNERS = ImmutableList.of("Winner #1 (", "Top Survivors", "Winners - ", "Winners: ", "Winner: ",
        "Winning Team: ", " won the game!", "Top Seeker: ", "Last team standing!", "1st Place: ", "1st Killer - ", "1st Place - ", "Winner: ",
        " - Damage Dealt - ", "Damage Dealt: ", "Winning Team -", "1st - ", " Duel - ", "Most Wool Placed  -");
    private static final Splitter WORDS = Splitter.on(' ').trimResults().omitEmptyStrings();
    private static final String MOTD = "--------------  Guild: Message Of The Day  --------------";
    private static final String RULE = "-------------------------------------------";
    private static final String LEVELHEAD = "https://thirdpartycache.lunarclientprod.com/hypixel/levelhead";

    public enum Source implements ChoiceSetting.Option {
        NETWORK("levelHeadSourceNetwork", 25, 50), BEDWARS("levelHeadSourceBedWars", 25, 50), SKYWARS("levelHeadSourceSkyWars", 12, 30);
        final String id;
        final int min, max;
        Source(String id, int min, int max) { this.id = id; this.min = min; this.max = max; }
        @Override public String langId() { return id; }
        String prefix() { return LunarLang.get("settings", id + "Prefix"); }

        int randomLevel(UUID id) {
            long l = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
            l = l < 0 ? -l : l;
            return (int)(l % (max - min)) + min;
        }
    }

    private final BoolSetting autoFriend = bool("autoFriend", false);
    private final BoolSetting autoGG = bool("autoGG", false);
    private final BoolSetting antiGG = bool("antiGG", false);
    private final BoolSetting autoTip = bool("autoTip", false);
    private final BoolSetting autoWho = bool("autoWho", false);
    private final BoolSetting levelHead = bool("levelHead", false);
    private final ChoiceSetting<Source> levelHeadSource = choice("levelHeadSource", Source.NETWORK);
    private final BoolSetting hypixelAutocomplete = bool("hypixelAutocomplete", true);
    private final BoolSetting shortChannelNames = bool("shortChannelNames", false);
    private final BoolSetting removeGuildOnTab = bool("removeGuildOnTab", true);
    private final BoolSetting removeLobbyStatuses = bool("removeLobbyStatuses", false);
    private final BoolSetting removeGuildMotd = bool("removeGuildMotd", true);
    private final BoolSetting levelAbove = bool("levelAbove", false);
    private final ColorSetting levelColor = color("levelColor", -171);
    private final ColorSetting levelHeadNumberColor = color("levelHeadNumberColor", -11141121);
    private final BoolSetting useBedwarsLevelsFormat = bool("useBedwarsLevelsFormat", true);
    private final BoolSetting hideTeamChat = bool("hideTeamChat", false);
    private final BoolSetting hideGuildChat = bool("hideGuildChat", false);
    private final BoolSetting hidePartyChat = bool("hidePartyChat", false);
    private final BoolSetting hideShout = bool("hideShout", false);
    private final BoolSetting hideSpectatorChat = bool("hideSpectatorChat", false);
    private final BoolSetting hideJoinMessages = bool("hideJoinMessages", false);
    private final BoolSetting hideLeaveMessages = bool("hideLeaveMessages", false);
    private final BoolSetting hidePrivateMessages = bool("hidePrivateMessages", false);
    private final BoolSetting hideSoulWellAnnouncements = bool("hideSoulWellAnnouncements", false);
    private final BoolSetting hideMysteryBoxAnnouncements = bool("hideMysteryBoxAnnouncements", false);
    private final TextSetting autoGGMessage = add(new TextSetting("autoGGMessage", "gg"));

    private final Map<UUID, String> levels = new ConcurrentHashMap<UUID, String>();
    private final Map<String, UUID> names = new ConcurrentHashMap<String, UUID>();
    private final Set<UUID> pending = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private List<Format> formats = Collections.emptyList();
    private Node autocomplete;

    private long lastGG, joined;
    private long ticks;

    private int motdLines = -1;
    private boolean whoSent;

    static final class Format { String name, colors, symbol; }
    static final class Formats { List<Format> formats; }

    static final class Node {
        final String[] values;
        final Map<String, Node> children;
        Node(String[] values, Map<String, Node> children) { this.values = values; this.children = children; }
    }

    public ModuleHypixelMods() {
        super("HYPIXEL_MOD", true);
        instance = this;
        rowShownWhen(Server::hypixel);
        ChatEvent.listen(this::onMessage);
        HypixelLocation.listen((before, now) -> onLocation(now));
        levelHeadSource.onChange(this::clearLevels);
        useBedwarsLevelsFormat.onChange(this::clearLevels);
        loadFormats();
    }

    @Override protected void layout(Page page) {
        page.add(removeGuildOnTab, removeGuildMotd, shortChannelNames, autoFriend, autoTip);
        page.group(autoGG, g -> g.add(autoGGMessage));
        page.add(antiGG, autoWho, levelHead);
        page.add(hypixelAutocomplete);
        page.section("levelHeadOptions", s -> {
            s.add(levelHeadSource, levelColor, levelHeadNumberColor, levelAbove).hideIf(() -> !levelHead.on());
            s.add(useBedwarsLevelsFormat).hideIf(() -> !levelHead.on() || !levelHeadSource.is(Source.BEDWARS));
        });
        page.section("chatOptions", s -> s.add(hidePrivateMessages, hideTeamChat, hidePartyChat, hideGuildChat, hideShout, hideSpectatorChat,
            removeLobbyStatuses, hideJoinMessages, hideLeaveMessages, hideSoulWellAnnouncements, hideMysteryBoxAnnouncements));
    }

    private boolean active() { return isEnabled() && Server.hypixel(); }

    private void clearLevels() { levels.clear(); names.clear(); pending.clear(); }

    private void loadFormats() {
        try (InputStream in = ModuleHypixelMods.class.getResourceAsStream("/assets/lunarforge/hypixel/bedwars_levels_format.json")) {
            if (in == null) return;
            Formats f = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Formats.class);
            if (f != null && f.formats != null) formats = f.formats;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String bedwarsLevel(int level) {
        if (formats.isEmpty()) return String.valueOf(level);
        Format f = formats.get(Math.min(level / 100, formats.size() - 1));
        String digits = String.valueOf(level);
        StringBuilder b = new StringBuilder();
        b.append('§').append(f.colors.charAt(0)).append('[');
        for (int i = 0; i < digits.length(); i++) b.append('§').append(f.colors.charAt(Math.min(i + 1, 4))).append(digits.charAt(i));
        b.append('§').append(f.colors.charAt(5)).append(f.symbol);
        b.append('§').append(f.colors.charAt(6)).append(']');
        return b.toString();
    }

    private String levelText(int level) {
        return levelHeadSource.is(Source.BEDWARS) && useBedwarsLevelsFormat.on() ? bedwarsLevel(level) : String.valueOf(level);
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) { joined = Minecraft.getSystemTime(); }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) { names.clear(); }

    private void onLocation(HypixelLocation.Location location) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active() || mc.thePlayer == null || location == null) return;
        if (autoTip.on() && !("SKYBLOCK".equals(location.gametype) && "Dungeon".equals(location.mode))) mc.thePlayer.sendChatMessage("/tip all");
        if (autoWho.on()) {
            mc.thePlayer.sendChatMessage("/who");
            whoSent = true;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 4 != 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getSession() == null || mc.getSession().getProfile().getId() == null) return;
        if (!active() || !levelHead.on() || pending.isEmpty()) return;
        final Set<UUID> batch = ImmutableSet.copyOf(pending);
        pending.removeAll(batch);
        String source = mc.getSession().getProfile().getId().toString();
        String game = location();
        StringBuilder q = new StringBuilder("?");
        for (UUID id : batch) q.append("uuid=").append(id).append('&');
        q.append("gameMode=").append(game).append('&');
        q.append("sourceUuid=").append(source).append('&');
        q.append("commit=").append(com.example.lunarforge.net.LunarBuild.commit()).append('&');
        q.append("level=").append(levelHeadSource.get().name());
        final String url = LEVELHEAD + q, gameHeader = game, sourceHeader = source;
        Thread t = new Thread(() -> fetch(url, gameHeader, sourceHeader, batch), "Lunar Level Head");
        t.setDaemon(true);
        t.start();
    }

    private static String location() {
        HypixelLocation.Location l = HypixelLocation.get();
        String server = l == null || l.server == null ? null : l.server;
        try {
            return server == null ? "unknown" : URLEncoder.encode(server, "UTF-8");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void fetch(String url, String game, String source, Set<UUID> batch) {
        try {
            HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "LunarClient/" + com.example.lunarforge.net.LunarBuild.version());
            c.setRequestProperty("X-Hypixel-Gamemode", game);
            c.setRequestProperty("X-SourceUuid", source);
            c.setRequestProperty("X-LC-Commit", com.example.lunarforge.net.LunarBuild.commit());
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            JsonObject json;
            try (InputStream in = c.getInputStream()) {
                json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            for (UUID id : batch) {
                JsonElement e = json.get(id.toString());
                if (e != null && !e.isJsonNull()) levels.put(id, levelText(e.getAsInt()));
                else names.values().removeIf(id::equals);
            }
        } catch (Exception ignored) {
        }
    }

    private void onMessage(ChatEvent e) {
        if (!active()) return;
        IChatComponent component = e.component();
        Minecraft mc = Minecraft.getMinecraft();
        if (motdLines >= 0) {
            motdLines++;
            if (motdLines != 1 && (motdLines >= 6 || component.getChatStyle().getColor() == EnumChatFormatting.AQUA && e.plain.startsWith(RULE))) motdLines = -1;
            e.cancelled = true;
            return;
        }
        String text = e.plain;
        if (autoWho.on()) {
            if (text.equalsIgnoreCase("Cages opened! FIGHT!")) {
                mc.thePlayer.sendChatMessage("/who");
                whoSent = true;
            } else if (whoSent) {
                if (text.equalsIgnoreCase("This command is not available on this server!") || text.equalsIgnoreCase("Game hasn't started yet!")) {
                    e.cancelled = true;
                    whoSent = false;
                } else if (text.startsWith("Mode: ") || text.startsWith("ONLINE: ")) {
                    whoSent = false;
                }
            }
        }
        if (autoTip.on() && text.equalsIgnoreCase("You already tipped everyone that has boosters active, so there isn't anybody to be tipped right now!")) {
            e.cancelled = true;
            return;
        }
        if (antiGG.on()) {
            String lower = text.toLowerCase(Locale.ROOT), gg = autoGGMessage.get();
            if (lower.endsWith("gg") || lower.endsWith("good game") || !gg.isEmpty() && lower.endsWith(gg)) e.cancelled = true;
        }
        if (autoGG.on()) {
            if (Minecraft.getSystemTime() - lastGG <= TimeUnit.SECONDS.toMillis(2)) return;
            String gg = autoGGMessage.get();
            if (gg.length() > GG_MAX) gg = gg.substring(0, GG_MAX);
            if (text.startsWith(" ") && !text.startsWith(" + ") && !gg.isEmpty()) {
                for (String winner : WINNERS) {
                    if (!text.contains(winner)) continue;
                    mc.thePlayer.sendChatMessage("/achat " + gg);
                    lastGG = Minecraft.getSystemTime();
                    break;
                }
            }
        }
        if (autoFriend.on() && !text.contains(":") && text.contains("Friend request from")) {
            for (String line : text.split("\n")) {
                if (!line.contains("Friend request from ")) continue;
                String[] parts = line.replace("Friend request from ", "").split(" ");
                mc.thePlayer.sendChatMessage("/friend accept " + parts[parts.length - 1]);
                break;
            }
        }
        if (e.cancelled) return;
        if (removeLobbyStatuses.on() && coloredContains(component, EnumChatFormatting.GOLD, "joined the lobby!", "spooked into the lobby!")) e.cancelled = true;
        else if (hideJoinMessages.on() && endsYellow(component, "joined.")) e.cancelled = true;
        else if (hideLeaveMessages.on() && endsYellow(component, "left.")) e.cancelled = true;
        else if (hidePrivateMessages.on() && PRIVATE.matcher(text).matches()) e.cancelled = true;
        else if (hideShout.on() && startsColored(component, "[SHOUT]", EnumChatFormatting.GOLD)) e.cancelled = true;
        else if (hideSpectatorChat.on() && startsColored(component, "[SPECTATOR]", EnumChatFormatting.GRAY)) e.cancelled = true;
        else if (hideSoulWellAnnouncements.on() && WELL.matcher(text).matches()) e.cancelled = true;
        else if (hideMysteryBoxAnnouncements.on() && text.startsWith("✦") && text.contains("Mystery Box")) e.cancelled = true;
        else if (hideTeamChat.on() && TEAM.matcher(text).matches()) e.cancelled = true;
        if (!e.cancelled && removeGuildMotd.on() && Minecraft.getSystemTime() - joined < 3000L
                && firstColor(component) == EnumChatFormatting.AQUA && text.equals(MOTD)) {
            motdLines = 0;
            e.cancelled = true;
            return;
        }
        if (!e.cancelled) channels(e, text);
    }

    private void channels(ChatEvent e, String text) {
        String from = null, to = null;
        if (text.startsWith("Party > ")) {
            if (hidePartyChat.on()) e.cancelled = true;
            else if (shortChannelNames.on()) { from = "Party"; to = "P"; }
        } else if (text.startsWith("Guild > ")) {
            if (hideGuildChat.on()) e.cancelled = true;
            else if (shortChannelNames.on()) { from = "Guild >"; to = "G >"; }
        } else if (shortChannelNames.on()) {
            if (text.startsWith("Friend > ")) { from = "Friend >"; to = "F >"; }
            else if (text.startsWith("Officer > ")) { from = "Officer >"; to = "O >"; }
        }
        if (from != null) e.set(replaceFirst(e.component(), from, to));
    }

    private static List<IChatComponent> parts(IChatComponent c) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        for (IChatComponent part : c) out.add(part);
        return out;
    }

    private static EnumChatFormatting firstColor(IChatComponent c) {
        for (IChatComponent part : c) if (!part.getUnformattedTextForChat().isEmpty()) return part.getChatStyle().getColor();
        return c.getChatStyle().getColor();
    }

    private static boolean coloredContains(IChatComponent c, EnumChatFormatting color, String... needles) {
        for (IChatComponent part : parts(c)) {
            if (part.getChatStyle().getColor() != color) continue;
            String t = Server.strip(part.getUnformattedTextForChat());
            for (String n : needles) if (t.contains(n)) return true;
        }
        return false;
    }

    private static boolean startsColored(IChatComponent c, String prefix, EnumChatFormatting color) {
        for (IChatComponent part : parts(c)) {
            String t = Server.strip(part.getUnformattedTextForChat());
            if (t.isEmpty()) continue;
            return part.getChatStyle().getColor() == color && t.startsWith(prefix);
        }
        return false;
    }

    private static boolean endsYellow(IChatComponent c, String suffix) {
        List<IChatComponent> all = parts(c);
        for (int i = all.size() - 1; i >= 0; i--) {
            String t = Server.strip(all.get(i).getUnformattedTextForChat());
            if (t.isEmpty()) continue;
            return all.get(i).getChatStyle().getColor() == EnumChatFormatting.YELLOW && t.endsWith(suffix);
        }
        return false;
    }

    private static IChatComponent replaceFirst(IChatComponent c, String from, String to) {
        IChatComponent out = new ChatComponentText("");
        boolean done = false;
        for (IChatComponent part : parts(c)) {
            String t = part.getUnformattedTextForChat();
            if (!done && !Server.strip(t).isEmpty()) {
                done = true;
                if (Server.strip(t).startsWith(from)) t = t.replaceFirst(Pattern.quote(from), to);
            }
            IChatComponent copy = new ChatComponentText(t);
            copy.setChatStyle(part.getChatStyle().createDeepCopy());
            out.appendSibling(copy);
        }
        return out;
    }

    public static String tabName(String name) {
        ModuleHypixelMods m = instance;
        if (name == null || !name.endsWith("]") || m == null || !m.active() || !m.removeGuildOnTab.on()) return name;
        int at = name.lastIndexOf('[');
        return at < 0 ? name : name.substring(0, at);
    }

    private String level(EntityPlayer player) {
        if (!active() || !levelHead.on()) return null;
        UUID id = player.getUniqueID();
        if (npc(player)) return null;
        if (player.hasCustomName() && player.getCustomNameTag().contains("§k")) return null;
        String level = levels.get(id);
        if (level == null) {
            if (id.version() == 1) {
                levels.put(id, levelText(levelHeadSource.get().randomLevel(id)));
            } else if (!names.containsKey(player.getName())) {
                names.put(player.getName(), id);
                levels.put(id, "");
                pending.add(id);
            }
            return null;
        }
        return level.isEmpty() ? null : level;
    }

    private static boolean npc(EntityPlayer player) {
        if (player.getUniqueID().version() == 2) return true;
        NetworkPlayerInfo info = Minecraft.getMinecraft().getNetHandler() == null ? null : Minecraft.getMinecraft().getNetHandler().getPlayerInfo(player.getUniqueID());
        return info == null;
    }

    public static double nameY(EntityLivingBase entity, double y) {
        ModuleHypixelMods m = instance;
        if (m == null || !(entity instanceof EntityPlayer) || m.levelAbove.on()) return y;
        return m.level((EntityPlayer)entity) == null ? y : y + 1 / 3.5;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onName(RenderLivingEvent.Specials.Post<EntityLivingBase> event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer)event.entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (player.isSneaking() || !Minecraft.isGuiEnabled() || player.isInvisibleToPlayer(mc.thePlayer)) return;
        String level = level(player);
        if (level == null) return;
        RenderManager rm = mc.getRenderManager();
        if (rm.livingPlayer != null && player.getDistanceSqToEntity(rm.livingPlayer) > 64 * 64) return;
        if (player == rm.livingPlayer) return;
        double y = event.y + player.height + 0.5 - (player.isChild() ? player.height / 2.0f : 0);
        if (levelAbove.on()) y += 1 / 3.5;
        if (player.getDistanceSqToEntity(rm.livingPlayer) < 100.0 && player.getWorldScoreboard().getObjectiveInDisplaySlot(2) != null) {
            y += mc.fontRendererObj.FONT_HEIGHT * 1.15f * 0.02666667f;
        }
        String prefix = levelHeadSource.get().prefix();
        drawLabel(rm, mc.fontRendererObj, prefix, level, event.x, y, event.z);
    }

    private void drawLabel(RenderManager rm, FontRenderer font, String prefix, String level, double x, double y, double z) {
        int prefixColor = levelColor.color(0.0f) & 0xFFFFFF | 0xDF000000;
        int numberColor = levelHeadNumberColor.color(0.0f) & 0xFFFFFF | 0xDF000000;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float)x, (float)y, (float)z);
        GL11Normal();
        GlStateManager.rotate(-rm.playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(rm.playerViewX, 1.0f, 0.0f, 0.0f);
        GlStateManager.scale(-0.02666667f, -0.02666667f, 0.02666667f);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int w = font.getStringWidth(prefix + level) / 2;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        float alpha = 0.25f;
        GlStateManager.disableTexture2D();
        wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(-w - 1, -1, 0).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
        wr.pos(-w - 1, 8, 0).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
        wr.pos(w + 1, 8, 0).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
        wr.pos(w + 1, -1, 0).color(0.0f, 0.0f, 0.0f, alpha).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
        int px = font.getStringWidth(prefix);
        font.drawString(prefix, -w, 0, 553648127);
        font.drawString(level, -w + px, 0, 553648127);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        font.drawString(prefix, -w, 0, prefixColor);
        font.drawString(level, -w + px, 0, numberColor);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    private static void GL11Normal() { org.lwjgl.opengl.GL11.glNormal3f(0.0f, 1.0f, 0.0f); }

    public static String[] complete(String typed) {
        ModuleHypixelMods m = instance;
        if (m == null || !m.active() || !m.hypixelAutocomplete.on() || !typed.startsWith("/")) return null;
        Node node = m.tree();
        String command = typed.substring(1).toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<String>(WORDS.splitToList(command));
        if (typed.endsWith(" ")) words.add("");
        if (words.isEmpty()) return null;
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (i == words.size() - 1) {
                List<String> out = new ArrayList<String>();
                for (String key : node.children.keySet()) if (key.startsWith(word)) out.add(i == 0 ? "/" + key : key);
                for (String value : node.values) {
                    if (value.equals("%PARTY%")) {
                        String own = Minecraft.getMinecraft().thePlayer.getName();
                        if (own.toLowerCase(Locale.ROOT).startsWith(word) && !out.contains(own)) out.add(own);
                    } else if (value.equals("%PLAYERS%")) {
                        for (NetworkPlayerInfo info : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
                            String name = info.getGameProfile().getName();
                            if (name.toLowerCase(Locale.ROOT).startsWith(word)) out.add(name);
                        }
                    } else if (value.startsWith(word)) {
                        out.add(i == 0 ? "/" + value : value);
                    }
                }
                if (out.size() == 1 && ("/" + command).endsWith(out.get(0))) return new String[0];
                return out.isEmpty() ? null : out.toArray(new String[0]);
            }
            node = node.children.get(word);
            if (node == null) return null;
        }
        return null;
    }

    private Node tree() {
        if (autocomplete != null) return autocomplete;
        autocomplete = new Node(new String[0], new HashMap<String, Node>());
        try (InputStream in = ModuleHypixelMods.class.getResourceAsStream("/assets/lunarforge/hypixel/autocomplete.json")) {
            if (in != null) autocomplete = node(new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return autocomplete;
    }

    private static Node node(JsonElement json) {
        if (!json.isJsonObject()) return new Node(new String[0], new HashMap<String, Node>());
        String[] values = new String[0];
        Map<String, Node> children = new HashMap<String, Node>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonArray() && e.getKey().equals("__values__")) {
                values = new Gson().fromJson(e.getValue(), String[].class);
            } else if (e.getValue().isJsonObject()) {
                Node child = node(e.getValue());
                for (String name : e.getKey().split("\\|")) children.put(name, child);
            }
        }
        return new Node(values, children);
    }
}
