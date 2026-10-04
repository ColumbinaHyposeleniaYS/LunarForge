package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.ArrayList;
import java.util.List;

public final class ModulePvpInfoMelee extends Module {
    private final ModulePvpInfo parent;
    private final BoolSetting hitAccuracy = bool("hitAccuracy", true);
    private final BoolSetting hitsTaken = bool("hitsTaken", true);
    private final BoolSetting longestCombo = bool("longestCombo", true);
    private final BoolSetting wTapAccuracy = bool("wTapAccuracy", true);

    ModulePvpInfoMelee(ModulePvpInfo parent) {
        super("PVP_INFO_MELEE_CHILD", false);
        this.parent = parent;
        hud(new Hud());
    }

    private final class Hud extends PvpInfoStatsHud {
        Hud() { super(ModulePvpInfoMelee.this, ModulePvpInfoMelee.this.parent); }

        @Override protected void statsLayout(Page page) {
            page.section("renderOptions", s -> s.add(hitAccuracy, hitsTaken, longestCombo, wTapAccuracy));
        }

        @Override protected boolean showing() {
            return hitAccuracy.on() || hitsTaken.on() || longestCombo.on() || wTapAccuracy.on();
        }

        @Override protected List<Row> rows() {
            ModulePvpInfo.Stats stats = parent.stats();
            List<Row> rows = new ArrayList<Row>();
            rows.add(row(align() == Alignment.RIGHT ? 4 : 0, piece(heading(lang("meleeInfo")), headingColor)));
            if (hitAccuracy.on()) {
                rows.add(row(0, piece(lang("meleeAccuracy"), statColor), piece(stats.meleeAccuracy(), numberColor)));
            }
            if (hitsTaken.on()) {
                rows.add(row(0, piece(lang("hitsTaken"), statColor), piece(stats.hitsTaken(), numberColor)));
            }
            if (longestCombo.on()) {
                rows.add(row(0, piece(lang("longestCombo"), statColor), piece(stats.longestCombo(), numberColor)));
            }
            if (wTapAccuracy.on()) {
                rows.add(row(0, piece(lang("wTapAccuracy"), statColor), piece(stats.wTapAccuracy(), numberColor)));
            }
            return rows;
        }
    }
}
