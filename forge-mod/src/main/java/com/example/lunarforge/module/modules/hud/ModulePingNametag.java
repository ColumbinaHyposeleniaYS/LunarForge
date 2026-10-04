package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class ModulePingNametag extends Module {
    private final ModulePing parent;
    private final BoolSetting pingAbove = bool("pingAbove", true);

    ModulePingNametag(ModulePing parent) {
        super("PING_NAMETAG", false);
        this.parent = parent;
    }

    @Override protected void layout(Page page) { page.add(pingAbove); }

    @SubscribeEvent
    public void onSpecials(RenderLivingEvent.Specials.Post<EntityLivingBase> event) {
        if (!isEnabled() || !parent.isEnabled() || !(event.entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer)event.entity;
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager rm = mc.getRenderManager();
        if (mc.thePlayer == null || player == rm.livingPlayer || player.isInvisibleToPlayer(mc.thePlayer)
            || player.riddenByEntity != null || !Minecraft.isGuiEnabled()) return;
        double range = player.isSneaking() ? 32.0 : 64.0;
        if (player.getDistanceSqToEntity(rm.livingPlayer) > range * range) return;
        if (player.getDisplayName().getFormattedText().contains("§k")) return;
        int ping = 0;
        boolean self = player == mc.thePlayer;
        if (self) {
            ping = parent.ping();
        } else {
            NetworkPlayerInfo info = parent.playerInfo(player.getUniqueID());
            if (info != null) ping = Math.max(info.getResponseTime(), 0);
        }
        List<ModulePing.Part> parts = parent.text(ping, self, null);
        String prefix = parent.showPingPrefix.on() ? parent.pingPrefix.get() : "";
        if (prefix.trim().isEmpty()) prefix = "";
        float width = Draw.width(prefix);
        for (ModulePing.Part part : parts) width += Draw.width(part.text);
        draw(event, player, prefix, parts, width, pingAbove.on() ? -10 : 10);
    }

    private void draw(RenderLivingEvent.Specials.Post<EntityLivingBase> event, EntityPlayer player, String prefix,
                      List<ModulePing.Part> parts, float width, int lineOffset) {
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        float scale = 0.016666668f * 1.6f;
        GlStateManager.pushMatrix();
        GlStateManager.translate((float)event.x, (float)event.y + player.height + 0.5f - (player.isChild() ? player.height / 2 : 0), (float)event.z);
        GL11.glNormal3f(0, 1, 0);
        GlStateManager.rotate(-rm.playerViewY, 0, 1, 0);
        GlStateManager.rotate(rm.playerViewX, 1, 0, 0);
        GlStateManager.scale(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float x = -width / 2;
        int y = lineOffset;
        Draw.rect(x - 1, y - 1, width + 2, 9, 0x40000000);
        GlStateManager.depthMask(true);
        float cx = x;
        if (!prefix.isEmpty()) cx = Draw.text(prefix, cx, y, parent.pingPrefixColor.color(0), false);
        for (ModulePing.Part part : parts) cx = Draw.text(part.text, cx, y, part.color, false);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }
}
