package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ModuleToggleSneak extends Module {
    final BoolSetting toggleSprint = bool("toggleSprint", true);
    private final BoolSetting alwaysSprint = bool("alwaysSprint", false);
    private final BoolSetting sprintKeybindOverride = bool("sprintKeybindOverride", false);
    private final KeySetting keybindSprint = keybind("keybindSprint");
    private final BoolSetting toggleSneak = bool("toggleSneak", false);
    private final BoolSetting sneakKeybindOverride = bool("sneakKeybindOverride", false);
    private final KeySetting keybindSneak = keybind("keybindSneak");
    private final BoolSetting toggleSneakContainer = bool("toggleSneakContainer", false);
    final BoolSetting flyBoost = bool("flyBoost", true);
    final NumberSetting flyBoostAmount = integer("flyBoostAmount", 4, 2, 8);
    private final BoolSetting restrictFlyBoost = bool("restrictFlyBoost", true).noWidget();
    private final BoolSetting doubleTap = bool("doubleTap", true);

    boolean sprinting;

    boolean sneaking;
    private boolean flying;

    private long sneakDown, sprintDown;

    boolean sprintHeld;

    private Float flySpeed;

    private static final Field SPRINT_TOGGLE_TIMER = Fields.find(EntityPlayerSP.class, "sprintToggleTimer", "field_71156_d");

    public ModuleToggleSneak() {
        super("TOGGLE_SNEAK", true);
        child(new ModuleToggleSneakHud(this), "generalOptions");
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.group(toggleSprint, g -> {
                g.add(alwaysSprint);
                g.group(sprintKeybindOverride, k -> k.add(keybindSprint));
            });
            s.group(toggleSneak, g -> {
                g.group(sneakKeybindOverride, k -> k.add(keybindSneak));
                g.add(toggleSneakContainer);
            });
            s.add(doubleTap);
        });
        page.section("flyBoostOptions", s -> s.add(flyBoost, flyBoostAmount));
        page.add(restrictFlyBoost);
    }

    @Override protected void onEnable() { sprinting = toggleSprint.on(); }

    private static GameSettings gameSettings() { return Minecraft.getMinecraft().gameSettings; }

    private boolean alwaysSprinting() { return toggleSprint.on() && alwaysSprint.on(); }

    private boolean containerSneak() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean hypixel = mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP.toLowerCase().contains("hypixel.net");
        return toggleSneakContainer.on() && !hypixel;
    }

    private int sprintKey() { return sprintKeybindOverride.on() ? keybindSprint.code() : gameSettings().keyBindSprint.getKeyCode(); }

    private int sneakKey() { return sneakKeybindOverride.on() ? keybindSneak.code() : gameSettings().keyBindSneak.getKeyCode(); }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!isEnabled()) return;
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) return;
        onKey(Keyboard.getEventKeyState(), key);
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        if (!isEnabled() || Mouse.getEventButton() < 0) return;
        onKey(Mouse.getEventButtonState(), Mouse.getEventButton() - 100);
    }

    private boolean onKey(boolean down, int key) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return false;
        if (mc.currentScreen != null) {
            sneakDown = 0; sprintDown = 0; sprintHeld = false;
            return false;
        }
        if (down) {
            if (key == sneakKey() && toggleSneak.on()) sneakDown = System.currentTimeMillis();
            if (key == sprintKey() && toggleSprint.on()) { sprintDown = System.currentTimeMillis(); sprintHeld = true; }
            return false;
        }
        if (key == sprintKey() && toggleSprint.on()) sprintHeld = false;
        boolean isFlying = mc.thePlayer.capabilities.isFlying;
        if (!isFlying && key == sneakKey() && toggleSneak.on() && System.currentTimeMillis() - sneakDown <= 200) {
            sneaking = !sneaking;
            Fields.setPressed(gameSettings().keyBindSneak, sneaking);
            return true;
        }
        if (!isFlying && key == sprintKey() && toggleSprint.on() && System.currentTimeMillis() - sprintDown <= 400) {
            if (!alwaysSprinting()) sprinting = !sprinting;
            Fields.setPressed(gameSettings().keyBindSprint, sprinting);
            return true;
        }

        restoreKeys();
        return false;
    }

    private void restoreKeys() {
        if (toggleSprint.on()) Fields.setPressed(gameSettings().keyBindSprint, sprinting || physicallyDown(gameSettings().keyBindSprint));
        if (toggleSneak.on() && sneaking != gameSettings().keyBindSneak.isKeyDown()) Fields.setPressed(gameSettings().keyBindSneak, sneaking);
    }

    @SubscribeEvent
    public void onScreen(GuiOpenEvent event) {
        if (!isEnabled()) return;
        if (toggleSprint.on()) Fields.setPressed(gameSettings().keyBindSprint, sprinting);
        if (toggleSneak.on() && sneaking != gameSettings().keyBindSneak.isKeyDown()) Fields.setPressed(gameSettings().keyBindSneak, sneaking);
        if (event.gui != null && !(event.gui instanceof GuiChat) && gameSettings().keyBindSneak.isKeyDown() && !containerSneak())
            Fields.setPressed(gameSettings().keyBindSneak, false);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.START || event.player != mc.thePlayer || !isEnabled()) return;
        EntityPlayerSP player = mc.thePlayer;
        PlayerCapabilities caps = player.capabilities;
        if (alwaysSprinting()) {
            sprinting = true;
            Fields.setPressed(gameSettings().keyBindSprint, true);
        }
        if (caps.isFlying) flying = true;
        else if (flying) {
            if (sprinting) Fields.setPressed(gameSettings().keyBindSprint, sprinting);
            flying = false;
        }
        if (!doubleTap.on()) Fields.setInt(SPRINT_TOGGLE_TIMER, player, 0);
        boolean allowed = !restrictFlyBoost.on() || caps.isCreativeMode || mc.playerController.isSpectator();
        boolean sprintKeyHeld = physicallyDown(gameSettings().keyBindSprint);
        boolean isFlying = caps.isFlying;
        int amount = flyBoostAmount.intValue();
        if (flyBoost.on() && allowed && isFlying && sprintKeyHeld) {
            if (flySpeed == null) flySpeed = caps.getFlySpeed();
            caps.setFlySpeed(0.05f * amount);
            if (player.movementInput.sneak) player.motionY -= 0.15 * amount;
            if (player.movementInput.jump) player.motionY += 0.15 * amount;
        }
        if (flySpeed != null && !(sprintKeyHeld && isFlying)) {
            caps.setFlySpeed(flySpeed);
            flySpeed = null;
        }
    }

    static boolean physicallyDown(KeyBinding key) {
        int code = key.getKeyCode();
        if (code == Keyboard.KEY_NONE) return false;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }
}
