package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.module.ChatEvent;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.TextSetting;
import com.example.lunarforge.util.Fields;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ModuleNickHider extends Module {
    private static ModuleNickHider instance;
    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");
    private static final Pattern SENDING = Pattern.compile("Sending to server (?<lobbyName>([a-zA-Z0-9])+)(...|!)");
    private static final Pattern HUB = Pattern.compile("Request join for Hub (\\#[0-9]+ \\()?(mini|mega)[0-9]+[A-Z]\\)?(...|!)");
    private static final Pattern SENDING_YOU = Pattern.compile("Sending you to (mini|mega)[0-9]+[A-Z](...|!)");

    private static final Pattern NICK_BOOK = Pattern.compile("you will be nicked as ?(?:[\\[\\]a-zA-Z0-9+]+)? (?<name>[a-zA-Z0-9_]{3,16}).");
    private static final Pattern NICKED = Pattern.compile("You are now nicked as (?<name>[a-zA-Z0-9_]{3,16})!");
    private static final Pattern NAME = Pattern.compile("([a-zA-Z0-9_]{3,16})");
    private static final Field BOOK_PAGES = Fields.find(GuiScreenBook.class, "bookPages", "field_146483_y");
    private static final Field BOOK_PAGE = Fields.find(GuiScreenBook.class, "currPage", "field_146484_x");
    private static final Field SIGN = Fields.find(GuiEditSign.class, "tileSign", "field_146848_f");

    private final BoolSetting hideName = bool("hideName", true);
    private final BoolSetting hideRealName = bool("hideRealName", true);
    private final BoolSetting hideOthersNames = bool("hideOthersNames", false);
    private final BoolSetting hideOwnSkin = bool("hideOwnSkin", true);
    private final BoolSetting useRealSkin = bool("useRealSkin", true);
    private final BoolSetting hideOthersSkin = bool("hideOthersSkin", false);
    private final BoolSetting hideLobbyID = bool("hideLobbyID", false);
    private final TextSetting ownName = add(new TextSetting("ownName", "You"));
    private final TextSetting hiddenPrefix = add(new TextSetting("hiddenPrefix", "Player"));
    private final BoolSetting customSuffix = bool("customSuffix", false);
    private final TextSetting hiddenSuffix = add(new TextSetting("hiddenSuffix"));

    private static final class Replacement {
        final Pattern pattern;
        final String name, with;
        final boolean own;
        Replacement(String name, String with, boolean own) {
            this.pattern = Pattern.compile(Pattern.quote(name), Pattern.CASE_INSENSITIVE);
            this.name = name;
            this.with = with;
            this.own = own;
        }
    }

    private volatile List<Replacement> replacements = Collections.emptyList();
    private final AtomicInteger counter = new AtomicInteger();

    private final LoadingCache<String, String> strings = CacheBuilder.newBuilder().maximumSize(2500L)
        .expireAfterAccess(5L, TimeUnit.MINUTES).build(new CacheLoader<String, String>() {
            @Override public String load(String s) { return replaceKeepingStyle(s); }
        });

    private String hypixelNick;

    private final String serverNick = "";

    private String lastOwnName, lastPrefix, lastSuffix;

    private final Set<UUID> seen = new HashSet<UUID>();

    private static ResourceLocation realSkin;
    private static String realSkinType;
    private static boolean realSkinRequested;

    public ModuleNickHider() {
        super("NICK_HIDER", false);
        instance = this;
        hypixelNick = ModuleManager.store(key(), "lastKnownHypixelNick", "");
        lastOwnName = ownName.get();
        lastPrefix = hiddenPrefix.get();
        lastSuffix = hiddenSuffix.get();
        ownName.onChange(() -> validate(ownName, "Nickname"));
        hiddenPrefix.onChange(() -> validate(hiddenPrefix, "Prefix"));
        hiddenSuffix.onChange(() -> validate(hiddenSuffix, "Suffix"));
        hideName.onChange(() -> { if (hideName.on()) hideOwn(); else forgetHypixelNick(false); });
        hideRealName.onChange(() -> {
            if (hideRealName.on()) { forgetHypixelNick(false); hideOwn(); }
            else {
                remove(username());
                forgetHypixelNick(false);
                if (hideName.on() && !hypixelNick.isEmpty()) add(hypixelNick, true);
            }
        });
        hideOthersNames.onChange(() -> {
            if (hideOthersNames.on()) hideOthers();
            else { keepOwn(); invalidate(); }
        });
        customSuffix.onChange(this::rebuildAll);
        ChatEvent.listen(this::onMessage);
    }

    @Override protected void layout(Page page) {
        page.section("nameOptions", s -> {
            s.group(hideName, g -> g.add(ownName));
            s.add(hideRealName);
            s.group(hideOthersNames, g -> {
                g.add(hiddenPrefix);
                g.group(customSuffix, c -> c.add(hiddenSuffix));
            });
            s.add(hideLobbyID);
        });
        page.section("skinOptions", s -> s.add(hideOwnSkin, useRealSkin, hideOthersSkin));
    }

    public static boolean hidesLobbyRows() { return instance != null && instance.isEnabled() && instance.hideLobbyID.on(); }

    public static String hypixelNick() {
        return instance == null || instance.hypixelNick.isEmpty() ? null : instance.hypixelNick;
    }

    private static String username() { return Minecraft.getMinecraft().getSession().getUsername(); }

    private void validate(TextSetting setting, String what) {
        String raw = setting.get(), clean = clean(raw);
        if (!clean.equals(raw)) { setting.set(clean); return; }
        if (valid(clean, what)) {
            if (setting == ownName) { lastOwnName = clean; hideOwn(); }
            else { if (setting == hiddenPrefix) lastPrefix = clean; else lastSuffix = clean; rebuildAll(); }
        } else {
            setting.set(setting == ownName ? lastOwnName : setting == hiddenPrefix ? lastPrefix : lastSuffix);
        }
    }

    private static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (b.length() >= 16) break;
            if (c == ' ') c = '_';
            if ("abcdefghijklmnopqrstuvwxyz0123456789_".indexOf(Character.toLowerCase(c)) >= 0) b.append(c);
        }
        return b.toString();
    }

    private boolean valid(String s, String what) {
        if (s.trim().isEmpty() && !"Suffix".equals(what)) {
            LunarNotifications.info(what + " cannot be empty!");
            return false;
        }
        return !ChatFilter.profane(s);
    }

    @Override protected void onEnable() { clean(); if (connected()) { hideOwn(); hideOthers(); } }

    @Override protected void onDisable() { clean(); }

    private static boolean connected() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer != null && mc.getNetHandler() != null;
    }

    private static boolean isName(String s) { return NAME.matcher(s).matches() && s.length() >= 3 && s.length() <= 16; }

    private Replacement find(String name) {
        for (Replacement r : replacements) if (r.name.equalsIgnoreCase(name)) return r;
        return null;
    }

    private String replacementFor(boolean own) {
        if (own) {
            if (!serverNick.isEmpty()) return serverNick;
            return hideRealName.on() ? ownName.get() : username();
        }
        if (customSuffix.on()) return hiddenPrefix.get() + hiddenSuffix.get();
        return hiddenPrefix.get() + "-" + counter.incrementAndGet();
    }

    private void add(String name, boolean own) {
        boolean on = isEnabled() || own && !serverNick.isEmpty();
        if (!on || name == null || find(name) != null || !isName(name)) return;
        List<Replacement> list = new ArrayList<Replacement>(replacements);
        list.add(new Replacement(name, replacementFor(own), own));
        list.sort((a, b) -> Integer.compare(b.name.length(), a.name.length()));
        replacements = Collections.unmodifiableList(list);
        invalidate();
    }

    private void remove(String name) {
        Replacement r = find(name);
        if (r == null) return;
        List<Replacement> list = new ArrayList<Replacement>(replacements);
        list.remove(r);
        replacements = Collections.unmodifiableList(list);
        invalidate();
    }

    private void hideOwn() {
        String name = username();
        if (hideRealName.on()) {
            remove(name);
            add(name, true);
        }
        if (hideName.on() && !hypixelNick.isEmpty()) add(hypixelNick, true);
    }

    private void hideOthers() {
        if (!hideOthersNames.on() || Minecraft.getMinecraft().getNetHandler() == null) return;
        for (NetworkPlayerInfo info : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
            GameProfile p = info.getGameProfile();
            if (p == null || p.getName() == null || p.getName().equals(username()) || p.getName().equals(hypixelNick)) continue;
            consider(p);
        }
    }

    private void consider(GameProfile profile) {
        if (profile == null || Minecraft.getMinecraft().theWorld == null) return;
        String name = Server.strip(profile.getName());
        boolean self = name.equals(username());
        boolean nick = name.equals(hypixelNick) || !serverNick.isEmpty() && name.equals(serverNick);
        boolean other = !self && !nick && hideOthersNames.on();
        boolean server = !serverNick.isEmpty();
        if (self && (hideRealName.on() || server) || nick && (hideName.on() || server) || other) add(name, self || nick);
    }

    private void keepOwn() {
        List<Replacement> list = new ArrayList<Replacement>();
        for (Replacement r : replacements) if (r.own) list.add(r);
        replacements = Collections.unmodifiableList(list);
    }

    private void rebuildAll() {
        clean();
        if (Minecraft.getMinecraft().theWorld == null) return;
        for (net.minecraft.entity.player.EntityPlayer p : Minecraft.getMinecraft().theWorld.playerEntities) consider(p.getGameProfile());
    }

    private void clean() {
        replacements = Collections.emptyList();
        counter.set(0);
        seen.clear();
        invalidate();
    }

    private void invalidate() { strings.invalidateAll(); }

    private void forgetHypixelNick(boolean forget) {
        if (hypixelNick.isEmpty()) return;
        remove(hypixelNick);
        if (forget) setHypixelNick("");
    }

    private void nicked(String name) {
        forgetHypixelNick(true);
        setHypixelNick(name);
        if (!hypixelNick.isEmpty()) add(hypixelNick, true);
    }

    private void setHypixelNick(String nick) {
        hypixelNick = nick;
        ModuleManager.put(key(), "lastKnownHypixelNick", nick);
    }

    public static String replace(String text) {
        ModuleNickHider m = instance;
        if (text == null || m == null || !m.isEnabled() && m.serverNick.isEmpty() || m.replacements.isEmpty()) return text;
        return m.strings.getUnchecked(text);
    }

    private String replaceKeepingStyle(String text) {
        boolean server = !serverNick.isEmpty();
        if (!(hideName.on() || hideOthersNames.on() || hideRealName.on() || server)) return text;
        List<String> styles = new ArrayList<String>();
        StringBuilder chars = new StringBuilder();
        String style = "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                style = ChatText.formatting(style + text.substring(i, i + 2));
                i++;
                continue;
            }
            chars.append(c);
            styles.add(style);
        }
        boolean changed = false;
        for (Replacement r : replacements) {
            boolean use = r.own ? hideName.on() || hideRealName.on() || server : hideOthersNames.on();
            if (!use) continue;
            Matcher m = r.pattern.matcher(chars);
            if (!m.find()) continue;
            changed = true;
            List<String> outStyles = new ArrayList<String>();
            StringBuilder out = new StringBuilder();
            int from = 0, len = r.with.length();
            do {
                int start = m.start(), end = m.end();
                for (int i = from; i < start; i++) { outStyles.add(styles.get(i)); out.append(chars.charAt(i)); }
                float step = (float)(end - start) / (float)len, at = start;
                for (int i = 0; i < len; i++) {
                    out.append(r.with.charAt(i));
                    outStyles.add(styles.get(Math.min((int)at, end)));
                    at += step;
                }
                from = end;
            } while (m.find());
            for (int i = from; i < chars.length(); i++) { outStyles.add(styles.get(i)); out.append(chars.charAt(i)); }
            styles = outStyles;
            chars = out;
        }
        if (!changed) return text;
        StringBuilder result = new StringBuilder();
        String current = "";
        for (int i = 0; i < chars.length(); i++) {
            String s = styles.get(i);
            if (!s.equals(current)) {
                result.append("§r").append(s);
                current = s;
            }
            result.append(chars.charAt(i));
        }
        return result.toString();
    }

    public static ResourceLocation skin(ResourceLocation skin, NetworkPlayerInfo info) {
        ModuleNickHider m = instance;
        Minecraft mc = Minecraft.getMinecraft();
        if (m == null || !m.isEnabled() || mc.thePlayer == null || info.getGameProfile() == null) return skin;
        boolean own = mc.thePlayer.getUniqueID().equals(info.getGameProfile().getId());
        if (own) {
            if (m.useRealSkin.on() && realSkin() != null) return realSkin;
            if (m.hideOwnSkin.on()) return defaultSkin(info.getSkinType());
        } else if (m.hideOthersSkin.on()) {
            return defaultSkin(info.getSkinType());
        }
        return skin;
    }

    public static String skinType(String type, NetworkPlayerInfo info) {
        ModuleNickHider m = instance;
        Minecraft mc = Minecraft.getMinecraft();
        if (m == null || !m.isEnabled() || mc.thePlayer == null || info.getGameProfile() == null) return type;
        boolean own = mc.thePlayer.getUniqueID().equals(info.getGameProfile().getId());
        return own && m.useRealSkin.on() && realSkinType != null ? realSkinType : type;
    }

    private static ResourceLocation defaultSkin(String type) { return type == null || type.equals("default") ? STEVE : ALEX; }

    private static ResourceLocation realSkin() {
        if (!realSkinRequested) {
            realSkinRequested = true;
            final Minecraft mc = Minecraft.getMinecraft();
            final GameProfile profile = mc.getSession().getProfile();
            if (profile != null && profile.getId() != null) {
                new Thread(() -> {
                    try {
                        GameProfile filled = mc.getSessionService().fillProfileProperties(profile, false);
                        mc.addScheduledTask(() -> mc.getSkinManager().loadProfileTextures(filled, (type, location, texture) -> {
                            if (type != MinecraftProfileTexture.Type.SKIN) return;
                            realSkin = location;
                            String model = texture.getMetadata("model");
                            realSkinType = model == null ? "default" : model;
                        }, false));
                    } catch (Exception ignored) {
                    }
                }, "Nick Hider skin").start();
            }
        }
        return realSkin;
    }

    private void onMessage(ChatEvent e) {
        if (!isEnabled() || e.cancelled) return;
        String text = Server.strip(e.component().getUnformattedText());
        if (hideLobbyID.on() && (SENDING.matcher(text).matches() || SENDING_YOU.matcher(text).matches() || HUB.matcher(text).matches())) {
            e.cancelled = true;
            return;
        }
        if (Server.hypixel()) {
            if (e.legacy.equalsIgnoreCase("Your nick has been reset!")) forgetHypixelNick(true);
            else {
                Matcher m = NICKED.matcher(e.legacy);
                if (m.find()) nicked(m.group("name"));
            }
        }
    }

    @SubscribeEvent
    public void onGui(GuiOpenEvent event) {
        if (!isEnabled()) return;
        if (event.gui instanceof GuiScreenBook) {
            NBTTagList pages = (NBTTagList)Fields.get(BOOK_PAGES, event.gui);
            if (pages == null) return;
            int page = Fields.getInt(BOOK_PAGE, event.gui);
            String raw = pages.getStringTagAt(page);
            String text;
            try { text = IChatComponent.Serializer.jsonToComponent(raw).getUnformattedText(); }
            catch (Exception ex) { text = raw; }
            Matcher m = NICK_BOOK.matcher(Server.strip(text));
            if (m.find()) nicked(m.group("name"));
        } else if (event.gui == null && Minecraft.getMinecraft().currentScreen instanceof GuiEditSign) {
            TileEntitySign sign = (TileEntitySign)Fields.get(SIGN, Minecraft.getMinecraft().currentScreen);
            if (sign == null) return;
            String hint = sign.signText[2].getUnformattedText() + " " + sign.signText[3].getUnformattedText();
            if (hint.equalsIgnoreCase("Enter your desired username here")) nicked(sign.signText[0].getUnformattedText());
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (isEnabled() && event.world.isRemote && event.entity instanceof EntityOtherPlayerMP) consider(((EntityOtherPlayerMP)event.entity).getGameProfile());
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            clean();
            if (isEnabled()) hideOwn();
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled() || Minecraft.getMinecraft().getNetHandler() == null) return;
        for (NetworkPlayerInfo info : Minecraft.getMinecraft().getNetHandler().getPlayerInfoMap()) {
            GameProfile p = info.getGameProfile();
            if (p == null || p.getId() == null || !seen.add(p.getId())) continue;
            consider(p);
        }
    }
}
