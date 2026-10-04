package com.example.lunarforge.module.modules.hud.armorstatus;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ArmorProtection extends Module {
    private final BoolSetting onlyShowInInventory = bool("onlyShowInInventory", false);
    private String protection = "";

    public ArmorProtection() {
        super("ARMORSTATUS_PROTECTION_CHILD", false);
        hud(new TextHud(this, 0, 0, HudAnchor.BOTTOM_RIGHT, TextHud.sizes(18, 20, 28, 34, 50, 62)) {
            @Override protected String text(boolean preview) {
                if (preview) return "80%";
                if (onlyShowInInventory.on() && !inInventory()) return null;
                return protection;
            }
        });
    }

    @Override protected void layout(Page page) { page.add(onlyShowInInventory); }

    private static boolean inInventory() {
        Object screen = Minecraft.getMinecraft().currentScreen;
        return screen instanceof GuiInventory || screen instanceof GuiContainerCreative;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && isEnabled()) update();
    }

    private void update() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        int armor = 0, enchant = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack == null) continue;
            if (stack.getItem() instanceof ItemArmor) armor += ((ItemArmor)stack.getItem()).damageReduceAmount;
            Map<Integer, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
            Integer level = enchants.get(Enchantment.protection.effectId);
            if (level != null) enchant += (int)Math.floor((6 + level * level) / 3.0f * 0.75f);
        }
        float fromArmor = Math.min(armor, 20.0f) / 25.0f;
        float fromEnchants = Math.min(Math.min(enchant, 25) * 0.75f, 20.0f) / 25.0f;
        float total = 1.0f - (1.0f - fromArmor) * (1.0f - fromEnchants);
        protection = String.format(Locale.ROOT, "%3.1f%%", total * 100.0f);
    }
}
