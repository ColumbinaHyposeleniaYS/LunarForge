package com.example.lunarforge.gui.locker;

import static com.example.lunarforge.gui.locker.LockerStyle.*;

import com.example.lunarforge.cosmetics.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

final class LockerView {
    static final float BASE_W = 1250, BASE_H = 669;

    static final float PAD = 112;

    enum Tab {
        COSMETICS("Cosmetics", "shirt.png"), EMOTES("Emotes", "emotes.png"), FITS("Fits", "fits.png"),
        SKIN_CHANGER("Skin Changer", "skin-changer.png"), LUNARPLUS("Lunar+", "logo.png");
        final String label, icon;
        Tab(String label, String icon) { this.label = label; this.icon = icon; }
    }

    static final String[][] SORTS = {
        {"alpha-asc", "Alphabetical (A-Z)", "A-Z"}, {"alpha-desc", "Alphabetical (Z-A)", "Z-A"}, {"equipped-first", "Equipped first", "EQ"},
        {"new-first", "New first", "NEW"}, {"acquired-recent", "Acquired (Most recent)", "ACQ"}, {"acquired-oldest", "Acquired (Oldest)", "ACQ"}};

    static final class Hit {
        final Rectangle2D.Float rect; final String action; final boolean overlay;
        Hit(float x, float y, float w, float h, String action, boolean overlay) { rect = new Rectangle2D.Float(x, y, w, h); this.action = action; this.overlay = overlay; }
    }

