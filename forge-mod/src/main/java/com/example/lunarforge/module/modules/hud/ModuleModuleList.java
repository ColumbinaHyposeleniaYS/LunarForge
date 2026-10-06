package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.ModuleCatalog;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Module List overlay (classic "arraylist"): one row per enabled module,
 * widest on top, right-aligned. Every module's own "Hidden" switch (built into
 * the Module base, top of every module's settings page) excludes it from this
 * list; the Module List itself can be hidden the same way. Child modules are
 * folded into their parent and never listed twice.
 */
public final class ModuleModuleList extends Module {

    private static final String[] PREVIEW = {"Auto Clicker", "Keystrokes", "FPS"};

    private final BoolSetting background = bool("background", true).label(() -> "Background");
    private final BoolSetting textShadow = bool("textShadow", true).label(() -> "Text Shadow");
    private final ColorSetting textColor = color("textColor", 0xFFFFFFFF).label(() -> "Text Color");
    private final ColorSetting backgroundColor = color("backgroundColor", 0x90000000).label(() -> "Background Color");

    public ModuleModuleList() {
        super("MODULE_LIST", false);
        hud(new ListHud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(background, textShadow));
        page.section("colorOptions", s -> s.add(textColor, backgroundColor));
    }

    private static String displayName(Module module) {
        ModuleCatalog.Module entry = ModuleCatalog.findOrNull(module.key());
        return entry != null ? entry.name : module.id;
    }

    private final class ListHud extends HudElement {
        private final List<String> rows = new ArrayList<String>();

        ListHud() {
            super(ModuleModuleList.this, 0, 0, HudAnchor.TOP_RIGHT);
        }

        private float rowHeight() { return Draw.fontHeight() + 3.0f; }

        private float padding() { return 3.0f; }

        private void collect(boolean preview) {
            rows.clear();
            if (preview) {
                Collections.addAll(rows, PREVIEW);
                return;
            }
            List<Module> modules = new ArrayList<Module>();
            for (Module module : ModuleManager.modules()) {
                if (module.parent() != null || !module.isEnabled() || module.isHidden()) continue;
                modules.add(module);
            }
            Collections.sort(modules, new Comparator<Module>() {
                @Override public int compare(Module a, Module b) {
                    return Float.compare(Draw.width(displayName(b)), Draw.width(displayName(a)));
                }
            });
            for (Module module : modules) rows.add(displayName(module));
        }

        @Override public boolean visible(boolean preview) {
            collect(preview);
            float rowWidth = 0.0f;
            for (String row : rows) rowWidth = Math.max(rowWidth, Draw.width(row));
            if (rows.isEmpty()) return false;
            float pad = padding();
            size(rowWidth + pad * 2.0f, rows.size() * rowHeight());
            return true;
        }

        @Override public void render(boolean preview) {
            collect(preview);
            if (rows.isEmpty()) return;
            float pad = padding();
            float row = rowHeight();
            float boxWidth = width();
            float y = 0.0f;
            for (int i = 0; i < rows.size(); i++) {
                String name = rows.get(i);
                float textWidth = Draw.width(name);
                float x = boxWidth - textWidth - pad;
                if (background.on()) {
                    // bottom row fades slightly, like classic arraylists
                    int alpha = i == rows.size() - 1 ? 0x70 : 0x90;
                    Draw.rect(x - pad, y, textWidth + pad * 2.0f, row,
                            (alpha << 24) | (backgroundColor.argb() & 0xFFFFFF));
                }
                Draw.text(textColor, name, x, y + 1.5f, textShadow.on());
                y += row;
            }
        }
    }
}
