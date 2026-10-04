package com.example.lunarforge.module.modules.hud.armorstatus;

import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.modules.hud.ModuleArmorStatus;
import com.example.lunarforge.module.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public final class ArmorElement {
    private final ModuleArmorStatus status;
    public final ModuleArmorStatus.Slot slot;
    public final ItemStack item;
    private final boolean armor;
    private String name = "", damage = "";
    private ColorSetting damageColor;
    private int nameWidth, damageWidth;
    private int width, height, itemX, itemY, nameX, nameY, damageX, damageY;

    public ArmorElement(ModuleArmorStatus status, ModuleArmorStatus.Slot slot, ItemStack item, boolean layout) {
        this.status = status;
        this.slot = slot;
        this.item = item;
        this.armor = !slot.held();
        if (layout) init();
    }

    private void init() {
        name = item != null && status.itemName.on() ? item.getDisplayName() : "";
        nameWidth = (int)Draw.width(EnumChatFormatting.getTextWithoutFormattingCodes(name));
        String[] d = new String[1];
        damageColor = durability(status.maxDamage.on(), d);
        damage = d[0];
        damageWidth = (int)Draw.width(EnumChatFormatting.getTextWithoutFormattingCodes(damage));
        layout(status.durabilityPosition());
    }

    public ColorSetting durability(boolean showMax, String[] text) {
        boolean on = armor ? status.armorDamage.on() : status.itemDamage.on();
        if (!on || item == null || !item.getItem().isDamageable() || item.getItem().getMaxDamage() <= 0
            || (item.hasTagCompound() && item.getTagCompound().getBoolean("Unbreakable") && status.hideUnbreakableDurability.on())) {
            text[0] = "";
            return status.damageColor(100);
        }
        int max = item.getMaxDamage(), left = max - item.getItemDamage(), percent = left * 100 / max;
        switch (status.damageDisplay.get()) {
            case VALUE: text[0] = left + (showMax ? "/" + max : ""); break;
            case PERCENT: text[0] = percent + "%"; break;
            default: text[0] = ""; break;
        }
        return status.damageColor(percent);
    }

    public void layout(ModuleArmorStatus.Position pos) {
        if (item == null) { width = 0; height = 0; return; }
        if (pos == ModuleArmorStatus.Position.TOP || pos == ModuleArmorStatus.Position.BOTTOM) stacked(pos == ModuleArmorStatus.Position.TOP);
        else beside(pos == ModuleArmorStatus.Position.LEFT);
    }

    private void beside(boolean left) {
        int fh = Draw.fontHeight(), text = Math.max(nameWidth, damageWidth);
        int lines = fh * ((name.isEmpty() ? 0 : 1) + (damage.isEmpty() ? 0 : 1));
        width = 16 + (text == 0 ? 0 : 2 + text);
        height = Math.max(16, lines);
        if (left) { itemX = width - 16; nameX = text - nameWidth; damageX = text - damageWidth; }
        else { itemX = 0; nameX = width - text; damageX = width - text; }
        itemY = 0;
        nameY = half(height, lines);
        damageY = nameY + (name.isEmpty() ? 0 : fh);
    }

    private void stacked(boolean top) {
        int fh = Draw.fontHeight();
        int row = 16 + (nameWidth == 0 ? 0 : 2 + nameWidth);
        int extra = damage.isEmpty() ? 0 : 2 + fh;
        width = Math.max(row, damageWidth);
        height = 16 + extra;
        itemX = half(width, row);
        itemY = top ? extra : 0;
        nameX = itemX + 16 + 2;
        nameY = itemY + half(16, fh);
        damageX = 0;
        damageY = top ? 3 : 16;
    }

    private static int half(int a, int b) { return Math.round((a - b) / 2.0f); }

    public int width() { return width; }
    public int height() { return height; }

    public void draw(float x, float y) {
        if (item == null) return;
        drawItem(x + itemX, y + itemY);
        boolean shadow = status.textShadow.on();
        if (!name.isEmpty()) Draw.text(status.nameTextColor, name, x + nameX, y + nameY, shadow);
        if (damageColor != null && !damage.isEmpty()) Draw.text(damageColor, damage, x + damageX, y + damageY, shadow);
    }

    public void drawItem(float x, float y) {
        Draw.item(item, x, y);
        overlay(item, (int)x, (int)y, status.damageOverlay.on(), status.itemCount.on());
    }

    public static void overlay(ItemStack item, int x, int y, boolean bar, boolean count) {
        if (item == null || !bar && !count) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 500);
        if (bar && item.isItemDamaged()) {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            double d = (double)item.getItemDamage() / item.getMaxDamage();
            int length = (int)Math.round(13.0 - d * 13.0), g = (int)Math.round(255.0 - d * 255.0);
            Draw.rect(x + 2, y + 14, 13, 2, 0xFF000000);
            Draw.rect(x + 2, y + 14, 12, 1, 0xFF000000 | (255 - g) / 4 << 16 | 0x3F00);
            Draw.rect(x + 2, y + 14, length, 1, 0xFF000000 | 255 - g << 16 | g << 8);
            GlStateManager.enableDepth();
        }
        if (count) {
            int n = count(item);
            if (n > 1) {
                GlStateManager.disableDepth();
                String s = String.valueOf(n);
                Draw.text(s, x + 17 - Draw.width(s), y + 9, 0xFFFFFFFF, true);
                GlStateManager.enableDepth();
            }
        }
        GlStateManager.popMatrix();
    }

    private static int count(ItemStack item) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return 0;
        final Item type = item.getItem();
        int damage;
        Item match;
        if (item.getMaxStackSize() > 1) { match = type; damage = item.getItemDamage(); }
        else if (type == Items.bow) { match = Items.arrow; damage = -1; }
        else return 0;
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory)
            if (s != null && s.getItem() == match && (damage == -1 || s.getItemDamage() == damage)) n += s.stackSize;

        ItemStack held = player.inventory.getItemStack();
        if (held != null && held.getItem() == match && (damage == -1 || held.getItemDamage() == damage)) n += held.stackSize;
        return n;
    }
}
