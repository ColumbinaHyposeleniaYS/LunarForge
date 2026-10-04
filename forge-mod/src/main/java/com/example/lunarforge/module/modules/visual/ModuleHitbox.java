package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class ModuleHitbox extends Module {
    private static ModuleHitbox instance;

    public enum Type implements ChoiceSetting.Option {
        SOLID(1, 65535), DASHED(3, 65280), DOTTED(1, 43690);
        final int factor, pattern;
        Type(int factor, int pattern) { this.factor = factor; this.pattern = pattern; }
        @Override public String langId() { return "hitbox." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final List<Data> configs = new ArrayList<Data>();
    private final ChoiceSetting<Type> hitboxLinePattern = choice("hitboxLinePattern", Type.SOLID);
    private final BoolSetting maxDistanceToggle = bool("maxDistanceToggle", false);
    private final NumberSetting maxDistance = integer("maxDistance", 64, 1, 128);

    private final class Data {
        final Predicate<Entity> test;
        final boolean living;
        final NumberSetting lineWidth;
        final ColorSetting lineColor, hittableColor, damagedColor;
        final BoolSetting showHittableColor, onlyShowHittable, showDamagedColor, lookVector, show;

        Data(String type, Predicate<Entity> test, boolean living) {
            this.test = test;
            this.living = living;
            lineWidth = decimal("hitbox" + type + "LineWidth", 1.0f, 1.0f, 5.0f);
            lineColor = color("hitbox" + type + "LineColor", 0xFFFFFFFF);
            showHittableColor = bool("hitbox" + type + "ShowHittableColor", false);
            hittableColor = color("hitbox" + type + "HittableColor", 0xFF00FF00);
            onlyShowHittable = bool("hitbox" + type + "OnlyShowHittable", false);
            showDamagedColor = bool("hitbox" + type + "ShowDamagedColor", false);
            damagedColor = color("hitbox" + type + "DamagedColor", 0xFFFF0000);
            lookVector = bool("hitbox" + type + "LookVector", false);
            show = bool("hitbox" + type + "Show", true);
        }

        void layout(Page page) {
            page.group(show, c -> {
                c.add(lineWidth, lineColor);
                c.group(showHittableColor, h -> h.add(hittableColor)).hideIf(() -> !living);
                c.add(onlyShowHittable).hideIf(() -> !living);
                c.group(showDamagedColor, d -> d.add(damagedColor)).hideIf(() -> !living);
                c.add(lookVector);
            });
        }

        boolean shown(Entity e) {
            Entity player = Minecraft.getMinecraft().thePlayer;
            if (player == null || e == null || !show.on()) return false;
            if (living && onlyShowHittable.on() && !pointed(e)) return false;
            if (maxDistanceToggle.on()) {
                int max = maxDistance.intValue();
                int dx = MathHelper.floor_double(player.posX) - MathHelper.floor_double(e.posX);
                int dy = MathHelper.floor_double(player.posY) - MathHelper.floor_double(e.posY);
                int dz = MathHelper.floor_double(player.posZ) - MathHelper.floor_double(e.posZ);
                return dx * dx + dy * dy + dz * dz <= max * max;
            }
            return true;
        }

        ColorSetting colorFor(Entity e) {
            if (living && showDamagedColor.on() && e instanceof EntityLivingBase && ((EntityLivingBase)e).hurtTime > 0) return damagedColor;
            return living && showHittableColor.on() && pointed(e) ? hittableColor : lineColor;
        }

        private boolean pointed(Entity e) {
            Entity p = Minecraft.getMinecraft().pointedEntity;
            return p != null && p.getUniqueID().equals(e.getUniqueID());
        }
    }

    public ModuleHitbox() {
        super("HITBOX", false);
        instance = this;
        configs.add(new Data("Player", e -> e instanceof EntityPlayer, true));
        configs.add(new Data("Item", e -> e instanceof EntityItem, false));
        configs.add(new Data("ExpOrb", e -> e instanceof EntityXPOrb, false));
        configs.add(new Data("ItemFrame", e -> e instanceof EntityItemFrame, false));
        configs.add(new Data("Firework", e -> e instanceof EntityFireworkRocket, false));
        configs.add(new Data("WitherSkull", e -> e instanceof EntityWitherSkull, false));
        configs.add(new Data("Snowball", e -> e instanceof EntitySnowball, false));
        configs.add(new Data("Fireball", e -> e instanceof EntityFireball, false));
        configs.add(new Data("Arrow", e -> e instanceof EntityArrow, false));
        configs.add(new Data("Projectile", e -> e instanceof IProjectile, false));
        configs.add(new Data("Monster", e -> e instanceof IMob, true));
        configs.add(new Data("Passive", e -> e instanceof EntityAnimal || e instanceof EntityLivingBase, true));
        configs.add(new Data("Other", e -> true, false));
    }

    @Override protected void layout(Page page) {
        page.add(hitboxLinePattern);
        page.group(maxDistanceToggle, c -> c.add(maxDistance));
        for (Data d : configs) d.layout(page);
    }

    private Data data(Entity e) {
        for (Data d : configs) if (d.test.test(e)) return d;
        return configs.get(configs.size() - 1);
    }

    @Override protected void onEnable() { Minecraft.getMinecraft().getRenderManager().setDebugBoundingBox(true); }

    @Override protected void onDisable() { Minecraft.getMinecraft().getRenderManager().setDebugBoundingBox(false); }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc().getRenderManager() == null) return;
        boolean shown = mc().getRenderManager().isDebugBoundingBox();
        boolean enabled = isEnabled();
        if (enabled && !shown) setEnabled(false);
        else if (!enabled && shown) setEnabled(true);
    }

    public static boolean render(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        ModuleHitbox m = instance;
        if (m == null || !m.isEnabled()) return false;
        Data data = m.data(entity);
        if (!data.shown(entity)) return true;
        GlStateManager.depthMask(false);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        float half = entity.width / 2.0F;
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        AxisAlignedBB box = new AxisAlignedBB(bb.minX - entity.posX + x, bb.minY - entity.posY + y, bb.minZ - entity.posZ + z,
            bb.maxX - entity.posX + x, bb.maxY - entity.posY + y, bb.maxZ - entity.posZ + z);
        ColorSetting color = data.colorFor(entity);
        GL11.glLineWidth(data.lineWidth.value());
        GL11.glEnable(GL11.GL_LINE_STIPPLE);
        Type type = m.hitboxLinePattern.get();
        GL11.glLineStipple(type.factor, (short)type.pattern);
        int c = color.color(0.0f);
        if ((c >>> 24) / 255.0F < 0.95F) {
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 769);
        }
        RenderGlobal.drawOutlinedBoundingBox(box, c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_STIPPLE);
        if (data.lookVector.on()) {
            if (entity instanceof EntityLivingBase) {
                RenderGlobal.drawOutlinedBoundingBox(new AxisAlignedBB(x - half, y + entity.getEyeHeight() - 0.009999999776482582D, z - half,
                    x + half, y + entity.getEyeHeight() + 0.009999999776482582D, z + half), 255, 0, 0, 255);
            }
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            Vec3 look = entity.getLook(partialTicks);
            wr.begin(3, DefaultVertexFormats.POSITION_COLOR);
            wr.pos(x, y + entity.getEyeHeight(), z).color(0, 0, 255, 255).endVertex();
            wr.pos(x + look.xCoord * 2.0D, y + entity.getEyeHeight() + look.yCoord * 2.0D, z + look.zCoord * 2.0D).color(0, 0, 255, 255).endVertex();
            tess.draw();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        return true;
    }
}