    static final class Slot {
        final float x, y, w, h; final Cosmetic cosmetic; final String kind;
        Slot(String kind, Cosmetic cosmetic, float x, float y, float w, float h) { this.kind = kind; this.cosmetic = cosmetic; this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    Tab tab = Tab.COSMETICS;
    CosmeticType subType;
    boolean cosmeticsOpen = true;
    String search = "";
    boolean searchFocused;
    String sort = "alpha-asc";
    boolean sortOpen, typeOpen, filterOpen, showMoreColors, showMoreTags;
    final Set<String> colorFilter = new LinkedHashSet<String>(), tagFilter = new LinkedHashSet<String>();
    boolean animatedFilter, scalableFilter;
    Cosmetic selected;
    com.example.lunarforge.cosmetics.emote.Emotes.Emote selectedEmote;
    float scroll, scrollTarget, sideScroll;
    boolean fullscreen;
    float winX, winY;

    float yaw, pitch, zoom = 1, panX, panY;
    boolean highlights = true, equippedList, itemOpen;
    String itemType = "AXE", itemMaterial = "DIAMOND";

    final List<Hit> hits = new ArrayList<Hit>();
    final List<Slot> slots = new ArrayList<Slot>();
    float w = BASE_W, h = BASE_H;
    Rectangle2D.Float gridClip = new Rectangle2D.Float();
    float maxScroll;
    int columns;

    private Graphics2D g;
    private float mx, my;
    private boolean overlayLayer;

    float gridX() { return 272; }
    float loadoutX() { return w - 16 - 350; }
    float gridW() { return loadoutX() - 4 - gridX(); }
    float gridY() { return 64; }
    float gridH() { return h - 64 - 16; }
    float sortX() { return gridX() + gridW() - 16 - 36 - 8 - 82; }

    private List<Cosmetic> listCache = Collections.emptyList();
    private String listKey = "";

    List<Cosmetic> list() {
        String key = tab + "|" + subType + "|" + search + "|" + sort + "|" + colorFilter + tagFilter + animatedFilter + scalableFilter + "|" + Loadout.revision;
        if (key.equals(listKey)) return listCache;
        listKey = key;
        if (tab != Tab.COSMETICS) return listCache = Collections.emptyList();
        List<Cosmetic> out = new ArrayList<Cosmetic>();
        String q = search.toLowerCase(Locale.ROOT);
        for (Cosmetic c : CosmeticCatalog.all()) {
            if (subType != null && !subType.covers(c.type)) continue;
            if (!q.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(q)) continue;

            boolean ok = true;
            for (String col : colorFilter) if (!c.colors.contains(col)) { ok = false; break; }
            for (String t : tagFilter) if (ok && !c.tags.contains(t)) { ok = false; break; }
            if (ok && animatedFilter && !c.animated) ok = false;
            if (ok && scalableFilter && !c.scalable) ok = false;
            if (ok) out.add(c);
        }
        Comparator<Cosmetic> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
        switch (sort) {
            case "alpha-desc": out.sort(byName.reversed()); break;
            case "equipped-first": out.sort((a, b) -> {
                boolean ea = Loadout.isEquipped(a), eb = Loadout.isEquipped(b);
                return ea == eb ? byName.compare(a, b) : ea ? -1 : 1;
            }); break;
            case "acquired-recent": out.sort((a, b) -> a.released.equals(b.released) ? byName.compare(a, b) : b.released.compareTo(a.released)); break;
            case "acquired-oldest": out.sort((a, b) -> a.released.equals(b.released) ? byName.compare(a, b) : a.released.compareTo(b.released)); break;
            default: out.sort(byName);
        }
        return listCache = out;
    }

    BufferedImage paint(float scale, float mouseX, float mouseY, boolean overlay) {
        mx = mouseX; my = mouseY; overlayLayer = overlay;
        if (!overlay) { hits.clear(); slots.clear(); }
        else hits.removeIf(hit -> hit.overlay);
        float pad = fullscreen ? 0 : PAD;
        BufferedImage img = new BufferedImage(Math.max(1, Math.round((w + pad * 2) * scale)), Math.max(1, Math.round((h + pad * 2) * scale)), BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(scale, scale);
        g.translate(pad, pad);
        if (overlay) popovers();
        else {
            window();
            sidebar();
            toolbar();
            if (tab == Tab.COSMETICS) grid(); else if (tab == Tab.EMOTES) emoteGrid(); else empty("Nothing here.. yet?", "This section isn't part of the Forge port yet.");
            loadout();
        }
        g.dispose(); g = null;
        return img;
    }

    boolean popoverOpen() { return sortOpen || typeOpen || filterOpen; }

    private void hit(float x, float y, float w, float h, String action) { hits.add(new Hit(x, y, w, h, action, overlayLayer)); }

    private boolean hover(float x, float y, float w, float h) {
        if (!overlayLayer && popoverOpen() && overPopover(mx, my)) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    boolean overPopover(float x, float y) {
        for (Hit hit : hits) if (hit.overlay && hit.action.startsWith("popover") && hit.rect.contains(x, y)) return true;
        return false;
    }

    private void window() {
        if (!fullscreen) {
            shadow(g, 0, 0, w, h, 12, 30, 60, -10, rgba(10, 10, 10, .5f));
        }
        box(g, 0, 0, w, h, fullscreen ? 0 : 12, WINDOW);

        if (!fullscreen) hit(0, 0, w, h, "drag");
    }

    private void sidebar() {
        icon(g, "logo.png", 16, 19.5f, 24, 24, 0, 1);
        text(g, "Locker", 48, 16, 700, 24, SPACE_12);

        float top = 63, listH = h - 16 - top;
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Float(16, top, 240, listH));
        float y = top - sideScroll;
        y = navGroup(Tab.COSMETICS, y, cosmeticsOpen, CosmeticType.roots());
        for (Tab t : new Tab[]{Tab.EMOTES, Tab.FITS, Tab.SKIN_CHANGER, Tab.LUNARPLUS}) y = navRow(t, null, 16, y + 4, 224, true) + 37;
        g.setClip(clip);
        sideMax = Math.max(0, y + sideScroll - top - listH);
        if (sideMax > 0) scrollbar(16 + 240 - 16, top, listH, sideScroll, sideMax, listH + sideMax, "sidebarScroll");
    }
    float sideMax;

    private float navGroup(Tab t, float y, boolean open, List<CosmeticType> children) {
        navRow(t, null, 16, y, 224, false);
        float x = 16 + 192;
        boolean hasChildren = t == Tab.COSMETICS;
        if (hasChildren) {
            java.awt.geom.AffineTransform old = g.getTransform();
            if (open) g.rotate(Math.PI, x + 10, y + 18.5f);
            icon(g, "caret.png", x, y + 8.5f, 20, 20, 0, .5f);
            g.setTransform(old);
            hit(x, y + 8.5f, 20, 20, "expand:" + t);
        }
        y += 37;
        if (!hasChildren) return y;
        y += 2;
        if (!open) return y;
        if (t == Tab.COSMETICS) {
            for (CosmeticType c : children) { navRow(Tab.COSMETICS, c, 24, y, 216, false); y += 39; }
            y -= 2;
        }
        return y;
    }

    private float navRow(Tab t, CosmeticType type, float x, float y, float rw, boolean last) {
        boolean selectedRow = tab == t && (type == null ? subType == null : subType == type) && !(t == Tab.COSMETICS && type == null && subType != null);
        boolean hov = hover(x, y, rw, 37);
        if (selectedRow) box(g, x, y, rw, 37, 8, ALPHA_3);
        else if (hov) box(g, x, y, rw, 37, 8, ALPHA_2);
        String icon = type != null ? "types/" + type.icon + ".png" : t.icon;
        icon(g, icon, x + 12, y + 8.5f, 20, 20, type == null && t != Tab.LUNARPLUS ? SPACE_12 : 0, .5f);
        String label = type != null ? type.displayName() : t.label;
        float lw = text(g, label, x + 40, y + 8, 500, 16, SPACE_11);
        int count = type != null ? CosmeticCatalog.count(type) : t == Tab.COSMETICS ? CosmeticCatalog.all().size()
            : t == Tab.EMOTES ? com.example.lunarforge.cosmetics.emote.Emotes.all().size() : 0;
        text(g, "(" + count + ")", x + 40 + lw + 4, y + 9.5f, 500, 14, SPACE_10);
        if (t == Tab.FITS) iconButton("plus-square.png", x + 196, y + 10.5f, 16, 16, SPACE_11, "newFit");
        hit(x, y, rw, 37, type != null ? "type:" + type.name() : "tab:" + t.name());
        return y;
    }

    private void toolbar() {
        float sx = sortX(), searchW = tab == Tab.EMOTES ? gridX() + gridW() - 16 - 274 : sx - 8 - 274;
        box(g, 274, 16, searchW, 36, 8, SPACE_3);

        boolean emotes = tab == Tab.EMOTES;
        String typeIcon = emotes ? "emotes.png" : subType == null ? "shirt.png" : "types/" + subType.icon + ".png";
        icon(g, typeIcon, 286, 28, 12, 12, subType == null || emotes ? SPACE_12 : 0, .5f);
        String active = emotes ? "All Emotes" : subType == null ? "All Cosmetics" : "All " + subType.displayName();
        float lw = text(g, active, 302, 28 - 1.5f, 500, 12, SPACE_12);
        icon(g, "caret.png", 302 + lw + 4, 28, 12, 12, SPACE_12, 1);
        if (!emotes) hit(274, 16, 302 + lw + 16 - 274, 36, "typePicker");
        float div = 302 + lw + 4 + 12 + 8;
        box(g, div, 28, 1, 12, 0, SPACE_5);
        float ix = div + 9, iw = 274 + searchW - 12 - ix;
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Float(ix, 21, iw, 28));
        float tw = text(g, search.isEmpty() ? "Search..." : search, ix, 28 - 1, 400, 12, search.isEmpty() ? SPACE_10 : SPACE_11);
        if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0) box(g, ix + (search.isEmpty() ? 0 : tw) + 1, 27, 1, 14, 0, SPACE_12);
        g.setClip(clip);
        hit(ix, 16, iw, 36, "search");

        if (emotes) return;

        boolean sh = hover(sx, 16, 82, 36) || sortOpen;
        box(g, sx, 16, 82, 36, 8, sh ? SPACE_4 : SPACE_3);
        String[] s = sort();
        icon(g, "sort-" + s[0] + ".png", sx + 12, 26, 16, 16, SPACE_11, 1);
        text(g, s[2], sx + 32, 27, 700, 12, SPACE_11);
        icon(g, "caret-small.png", sx + 58, 28, 12, 12, SPACE_11, .5f);
        hit(sx, 16, 82, 36, "sortMenu");

        float fx = sx + 82 + 8;
        boolean fh = hover(fx, 16, 36, 36) || filterOpen || !colorFilter.isEmpty() || !tagFilter.isEmpty() || animatedFilter || scalableFilter;
        box(g, fx, 16, 36, 36, 8, fh ? SPACE_4 : SPACE_3);
        icon(g, "filter.png", fx + 8, 24, 20, 20, SPACE_11, 1);
        hit(fx, 16, 36, 36, "filterMenu");
    }

