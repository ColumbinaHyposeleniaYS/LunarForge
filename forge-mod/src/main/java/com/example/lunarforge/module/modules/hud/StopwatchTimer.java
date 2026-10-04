package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ButtonSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.TextSetting;
import com.example.lunarforge.util.DesktopNotify;
import com.example.lunarforge.util.TimeFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.time.DurationFormatUtils;

public final class StopwatchTimer extends Module {
    final TextSetting timerName = add(new TextSetting("timerName", "New Timer"));
    private final KeySetting startStopTimerKey = keyCombo("startStopTimerKey");
    private final KeySetting pauseTimerKey = keyCombo("pauseTimerKey");
    final NumberSetting hours = integer("hours", 0, 0, 23);
    final NumberSetting minutes = integer("minutes", 0, 0, 59);
    final NumberSetting seconds = integer("seconds", 0, 0, 59);
    final BoolSetting loopOnEnd = bool("loopOnEnd", false);
    private final BoolSetting useCustomFormat = bool("useCustomFormat", false);
    private final ChoiceSetting<TimeFormat> timeDisplayOption = choice("timeDisplayOption", TimeFormat.DEFAULT);
    private final TextSetting customFormat = add(new TextSetting("customFormat", "HH:mm:ss"));
    final BoolSetting showTitleAction = bool("showTitleAction", false);
    final TextSetting titleText = add(new TextSetting("titleText", ""));
    final BoolSetting desktopNotification = bool("desktopNotification", false);
    final BoolSetting ingameNotification = bool("ingameNotification", false);
    final BoolSetting playSound = bool("playSound", false);
    private final ButtonSetting remove;
    private final ButtonSetting stopTimer = add(new ButtonSetting("stopTimer", this::stop));

    private long end, pausedAt, left;
    private boolean paused;

    private final boolean hideWhenStopped = true;

    private StopwatchTimer(ModuleStopwatch parent, String id) {
        super(id, true);
        remove = add(new ButtonSetting("remove", () -> parent.remove(this)));
        int n = parent.timers.size() % 100;
        hud(new Hud(-10.0f - n / 10 * 56, 30.0f + n % 10 * 18));
        LunarLang.registerName(id, timerName::get);
    }

    static StopwatchTimer create(ModuleStopwatch parent, String id) { return new StopwatchTimer(parent, id); }

    @Override protected void layout(Page page) {
        page.add(remove);
        page.add(stopTimer);
        page.section("timerSetup", s -> s.add(timerName, startStopTimerKey, pauseTimerKey, hours, minutes, seconds, loopOnEnd));
        page.section("format", s -> {
            s.add(useCustomFormat);
            s.add(timeDisplayOption).hideIf(useCustomFormat::on);
            s.add(customFormat).hideIf(() -> !useCustomFormat.on());
        });
        page.section("actionSetup", s -> {
            s.group(showTitleAction, g -> g.add(titleText));
            s.add(playSound);
            s.add(ingameNotification, desktopNotification).hideIf(() -> !DesktopNotify.supported());
        });
    }

    @Override protected void onDisable() { stop(); }

    void keys(boolean inGame) {
        if (startStopTimerKey.pressed(inGame && isEnabled())) {
            if (!running()) start(); else stop();
        }
        if (pauseTimerKey.pressed(inGame) && running()) togglePause();
    }

    private static long now() { return Minecraft.getSystemTime(); }

    void start() {
        end = now() + total();
        left = 0L;
        paused = false;
    }

    void stop() {
        end = 0L;
        left = 0L;
    }

    private void togglePause() {
        if (paused) {
            end += now() - pausedAt;
            paused = false;
            pausedAt = 0L;
        } else {
            paused = true;
            pausedAt = now();
        }
    }

    private long remaining() {
        if (!running()) return 0L;
        long l = paused ? end - pausedAt : end - now();
        return Math.max(l, 0L);
    }

    boolean running() { return end != 0L; }

    boolean finished() { return running() && !paused && end <= now(); }

    private long total() {
        long s = (long)hours.intValue() * 3600L + (long)minutes.intValue() * 60L + (long)seconds.intValue();
        return Math.max(s, 1L) * 1000L;
    }

    private String format(long ms) {
        if (ms < 0L) ms = 0L;
        if (useCustomFormat.on()) {
            try { return DurationFormatUtils.formatDuration(ms, customFormat.get()); }
            catch (Exception e) { return "Format Error"; }
        }
        return timeDisplayOption.get().format(ms);
    }

    private List<String> lines(boolean preview) {
        if (running()) return Collections.singletonList(format(remaining()));
        long shown = left > 0L ? left : total();
        if (preview) return Arrays.asList(format(shown), timerName.get());
        if (hideWhenStopped) return null;
        return Collections.singletonList(format(shown));
    }

    private final class Hud extends TextHud {
        Hud(float x, float y) {
            super(StopwatchTimer.this, x, y, HudAnchor.TOP_RIGHT, sizes(10, 18, 22, 44, 56, 120), false, false, true, false);
        }

        @Override protected List<String> lines(boolean preview) {
            List<String> l = StopwatchTimer.this.lines(preview);
            return l == null ? Collections.<String>emptyList() : l;
        }

        @Override protected String text(boolean preview) { return null; }
    }
}
