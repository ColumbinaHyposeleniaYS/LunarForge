package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.AutoClicker).
 * Clicks the attack key at a randomized CPS rate while the player holds attack.
 */
public final class ModuleAutoClicker extends Module {
    private final NumberSetting minCps = integer("minCps", 8, 1, 20).label(() -> "Min CPS");
    private final NumberSetting maxCps = integer("maxCps", 12, 1, 20).label(() -> "Max CPS");
    private final BoolSetting blockHit = bool("blockHit", false).label(() -> "Block Hit");
    private final NumberSetting blockHitTicks = decimal("blockHitTicks", 1.5f, 1.0f, 20.0f).label(() -> "Block Hit Ticks");
    private final BoolSetting weaponsOnly = bool("weaponsOnly", true).label(() -> "Weapons Only");
    private final BoolSetting allowTools = bool("allowTools", false).label(() -> "Allow Tools");
    private final BoolSetting breakBlocks = bool("breakBlocks", true).label(() -> "Don't Break Blocks");
    private final NumberSetting range = decimal("range", 3.0f, 3.0f, 8.0f).label(() -> "Player Range");
    private final NumberSetting hitBoxVertical = decimal("hitBoxVertical", 0.1f, 0.0f, 1.0f).label(() -> "Hit Box Vertical");
    private final NumberSetting hitBoxHorizontal = decimal("hitBoxHorizontal", 0.2f, 0.0f, 1.0f).label(() -> "Hit Box Horizontal");

    private boolean clickPending;
    private long clickDelay;
    private boolean blockHitPending;
    private long blockHitDelay;

    public ModuleAutoClicker() {
        super("AUTO_CLICKER", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(minCps, maxCps);
            s.group(weaponsOnly, g -> {
                g.add(allowTools);
                g.group(blockHit, b -> b.add(blockHitTicks));
            });
            s.group(breakBlocks, g -> g.add(range, hitBoxVertical, hitBoxHorizontal));
        });
    }

    @Override protected void onEnable() {
        clickDelay = 0L;
        blockHitDelay = 0L;
    }

    private long nextClickDelay() {
        int min = minCps.intValue(), max = Math.max(maxCps.intValue(), min);
        return 1000L / GameplayUtil.nextLong(min, max);
    }

    private long blockHitDelayMs() {
        return (long) (50.0F * blockHitTicks.value());
    }

    private static boolean isBreakingBlock(Minecraft mc) {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean allowed = !weaponsOnly.on() || GameplayUtil.hasRawUnbreakingEnchant(held)
                || (allowTools.on() && GameplayUtil.isTool(held));
        if (!allowed) return false;
        if (breakBlocks.on() && isBreakingBlock(mc) && !hasValidTarget(mc)) {
            GameType gameType = mc.playerController.getCurrentGameType();
            return gameType != GameType.SURVIVAL && gameType != GameType.CREATIVE;
        }
        return true;
    }

    private boolean isValidTarget(Minecraft mc, EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.thePlayer.ridingEntity) return false;
        if (player == mc.getRenderViewEntity() || player == mc.getRenderViewEntity().ridingEntity) return false;
        if (player.deathTime > 0) return false;
        float borderSize = player.getCollisionBorderSize();
        AxisAlignedBB box = player.getEntityBoundingBox().expand(
                borderSize + hitBoxHorizontal.value(),
                borderSize + hitBoxVertical.value(),
                borderSize + hitBoxHorizontal.value());
        return GameplayUtil.rayTraceIntersects(box, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, range.value());
    }

    private boolean hasValidTarget(Minecraft mc) {
        Iterator<?> iterator = mc.theWorld.loadedEntityList.iterator();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            if (next instanceof EntityPlayer && isValidTarget(mc, (EntityPlayer) next)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (clickDelay > 0L) clickDelay -= 50L;
        if (blockHitDelay > 0L) blockHitDelay -= 50L;

        if (mc.currentScreen != null) {
            clickPending = false;
            blockHitPending = false;
            return;
        }

        int attackCode = mc.gameSettings.keyBindAttack.getKeyCode();
        int useCode = mc.gameSettings.keyBindUseItem.getKeyCode();

        if (clickPending) {
            clickPending = false;
            GameplayUtil.updateKeyState(attackCode);
        }
        if (blockHitPending) {
            blockHitPending = false;
            GameplayUtil.updateKeyState(useCode);
        }

        if (!isEnabled() || !canClick(mc) || !mc.gameSettings.keyBindAttack.isKeyDown()) return;

        if (!mc.thePlayer.isUsingItem()) {
            while (clickDelay <= 0L) {
                clickPending = true;
                clickDelay += nextClickDelay();
                KeyBinding.setKeyBindState(attackCode, false);
                KeyBinding.onTick(attackCode);
            }
        }
        if (blockHit.on() && blockHitDelay <= 0L && mc.gameSettings.keyBindUseItem.isKeyDown()
                && GameplayUtil.isSword(mc.thePlayer.getHeldItem())) {
            blockHitPending = true;
            KeyBinding.setKeyBindState(useCode, false);
            if (!mc.thePlayer.isUsingItem()) {
                blockHitDelay += blockHitDelayMs();
                KeyBinding.onTick(useCode);
            }
        }
    }

    /** Physical clicks participate in the same CPS cadence (mirrors Leader-Lite's LeftClickMouseEvent). */
    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled() || clickPending) return;
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) return;
        clickDelay += nextClickDelay();
    }
}
