package com.example.lunarforge.module.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChoiceSetting<E extends Enum<E> & ChoiceSetting.Option> extends Setting<E> {
    public interface Option {
        String langId();
    }

    private final Class<E> type;

    public ChoiceSetting(String key, E defaultValue) {
        super(key, defaultValue);
        this.type = defaultValue.getDeclaringClass();
    }

    public boolean is(E value) { return get() == value; }

    @Override protected E parse(String raw) {
        for (E e : type.getEnumConstants()) if (e.name().equalsIgnoreCase(raw.trim())) return e;
        return null;
    }

    @Override protected String format(E value) { return value.name(); }

    @Override protected String kind() { return "enum"; }

    @Override protected void describe(Map<String, Object> fields) {
        List<Object> values = new ArrayList<Object>();
        for (E e : type.getEnumConstants()) {
            List<Object> pair = new ArrayList<Object>();
            pair.add(e.name());
            pair.add(e.langId());
            values.add(pair);
        }
        fields.put("values", values);
    }
}
