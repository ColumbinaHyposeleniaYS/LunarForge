package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.ArrayList;
import java.util.List;

public final class ModulePvpInfoHealth extends Module {
    private final ModulePvpInfo parent;
    private final BoolSetting lostHealth = bool("lostHealth", true);
    private final BoolSetting recoveredHealth = bool("recoveredHealth", true);
    private final BoolSetting gapplesUsed = bool("gapplesUsed", true);
    private final BoolSetting potionsUsed = bool("potionsUsed", true);

    ModulePvpInfoHealth(ModulePvpInfo parent) {
        super("PVP_INFO_HEALTH_CHILD", false);
        this.parent = parent;
        hud(new Hud());
    }

    private final class Hud extends PvpInfoStatsHud {
        Hud() { super(ModulePvpInfoHealth.this, ModulePvpInfoHealth.this.parent); }

        @Override protected void statsLayout(Page page) {
            page.section("renderOptions", s -> s.add(lostHealth, recoveredHealth, gapplesUsed, potionsUsed));
        }

        @Override protected boolean showing() {
            return lostHealth.on() || recoveredHealth.on() || gapplesUsed.on() || potionsUsed.on();
        }

        @Override protected List<Row> rows() {
            ModulePvpInfo.Stats stats = parent.stats();
            List<Row> rows = new ArrayList<Row>();
            rows.add(row(align() == Alignment.RIGHT ? 4 : 0, piece(heading(lang("healthInfo")), headingColor)));
            if (lostHealth.on()) {
                rows.add(row(0, piece(lang("lostHealth"), statColor), piece(stats.lostHealth(), numberColor)));
            }
            if (recoveredHealth.on()) {
                rows.add(row(0, piece(lang("regennedHealth"), statColor), piece(stats.recoveredHealth(), numberColor)));
            }
            if (gapplesUsed.on()) {
                rows.add(row(0, piece(lang("gapplesUsed"), statColor), piece(stats.gapplesUsed(), numberColor)));
            }
            if (potionsUsed.on()) {
                rows.add(row(0, piece(lang("healthPotionsUsed"), statColor), piece(stats.potionsUsed(), numberColor)));
            }
            return rows;
        }
    }
}
