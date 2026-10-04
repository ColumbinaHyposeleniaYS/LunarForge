package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class OneSevenAnimations extends Module {
    final BoolSetting glintAnimation = bool("glintAnimation", true);
    final BoolSetting healthAnimation = bool("healthAnimation", true);
    final BoolSetting hurtCameraShake = bool("hurtCameraShake", true);
    final BoolSetting sneakAnimation = bool("sneakAnimation", true);
    final NumberSetting sneakSpeed = decimal("sneakSpeed", 1.0f, 0.0f, 2.0f);

    OneSevenAnimations() { super("ONE_SEVEN_ANIMATIONS_LEGACY", true); }

    @Override protected void layout(Page page) {
        page.add(glintAnimation);
        page.add(healthAnimation, hurtCameraShake);
        page.add(sneakAnimation);
        page.add(sneakSpeed).hideIf(() -> !sneakAnimation.on());
    }

    public boolean health() { return isEnabled() && healthAnimation.on(); }

    public boolean hurtShake() { return isEnabled() && hurtCameraShake.on(); }

    public boolean glint() { return isEnabled() && glintAnimation.on(); }

    private boolean sneakHeld;
    private float standing, lastEye, target, height, previous;
    private int stableFrames;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        previous = height;
        if (height == target) return;
        float speed = (sneakHeld ? 1.0f : 0.6f) * Math.max(0.1f, sneakSpeed.value());
        height += (target - height) * speed;
        if (previous > height && height < target || height > previous && height > target) height = target;
    }

    float eyeHeight(float eye, float offset, float partialTicks) {
        if (!isEnabled() || !sneakAnimation.on()) return Float.NaN;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        sneakHeld = mc.gameSettings.keyBindSneak.isKeyDown();
        if (stableFrames >= 5 && !sneakHeld && !player.isSneaking()) standing = eye;
        if (lastEye == eye) ++stableFrames;
        else { lastEye = eye; stableFrames = 0; }
        target = sneakHeld ? standing - offset : standing;
        return previous + (height - previous) * partialTicks;
    }
}
