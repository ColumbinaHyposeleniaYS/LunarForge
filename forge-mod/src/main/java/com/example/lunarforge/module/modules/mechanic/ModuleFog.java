package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ModuleFog extends Module {
    private final NumberSetting waterFogDensity = decimal("waterFogDensity", 1.0f, 0.0f, 1.8f);

    private final NumberSetting renderDistanceFogDensity = decimal("renderDistanceFogDensity", 1.0f, 0.0f, 1.95f);

    private final BoolSetting renderDistanceFogColorToggle = bool("renderDistanceFogColorToggle", false);

    private final ColorSetting renderDistanceFogColor = color("renderDistanceFogColor", 0x00C0D8FF);

    public ModuleFog() {
        super("FOG", false);
    }

    @Override protected void layout(Page page) {
        page.section("fogDensity", s -> s.add(waterFogDensity, renderDistanceFogDensity));
        page.section("fogColor", s -> s.group(renderDistanceFogColorToggle, g -> g.add(renderDistanceFogColor)));
    }

    public NumberSetting renderDistanceFogDensity() {
        return renderDistanceFogDensity;
    }

    private static final Field CLOUD_FOG = Fields.find(net.minecraft.client.renderer.EntityRenderer.class, "cloudFog", "field_78500_U");

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!isEnabled() || !renderDistanceFogColorToggle.on()) return;
        if (event.block.getMaterial() == Material.water || event.block.getMaterial() == Material.lava) return;
        int c = renderDistanceFogColor.color((float)event.renderPartialTicks);
        event.red = channel(c >> 16 & 0xFF);
        event.green = channel(c >> 8 & 0xFF);
        event.blue = channel(c & 0xFF);
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (!isEnabled()) return;
        if (Fields.getBoolean(CLOUD_FOG, event.renderer)) return;
        boolean living = event.entity instanceof EntityLivingBase;
        if (living && ((EntityLivingBase)event.entity).isPotionActive(Potion.blindness)) return;
        if (event.block.getMaterial() != Material.water) return;
        float scale = waterFogDensity.value();
        if (scale == 1.0f) return;
        float base = living && ((EntityLivingBase)event.entity).isPotionActive(Potion.waterBreathing)
            ? 0.01f
            : 0.1f - (float)EnchantmentHelper.getRespiration(event.entity) * 0.03f;
        event.density = base * (scale * 2.0f);
        GlStateManager.setFog(2048);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (!isEnabled()) return;
        float f = renderDistanceFogDensity.value();
        if (f == 1.0f) return;
        if (mc().theWorld != null && mc().theWorld.provider.doesXZShowFog((int)event.entity.posX, (int)event.entity.posZ)) return;
        float far = event.farPlaneDistance;
        float start = event.fogMode == -1 ? 0.0f : far * 0.75f;
        GlStateManager.setFogStart(fogStart(f, start));
        GlStateManager.setFogEnd(fogEnd(f, far));
    }

    private static float fogStart(float f, float start) {
        return f == 0.0f ? (start == 0.0f ? 50.0f : start * 5.0f) : (start == 0.0f ? 0.1f : start) * Math.max(2.0f - f, 0.01f);
    }

    private static float fogEnd(float f, float end) {
        return f == 0.0f ? end * 5.0f : end * Math.max(2.0f - f, 0.01f);
    }

    private static float channel(int n) {
        return n * 0.003921569f;
    }
}
