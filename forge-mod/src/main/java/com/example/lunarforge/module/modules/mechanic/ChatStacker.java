package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.ChatEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

final class ChatStacker {
    private static final class Stack {
        int size = 1, id;
        long timestamp = System.currentTimeMillis();
        Stack(int id) { this.id = id; }
        int grow(int newId) { timestamp = System.currentTimeMillis(); id = newId; return ++size; }
        boolean expired(long ms) { return System.currentTimeMillis() > timestamp + ms; }
    }

    private final Map<IChatComponent, Stack> stacks = new HashMap<IChatComponent, Stack>();
    private IChatComponent last;
    private int lastCount;

    void clear() {
        last = null;
        lastCount = 0;
        stacks.clear();
    }

    void onMessage(ModuleChat chat, ChatEvent message) {
        IChatComponent component = message.component();
        int id = message.id;
        long timeframe = chat.timeBasedStackMessagesTimeframe.intValue() * 1000L;
        for (Iterator<Stack> it = stacks.values().iterator(); it.hasNext(); ) if (it.next().expired(timeframe)) it.remove();
        if (chat.chatStackIgnoreBlank.on() || chat.chatStackIgnoreBreak.on()) {
            String text = ChatText.plain(component);
            if (chat.chatStackIgnoreBlank.on() && text.isEmpty()) return;
            if (chat.chatStackIgnoreBreak.on() && isBreak(text)) return;
        }
        Stack stack = stacks.get(component);
        if (component.equals(last)) {
            int n;
            if (stack != null) lastCount = n = stack.grow(id);
            else n = ++lastCount;
            message.set(counted(component, n));
            message.removeLast = true;
            return;
        }
        last = component;
        lastCount = 1;
        if (!chat.stackMessagesTimeBased.on()) return;
        if (stack != null) {
            ChatHooks.deleteMessage(stack.id);
            int n = stack.grow(id) + (lastCount - 1);
            message.set(counted(component, n));
            stacks.put(component, stack);
        } else {
            stacks.put(component, new Stack(id));
        }
    }

    private static IChatComponent counted(IChatComponent component, int n) {
        ChatComponentText suffix = new ChatComponentText(" [x" + n + "]");
        suffix.getChatStyle().setColor(EnumChatFormatting.GRAY);
        return component.createCopy().appendSibling(suffix);
    }

    private static boolean isBreak(String s) {
        char first = ' ';
        for (char c : s.toCharArray()) {
            if (first == ' ') {
                if (c != '-' && c != '▬') return false;
                first = c;
            }
            if (c != first) return false;
        }
        return true;
    }
}
