package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.*;
import com.example.lunarforge.module.setting.*;
import net.minecraft.scoreboard.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

public final class ModuleScoreboard extends Module {
    private final BoolSetting numbers = bool("numbers", false), hide = bool("hideScoreboard", false), shadow = bool("textShadow", false);
    private final BoolSetting border = bool("border", false), message = bool("displayToggleMessage", true);
    private final NumberSetting thickness = decimal("borderThickness", .5f, .5f, 3);
    private final ColorSetting background = color("backgroundColor", 0x50000000), header = color("headerColor", 0x50000000), borderColor = color("borderColor", 0x9F000000);
    private final KeySetting toggle = keyCombo("toggleHideScoreboard");
    private boolean hidden, down;
    public ModuleScoreboard() { super("SCOREBOARD", true); hud(new Hud()); }
    protected void layout(Page p) {
        p.section("generalOptions", s -> s.add(numbers, hide, shadow, thickness, border));
        p.section("toggle", s -> s.add(toggle, message));
        p.section("colorOptions", s -> s.add(background, header, borderColor));
    }
    public static boolean replacesVanilla() {
        Module m = com.example.lunarforge.module.ModuleManager.get("scoreboard");
        return m != null && m.isEnabled();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        boolean held = isEnabled() && mc().theWorld != null && mc().currentScreen == null && toggle.isDown();
        if (held && !down) {
            hidden = !hidden;
            if (message.on()) com.example.lunarforge.gui.LunarNotifications.info(
                    com.example.lunarforge.gui.ui.LunarLang.get("popups", hidden ? "scoreboardHidden" : "scoreboardDisplayed"));
        }
        down = held;
    }
    private final class Hud extends HudElement {
        private String title;
        private final List<String> names = new ArrayList<String>(), scores = new ArrayList<String>();
        Hud() { super(ModuleScoreboard.this, 0, 0, HudAnchor.MIDDLE_RIGHT); }
        public boolean editable() { return !hide.on() && !hidden; }
        public boolean visible(boolean preview) {
            if (hide.on() || hidden) return false;
            names.clear(); scores.clear();
            ScoreObjective objective = null;
            if (mc().theWorld != null && mc().thePlayer != null) {
                Scoreboard board = mc().theWorld.getScoreboard();
                ScorePlayerTeam team = board.getPlayersTeam(mc().thePlayer.getName());
                int index = team == null ? -1 : team.getChatFormat().getColorIndex();
                if (index >= 0) objective = board.getObjectiveInDisplaySlot(3 + index);
                if (objective == null) objective = board.getObjectiveInDisplaySlot(1);
            }
            if (objective == null) {
                if (!preview) return false;
                title = "\u00a7bLunar\u00a7r \u00a7lClient";
                for (String name : Arrays.asList("Steve", "Alex", "Villager", "Enderman")) { names.add(name); scores.add("\u00a7c0"); }
            } else {
                title = objective.getDisplayName(); Scoreboard board = objective.getScoreboard();
                List<Score> rows = new ArrayList<Score>();
                for (Score score : board.getSortedScores(objective)) if (score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) rows.add(score);
                for (Score score : rows.subList(Math.max(0, rows.size() - 15), rows.size())) {
                    names.add(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()), score.getPlayerName()));
                    scores.add("\u00a7c" + score.getScorePoints());
                }
            }
            float width = Draw.width(title);
            for (int i = 0; i < names.size(); i++) width = Math.max(width, Draw.width(names.get(i)) + Draw.width(": ") + Draw.width(scores.get(i)));

            if (numbers.on() && width > Draw.width(title)) width -= Math.min(width - Draw.width(title), 9);
            else if (numbers.on()) width += 4;
            size(width + 2, names.size() * Draw.fontHeight() + 10); return true;
        }
        public void render(boolean preview) {
            int h = Draw.fontHeight();
            if (!names.isEmpty()) {
                Draw.fill(header, 0, 1, width(), h); Draw.fill(background, 0, h + 1, width(), height() - h);
                if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), thickness.value());
            }
            for (int i = 0; i < names.size(); i++) {
                float y = height() - (i + 1) * h;
                Draw.text(names.get(i), 2, y, -1, shadow.on());
                if (!numbers.on()) Draw.text(scores.get(i), width() - Draw.width(scores.get(i)), y, -1, shadow.on());
            }
            if (!names.isEmpty()) Draw.text(title, (width() - Draw.width(title)) / 2, height() - (names.size() + 1) * h, -1, shadow.on());
        }
    }
}
