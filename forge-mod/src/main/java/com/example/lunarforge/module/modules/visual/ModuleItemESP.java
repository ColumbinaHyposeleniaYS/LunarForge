package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.EspRenderUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Ported from Leader-Lite (leader.module.modules.render.ItemESP): highlights
 * dropped emerald/diamond/gold/iron items with a filled box, an optional
 * outline and an optional world-space item-count label. Entities are sorted
 * far to near inline (Leader-Lite's RenderEntityCache snapshot) and item
 * stacks on the same block are aggregated into one box.
 */
public final class ModuleItemESP extends Module {
    private final NumberSetting opacity = integer("opacity", 25, 0, 100).label(() -> "Opacity");
    private final BoolSetting outline = bool("outline", false).label(() -> "Outline");
    private final BoolSetting itemCount = bool("itemCount", true).label(() -> "Item Count");
    private final BoolSetting autoScale = bool("autoScale", true).label(() -> "Auto Scale");
    private final BoolSetting emeralds = bool("emeralds", true).label(() -> "Emeralds");
    private final BoolSetting diamonds = bool("diamonds", true).label(() -> "Diamonds");
    private final BoolSetting gold = bool("gold", true).label(() -> "Gold");
    private final BoolSetting iron = bool("iron", true).label(() -> "Iron");

    public ModuleItemESP() {
        super("ITEM_ESP", false);
    }

    @Override protected void layout(Page page) {
        page.section("filterOptions", s -> s.add(emeralds, diamonds, gold, iron));
        page.section("renderOptions", s -> s.add(opacity, outline, itemCount, autoScale));
    }

    private boolean shouldHighlight(int itemId) {
        return emeralds.on() && isEmeraldItem(itemId) || diamonds.on() && isDiamondItem(itemId)
                || gold.on() && isGoldItem(itemId) || iron.on() && isIronItem(itemId);
    }

    private boolean isEmeraldItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.emerald || block == Blocks.emerald_block || block == Blocks.emerald_ore;
    }

    private boolean isDiamondItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.diamond || item == Items.diamond_sword || item == Items.diamond_pickaxe
                || item == Items.diamond_shovel || item == Items.diamond_axe || item == Items.diamond_hoe
                || item == Items.diamond_helmet || item == Items.diamond_chestplate
                || item == Items.diamond_leggings || item == Items.diamond_boots
                || block == Blocks.diamond_block || block == Blocks.diamond_ore;
    }

    private boolean isGoldItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.gold_ingot || item == Items.gold_nugget || item == Items.golden_apple
                || block == Blocks.gold_block || block == Blocks.gold_ore;
    }

    private boolean isIronItem(int itemId) {
        Item item = Item.getItemById(itemId);
        Block block = Block.getBlockFromItem(item);
        return item == Items.iron_ingot || block == Blocks.iron_block || block == Blocks.iron_ore;
    }

    /** Emerald green, diamond aqua, gold yellow, iron white (Leader-Lite's chat palette). */
    private int itemColor(int itemId) {
        if (isEmeraldItem(itemId)) return 0xFF55FF55;
        if (isDiamondItem(itemId)) return 0xFF55FFFF;
        if (isGoldItem(itemId)) return 0xFFFFFF55;
        return isIronItem(itemId) ? 0xFFFFFFFF : 0xFFAAAAAA;
    }

    private int itemPriority(int itemId) {
        if (isEmeraldItem(itemId)) return 4;
        if (isDiamondItem(itemId)) return 3;
        if (isGoldItem(itemId)) return 2;
        return isIronItem(itemId) ? 1 : 0;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;

        LinkedHashMap<ItemData, Integer> itemMap = new LinkedHashMap<ItemData, Integer>();
        List<EntityItem> entities = new ArrayList<EntityItem>();
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityItem) entities.add((EntityItem) o);
        }
        for (EntityItem entityItem : entities) {
            if (entityItem.ticksExisted < 3) continue;
            if (!entityItem.ignoreFrustumCheck && !EspRenderUtil.isInViewFrustum(entityItem.getEntityBoundingBox(), 0.125)) continue;
            ItemStack stack = entityItem.getEntityItem();
            if (stack == null || stack.stackSize <= 0) continue;
            int itemId = Item.getIdFromItem(stack.getItem());
            if (!shouldHighlight(itemId)) continue;
            float t = event.partialTicks;
            ItemData data = new ItemData(itemId,
                    EspRenderUtil.lerpDouble(entityItem.posX, entityItem.lastTickPosX, t),
                    EspRenderUtil.lerpDouble(entityItem.posY, entityItem.lastTickPosY, t),
                    EspRenderUtil.lerpDouble(entityItem.posZ, entityItem.lastTickPosZ, t));
            Integer previous = itemMap.get(data);
            itemMap.put(data, stack.stackSize + (previous == null ? 0 : previous));
        }
        if (itemMap.isEmpty()) return;

        List<Map.Entry<ItemData, Integer>> entries = new ArrayList<Map.Entry<ItemData, Integer>>(itemMap.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<ItemData, Integer>>() {
            @Override public int compare(Map.Entry<ItemData, Integer> a, Map.Entry<ItemData, Integer> b) {
                return Integer.compare(itemPriority(a.getKey().itemId), itemPriority(b.getKey().itemId));
            }
        });

        for (Map.Entry<ItemData, Integer> entry : entries) {
            ItemData data = entry.getKey();
            int color = itemColor(data.itemId);
            int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
            double distance = mc.getRenderViewEntity().getDistance(data.x, data.y, data.z);
            double scale = 0.5 + 0.375 * ((Math.max(6.0, autoScale.on() ? distance : 6.0) - 6.0) / 28.0);
            net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
            double x = data.x - rm.viewerPosX;
            double y = data.y - rm.viewerPosY;
            double z = data.z - rm.viewerPosZ;
            AxisAlignedBB box = new AxisAlignedBB(x - scale * 0.5, y, z - scale * 0.5,
                    x + scale * 0.5, y + scale, z + scale * 0.5);
            EspRenderUtil.enableRenderState();
            if (opacity.intValue() > 0) {
                EspRenderUtil.drawFilledBox(box, red, green, blue);
                GlStateManager.resetColor();
            }
            if (outline.on()) {
                EspRenderUtil.drawBoundingBox(box, red, green, blue, 255, 1.5F);
                GlStateManager.resetColor();
            }
            EspRenderUtil.disableRenderState();
            if (itemCount.on()) {
                drawCountLabel(mc, entry.getValue(), x, y + scale * 0.5, distance);
            }
        }
    }

    /** World-space billboard count label (vanilla font metrics: 2x Leader-Lite's 18px font scale). */
    private void drawCountLabel(Minecraft mc, int count, double x, double y, double distance) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
        float flip = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
        GlStateManager.rotate(mc.getRenderManager().playerViewX, flip, 0.0F, 0.0F);
        double fontScale = -0.0875 - 0.065625 * ((Math.max(6.0, autoScale.on() ? distance : 6.0) - 6.0) / 28.0);
        GlStateManager.scale(fontScale, fontScale, 1.0);
        GlStateManager.disableDepth();
        String text = String.format("%d", count);
        float width = mc.fontRendererObj.getStringWidth(text);
        float height = mc.fontRendererObj.FONT_HEIGHT;
        float tx = (width / 2.0F - 0.5F) * -1.0F;
        float ty = (height / 2.0F - 0.5F) * -1.0F;
        GlStateManager.enableBlend();
        String plain = text.replaceAll("(?i)\u00A7[\\da-f]", "");
        mc.fontRendererObj.drawString(plain, tx + 1.0f, ty, 0, false);
        mc.fontRendererObj.drawString(plain, tx - 1.0f, ty, 0, false);
        mc.fontRendererObj.drawString(plain, tx, ty + 1.0f, 0, false);
        mc.fontRendererObj.drawString(plain, tx, ty - 1.0f, 0, false);
        mc.fontRendererObj.drawString(text, tx, ty, -1, false);
        GlStateManager.enableDepth();
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    /** Aggregation key: item id plus the integer block the stack rests on (Leader-Lite ItemData). */
    private static final class ItemData {
        private final int hashCode;
        final int itemId;
        final double x, y, z;

        ItemData(int id, double x, double y, double z) {
            this.itemId = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hashCode = Objects.hash(id, (int) x, (int) y, (int) z);
        }

        @Override public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ItemData)) return false;
            ItemData other = (ItemData) object;
            return itemId == other.itemId && (int) x == (int) other.x
                    && (int) y == (int) other.y && (int) z == (int) other.z;
        }

        @Override public int hashCode() { return hashCode; }
    }
}
