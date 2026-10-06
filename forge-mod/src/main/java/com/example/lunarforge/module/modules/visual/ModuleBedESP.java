package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.EspRenderUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.render.BedESP): highlights
 * beds (merged head+foot box, DEFAULT 0.5625 / FULL 1.0 height) and, when
 * enabled, the obsidian covering them in purple. Leader-Lite captures bed
 * positions from a chunk-mesh-building mixin; without mixins LunarForge scans
 * the chunks around the player on a background thread instead (beds are
 * static, a refresh every second is plenty), which also removes the need for
 * the loadRenderers() remesh toggle.
 */
public final class ModuleBedESP extends Module {

    public enum BedMode implements ChoiceSetting.Option {
        DEFAULT("default"), FULL("full");
        private final String id;
        BedMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ChoiceSetting<BedMode> mode = choice("bedMode", BedMode.DEFAULT).label(() -> "Mode");
    private final ColorSetting customColor = color("customColor", 0xFF704070).label(() -> "Color");
    private final BoolSetting outline = bool("outline", false).label(() -> "Outline");
    private final BoolSetting obsidian = bool("obsidian", true).label(() -> "Obsidian");
    private final NumberSetting scanRange = integer("scanRange", 5, 2, 8).label(() -> "Scan Range (chunks)");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "LunarForge BedESP Scan");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean scanning = new AtomicBoolean();
    private final Set<BlockPos> beds = new HashSet<BlockPos>();
    private long lastScan;

    public ModuleBedESP() {
        super("BED_ESP", false);
    }

    @Override protected void layout(Page page) {
        page.section("renderOptions", s -> s.add(mode, outline, obsidian, scanRange));
        page.section("colorOptions", s -> s.add(customColor));
    }

    @Override protected void onDisable() {
        synchronized (beds) { beds.clear(); }
    }

    public double getHeight() {
        return mode.get() == BedMode.FULL ? 1.0 : 0.5625;
    }

    @SubscribeEvent
    public void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || !isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        long now = System.currentTimeMillis();
        if (now - lastScan < 1500L || !scanning.compareAndSet(false, true)) return;
        lastScan = now;
        final World world = mc.theWorld;
        final int centerX = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        final int centerZ = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
        final int range = scanRange.intValue();
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    Set<BlockPos> found = new HashSet<BlockPos>();
                    for (int cx = centerX - range; cx <= centerX + range; cx++) {
                        for (int cz = centerZ - range; cz <= centerZ + range; cz++) {
                            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                            if (!chunk.isLoaded()) continue;
                            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                            for (int x = 0; x < 16; x++) {
                                for (int z = 0; z < 16; z++) {
                                    for (int y = 0; y < 256; y++) {
                                        pos.set(cx * 16 + x, y, cz * 16 + z);
                                        IBlockState state = chunk.getBlockState(pos);
                                        if (state.getBlock() instanceof BlockBed
                                                && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                                            found.add(new BlockPos(pos));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    synchronized (beds) {
                        beds.clear();
                        beds.addAll(found);
                    }
                } finally {
                    scanning.set(false);
                }
            }
        });
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        RenderManager rm = mc.getRenderManager();
        List<BlockPos> snapshot;
        synchronized (beds) { snapshot = new ArrayList<BlockPos>(beds); }
        if (snapshot.isEmpty()) return;

        EspRenderUtil.enableRenderState();
        for (BlockPos blockPos : snapshot) {
            IBlockState state = mc.theWorld.getBlockState(blockPos);
            if (!(state.getBlock() instanceof BlockBed) || state.getValue(BlockBed.PART) != EnumPartType.HEAD) continue;
            BlockPos opposite = blockPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState oppositeState = mc.theWorld.getBlockState(opposite);
            if (!(oppositeState.getBlock() instanceof BlockBed)
                    || oppositeState.getValue(BlockBed.PART) != EnumPartType.FOOT) continue;

            if (obsidian.on()) {
                for (EnumFacing facing : new EnumFacing[]{EnumFacing.UP, EnumFacing.NORTH,
                        EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST}) {
                    BlockPos offsetX = blockPos.offset(facing);
                    BlockPos offsetZ = opposite.offset(facing);
                    boolean xObsidian = mc.theWorld.getBlockState(offsetX).getBlock() instanceof BlockObsidian;
                    boolean zObsidian = mc.theWorld.getBlockState(offsetZ).getBlock() instanceof BlockObsidian;
                    if (xObsidian && zObsidian) {
                        AxisAlignedBB box = new AxisAlignedBB(
                                Math.min(offsetX.getX(), offsetZ.getX()), offsetX.getY(),
                                Math.min(offsetX.getZ(), offsetZ.getZ()),
                                Math.max(offsetX.getX() + 1.0, offsetZ.getX() + 1.0),
                                offsetX.getY() + 1.0,
                                Math.max(offsetX.getZ() + 1.0, offsetZ.getZ() + 1.0))
                                .offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
                        drawObsidianBox(box);
                    } else if (xObsidian) {
                        drawObsidian(EspRenderUtil.blockBox(offsetX, 1.0, rm));
                    } else if (zObsidian) {
                        drawObsidian(EspRenderUtil.blockBox(offsetZ, 1.0, rm));
                    }
                }
            }

            AxisAlignedBB box = new AxisAlignedBB(
                    Math.min(blockPos.getX(), opposite.getX()), blockPos.getY(),
                    Math.min(blockPos.getZ(), opposite.getZ()),
                    Math.max(blockPos.getX() + 1.0, opposite.getX() + 1.0),
                    blockPos.getY() + getHeight(),
                    Math.max(blockPos.getZ() + 1.0, opposite.getZ() + 1.0))
                    .offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
            int argb = customColor.argb();
            if (outline.on()) {
                EspRenderUtil.drawBoundingBox(box, argb >> 16 & 255, argb >> 8 & 255, argb & 255, 255, 1.5F);
            }
            EspRenderUtil.drawFilledBox(box, argb >> 16 & 255, argb >> 8 & 255, argb & 255);
        }
        EspRenderUtil.disableRenderState();
        net.minecraft.client.renderer.GlStateManager.resetColor();
    }

    private void drawObsidianBox(AxisAlignedBB box) {
        if (outline.on()) EspRenderUtil.drawBoundingBox(box, 170, 0, 170, 255, 1.5F);
        EspRenderUtil.drawFilledBox(box, 170, 0, 170);
    }

    private void drawObsidian(AxisAlignedBB box) {
        if (outline.on()) EspRenderUtil.drawBoundingBox(box, 170, 0, 170, 255, 1.5F);
        EspRenderUtil.drawFilledBox(box, 170, 0, 170);
    }
}
