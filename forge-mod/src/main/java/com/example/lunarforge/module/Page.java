package com.example.lunarforge.module;

import com.example.lunarforge.gui.ui.OptionCatalog;
import com.example.lunarforge.module.setting.Setting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Page {
    public static final class Entry {
        final Setting<?> setting;
        final String section;
        final Page children = new Page();
        BooleanSupplier hide;
        private Entry(Setting<?> setting, String section) { this.setting = setting; this.section = section; }

        public Entry hideIf(final BooleanSupplier rule) {
            final BooleanSupplier previous = hide;
            hide = previous == null ? rule : new BooleanSupplier() {
                @Override public boolean getAsBoolean() { return previous.getAsBoolean() || rule.getAsBoolean(); }
            };
            return this;
        }
    }

    public static final class Entries {
        private final List<Entry> entries;
        private Entries(List<Entry> entries) { this.entries = entries; }
        public Entries hideIf(BooleanSupplier rule) { for (Entry e : entries) e.hideIf(rule); return this; }
    }

    final List<Entry> entries = new ArrayList<Entry>();

    final List<Module> rows = new ArrayList<Module>();

    final List<Module> lastRows = new ArrayList<Module>();

    public Page section(String name, Consumer<Page> body) {
        Entry section = null;
        for (Entry e : entries) if (name.equals(e.section)) section = e;
        if (section == null) { section = new Entry(null, name); entries.add(section); }
        body.accept(section.children);
        return this;
    }

    public Entries add(Setting<?>... settings) {
        List<Entry> added = new ArrayList<Entry>();
        for (Setting<?> s : settings) { Entry e = new Entry(s, null); entries.add(e); added.add(e); }
        return new Entries(added);
    }

    public Entry addFirst(Setting<?> setting) {
        Entry e = new Entry(setting, null);
        entries.add(0, e);
        return e;
    }

    public Entry group(Setting<?> parent, Consumer<Page> children) {
        Entry e = new Entry(parent, null);
        children.accept(e.children);
        entries.add(e);
        return e;
    }

    public Entries under(Setting<?> parent, Setting<?>... settings) {
        Entry target = find(parent);
        return target == null ? null : target.children.add(settings);
    }

    private Entry find(Setting<?> setting) {
        for (Entry e : entries) {
            if (e.setting == setting) return e;
            Entry hit = e.children.find(setting);
            if (hit != null) return hit;
        }
        return null;
    }

    List<OptionCatalog.Node> nodes() {
        List<OptionCatalog.Node> out = new ArrayList<OptionCatalog.Node>();
        for (Module row : rows) out.add(row.rowNode());
        for (Entry e : entries) {
            List<OptionCatalog.Node> children = e.children.nodes();
            if (e.section != null) {
                if (children.isEmpty()) continue;
                Map<String, Object> fields = new HashMap<String, Object>();
                fields.put("type", "section");
                fields.put("key", e.section);
                out.add(OptionCatalog.Node.of(fields, children, e.hide));
            } else {
                out.add(e.setting.node(children, e.hide));
            }
        }
        for (Module row : lastRows) out.add(row.rowNode());
        return Collections.unmodifiableList(out);
    }

    Page sectionPage(String name) {
        for (Entry e : entries) if (name.equals(e.section)) return e.children;
        Entry section = new Entry(null, name);
        entries.add(section);
        return section.children;
    }
}
