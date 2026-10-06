package com.example.lunarforge.module;

import com.example.lunarforge.gui.ui.OptionCatalog;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.modules.hud.ModuleArmorStatus;
import com.example.lunarforge.module.modules.hud.ModuleClock;
import com.example.lunarforge.module.modules.hud.ModuleCoordinates;
import com.example.lunarforge.module.modules.hud.ModuleCps;
import com.example.lunarforge.module.modules.hud.ModuleDayCounter;
import com.example.lunarforge.module.modules.hud.ModuleDirectionHud;
import com.example.lunarforge.module.modules.hud.ModuleFps;
import com.example.lunarforge.module.modules.hud.ModuleKeystrokes;
import com.example.lunarforge.module.modules.hud.ModuleMemory;
import com.example.lunarforge.module.modules.hud.ModulePing;
import com.example.lunarforge.module.modules.hud.ModulePotionCounter;
import com.example.lunarforge.module.modules.hud.ModulePvpInfo;
import com.example.lunarforge.module.modules.hud.ModuleServerAddress;
import com.example.lunarforge.module.modules.mechanic.ModuleAutoTextActions;
import com.example.lunarforge.module.modules.legit.ModuleAutoClicker;
import com.example.lunarforge.module.modules.legit.ModuleAutoTool;
import com.example.lunarforge.module.modules.legit.ModuleBlockHitMode;
import com.example.lunarforge.module.modules.legit.ModuleEagle;
import com.example.lunarforge.module.modules.legit.ModuleFastPlace;
import com.example.lunarforge.module.modules.legit.ModuleInventoryClicker;
import com.example.lunarforge.module.modules.combat.ModuleAimAssist;
import com.example.lunarforge.module.modules.combat.ModuleHitSelect;
import com.example.lunarforge.module.modules.combat.ModuleJumpReset;
import com.example.lunarforge.module.modules.combat.ModuleKnockbackDelay;
import com.example.lunarforge.module.modules.combat.ModuleWTap;
import com.example.lunarforge.module.modules.mechanic.ModuleAutoTextHotkey;
import com.example.lunarforge.module.modules.mechanic.ModuleDamageTint;
import com.example.lunarforge.module.modules.mechanic.ModuleFog;
import com.example.lunarforge.module.modules.mechanic.ModuleFov;
import com.example.lunarforge.module.modules.mechanic.ModuleLighting;
import com.example.lunarforge.module.modules.mechanic.ModuleTimeChanger;
import com.example.lunarforge.module.modules.mechanic.ModuleToggleSneak;
import com.example.lunarforge.module.modules.mechanic.ModuleTntCountdown;
import com.example.lunarforge.module.modules.mechanic.ModuleWeatherChanger;
import com.example.lunarforge.module.modules.visual.ModuleOneSevenVisuals;
import com.example.lunarforge.module.modules.visual.ModuleItemPhysics;
import com.example.lunarforge.module.modules.visual.ModuleItems2d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ModuleManager {
    private static UiModel model;
    private static final List<Module> MODULES = new ArrayList<Module>();
    private static final Map<String, Module> BY_KEY = new LinkedHashMap<String, Module>();

    private ModuleManager() {}

    public static synchronized void init(UiModel ui) {
        model = ui;
        if (!MODULES.isEmpty()) return;
        register(new ModuleFps());
        register(new ModuleCps());
        register(new ModuleToggleSneak());
        register(new ModuleArmorStatus());
        register(new ModuleKeystrokes());
        register(new ModuleClock());
        register(new ModuleMemory());
        register(new ModuleDayCounter());
        register(new ModuleServerAddress());
        register(new ModulePotionCounter());
        register(new ModulePvpInfo());
        register(new ModuleCoordinates());
        register(new ModulePing());
        register(new ModuleDirectionHud());
        register(new ModuleTntCountdown());
        register(new ModuleAutoTextHotkey());
        register(new ModuleAutoTextActions());
        register(new ModuleAutoClicker());
        register(new ModuleFastPlace());
        register(new ModuleInventoryClicker());
        register(new ModuleAutoTool());
        register(new ModuleEagle());
        register(new ModuleBlockHitMode());
        register(new ModuleWTap());
        register(new ModuleKnockbackDelay());
        register(new ModuleAimAssist());
        register(new ModuleHitSelect());
        register(new ModuleJumpReset());
        register(new ModuleTimeChanger());
        register(new ModuleWeatherChanger());
        register(new ModuleLighting());
        register(new ModuleFog());
        register(new ModuleDamageTint());
        register(new ModuleFov());
        register(new ModuleOneSevenVisuals());
        register(new ModuleItemPhysics());
        register(new ModuleItems2d());
        register(new com.example.lunarforge.module.modules.hud.ModuleCombo());
        register(new com.example.lunarforge.module.modules.hud.ModuleReachDisplay());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleZoom());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleFreelook());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleSnaplook());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleHurtCam());
        register(new com.example.lunarforge.module.modules.hud.ModuleScoreboard());
        register(new com.example.lunarforge.module.modules.hud.ModuleBossbar());
        register(new com.example.lunarforge.module.modules.hud.ModulePackDisplay());
        register(new com.example.lunarforge.module.modules.visual.ModuleMenuBlur());
        register(new com.example.lunarforge.module.modules.visual.ModuleMotionBlur());
        register(new com.example.lunarforge.module.modules.visual.ModuleColorSaturation());
        register(new com.example.lunarforge.module.modules.visual.ModuleHitColor());
        register(new com.example.lunarforge.module.modules.visual.ModuleShinyPots());
        register(new com.example.lunarforge.module.modules.visual.ModuleGlintColorizer());
        register(new com.example.lunarforge.module.modules.visual.ModuleMobSize());
        register(new com.example.lunarforge.module.modules.visual.ModuleHitbox());
        register(new com.example.lunarforge.module.modules.visual.ModuleBlockOutline());
        register(new com.example.lunarforge.module.modules.visual.ModuleHeightLimit());
        register(new com.example.lunarforge.module.modules.visual.ModuleLightOverlay());
        register(new com.example.lunarforge.module.modules.visual.ModuleParticleChanger());
        register(new com.example.lunarforge.module.modules.visual.ModuleSaturation());
        register(new com.example.lunarforge.module.modules.hud.ModuleTitles());
        register(new com.example.lunarforge.module.modules.hud.ModuleActionBar());
        register(new com.example.lunarforge.module.modules.visual.ModuleScrollableTooltips());
        register(new com.example.lunarforge.module.modules.hud.ModulePotionEffects());
        register(new com.example.lunarforge.module.modules.hud.ModuleStopwatch());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleChat());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleNickHider());
        register(new com.example.lunarforge.module.modules.server.ModuleQuickplay());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleCrosshair());
        register(new com.example.lunarforge.module.modules.mechanic.ModuleTab());
        register(new com.example.lunarforge.module.modules.server.ModuleHypixelMods());
        register(new com.example.lunarforge.module.modules.server.ModuleHypixelBedwars());
        register(new com.example.lunarforge.module.modules.visual.Module3dSkins());
        register(new com.example.lunarforge.module.modules.hud.ModuleModuleList());
        register(new com.example.lunarforge.module.modules.visual.ModuleItemESP());
        register(new com.example.lunarforge.module.modules.visual.ModuleChestESP());
        register(new com.example.lunarforge.module.modules.visual.ModuleESP());
        register(new com.example.lunarforge.module.modules.visual.ModuleNameTags());
        register(new com.example.lunarforge.module.modules.visual.ModuleBedESP());
        register(new com.example.lunarforge.module.modules.visual.ModuleViewClip());
        OptionCatalog.buttons = new java.util.function.BiConsumer<String, String>() {
            @Override public void accept(String id, String key) {
                Module m = BY_KEY.get(id.toLowerCase(Locale.ROOT));
                if (m == null) return;
                for (com.example.lunarforge.module.setting.Setting<?> s : m.settings()) {
                    if (s.key.equals(key) && s instanceof com.example.lunarforge.module.setting.ButtonSetting) {
                        ((com.example.lunarforge.module.setting.ButtonSetting)s).press();
                        return;
                    }
                }
            }
        };
        OptionCatalog.provider = new java.util.function.Function<String, OptionCatalog.Page>() {
            @Override public OptionCatalog.Page apply(String key) {
                Module m = BY_KEY.get(key);
                return m == null ? null : m.page();
            }
        };
    }

    private static void register(Module module) {
        MODULES.add(module);
        index(module);
    }

    private static void index(Module module) {
        BY_KEY.put(module.key(), module);
        for (Module child : module.children()) index(child);
    }

    public interface HudListener {
        void added(HudElement hud);
        void removed(HudElement hud);
    }

    public static HudListener hudListener;
    private static boolean subscribed;

    static void attached(Module module) {
        index(module);
        if (subscribed) subscribe(module);
        if (hudListener != null) for (HudElement hud : module.huds()) hudListener.added(hud);
    }

    static void detached(Module module) {
        for (Module child : module.children()) detached(child);
        BY_KEY.remove(module.key());
        if (subscribed) {
            MinecraftForge.EVENT_BUS.unregister(module);
            FMLCommonHandler.instance().bus().unregister(module);
        }
        if (hudListener != null) for (HudElement hud : module.huds()) hudListener.removed(hud);
    }

    private static void subscribe(Module m) {
        MinecraftForge.EVENT_BUS.register(m);
        FMLCommonHandler.instance().bus().register(m);
        for (Module child : m.children()) if (BY_KEY.get(child.key()) == child) subscribe(child);
    }

    public static void registerEvents() {
        subscribed = true;
        HypixelLocation.register();
        com.example.lunarforge.module.render.TooltipHooks.register();
        for (Module m : BY_KEY.values()) {
            MinecraftForge.EVENT_BUS.register(m);
            FMLCommonHandler.instance().bus().register(m);
        }
    }

    public static void changed(String id, String option) {
        Module m = BY_KEY.get(id.toLowerCase(Locale.ROOT));
        if (m == null) return;
        for (com.example.lunarforge.module.setting.Setting<?> s : m.settings()) if (s.key.equals(option)) s.changed();
    }

    public static List<Module> modules() { return Collections.unmodifiableList(MODULES); }

    public static Module get(String id) { return BY_KEY.get(id.toLowerCase(Locale.ROOT)); }

    public static List<HudElement> huds() {
        List<HudElement> out = new ArrayList<HudElement>();
        for (Module m : BY_KEY.values()) out.addAll(m.huds());
        return out;
    }

    public static HudElement hud(String id) {
        Module m = get(id);
        return m == null || m.huds().isEmpty() ? null : m.huds().get(0);
    }

    public static UiModel model() { return model; }

    public static String store(String id, String option, String fallback) {
        return model == null ? fallback : model.value(id, option, fallback);
    }

    public static void put(String id, String option, String value) {
        if (model != null) model.set(id, option, value);
    }

    public static void remove(String id, String option) {
        if (model != null) { model.store.remove(model.key(id, option)); model.revision++; changed(id, option); }
    }
}
