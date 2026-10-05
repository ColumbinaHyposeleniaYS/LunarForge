package com.example.lunarforge.module.modules.world;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import com.example.lunarforge.util.GameplayUtil;
import java.lang.reflect.Field;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockIce;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Ported from Vape v4 (gg.vape.module.blatant.Scaffold + its scaffold modes).
 *
 * Legit mode is a faithful port of Vape's LegitScaffoldMode: it only presses
 * sneak for you at the edge of a block while you bridge backwards
 * (fastbridge/ninja/eagle style), with the randomized sneak delay grace
 * window. Placing blocks stays manual, exactly like Vape.
 *
 * Normal mode adds the auto placement of Vape's blatant modes, adapted to a
 * plain Forge tick loop: it picks the replaceable block under/behind you in
 * the movement direction, finds a solid neighbour to click on, optionally
 * rotates to the placement face using Vape's rotation speed formula
 * (min(2 + angularDistance/8, 12)° per tick) and right-click places it,
 * pacing itself through the vanilla rightClickDelayTimer. Both modes share
 * the edge sneak so Normal also refuses to walk off while placing.
 *
 * Not ported from Vape: GodBridge's auto-driving (PlayerMovementTaskManager),
 * the TellyBridge mode and the item blacklist/whitelist UI — usable blocks
 * here are full-cube ItemBlocks (ice excluded, like Vape's blacklist).
 */
public final class ModuleScaffold extends Module {

    public enum Mode implements ChoiceSetting.Option {
        LEGIT("Legit"), NORMAL("Normal");

        private final String label;

        Mode(String label) { this.label = label; }

        @Override public String langId() { return label; }
    }

    private static final EnumFacing[] SUPPORT_FACES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN};

    private final ChoiceSetting<Mode> mode = choice("mode", Mode.LEGIT).label(() -> "Mode");
    private final NumberSetting sneakDelay = integer("sneakDelay", 100, 0, 500).label(() -> "Sneak Delay (ms)");
    private final BoolSetting requireSneak = bool("requireSneak", false).label(() -> "Require Sneak")
            .hideIf(() -> !mode.is(Mode.LEGIT));
    private final BoolSetting autoSwitch = bool("autoSwitch", true).label(() -> "Auto Switch")
            .hideIf(() -> !mode.is(Mode.NORMAL));
    private final BoolSetting rotations = bool("rotations", true).label(() -> "Rotations")
            .hideIf(() -> !mode.is(Mode.NORMAL));
    private final BoolSetting pitchCheck = bool("pitchCheck", false).label(() -> "Pitch Check");
    private final NumberSetting pitchValue = decimal("pitchValue", 45.0F, 0.0F, 90.0F).label(() -> "Pitch")
            .hideIf(() -> !pitchCheck.on());

    private static final Field RIGHT_CLICK_DELAY = Fields.find(Minecraft.class, "rightClickDelayTimer", "field_71467_ac");

    private final Random random = new Random();
    private long sneakDelayMs = 100L;
    private long edgeSneakTimer;
    private long standDelayTimer;
    private int placeWaitTicks;

    public ModuleScaffold() {
        super("SCAFFOLD", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(mode, sneakDelay, pitchCheck);
            s.add(pitchValue);
            s.add(requireSneak);
            s.add(autoSwitch, rotations);
        });
    }

    @Override protected void onEnable() {
        sneakDelayMs = rollSneakDelay();
    }

    @Override protected void onDisable() {
        Minecraft mc = Minecraft.getMinecraft();
        // Vanilla unpresses every binding when a GUI opens, so with a screen open
        // there is nothing to restore — and restoring from the raw keyboard state
        // would press sneak for Shift held in the GUI (shift-click).
        if (mc.thePlayer != null && mc.gameSettings != null && mc.currentScreen == null) {
            GameplayUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
        placeWaitTicks = 0;
    }

    /** Vape RandomValue("Sneak delay"): delay until standing after sneaking, kept loose around the setting. */
    private long rollSneakDelay() {
        int delay = sneakDelay.intValue();
        return delay <= 0L ? 0L : GameplayUtil.nextLong(Math.max(1L, delay - 50L), delay + 50L);
    }

    private boolean shouldScaffold(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null) return false;
        if (requireSneak.on() && mode.is(Mode.LEGIT) && !GameplayUtil.physicalDown(mc.gameSettings.keyBindSneak)) return false;
        if (mc.thePlayer.isSpectator()) return false;
        if (pitchCheck.on() && mc.thePlayer.rotationPitch < pitchValue.value()) return false;
        return true;
    }

    /**
     * Vape LegitScaffoldMode/ScaffoldEdgeSneakHelper edge detection: the
     * bounding box shrunk by 0.2 and offset by (motionX, -1, motionZ) must
     * collide with nothing, i.e. there is no floor where we are heading.
     */
    private static boolean atEdge(Minecraft mc) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().expand(-0.2D, 0.0D, -0.2D)
                .offset(mc.thePlayer.motionX, -1.0D, mc.thePlayer.motionZ);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box).isEmpty();
    }

    /** LegitScaffoldMode port: force sneak for this tick when heading off an edge, honouring the delay grace window. */
    private void edgeSneak(Minecraft mc) {
        KeyBinding sneakKey = mc.gameSettings.keyBindSneak;
        int sneakCode = sneakKey.getKeyCode();
        boolean sneakKeyWasDown = GameplayUtil.physicalDown(sneakKey);
        boolean shouldSneak = false;
        float forwardInput = 0.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindBack)) forwardInput += 1.0F;
        if (GameplayUtil.physicalDown(mc.gameSettings.keyBindForward)) forwardInput -= 1.0F;
        boolean notMovingForward = forwardInput <= 0.0F;
        if (notMovingForward && mc.thePlayer.onGround && atEdge(mc)) shouldSneak = true;
        boolean skipTimerReset = false;
        if (!shouldSneak && sneakDelayMs > 30L
                && System.currentTimeMillis() - edgeSneakTimer < sneakDelayMs) {
            shouldSneak = true;
            skipTimerReset = true;
        }
        if (mc.thePlayer.onGround) {
            if (shouldSneak) {
                if (!mc.thePlayer.isSneaking()) sneakDelayMs = rollSneakDelay();
                KeyBinding.setKeyBindState(sneakCode, true);
                standDelayTimer = System.currentTimeMillis();
                if (!skipTimerReset) edgeSneakTimer = System.currentTimeMillis();
            } else if (!sneakKeyWasDown) {
                KeyBinding.setKeyBindState(sneakCode, false);
            }
        }
    }

    /** Usable scaffold block: full-cube ItemBlock, not slippery ice, not an interactable block (Vape's blacklist, simplified). */
    private static boolean isUsableBlock(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == null || block instanceof BlockIce) return false;
        return block.isFullCube() && !GameplayUtil.isInteractable(block);
    }

    private static boolean replaceable(Minecraft mc, BlockPos pos) {
        if (pos.getY() < 0 || pos.getY() >= 256) return false;
        return mc.theWorld.getBlockState(pos).getBlock().getMaterial().isReplaceable();
    }

    private static boolean solidSupport(Minecraft mc, BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block.getMaterial() == net.minecraft.block.material.Material.air) return false;
        return !GameplayUtil.isInteractable(block) && block.getMaterial().isSolid();
    }

    /** Hit vector on the support block's face pointing at the target, with a small random jitter like Vape's aim points. */
    private static Vec3 hitVec(BlockPos support, EnumFacing face) {
        double x = support.getX() + 0.5D + face.getFrontOffsetX() * 0.5D;
        double y = support.getY() + 0.5D + face.getFrontOffsetY() * 0.5D;
        double z = support.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D;
        double jitter = (Math.random() - 0.5D) * 0.5D;
        if (face.getFrontOffsetX() != 0) { y += jitter; z += (Math.random() - 0.5D) * 0.5D; }
        else if (face.getFrontOffsetY() != 0) { x += jitter; z += (Math.random() - 0.5D) * 0.5D; }
        else { x += jitter; y += (Math.random() - 0.5D) * 0.5D; }
        return new Vec3(x, y, z);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.player != mc.thePlayer || mc.theWorld == null || !isEnabled()) return;
        if (event.phase == TickEvent.Phase.START) {
            if (shouldScaffold(mc)) edgeSneak(mc);
        } else if (event.phase == TickEvent.Phase.END) {
            // Never touch the sneak binding while a GUI is open: Shift there belongs
            // to shift-clicks, and restoring from the raw keyboard state would sneak
            // the player in the world while the inventory is open.
            if (mc.currentScreen == null) {
                GameplayUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
            }
        }
    }

    /** Normal mode: pick the block slot, find the placement target, optionally rotate, right-click place. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !isEnabled() || !mode.is(Mode.NORMAL)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!shouldScaffold(mc)) return;
        if (RIGHT_CLICK_DELAY != null && Fields.getInt(RIGHT_CLICK_DELAY, mc) > 0) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (!isUsableBlock(held)) {
            if (!autoSwitch.on()) return;
            int slot = -1;
            for (int i = 0; i < 9; i++) {
                if (isUsableBlock(mc.thePlayer.inventory.getStackInSlot(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) return;
            mc.thePlayer.inventory.currentItem = slot;
            held = mc.thePlayer.getHeldItem();
        }

        BlockPos target = findTarget(mc);
        if (target == null) {
            placeWaitTicks = 0;
            return;
        }
        if (place(mc, target)) placeWaitTicks = 0; else placeWaitTicks++;
    }

    /**
     * The block to fill: under and behind the player in the movement
     * direction (diagonal aware), falling back to the side columns and the
     * block straight below (covers towers and the first step off an edge).
     */
    private BlockPos findTarget(Minecraft mc) {
        int baseY = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY) - 1;
        BlockPos under = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), baseY, MathHelper.floor_double(mc.thePlayer.posZ));
        double dx = mc.thePlayer.motionX;
        double dz = mc.thePlayer.motionZ;
        int ox = 0;
        int oz = 0;
        if (dx * dx + dz * dz > 0.0025D) {
            ox = Math.abs(dx) > 0.03D ? (dx > 0.0D ? 1 : -1) : 0;
            oz = Math.abs(dz) > 0.03D ? (dz > 0.0D ? 1 : -1) : 0;
        } else {
            Vec3 look = GameplayUtil.lookVector(mc.thePlayer.rotationYaw, 0.0F);
            ox = Math.abs(look.xCoord) > 0.3D ? (look.xCoord > 0.0D ? 1 : -1) : 0;
            oz = Math.abs(look.zCoord) > 0.3D ? (look.zCoord > 0.0D ? 1 : -1) : 0;
        }
        BlockPos[] candidates = {under.add(ox, 0, oz), under.add(ox, 0, 0), under.add(0, 0, oz), under};
        for (BlockPos candidate : candidates) {
            if (!replaceable(mc, candidate)) continue;
            for (EnumFacing facing : SUPPORT_FACES) {
                BlockPos support = candidate.offset(facing);
                if (!solidSupport(mc, support)) continue;
                EnumFacing clickFace = facing.getOpposite();
                Vec3 hit = hitVec(support, clickFace);
                if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(hit) > 4.5D) continue;
                return candidate;   // placement details are re-derived in place()
            }
        }
        return null;
    }

    private boolean place(Minecraft mc, BlockPos target) {
        // re-derive support + face for the chosen target
        BlockPos support = null;
        EnumFacing clickFace = null;
        Vec3 hit = null;
        for (EnumFacing facing : SUPPORT_FACES) {
            BlockPos neighbour = target.offset(facing);
            if (!solidSupport(mc, neighbour)) continue;
            EnumFacing face = facing.getOpposite();
            Vec3 candidateHit = hitVec(neighbour, face);
            if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(candidateHit) > 4.5D) continue;
            support = neighbour;
            clickFace = face;
            hit = candidateHit;
            break;
        }
        if (support == null) return false;

        float aimYaw = GameplayUtil.aimYaw(mc.thePlayer.posX, mc.thePlayer.posZ, hit.xCoord, hit.zCoord);
        float aimPitch = GameplayUtil.aimPitch(mc.thePlayer.posX,
                mc.thePlayer.getPositionEyes(1.0F).yCoord, mc.thePlayer.posZ, hit.xCoord, hit.yCoord, hit.zCoord);
        if (rotations.on()) {
            float yawDiff = MathHelper.wrapAngleTo180_float(aimYaw - mc.thePlayer.rotationYaw);
            float pitchDiff = MathHelper.wrapAngleTo180_float(aimPitch - mc.thePlayer.rotationPitch);
            float step = (float) Math.min(2.0F + Math.abs(yawDiff) / 8.0F, 12.0F);
            mc.thePlayer.rotationYaw += MathHelper.clamp_float(yawDiff, -step, step);
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(
                    mc.thePlayer.rotationPitch + MathHelper.clamp_float(pitchDiff, -step, step), -90.0F, 90.0F);
            if (Math.abs(yawDiff) > 5.0F || Math.abs(pitchDiff) > 5.0F) {
                if (placeWaitTicks < 8) return false;   // keep rotating; force the place after 8 ticks so we never stall
            }
        }
        placeWaitTicks = 0;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        boolean placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, support, clickFace, hit);
        if (placed) {
            if (RIGHT_CLICK_DELAY != null) Fields.setInt(RIGHT_CLICK_DELAY, mc, 4);
            mc.thePlayer.swingItem();
        }
        return placed;
    }
}
