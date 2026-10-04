package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.player.AutoTool).
 * Automatically switches to the best tool in the hotbar while mining,
 * and switches back when you stop.
 */
public final class ModuleAutoTool extends Module {
    private final NumberSetting switchDelay = integer("switchDelay", 0, 0, 5).label(() -> "Switch Delay");
    private final BoolSetting switchBack = bool("switchBack", true).label(() -> "Switch Back");
    private final BoolSetting sneakOnly = bool("sneakOnly", true).label(() -> "Sneak Only");

    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter;

    public ModuleAutoTool() {
        super("AUTO_TOOL", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(switchDelay, switchBack, sneakOnly));
    }

    @Override protected void onDisable() {
        currentToolSlot = -1;
        previousSlot = -1;
        tickDelayCounter = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (currentToolSlot != -1 && currentToolSlot != mc.thePlayer.inventory.currentItem) {
            currentToolSlot = -1;
            previousSlot = -1;
        }

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()) {
            if (tickDelayCounter >= switchDelay.intValue()
                    && (!sneakOnly.on() || GameplayUtil.physicalDown(mc.gameSettings.keyBindSneak))) {
                int slot = GameplayUtil.findToolSlot(
                        mc.thePlayer.inventory.currentItem,
                        mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock());
                if (mc.thePlayer.inventory.currentItem != slot) {
                    if (previousSlot == -1) previousSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = currentToolSlot = slot;
                }
            }
            tickDelayCounter++;
        } else {
            if (switchBack.on() && previousSlot != -1) mc.thePlayer.inventory.currentItem = previousSlot;
            currentToolSlot = -1;
            previousSlot = -1;
            tickDelayCounter = 0;
        }
    }
}
