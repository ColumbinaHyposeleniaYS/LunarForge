package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.EspRenderUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.render.ChestESP): outlines
 * chests (normal/trapped colors), merges double chests into one box, and can
 * draw tracer lines to each chest. Leader-Lite borrowed the tracer opacity
 * from its Tracers module; here the tracer opacity is a local setting.
 */
public final class ModuleChestESP extends Module {
    private final ColorSetting chest = color("chest", 0xFFFFAA00).label(() -> "Chest");
    private final ColorSetting trappedChest = color("trappedChest", 0xFFFF2B00).label(() -> "Trapped Chest");
    private final ColorSetting enderChest = color("enderChest", 0xFF1A1100).label(() -> "Ender Chest");
    private final BoolSetting tracers = bool("tracers", false).label(() -> "Tracers");
    private final NumberSetting tracersOpacity = integer("tracersOpacity", 70, 0, 100).label(() -> "Tracers Opacity");

    public ModuleChestESP() {
        super("CHEST_ESP", false);
    }

    @Override protected void layout(Page page) {
        page.section("colorOptions", s -> s.add(chest, trappedChest, enderChest));
        page.section("renderOptions", s -> {
            s.add(tracers);
            s.add(tracersOpacity).hideIf(() -> !tracers.on());
        });
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        RenderManager rm = mc.getRenderManager();

        List<TileEntity> tileEntities = new ArrayList<TileEntity>(mc.theWorld.loadedTileEntityList);
        EspRenderUtil.enableRenderState();
        for (TileEntity tileEntity : tileEntities) {
            if (!(tileEntity instanceof TileEntityChest) && !(tileEntity instanceof TileEntityEnderChest)) continue;
            net.minecraft.util.BlockPos pos = tileEntity.getPos();
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            double minX = 0.0625, minZ = 0.0625, maxX = 0.9375, maxZ = 0.9375;
            int argb;
            if (block instanceof BlockChest) {
                argb = block.canProvidePower() ? trappedChest.argb() : chest.argb();
                EnumFacing facing = mc.theWorld.getBlockState(pos).getValue(BlockChest.FACING);
                switch (facing) {
                    case NORTH:
                        if (mc.theWorld.getBlockState(pos.east()).getBlock() == block) continue;
                        if (mc.theWorld.getBlockState(pos.west()).getBlock() == block) minX -= 1.0;
                        break;
                    case SOUTH:
                        if (mc.theWorld.getBlockState(pos.west()).getBlock() == block) continue;
                        if (mc.theWorld.getBlockState(pos.east()).getBlock() == block) maxX += 1.0;
                        break;
                    case WEST:
                        if (mc.theWorld.getBlockState(pos.north()).getBlock() == block) continue;
                        if (mc.theWorld.getBlockState(pos.south()).getBlock() == block) maxZ += 1.0;
                        break;
                    case EAST:
                        if (mc.theWorld.getBlockState(pos.south()).getBlock() == block) continue;
                        if (mc.theWorld.getBlockState(pos.north()).getBlock() == block) minZ -= 1.0;
                        break;
                    default:
                        continue;
                }
            } else {
                argb = enderChest.argb();
            }
            AxisAlignedBB box = new AxisAlignedBB(
                    pos.getX() + minX, pos.getY(), pos.getZ() + minZ,
                    pos.getX() + maxX, pos.getY() + 0.875, pos.getZ() + maxZ)
                    .offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
            EspRenderUtil.drawBoundingBox(box, argb >> 16 & 255, argb >> 8 & 255, argb & 255, 255, 1.5F);
            if (tracers.on()) {
                float alpha = tracersOpacity.intValue() / 100.0F;
                EspRenderUtil.drawCameraLine(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        (argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F, (argb & 255) / 255.0F,
                        alpha, 1.5F);
            }
        }
        EspRenderUtil.disableRenderState();
        GlStateManager.resetColor();
    }
}
