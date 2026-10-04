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
import com.example.lunarforge.module.setting.TextSetting;
import com.example.lunarforge.util.TimeFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.time.DurationFormatUtils;

public final class StopwatchChild extends Module {
    private final TextSetting stopwatchName = add(new TextSetting("stopwatchName", "New Stopwatch"));
    private final BoolSetting useCustomFormat = bool("useCustomFormat", false);
    private final ChoiceSetting<TimeFormat> timeDisplayOption = choice("timeDisplayOption", TimeFormat.DEFAULT);
    private final TextSetting customFormat = add(new TextSetting("customFormat", "HH:mm:ss").maxLength(256));
    private final KeySetting stopwatchKeybind = keyCombo("stopwatchKeybind");
    private final BoolSetting resetEveryStart = bool("resetEveryStart", true);
    private final ButtonSetting remove;

    private boolean running;

    private long startTime = -1L, counted;

    private final boolean hideWhenStopped = false;

    private StopwatchChild(ModuleStopwatch parent, String id) {
        super(id, true);
        remove = add(new ButtonSetting("remove", () -> parent.remove(this)));
        hud(new Hud());
        LunarLang.registerName(id, stopwatchName::get);
    }

    static StopwatchChild create(ModuleStopwatch parent, String id) { return new StopwatchChild(parent, id); }

    @Override protected void layout(Page page) {
        page.add(remove);
        page.section("stopwatchSetup", s -> s.add(stopwatchName, stopwatchKeybind, resetEveryStart));
        page.section("format", s -> {
            s.add(useCustomFormat);
            s.add(timeDisplayOption).hideIf(useCustomFormat::on);
            s.add(customFormat).hideIf(() -> !useCustomFormat.on());
        });
    }

    @Override protected void onDisable() { reset(); }

    void keys(boolean active) {
        if (!stopwatchKeybind.pressed(active && isEnabled())) return;
        if (running) stop(); else start();
    }

    private static long now() { return Minecraft.getSystemTime(); }

    private void start() {
        running = true;
        startTime = now();
        if (resetEveryStart.on()) counted = 0L;
    }

    private void stop() {
        running = false;
        counted += now() - startTime;
    }

    private void reset() {
        running = false;
        startTime = -1L;
        counted = 0L;
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
        if (!running && hideWhenStopped && !preview) return null;
        long elapsed = running ? counted + now() - startTime : counted;
        String time = format(elapsed);
        return preview ? Arrays.asList(time, stopwatchName.get()) : Collections.singletonList(time);
    }

    private final class Hud extends TextHud {
        Hud() { super(StopwatchChild.this, 0.0f, 0.0f, HudAnchor.TOP_RIGHT, sizes(10, 18, 22, 44, 56, 120), false, false, true, false); }

        @Override protected List<String> lines(boolean preview) {
            List<String> l = StopwatchChild.this.lines(preview);
            return l == null ? Collections.<String>emptyList() : l;
        }

        @Override protected String text(boolean preview) { return null; }
    }
}