    String[] sort() { for (String[] s : SORTS) if (s[0].equals(sort)) return s; return SORTS[0]; }

    private void grid() {
        List<Cosmetic> list = list();
        float gx = gridX() + 2, gy = gridY() + 2, contentW = gridW() - 16 - 4;
        columns = Math.max(1, (int)Math.floor((contentW + 10) / (185 + 10)));
        float tileW = (contentW - 10 * (columns - 1)) / columns, pitch = 235;
        int rows = (list.size() + columns - 1) / columns;
        float contentH = rows * pitch - 10 + 4;
        maxScroll = Math.max(0, contentH - gridH());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        gridClip = new Rectangle2D.Float(gridX(), gridY(), gridW(), gridH());
        if (list.isEmpty()) {
            if (!search.isEmpty()) empty("Couldn’t find ‘" + search + "’", "Are you sure you spelled it correctly?");
            else empty("You don't own any cosmetics that match your filters...", "");
            return;
        }
        Shape clip = g.getClip();
        g.clip(gridClip);
        int first = Math.max(0, (int)((scroll - 2) / pitch)), last = Math.min(rows - 1, (int)((scroll + gridH()) / pitch));
        for (int r = first; r <= last; r++)
            for (int c = 0; c < columns; c++) {
                int i = r * columns + c;
                if (i >= list.size()) break;
                tile(list.get(i), gx + c * (tileW + 10), gy + r * pitch - scroll, tileW);
            }
        g.setClip(clip);
        scrollbar(gridX() + gridW() - 16, gridY(), gridH(), scroll, maxScroll, contentH, "gridScroll");
    }

