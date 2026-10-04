package com.example.lunarforge.cosmetics.render;

import com.example.lunarforge.cosmetics.*;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;

public final class CosmeticLayers {
    private static boolean installed;

    private CosmeticLayers() {}

    public static List<Cosmetic> worn(EntityPlayer player) {
        if (player instanceof Mannequin) return ((Mannequin)player).cosmetics();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && player.getUniqueID().equals(mc.thePlayer.getUniqueID())) return Loadout.equipped();
        return Collections.emptyList();
    }

    public static final class Events {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void pre(net.minecraftforge.client.event.RenderLivingEvent.Pre event) {
            if (!(event.entity instanceof EntityPlayer) || !(event.renderer instanceof RenderPlayer)) return;
            EntityPlayer p = (EntityPlayer)event.entity;
            net.minecraft.client.model.ModelPlayer m = ((RenderPlayer)event.renderer).getMainModel();
            List<Cosmetic> worn = worn(p);
            if (!worn.isEmpty()) GeckoCosmetics.hideParts(p, m, worn);
        }

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void name(net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre event) {
            if (event.entity instanceof Mannequin) event.setCanceled(true);
        }
    }

    static final class Unhide implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;
        Unhide(RenderPlayer render) { this.render = render; }

        @Override public void doRenderLayer(AbstractClientPlayer p, float a, float b, float c, float d, float e, float f, float g) {
            net.minecraft.client.model.ModelPlayer m = render.getMainModel();
            m.bipedHead.showModel = m.bipedBody.showModel = m.bipedRightArm.showModel = m.bipedLeftArm.showModel = true;
            m.bipedRightLeg.showModel = m.bipedLeftLeg.showModel = true;
        }

        @Override public boolean shouldCombineTextures() { return false; }
    }

    static final class Models implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;

        Models(RenderPlayer render) { this.render = render; }

        @Override public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            if (p.isInvisible()) return;
            for (Cosmetic c : worn(p)) {
                if (!c.renderable()) continue;
                if (c.geckolib) GeckoCosmetics.render(p, render.getMainModel(), c, partialTicks);
                else if (c.type != CosmeticType.CLOAK && c.type != CosmeticType.WINGS) ObjCosmetics.render(p, render.getMainModel(), c);
            }
        }

        @Override public boolean shouldCombineTextures() { return false; }
    }

    static Cosmetic find(EntityPlayer player, CosmeticType type) {
        for (Cosmetic c : worn(player)) if (c.type == type && !c.geckolib && c.renderable()) return c;
        return null;
    }

    public static void install() {
        if (installed) return;
        installed = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());
        com.example.lunarforge.cosmetics.emote.EmoteRenderer emotes = new com.example.lunarforge.cosmetics.emote.EmoteRenderer();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(emotes);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(emotes);
        for (RenderPlayer render : Minecraft.getMinecraft().getRenderManager().getSkinMap().values()) {
            try {
                List<LayerRenderer<?>> layers = ReflectionHelper.getPrivateValue(RendererLivingEntity.class, render, "layerRenderers", "field_177097_h");

                for (int i = 0; i < layers.size(); i++)
                    if (layers.get(i) instanceof LayerCape) layers.set(i, new Cloak(render, layers.get(i)));
                if (!containsCloak(layers)) layers.add(new Cloak(render, null));
                layers.add(0, new Unhide(render));
                layers.add(new Wings(render));
                layers.add(new Models(render));
            } catch (Exception e) {
                LogManager.getLogger("LunarForge/Cosmetics").error("Could not add cosmetic layers", e);
            }
        }
    }

    private static boolean containsCloak(List<LayerRenderer<?>> layers) {
        for (LayerRenderer<?> l : layers) if (l instanceof Cloak) return true;
        return false;
    }

    static final class Cloak implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;
        @SuppressWarnings("rawtypes") private final LayerRenderer vanilla;

        Cloak(RenderPlayer render, LayerRenderer<?> vanilla) { this.render = render; this.vanilla = vanilla; }

        @SuppressWarnings("unchecked")
        @Override public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            Cosmetic cloak = find(p, CosmeticType.CLOAK);
            ResourceLocation texture = cloak == null || p.isInvisible() ? null : CosmeticTextures.get(cloak);
            if (texture == null) {
                if (vanilla != null) vanilla.doRenderLayer(p, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
                return;
            }
            GlStateManager.color(1, 1, 1, 1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, .125f);

            boolean still = p instanceof Mannequin;
            double dx = still ? 0 : p.prevChasingPosX + (p.chasingPosX - p.prevChasingPosX) * partialTicks - (p.prevPosX + (p.posX - p.prevPosX) * partialTicks);
            double dy = still ? 0 : p.prevChasingPosY + (p.chasingPosY - p.prevChasingPosY) * partialTicks - (p.prevPosY + (p.posY - p.prevPosY) * partialTicks);
            double dz = still ? 0 : p.prevChasingPosZ + (p.chasingPosZ - p.prevChasingPosZ) * partialTicks - (p.prevPosZ + (p.posZ - p.prevPosZ) * partialTicks);
            float yaw = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * partialTicks;
            double sin = MathHelper.sin(yaw * (float)Math.PI / 180f), cos = -MathHelper.cos(yaw * (float)Math.PI / 180f);
            float lift = MathHelper.clamp_float((float)dy * 10f, -6f, 32f);
            float forward = (float)(dx * sin + dz * cos) * 100f;
            float side = (float)(dx * cos - dz * sin) * 100f;
            if (forward < 0) forward = 0;
            float bob = still ? 0 : p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * partialTicks;
            lift += MathHelper.sin((p.prevDistanceWalkedModified + (p.distanceWalkedModified - p.prevDistanceWalkedModified) * partialTicks) * 6f) * 32f * bob;
            if (p.isSneaking()) lift += 25f;
            GlStateManager.rotate(6f + forward / 2f + lift, 1, 0, 0);
            GlStateManager.rotate(side / 2f, 0, 0, 1);
            GlStateManager.rotate(-side / 2f, 0, 1, 0);
            GlStateManager.rotate(180f, 0, 1, 0);
            render.getMainModel().renderCape(.0625f);
            GlStateManager.popMatrix();
        }

        @Override public boolean shouldCombineTextures() { return false; }
    }

    static final class Wings implements LayerRenderer<AbstractClientPlayer> {
        private static final WingModel MODEL = new WingModel();
        private final RenderPlayer render;

        Wings(RenderPlayer render) { this.render = render; }

        @Override public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
            Cosmetic wings = find(p, CosmeticType.WINGS);
            if (wings == null || p.isInvisible()) return;
            ResourceLocation texture = CosmeticTextures.get(wings);
            if (texture == null) return;
            GlStateManager.pushMatrix();
            render.getMainModel().bipedBody.postRender(.0625f);
            if (p.isSneaking()) GlStateManager.translate(0, .2f, 0);
            GlStateManager.color(1, 1, 1, 1);
            MODEL.render(.13f, .0625f, texture);
            GlStateManager.popMatrix();
        }

        @Override public boolean shouldCombineTextures() { return false; }
    }
}
