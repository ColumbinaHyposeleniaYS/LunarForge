package com.example.lunarforge.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChatSendEvent {
    private static final List<Consumer<ChatSendEvent>> LISTENERS = new ArrayList<Consumer<ChatSendEvent>>();

    public static Consumer<String> sent;

    private String message;
    public boolean cancelled;

    private ChatSendEvent(String message) { this.message = message; }

    public String getMessage() { return message; }

    public void setMessage(String m) { message = m; }

    public static void listen(Consumer<ChatSendEvent> listener) { LISTENERS.add(listener); }

    public static String fire(String message) {
        ChatSendEvent e = new ChatSendEvent(message);
        for (Consumer<ChatSendEvent> l : LISTENERS) l.accept(e);
        if (e.cancelled) return null;
        if (sent != null) sent.accept(e.message);
        return e.message;
    }
}
