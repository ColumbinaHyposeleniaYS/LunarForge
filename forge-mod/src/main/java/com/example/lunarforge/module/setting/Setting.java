package com.example.lunarforge.module.setting;

import com.example.lunarforge.gui.ui.OptionCatalog;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final String key;
    protected final T defaultValue;
    Module owner;

    BooleanSupplier hidden;

    boolean noWidget;

    protected Setting(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public void attach(Module module) {
        if (owner != null && owner != module) throw new IllegalStateException(key + " already belongs to " + owner.id);
        owner = module;
    }

    public Module owner() { return owner; }

    public T getDefault() { return defaultValue; }

    public T get() {
        String raw = ModuleManager.store(owner.key(), key, null);
        if (raw == null) return defaultValue;
        try {
            T value = parse(raw);
            return value == null ? defaultValue : value;
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public void set(T value) { ModuleManager.put(owner.key(), key, format(value)); }

    public void reset() { ModuleManager.remove(owner.key(), key); }

    private final java.util.List<Runnable> listeners = new java.util.ArrayList<Runnable>();

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Runnable listener) { listeners.add(listener); return (S)this; }

    private boolean notifying;

    public void changed() {
        if (notifying) return;
        notifying = true;
        try { for (Runnable r : listeners) r.run(); } finally { notifying = false; }
    }

    public boolean isDefault() { return get().equals(defaultValue); }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S noWidget() { noWidget = true; return (S)this; }

    java.util.function.Supplier<String> label;

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S label(java.util.function.Supplier<String> text) { label = text; return (S)this; }

    public <S extends Setting<T>> S labelKey(final String langKey) {
        return label(() -> com.example.lunarforge.gui.ui.LunarLang.get("settings", langKey));
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S hideIf(BooleanSupplier rule) { hidden = rule; return (S)this; }

    protected abstract T parse(String raw);

    protected String format(T value) { return String.valueOf(value); }

    protected abstract String kind();

    protected void describe(Map<String, Object> fields) {}

    public OptionCatalog.Node node(List<OptionCatalog.Node> children, final BooleanSupplier layoutHide) {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("type", "option");
        fields.put("key", key);
        fields.put("kind", kind());
        fields.put("default", format(defaultValue));
        if (noWidget) fields.put("transient", Boolean.TRUE);
        if (label != null) fields.put("text", label.get());
        describe(fields);
        final BooleanSupplier own = hidden;
        BooleanSupplier hide = own == null ? layoutHide : layoutHide == null ? own : new BooleanSupplier() {
            @Override public boolean getAsBoolean() { return own.getAsBoolean() || layoutHide.getAsBoolean(); }
        };
        return OptionCatalog.Node.of(fields, children, hide);
    }
}
