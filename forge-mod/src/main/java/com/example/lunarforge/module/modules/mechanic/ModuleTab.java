package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarGfx;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.util.Fields;
import com.google.common.collect.Ordering;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.IChatComponent;

public final class ModuleTab extends Module {
    static ModuleTab instance;
    private static final Field HEADER = Fields.find(GuiPlayerTabOverlay.class, "header", "field_175256_i");
    private static final Field FOOTER = Fields.find(GuiPlayerTabOverlay.class, "footer", "field_175255_h");
    private static final Field DISPLAY_NAME = Fields.find(NetworkPlayerInfo.class, "displayName", "field_178872_h");

    final BoolSetting pingRow = bool("pingRow", false);
    final ColorSetting headerColor = color("headerColor", 0x80000000);
    final ColorSetting footerColor = color("footerColor", 0x80000000);
    final ColorSetting backgroundColor = color("backgroundColor", 0x80000000);
    final ColorSetting rowsColor = color("rowsColor", 0x20000000);
    final BoolSetting highlightOwnName = bool("highlightOwnName", false);
    final ColorSetting nameColor = color("nameColor", 0xFFFF0000);
    final BoolSetting disableHeader = bool("disableHeader", false);
    final BoolSetting disableFooter = bool("disableFooter", false);
    final BoolSetting hideNPC = bool("hideNPC", false);
    final BoolSetting nameShadow = bool("nameShadow", true);
    final BoolSetting pingNumberShadow = bool("pingNumberShadow", false);
    final BoolSetting hidePing = bool("hidePing", false);
    final BoolSetting hidePingIfOver500 = bool("hidePingIfOver500", true);
    final BoolSetting dynamicPingColor = bool("dynamicPingColor", false);
    final BoolSetting displayPingAsNumber = bool("displayPingAsNumber", false);
    final ColorSetting pingNumberColor = color("pingNumberColor", 0xFFFFFF55);
    final ColorSetting lowPingNumberColor = color("lowPingNumberColor", 0xFF55FF55);
    final ColorSetting mediumPingNumberColor = color("mediumPingNumberColor", 0xFFFFFF55);
    final ColorSetting highPingNumberColor = color("highPingNumberColor", 0xFFFF5555);
    final ColorSetting extremePingNumberColor = color("extremePingNumberColor", 0xFFAA0000);
    final BoolSetting showLunarIconsOnRight = bool("showLunarIconsOnRight", false);
    final BoolSetting moveSelfToTop = bool("moveSelfToTop", false);
    final BoolSetting displayPlayerHead = bool("displayPlayerHead", true);

    final KeySetting tabKeybind = add(new KeySetting("tabKeybind", "TAB", false));
    final BoolSetting toggleKeyTab = bool("toggleKeyTab", false);

    private boolean active, wasDown, closing;

    private IChatComponent heldHeader, heldFooter;

    private int row;
    private List<NetworkPlayerInfo> rows;
    private final LunarGfx gfx = new LunarGfx("tab");

    public ModuleTab() {
        super("TAB", true);
        instance = this;
        tabKeybind.onChange(() -> {
            KeyBinding list = Minecraft.getMinecraft().gameSettings.keyBindPlayerList;
            list.setKeyCode(tabKeybind.code());
            KeyBinding.resetKeyBindingArrayAndHash();
            Minecraft.getMinecraft().gameSettings.saveOptions();
        });
    }

    @Override protected void layout(Page page) {
        page.add(tabKeybind);
        page.section("generalOptions", s -> s.add(toggleKeyTab, disableHeader, displayPlayerHead, disableFooter, hideNPC, showLunarIconsOnRight));
        page.section("colorOptions", s -> {
            s.add(headerColor, footerColor);
            s.add(backgroundColor, pingRow);
            s.add(rowsColor).hideIf(pingRow::on);
        });
        page.section("nameOptions", s -> {
            s.group(highlightOwnName, g -> g.add(nameColor));
            s.add(nameShadow);
            s.add(moveSelfToTop);
        });
        page.section("pingOptions", s -> {
            s.add(hidePing, hidePingIfOver500);
            s.group(displayPingAsNumber, g -> {
                g.add(pingNumberShadow);
                g.group(dynamicPingColor, d -> d.add(lowPingNumberColor, mediumPingNumberColor, highPingNumberColor, extremePingNumberColor));
                g.add(pingNumberColor).hideIf(dynamicPingColor::on);
            });
        });
    }

