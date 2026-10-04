package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.util.ArrayList;
import java.util.List;

public final class ModulePvpInfoProjectile extends Module {
    private final ModulePvpInfo parent;
    private final BoolSetting bowAccuracy = bool("bowAccuracy", true);
    private final BoolSetting rodAccuracy = bool("rodAccuracy", true);
    private final BoolSetting eggsAndSnowballsUsed = bool("eggsAndSnowballsUsed", true);
    private final BoolSetting pearlsUsed = bool("pearlsUsed", true);
    private final ColorSetting dividerColor = color("dividerColor", 0xFF808080);

    ModulePvpInfoProjectile(ModulePvpInfo parent) {
        super("PVP_INFO_PROJECTILE_CHILD", false);
        this.parent = parent;
        hud(new Hud());
    }

    private final class Hud extends PvpInfoStatsHud {
        Hud() { super(ModulePvpInfoProjectile.this, ModulePvpInfoProjectile.this.parent); }

        @Override protected void statsLayout(Page page) {
            page.section("renderOptions", s -> s.add(bowAccuracy, rodAccuracy, eggsAndSnowballsUsed, pearlsUsed));
        }

        @Override protected void colorLayout(Page page) {
            page.add(dividerColor);
        }

        @Override protected boolean showing() {
            return bowAccuracy.on() || rodAccuracy.on() || eggsAndSnowballsUsed.on() || pearlsUsed.on();
        }

        @Override protected List<Row> rows() {
            ModulePvpInfo.Stats stats = parent.stats();
            List<Row> rows = new ArrayList<Row>();
            rows.add(row(align() == Alignment.RIGHT ? 4 : 0, piece(heading(lang("projectileInfo")), headingColor)));
            if (bowAccuracy.on()) {
                rows.add(row(0, piece(lang("bowAccuracy"), statColor), piece(stats.bowAccuracy(), numberColor)));
            }

            if (eggsAndSnowballsUsed.on()) {
                rows.add(row(0, piece(lang("eggs"), statColor), piece(" / ", dividerColor),
                        piece(lang("snowballsUsed"), statColor), piece(stats.eggsAndSnowballsUsed(), numberColor)));
            }
            if (pearlsUsed.on()) {
                rows.add(row(0, piece(lang("pearlsUsed"), statColor), piece(stats.pearlsUsed(), numberColor)));
            }
            return rows;
        }
    }
}
