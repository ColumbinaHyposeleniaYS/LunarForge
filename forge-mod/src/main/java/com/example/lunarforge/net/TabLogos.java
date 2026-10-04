package com.example.lunarforge.net;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import com.example.lunarforge.module.modules.mechanic.ModuleTab;

public final class TabLogos {
    private static final ResourceLocation LOGO_16 = new ResourceLocation("lunarforge", "textures/logo/logo-16x16.png");
    private static final ResourceLocation LOGO_24 = new ResourceLocation("lunarforge", "textures/logo/logo-24x24.png");
    private static final ResourceLocation LOGO_32 = new ResourceLocation("lunarforge", "textures/logo/logo-32x32.png");
    private static final ResourceLocation LOGO_100 = new ResourceLocation("lunarforge", "textures/logo/logo-100x100.png");
    private static final ResourceLocation PLUS = new ResourceLocation("lunarforge", "ui/icons/add-64x64.png");
    private static final long EXPIRE_MS = 10 * 60 * 1000L;
    private static final int MAX_ENTRIES = 300;
    private static final int ICON = 8, SHIFT = 9;

    private static final class Logo {
        final boolean onLunar;
        final int color, plusColor;
        volatile long lastAccess = System.currentTimeMillis();
        Logo(boolean onLunar, int color, int plusColor) { this.onLunar = onLunar; this.color = color; this.plusColor = plusColor; }
    }

    private static final Map<UUID, Logo> LOGOS = new ConcurrentHashMap<UUID, Logo>();
    private static final Set<UUID> QUEUED = ConcurrentHashMap.newKeySet();
    private static volatile Logo own;
    private static long lastExpire;

    private static NetworkPlayerInfo current;

    private TabLogos() {}

    static void reset() {
        LOGOS.clear();
        QUEUED.clear();
        own = null;
    }

    static Logo get(UUID id) {
        if (id == null) return null;
        Logo self = own;
        if (self != null && id.equals(Minecraft.getMinecraft().getSession().getProfile().getId())) return self;
        Logo logo = LOGOS.get(id);
        if (logo == null) return null;
        logo.lastAccess = System.currentTimeMillis();
        return logo.onLunar ? logo : null;
    }

    private static boolean isRealPlayer(GameProfile profile) {
        return profile != null && profile.getId() != null && (profile.getId().version() != 2 || profile.getName().startsWith("!"));
    }

    private static void queue(NetworkPlayerInfo info) {
        GameProfile profile = info.getGameProfile();
        if (!isRealPlayer(profile)) return;
        Logo known = LOGOS.get(profile.getId());
        if (known != null) { known.lastAccess = System.currentTimeMillis(); return; }
        LOGOS.put(profile.getId(), new Logo(false, 0, 0));
        QUEUED.add(profile.getId());
    }

