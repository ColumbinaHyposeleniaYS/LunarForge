package com.example.lunarforge.module.modules.hud.armorstatus;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.modules.hud.ModuleArmorStatus;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;

public final class ArmorSlotChild extends Module {
    public final ModuleArmorStatus.Slot slot;
    private final ModuleArmorStatus status;
    final ChoiceSetting<ModuleArmorStatus.Position> durabilityPosition = choice("durabilityPosition", ModuleArmorStatus.Position.RIGHT);
    final BoolSetting background = bool("background", false);
    final BoolSetting border = bool("border", false);
    final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    final ColorSetting borderColor = color("borderColor", 0x9F000000);
    private final Hud hud;

    public ArmorSlotChild(ModuleArmorStatus status, ModuleArmorStatus.Slot slot) {
        super("ARMORSTATUS_" + slot.name() + "_CHILD", true);
        this.status = status;
        this.slot = slot;
        hud = hud(new Hud());
    }

    private boolean hidden() { return !status.moveArmorIndividually.on() || status.vanillaMode.on(); }

    @Override protected void layout(Page page) {
        page.add(hud.scale, durabilityPosition).hideIf(this::hidden);
        page.group(background, g -> {
            g.add(backgroundColor);
            g.group(border, b -> b.add(borderThickness, borderColor));
        }).hideIf(this::hidden);
    }

    ModuleArmorStatus.Position position() {
        if (ModuleArmorStatus.stored(key(), "durabilityPosition") == null && ModuleArmorStatus.anchor(key(), hud.anchor).horizontal == HudAnchor.Side.END)
            return ModuleArmorStatus.Position.LEFT;
        return durabilityPosition.get();
    }

    private final class Hud extends HudElement {
        private ArmorElement element;

        Hud() { super(ArmorSlotChild.this, 0, 0, HudAnchor.BOTTOM_RIGHT); }

        @Override public void layout(Page page) {}

        @Override public boolean editable() { return status.moveArmorIndividually.on(); }

        @Override public boolean visible(boolean preview) {
            boolean chatOpen = Minecraft.getMinecraft().ingameGUI != null && Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatOpen();
            element = status.elements(preview).get(slot);
            boolean shown = !status.vanillaMode.on() && status.moveArmorIndividually.on() && (status.showWhileTyping.on() || !chatOpen) && element != null;
            if (!shown) { size(0, 0); return false; }
            element.layout(position());
            size(element.width() + 4, element.height() + 4);
            return true;
        }

        @Override public void render(boolean preview) {
            if (element == null) return;
            element.layout(position());
            float w = width(), h = height();
            if (background.on()) {
                Draw.fill(backgroundColor, 0, 0, w, h);
                if (border.on()) Draw.border(borderColor, 0, 0, w, h, borderThickness.value());
            }
            element.draw(2, 2.5f);
        }
    }
}
