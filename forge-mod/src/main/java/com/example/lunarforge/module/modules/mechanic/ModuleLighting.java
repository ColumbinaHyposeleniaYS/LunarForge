package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleLighting extends Module {
    private final BoolSetting fullBright = bool("fullBright", true);

    private final NumberSetting brightnessBoost = decimal("brightnessBoost", 1.0f, 1.0f, 10.0f);

    private final KeySetting fullBrightToggle = keyCombo("fullBrightToggle");

    private boolean gammaOverridden;

    private float savedGamma;

    private boolean fullBrightApplied;

    private boolean toggleHeld;

    public ModuleLighting() {
        super("LIGHTING", true);
    }

    @Override protected void layout(Page page) {
        page.section("performanceOptions", s -> s.add(fullBright, fullBrightToggle));
        page.section("brightnessOptions", s -> s.add(brightnessBoost));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        boolean down = isEnabled() && mc().currentScreen == null && fullBrightToggle.isDown();
        if (down && !toggleHeld) fullBright.set(!fullBright.on());
        toggleHeld = down;
        applyGammaOverride();
    }

    @Override protected void onDisable() {
        applyGammaOverride();
    }

    private void applyGammaOverride() {
        boolean applied = isEnabled() && !shadersActive();
        boolean full = applied && fullBright.on();
        if (applied) {
            setGammaOverride(full ? 100.0f : brightnessBoost.get());
            gammaOverridden = true;
        } else if (gammaOverridden) {
            removeGammaOverride();
            gammaOverridden = false;
        }
        setFullBrightApplied(full);
    }

    private void setFullBrightApplied(boolean value) {
        if (fullBrightApplied == value) return;
        fullBrightApplied = value;
        if (!value) warnDisableFullBright();
        if (mc().renderGlobal == null) return;
        mc().renderGlobal.loadRenderers();
    }

    private void warnDisableFullBright() {
        if (mc().theWorld == null) return;
        if (rewindActive()) return;
        LunarNotifications.push(LunarNotifications.Type.INFO, null,
            LunarLang.get("gui.lightingMod", "disable_full_bright_in_world_warning"), 5000L);
    }

    public void disableAutomatically() {
        if (rewindActive() || shadersActive()) return;
        if (isEnabled()) {
            LunarNotifications.push(LunarNotifications.Type.INFO, null,
                LunarLang.get("gui.lightingMod", "disabled_automatically"), 3000L);
        }
        setEnabled(false);
    }

    public boolean isFullBrightActive() {
        Module lightOverlay = ModuleManager.get("light_overlay");
        if (lightOverlay != null && lightOverlay.isEnabled()) return false;
        return fullBrightApplied;
    }

    public boolean fullBrightApplied() {
        return fullBrightApplied;
    }

    private static boolean shadersActive() {
        return false;
    }

    private static boolean rewindActive() {
        Module rewind = ModuleManager.get("rewind");
        if (rewind == null) return false;
        return false;
    }

    private static ModuleLighting instance() {
        Module module = ModuleManager.get("LIGHTING");
        return module instanceof ModuleLighting ? (ModuleLighting)module : null;
    }

    public static void beforeSave(GameSettings settings) {
        ModuleLighting lighting = instance();
        if (lighting == null || !lighting.gammaOverridden) return;
        float override = settings.gammaSetting;
        settings.gammaSetting = lighting.savedGamma;
        lighting.savedGamma = override;
    }

    public static void afterSave(GameSettings settings) {
        beforeSave(settings);
    }

    public static void afterSetOption(GameSettings settings, GameSettings.Options option) {
        ModuleLighting lighting = instance();
        if (lighting == null || !lighting.gammaOverridden || option != GameSettings.Options.GAMMA) return;
        float override = lighting.isEnabled() && lighting.fullBright.on() ? 100.0f : lighting.brightnessBoost.get();
        lighting.savedGamma = settings.gammaSetting;
        settings.gammaSetting = override;
    }

    private void setGammaOverride(float value) {
        if (!gammaOverridden) {
            gammaOverridden = true;
            savedGamma = mc().gameSettings.gammaSetting;
        }
        mc().gameSettings.gammaSetting = value;
    }

    private void removeGammaOverride() {
        if (!gammaOverridden) return;
        gammaOverridden = false;
        mc().gameSettings.gammaSetting = savedGamma;
    }
}
