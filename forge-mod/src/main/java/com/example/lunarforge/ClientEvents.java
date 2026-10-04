package com.example.lunarforge;

import com.example.lunarforge.feature.FeatureManager;
import com.example.lunarforge.gui.LunarMovementScreen;
import com.example.lunarforge.splash.LunarSplash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class ClientEvents {
    private static final String KEY_CATEGORY = "key.categories.lunarforge";

    private final FeatureManager features;
    private final KeyBinding openMenu = new KeyBinding(
        "key.lunarforge.open_menu",
        Keyboard.KEY_RSHIFT,
        KEY_CATEGORY
    );

    private final KeyBinding emoteWheel = new KeyBinding("key.lunarforge.emote_wheel", Keyboard.KEY_B, KEY_CATEGORY);

    public ClientEvents(FeatureManager features) {
        this.features = features;
    }

    public void registerKeyBindings() {
        try {
            int saved = Integer.parseInt(features.get("menuKey", "" + Keyboard.KEY_RSHIFT));
            if (saved > Keyboard.KEY_NONE && saved < Keyboard.KEYBOARD_SIZE) openMenu.setKeyCode(saved);
        } catch (NumberFormatException ignored) {  }
        ClientRegistry.registerKeyBinding(openMenu);
        ClientRegistry.registerKeyBinding(emoteWheel);
        KeyBinding.resetKeyBindingArrayAndHash();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        LunarSplash.finish();
        if (event.gui instanceof net.minecraft.client.gui.GuiMainMenu)
            event.gui = new com.example.lunarforge.gui.home.LunarHomeScreen(features, openMenu);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openMenu.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null && mc.currentScreen == null) mc.displayGuiScreen(new LunarMovementScreen(features, openMenu));
        }
        if (emoteWheel.isPressed()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null && mc.currentScreen == null) mc.displayGuiScreen(new com.example.lunarforge.gui.locker.EmoteWheelScreen(emoteWheel));
        }
    }

    @SubscribeEvent
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) com.example.lunarforge.gui.LunarNotifications.render();
    }

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text event) {
        if (Minecraft.getMinecraft().currentScreen instanceof LunarMovementScreen) return;
        if (features.isHudVisible()) {
            features.renderHud(event.resolution);
        }
    }
}
