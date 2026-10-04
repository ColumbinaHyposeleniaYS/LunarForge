package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.ChatEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

final class ChatFilter {
    private static List<Pattern> normal, high;
    private static Pattern normalWords, highWords;

    private List<Pattern> patterns;
    private Pattern custom = Pattern.compile("");

    private static synchronized void loadLists() {
        if (normal != null) return;
        normal = new ArrayList<Pattern>();
        high = new ArrayList<Pattern>();
        try (InputStream in = Minecraft.getMinecraft().getResourceManager()
                .getResource(new ResourceLocation("lunarforge", "profanity/profanity_filter.json")).getInputStream()) {
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject regex = root.getAsJsonObject("regex"), words = root.getAsJsonObject("words");
            for (JsonElement e : regex.getAsJsonArray("normal")) normal.add(Pattern.compile(e.getAsString(), Pattern.CASE_INSENSITIVE));
            for (JsonElement e : regex.getAsJsonArray("high")) high.add(Pattern.compile(e.getAsString(), Pattern.CASE_INSENSITIVE));
            normalWords = Pattern.compile("\\b(" + join(words.getAsJsonArray("normal")) + ")\\b", Pattern.CASE_INSENSITIVE);
            highWords = Pattern.compile("\\b(" + join(words.getAsJsonArray("high")) + ")\\b", Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            LogManager.getLogger("LunarForge").warn("Could not find profanity filter json file: lunar:profanity/profanity_filter.json");
        }
    }

    private static String join(JsonArray words) {
        StringBuilder b = new StringBuilder();
        for (JsonElement e : words) { if (b.length() > 0) b.append('|'); b.append(e.getAsString()); }
        return b.toString();
    }

    static boolean profane(String text) {
        loadLists();
        List<Pattern> list = new ArrayList<Pattern>(high);
        if (highWords != null) list.add(highWords);
        for (Pattern p : list) if (p.matcher(text).find()) return true;
        return false;
    }

    boolean apply(ModuleChat chat, ChatEvent message) {
        ModuleChat.ChatColor color = chat.chatNameColor.get();
        boolean bold = chat.chatNameBold.on(), italic = chat.chatNameItalic.on(), underline = chat.chatNameUnderline.on();
        boolean strike = chat.chatNameStrikethrough.on(), obfuscated = chat.chatNameObfuscated.on();
        if (color != ModuleChat.ChatColor.OFF || bold || italic || underline || strike || obfuscated) {
            String name = Minecraft.getMinecraft().thePlayer.getName();
            IChatComponent before = message.component();
            IChatComponent after = ChatText.transform(before, Pattern.compile(Pattern.quote(name)), (match, codes) -> {
                ChatStyle style = ChatText.styleOf(codes);
                if (color.formatting != null) style.setColor(color.formatting);
                if (bold) style.setBold(true);
                if (italic) style.setItalic(true);
                if (underline) style.setUnderlined(true);
                if (strike) style.setStrikethrough(true);
                if (obfuscated) style.setObfuscated(true);
                ChatComponentText t = new ChatComponentText(match);
                t.setChatStyle(style);
                return t;
            });
            if (after != before) message.set(after);
        }
        IChatComponent filtered = filter(message.component());
        if (filtered != null) {
            message.filtered = true;
            message.set(filtered);
        }
        if (message.filtered && chat.stopProfaneMessages.on()) {
            message.cancelled = true;
            return true;
        }
        return false;
    }

    private IChatComponent filter(IChatComponent component) {
        if (patterns == null) return null;
        IChatComponent out = component;
        for (Pattern p : patterns) out = ChatText.mask(out, p, "*");
        return out == component ? null : out;
    }

    private static List<Pattern> usable(List<Pattern> list) {
        List<Pattern> out = new ArrayList<Pattern>();
        for (Pattern p : list) if (!p.toString().isEmpty() && !p.toString().contains("()")) out.add(p);
        return out;
    }

    void select(ModuleChat.Profanity mode) {
        loadLists();
        List<Pattern> list;
        switch (mode) {
            case NORMAL:
                list = new ArrayList<Pattern>(normal);
                if (normalWords != null) list.add(normalWords);
                list.add(custom);
                break;
            case HIGH:
                list = new ArrayList<Pattern>(high);
                if (highWords != null) list.add(highWords);
                list.add(custom);
                break;
            case CUSTOM:
                list = new ArrayList<Pattern>();
                list.add(custom);
                break;
            default:
                list = null;
        }
        patterns = list == null ? null : usable(list);
    }

    void load(File file, ModuleChat.Profanity mode) {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) continue;
                b.append('(').append(Pattern.quote(line)).append(')');
                if (i != lines.size() - 1) b.append('|');
            }
            custom = Pattern.compile("(" + b + ")", Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            LogManager.getLogger("LunarForge").warn("Loading ChatMod", e);
        }
        select(mode);
    }
}
