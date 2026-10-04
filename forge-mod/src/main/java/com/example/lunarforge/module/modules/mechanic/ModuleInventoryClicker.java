package com.example.lunarforge.module.modules.mechanic;

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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

/**
 * Ported from Leader-Lite (leader.module.modules.legit.InventoryClicker).
 * Lets you hold the left mouse button to keep clicking in container GUIs
 * (vanilla only clicks once per press).
 */
public final class ModuleInventoryClicker extends Module {
    private static final Method MOUSE_CLICKED = ReflectionHelper.findMethod(
            GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);

    private final NumberSetting triggerTicks = integer("triggerTicks", 2, 0, 20).label(() -> "Trigger Ticks");
    private final BoolSetting inInventory = bool("inInventory", true).label(() -> "In Inventory");
    private final BoolSetting inChest = bool("inChest", true).label(() -> "In Chests");
    private final BoolSetting inEnderChest = bool("inEnderChest", true).label(() -> "In Ender Chests");
    private final BoolSetting inOther = bool("inOtherContainers", true).label(() -> "In Other Containers");

    private int ticks;

    public ModuleInventoryClicker() {
        super("INVENTORY_CLICKER", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(triggerTicks, inInventory, inChest, inEnderChest, inOther));
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
            if (chest.inventorySlots instanceof ContainerChest
                    && isEnderChest(((ContainerChest) chest.inventorySlots).getLowerChestInventory())) {
                return inEnderChest.on();
            }
            return inChest.on();
        }
        return screen instanceof GuiContainer && inOther.on();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        if (!(mc.currentScreen instanceof GuiContainer) || !isAllowed(mc.currentScreen) || !Mouse.isButtonDown(0)) {
            ticks = 0;
            return;
        }

        GuiContainer screen = (GuiContainer) mc.currentScreen;
        int mouseX = Mouse.getX() * screen.width / mc.displayWidth;
        int mouseY = screen.height - Mouse.getY() * screen.height / mc.displayHeight - 1;
        ticks++;
        if (ticks > triggerTicks.intValue()) {
            try {
                MOUSE_CLICKED.invoke(screen, mouseX, mouseY, 0);
            } catch (InvocationTargetException | IllegalAccessException ignored) {
                ticks = 0;
            }
        }
    }
}
