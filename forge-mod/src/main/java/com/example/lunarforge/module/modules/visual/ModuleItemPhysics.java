package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.NumberSetting;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleItemPhysics extends Module {
    private final NumberSetting rotationSpeed = decimal("rotationSpeed", 0.5f, 0.1f, 4.0f);

    private final Map<Integer, Data> data = new HashMap<Integer, Data>();

    public ModuleItemPhysics() {
        super("ITEM_PHYSICS", false);
    }

    @Override protected void layout(Page page) {
        page.add(rotationSpeed);
    }

    @Override protected void onDisable() {
        data.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        Minecraft client = mc();
        if (client.theWorld == null) {
            data.clear();
            return;
        }
        if (client.isGamePaused()) return;
        float speed = rotationSpeed.value();
        for (Iterator<Data> it = data.values().iterator(); it.hasNext(); ) {
            Data d = it.next();
            EntityItem item = d.entity.get();
            if (item == null || item.isDead) {
                it.remove();
                continue;
            }
            if (Double.isNaN(item.posX) || Double.isNaN(item.posY) || Double.isNaN(item.posZ)) continue;
            if (item.onGround) {
                if (d.angle != 0.0 && d.angle != 180.0) d.angle = d.angle > 90.0 && d.angle < 270.0 ? 180.0 : 0.0;
                d.prevAngle = d.angle;
                d.velocity = 0.0;
            } else {
                d.prevAngle = d.angle;
                double mx = item.motionX, my = item.motionY, mz = item.motionZ;
                double v = Math.min(Math.sqrt(mx * mx + my * my + mz * mz) * 100.0, 30.0) * 0.2 + 0.8 * d.velocity;
                d.angle += v * speed;
                d.velocity = v;
            }
        }
    }

    private static ModuleItemPhysics active() {
        Module module = ModuleManager.get("ITEM_PHYSICS");
        return module instanceof ModuleItemPhysics && module.isEnabled() ? (ModuleItemPhysics)module : null;
    }

    public static void translate(float x, float y, float z, EntityItem entity, double renderY) {
        if (active() == null) {
            GlStateManager.translate(x, y, z);
            return;
        }
        float offset = 0.035f + new Random(entity.getEntityId()).nextFloat() * 0.01f;
        if (entity.worldObj != null
                && entity.worldObj.getBlockState(new BlockPos(entity.posX, entity.posY, entity.posZ)).getBlock() == Blocks.snow_layer) {
            offset += 0.125f;
        }
        GlStateManager.translate(x, (float)(renderY + offset), z);
    }

    public static void rotate(float angle, float x, float y, float z, EntityItem entity, float partialTicks) {
        ModuleItemPhysics physics = active();
        if (physics == null) {
            GlStateManager.rotate(angle, x, y, z);
            return;
        }
        GlStateManager.rotate(90.0f, 1.0f, 0.0f, 0.0f);
        GlStateManager.rotate(entity.rotationYaw, 0.0f, 0.0f, 1.0f);
        Data d = physics.data.get(entity.getEntityId());
        if (d == null || d.entity.get() != entity) {
            d = new Data(entity);
            physics.data.put(entity.getEntityId(), d);
        }
        GlStateManager.rotate((float)(d.prevAngle + (d.angle - d.prevAngle) * partialTicks), 1.0f, 0.0f, 0.0f);
    }

    public static float clumpSpread(float spread) {
        return active() == null ? spread : spread * 0.5f;
    }

    public static IBakedModel handleCameraTransforms(IBakedModel model, ItemCameraTransforms.TransformType type) {
        if (active() != null && type == ItemCameraTransforms.TransformType.GROUND) {
            GlStateManager.translate(0.0f, -model.getItemCameraTransforms().ground.translation.y, 0.0f);
        }
        return ForgeHooksClient.handleCameraTransforms(model, type);
    }

    private static final class Data {
        final WeakReference<EntityItem> entity;
        double angle, prevAngle, velocity;

        Data(EntityItem entity) {
            this.entity = new WeakReference<EntityItem>(entity);
        }
    }
}
