package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.ChatEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;

final class ChatHeads {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String SEPARATORS = "»›:>";

    private final LinkedHashMap<Integer, NetworkPlayerInfo> byMessage = new LinkedHashMap<Integer, NetworkPlayerInfo>();

    void register(ChatEvent message) {
        NetworkPlayerInfo info = find(message.plain);
        if (info == null) return;
        byMessage.put(message.id, info);
        Iterator<Integer> it = byMessage.keySet().iterator();
        while (byMessage.size() > 1200 && it.hasNext()) { it.next(); it.remove(); }
    }

    private NetworkPlayerInfo find(String text) {
        NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
        if (net == null || net.getPlayerInfoMap() == null || net.getPlayerInfoMap().isEmpty()) return null;
        Map<String, NetworkPlayerInfo> players = new HashMap<String, NetworkPlayerInfo>();
        for (NetworkPlayerInfo info : net.getPlayerInfoMap()) {
            if (info.getGameProfile() == null || info.getGameProfile().getName() == null) continue;
            players.put(info.getGameProfile().getName().toLowerCase(Locale.ROOT), info);
        }
        String name = sender(text, players.keySet());
        return name == null ? null : players.get(name.toLowerCase(Locale.ROOT));
    }

    private static String sender(String text, Set<String> names) {
        Matcher m = NAME.matcher(text);
        while (m.find()) {
            if (m.start() > 40) return null;
            if (!(bracketed(text, m) || separator(text, m) != '\0') || !names.contains(m.group().toLowerCase(Locale.ROOT))) continue;
            return m.group();
        }
        return null;
    }

    ResourceLocation skin(int id) {
        NetworkPlayerInfo info = byMessage.get(id);
        return info == null ? null : info.getLocationSkin();
    }

    private static char separator(String s, Matcher m) {
        int i = skipSpaces(s, m.end());
        if (i < s.length() && s.charAt(i) == '[') {
            int close = s.indexOf(']', i);
            if (close == -1) return '\0';
            i = skipSpaces(s, close + 1);
        }
        return i < s.length() && SEPARATORS.indexOf(s.charAt(i)) != -1 ? s.charAt(i) : '\0';
    }

    private static boolean bracketed(String s, Matcher m) {
        return m.start() > 0 && s.charAt(m.start() - 1) == '<' && m.end() < s.length() && s.charAt(m.end()) == '>';
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && s.charAt(i) == ' ') i++;
        return i;
    }
}
