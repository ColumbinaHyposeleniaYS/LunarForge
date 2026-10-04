package com.example.lunarforge.module.hud;

import com.example.lunarforge.feature.ClientFeature;
import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.NumberSetting;

public abstract class HudElement implements ClientFeature {
    public final Module module;
    public final HudAnchor anchor;
    private final float defaultX, defaultY;

    public final NumberSetting scale;
    private float width, height;

    protected HudElement(Module module, float x, float y, HudAnchor anchor) {
        this(module, x, y, anchor, 0.25f, 5.0f);
    }

    protected HudElement(Module module, float x, float y, HudAnchor anchor, float minScale, float maxScale) {
        this.module = module;
        this.defaultX = x; this.defaultY = y; this.anchor = anchor;
        this.scale = module.add(NumberSetting.decimal("scale", 1.0f, minScale, maxScale));
    }

    @Override public String getId() { return module.key(); }

    public HudAnchor currentAnchor() {
        UiModel model = ModuleManager.model();
        if (model != null) {
            HudAnchor current = HudAnchor.fromId(model.value(getId(), "anchor", UiModel.defaultOption(getId(), "anchor")));
            if (current != null) return current;
        }
        return anchor;
    }

    public float defaultX() { return defaultX; }
    public float defaultY() { return defaultY; }
    @Override public String getText() { return ""; }

    public abstract boolean visible(boolean preview);

    public abstract void render(boolean preview);

    protected void size(float w, float h) { width = w; height = h; }
    public float width() { return width; }
    public float height() { return height; }

    public float scale() { return scale.value(); }

    public boolean screenSpace() { return false; }

    public boolean editable() { return true; }

    public void layout(Page page) { page.addFirst(scale); }
}