    private List<com.example.lunarforge.cosmetics.emote.Emotes.Emote> emoteList() {
        List<com.example.lunarforge.cosmetics.emote.Emotes.Emote> out = new ArrayList<com.example.lunarforge.cosmetics.emote.Emotes.Emote>();
        String q = search.toLowerCase(Locale.ROOT);
        for (com.example.lunarforge.cosmetics.emote.Emotes.Emote e : com.example.lunarforge.cosmetics.emote.Emotes.all())
            if (q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        return out;
    }

    private void emoteGrid() {
        List<com.example.lunarforge.cosmetics.emote.Emotes.Emote> list = emoteList();
        float gx = gridX() + 2, gy = gridY() + 2, contentW = gridW() - 16 - 4;
        columns = Math.max(1, (int)Math.floor((contentW + 10) / (185 + 10)));
        float tileW = (contentW - 10 * (columns - 1)) / columns, pitch = 235;
        int rows = (list.size() + columns - 1) / columns;
        float contentH = rows * pitch - 10 + 4;
        maxScroll = Math.max(0, contentH - gridH());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        gridClip = new Rectangle2D.Float(gridX(), gridY(), gridW(), gridH());
        if (list.isEmpty()) { empty("Couldn’t find ‘" + search + "’", "Are you sure you spelled it correctly?"); return; }
        Shape clip = g.getClip();
        g.clip(gridClip);
        int first = Math.max(0, (int)((scroll - 2) / pitch)), last = Math.min(rows - 1, (int)((scroll + gridH()) / pitch));
        for (int r = first; r <= last; r++)
            for (int c = 0; c < columns; c++) {
                int i = r * columns + c;
                if (i >= list.size()) break;
                emoteTile(list.get(i), gx + c * (tileW + 10), gy + r * pitch - scroll, tileW);
            }
        g.setClip(clip);
        scrollbar(gridX() + gridW() - 16, gridY(), gridH(), scroll, maxScroll, contentH, "gridScroll");
    }

    private void emoteTile(com.example.lunarforge.cosmetics.emote.Emotes.Emote e, float x, float y, float tw) {
        boolean equipped = com.example.lunarforge.cosmetics.emote.Emotes.isEquipped(e), sel = selectedEmote != null && selectedEmote.id == e.id;
        boolean hov = hover(x, y, tw, 176) && gridClip.contains(mx, my);
        box(g, x, y, tw, 176, 8, equipped ? GREEN_A3 : sel || hov ? ALPHA_5 : ALPHA_3);
        if (sel || hov) outline(g, x, y, tw, 176, 8, 2, equipped ? GREEN_A6 : ALPHA_9);
        if (e.hasIcon()) slots.add(new Slot("emote:" + e.id, null, x + (tw - 112) / 2, y + 24, 112, 112));
        else icon(g, "emotes.png", x + tw / 2 - 24, y + 64, 48, 48, SPACE_11, .25f);
        if (equipped) {
            box(g, x, y + 152, tw, 24, 8, EQUIPPED_BAR, false);
            float lw = width(g, "Equipped", 500, 12), cx = x + (tw - 15 - 4 - lw) / 2;
            icon(g, "check.png", cx, y + 156.5f, 15, 15, 0, 1);
            text(g, "Equipped", cx + 19, y + 156, 500, 12, GREEN_11);
        }
        String name = ellipsize(g, e.name, 500, 14, tw * .95f);
        float nw = width(g, name, 500, 14);
        text(g, name, x + (tw - nw) / 2, y + 184, 500, 14, ALPHA_12);
        hit(x, Math.max(y, gridY()), tw, Math.min(y + 176, gridY() + gridH()) - Math.max(y, gridY()), "emote:" + e.id);
    }

    private void tile(Cosmetic c, float x, float y, float tw) {
        boolean equipped = Loadout.isEquipped(c), sel = selected != null && selected.id == c.id;
        boolean hov = hover(x, y, tw, 176) && gridClip.contains(mx, my);
        int bg = equipped ? GREEN_A3 : sel || hov ? ALPHA_5 : ALPHA_3;
        box(g, x, y, tw, 176, 8, bg);
        if (sel || hov) outline(g, x, y, tw, 176, 8, 2, equipped ? GREEN_A6 : ALPHA_9);

        slots.add(new Slot("tile", c, x + (tw - 176) / 2, y, 176, 176));

        if (c.type.display == null && !c.renderable()) icon(g, "types/" + c.type.icon + ".png", x + tw / 2 - 24, y + 64, 48, 48, 0, .25f);
        if (equipped) {
            icon(g, "outfit.png", x + 8, y + 8, 16, 16, GREEN_11, .6f);
            box(g, x, y + 152, tw, 24, 8, EQUIPPED_BAR, false);
            float lw = width(g, "Equipped", 500, 12), cx = x + (tw - 15 - 4 - lw) / 2;
            icon(g, "check.png", cx, y + 156.5f, 15, 15, 0, 1);
            text(g, "Equipped", cx + 19, y + 156, 500, 12, GREEN_11);
        }
        String name = ellipsize(g, c.name, 500, 14, tw * .95f);
        float nw = width(g, name, 500, 14);
        text(g, name, x + (tw - nw) / 2, y + 184, 500, 14, ALPHA_12);
        hit(x, Math.max(y, gridY()), tw, Math.min(y + 176, gridY() + gridH()) - Math.max(y, gridY()), "tile:" + c.id);
    }

    private void empty(String title, String sub) {
        float cx = gridX() + gridW() / 2, cy = gridY() + gridH() / 2 + 60;
        float tw = width(g, title, 700, 18);
        text(g, title, cx - tw / 2, cy, 700, 18, SPACE_12);
        if (!sub.isEmpty()) { float sw = width(g, sub, 500, 14); text(g, sub, cx - sw / 2, cy + 32, 500, 14, SPACE_11); }
    }

    private void scrollbar(float x, float y, float trackH, float offset, float max, float contentH, String action) {
        if (max <= 0) return;
        float thumbH = Math.max(36, trackH * trackH / contentH), thumbY = y + (trackH - thumbH) * offset / max;
        boolean hov = hover(x, y, 16, trackH);
        box(g, x + 5, thumbY + 5, 6, thumbH - 10, 3, hov ? ALPHA_5 : ALPHA_3);
        hit(x, y, 16, trackH, action);
    }

    private void loadout() {
        float lx = loadoutX();
        slots.add(new Slot("face", null, lx, 22, 20, 20));
        text(g, "Your Loadout", lx + 28, 20, 500, 20, SPACE_11);
        iconButton("close.png", w - 40, 20, 24, 24, SPACE_11, "close");

        box(g, lx, 60, 350, 16, 8, SPACE_3, true);
        iconButton("swap.png", lx + 310, 58, 20, 20, SPACE_10, "swapOutfit");
        float bodyH = h - 76 - 16 - 44 - 16;
        box(g, lx, 76, 350, bodyH, 8, SPACE_2, false);
        slots.add(new Slot("loadout", null, lx, 76, 350, bodyH));
        hit(lx, 76, 350, bodyH, "loadoutModel");

        List<Cosmetic> worn = Loadout.equipped();
        float tx = lx + 290, ty = 96, listH = equippedList ? worn.size() * 36 : 0;
        box(g, tx, ty, 40, 74 + listH, 8, SPACE_3);
        boolean hh = hover(tx + 4, ty + 4, 32, 32);
        if (highlights || hh) box(g, tx + 4, ty + 4, 32, 32, 8, highlights ? ALPHA_3 : ALPHA_2);
        icon(g, "eye-off.png", tx + 12, ty + 12, 16, 16, SPACE_11, 1);
        hit(tx + 4, ty + 4, 32, 32, "highlights");
        boolean lh = hover(tx + 4, ty + 38, 32, 32);
        if (equippedList || lh) box(g, tx + 4, ty + 38, 32, 32, 8, equippedList ? ALPHA_5 : ALPHA_2);
        icon(g, "shirt.png", tx + 13, ty + 47, 14, 14, SPACE_12, .6f);
        hit(tx + 4, ty + 38, 32, 32, "equippedList");
        if (equippedList) {
            float ey = ty + 74;
            for (Cosmetic c : worn) {
                boolean sel = selected != null && selected.id == c.id;
                if (sel || hover(tx + 4, ey, 32, 32)) box(g, tx + 4, ey, 32, 32, 8, sel ? ALPHA_5 : ALPHA_2);
                icon(g, "types/" + c.type.icon + ".png", tx + 10, ey + 6, 20, 20, 0, .8f);
                hit(tx + 4, ey, 32, 32, "equippedPick:" + c.id);
                ey += 36;
            }
        }

        float ix = lx + 20, iy = 96;
        box(g, ix, iy, 40, 32, 8, SPACE_3);
        slots.add(new Slot("item:" + itemMaterial + ":" + itemType, null, ix + 10, iy + 6, 20, 20));
        hit(ix, iy, 40, 32, "itemPicker");
        if (itemOpen) {
            String[] materials = {"WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND"};
            float my2 = iy + 40;
            box(g, ix, my2, 40, materials.length * 28 + 8, 8, SPACE_3);
            for (int i = 0; i < materials.length; i++) {
                float ry = my2 + 4 + i * 28;
                if (materials[i].equals(itemMaterial)) box(g, ix + 4, ry + 2, 32, 24, 8, ALPHA_5);
                slots.add(new Slot("item:" + materials[i] + ":" + itemType, null, ix + 10, ry + 4, 20, 20));
                hit(ix, ry, 40, 28, "material:" + materials[i]);
            }
            String[] types = {"SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE"};
            float rx = ix + 48;
            box(g, rx, iy, types.length * 36 + 36, 32, 8, SPACE_3);
            for (int i = 0; i < types.length; i++) {
                float cx = rx + 4 + i * 36;
                if (types[i].equals(itemType)) box(g, cx + 2, iy + 4, 32, 24, 8, ALPHA_5);
                slots.add(new Slot("item:" + itemMaterial + ":" + types[i], null, cx + 8, iy + 6, 20, 20));
                hit(cx, iy, 36, 32, "itemType:" + types[i]);
            }
            iconButton("x-square.png", rx + 4 + types.length * 36 + 6, iy + 8, 16, 16, SPACE_11, "itemNone");
        }

        boolean emoteTab = tab == Tab.EMOTES;
        String selName = emoteTab ? (selectedEmote == null ? null : selectedEmote.name) : selected == null ? null : selected.name;
        if (selName != null) {
            boolean worn2 = emoteTab ? com.example.lunarforge.cosmetics.emote.Emotes.isEquipped(selectedEmote) : Loadout.isEquipped(selected);
            float nameTop = 76 + bodyH - 20 - 21;
            float nw = width(g, selName, 700, 16);
            text(g, selName, lx + (350 - nw) / 2, nameTop, 700, 16, SPACE_12);
            if (!worn2) { float pw = width(g, "Previewing", 500, 12); text(g, "Previewing", lx + (350 - pw) / 2, nameTop - 18, 500, 12, SPACE_10); }
        }

        float fy = h - 16 - 44;
        if (selName == null) {
            box(g, lx, fy, 350, 42, 8, SPACE_2);
            String hint = emoteTab ? "Select an emote to preview it" : "Select a cosmetic to preview it";
            float tw = width(g, hint, 450, 13), cx = lx + (350 - 16 - 10 - tw) / 2;
            icon(g, "info.png", cx, fy + 13, 16, 16, SPACE_11, 1);
            text(g, hint, cx + 26, fy + 13.5f - 1, 450, 13, SPACE_10);
        } else {
            boolean on = emoteTab ? com.example.lunarforge.cosmetics.emote.Emotes.isEquipped(selectedEmote) : Loadout.isEquipped(selected), eh = hover(lx, fy, 350, 42);
            box(g, lx, fy, 350, 42, 8, on ? (eh ? rgba(253, 0, 0, .27f) : RED_A) : (eh ? rgba(3, 253, 21, .18f) : GREEN_A3));
            String label = on ? "Unequip" : "Equip";
            float lw = width(g, label, 500, 16), cx = lx + (350 - 16 - 8 - lw) / 2;
            icon(g, on ? "unequip.png" : "equip.png", cx, fy + 13, 16, 16, on ? RED_11 : GREEN_11, 1);
            text(g, label, cx + 24, fy + 11.5f, 500, 16, on ? RED_11 : GREEN_11);
            hit(lx, fy, 350, 42, "toggleSelected");
        }
    }

    private void iconButton(String icon, float x, float y, float w, float h, int color, String action) {
        boolean hov = hover(x - 4, y - 4, w + 8, h + 8);
        icon(g, icon, x, y, w, h, hov ? SPACE_12 : color, 1);
        hit(x - 4, y - 4, w + 8, h + 8, action);
    }

    private void popovers() {
        if (typeOpen) typeMenu();
        if (sortOpen) sortMenu();
        if (filterOpen) filterMenu();
    }

    private void menuShadow(float x, float y, float w, float h) {
        shadow(g, x, y, w, h, 8, 8, 24, 0, rgba(10, 10, 10, .3f));
        shadow(g, x, y, w, h, 8, 2, 8, 0, rgba(10, 10, 10, .15f));
    }

    private void typeMenu() {
        List<Object[]> rows = new ArrayList<Object[]>();
        CosmeticType scope = subType == null ? null : root(subType);
        rows.add(new Object[]{scope, scope == null ? "All Cosmetics" : "All " + scope.displayName()});
        List<CosmeticType> groups = scope == null ? CosmeticType.roots() : scope.children();
        for (CosmeticType t : groups) {
            rows.add(null);
            rows.add(new Object[]{t, t.children().isEmpty() ? t.displayName() : "All " + t.displayName()});
            for (CosmeticType c : t.children()) rows.add(new Object[]{c, c.displayName()});
        }
        float x = 274, y = 56, mw = 200, mh = 16;
        for (Object[] r : rows) mh += r == null ? 8 : 28;
        menuShadow(x, y, mw, mh);
        box(g, x, y, mw, mh, 8, SPACE_3);
        hit(x, y, mw, mh, "popover");
        float ry = y + 18;
        for (Object[] r : rows) {
            if (r == null) { box(g, x + 16, ry - 1, 152, 1, 0, SPACE_5); ry += 8; continue; }
            CosmeticType t = (CosmeticType)r[0];
            boolean cur = t == subType, hov = hover(x + 8, ry - 5, mw - 16, 28);
            if (hov) box(g, x + 8, ry - 5, mw - 16, 28, 8, SPACE_4);
            icon(g, t == null ? "shirt.png" : "types/" + t.icon + ".png", x + 16, ry, 16, 16, t == null ? SPACE_12 : 0, cur ? 1 : .6f);
            text(g, (String)r[1], x + 40, ry - 1, 450, 14, cur ? SPACE_12 : SPACE_11);
            hit(x + 8, ry - 5, mw - 16, 28, "pickType:" + (t == null ? "ALL" : t.name()));
            ry += 28;
        }
    }

    private static CosmeticType root(CosmeticType t) { while (t.parent != null) t = t.parent; return t; }

    private void sortMenu() {
        float x = sortX(), y = 16 + 36 + 5;
        float mw = 225, mh = 8 + SORTS.length * 38 - 4 + 8;
        menuShadow(x, y, mw, mh);
        box(g, x, y, mw, mh, 8, SPACE_3);
        hit(x, y, mw, mh, "popover");
        for (int i = 0; i < SORTS.length; i++) {
            float ry = y + 8 + i * 38;
            boolean cur = SORTS[i][0].equals(sort), hov = hover(x + 8, ry, mw - 16, 34);
            if (cur) box(g, x + 8, ry, mw - 16, 34, 8, SPACE_5);
            else if (hov) box(g, x + 8, ry, mw - 16, 34, 8, SPACE_4);
            icon(g, "sort-" + SORTS[i][0] + ".png", x + 16, ry + 9, 16, 16, cur ? SPACE_12 : SPACE_11, 1);
            text(g, SORTS[i][1], x + 36, ry + 8, 500, 14, cur ? SPACE_12 : SPACE_11);
            hit(x + 8, ry, mw - 16, 34, "pickSort:" + SORTS[i][0]);
        }
    }

    private void filterMenu() {
        Set<String> colors = new TreeSet<String>(), tags = new TreeSet<String>();
        for (Cosmetic c : CosmeticCatalog.all()) { colors.addAll(c.colors); tags.addAll(c.tags); }
        List<String> cl = new ArrayList<String>(colors), tl = new ArrayList<String>(tags);
        int shownColors = showMoreColors ? cl.size() : Math.min(4, cl.size());
        int tagRows = showMoreTags ? (tl.size() + 1) / 2 : Math.min(4, (tl.size() + 1) / 2);
        int rowsTop = Math.max(shownColors, tagRows);
        float x = sortX() + 82 + 8, y = 57.6f, mw = 408;
        float typesTop = y + 49 + rowsTop * 28 + 36 + 2;
        float mh = typesTop - y + 33 + 16 + 20;
        menuShadow(x, y, mw, mh);
        box(g, x, y, mw, mh, 8, SPACE_3);
        hit(x, y, mw, mh, "popover");
        text(g, "Colors", x + 16, y + 16, 500, 16, SPACE_12);
        for (int i = 0; i < shownColors; i++) {
            String col = cl.get(i);
            float ry = y + 49 + i * 28;
            checkbox(x + 16, ry, colorFilter.contains(col));
            color(g, swatch(col));
            g.fill(new java.awt.geom.Ellipse2D.Float(x + 44, ry + 5, 10, 10));
            text(g, col.toLowerCase(Locale.ROOT), x + 62, ry + 1, 500, 14, SPACE_12);
            hit(x + 16, ry, 120, 20, "filterColor:" + col);
        }
        showMore(x + 16, y + 49 + rowsTop * 28 + 4, showMoreColors, "moreColors");
        text(g, "Tags", x + 152, y + 16, 500, 16, SPACE_12);
        for (int i = 0; i < tagRows * 2 && i < tl.size(); i++) {
            String tag = tl.get(i);
            float rx = x + 152 + (i % 2) * 130, ry = y + 49 + (i / 2) * 28;
            checkbox(rx, ry, tagFilter.contains(tag));
            text(g, tag.toLowerCase(Locale.ROOT).replace('_', ' '), rx + 28, ry + 1, 500, 14, SPACE_12);
            hit(rx, ry, 120, 20, "filterTag:" + tag);
        }
        showMore(x + 152, y + 49 + rowsTop * 28 + 4, showMoreTags, "moreTags");
        text(g, "Types", x + 16, typesTop, 500, 16, SPACE_12);
        float ty = typesTop + 33;
        checkbox(x + 16, ty, animatedFilter);
        icon(g, "animated.png", x + 44, ty + 4, 12, 12, SPACE_11, 1);
        text(g, "animated", x + 64, ty + 1, 500, 14, SPACE_12);
        hit(x + 16, ty, 110, 20, "filterAnimated");
        checkbox(x + 148, ty, scalableFilter);
        icon(g, "scalable.png", x + 176, ty + 4, 12, 12, SPACE_11, 1);
        text(g, "scalable", x + 196, ty + 1, 500, 14, SPACE_12);
        hit(x + 148, ty, 110, 20, "filterScalable");
    }

    private void checkbox(float x, float y, boolean on) {
        box(g, x, y, 20, 20, 4, on ? 0xFFC345FF : SPACE_2);
        if (on) {
            color(g, 0xFFFFFFFF);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Float(x + 5.5f, y + 10.5f, x + 8.5f, y + 13.5f));
            g.draw(new java.awt.geom.Line2D.Float(x + 8.5f, y + 13.5f, x + 14.5f, y + 6.5f));
            g.setStroke(new BasicStroke(1));
        }
    }

    private void showMore(float x, float y, boolean open, String action) {
        java.awt.geom.AffineTransform old = g.getTransform();
        if (open) g.rotate(Math.PI, x + 7, y + 7);
        icon(g, "caret-small.png", x, y, 14, 14, SPACE_11, 1);
        g.setTransform(old);
        text(g, open ? "Show less" : "Show more", x + 18, y - 1, 500, 14, SPACE_11);
        hit(x, y - 2, 100, 18, action);
    }

    private static int swatch(String name) {
        switch (name) {
            case "RED": return 0xFFFF0000; case "ORANGE": return 0xFFFFA500; case "YELLOW": return 0xFFFFFF00; case "GREEN": return 0xFF008000;
            case "BLUE": return 0xFF0000FF; case "PURPLE": return 0xFF800080; case "PINK": return 0xFFFFC0CB; case "BLACK": return 0xFF000000;
            case "WHITE": return 0xFFFFFFFF; case "GRAY": case "GREY": return 0xFF808080; case "BROWN": return 0xFFA52A2A; case "CYAN": return 0xFF00FFFF;
            case "GOLD": return 0xFFFFD700; case "SILVER": return 0xFFC0C0C0;
            default: return 0xFF888888;
        }
    }
}
