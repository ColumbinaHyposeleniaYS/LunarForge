package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.*;
import com.example.lunarforge.module.setting.*;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ModuleBossbar extends Module {
    private final BoolSetting bar = bool("renderBar", true), text = bool("renderBossText", true), custom = bool("customBossBar", false);
    private final ColorSetting color = color("barColor", -1);
    private final BoolSetting guiScale = bool("useMinecraftGUIScale", true);
    private final ResourceLocation vanilla = new ResourceLocation("textures/gui/icons.png");
    private final ResourceLocation lunar = new ResourceLocation("lunarforge", "textures/hud/boss-icons.png");
    public ModuleBossbar() { super("BOSSBAR", true); hud(new Hud()); }
    protected void layout(Page p) { p.add(bar, text); p.group(custom, s -> s.add(color)); p.add(guiScale); }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Pre e) {
        if (isEnabled() && e.type == RenderGameOverlayEvent.ElementType.BOSSHEALTH) {
            e.setCanceled(true);

            if (BossStatus.statusBarTime > 0) --BossStatus.statusBarTime;
        }
    }
    private final class Hud extends HudElement {
        Hud() { super(ModuleBossbar.this, 0, 1, HudAnchor.TOP_CENTER, .5f, 1.5f); size(182,18); }
        public boolean visible(boolean preview) { return preview || BossStatus.statusBarTime > 0 && BossStatus.bossName != null; }
        public void render(boolean preview) {
            ResourceLocation texture = custom.on() ? lunar : vanilla;
            int tint = custom.on() ? color.color(0) : -1;
            if (bar.on()) {
                Draw.blit(texture, 0, 9, 0, 74, 182, 5, 256, 256, tint);
                Draw.blit(texture, 0, 9, 0, 74, 182, 5, 256, 256, tint);
                Draw.blit(texture, 0, 9, 0, 79, Math.min(182, (int)((preview ? 1 : BossStatus.healthScale) * 183)), 5, 256, 256, tint);
            }
            if (text.on()) { String name = preview ? "Ender Dragon" : BossStatus.bossName; Draw.text(name, 91 - Draw.width(name)/2, 0, -1, true); }
        }
    }
}
