package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.HypixelLocation;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.RowHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

final class BedwarsResources extends Module {
    private static final ScheduledExecutorService THREAD = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lunar-bedwars-resource-counter-thread");
        t.setDaemon(true);
        return t;
    });

    private static ItemStack[] icons;

    private static ItemStack icon(int i) {
        if (icons == null) icons = new ItemStack[]{new ItemStack(Items.iron_ingot), new ItemStack(Items.gold_ingot), new ItemStack(Items.diamond), new ItemStack(Items.emerald)};
        return icons[i];
    }

    private final ModuleHypixelBedwars bedwars;
    private final BoolSetting textShadow = bool("textShadow", true);
    private final BoolSetting background = bool("background", true);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting autoAlign = bool("autoAlign", true);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ChoiceSetting<RowHud.Alignment> alignment = choice("alignment", RowHud.Alignment.LEFT);
    private final BoolSetting showTitle = bool("showTitle", true);
    private final BoolSetting useResourceIcons = bool("useResourceIcons", false);
    private final BoolSetting showResourcesInEnderchest = bool("showResourcesInEnderchest", false);
    private final BoolSetting showIron = bool("showIron", true);
    private final BoolSetting showGold = bool("showGold", true);
    private final BoolSetting showDiamonds = bool("showDiamonds", true);
    private final BoolSetting showEmeralds = bool("showEmeralds", true);
    private final ColorSetting titleColor = color("titleColor", -171);
    private final ColorSetting textColor = color("textColor", -1);
    private final ColorSetting numberColor = color("numberColor", -11141291);
    private final ColorSetting dividerColor = color("dividerColor", -8355712);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", -1627389952);

    private volatile boolean counting;
    private volatile int iron, gold, diamonds, emeralds, chestIron, chestGold, chestDiamonds, chestEmeralds;

    BedwarsResources(ModuleHypixelBedwars bedwars) {
        super("HYPIXEL_BEDWARS_RESOURCE_COUNTER_CHILD", true);
        this.bedwars = bedwars;
        hud(new Hud());
        THREAD.submit(new Runnable() {
            @Override public void run() {
                count(Minecraft.getMinecraft().currentScreen);
                THREAD.schedule(this, 500L, TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override protected void layout(Page page) {
        page.section("hudDisplayOptions", s -> {
            s.add(textShadow);
            s.group(background, b -> b.group(border, t -> t.add(borderThickness)));
            s.add(autoAlign);
            s.add(alignment).hideIf(autoAlign::on);
        });
        page.section("renderOptions", s -> s.add(showTitle, useResourceIcons, showResourcesInEnderchest, showIron, showGold, showDiamonds, showEmeralds));
        page.section("colorOptions", s -> {
            s.add(titleColor, textColor, numberColor, dividerColor);
            s.add(backgroundColor).hideIf(() -> !background.on());
            s.add(borderColor).hideIf(() -> !border.on());
        });
    }

    @SubscribeEvent
    public void onScreen(GuiOpenEvent event) {
        if (event.gui == null) count(Minecraft.getMinecraft().currentScreen);
        else THREAD.schedule(() -> count(Minecraft.getMinecraft().currentScreen), 100L, TimeUnit.MILLISECONDS);
    }

    void location(HypixelLocation.Location l) {
        if (!bedwars.onHypixel()) return;
        if (l == null) l = HypixelLocation.get();
        counting = bedwars.inBedwars() && l != null && l.map != null;
        if (!counting) chestIron = chestGold = chestDiamonds = chestEmeralds = 0;
    }

    private void count(GuiScreen screen) {
        if (!counting) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null) return;
            int i = 0, g = 0, d = 0, e = 0;
            for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
                if (s == null) continue;
                Item item = s.getItem();
                if (item == Items.iron_ingot) i += s.stackSize;
                else if (item == Items.gold_ingot) g += s.stackSize;
                else if (item == Items.diamond) d += s.stackSize;
                else if (item == Items.emerald) e += s.stackSize;
            }
            iron = i; gold = g; diamonds = d; emeralds = e;
            if (screen instanceof GuiChest) {
                IInventory chest = ((net.minecraft.inventory.ContainerChest)((GuiChest)screen).inventorySlots).getLowerChestInventory();
                String name = chest.getDisplayName().getUnformattedText();
                if (name.equals("Ender Chest") || name.equals("container.enderchest")) {
                    int ci = 0, cg = 0, cd = 0, ce = 0;
                    Container c = ((GuiChest)screen).inventorySlots;
                    for (Slot slot : c.inventorySlots) {
                        ItemStack s = slot == null ? null : slot.getStack();
                        if (s == null) continue;
                        Item item = s.getItem();
                        if (item == Items.iron_ingot) ci += s.stackSize;
                        else if (item == Items.gold_ingot) cg += s.stackSize;
                        else if (item == Items.diamond) cd += s.stackSize;
                        else if (item == Items.emerald) ce += s.stackSize;
                    }

                    chestIron = Math.max(0, ci - i);
                    chestGold = Math.max(0, cg - g);
                    chestDiamonds = Math.max(0, cd - d);
                    chestEmeralds = Math.max(0, ce - e);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private final class Hud extends RowHud {
        Hud() { super(BedwarsResources.this, 0.0f, 0.0f, HudAnchor.TOP_RIGHT); }

        @Override protected boolean autoAlign() { return autoAlign.on(); }
        @Override protected Alignment alignment() { return alignment.get(); }
        @Override protected boolean background() { return background.on(); }
        @Override protected ColorSetting backgroundColor() { return backgroundColor; }
        @Override protected boolean border() { return border.on(); }
        @Override protected float borderThickness() { return borderThickness.value(); }
        @Override protected ColorSetting borderColor() { return borderColor; }

        @Override public boolean visible(boolean preview) {
            if (!bedwars.isEnabled() || (preview ? !bedwars.inBedwars() : !counting)) { size(0, 0); return false; }
            if (!showTitle.on() && !showIron.on() && !showGold.on() && !showDiamonds.on() && !showEmeralds.on()) return false;
            return super.visible(preview);
        }

        private Text t(String s, ColorSetting c) { return text(s, c, textShadow.on()); }

        private Piece line(String name, ItemStack icon, int pad, int n, int chest) {
            if (showResourcesInEnderchest.on()) {
                if (useResourceIcons.on()) return row(pad, new Item(icon), new Space(4), t("" + n, numberColor), t(" + ", dividerColor), t("" + chest, numberColor));
                return row(pad, t(name + ": ", textColor), t("" + n, numberColor), t(" + ", dividerColor), t("" + chest, numberColor));
            }
            if (useResourceIcons.on()) return row(pad, new Item(icon), new Space(4), t("" + n, numberColor));
            return row(pad, t(name + ": ", textColor), t("" + n, numberColor));
        }

        @Override protected List<Piece> rows(boolean preview) {
            List<Piece> list = new ArrayList<Piece>();
            int pad = effective(autoAlign.on(), currentAnchor(), alignment.get()) == Alignment.LEFT ? 4 : 0;
            if (showTitle.on()) list.add(row(0, t("§lResources", titleColor)));
            if (showIron.on()) list.add(line("Iron", icon(0), pad, preview ? 37 : iron, preview ? 32 : chestIron));
            if (showGold.on()) list.add(line("Gold", icon(1), pad, preview ? 12 : gold, preview ? 1 : chestGold));
            if (showDiamonds.on()) list.add(line("Diamonds", icon(2), pad, preview ? 4 : diamonds, preview ? 0 : chestDiamonds));
            if (showEmeralds.on()) list.add(line("Emeralds", icon(3), pad, preview ? 0 : emeralds, preview ? 4 : chestEmeralds));
            return list;
        }
    }
}