    static boolean on() {
        ModuleTab t = instance;
        if (t == null || !t.isEnabled()) return false;
        String mode = HypixelLocation.get().mode;
        return mode == null || !mode.startsWith("RAVENGARD");
    }

    public static boolean listKey(KeyBinding key) {
        ModuleTab t = instance;
        if (t == null || !on()) return key.isKeyDown();
        boolean down = key.isKeyDown();
        if (down && !t.wasDown && Minecraft.getMinecraft().currentScreen == null) {
            if (!t.active) t.active = true;
            else t.closing = true;
        } else if (!down && t.wasDown) {
            if (!t.toggleKeyTab.on() || t.closing) t.active = false;
            t.closing = false;
        }
        t.wasDown = down;
        return t.active;
    }

    public static void begin(GuiPlayerTabOverlay tab) {
        ModuleTab t = instance;
        if (t == null || !on()) return;
        t.row = 0;
        if (t.disableHeader.on()) { t.heldHeader = (IChatComponent)Fields.get(HEADER, tab); Fields.set(HEADER, tab, null); }
        if (t.disableFooter.on()) { t.heldFooter = (IChatComponent)Fields.get(FOOTER, tab); Fields.set(FOOTER, tab, null); }
    }

    public static void end(GuiPlayerTabOverlay tab) {
        ModuleTab t = instance;
        if (t == null || !on()) return;
        if (t.disableHeader.on()) Fields.set(HEADER, tab, t.heldHeader);
        if (t.disableFooter.on()) Fields.set(FOOTER, tab, t.heldFooter);
        t.heldHeader = t.heldFooter = null;
    }

    private static boolean realPlayer(NetworkPlayerInfo info) {
        GameProfile p = info.getGameProfile();
        return p.getId().version() != 2 || p.getName().startsWith("!");
    }

    public static List<NetworkPlayerInfo> sorted(Ordering<NetworkPlayerInfo> ordering, Iterable<NetworkPlayerInfo> players) {
        List<NetworkPlayerInfo> list = new ArrayList<NetworkPlayerInfo>(ordering.sortedCopy(players));
        ModuleTab t = instance;
        if (t != null && on() && t.moveSelfToTop.on() && Minecraft.getMinecraft().thePlayer != null) {
            UUID self = Minecraft.getMinecraft().thePlayer.getUniqueID();
            NetworkPlayerInfo own = null;
            for (NetworkPlayerInfo i : list) if (i.getGameProfile() != null && self.equals(i.getGameProfile().getId())) { own = i; break; }
            if (own != null) { list.remove(own); list.add(0, own); }
        }
        if (t != null && on() && t.hideNPC.on() && HypixelLocation.get().lobby()) list.removeIf(i -> !realPlayer(i));
        if (ModuleNickHider.hidesLobbyRows()) {
            NetworkPlayerInfo blank = null;
            for (NetworkPlayerInfo i : list) {
                IChatComponent name = (IChatComponent)Fields.get(DISPLAY_NAME, i);
                if (name != null && name.getUnformattedText().isEmpty()) { blank = i; break; }
            }
            for (int i = 0; i < list.size(); i++) {
                NetworkPlayerInfo info = list.get(i);
                if (info != null && Server.skyblock() && Minecraft.getMinecraft().ingameGUI.getTabList().getPlayerName(info).contains("Server: ")) {
                    if (blank != null) list.set(i, blank);
                }
            }
        }
        if (t != null) t.rows = list.subList(0, Math.min(list.size(), 80));
        return list;
    }

