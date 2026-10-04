package com.example.lunarforge.gui.ui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.awt.geom.Area;
import javax.imageio.ImageIO;

public final class UiRenderer implements MenuCanvas {
    public static final int WIDTH = 492, HEIGHT = 303;

    public static final int PAD = 2;
    private static final int WHITE = 0xFFFFFFFF;
    private final Font bold, light, medium;
    private final Map<String, BufferedImage> images = new HashMap<String, BufferedImage>();
    public final List<Hit> hits = new ArrayList<Hit>();
    public float maxScroll, contentHeight;

    public final List<MenuWidget> shownWidgets = new ArrayList<MenuWidget>();

    public boolean animating;
    private final Map<String, long[]> fades = new HashMap<String, long[]>(), slides = new HashMap<String, long[]>();
    private String builtPage = null;
    public float scrollTop = 62.5f, scrollHeight = 235;
    private Graphics2D g;
    private UiModel model;
    private float mx, my;

    private boolean suppressHover;
    private boolean blink;

    public static final class Hit {
        public final Rectangle2D.Float rect;
        public final String action, tooltip;
        Hit(float x, float y, float w, float h, String action, String tooltip) {
            rect = new Rectangle2D.Float(x, y, w, h); this.action = action; this.tooltip = tooltip;
        }
    }
    public UiRenderer() {
        bold = font("roboto-bold.ttf"); light = font("roboto-light.ttf"); medium = font("roboto-medium.ttf");
    }
    private Font font(String file) {
        try (InputStream stream = resource("fonts/" + file)) { return Font.createFont(Font.TRUETYPE_FONT, stream); }
        catch (Exception e) { throw new IllegalStateException("Missing original Lunar font: " + file, e); }
    }
    private InputStream resource(String path) { return UiRenderer.class.getResourceAsStream("/assets/lunarforge/ui/" + path); }
    public BufferedImage render(UiModel state, float mouseX, float mouseY, int rasterScale, boolean cursor) {
        this.rasterScale = rasterScale; model = state; mx = mouseX; my = mouseY; blink = cursor; hits.clear(); maxScroll = 0; contentHeight = 0; animating = false;
        shownWidgets.clear();
        scrollTop = 62.5f; scrollHeight = 235;
        BufferedImage image = new BufferedImage((WIDTH + PAD * 2) * rasterScale, (HEIGHT + PAD * 2) * rasterScale, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        g.scale(rasterScale, rasterScale);
        g.translate(PAD, PAD);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        String page = model.tab + "|" + model.selected + "|" + model.settingsTab;
        if (!page.equals(builtPage)) { slides.clear(); knobs.clear(); builtPage = page; }
        suppressHover = !model.keybindPopup.isEmpty();
        if (!model.editor.isEmpty()) {
            if (!model.confirm.isEmpty()) confirmDialog(); else profileEditor();
        } else {
            panel();
            if (!model.selected.isEmpty()) options();
            else if (model.tab.equals("MODS")) mods();
            else settings();
        }

        g.dispose(); g = null;
        return image;
    }
    private void panel() {
        ring(-1, 0, 494, 303, 4, 0x601E1E1E);
        ring(0, 1, 492, 301, 3.25f, 0x401E1E1E);
        fill(0, 1, 492, 301, 5, 0xB21A1818);
        fill(0, 1, 492, 32, 5, 0x36000000, true, true, false, false);
        rect(0, 32.5f, 492, .5f, 0x20FFFFFF);
        fill(0, 283, 105, 19, 7, 0x20000000, false, true, true, false);
        fill(0, 42, 105, 233, 8, 0x20000000, false, true, false, true);
        image("logo/logo-branding-light-430x66.png", 9, 8, 107.5f, 16.5f, 1);
        String[] tabs = {"MODS", "SETTINGS"};
        float total = 0;
        for (String s : tabs) total += width(spaced(lang(s)), bold, 7) + 16 + 8;
        float x = WIDTH / 2f - (total - 8) / 2;
        for (String s : tabs) {
            float w = width(spaced(lang(s)), bold, 7) + 16;
            button(x, 8, w, 16, spaced(lang(s)), "tab:" + s, model.tab.equals(s), 0, ""); x += w + 8;
        }

        boolean closeOver = hover(468, 8, 16, 16);
        body(468, 8, 16, 16, 4, 0x40252525, 0x20FFFFFF, fadeColor("close", closeOver, 0x20FFFFFF, 0x50FFFFFF));
        tinted("icons/mainmenu/exit-17x17.png", 472, 12, 8, 8, fadeColor("closeIcon", closeOver, 0x50FFFFFF, 0xAFFFFFFF));
        hit(468, 8, 16, 16, "close", "Close");
        int row = 0;
        for (String profile : model.profiles) {
            float f = 43 + row * 16;
            boolean first = row++ == 0, active = profile.equals(model.profile());
            if (active) fill(0, f - 1, 105, 16, 8, 0x20FFFFFF, false, first, false, false);
            String display = model.displayName(profile);
            ring(0, f, 104, 14, 5, 0x20FFFFFF, false, first, false, false);
            boolean pencil = hover(95, f, 9, 14);
            image("icons/pencil-64.png", 95, f + 3.5f, 7, 7, (pencil ? 0x5C : 0x33) / 255f);

            String icon = model.profileIcon(profile);
            float nameX = 8;
            if (!icon.isEmpty()) { image("icons/profiles/" + icon + ".png", 2, f + 3.5f, 8, 8, 0xA6 / 255f); nameX += 6; }
            text(fit(spaced(display) + " ", bold, 6, 93 - nameX), bold, 6, nameX, f + 3.5f, 0xAFFFFFFF);
            hit(0, f - 1, 95, 16, "profile:" + profile, "");
            hit(95, f, 9, 14, "editProfile:" + profile, "");
        }

        boolean over = hover(8, 287.5f, 88, 11);
        body(8, 287.5f, 88, 11, 4, 0x60A2A2A2, 0x60A2A2A2, fadeColor("editHud", over, 0xFF5788D2, 0xFF4F94FC));

        float label = 287.5f + 5.5f - fontHeight(bold, 5);
        String editHud = spaced(LunarLang.get("gui.components", "editHudLayout"));
        centered(editHud, bold, 5, 53, label, 0x20000000);
        centered(editHud, bold, 5, 52, label - 1, WHITE);
        hit(8, 287.5f, 88, 11, "editHud", "Drag and resize your enabled HUD modules");
    }
    private void mods() {
        float x = 110;
        String[] categories = {"ALL", "HUD", "SERVER", "MECHANIC", "LEGIT", "COMBAT", "WORLD"};
        // seven tabs no longer fit with letterspacing + wide padding; fall back to plain labels when needed
        float limit = 485f - 44f - 110f;
        float spacedTotal = 0f, plainTotal = 0f;
        for (String category : categories) {
            spacedTotal += width(spaced(lang(category)), bold, 7) + 12;
            plainTotal += width(lang(category), bold, 7) + 12;
        }
        boolean useSpaced = spacedTotal + (categories.length - 1) * 4 <= limit;
        for (String category : categories) {
            String label = useSpaced ? spaced(lang(category)) : lang(category);
            float w = width(label, bold, 7) + 12;
            button(x, 43.5f, w, 14, label, "category:" + category, model.category.equals(category), 1, ""); x += w + 4;
        }
        iconButton(x, 43.5f, 14, 14, model.compact() ? "icons/large-menu-24x24.png" : "icons/compact-menu-24x24.png", "compact", ""); x += 16;
        iconButton(x, 43.5f, 14, 14, "icons/sort_icons/" + UiModel.SORTS[model.sort()] + ".png", "sort", ""); x += 16;
        search(x, 43.5f, 485 - x, 14);
        List<ModuleCatalog.Module> modules = model.visibleModules();

        float cardHeight = model.compact() ? 22 : 112;
        int lastRow = Math.max(0, (modules.size() - 1) / 3);
        LunarScroller sc = model.scroller;
        sc.height = 235; sc.content = lastRow == 0 ? 115 + 4 : 4 + cardHeight + (cardHeight + 8) * lastRow;
        sc.clamp();
        scrollTop = 62.5f; scrollHeight = 235; contentHeight = sc.content; maxScroll = Math.max(0, sc.content - sc.height);
        Shape old = g.getClip(); g.clip(new Rectangle2D.Float(108, 62.5f, 381, 240));
        for (int i = 0; i < modules.size(); i++) {
            float cx = 110 + (i % 3) * 123, cy = 64.5f + (i / 3) * (cardHeight + 8) - sc.offset;
            if (cy + cardHeight < 62.5f || cy > 297.5f) continue;
            if (model.compact()) compactCard(modules.get(i), cx, cy); else card(modules.get(i), cx, cy);
        }
        g.setClip(old);
        scrollbar(481);
    }

    private static String lang(String constant) { return LunarLang.get("gui.components", constant.toLowerCase(Locale.ROOT)).toUpperCase(Locale.ROOT); }
    private void card(ModuleCatalog.Module m, float x, float y) {
        boolean over = hover(x, y, 115, 112) && my >= 62.5f && my < 302.5f;

        fill(x, y, 115, 112, 7, fadeColor("card:" + m.id, over, 0x20575757, 0x50676767));
        ring(x, y, 115, 112, 6, 0x20E2E2E2);
        image("icons/features/" + m.icon + "-52x52.png", x + 44.5f, y + 16, 26, 26, 0xD9 / 255f);
        centered(LunarLang.featureName(m.id, m.name), light, 11, x + 57.5f, y + 53, 0xFF7E7D7D);

        boolean options = hover(x, y + 72, 115, 20) && over;
        rect(x, y + 72, 115, 20, fadeColor("options:" + m.id, options, 0x20FFFFFF, 0x50FFFFFF));
        rect(x, y + 73, 1, 18, 0x20FFFFFF); rect(x + 114, y + 73, 1, 18, 0x20FFFFFF); rect(x + 94, y + 73, 1, 18, 0x20FFFFFF);
        rect(x, y + 72, 115, 1, 0x20FFFFFF); rect(x, y + 91, 115, 1, 0x20FFFFFF);
        image("icons/mainmenu/cog-20x20.png", x + 100, y + 77.5f, 9, 9, 1);
        centered(spaced(lang("options")), bold, 7, x + 47, y + 78, WHITE);
        hitClip(x, y + 72, 115, 20, "options:" + m.id, "Options");

        boolean enabled = model.enabled(m), toggle = hover(x, y + 92, 115, 20) && over;
        fill(x, y + 92, 115, 20, 10, enabled ? fadeColor("toggle:" + m.id, toggle, 0xAF29D67A, 0xFF29D67A)
            : fadeColor("toggle:" + m.id, toggle, 0xAFDE2152, 0xFFDE2152), false, false, true, true);
        ring(x + 1, y + 93, 113, 18, 5, 0x35FFFFFF, false, false, true, true);
        centered(spaced(lang(enabled ? "enabled" : "disabled")), bold, 7, x + 57.5f, y + 98, WHITE);
        hitClip(x, y + 92, 115, 20, "toggle:" + m.id, m.implemented ? "Toggle " + m.name : "UI preview: this gameplay module is not ported yet");

        if (over || model.favorite(m)) {
            boolean star = hover(x + 3, y + 3, 11, 11);
            int tint = model.favorite(m) ? (star ? 0xFFFFDF38 : 0xFFFFC842) : (star ? WHITE : 0xFFB1B3C0);
            tinted("icons/" + (model.favorite(m) ? "star-filled-64x64.png" : "star-64x64.png"), x + 4, y + 4, 9, 9, tint);
            hitClip(x + 3, y + 3, 11, 11, "favorite:" + m.id, model.favorite(m) ? "Remove favorite" : "Favorite");
        }
    }
    private void compactCard(ModuleCatalog.Module m, float x, float y) {
        boolean over = hover(x, y, 115, 22) && my >= 62.5f && my < 302.5f;
        rect(x, y, 115, 22, fadeColor("card:" + m.id, over, 0x20575757, 0x50676767));
        ring(x, y, 115, 22, 3, model.enabled(m) ? fadeColor("ring:" + m.id, over, 0x7029D67A, 0xB029D67A)
            : fadeColor("ring:" + m.id, over, 0x70DE2152, 0xB0DE2152));
        image("icons/features/" + m.icon + "-20x20.png", x + 6, y + 6, 10, 10, (lerp(0x60, 0xAF, fade("icon:" + m.id, over)) & 0xFF) / 255f);
        text(LunarLang.featureName(m.id, m.name), light, 7, x + 20, y + 7, 0xFFB5B5B5);
        hitClip(x, y, 93, 22, "toggle:" + m.id, m.implemented ? "Toggle " + m.name : "UI preview: gameplay not ported");
        boolean options = hover(x + 93, y, 22, 22) && over;
        image("icons/mainmenu/cog-20x20.png", x + 99, y + 6, 9, 9, (lerp(0xA0, 0xFF, fade("options:" + m.id, options)) & 0xFF) / 255f);
        rect(x + 93, y + 6, .5f, 10, 0x1FFFFFFF);
        hitClip(x + 93, y, 22, 22, "options:" + m.id, "Options");
    }

    private static final float PX = 110, PY = 42.5f, PW = 377, PH = 255;

    private float clipTop = 62.5f;

    private void options() {
        ModuleCatalog.Module m = null;
        for (ModuleCatalog.Module c : ModuleCatalog.ALL) if (c.id.equals(model.selected)) m = c;
        if (m == null) { model.selected = ""; return; }
        OptionCatalog.Page page = OptionCatalog.page(m.id);

        fill(PX, PY, PW, 25, 8, 0x35000000, true, true, false, false);
        fill(PX, PY + 25, PW, PH - 25, 12, 0x20000000, false, false, true, true);
        rect(PX, PY + 24, PW, 1, 0x20FFFFFF);
        text(spaced(LunarLang.featureName(m.id, m.name).toUpperCase(Locale.ROOT)), bold, 9, PX + 30, PY + 7, WHITE);
        image("icons/settings/arrow-left-32x32.png", PX + 7, PY + 4, 16, 16, 1);
        hit(PX + 4, PY + 3, 21, 18, "back", "");

        float bx = PX + PW - 4 - 18;
        iconButton(bx, PY + 6, 14, 14, "icons/reset-settings-24x24.png", "resetModule", "");
        if (page.hud) { bx -= 18; iconButton(bx, PY + 6, 14, 14, "icons/reset-position-24x24.png", "resetPosition", ""); }
        if (page.keybind) { bx -= 18; iconButton(bx, PY + 6, 14, 14, "icons/keybind-24x24.png", "keybindPopup:" + m.id, ""); }
        search(bx - 4 - 100, PY + 6, 100, 14);

        float descH = 0;
        String description = LunarLang.featureDescription(m.id);
        if (description != null) {
            List<String> lines = wrap(description, light, 6, PW - 12);
            for (int i = 0; i < lines.size(); i++) text(lines.get(i), light, 6, PX + 6, PY + 28 + i * 8 + .5f, WHITE);
            descH = lines.size() * 8 + 8;
            rect(PX, PY + 24, PW, descH, 0x15000000);
            rect(PX, PY + 25 + lines.size() * 8 + 8, PW, 1, 0x20FFFFFF);
        }
        float top = PY + 27 + descH;
        scrollTop = top; scrollHeight = PH - 40 - descH;
        List<OptionCatalog.Node> nodes = filtered(page.options, m.id);
        float content = container(nodes, m.id, PX, top, PW - 30, 0, 0, true, false) - top + 4;
        contentHeight = content;
        maxScroll = Math.max(0, content - scrollHeight);
        model.scroller.content = content; model.scroller.height = scrollHeight; model.scroller.clamp();
        Shape old = g.getClip(); g.clip(new Rectangle2D.Float(PX, PY + 26 + descH, PW, PH - 26 - descH));
        clipTop = PY + 26 + descH;
        container(nodes, m.id, PX, top - model.scroller.offset, PW - 30, 0, 0, true, true);
        clipTop = 62.5f;
        g.setClip(old);
        scrollbar(PX + PW - 6);
        if (!model.keybindPopup.isEmpty()) keybindPopup(m);
    }

    private List<OptionCatalog.Node> filtered(List<OptionCatalog.Node> nodes, String module) {
        if (model.search.isEmpty()) return nodes;
        String q = normalise(model.search);
        List<OptionCatalog.Node> out = new ArrayList<OptionCatalog.Node>();
        for (OptionCatalog.Node n : nodes) {
            if (n.type.equals("section")) {
                if (!filtered(n.children, module).isEmpty()) out.add(n);
            } else if (matchesOption(n, q)) out.add(n);
        }
        return out;
    }

    private boolean matchesOption(OptionCatalog.Node n, String q) {
        String name = label(n).toLowerCase(Locale.ROOT);
        if (name.contains(q)) return true;
        for (String word : name.split(" ")) if (normalise(word).startsWith(q)) return true;
        for (OptionCatalog.Node c : n.children) if (!c.type.equals("section") && matchesOption(c, q)) return true;
        return false;
    }

    static String normalise(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9.]", "").toLowerCase(Locale.ROOT);
    }

