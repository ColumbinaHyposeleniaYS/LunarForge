package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.ClickCounter;
import com.example.lunarforge.util.Fields;
import com.example.lunarforge.util.GameplayUtil;
import java.lang.reflect.Field;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.FastPlace).
 * Shortens the vanilla right-click delay so blocks can be placed faster.
 */
public final class ModuleFastPlace extends Module {
    private static final Field RIGHT_CLICK_DELAY =
            Fields.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");

    private final NumberSetting delay = decimal("delay", 1.0f, 1.0f, 3.0f).label(() -> "Delay");
    private final BoolSetting blocksOnly = bool("blocksOnly", true).label(() -> "Blocks Only");
    private final BoolSetting placeFix = bool("placeFix", true).label(() -> "Place Fix");
    private final BoolSetting skipObsidian = bool("skipObsidian", true).label(() -> "Skip Obsidian");
    private final BoolSetting skipInteractable = bool("skipInteractable", true).label(() -> "Skip Interactable");

    private long delayMs;

    public ModuleFastPlace() {
        super("FAST_PLACE", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(delay);
            s.group(blocksOnly, g -> {
                g.add(placeFix);
                g.add(skipObsidian);
                g.add(skipInteractable);
            });
        });
    }

    @Override protected void onDisable() {
        delayMs = 0L;
    }

    private boolean canPlace(Minecraft mc) {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack != null) {
            Item item = stack.getItem();
            if (item instanceof ItemFishingRod) return false;
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                if (skipObsidian.on() && block instanceof BlockObsidian) return false;
                if (skipInteractable.on() && GameplayUtil.isInteractable(block)) return false;
                if (!placeFix.on()) return true;
                MovingObjectPosition mop = GameplayUtil.rayTraceBlocks(
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.playerController.getBlockReachDistance());
                return mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                        && ((ItemBlock) item).canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, stack);
            }
        }
        return !blocksOnly.on();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        int rightClickDelayTimer = Fields.getInt(RIGHT_CLICK_DELAY, mc);
        if (rightClickDelayTimer == 4) delayMs += (long) (50.0F * delay.value());
        if (delayMs > 0L) delayMs -= 50L;
        if (delayMs <= 0L && rightClickDelayTimer > 1 && canPlace(mc)) {
            Fields.setInt(RIGHT_CLICK_DELAY, mc, 0);
            if (mc.gameSettings.keyBindUseItem.isKeyDown() && !mc.thePlayer.isUsingItem()) {
                ClickCounter.register(1);
            }
        }
    }
}