    public static boolean heads(boolean vanilla) {
        ModuleTab t = instance;
        return t != null && on() && !t.displayPlayerHead.on() ? false : vanilla;
    }

    public static int nine(int n) { return iconsRight() ? 18 : n; }

    public static boolean iconsRight() { ModuleTab t = instance; return t != null && on() && t.showLunarIconsOnRight.on(); }

    public static void rect(int x1, int y1, int x2, int y2, int color, int which) {
        ModuleTab t = instance;
        if (t == null || !on()) { Gui.drawRect(x1, y1, x2, y2, color); return; }
        switch (which) {
            case 0: color = t.headerColor.color(0.0f); break;
            case 1: color = t.backgroundColor.color(0.0f); break;
            case 3: color = t.footerColor.color(0.0f); break;
            default: {
                int index = t.row;
                color = t.rowsColor.color(index);
                if (t.rows != null && t.pingRow.on() && index < t.rows.size() && t.rows.get(index) != null) {
                    int ping = t.pingColor(t.rows.get(index).getResponseTime());
                    color = Math.max(0, Math.min(255, (int)((ping >>> 24) * 0.1254902f))) << 24 | ping & 0xFFFFFF;
                }
                t.row = index + 1;
            }
        }
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    static boolean hidesPing(int ping) {
        if (ping < 0) return true;
        if (Server.skyblock() && ping == 1) return true;
        ModuleTab t = instance;
        boolean over500 = t == null ? true : t.hidePingIfOver500.on();
        return t != null && on() && t.hidePing.on() || ping <= 0 || ping > 500 && over500;
    }

    int pingColor(int ping) {
        if (ping < 0) return pingNumberColor.color(0.0f);
        if (ping < 65) return lowPingNumberColor.color(0.0f);
        if (ping < 120) return mediumPingNumberColor.color(0.0f);
        if (ping < 250) return highPingNumberColor.color(0.0f);
        return extremePingNumberColor.color(0.0f);
    }

    public static int ping(int width, int x, int y, NetworkPlayerInfo info) {
        int right = x + width - 2;
        boolean hidden = hidesPing(info.getResponseTime());
        int shift = 0;
        ModuleTab t = instance;
        if (iconsRight()) {
            shift = 10;
            int off = hidden && t.hidePing.on() ? 0 : 9;
            com.example.lunarforge.net.TabLogos.drawLeftIcon(info, right - off, y);
        }
        if (hidden) return Integer.MIN_VALUE;
        if (t != null && t.number(info.getResponseTime(), right + shift, y)) return Integer.MIN_VALUE;
        return width + shift;
    }

    private boolean number(int ping, float x, float y) {
        if (!isEnabled() || !displayPingAsNumber.on()) return false;
        String s = "" + ping;
        int color = dynamicPingColor.on() ? pingColor(ping) : pingNumberColor.color(0.0f);
        gfx.setScale(new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
        float w = gfx.textWidth(s, "roboto-bold.ttf", 12);
        if (pingNumberShadow.on()) gfx.text(s, "roboto-bold.ttf", 12, x - w + 0.5f, y + 0.5f, com.example.lunarforge.module.hud.Draw.shadow(color));
        gfx.text(s, "roboto-bold.ttf", 12, x - w, y, color);
        return true;
    }

    public static boolean nameShadow() { ModuleTab t = instance; return t == null || !on() || t.nameShadow.on(); }

    public static boolean highlightOwn(NetworkPlayerInfo info) {
        ModuleTab t = instance;
        return t != null && on() && t.highlightOwnName.on() && Minecraft.getMinecraft().thePlayer != null && info.getGameProfile() != null
            && Minecraft.getMinecraft().thePlayer.getUniqueID().equals(info.getGameProfile().getId());
    }

    public static int ownNameColor() { return instance.nameColor.color(0.0f); }
}
