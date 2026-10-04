package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleDamageTint extends Module {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final ResourceLocation HEARTBEAT = new ResourceLocation("lunar", "sound/heartbeat.ogg");

    private static final Field SOUND_VOLUME = Fields.find(PositionedSound.class, "volume", "field_147662_b");

    private static float healthRatio = 1.0f;

    private static int heartbeatCooldown;

    private final ColorSetting vignetteColor = color("vignetteColor", 0xFFFF0000);

    private final NumberSetting vignetteIntensity = decimal("vignetteIntensity", 1.0f, 0.0f, 1.0f);

    private final NumberSetting showVignetteBelow = integer("showVignetteBelow", 100, 0, 100);

    private final BoolSetting heartbeatAudio = bool("heartbeatAudio", false);

    private final NumberSetting heartbeatAudioVolume = decimal("heartbeatAudioVolume", 1.0f, 0.0f, 1.0f);

    public ModuleDamageTint() {
        super("DAMAGE_TINT", false);
    }

    @Override protected void layout(Page page) {
        page.add(vignetteColor, vignetteIntensity, showVignetteBelow, heartbeatAudio, heartbeatAudioVolume);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft client = mc();
        EntityPlayerSP player = client.thePlayer;
        if (player == null) return;
        float health = player.getHealth();
        healthRatio = health / player.getMaxHealth();
        if (hidden(player)) return;
        if (heartbeatAudio.on()) {
            if (client.isGamePaused()) return;
            if (heartbeatCooldown <= 0) {
                heartbeatCooldown = (int)(17.0f + health * 3.0f);
                if (healthRatio == 0.0f || healthRatio > 0.5f) return;
                if (healthRatio >= showVignetteBelow.intValue() / 100.0f) return;
                playHeartbeat(heartbeatAudioVolume.value());
            } else {
                --heartbeatCooldown;
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!isEnabled() || event.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        Minecraft client = mc();
        if (client.thePlayer == null || client.theWorld == null) return;
        if (hidden(client.thePlayer)) return;
        if (healthRatio == 0.0f) return;
        float threshold = showVignetteBelow.intValue() / 100.0f;
        if (healthRatio >= threshold) return;
        float alpha = 1.0f - healthRatio / threshold;
        alpha *= vignetteIntensity.value();
        alpha = clamp(alpha, 0.0f, 1.0f);
        int tint = ~scaleRgb(vignetteColor.color(0.0f) & 0xFFFFFF, alpha);
        drawVignette(event.resolution.getScaledWidth(), event.resolution.getScaledHeight(), tint);
    }

    private static boolean hidden(EntityPlayerSP player) {
        return player.capabilities.isCreativeMode || player.isSpectator();
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int scaleRgb(int color, float f) {
        if (f >= 1.0f) return color;
        if (f <= 0.0f) return color & 0xFF000000;
        int red = (int)((color >> 16 & 0xFF) * f);
        int green = (int)((color >> 8 & 0xFF) * f);
        int blue = (int)((color & 0xFF) * f);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void drawVignette(float width, float height, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(VIGNETTE);
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(0, 769, 0, 769);
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0.0, height, 0.0).tex(0.0, 1.0).endVertex();
        wr.pos(width, height, 0.0).tex(1.0, 1.0).endVertex();
        wr.pos(width, 0.0, 0.0).tex(1.0, 0.0).endVertex();
        wr.pos(0.0, 0.0, 0.0).tex(0.0, 0.0).endVertex();
        Tessellator.getInstance().draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    }

    private static void playHeartbeat(float volume) {
        PositionedSoundRecord sound = PositionedSoundRecord.create(HEARTBEAT);
        Fields.setFloat(SOUND_VOLUME, sound, volume);
        mc().getSoundHandler().playSound(sound);
    }
}
