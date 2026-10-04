package com.example.lunarforge.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.IChatComponent;

public final class ChatEvent {
    private static final List<Consumer<ChatEvent>> LISTENERS = new ArrayList<Consumer<ChatEvent>>();

    public final IChatComponent original;

    public final int id;

    public final String legacy, plain;
    private IChatComponent component;
    public boolean cancelled, changed;

    public boolean filtered;

    public boolean removeLast;

    public ChatEvent(IChatComponent component, int id) {
        this.original = component;
        this.component = component;
        this.id = id;
        this.legacy = component.getUnformattedText();
        this.plain = Server.strip(legacy);
    }

    public IChatComponent component() { return component; }

    public void set(IChatComponent c) {
        changed = true;
        component = c;
    }

    public static void listen(Consumer<ChatEvent> listener) { LISTENERS.add(listener); }

    public static ChatEvent fire(IChatComponent component, int id) {
        ChatEvent e = new ChatEvent(component, id);
        for (Consumer<ChatEvent> l : LISTENERS) l.accept(e);
        return e;
    }
}
