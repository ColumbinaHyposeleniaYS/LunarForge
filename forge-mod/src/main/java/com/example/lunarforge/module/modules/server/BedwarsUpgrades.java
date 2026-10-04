package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.ChatEvent;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtils;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class BedwarsUpgrades extends Module {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final List<String> FORGES = Arrays.asList("Iron", "Golden", "Emerald", "Molten");
    private static final Pattern PURCHASE = Pattern.compile("\\w{1,16} (?:purchased|unlocked) (?<name>[\\w\\s]+?)(?: (?<level>[IVXLCDM]+))?");
    private static final Pattern TRAP_BOUGHT = Pattern.compile("^\\w{1,16} (?:purchased|unlocked) ([a-zA-Z0-9-]+(?: [a-zA-Z0-9-]+)*) (?:Trap|trap)$");
    private static final Pattern TRAP_REMOVED = Pattern.compile("^Removed ([a-zA-Z0-9-]+(?: [a-zA-Z0-9-]+)*) (?:Trap|trap) from the queue!$");
    private static final Pattern TRAP_SET_OFF = Pattern.compile("([a-zA-Z0-9-]+(?: [a-zA-Z0-9-]+)*)\\s+(?:Trap|trap) (?:was set off!|set off by .+)$");
    private static final Pattern ELIMINATED = Pattern.compile("TEAM ELIMINATED > (Red|Blue|Green|Yellow|Aqua|White|Pink|Gray) Team has been eliminated!");

    private final ModuleHypixelBedwars bedwars;
    private final BoolSetting hideWhenNoUpgrades = bool("hideWhenNoUpgrades", true);
    private final BoolSetting displayUpgradeCost = bool("displayUpgradeCost", true);
    private final BoolSetting showUpgrades = bool("showUpgrades", true);
    private final BoolSetting showTraps = bool("showTraps", true);
    private final BoolSetting effectNames = bool("effectNames", false);
    private final BoolSetting romanNumerals = bool("romanNumerals", false);
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ColorSetting upgradesTitleColor = color("upgradesTitleColor", -171);
    private final ColorSetting upgradeTextColor = color("upgradeTextColor", -1);
    private final ColorSetting trapsTitleColor = color("trapsTitleColor", -171);
    private final ColorSetting trapsTextColor = color("trapsTextColor", -1);
    private final ColorSetting upgradeCostColor = color("upgradeCostColor", -12211035);
    private final ColorSetting maxUpgradeCostColor = color("maxUpgradeCostColor", -13447886);

    private static final class Upgrade {
        final String name, effect;
        final int tiers;
        final int[] costsSmall, costsTeams;
        Upgrade(String name, String effect, int[] small, int[] teams) { this.name = name; this.effect = effect; this.tiers = small.length; costsSmall = small; costsTeams = teams; }
        boolean forge() { return "Forge".equals(name); }
    }

    private static final Map<String, Upgrade> UPGRADES = new LinkedHashMap<String, Upgrade>();
    static {
        for (Upgrade u : new Upgrade[]{
            new Upgrade("Sharpened Swords", "sharpness", new int[]{4}, new int[]{8}),
            new Upgrade("Reinforced Armor", "protection", new int[]{2, 4, 8, 16}, new int[]{5, 10, 20, 30}),
            new Upgrade("Maniac Miner", "haste", new int[]{2, 4}, new int[]{4, 6}),
            new Upgrade("Forge", "resources", new int[]{2, 4, 6, 8}, new int[]{4, 8, 12, 16}),
            new Upgrade("Heal Pool", "regeneration", new int[]{1}, new int[]{3}),
            new Upgrade("Cushioned Boots", "featherFalling", new int[]{1, 2}, new int[]{2, 4}),
            new Upgrade("DeadShot", "damage", new int[]{3, 5, 7, 10}, new int[]{3, 5, 7, 10})}) UPGRADES.put(u.name, u);
    }

    private enum Mode { SOLO, DOUBLES, OTHER }

    private final Map<Upgrade, Integer> bought = new LinkedHashMap<Upgrade, Integer>();
    private final List<String> traps = new ArrayList<String>();
    private Mode mode = Mode.SOLO;
    private String server;

    BedwarsUpgrades(ModuleHypixelBedwars bedwars) {
        super("HYPIXEL_BEDWARS_UPGRADE_DISPLAY_CHILD", true);
        this.bedwars = bedwars;
        hud(new Hud());
        ChatEvent.listen(this::onMessage);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(hideWhenNoUpgrades, textShadow, background, border);
            s.group(showUpgrades, g -> g.add(romanNumerals, effectNames, upgradesTitleColor, upgradeTextColor));
            s.group(showTraps, g -> g.add(trapsTitleColor, trapsTextColor));
            s.group(displayUpgradeCost, g -> g.add(upgradeCostColor, maxUpgradeCostColor));
        });
        page.section("colorOptions", s -> {
            s.add(backgroundColor).hideIf(() -> !background.on());
            s.add(borderColor, borderThickness).hideIf(() -> !border.on());
        });
    }

    private void clear() { traps.clear(); bought.clear(); }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !bedwars.inGame()) return;
        HypixelLocation.Location l = HypixelLocation.get();
        if (l == null) return;
        if (!Objects.equals(server, l.server)) { server = l.server; clear(); }
        if (l.mode != null) {
            String m = l.mode.toLowerCase(Locale.ROOT);
            if (m.contains("_eight_one")) mode = Mode.SOLO;
            else if (m.contains("_eight_two")) mode = Mode.DOUBLES;
            else if (m.contains("_four_three") || m.contains("_four_four") || m.contains("_two_four")) mode = Mode.OTHER;
        }
    }

    private void onMessage(ChatEvent e) {
        if (e.cancelled || !bedwars.inGame()) return;
        String text = e.plain;
        if (mode == Mode.SOLO && text.equals("You have been eliminated!")) { clear(); return; }
        if (text.trim().equals("- Max Team Upgrades")) {
            for (Upgrade u : UPGRADES.values()) if (!"DeadShot".equals(u.name)) bought.put(u, u.tiers);
            return;
        }
        Matcher m = TRAP_BOUGHT.matcher(text);
        if (m.matches()) { traps.add(m.group(1)); return; }
        m = PURCHASE.matcher(text);
        if (m.matches()) {
            String name = m.group("name").trim();
            if (name.toLowerCase(Locale.ROOT).contains("forge")) name = "Forge";
            Upgrade u = UPGRADES.get(name);
            if (u != null) {
                String level = m.group("level");
                if (level != null) level = level.replaceAll("\\s", "").trim();
                int tier = level != null && !level.isEmpty() ? Arrays.asList(ROMAN).indexOf(level) : tier(u) + 1;
                bought.put(u, tier);
            }
            return;
        }
        if (mode != Mode.SOLO && (m = ELIMINATED.matcher(text)).matches()) {
            Minecraft mc = Minecraft.getMinecraft();
            String own = StringUtils.stripControlCodes(mc.thePlayer.getDisplayName().getFormattedText());
            if (!own.isEmpty()) {
                char c = own.charAt(0);
                if (c == 'S') c = 'G';
                if (c == m.group(1).charAt(0)) clear();
            }
            return;
        }
        m = TRAP_SET_OFF.matcher(text);
        if (m.matches()) { traps.remove(m.group(1)); bedwars.trapTriggered(); return; }
        m = TRAP_REMOVED.matcher(text);
        if (m.matches()) traps.remove(m.group(1));
    }

    private int tier(Upgrade u) { Integer t = bought.get(u); return t == null ? 0 : t; }

    private String line(Upgrade u, int tier) {
        String name = effectNames.on() ? lang(u.effect) : u.name;
        if (u.forge()) return FORGES.get(Math.min(tier, FORGES.size() - 1)) + " " + name;
        if (u.tiers == 1) return name;
        return name + " " + (romanNumerals.on() ? ROMAN[Math.max(0, Math.min(tier, 10))] : String.valueOf(tier));
    }

    private final class Hud extends HudElement {
        Hud() {
            super(BedwarsUpgrades.this, 0.0f, 0.0f, HudAnchor.TOP_LEFT);
            size(140.0f, 40.0f);
        }

        @Override public boolean visible(boolean preview) {
            if (!bedwars.isEnabled() || !bedwars.inBedwars()) { size(0, 0); return false; }
            if (preview) return showUpgrades.on() || showTraps.on();
            HypixelLocation.Location l = HypixelLocation.get();
            if (!bedwars.inGame() || l != null && l.lobby()) return false;
            if (hideWhenNoUpgrades.on() && bought.isEmpty() && traps.isEmpty()) return false;
            if (width() == 0.0f) size(140.0f, 40.0f);
            return showUpgrades.on() || showTraps.on();
        }

        @Override public void render(boolean preview) {
            if (background.on()) Draw.fill(backgroundColor, 0, 0, width(), height());
            if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            int n = Draw.fontHeight() + 1;
            boolean shadow = textShadow.on();
            float x = 3.0f, y = 3.0f;
            if (showUpgrades.on()) {
                Draw.text(upgradesTitleColor, "§l" + lang("upgrades"), x, y, shadow);
                y += n;
                if (bought.isEmpty()) {
                    Draw.text(upgradeTextColor, lang("none"), x, y, shadow);
                    y += n;
                } else {
                    for (Map.Entry<Upgrade, Integer> e : new ArrayList<Map.Entry<Upgrade, Integer>>(bought.entrySet())) {
                        Upgrade u = e.getKey();
                        int tier = e.getValue();
                        Draw.text(upgradeTextColor, line(u, tier), x, y, shadow);
                        if (displayUpgradeCost.on()) {
                            if (tier < u.tiers) {
                                int cost = mode == Mode.SOLO || mode == Mode.DOUBLES ? u.costsSmall[tier] : u.costsTeams[tier];
                                Draw.text(upgradeCostColor, cost + " ⬆", x + (cost > 9 ? 115 : 120), y, shadow);
                            } else {
                                Draw.text(maxUpgradeCostColor, " ✔", x + 115.0f, y, shadow);
                            }
                        }
                        y += n;
                    }
                }
            }
            if (showTraps.on()) {
                y += 3.0f;
                Draw.text(trapsTitleColor, "§l" + lang("traps"), x, y, shadow);
                y += n;
                if (traps.isEmpty()) {
                    Draw.text(trapsTextColor, lang("none"), x, y, shadow);
                    y += n;
                } else {
                    for (String trap : new ArrayList<String>(traps)) {
                        Draw.text(trapsTextColor, trap + " Trap", x, y, shadow);
                        y += n;
                    }
                }
            }
            size(140.0f, Math.round(y + 1.0f));
        }
    }
}