    static void tick(AssetSocket socket) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() != null) for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) queue(info);
        if (socket != null && socket.isReady() && !QUEUED.isEmpty()) {
            List<UUID> batch = new ArrayList<UUID>(QUEUED);
            QUEUED.removeAll(batch);
            socket.loadTabLogos(batch);
        }
        long now = System.currentTimeMillis();
        if (now - lastExpire < 3000L) return;
        lastExpire = now;

        for (Iterator<Map.Entry<UUID, Logo>> it = LOGOS.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue().lastAccess > EXPIRE_MS) it.remove();
        }
        while (LOGOS.size() > MAX_ENTRIES) {
            UUID oldest = null;
            long at = Long.MAX_VALUE;
            for (Map.Entry<UUID, Logo> e : LOGOS.entrySet()) {
                if (e.getValue().lastAccess < at) { at = e.getValue().lastAccess; oldest = e.getKey(); }
            }
            LOGOS.remove(oldest);
            QUEUED.remove(oldest);
        }
    }

    static void loaded(byte[] data) throws Exception {
        Proto.Reader in = new Proto.Reader(data);
        while (in.next()) {
            if (in.field() != 1) { in.skip(); continue; }
            Proto.Reader logo = new Proto.Reader(in.bytes());
            UUID id = null;
            int color = 0, plus = 0;
            while (logo.next()) {
                switch (logo.field()) {
                    case 1: id = Proto.readUuid(logo.bytes()); break;
                    case 2: color = readColor(logo.bytes()); break;
                    case 3: plus = readColor(logo.bytes()); break;
                    default: logo.skip();
                }
            }
            if (id != null) LOGOS.put(id, new Logo(true, opaque(color), plus == 0 ? 0 : opaque(plus)));
        }
    }

    static void ownLogin(byte[] data) throws Exception {
        Proto.Reader in = new Proto.Reader(data);
        int color = 0, plus = 0;
        boolean hasColor = false;
        while (in.next()) {
            if (in.field() == 1) { color = readColor(in.bytes()); hasColor = true; }
            else if (in.field() == 3) plus = readColor(in.bytes());
            else in.skip();
        }
        own = hasColor ? new Logo(true, opaque(color), plus == 0 ? 0 : opaque(plus)) : null;
    }

    private static int readColor(byte[] data) throws Exception {
        Proto.Reader in = new Proto.Reader(data);
        while (in.next()) {
            if (in.field() == 1) return (int)in.varint();
            in.skip();
        }
        return 0;
    }

    private static int opaque(int rgb) { return rgb | 0xFF000000; }

    public static String playerName(GuiPlayerTabOverlay tab, NetworkPlayerInfo info) {
        queue(info);
        current = info;
        return com.example.lunarforge.module.modules.server.ModuleHypixelMods.tabName(tab.getPlayerName(info));
    }

    public static int stringWidth(FontRenderer font, String text) {
        int width = font.getStringWidth(text);
        NetworkPlayerInfo row = current;
        current = null;
        if (row != null && get(row.getGameProfile().getId()) != null) width += SHIFT;
        return width;
    }

    public static int drawName(FontRenderer font, String text, float x, float y, int color) {
        NetworkPlayerInfo row = current;
        current = null;
        boolean shadow = ModuleTab.nameShadow();
        if (row == null) return font.drawString(text, x, y, color, shadow);
        Logo logo = get(row.getGameProfile().getId());
        if (logo != null && !ModuleTab.iconsRight()) {
            drawLogo(logo, x, y);
            x += SHIFT;
        }
        if (!ModuleTab.highlightOwn(row)) return font.drawString(text, x, y, color, shadow);
        String name = Minecraft.getMinecraft().thePlayer.getName();
        int at = text.indexOf(name);
        if (at < 0) return font.drawString(text, x, y, color, shadow);
        int alpha = color & 0xFF000000;
        String before = text.substring(0, at), after = text.substring(at + name.length());
        float end = font.drawString(before, x, y, color, shadow);
        end = font.drawString(name, end, y, ModuleTab.ownNameColor() & 0xFFFFFF | (alpha == 0 ? 0xFF000000 : alpha), shadow);
        return font.drawString(formatting(before) + after, end, y, color, shadow);
    }

    private static String formatting(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '§') continue;
            char c = Character.toLowerCase(s.charAt(i + 1));
            if ("0123456789abcdefr".indexOf(c) >= 0) out.setLength(0);
            if (c != 'r') out.append('§').append(c);
            i++;
        }
        return out.toString();
    }

    public static void drawLeftIcon(NetworkPlayerInfo row, float x, float y) {
        Logo logo = row == null || row.getGameProfile() == null ? null : get(row.getGameProfile().getId());
        if (logo != null) drawLogo(logo, x, y);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void drawLogo(Logo logo, float x, float y) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        ResourceLocation texture = scale <= 2 ? LOGO_16 : scale == 3 ? LOGO_24 : scale == 4 ? LOGO_32 : LOGO_100;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        quad(texture, x, y, ICON, logo.color);
        if (logo.plusColor != 0) quad(PLUS, x + 5, y + 1.5f, 3, logo.plusColor);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void quad(ResourceLocation texture, float x, float y, float size, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, y + size, 0).tex(0, 1).endVertex();
        wr.pos(x + size, y + size, 0).tex(1, 1).endVertex();
        wr.pos(x + size, y, 0).tex(1, 0).endVertex();
        wr.pos(x, y, 0).tex(0, 0).endVertex();
        tess.draw();
    }
}
