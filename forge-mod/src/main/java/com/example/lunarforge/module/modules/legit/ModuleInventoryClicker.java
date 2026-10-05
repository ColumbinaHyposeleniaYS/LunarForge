package com.example.lunarforge.module.modules.legit;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.EnumChatFormatting;
import java.util.Locale;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.InventoryClicker).
 * Lets you hold the left mouse button to keep clicking in container GUIs
 * (vanilla only clicks once per press).
 *
 * Bedwars item/upgrade shops are server-created chests, not ordinary
 * storage containers, so they get their own scopes (both off by default);
 * shops are recognized by scoreboard, chest title and item lore, and the
 * click state resets between screens (upstream fix 744e62e).
 */
public final class ModuleInventoryClicker extends Module {
    /** GuiScreen.mouseClicked under both MCP and SRG names; resolved lazily per JVM. */
    private static volatile Method mouseClickedMethod;

    private static Method mouseClicked() {
        Method m = mouseClickedMethod;
        if (m != null) return m;
        synchronized (ModuleInventoryClicker.class) {
            if (mouseClickedMethod == null) {
                for (String name : new String[] {"mouseClicked", "func_73864_a"}) {
                    try {
                        Method found = GuiScreen.class.getDeclaredMethod(name, int.class, int.class, int.class);
                        found.setAccessible(true);
                        mouseClickedMethod = found;
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
            return mouseClickedMethod;
        }
    }

    private final NumberSetting triggerTicks = integer("triggerTicks", 2, 0, 20).label(() -> "Trigger Ticks");
    private final BoolSetting inInventory = bool("inInventory", true).label(() -> "In Inventory");
    private final BoolSetting inChest = bool("inChest", true).label(() -> "In Chests");
    private final BoolSetting inEnderChest = bool("inEnderChest", true).label(() -> "In Ender Chests");
    private final BoolSetting inOther = bool("inOtherContainers", true).label(() -> "In Other Containers");
    private final BoolSetting inBedwarsItemShop = bool("inBedwarsItemShop", false).label(() -> "In Bedwars Item Shop");
    private final BoolSetting inBedwarsUpgradeShop = bool("inBedwarsUpgradeShop", false).label(() -> "In Bedwars Upgrade Shop");

    private int ticks;
    private GuiScreen lastScreen;

    public ModuleInventoryClicker() {
        super("INVENTORY_CLICKER", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(triggerTicks, inInventory, inChest, inEnderChest, inOther,
                inBedwarsItemShop, inBedwarsUpgradeShop));
    }

    private boolean isEnderChest(IInventory inventory) {
        if (inventory instanceof InventoryEnderChest) return true;
        String name = inventory.getName();
        if (name == null) return false;
        String plain = name.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        return plain.equals("container.enderchest")
                || plain.equalsIgnoreCase(I18n.format("container.enderchest"))
                || plain.equalsIgnoreCase("Ender Chest");
    }

    private boolean isAllowed(GuiScreen screen) {
        if (screen instanceof GuiInventory || screen instanceof GuiContainerCreative) return inInventory.on();
        if (screen instanceof GuiChest) {
            GuiChest chest = (GuiChest) screen;
            int shop = bedwarsShopType(chest);
            if (shop == 1) return inBedwarsItemShop.on();
            if (shop == 2) return inBedwarsUpgradeShop.on();
            if (chest.inventorySlots instanceof ContainerChest
                    && isEnderChest(((ContainerChest) chest.inventorySlots).getLowerChestInventory())) {
                return inEnderChest.on();
            }
            return inChest.on();
        }
        return screen instanceof GuiContainer && inOther.on();
    }

    private boolean inBedwars() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) return false;
        String name = plain(objective.getDisplayName());
        return name.contains("bed wars") || name.contains("bedwars") || name.contains("起床战争") || name.contains("起床戰爭");
    }

    private String plain(String text) {
        return text == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(text)
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Bedwars menus are server-created chests, not ordinary storage containers. */
    private int bedwarsShopType(GuiChest screen) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!inBedwars() || !(screen.inventorySlots instanceof ContainerChest)) return 0;
        IInventory inventory = ((ContainerChest) screen.inventorySlots).getLowerChestInventory();
        String title = plain(inventory.getName());
        if (title.equals("upgrades & traps") || title.equals("upgrades and traps") || title.equals("team upgrades")
                || title.equals("upgrade shop") || title.equals("upgrades") || title.equals("traps")
                || title.equals("升级与陷阱") || title.equals("升级和陷阱") || title.equals("團隊升級")
                || title.equals("团队升级") || title.equals("升級與陷阱") || title.equals("升级商店")
                || title.equals("升級商店")) return 2;
        if (title.equals("quick buy") || title.equals("item shop") || title.equals("blocks") || title.equals("melee")
                || title.equals("armor") || title.equals("armour") || title.equals("tools") || title.equals("ranged")
                || title.equals("potions") || title.equals("utility") || title.equals("rotating items")
                || title.equals("favorites") || title.contains("快捷购买") || title.contains("快捷購買")
                || title.contains("物品商店")) return 1;
        // Fallback for translated/category titles: require actual shop lore, not just "shop" in a name.
        boolean purchase = false, navigation = false, upgrade = false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack == null || !stack.hasTagCompound()) continue;
            String itemName = plain(stack.getDisplayName());
            navigation |= itemName.equals("quick buy") || itemName.contains("快捷购买") || itemName.contains("快捷購買");
            upgrade |= itemName.contains("reinforced armor") || itemName.contains("sharpened swords")
                    || itemName.contains("miner fatigue trap") || itemName.contains("强化盔甲") || itemName.contains("锋利宝剑");
            NBTTagList lore = stack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
            for (int j = 0; j < lore.tagCount(); j++) {
                String line = plain(lore.getStringTagAt(j));
                purchase |= line.contains("click to purchase") || line.contains("click to buy") || line.contains("点击购买")
                        || line.contains("點擊購買") || line.contains("not enough diamonds") || line.contains("not enough iron")
                        || line.contains("not enough gold") || line.contains("not enough emeralds");
                navigation |= line.contains("add to quick buy") || line.contains("remove from quick buy");
            }
        }
        if (purchase && upgrade) return 2;
        return purchase && navigation ? 1 : 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != lastScreen) { ticks = 0; lastScreen = mc.currentScreen; }
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) { ticks = 0; return; }

        if (!(mc.currentScreen instanceof GuiContainer) || !isAllowed(mc.currentScreen) || !Mouse.isButtonDown(0)) {
            ticks = 0;
            return;
        }

        GuiContainer screen = (GuiContainer) mc.currentScreen;
        int mouseX = Mouse.getX() * screen.width / mc.displayWidth;
        int mouseY = screen.height - Mouse.getY() * screen.height / mc.displayHeight - 1;
        ticks++;
        if (ticks > triggerTicks.intValue()) {
            Method method = mouseClicked();
            if (method == null) {
                ticks = 0;
                return;
            }
            try {
                method.invoke(screen, mouseX, mouseY, 0);
            } catch (InvocationTargetException | IllegalAccessException ignored) {
                ticks = 0;
            }
        }
    }

    @Override protected void onDisable() {
        ticks = 0;
        lastScreen = null;
    }
}
