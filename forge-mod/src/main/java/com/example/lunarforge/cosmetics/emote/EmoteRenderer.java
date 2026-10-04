package com.example.lunarforge.cosmetics.emote;

import com.example.lunarforge.cosmetics.CosmeticTextures;
import com.example.lunarforge.cosmetics.render.Mannequin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class EmoteRenderer {
    private static final float SCALE = .9375f;
    private int savedView = -1;

    @SubscribeEvent
    public void render(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer p = (AbstractClientPlayer)event.entityPlayer;
        Emotes.Emote e = Emotes.playing(p);
        if (e == null || !Emotes.ready()) return;
        boolean preview = p instanceof Mannequin;
        float frame = Emotes.frame(p, event.partialRenderTick, preview);
        if (frame < 0) { Emotes.finish(p); return; }
        event.setCanceled(true);

        Bobj body = Emotes.body("slim".equals(p.getSkinType())), lib = Emotes.library();
        Bobj.Action action = lib.actions.get("emote_" + e.key);
        float[][] skin = body.pose(action, frame);
        float yaw = preview ? p.renderYawOffset : p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * event.partialRenderTick;

        GlStateManager.pushMatrix();
        GlStateManager.translate(event.x, event.y, event.z);
        GlStateManager.rotate(-yaw, 0, 1, 0);
        GlStateManager.scale(SCALE, SCALE, SCALE);
        GlStateManager.enableRescaleNormal();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Minecraft.getMinecraft().getTextureManager().bindTexture(p.getLocationSkin());
        for (Bobj.Mesh m : body.meshes.values())
            if (m.name.equals("body")) draw(m, skin);
        for (String[] prop : e.meshes) {
            if (frame < Float.parseFloat(prop[1])) continue;
            Bobj.Mesh m = lib.meshes.get(prop[0]);
            String tex = Emotes.meshTexture(prop[0]);
            ResourceLocation loc = tex == null ? null : CosmeticTextures.get(tex, false);
            if (m == null || loc == null) continue;
            Minecraft.getMinecraft().getTextureManager().bindTexture(loc);
            draw(m, skin);
        }
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    private static void draw(Bobj.Mesh m, float[][] skin) {
        int n = m.pos.length / 3;
        float[] out = new float[n * 3];
        float[][] rot = new float[n][];
        for (int i = 0; i < n; i++) {
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            int[] bi = m.weightBones[i]; float[] bw = m.weights[i];
            float total = 0, ox = 0, oy = 0, oz = 0;
            for (int k = 0; k < bi.length; k++) {
                float[] s = skin[bi[k]]; float w = bw[k];
                ox += w * (s[0] * x + s[4] * y + s[8] * z + s[12]);
                oy += w * (s[1] * x + s[5] * y + s[9] * z + s[13]);
                oz += w * (s[2] * x + s[6] * y + s[10] * z + s[14]);
                total += w;
            }
            if (total <= 0) { ox = x; oy = y; oz = z; } else { ox /= total; oy /= total; oz /= total; }
            out[i * 3] = ox; out[i * 3 + 1] = oy; out[i * 3 + 2] = oz;
            rot[i] = bi.length > 0 ? skin[bi[0]] : null;
        }
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
        for (int t = 0; t < m.tris.length; t += 3) {
            int v = m.tris[t], vt = m.tris[t + 1], vn = m.tris[t + 2];
            float u = vt >= 0 ? m.uv[vt * 2] : 0, vv = vt >= 0 ? m.uv[vt * 2 + 1] : 0;
            float nx = 0, ny = 1, nz = 0;
            if (vn >= 0) {
                nx = m.normals[vn * 3]; ny = m.normals[vn * 3 + 1]; nz = m.normals[vn * 3 + 2];
                float[] s = rot[v];
                if (s != null) { float a = s[0] * nx + s[4] * ny + s[8] * nz, b = s[1] * nx + s[5] * ny + s[9] * nz, c = s[2] * nx + s[6] * ny + s[10] * nz; nx = a; ny = b; nz = c; }
            }
            wr.pos(out[v * 3], out[v * 3 + 1], out[v * 3 + 2]).tex(u, vv).normal(nx, ny, nz).endVertex();
        }
        Tessellator.getInstance().draw();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) { savedView = -1; return; }
        Emotes.Emote e = Emotes.playing(mc.thePlayer);
        if (e != null) {
            net.minecraft.util.MovementInput in = mc.thePlayer.movementInput;
            boolean moving = in != null && (in.moveForward != 0 || in.moveStrafe != 0 || in.jump || in.sneak);
            if (moving || Emotes.frame(mc.thePlayer, 0, false) < 0) { Emotes.stop(mc.thePlayer); e = null; }
        }
        if (e != null && savedView < 0) { savedView = mc.gameSettings.thirdPersonView; mc.gameSettings.thirdPersonView = 2; }
        else if (e == null && savedView >= 0) { mc.gameSettings.thirdPersonView = savedView; savedView = -1; }
    }
}
