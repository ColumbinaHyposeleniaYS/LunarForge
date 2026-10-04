package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.GameplayUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.Eagle).
 * Automatically sneaks at edges so the player cannot fall off while bridging.
 *
 * Leader-Lite rewrites the movement input inside its MoveInputEvent; on LunarForge
 * the same result is achieved by momentarily forcing the sneak KeyBinding state
 * during the player tick (same pattern ModuleToggleSneak uses), so vanilla applies
 * its own sneak slowdown this tick. Leader-Lite's "sneaking-only" branch that undoes
 * and re-applies the vanilla sneak factor nets out to no visible change and is
 * intentionally not reproduced. The 1.14+ version warning was dropped because
 * LunarForge only targets 1.8.9.
 */
public final class ModuleEagle extends Module {
    private final NumberSetting minDelay = integer("minDelay", 2, 0, 10).label(() -> "Min Delay");
    private final NumberSetting maxDelay = integer("maxDelay", 3, 0, 10).label(() -> "Max Delay");
    private final BoolSetting directionCheck = bool("directionCheck", true).label(() -> "Direction Check");
    private final BoolSetting jumpCheck = bool("jumpCheck", true).label(() -> "Jump Check");
    private final BoolSetting pitchCheck = bool("pitchCheck", true).label(() -> "Pitch Check");
    private final BoolSetting blocksOnly = bool("blocksOnly", true).label(() -> "Blocks Only");
    private final BoolSetting sneakOnly = bool("sneakOnly", false).label(() -> "Sneaking Only");

    private int sneakDelay;

    public ModuleEagle() {
        super("EAGLE", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(minDelay, maxDelay);
            s.add(directionCheck, jumpCheck, pitchCheck, blocksOnly, sneakOnly);
        });
    }

    @Override protected void onDisable() {
        sneakDelay = 0;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings != null) GameplayUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    /** Mirrors Leader-Lite's PlayerUtil.canMove: nothing collides when the bounding box is offset by (x, -1, z). */
    private static boolean canMove(Minecraft mc, double x, double z) {
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(x, -1.0D, z)).isEmpty();
    }

    /** Mirrors Leader-Lite's MoveUtil.getAllowedHorizontalDistance. */
    private static float allowedHorizontalDistance(Minecraft mc) {
        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ));
        float slipperiness = mc.theWorld.getBlockState(below).getBlock().slipperiness * 0.91F;
        return mc.thePlayer.getAIMoveSpeed() * (0.16277136F / (slipperiness * slipperiness * slipperiness));
    }

    /** Mirrors Leader-Lite's MoveUtil.predictMovement (vanilla moveEntityWithHeading velocity math). */
    private static double[] predictMovement(Minecraft mc) {
        GameSettings gs = mc.gameSettings;
        float strafeInput = leftRightValue(gs) * 0.98F;
        float forwardInput = forwardBackValue(gs) * 0.98F;
        float inputMagnitude = strafeInput * strafeInput + forwardInput * forwardInput;
        if (inputMagnitude >= 1.0E-4F) {
            inputMagnitude = MathHelper.sqrt_float(inputMagnitude);
            if (inputMagnitude < 1.0F) inputMagnitude = 1.0F;
            inputMagnitude = allowedHorizontalDistance(mc) / inputMagnitude;
            float sinYaw = MathHelper.sin(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
            float cosYaw = MathHelper.cos(mc.thePlayer.rotationYaw * (float) Math.PI / 180.0F);
            strafeInput *= inputMagnitude;
            forwardInput *= inputMagnitude;
            return new double[]{strafeInput * cosYaw - forwardInput * sinYaw, forwardInput * cosYaw + strafeInput * sinYaw};
        }
        return new double[]{0.0D, 0.0D};
    }

    private static int forwardBackValue(GameSettings gs) {
        int value = 0;
        if (gs.keyBindForward.isKeyDown()) ++value;
        if (gs.keyBindBack.isKeyDown()) --value;
        return value;
    }

    private static int leftRightValue(GameSettings gs) {
        int value = 0;
        if (gs.keyBindLeft.isKeyDown()) ++value;
        if (gs.keyBindRight.isKeyDown()) --value;
        return value;
    }

    private boolean shouldSneak(Minecraft mc) {
        GameSettings gs = mc.gameSettings;
        if (directionCheck.on() && gs.keyBindForward.isKeyDown()) return false;
        if (jumpCheck.on() && gs.keyBindJump.isKeyDown()) return false;
        if (pitchCheck.on() && mc.thePlayer.rotationPitch < 69.0F) return false;
        if (sneakOnly.on() && !GameplayUtil.physicalDown(gs.keyBindSneak)) return false;
        ItemStack held = mc.thePlayer.getHeldItem();
        boolean holdingBlock = held != null && held.getItem() instanceof ItemBlock;
        return (!blocksOnly.on() || holdingBlock) && mc.thePlayer.onGround;
    }

    private static boolean canMoveSafely(Minecraft mc) {
        double[] offset = predictMovement(mc);
        return canMove(mc, mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    /** Leader-Lite TickEvent PRE: countdown and re-roll of the randomized sneak delay. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (sneakDelay > 0) sneakDelay--;
        if (sneakDelay == 0 && canMoveSafely(mc)) {
            int min = minDelay.intValue();
            int max = Math.max(maxDelay.intValue(), min);
            sneakDelay = (int) GameplayUtil.nextLong(min, max);
        }
    }

    /**
     * Leader-Lite MoveInputEvent replacement: restore the sneak key to its physical
     * state, then force it pressed for this tick when Eagle wants to sneak, so the
     * movement input computed right afterwards uses vanilla sneak behaviour.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.START || event.player != mc.thePlayer || !isEnabled()) return;
        if (mc.theWorld == null) return;

        int sneakCode = mc.gameSettings.keyBindSneak.getKeyCode();
        GameplayUtil.updateKeyState(sneakCode);
        if (mc.currentScreen != null) return;
        if (shouldSneak(mc) && (sneakDelay > 0 || canMoveSafely(mc))) {
            KeyBinding.setKeyBindState(sneakCode, true);
        }
    }
}
