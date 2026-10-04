package com.example.lunarforge.gui.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OptionCatalog {
    public static final class Node {
        public final String type, key, kind, value;
        public final float min, max;
        public final boolean integer, alpha, chroma, chromaOn, transient_, hud;
        public final String hide;

        public final String text;

        public final MenuWidget widget;

        public final float width;

        public final int maxLength, roundTo;

        public final List<String[]> values;
        public final List<Node> children;

        public final java.util.function.BooleanSupplier hideWhen;

        Node(Map<String, Object> m) { this(m, null, null); }

        public static Node of(Map<String, Object> fields, List<Node> children, java.util.function.BooleanSupplier hideWhen) {
            return new Node(fields, children, hideWhen);
        }

        @SuppressWarnings("unchecked")
        private Node(Map<String, Object> m, List<Node> built, java.util.function.BooleanSupplier hideWhen) {
            this.hideWhen = hideWhen;
            type = (String)m.get("type");
            key = (String)m.get("key");
            kind = m.containsKey("kind") ? (String)m.get("kind") : "";
            value = m.get("default") == null ? "" : String.valueOf(m.get("default"));
            min = number(m.get("min"));
            max = number(m.get("max"));
            integer = Boolean.TRUE.equals(m.get("integer"));
            alpha = !Boolean.FALSE.equals(m.get("alpha"));
            chroma = !Boolean.FALSE.equals(m.get("chroma"));
            chromaOn = Boolean.TRUE.equals(m.get("chromaOn"));
            transient_ = Boolean.TRUE.equals(m.get("transient"));
            hud = Boolean.TRUE.equals(m.get("hud"));
            hide = (String)m.get("hide");
            width = number(m.get("width"));
            widget = m.get("widget") instanceof MenuWidget ? (MenuWidget)m.get("widget") : null;
            text = (String)m.get("text");
            maxLength = m.containsKey("maxLength") ? Integer.parseInt(String.valueOf(m.get("maxLength"))) : 0;
            roundTo = m.containsKey("roundTo") ? Integer.parseInt(String.valueOf(m.get("roundTo"))) : 0;
            List<String[]> v = new ArrayList<String[]>();
            if (m.get("values") instanceof List) for (Object o : (List<Object>)m.get("values")) {
                List<Object> pair = (List<Object>)o;
                v.add(new String[]{(String)pair.get(0), (String)pair.get(1)});
            }
            values = Collections.unmodifiableList(v);
            List<Node> c = built == null ? new ArrayList<Node>() : new ArrayList<Node>(built);
            if (built == null && m.get("children") instanceof List) for (Object o : (List<Object>)m.get("children")) c.add(new Node((Map<String, Object>)o));
            children = Collections.unmodifiableList(c);
        }

        private static float number(Object o) {
            try { return o == null ? Float.NaN : Float.parseFloat(String.valueOf(o)); }
            catch (NumberFormatException e) { return Float.NaN; }
        }
    }

    public static final class Page {
        public final boolean hud, keybind;
        public final List<Node> options;
        public Page(boolean hud, boolean keybind, List<Node> options) { this.hud = hud; this.keybind = keybind; this.options = options; }
    }

    public static java.util.function.BiConsumer<String, String> buttons;

    public static java.util.function.Function<String, Page> provider;

    private static Page provided(String id) {
        return provider == null ? null : provider.apply(id.toLowerCase(Locale.ROOT));
    }

    private static final Map<String, Page> PAGES = new HashMap<String, Page>();
    private static final Page EMPTY = new Page(false, false, Collections.<Node>emptyList());

    @SuppressWarnings("unchecked")
    private static synchronized void load() {
        if (!PAGES.isEmpty()) return;
        try (InputStream in = OptionCatalog.class.getResourceAsStream("/assets/lunarforge/ui/option_tree.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
            Map<String, Object> root = (Map<String, Object>)Json.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            for (Map.Entry<String, Object> e : root.entrySet()) {
                Map<String, Object> page = (Map<String, Object>)e.getValue();
                List<Node> nodes = new ArrayList<Node>();
                for (Object o : (List<Object>)page.get("options")) nodes.add(new Node((Map<String, Object>)o));
                PAGES.put(e.getKey(), new Page(Boolean.TRUE.equals(page.get("hud")), Boolean.TRUE.equals(page.get("keybind")),
                    Collections.unmodifiableList(nodes)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load the option tree", e);
        }
    }

    public static Page page(String id) {
        Page own = provided(id);
        if (own != null) return own;
        load();
        Page p = PAGES.get(id.toUpperCase(Locale.ROOT));
        return p == null ? EMPTY : p;
    }

    public static List<Node> options(String id) {
        Page own = provided(id);
        if (own != null) return own.options;
        load();
        String upper = id.toUpperCase(Locale.ROOT);
        if (PAGES.containsKey(upper)) return PAGES.get(upper).options;
        for (Page p : PAGES.values()) {
            Node child = child(p.options, upper);
            if (child != null) return child.children;
        }
        return Collections.emptyList();
    }

    private static Node child(List<Node> nodes, String id) {
        for (Node n : nodes) {
            if (n.type.equals("child") && n.key.equals(id)) return n;
            if (!n.type.equals("child")) { Node hit = child(n.children, id); if (hit != null) return hit; }
        }
        return null;
    }

    public static Node find(String id, String key) {
        for (Node n : flatten(options(id))) if (n.key.equals(key)) return n;
        return null;
    }

    public static List<Node> flatten(List<Node> nodes) {
        List<Node> out = new ArrayList<Node>();
        for (Node n : nodes) {
            if (n.type.equals("option")) out.add(n);
            if (!n.type.equals("child")) out.addAll(flatten(n.children));
        }
        return out;
    }

    private OptionCatalog() {}
}
