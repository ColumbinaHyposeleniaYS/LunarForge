package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ModuleCrosshair extends Module {
    final CrosshairChild normal, friendly, enemy;
    private final BoolSetting showInF5 = bool("showInF5", true);

    public ModuleCrosshair() {
        super("CROSSHAIR", false);
        normal = child(new CrosshairChild(this, CrosshairChild.Kind.NORMAL, "CROSSHAIR_NORMAL", true), null);
        friendly = child(new CrosshairChild(this, CrosshairChild.Kind.FRIENDLY, "CROSSHAIR_FRIENDLY", false), null);
        enemy = child(new CrosshairChild(this, CrosshairChild.Kind.ENEMY, "CROSSHAIR_ENEMY", false), null);
    }

    @Override protected void layout(Page page) { page.add(showInF5); }

    @SubscribeEvent
    public void onCrosshair(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS && isEnabled()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;

        if (screen != null && !(screen instanceof GuiChat) && screen.getClass().getName().startsWith("com.example.lunarforge.")) return;
        if (mc.gameSettings.thirdPersonView != 0 && !showInF5.on()) return;
        CrosshairChild.Kind target = CrosshairChild.Kind.NORMAL;
        boolean enemies = enemy.isEnabled(), friends = friendly.isEnabled();
        if (enemies || friends) {
            Entity pointed = mc.pointedEntity;
            if (pointed instanceof EntityPlayer) {
                if (enemies && !pointed.isInvisibleToPlayer(mc.thePlayer)) target = CrosshairChild.Kind.ENEMY;
            } else if (pointed instanceof EntityLivingBase) {
                boolean hostile = pointed instanceof IMob;
                if (enemies && hostile) target = CrosshairChild.Kind.ENEMY;
                else if (friends && !hostile) target = CrosshairChild.Kind.FRIENDLY;
            }
        }
        CrosshairChild chosen = target == CrosshairChild.Kind.FRIENDLY ? friendly : target == CrosshairChild.Kind.ENEMY ? enemy : normal;
        if (target != CrosshairChild.Kind.NORMAL && !chosen.isEnabled()) chosen = normal;
        if (!chosen.isEnabled()) return;
        ScaledResolution res = new ScaledResolution(mc);
        int x = Math.round(res.getScaledWidth() / 2.0f), y = Math.round(res.getScaledHeight() / 2.0f);
        chosen.draw(CrosshairGl.INSTANCE, x, y, false, event.partialTicks);
    }
}