    private String label(OptionCatalog.Node n) {
        if (n.type.equals("child")) return LunarLang.featureName(n.key, n.key);
        if (n.type.equals("section")) return LunarLang.get("settings.labels", n.key);
        if (n.text != null) return n.text;
        return LunarLang.get("settings", n.key);
    }

    private String owner(OptionCatalog.Node child, String module) { return child.key.toLowerCase(Locale.ROOT); }

    private String value(String module, OptionCatalog.Node n) { return model.value(module, n.key, n.value); }

    private boolean hidden(OptionCatalog.Node n, String module) {
        if (n.transient_) return true;
        if (n.hideWhen != null && n.hideWhen.getAsBoolean()) return true;
        if (n.type.equals("child")) return false;
        if (n.type.equals("section")) {
            for (OptionCatalog.Node c : n.children) if (!hidden(c, module)) return false;
            return true;
        }
        return n.hide != null && new Rule(n.hide, module).eval();
    }

    private final class Rule {
        private final String[] t; private int i; private final String module;
        Rule(String rule, String module) {
            List<String> tokens = new ArrayList<String>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9]*(?:[=~][A-Z0-9_]+)?|[!()|&]").matcher(rule);
            while (m.find()) tokens.add(m.group());
            t = tokens.toArray(new String[0]); this.module = module;
        }
        boolean eval() { boolean v = or(); return v; }
        private boolean or() { boolean v = and(); while (i < t.length && t[i].equals("|")) { i++; v |= and(); } return v; }
        private boolean and() { boolean v = not(); while (i < t.length && t[i].equals("&")) { i++; v &= not(); } return v; }
        private boolean not() {
            String s = t[i++];
            if (s.equals("!")) return !not();
            if (s.equals("(")) { boolean v = or(); i++; return v; }
            if (s.equals("true")) return true;
            if (s.equals("false")) return false;
            int eq = Math.max(s.indexOf('='), s.indexOf('~'));
            if (eq < 0) return Boolean.parseBoolean(current(s));
            boolean same = current(s.substring(0, eq)).equals(s.substring(eq + 1));
            return s.charAt(eq) == '=' ? same : !same;
        }
        private String current(String key) {
            OptionCatalog.Node n = find(OptionCatalog.page(module).options, key);
            return model.value(module, key, n == null ? "" : n.value);
        }
    }

    private static OptionCatalog.Node find(List<OptionCatalog.Node> nodes, String key) {
        for (OptionCatalog.Node n : nodes) {
            if (key.equals(n.key) && n.type.equals("option")) return n;
            if (!n.type.equals("child")) { OptionCatalog.Node hit = find(n.children, key); if (hit != null) return hit; }
        }
        return null;
    }

    private boolean half(OptionCatalog.Node n, String module) {
        if (n.type.equals("child")) return !model.expanded.contains(module + "." + n.key);
        if (!n.type.equals("option") || !n.kind.equals("bool")) return false;
        return n.children.isEmpty() || !model.expanded.contains(module + "." + n.key);
    }

    private float container(List<OptionCatalog.Node> nodes, String module, float x, float y, float w, float inset, float step,
                            boolean page, boolean draw) {
        int column = 0;
        float rowHeight = 0;
        List<OptionCatalog.Node> visible = new ArrayList<OptionCatalog.Node>();
        for (OptionCatalog.Node n : nodes) if (!hidden(n, module) && !(n.type.equals("option") && !drawable(n))) visible.add(n);
        for (int i = 0; i < visible.size(); i++) {
            OptionCatalog.Node n = visible.get(i);
            boolean isHalf = half(n, module);
            if (column == 2) { column = 0; y += rowHeight; rowHeight = 0; }
            if (!isHalf) { column = 0; y += rowHeight; rowHeight = 0; }
            float rx = x + (page ? (n.type.equals("section") ? 0 : 15) : inset) + column * (w / 2);
            float rw = (isHalf ? w / 2 : w) - step;
            float h = row(n, module, rx, y, rw, draw);
            rowHeight = Math.max(rowHeight, h);
            if (!isHalf) { y += rowHeight; rowHeight = 0; continue; }
            if (i == visible.size() - 1) y += rowHeight;
            column++;
        }
        return y;
    }

    private static boolean drawable(OptionCatalog.Node n) {
        if (n.kind.equals("number")) return !Float.isNaN(n.min) && !Float.isNaN(n.max);
        return n.kind.equals("bool") || n.kind.equals("color") || n.kind.equals("enum") || n.kind.equals("key")
            || n.kind.equals("keycombo") || n.kind.equals("string") || n.kind.equals("button") || n.kind.equals("widget") && n.widget != null;
    }

    private float row(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        if (n.type.equals("section")) return group(n, module, x, y, w, draw);
        if (n.type.equals("child")) return childRow(n, module, x, y, w, draw);
        if (n.kind.equals("bool")) return n.children.isEmpty() ? toggle(n, module, x, y, w, draw) : expandable(n, module, x, y, w, draw);
        if (n.kind.equals("number")) { if (draw) slider(n, module, x, y, w); return 14; }
        if (n.kind.equals("color")) return colorOption(n, module, x, y, w, draw);
        if (n.kind.equals("enum")) { if (draw) enumOption(n, module, x, y, w); return 14; }
        if (n.kind.equals("key") || n.kind.equals("keycombo")) { if (draw) keybind(n, module, x, y, w); return 14; }
        if (n.kind.equals("string")) { if (draw) textOption(n, module, x, y, w); return 14; }
        if (n.kind.equals("button")) { if (draw) buttonOption(n, module, x, y); return 14; }
        if (n.kind.equals("widget")) return widgetRow(n, x, y, w, draw);
        return 0;
    }

    private float group(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        x += 14;
        if (draw) text(label(n).toUpperCase(Locale.ROOT), bold, 6, x - 6, y + 3, 0xFFC1C0BE);
        float end = container(n.children, module, x, y + 8 + 3, w, 0, 4, false, draw);
        return end - y;
    }

    private float toggle(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        if (!draw) return 14;
        boolean on = Boolean.parseBoolean(value(module, n));
        text(fit(label(n), light, 9, w - 37), light, 9, x + 36, y + 1.5f, 0xFFC1C0BE);
        labeledSwitch(x, y, on, module + "." + n.key, hover(x, y, w, 14));
        hitClip(x, y, w, 14, "bool:" + module + ":" + n.key + ":" + n.value, "");
        return 14;
    }

    private float expandable(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        String key = module + "." + n.key;
        boolean open = model.expanded.contains(key);
        float inner = open ? container(n.children, module, x, y + 14 + 3, w, 4, 10, false, false) - (y + 17) : 0;
        float height = open ? inner + 14 + 7 : 14;
        if (!draw) return height;
        boolean on = Boolean.parseBoolean(value(module, n));
        float column = open ? w / 2 : w, room = column - 48;
        String name = label(n);
        if (width(name, light, 9) <= room) text(name, light, 9, x + 36, y + 1.5f, 0xFFC1C0BE);
        else if (width(name, light, 8) <= room) text(name, light, 8, x + 36, y + 2, 0xFFC1C0BE);
        else text(name, light, 7, x + 36, y + 2.5f, 0xFFC1C0BE);
        labeledSwitch(x, y, on, key, hover(x, y, 34, 20));
        boolean overArrow = hover(x + 34, y, w - 34, 20);
        arrowGlyph(x + column - 11, y + 7, turn("chevron:" + key, open, 200), overArrow ? WHITE : 0x99FFFFFF);
        hitClip(x, y, 32, 14, "boolOpen:" + module + ":" + n.key + ":" + n.value, "");
        hitClip(x + 32, y, w - 32, 14, "expand:" + key, "");
        if (open) {
            fill(x, y + 15, w - 2, inner + 20 + 6 - 14, 6, 0x20B0B0B0);
            container(n.children, module, x, y + 14 + 3, w, 4, 10, false, true);
        }
        return height;
    }

    private float childRow(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        String child = owner(n, module), key = module + "." + n.key;
        boolean hasOptions = false;
        for (OptionCatalog.Node c : n.children) if (!hidden(c, child)) { hasOptions = true; break; }
        boolean open = hasOptions && model.expanded.contains(key);
        float inner = open ? container(n.children, child, x, y + 18 + 4, w, 4, 30, false, false) - (y + 22) : 0;
        float height = open ? inner + 18 + 10 : 18;
        if (!draw) return height;
        boolean on = model.flag(child, "enabled", Boolean.parseBoolean(n.value));
        text(fit(label(n), light, 9, w - 60), light, 9, x + 34, y + 4.5f, 0xFFC1C0BE);
        labeledSwitch(x, y + 3, on, child + ".enabled", hover(x, y, 34, 20));
        hitClip(x, y, 32, 18, "bool:" + child + ":enabled:" + n.value, "");
        if (hasOptions) {
            boolean cog = hover(x + 34, y, w - 34, 20);
            float spin = cog ? (System.currentTimeMillis() % 4000) / 4000f * 4 : 0;
            if (cog) animating = true;
            arrowGlyph(x + w - 11, y + 10, spin, cog ? WHITE : 0x99FFFFFF, "icons/settings/cog-16x16.png");
            hitClip(x + 32, y, w - 32, 18, "expand:" + key, "");
        }
        if (open) {
            fill(x, y + 19, w - 2, inner + 28 + 8 - 18, 6, 0x20B0B0B0);
            container(n.children, child, x, y + 18 + 4, w, 4, 30, false, true);
        }
        return height;
    }

    private float turn(String key, boolean on, long ms) {
        long now = System.currentTimeMillis();
        long[] state = slides.get(key);
        if (state == null) { state = new long[]{on ? 1 : 0, 0}; slides.put(key, state); }
        if ((state[0] == 1) != on) { state[0] = on ? 1 : 0; state[1] = now; }
        float t = Math.min(1, (now - state[1]) / (float)ms);
        if (t < 1) animating = true;
        return on ? t : 1 - t;
    }

    private final Map<String, LunarSlider.Knob> knobs = new HashMap<String, LunarSlider.Knob>();

    private void slider(OptionCatalog.Node n, String module, float x, float y, float w) {
        float min = n.min, max = n.max, fallback = parse(n.value, min);
        float value = Math.max(min, Math.min(max, model.number(module, n.key, fallback)));
        String name = label(n), shown = format(n, value), key = module + "." + n.key;
        text(name, light, 9, x, y + 1.5f, 0xFFC1C0BE);

        float valueWidth = Math.max(width(format(n, min), bold, 5), width(format(n, max), bold, 5));
        float labelRoom = width(name, light, 9) + valueWidth + 6;
        float sw = w - w / 3, sx = x + w / 3;
        if (sw > w - (labelRoom + 15)) { sw = w - (labelRoom + 15); sx = x + labelRoom + 5; }
        float h = LunarSlider.HEIGHT, left = sx + 4, width = sw - 10 - 8;

        text(shown, bold, 5, left - 8 - width(shown, bold, 5), y + h / 2 - (int)(fontHeight(bold, 5) / 2) - 2, 0x8FFFFFFF);

        float pad = LunarSlider.pad(width), track = LunarSlider.track(width);
        rect(left + pad, y + h / 2 - h / 8, track, h / 4, 0xFF1A1B1B);
        LunarSlider.Knob knob = knobs.get(key);
        if (knob == null) { knob = new LunarSlider.Knob(value); knobs.put(key, knob); }
        long now = System.currentTimeMillis();
        int fade = knob.fade(key.equals(model.sliderHeld), now);
        if (fade > 0 && fade < 255) animating = true;
        if (fade > 50) {
            int[] ticks = LunarSlider.ticks(min, max, wholeNumbers(n), n.roundTo);
            float gap = track / ticks[0];
            for (int i = 0; i <= ticks[0]; i++) {
                float tx = left + pad + i * gap;
                tick(tx, y, h / 4 + 2, fade);
                for (int j = 1; j < ticks[1]; j++) tick(tx + gap * j / ticks[1], y, h / 4, fade);
            }
        }
        float frac = knob.position(value, min, max, now);
        if (knob.moving(now)) animating = true;
        float cx = left + pad + track * frac, cy = y + h / 2, r = h / 3;
        g.setColor(new Color(0xFF4F94FC, true)); g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        Area ringArea = new Area(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        ringArea.subtract(new Area(new Ellipse2D.Float(cx - r + 1, cy - r + 1, r * 2 - 2, r * 2 - 2)));
        g.setColor(new Color(0x24FFFFFF, true)); g.fill(ringArea);
        hitClip(left, y, width, h, "slider:" + module + ":" + n.key + ":" + min + ":" + max + (n.integer ? ":int:" : ":float:") + h, "");
        if (Math.abs(value - fallback) >= 1e-4) resetGlyph(x + w - 9, y + 3, "set:" + module + ":" + n.key + ":" + n.value);
    }

    private void tick(float x, float y, float height, int alpha) {
        rect(x, y + LunarSlider.HEIGHT / 2 - height / 2, 1, height, alpha << 24 | 0x383840);
    }

    private static boolean wholeNumbers(OptionCatalog.Node n) { return n.integer || n.roundTo == 1; }

    private static String format(OptionCatalog.Node n, float v) {
        return wholeNumbers(n) ? "" + Math.round(v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static float parse(String s, float fallback) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return fallback; }
    }

    private void resetGlyph(float x, float y, String action) {
        boolean over = hover(x, y, 8, 8);
        tinted("icons/settings/reset-16x16.png", x + 1, y + 1, 6, 6, ((int)((over ? .9f : .65f) * 255) << 24) | 0xFFFFFF);
        hitClip(x, y, 8, 8, action, "");
    }

    private void enumOption(OptionCatalog.Node n, String module, float x, float y, float w) {
        text(label(n), light, 9, x, y + 1.5f, 0xFFC1C0BE);
        String current = value(module, n);
        arrow("icons/settings/arrow-left-18x18.png", x + w - 115, y, "enum:" + module + ":" + n.key + ":prev");
        arrow("icons/settings/arrow-right-18x18.png", x + w - 30, y, "enum:" + module + ":" + n.key + ":next");
        centered(enumText(n, current), medium, 6.5f, x + w - 64, y + 2.5f, 0xAFC1C0BE);
        if (!current.equals(n.value)) resetGlyph(x + w - 9, y + 3, "set:" + module + ":" + n.key + ":" + n.value);
    }

    private static String enumText(OptionCatalog.Node n, String constant) {
        for (String[] v : n.values) if (v[0].equals(constant)) return v[1] == null ? v[0] : LunarLang.get("settings", v[1]);
        return constant;
    }

    private void keybind(OptionCatalog.Node n, String module, float x, float y, float w) { keybind(n, module, x, y, w, label(n)); }
    private void keybind(OptionCatalog.Node n, String module, float x, float y, float w, String name) {
        text(name, light, 9, x, y + 1.5f, 0xFFC1C0BE);
        String field = module + ":" + n.key;
        boolean listening = model.keyListening.equals(field);
        float bx = x + w - 82, by = y + 2;
        boolean over = hover(bx, by, 70, 12);
        body(bx, by, 70, 12, 4, 0x40252525, 0x20FFFFFF, fadeColor("key:" + field, over || listening, 0x20FFFFFF, 0x45FFFFFF));
        String key = value(module, n);
        String shown = listening ? "?" : spaced(LunarLang.get("gui.components", key.equals("NONE") ? "none" : key).toUpperCase(Locale.ROOT));
        float ty = by + 6 - fontHeight(bold, 7);
        centered(shown, bold, 7, bx + 35 + 1, ty + 1, 0x20000000);
        centered(shown, bold, 7, bx + 35, ty, 0xFFBEC3BD);
        hitClip(bx, by, 70, 12, "listenKey:" + field, "");
        if (!key.equals(n.value)) resetGlyph(x + w - 9, y + 3, "set:" + module + ":" + n.key + ":" + n.value);
    }

    private void textOption(OptionCatalog.Node n, String module, float x, float y, float w) {
        text(label(n), light, 9, x, y + 1.5f, 0xFFC1C0BE);
        String field = module + ":" + n.key;
        float fx = x + w - 90, fy = y + 2;
        body(fx, fy, 90, 12, 5, 0, 0x20FFFFFF, 0x20797979);
        boolean editing = model.textEditing.equals(field);
        String shown = editing ? model.input : value(module, n);
        Shape old = g.getClip(); g.clip(new Rectangle2D.Float(fx + 2, fy, 86, 12));
        text(shown, bold, 7, fx + 4, fy + 2, 0x90FFFFFF);
        if (editing && blink) text("_", bold, 7, fx + 4 + width(shown, bold, 7), fy + 2, 0x90FFFFFF);
        g.setClip(old);
        hitClip(fx, fy, 90, 12, "editText:" + field + ":" + n.maxLength, "");
    }

    private void dialog(float x, float y, float w, float h, String title, int body) {
        rect(x, y + 24, w, .5f, 0x20FFFFFF);
        fill(x, y + 1, w, 23, 5, 0x25000000, true, true, false, false);
        rect(x, y + h - 30, w, .5f, 0x20FFFFFF);
        fill(x, y + h - 29.5f, w, 28.5f, 5, 0x45000000, false, false, true, false);
        ring(x - 1, y, w + 2, h, 4, 0x40000000);
        ring(x, y + 1, w, h - 2, 3, 0x20FFFFFF);
        fill(x, y + 1, w, h - 2, 5, body);
        text(title, light, 11, x + 8, y + 6, WHITE);
    }

    private void lunarButton(float x, float y, float w, float h, String key, String action, boolean active) {
        boolean lit = active || hover(x, y, w, h);
        body(x, y, w, h, 4, 0x40252525, 0x20FFFFFF, fadeColor("lbutton:" + action, lit, 0x20FFFFFF, 0x45FFFFFF));
        String label = spaced(LunarLang.get("gui.components", key).toUpperCase(Locale.ROOT));
        float ty = y + h / 2 - fontHeight(bold, 7);
        text(label, bold, 7, x + w / 2 + 1 - width(label, bold, 7) / 2, ty + 1, 0x20000000);
        text(label, bold, 7, x + w / 2 - width(label, bold, 7) / 2, ty, 0xFFBEC3BD);
        hit(x, y, w, h, action, "");
    }

    private void buttonOption(OptionCatalog.Node n, String module, float x, float y) {
        float w = Float.isNaN(n.width) ? 70 : n.width, h = 12;
        String action = "button:" + module + ":" + n.key;
        boolean lit = hover(x, y, w, h);
        body(x, y, w, h, 4, 0x40252525, 0x20FFFFFF, fadeColor("lbutton:" + action, lit, 0x20FFFFFF, 0x45FFFFFF));
        String label = spaced(LunarLang.get("settings.buttons", n.key).toUpperCase(Locale.ROOT));
        float ty = y + h / 2 - fontHeight(bold, 7);
        text(label, bold, 7, x + w / 2 + 1 - width(label, bold, 7) / 2, ty + 1, 0x20000000);
        text(label, bold, 7, x + w / 2 - width(label, bold, 7) / 2, ty, 0xFFBEC3BD);
        hit(x, y, w, h, action, "");
    }

    private float widgetRow(OptionCatalog.Node n, float x, float y, float w, boolean draw) {
        MenuWidget widget = n.widget;
        float h = widget.height(w);
        if (!draw || h <= 0) return h;
        widget.paint(this, x, y, w, h, mx, my);
        hitClip(x, y, w, h, "widget:" + shownWidgets.size(), "");
        shownWidgets.add(widget);
        if (widget.animating()) animating = true;
        return h;
    }

    private Font canvasFont(String file) { return file.equals(BOLD) ? bold : file.equals(MEDIUM) ? medium : light; }
    @Override public void text(String text, String font, float lunarSize, float x, float y, int argb) { text(text, canvasFont(font), lunarSize / 2, x, y, argb); }
    @Override public float textWidth(String text, String font, float lunarSize) { return width(text, canvasFont(font), lunarSize / 2); }
    @Override public void asset(String path, float x, float y, float w, float h, int argb) { tinted(path, x, y, w, h, argb); }
    @Override public void bitmap(BufferedImage image, float x, float y, float w, float h, int argb) {
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Composite oldComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (argb >>> 24) / 255f));
        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform(); t.translate(x, y); t.scale(w / image.getWidth(), h / image.getHeight());
        g.drawImage(image, t, null);
        g.setComposite(oldComposite);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }
    @Override public void imageRegion(String path, float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        image(path, -1000, -1000, 1, 1, 0f);
        BufferedImage source = images.get(path);
        int sx0 = Math.round(u0 * source.getWidth()), sy0 = Math.round(v0 * source.getHeight());
        int sx1 = Math.round(u1 * source.getWidth()), sy1 = Math.round(v1 * source.getHeight());
        g.drawImage(source, Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), sx0, sy0, sx1, sy1, null);
    }
    @Override public Graphics2D graphics() { return g; }

    private void iconBox(float x, float y, float w, float h, String path, String action) {
        boolean over = hover(x, y, w, h);
        body(x, y, w, h, 4, 0x40252525, 0x20FFFFFF, fadeColor("ibox:" + action, over, 0x20FFFFFF, 0x50FFFFFF));
        tinted(path, snap(x + w / 2 - 4), snap(y + h / 2 - 4), 8, 8, 0x99FFFFFF);
        hit(x, y, w, h, action, "");
    }

    private void textField(float x, float y, float w, float h, String text, String placeholder, boolean focused, boolean error, int max, String action) {
        body(x, y, w, h, 5, error ? 0xFFFF0000 : 0, 0x20FFFFFF, focused ? 0x35FFFFFF : 0x20FFFFFF);
        float tx = x + 4, ty = y + (h - 8) / 2;
        Shape old = g.getClip(); g.clip(new Rectangle2D.Float(x, y, w, h));
        if (!text.isEmpty()) text(text, medium, 6.5f, tx, ty, 0x90FFFFFF);
        else if (!focused) text(placeholder, medium, 6.5f, tx, ty, 0x30FFFFFF);
        if (focused && blink) {
            float end = tx + width(text, medium, 6.5f);
            if (text.length() >= max) rect(end + .5f, ty + 1, .5f, 2 + fontHeight(medium, 6.5f), 0xFFD0D0D0);
            else text("_", medium, 6.5f, end, ty, 0x90FFFFFF);
        }
        g.setClip(old);
        hit(x, y, w, h, action, "");
    }

    private void labelOption(String name, float x, float y) {
        text(name.toUpperCase(Locale.ROOT), bold, 6, x + 8, y + 3, 0xFFC1C0BE);
    }

    private void profileEditor() {
        float w = 350, h = 152, x = WIDTH / 2f - w / 2, y = HEIGHT / 2f - 76;
        String profile = model.editor;

        if (!model.editorIcon.isEmpty()) image("icons/profiles/" + model.editorIcon + ".png", x + w - 36, y + 50, 10, 10, 1);
        dialog(x, y, w, h, LunarLang.get("gui.components", "profileEditor"), 0x80000000);
        labelOption(LunarLang.get("gui.profile", "shortDescriptionChars", 20 - model.editorName.length()), x, y + 32);
        textField(x + 8, y + 48, w - 112, 16, model.editorName, model.displayName(profile), model.editorFocus.equals("name"),
            model.editorNameError, 20, "editorName");
        labelOption(LunarLang.get("gui.profile", "lblServer"), x, y + 64);
        String server = model.server(profile);
        textField(x + 8, y + 80, w - 112, 16, model.editorServer, server.isEmpty() ? "Unknown" : server, model.editorFocus.equals("server"),
            model.editorServerError, 60, "editorServer");
        labelOption(LunarLang.get("settings.labels", "icon"), x + w - 29, y + 32);
        iconBox(x + w - 24, y + 48, 14, 14, "icons/assets/arrow-down-17x17.png", "editorIconMenu");
        float n = 0;
        lunarButton(x + w - (n += 56), y + h - 24, 50, 18, "save", "editorSave", false);
        if (model.canDelete(profile)) lunarButton(x + w - (n += 56), y + h - 24, 50, 18, "delete", "editorDelete", false);
        if (model.inWorld) lunarButton(x + w - (n += 56), y + h - 24, 50, 18, "reset", "editorReset", false);
        iconBox(x + 6, y + h - 24, 18, 18, "icons/cosmetics/back-40x40.png", "editorBack");
        if (model.iconMenu != null) iconDropdown(model.iconMenu[0], model.iconMenu[1]);
    }

    private void iconDropdown(float px, float py) {
        hits.clear();
        float x = px + 2, y = py, w = 100, h = UiModel.PROFILE_ICONS.length * 10 + 8;
        body(x, y, w, h, 6, 0x40252525, 0x20FFFFFF, 0xCF1D1D1D);
        float fh = fontHeight(light, 6);
        for (int i = 0; i < UiModel.PROFILE_ICONS.length; i++) {
            float rx = px + 3, ry = y + 4 + i * 10, rw = w - 2;
            rect(rx, ry, rw, 10, fadeColor("icon:" + i, hover(rx, ry, rw, 10), 0, 0x45000000));
            String name = UiModel.PROFILE_ICONS[i][1];
            text(name, medium, 6.5f, rx + 5, ry + 5 - fh, 0x20000000);
            text(name, medium, 6.5f, rx + 4, ry + 4 - fh, 0xDFE2E2E2);
            hit(rx, ry, rw, 10, "editorIcon:" + UiModel.PROFILE_ICONS[i][0], "");
        }
    }

    private long confirmSince;
    private String confirmShown = "";

    private void confirmDialog() {
        if (!model.confirm.equals(confirmShown)) { confirmShown = model.confirm; confirmSince = System.currentTimeMillis(); }
        float cx = WIDTH / 2f, cy = HEIGHT / 2f, x = cx - 160, y = cy - 60, w = 320, h = 118;
        rect(x, y + 24, w, .5f, 0x20FFFFFF);
        fill(x, y + 1, w, 23, 5, 0x25000000, true, true, false, false);
        ring(x - 1, y, w + 2, h, 4, 0x40000000);
        ring(x, y + 1, w, h - 2, 3, 0x20FFFFFF);
        fill(x, y + 1, w, h - 2, 5, 0x80000000);
        long phase = (System.currentTimeMillis() - confirmSince) % 4000;
        float t = phase < 2000 ? phase / 2000f : 2 - phase / 2000f;
        animating = true;
        String path = "gui.profile." + model.confirm;
        centered(LunarLang.get(path, "warning"), light, 11, cx, cy - 54, lerp(0xFFFFFFFF, 0xFFFF3333, t));
        centered(LunarLang.get(path, "lineOne"), medium, 6.5f, cx, cy - 24, WHITE);
        if (LunarLang.has(path, "lineTwo")) centered(LunarLang.get(path, "lineTwo"), medium, 6.5f, cx, cy - 14, WHITE);
        lunarButton(cx - 100, cy + 10, 200, 16, "understood", "confirmYes", false);
        lunarButton(cx - 100, cy + 30, 200, 16, "cancel", "confirmNo", true);
    }

    private void keybindPopup(ModuleCatalog.Module m) {
        hits.clear();
        suppressHover = false;
        rect(-PAD, -PAD, WIDTH + PAD * 2, HEIGHT + PAD * 2, 0xD0000000);
        String name = LunarLang.featureName(m.id, m.name);
        float w = Math.max(180, width(name, light, 9) + 112), h = 80, x = WIDTH / 2f - w / 2, y = HEIGHT / 2f - 40;
        dialog(x, y, w, h, LunarLang.get("gui.components", "toggleKeybind"), 0xF0000000);
        keybind(new OptionCatalog.Node(keyNode(m.id)), m.id, x + 8, y + 34, w - 16, name);
        lunarButton(x + w - 56, y + h - 24, 50, 18, "done", "keybindPopupDone", false);
    }

    private static Map<String, Object> keyNode(String id) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("type", "option"); m.put("key", "toggleKeybind"); m.put("kind", "key"); m.put("default", "NONE");
        return m;
    }

    private static final int[] PRESETS = {0xFFAA0000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF00AA00, 0xFF55FF55,
        0xFF55FFFF, 0xFF00AAAA, 0xFF0000AA, 0xFF5555FF, 0xFFFF55FF, 0xFFAA00AA};

    private float colorOption(OptionCatalog.Node n, String module, float x, float y, float w, boolean draw) {
        String field = module + ":" + n.key;
        boolean open = model.expanded.contains("color|" + module + "." + n.key);
        if (!draw) return open ? 80 : 14;
        String fallback = n.value.toUpperCase(Locale.ROOT);
        String hex = model.color(module, n.key, fallback);
        int argb = (int)Long.parseLong(hex, 16);
        text(label(n), light, 8, x, y + 1.5f, 0xFFC1C0BE);
        if (model.hexEditing.equals(field)) {
            text(model.input.toUpperCase(Locale.ROOT), light, 8, x + w - 68, y + 2.5f, 0x90FFFFFF);
            if (blink) text("_", light, 8, x + w - 68 + width(model.input.toUpperCase(Locale.ROOT), light, 8), y + 2.5f, 0x90FFFFFF);
        } else {
            text("#" + hex.toLowerCase(Locale.ROOT), light, 8, x + w - 68, y + 2.5f, 0x80C1C0BE);
        }

        fill(x + w - 84, y + 2, 10, 10, 8, argb);
        ring(x + w - 83, y + 3, 8, 8, 3, 0xAFFFFFFF);
        boolean favorite = model.colorList("favoriteColors").contains(hex);
        boolean onStar = hover(x + w - 20, y, 10, 14), onReset = hover(x + w - 10, y, 10, 14);
        if (!hex.equalsIgnoreCase(fallback))
            tinted("icons/settings/reset-16x16.png", x + w - 8, y + 4, 6, 6, ((int)((onReset ? .9f : .65f) * 255) << 24) | 0xFFFFFF);
        tinted("icons/stars/star-64x64.png", x + w - 18, y + 4, 6, 6, ((int)((onStar ? .8f : .5f) * 255) << 24) | (favorite ? 0xCCCC00 : 0));
        hitClip(x + w - 10, y, 10, 14, "swatch:" + field + ":" + fallback, "");
        hitClip(x + w - 20, y, 10, 14, "colorFav:" + field + ":" + fallback, "");
        hitClip(x + w - 68, y, 48, 14, "hex:" + field + ":" + (n.alpha ? "a" : "-"), "");
        hitClip(x, y, w - 68, 14, "colorOpen:" + field, "");
        if (!open) return 14;

        float top = y + 14, h = 60;
        if (n.chroma) {
            fill(x, top, 112, h, 4, 0x20000000);
            boolean chroma = model.flag(module, n.key + "Chroma", n.chromaOn);
            boolean boxOver = hover(x + 7, y + 17, 110, 12);
            fill(x + 7, y + 19, 8, 8, 4, boxOver ? 0xFF4F94FC : 0xAA4F94FC);
            ring(x + 8, y + 20, 6, 6, 2, 0x35FFFFFF);
            if (chroma) image("icons/settings/checked-14x14.png", x + 8, y + 19.5f, 6, 6, 1);
            text(LunarLang.get("settings", "chroma"), light, 8, x + 20, y + 18.5f, 0xFFC1C0BE);
            hitClip(x + 7, y + 17, 110, 12, "bool:" + module + ":" + n.key + "Chroma:" + n.chromaOn, "");
            float speed = model.number(module, n.key + "ChromaSpeed", 40);
            text(LunarLang.get("settings", "chromaSpeed"), light, 8, x + 7, y + 31, 0xFFC1C0BE);
            String shown = "" + Math.round(speed);
            text(shown, light, 8, x + 59 - width(shown, light, 8), y + 31, 0xFFC1C0BE);
            miniSlider(x + 61, y + 30, 43, 12, (speed - 1) / 99f, "slider:" + module + ":" + n.key + "ChromaSpeed:1:100:int");

            text(LunarLang.get("settings", "chromaType"), light, 8, x + 7, y + 44.5f, 0xFFC1C0BE);
            String type = model.value(module, n.key + "ChromaType", "WAVE");
            arrow("icons/settings/arrow-left-18x18.png", x + 7 + 50, y + 43, "chromaType:" + field + ":prev");
            centered(LunarLang.get("settings", type.equals("SHIFT") ? "shift" : "wave"), medium, 6.5f, x + 7 + 76, y + 45.5f, 0xAFC1C0BE);
            arrow("icons/settings/arrow-right-18x18.png", x + 7 + 88, y + 43, "chromaType:" + field);
        }

        float hue = model.hue(module, n.key, argb);
        float[] hsb = Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
        float sx = x + 118;
        fill(sx, top, 114, h, 4, WHITE);
        g.setPaint(new GradientPaint(sx + 1, 0, Color.WHITE, sx + 113, 0, Color.getHSBColor(hue, 1, 1)));
        g.fill(new Rectangle2D.Float(sx + 1, top + 1, 112, h - 2));
        g.setPaint(new GradientPaint(0, top + 1, new Color(0, 0, 0, 0), 0, top + h - 1, Color.BLACK));
        g.fill(new Rectangle2D.Float(sx + 1, top + 1, 112, h - 2));
        float cx = sx + 1 + 112 * hsb[1], cy = top + 1 + (h - 2) * (1 - hsb[2]);
        g.setColor(Color.BLACK); g.fill(new Ellipse2D.Float(cx - 2, cy - 2, 4, 4));
        g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(cx - 1, cy - 1, 2, 2));
        hitClip(sx + 1, top + 1, 112, h - 2, "sb:" + field, "");
        fill(x + 238, top, 9, h, 4, WHITE);
        image("components/hue_selector.png", x + 239, top + 1, 7, h - 2, 1);
        triangle(x + 244.5f, top + 1 - 1.75f + (h - 2) * hue, 3.5f, WHITE);
        hitClip(x + 239, top + 1, 7, h - 2, "hue:" + field, "");
        if (n.alpha) {
            fill(x + 252, top, 9, h, 4, WHITE);
            image("components/alpha_selector.png", x + 253, top + 1, 7, h - 2, 1);
            triangle(x + 258.5f, top + 1 - 1.75f + (h - 2) * (1 - (argb >>> 24) / 255f), 3.5f, 0xFFFF3333);
            hitClip(x + 253, top + 1, 7, h - 2, "alpha:" + field, "");
        }
        rect(x + 266, top, .5f, h, 0x20A2A2A2);
        rect(x + 314, top, .5f, h, 0x20A2A2A2);
        rect(x + 295, top, .5f, h, 0x20A2A2A2);
        for (int i = 0; i < PRESETS.length; i++) swatchButton(x + 271.5f + 10 * (i % 2), y + 15 + 10 * (i / 2), PRESETS[i], field);
        List<String> recent = model.colorList("recentColors"), favorites = model.colorList("favoriteColors");
        for (int i = 0; i < recent.size() && i < 6; i++) swatchButton(x + 301, y + 15 + 10 * i, (int)Long.parseLong(recent.get(i), 16), field);
        for (int i = 0; i < favorites.size() && i < 12; i++) swatchButton(x + 318.5f + 10 * (i % 2), y + 15 + 10 * (i / 2), (int)Long.parseLong(favorites.get(i), 16), field);

        for (int i = 0; i < 6; i++) fill(x + 301, y + 15 + 10 * i, 8, 8, 4, 0x35000000);
        for (int i = 0; i < 6; i++) for (int j = 0; j < 2; j++) fill(x + 318.5f + 10 * j, y + 15 + 10 * i, 8, 8, 4, 0x35000000);
        return 80;
    }

    private void swatchButton(float x, float y, int argb, String field) {
        fill(x, y, 8, 8, 5, argb);
        ring(x + 1, y + 1, 6, 6, 2.5f, 0xAFFFFFFF);
        hitClip(x, y, 8, 8, "swatch:" + field + ":" + String.format(Locale.ROOT, "%08X", argb), "");
    }

    private void triangle(float x, float y, float s, int argb) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x + s / 2, y); path.lineTo(x + s / 2, y + s); path.lineTo(x - s / 2, y + s / 2); path.closePath();
        g.setColor(new Color(argb, true)); g.fill(path);
    }

    private void miniSlider(float x, float y, float w, float h, float portion, String action) {
        float p = h / 3, track = w - h * 2 / 3;
        rect(x + p, y + h / 2 - h / 8, track, h / 4, 0xFF1A1B1B);
        float cx = x + p + track * Math.max(0, Math.min(1, portion)), cy = y + h / 2, r = h / 3;
        g.setColor(new Color(0xFF4F94FC, true)); g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));

        hitClip(x, y, w, h, action + ":" + h, "");
    }

    private void arrowGlyph(float cx, float cy, float quarter, int argb) { arrowGlyph(cx, cy, quarter, argb, "icons/settings/arrow-right-18x18.png"); }
    private void arrowGlyph(float cx, float cy, float quarter, int argb, String path) {
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.toRadians(90 * quarter));
        tinted(path, -4, -4, 8, 8, argb);
        g.setTransform(saved);
    }

    private List<String> wrap(String text, Font font, float size, float maximum) {
        List<String> lines = new ArrayList<String>();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(next, font, size) > maximum) { lines.add(line); line = word; }
            else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private void settings() {
        float x = 110;
        for (String tab : new String[]{"GENERAL", "PERFORMANCE", "CONTROLS"}) {
            float w = width(spaced(tab), bold, 7) + 16;
            button(x, 43.5f, w, 14, spaced(tab), "settings:" + tab, model.settingsTab.equals(tab), 1, ""); x += w + 4;
        }
        search(383, 43.5f, 100, 14);

        float y = 69.5f, left = 125, full = 347;
        if (model.settingsTab.equals("GENERAL")) {
            section("HUD Options", y); y += 12;
            if (matches("Hide HUD in Debug")) { toggleRow("Hide HUD in Debug", "global", "hideDebug", true, left, y, full); y += 14; }
            if (matches("Dim Menu Background")) { toggleRow("Dim Menu Background", "global", "menuDim", true, left, y, full); y += 14; }
            section(LunarLang.get("settings.labels", "modOptions"), y); y += 12;
            if (matches("Menu Layout")) { enumRow("Menu Layout", model.compact() ? "Compact" : "Large", "compact", left, y, full); y += 14; }
            String sorting = LunarLang.get("settings", "sortingOptions"), byOptions = LunarLang.get("settings", "searchOptions");
            if (matches(sorting)) { enumRow(sorting, UiModel.sortName(model.sort()), "sort", left, y, full); y += 14; }
            if (matches(byOptions)) {
                text(fit(byOptions, light, 9, full - 38), light, 9, left + 36, y + 1.5f, 0xFFC1C0BE);
                boolean on = Boolean.parseBoolean(model.store.get("searchOptions", "true"));
                labeledSwitch(left, y, on, "searchOptions", hover(left, y, full, 14));
                hit(left, y, full, 14, "searchOptions", "");
            }
        } else if (model.settingsTab.equals("CONTROLS")) {
            section("Keybinds", y); y += 12;
            if (matches("Mod Menu")) { valueRow("Mod Menu", model.captureKey && model.captureTarget.equals("menu") ? "Press a key..." : model.store.get("menuKeyName", "RSHIFT"), "rebind", left, y, full, "Click to change the menu key"); y += 14; }
            if (matches("Emote Wheel")) valueRow("Emote Wheel", model.captureKey && model.captureTarget.equals("emote") ? "Press a key..." : model.store.get("emoteKeyName", "B"), "rebindEmote", left, y, full, "Click to change the emote wheel key");
        } else {
            section("Performance", y); y += 12;
            valueRow("Video Settings", "Open", "videoSettings", left, y, full, "Open Minecraft video settings");
        }
        model.scroller.content = 0; model.scroller.height = 235; model.scroller.clamp();
    }

    private void section(String title, float y) {
        text(title.toUpperCase(Locale.ROOT), bold, 6, 118, y + 3, 0xFFC1C0BE);
    }
    private boolean matches(String text) { return text.toLowerCase(Locale.ROOT).contains(model.search.toLowerCase(Locale.ROOT)); }
    private void toggleRow(String label, String id, String option, boolean fallback, float x, float y, float w) {
        text(fit(label, light, 9, w - 38), light, 9, x + 36, y + 1.5f, 0xFFC1C0BE);
        labeledSwitch(x, y, model.flag(id, option, fallback), id + "." + option, hover(x, y, w, 14));
        hit(x, y, w, 14, "bool:" + id + ":" + option + ":" + fallback, "");
    }

    private void enumRow(String label, String value, String action, float x, float y, float w) {
        text(label, light, 9, x, y + 1.5f, 0xFFC1C0BE);
        arrow("icons/settings/arrow-left-18x18.png", x + w - 115, y, action + ":prev");
        arrow("icons/settings/arrow-right-18x18.png", x + w - 30, y, action);
        centered(value, medium, 6.5f, x + w - 64, y + 2.5f, 0xAFC1C0BE);
    }

    private void arrow(String path, float x, float y, String action) {
        rect(x, y, 13, 13, 0x06033F33);
        int alpha = 128 + (int)(fade("arrow:" + action, hover(x, y, 13, 13)) * 127);
        tinted(path, x + 2.25f, y + 2.25f, 9, 9, alpha << 24 | 0x00FFFF);
        hit(x, y, 13, 13, action, "");
    }

    private void valueRow(String label, String value, String action, float x, float y, float w, String tooltip) {
        text(label, light, 9, x, y + 1.5f, 0xFFC1C0BE);
        centered(value, medium, 6.5f, x + w - 64, y + 2.5f, hover(x, y, w, 14) ? WHITE : 0xAFC1C0BE);
        hit(x, y, w, 14, action, tooltip);
    }

    private void labeledSwitch(float x, float y, boolean on, String key, boolean hovered) {
        fill(x, y + 2, 30, 10, 5, hovered ? 0x40E2E2E2 : 0x20E2E2E2);
        ring(x + 1, y + 3, 28, 8, 2.5f, 0x35FFFFFF);
        float thumb = x + 10 * slide("switch:" + key, on);
        fill(thumb, y + 2, 20, 10, 5, on ? 0xAF29D67A : 0xAFDE2152);
        ring(thumb + 1, y + 3, 18, 8, 2.5f, 0x35FFFFFF);
        centered(LunarLang.get("gui.components", on ? "on" : "off"), bold, 6, thumb + 10, y + 3, WHITE);
    }

    private void search(float x, float y, float w, float h) {
        body(x, y, w, h, 5, 0, 0x20FFFFFF, model.searchFocused ? 0x35FFFFFF : 0x20FFFFFF);
        tinted("icons/assets/magnifying-glass-12x12.png", x + 7, y + 4.5f, 5.5f, 5.5f, 0x33FFFFFF);
        Shape old = g.getClip(); g.clip(new Rectangle2D.Float(x + 15, y, w - 18, h));
        float ty = y + (h - 8) / 2;
        if (!model.search.isEmpty()) text(model.search, medium, 6.5f, x + 16, ty, 0x90FFFFFF);
        else if (!model.searchFocused) text(LunarLang.get("gui.components", "searchPlaceholder"), medium, 6.5f, x + 16, ty, 0x30FFFFFF);
        if (model.searchFocused && blink) rect(Math.min(x + w - 4, x + 17.5f + width(model.search, medium, 6.5f)), ty + 1, .5f, 8, 0xFFD0D0D0);
        g.setClip(old); hit(x, y, w, h, "search", "");
    }

    private void scrollbar(float x) {
        LunarScroller sc = model.scroller;
        if (!sc.overflows()) return;
        float thumb = sc.thumb(), ty = scrollTop + sc.thumbY();
        boolean over = mx >= x && mx <= x + 4 && my > ty && my < ty + thumb;
        rect(x, scrollTop, 4, scrollHeight, 0x203D3D3D);
        rect(x, ty, 4, thumb, over || sc.dragging ? 0x60E2E2E2 : 0x20E2E2E2);
        hit(x, scrollTop, 4, scrollHeight, "scrollbar", "");
    }
    public Hit hitAt(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).rect.contains(x, y)) return hits.get(i);
        return null;
    }
    private void hit(float x, float y, float w, float h, String action, String tooltip) { hits.add(new Hit(x, y, w, h, action, tooltip)); }
    private void hitClip(float x, float y, float w, float h, String action, String tooltip) {
        float top = Math.max(clipTop, y), bottom = Math.min(297.5f, y + h);
        if (bottom > top) hit(x, top, w, bottom - top, action, tooltip);
    }
    private boolean hover(float x, float y, float w, float h) { return !suppressHover && mx >= x && mx < x + w && my >= y && my < y + h; }

    private void button(float x, float y, float w, float h, String label, String action, boolean active, int style, String tooltip) {
        boolean over = hover(x, y, w, h), lit = active || over;
        String key = "button:" + action;

        float size = h <= 12 ? 5 : 7, offset = h == 12 ? 1 : 0;
        float ty = y + h / 2 - fontHeight(bold, size) - offset;
        if (style == 0) {
            body(x, y, w, h, 4, 0, 0x20A2A2A2, fadeColor(key, lit, 0x203B3B3B, 0x20FFFFFF));
            shadowed(fit(label, bold, size, w - 4), bold, size, x + w / 2, ty, over ? 0xFFBEC3BD : 0x96BEC3BD);
        } else {
            int from = style == 2 ? 0x804F9400 : 0x805788D2, to = style == 2 ? 0xFF4F9400 : 0xFF4F94FC;
            if (style == 3) from = 0xFF5788D2;
            body(x, y, w, h, 4, 0, lit ? 0x60A2A2A2 : 0x20A2A2A2, fadeColor(key, lit, from, to));
            shadowed(fit(label, bold, size, w - 4), bold, size, x + w / 2, ty, lit || style == 3 ? WHITE : 0xAFFFFFFF);
        }
        hit(x, y, w, h, action, tooltip);
    }

    private void iconButton(float x, float y, float w, float h, String path, String action, String tooltip) {
        image(path, x + 1, y + 1, w - 2, h - 2, .65f + .25f * fade("icon:" + action, hover(x, y, w, h)));
        hit(x, y, w, h, action, tooltip);
    }
    private void image(String path, float x, float y, float w, float h, float alpha) {
        BufferedImage image = images.get(path);
        if (image == null) {
            try (InputStream input = resource(path)) {
                if (input == null) throw new IOException(path);
                image = ImageIO.read(input); images.put(path, image);
            } catch (IOException e) { throw new IllegalStateException("Missing Lunar UI asset: " + path, e); }
        }
        Composite old = g.getComposite(); g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform(); transform.translate(x, y); transform.scale(w / image.getWidth(), h / image.getHeight());
        g.drawImage(image, transform, null); g.setComposite(old);
    }
    private void tinted(String path, float x, float y, float w, float h, int argb) {
        String key = path + "#" + Integer.toHexString(argb | 0xFF000000);
        if (!images.containsKey(key)) {
            image(path, -1000, -1000, 1, 1, 0f);
            BufferedImage source = images.get(path), out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int py = 0; py < source.getHeight(); py++) for (int px = 0; px < source.getWidth(); px++) {
                int c = source.getRGB(px, py);
                int r = ((c >> 16) & 0xFF) * ((argb >> 16) & 0xFF) / 255, gr = ((c >> 8) & 0xFF) * ((argb >> 8) & 0xFF) / 255, b = (c & 0xFF) * (argb & 0xFF) / 255;
                out.setRGB(px, py, (c & 0xFF000000) | r << 16 | gr << 8 | b);
            }
            images.put(key, out);
        }
        image(key, x, y, w, h, ((argb >>> 24) & 0xFF) / 255f);
    }

    private float fade(String key, boolean on) {
        long now = System.currentTimeMillis();
        long[] state = fades.get(key);
        if (state == null) { state = new long[]{on ? 1 : 0, 0}; fades.put(key, state); }
        if ((state[0] == 1) != on) { state[0] = on ? 1 : 0; state[1] = now; }
        float progress = Math.min(1, (now - state[1]) / 125f);
        if (progress < 1) animating = true;
        return on ? progress : 1 - progress;
    }

    private float slide(String key, boolean on) {
        long now = System.currentTimeMillis();
        long[] state = slides.get(key);

        if (state == null) { state = new long[]{on ? 1 : 0, 0}; slides.put(key, state); }
        if ((state[0] == 1) != on) { state[0] = on ? 1 : 0; state[1] = now; }
        float linear = Math.min(1, (now - state[1]) / 100f);
        if (linear < 1) animating = true;
        float t = linear * linear;
        return on ? t : 1 - t;
    }
    private int fadeColor(String key, boolean on, int from, int to) { return lerp(from, to, fade(key, on)); }
    private static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8)
            out |= Math.round(((a >>> shift) & 0xFF) * (1 - t) + ((b >>> shift) & 0xFF) * t) << shift;
        return out;
    }
    private static Shape corners(float x, float y, float w, float h, float r, boolean tl, boolean tr, boolean bl, boolean br) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x + (tl ? r : 0), y);
        path.lineTo(x + w - (tr ? r : 0), y);
        if (tr) path.append(new Arc2D.Float(x + w - 2 * r, y, 2 * r, 2 * r, 90, -90, Arc2D.OPEN), true);
        path.lineTo(x + w, y + h - (br ? r : 0));
        if (br) path.append(new Arc2D.Float(x + w - 2 * r, y + h - 2 * r, 2 * r, 2 * r, 0, -90, Arc2D.OPEN), true);
        path.lineTo(x + (bl ? r : 0), y + h);
        if (bl) path.append(new Arc2D.Float(x, y + h - 2 * r, 2 * r, 2 * r, 270, -90, Arc2D.OPEN), true);
        path.lineTo(x, y + (tl ? r : 0));
        if (tl) path.append(new Arc2D.Float(x, y, 2 * r, 2 * r, 180, -90, Arc2D.OPEN), true);
        path.closePath();
        return path;
    }

    @Override public void fill(float x, float y, float w, float h, float r, int color, boolean tl, boolean tr, boolean bl, boolean br) {
        g.setColor(new Color(color, true));
        g.fill(corners(x, y, w, h, Math.min(r, Math.min(w, h)) / 2, tl, tr, bl, br));
    }
    @Override public void fill(float x, float y, float w, float h, float r, int color) { fill(x, y, w, h, r, color, true, true, true, true); }

    private void ring(float x, float y, float w, float h, float r, int color, boolean tl, boolean tr, boolean bl, boolean br) {
        if ((color >>> 24) == 0) return;
        Area area = new Area(corners(x - 1, y - 1, w + 2, h + 2, r, tl, tr, bl, br));
        area.subtract(new Area(corners(x, y, w, h, Math.max(0, r - 1), tl, tr, bl, br)));
        g.setColor(new Color(color, true)); g.fill(area);
    }
    @Override public void ring(float x, float y, float w, float h, float r, int color) { ring(x, y, w, h, r, color, true, true, true, true); }

    private void body(float x, float y, float w, float h, float r, int outer, int inner, int fill) {
        fill(x, y, w, h, r + 1, fill);
        ring(x, y, w, h, r, outer);
        ring(x + 1, y + 1, w - 2, h - 2, r - 1.25f, inner);
    }

    private void shadowed(String text, Font font, float size, float cx, float y, int color) {
        centered(text, font, size, cx + 1, y + 1, 0x20000000);
        centered(text, font, size, cx, y, color);
    }
    @Override public void rect(float x, float y, float w, float h, int color) { g.setColor(new Color(color, true)); g.fill(new Rectangle2D.Float(x, y, w, h)); }

    private static final class Glyphs {
        final Font font; final int[] advance = new int[256]; final int ascent, height;
        Glyphs(Font base, float px) {
            font = base.deriveFont(px);
            BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scratch.createGraphics();
            sg.setFont(font);
            sg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics metrics = sg.getFontMetrics();
            int tallest = -1;
            for (int c = 0; c < 256; c++) {
                java.awt.Rectangle bounds = metrics.getStringBounds(String.valueOf((char)c), sg).getBounds();
                advance[c] = bounds.width;
                tallest = Math.max(tallest, bounds.height);
            }
            ascent = metrics.getAscent(); height = (tallest - 8) / 2;
            sg.dispose();
        }
    }
    private final Map<String, Glyphs> glyphs = new HashMap<String, Glyphs>();
    private int rasterScale = 2;
    private Glyphs glyphs(Font font, float size) {
        String key = font.getFontName() + "@" + size;
        Glyphs result = glyphs.get(key);
        if (result == null) { result = new Glyphs(font, size * 2); glyphs.put(key, result); }
        return result;
    }

    private float fontHeight(Font font, float size) { return glyphs(font, size).height; }
    private float width(String text, Font font, float size) {
        Glyphs gl = glyphs(font, size); int total = 0;
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if (c < 256) total += gl.advance[c]; }
        return total / 2f;
    }
    private float snap(float v) { return (float)Math.round(v * rasterScale) / rasterScale; }
    private void text(String text, Font font, float size, float x, float y, int color) {
        Glyphs gl = glyphs(font, size);
        if ((color & 0xFC000000) == 0) color |= 0xFF000000;
        float d = snap(x - 1) * 2, d2 = snap(y) * 2;
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.scale(.5, .5); g.setFont(gl.font); g.setColor(new Color(color, true));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 256) continue;
            g.drawString(String.valueOf(c), d + 2, d2 + gl.ascent);
            d += gl.advance[c];
        }
        g.setTransform(saved);
    }
    private void centered(String text, Font font, float size, float x, float y, int color) { text(text, font, size, x - width(text, font, size) / 2, y, color); }
    private String fit(String text, Font font, float size, float maximum) {
        if (width(text, font, size) <= maximum) return text;
        while (!text.isEmpty() && width(text + "...", font, size) > maximum) text = text.substring(0, text.length() - 1);
        return text + "...";
    }
    private String spaced(String text) { return text.replace("", " ").trim(); }
}
