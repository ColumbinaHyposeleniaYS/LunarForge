package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

final class ChatText {
    private static final Pattern CODE = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private ChatText() {}

    static String plain(IChatComponent c) { return Server.strip(c.getUnformattedText()); }

    static boolean contains(IChatComponent c, String... parts) {
        String text = plain(c);
        for (String p : parts) if (p != null && !p.isEmpty() && text.contains(p)) return true;
        return false;
    }

    static boolean containsWord(IChatComponent c, String... words) {
        String text = plain(c);
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            if (Pattern.compile("(^|\\W)" + Pattern.quote(w) + "(\\W|$)").matcher(text).find()) return true;
        }
        return false;
    }

    static IChatComponent transform(IChatComponent root, Pattern pattern, Replacer replace) {
        boolean[] changed = {false};
        IChatComponent out = walk(root, pattern, replace, changed);
        return changed[0] ? out : root;
    }

    interface Replacer {
        IChatComponent apply(String match, String formatting);
    }

    private static IChatComponent walk(IChatComponent node, Pattern pattern, Replacer replace, boolean[] changed) {
        IChatComponent copy;
        if (node instanceof ChatComponentText) {
            String text = ((ChatComponentText)node).getChatComponentText_TextValue();
            copy = split(text, node.getChatStyle(), pattern, replace, changed);
        } else {
            copy = node.createCopy();
            copy.getSiblings().clear();
        }
        for (IChatComponent sibling : node.getSiblings()) copy.appendSibling(walk(sibling, pattern, replace, changed));
        return copy;
    }

    private static IChatComponent split(String text, ChatStyle style, Pattern pattern, Replacer replace, boolean[] changed) {
        StringBuilder bare = new StringBuilder();
        List<Integer> at = new ArrayList<Integer>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) { i++; continue; }
            bare.append(c);
            at.add(i);
        }
        Matcher m = pattern.matcher(bare);
        List<int[]> spans = new ArrayList<int[]>();
        while (m.find()) if (m.end() > m.start()) spans.add(new int[]{at.get(m.start()), at.get(m.end() - 1) + 1, m.start(), m.end()});
        ChatComponentText out = new ChatComponentText(spans.isEmpty() ? text : "");
        out.setChatStyle(style.createShallowCopy());
        if (spans.isEmpty()) return out;
        changed[0] = true;
        int from = 0;
        for (int[] span : spans) {
            String before = text.substring(from, span[0]);
            String codes = formatting(text.substring(0, span[0]));
            if (!before.isEmpty()) out.appendSibling(new ChatComponentText(formatting(text.substring(0, from)) + before));
            out.appendSibling(replace.apply(bare.substring(span[2], span[3]), codes));
            from = span[1];
        }
        if (from < text.length()) out.appendSibling(new ChatComponentText(formatting(text.substring(0, from)) + text.substring(from)));
        return out;
    }

    static String formatting(String s) {
        StringBuilder out = new StringBuilder();
        Matcher m = CODE.matcher(s);
        while (m.find()) {
            char c = Character.toLowerCase(m.group().charAt(1));
            if ("0123456789abcdef".indexOf(c) >= 0 || c == 'r') out.setLength(0);
            if (c != 'r') out.append(m.group());
        }
        return out.toString();
    }

    static ChatStyle styleOf(String codes) {
        ChatStyle style = new ChatStyle();
        Matcher m = CODE.matcher(codes);
        while (m.find()) {
            char c = Character.toLowerCase(m.group().charAt(1));
            int hex = "0123456789abcdef".indexOf(c);
            if (hex >= 0) {
                style = new ChatStyle();
                style.setColor(EnumChatFormatting.values()[hex]);
            } else if (c == 'l') style.setBold(true);
            else if (c == 'o') style.setItalic(true);
            else if (c == 'n') style.setUnderlined(true);
            else if (c == 'm') style.setStrikethrough(true);
            else if (c == 'k') style.setObfuscated(true);
        }
        return style;
    }

    static IChatComponent mask(IChatComponent root, Pattern pattern, final String with) {
        return transform(root, pattern, (match, codes) -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < match.length(); i++) b.append(with);
            return new ChatComponentText(codes + b);
        });
    }

    static IChatComponent text(String s, Function<ChatStyle, ChatStyle> style) {
        ChatComponentText t = new ChatComponentText(s);
        t.setChatStyle(style.apply(new ChatStyle()));
        return t;
    }
}
