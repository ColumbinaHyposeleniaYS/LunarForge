package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.ChoiceSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModulePotionCounter extends Module {
    public enum PotionCounterType implements ChoiceSetting.Option {
        POTION("potion"), SOUP("soup");
        private final String id;
        PotionCounterType(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ChoiceSetting<PotionCounterType> potionCounter = choice("potionCounter", PotionCounterType.POTION);

    private int count;

    public ModulePotionCounter() {
        super("POTION_COUNTER", false);
        hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(potionCounter));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        count = count();
    }

    private int count() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return 0;
        InventoryPlayer inventory = player.inventory;
        int total = 0;
        for (ItemStack stack : inventory.mainInventory) total += stackSize(stack);

        total += stackSize(inventory.getItemStack());
        return total;
    }

    private int stackSize(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        if (potionCounter.is(PotionCounterType.SOUP)) {
            return stack.getItem() == Items.mushroom_stew ? stack.stackSize : 0;
        }
        if (!(stack.getItem() instanceof ItemPotion) || !ItemPotion.isSplash(stack.getMetadata())) return 0;
        for (PotionEffect effect : ((ItemPotion)stack.getItem()).getEffects(stack)) {
            if (effect.getPotionID() == 6) return stack.stackSize;
        }
        return 0;
    }

    private final class Hud extends TextHud {
        Hud() { super(ModulePotionCounter.this, 0, 62, HudAnchor.TOP_CENTER, sizes(10, 18, 22, 40, 56, 62)); }

        @Override protected String text(boolean preview) {
            int n = Minecraft.getMinecraft().thePlayer == null ? 0 : ModulePotionCounter.this.count;
            String type = potionCounter.is(PotionCounterType.SOUP) ? "soup" : "pot";
            return n + " " + (n == 1 ? type : type + "s");
        }
    }
}
