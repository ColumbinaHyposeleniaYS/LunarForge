package com.example.lunarforge.module;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.scoreboard.ScoreObjective;
import org.apache.commons.lang3.text.WordUtils;

public final class Server {
    private static final Pattern BRAND_PATTERN = Pattern.compile("(.+) (?:<- .+)?");
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private Server() {}

    public static boolean hypixel() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return false;
        String brand = player.getClientBrand();
        if (brand == null) return false;
        Matcher matcher = BRAND_PATTERN.matcher(brand);
        return matcher.find() && matcher.group(1).startsWith("Hypixel BungeeCord");
    }

    public static String serverName() {
        Minecraft client = Minecraft.getMinecraft();
        if (client.thePlayer == null || client.getCurrentServerData() == null || client.theWorld == null) return null;
        if (!hypixel()) return null;
        ScoreObjective objective = client.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) return null;
        String name = STRIP_COLOR_PATTERN.matcher(objective.getDisplayName()).replaceAll("");
        if (name.equalsIgnoreCase("skyblock co-op")) name = "SKYBLOCK";
        return WordUtils.capitalize(name.toLowerCase(), null);
    }

    public static boolean skyblock() { return "Skyblock".equals(serverName()); }

    public static String strip(String text) { return text == null ? null : STRIP_COLOR_PATTERN.matcher(text).replaceAll(""); }
}
