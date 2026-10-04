package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.ChatEvent;
import com.example.lunarforge.module.ChatSendEvent;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ButtonSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.TextSetting;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ModuleChat extends Module {
    public enum ChatColor implements ChoiceSetting.Option {
        OFF, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED,
        LIGHT_PURPLE, YELLOW, WHITE;

        final EnumChatFormatting formatting = name().equals("OFF") ? null : EnumChatFormatting.valueOf(name());

        @Override public String langId() {
            return org.apache.commons.lang3.text.WordUtils.capitalize(name().toLowerCase().replace("_", " "));
        }
    }

    public enum Profanity implements ChoiceSetting.Option {
        CUSTOM("custom"), OFF("off"), NORMAL("normal"), HIGH("high");
        private final String id;
        Profanity(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    static ModuleChat instance;

    private static final ResourceLocation PING = new ResourceLocation("random.orb");

    private static final String[] JOINS = {"spooked into the lobby!", "joined the lobby!", "has joined"};

    static int lastHovered = -1;

    private static String skipPing;

    final ChatStacker stacker = new ChatStacker();
    final ChatHeads heads = new ChatHeads();
    final ChatFilter filter = new ChatFilter();
    final ChatImagePreview preview = new ChatImagePreview();

    final BoolSetting unlimitedChat = bool("unlimitedChat", true);
    final BoolSetting stackMessages = bool("stackMessages", false);
    final BoolSetting chatHeight = bool("chatHeight", false);
    final BoolSetting stackMessagesTimeBased = bool("stackMessagesTimeBased", true);
    final NumberSetting timeBasedStackMessagesTimeframe = integer("timeBasedStackMessagesTimeframe", 10, 1, 60);
    final BoolSetting chatStackIgnoreBlank = bool("chatStackIgnoreBlank", false);
    final BoolSetting chatStackIgnoreBreak = bool("chatStackIgnoreBreak", false);
    final NumberSetting chatBackgroundOpacity = decimal("chatBackgroundOpacity", 1.0f, 0.0f, 1.0f);
    final BoolSetting chatShadow = bool("chatShadow", true);
    final BoolSetting disableChat = bool("disableChat", false);
    final BoolSetting noCloseMyChat = bool("noCloseMyChat", true);
    final ChoiceSetting<ChatColor> chatNameColor = choice("chatNameColor", ChatColor.OFF);
    final BoolSetting chatNameBold = bool("chatNameBold", false);
    final BoolSetting chatNameItalic = bool("chatNameItalic", false);
    final BoolSetting chatNameUnderline = bool("chatNameUnderline", false);
    final BoolSetting chatNameStrikethrough = bool("chatNameStrikethrough", false);
    final BoolSetting chatNameObfuscated = bool("chatNameObfuscated", false);
    final ChoiceSetting<Profanity> profanity = choice("profanity", Profanity.OFF);
    final BoolSetting stopProfaneMessages = bool("stopProfaneMessages", false);
    final NumberSetting inputFieldOpacity = decimal("inputFieldOpacity", 5.0f, 0.0f, 10.0f);
    final BoolSetting chatPingSound = bool("chatPingSound", true);
    final BoolSetting chatPingExactMatch = bool("chatPingExactMatch", false);
    final BoolSetting smoothChat = bool("smoothChat", true);
    final NumberSetting smoothChatSpeed = integer("smoothChatSpeed", 3, 1, 10);
    final BoolSetting chatTimestamps = bool("chatTimestamps", false);
    final ChoiceSetting<ChatColor> timestampColor = choice("timestampColor", ChatColor.GRAY);
    final BoolSetting showBrackets = bool("showBrackets", true);
    final ChoiceSetting<ChatColor> bracketsColor = choice("bracketsColor", ChatColor.GRAY);
    final BoolSetting twelveHourClock = bool("twelveHourClock", true);
    final BoolSetting showAmPm = bool("showAmPm", false);
    final BoolSetting showSeconds = bool("showSeconds", false);
    final BoolSetting timestampItalics = bool("timestampItalics", true);
    final BoolSetting timestampBold = bool("timestampBold", false);
    final BoolSetting copyChat = bool("copyChat", false);
    final BoolSetting copyChatRightClick = bool("copyChatRightClick", true);
    final KeySetting copyChatBind = add(new KeySetting("copyChatBind", "LCONTROL", true));
    final BoolSetting hoverImagePreview = bool("hoverImagePreview", false);
    final BoolSetting fullscreenImage = bool("fullscreenImage", true);
    final TextSetting customWhitelistedDomains = add(new TextSetting("customWhitelistedDomains"));
    final NumberSetting minImageSize = decimal("minImageSize", 0.0f, 0.0f, 100.0f);
    final NumberSetting maxImageSize = decimal("maxImageSize", 30.0f, 0.0f, 100.0f);
    final BoolSetting longChatSingleplayer = bool("longChatSingleplayer", false);
    final BoolSetting modernChatLengthHypixel = bool("modernChatLengthHypixel", false);
    final KeySetting chatVisibilityKeybind = keybind("chatVisibilityKeybind");
    final KeySetting chatPeekKeybind = keybind("chatPeekKeybind");
    final BoolSetting chatHeads = bool("chatHeads", false);
    private final ButtonSetting openFilter = add(new ButtonSetting("openFilter", () -> Sys.openURL(filterFile().toURI().toString())));
    private final ButtonSetting reloadFilter = add(new ButtonSetting("reloadFilter", () -> {
        filter.load(filterFile(), profanity.get());
        LunarNotifications.info("Reloaded custom filter!");
    }));

    private DateTimeFormatter timestamp;

    private boolean filterLoaded;

    private boolean chatHidden;

    public ModuleChat() {
        super("CHAT", true);
        instance = this;
        child(new ModuleChatCommandAliases(), "generalOptions");
        ChatSendEvent.sent = message -> skipPing = message;
        profanity.onChange(() -> filter.select(profanity.get()));
        twelveHourClock.onChange(this::timestampFormat);
        showAmPm.onChange(this::timestampFormat);
        showSeconds.onChange(this::timestampFormat);
        customWhitelistedDomains.onChange(() -> {
            preview.clearCache();
            preview.hosts(customWhitelistedDomains.get().split("[ ,]+"));
        });
        chatHeads.onChange(() -> {
            if (Minecraft.getMinecraft().ingameGUI != null) Minecraft.getMinecraft().ingameGUI.getChatGUI().refreshChat();
        });
        timestampFormat();
        preview.hosts(customWhitelistedDomains.get().split("[ ,]+"));
        ChatEvent.listen(this::onMessage);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(unlimitedChat);
            s.add(modernChatLengthHypixel, longChatSingleplayer);
            s.group(stackMessages, g -> g.add(stackMessagesTimeBased, timeBasedStackMessagesTimeframe, chatStackIgnoreBlank, chatStackIgnoreBreak));
            s.add(chatHeight);
            s.add(chatShadow, disableChat, noCloseMyChat);
            s.group(chatPingSound, g -> g.add(chatPingExactMatch));
            s.add(chatHeads);
            s.group(copyChat, g -> g.add(copyChatBind, copyChatRightClick));
            s.group(hoverImagePreview, g -> {
                g.add(customWhitelistedDomains, minImageSize, maxImageSize);
                g.add(fullscreenImage).hideIf(() -> maxImageSize.value() == 100.0f);
            });
            s.group(smoothChat, g -> g.add(smoothChatSpeed));
            s.add(chatPeekKeybind);
            s.add(chatVisibilityKeybind);
        });
        page.section("nameOptions", s -> s.add(chatNameColor, chatNameBold, chatNameItalic, chatNameUnderline, chatNameStrikethrough, chatNameObfuscated));
        page.section("opacityOptions", s -> s.add(chatBackgroundOpacity, inputFieldOpacity));
        page.section("filterOptions", s -> s.add(profanity, openFilter, reloadFilter, stopProfaneMessages));
        page.section("timestampOptions", s -> s.group(chatTimestamps, g -> {
            g.add(timestampColor, timestampItalics, timestampBold, twelveHourClock, showAmPm, showSeconds);
            g.group(showBrackets, b -> b.add(bracketsColor));
        }));
    }

    private File filterFile() {
        String profile = ModuleManager.model() == null ? "Default" : ModuleManager.model().store.get("activeProfile", "Default");
        return new File(new File(new File(Minecraft.getMinecraft().mcDataDir, "lunarforge"), profile), "profanity_filter.txt");
    }

    private void timestampFormat() {
        String p = twelveHourClock.on() ? "h" : "HH";
        p += ":mm";
        if (showSeconds.on()) p += ":ss";
        if (showAmPm.on()) p += " a";
        timestamp = DateTimeFormatter.ofPattern(p);
    }

    boolean smoothOn() { return isEnabled() && smoothChat.on(); }

    float smoothSpeed() { return smoothSpeed(smoothChatSpeed.intValue()); }

    static float smoothSpeed(int speed) { return (speed - 1.0f) / 9.0f * (0.01f - 0.001f) + 0.001f; }

    float inputOpacity() { return isEnabled() ? inputFieldOpacity.value() / 10.0f * 2.0f : 1.0f; }

    boolean hidden() { return isEnabled() && chatHidden && !peeking(); }

    boolean peeking() {
        return isEnabled() && chatPeekKeybind.code() != Keyboard.KEY_NONE && Minecraft.getMinecraft().currentScreen == null && chatPeekKeybind.isDown();
    }

    boolean headsOn() { return isEnabled() && chatHeads.on(); }

    int maxLength(boolean singleplayer, int n) {
        if (!isEnabled()) return n;
        if (longChatSingleplayer.on() && singleplayer) return 10000;
        if (modernChatLengthHypixel.on() && Server.hypixel()) return 256;
        return n;
    }

    private void onMessage(ChatEvent e) {
        if (!isEnabled() || e.cancelled || Minecraft.getMinecraft().thePlayer == null) return;
        if (Server.hypixel() && e.legacy.startsWith("{")) return;

        if (filter.apply(this, e)) return;
        if (disableChat.on()) {
            e.cancelled = true;
            return;
        }
        if (chatPingSound.on()) ping(e);
        if (chatTimestamps.on()) stamp(e);
        if (stackMessages.on()) stacker.onMessage(this, e);
        if (headsOn()) heads.register(e);
    }

    private void ping(ChatEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (ChatText.contains(e.component(), skipPing)) {
            skipPing = null;
            return;
        }
        String name = mc.thePlayer.getName(), nick = ModuleNickHider.hypixelNick();
        boolean mentioned = chatPingExactMatch.on() ? ChatText.containsWord(e.component(), name, nick) : ChatText.contains(e.component(), name, nick);
        if (!mentioned || ChatText.contains(e.component(), JOINS)) return;
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(PING, 1.0f));
    }

    private void stamp(ChatEvent e) {
        IChatComponent time = new ChatComponentText(LocalTime.now().format(timestamp));
        time.getChatStyle().setColor(timestampColor.get().formatting);
        IChatComponent head;
        if (showBrackets.on()) {
            head = new ChatComponentText("");
            EnumChatFormatting bracket = bracketsColor.get().formatting;
            head.appendSibling(colored("[", bracket)).appendSibling(time).appendSibling(colored("]", bracket));
        } else {
            head = time;
        }
        head.appendSibling(new ChatComponentText(" "));
        ChatStyle style = head.getChatStyle();
        if (timestampItalics.on()) style.setItalic(true);
        if (timestampBold.on()) style.setBold(true);
        IChatComponent out = new ChatComponentText("");
        out.appendSibling(head).appendSibling(e.component());
        e.set(out);
    }

    private static IChatComponent colored(String s, EnumChatFormatting color) {
        ChatComponentText t = new ChatComponentText(s);
        t.getChatStyle().setColor(color);
        return t;
    }

    private boolean copyHovered() {
        if (lastHovered == -1) return false;
        IChatComponent message = ChatHooks.messageById(lastHovered);
        if (message == null) return false;
        String text = ChatText.plain(message);
        GuiScreen.setClipboardString(text);
        StringBuilder shown = new StringBuilder();
        for (char c : text.toCharArray()) if (c < 'Ā') shown.append(c);
        LunarNotifications.push(LunarNotifications.Type.INFO, LunarLang.get("popups", "copiedMessage"), shown.toString());
        return true;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!isEnabled() || !copyChat.on() || !(event.gui instanceof GuiChat) || !Mouse.getEventButtonState()) return;
        int button = Mouse.getEventButton();
        boolean left = button == 0 && copyChatBind.isDown(), right = button == 1 && copyChatRightClick.on();
        if (!left && !right) return;

        if (left && Minecraft.getMinecraft().ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY()) == null) return;
        if (copyHovered()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!isEnabled() || !(event.gui instanceof GuiChat) || !Keyboard.getEventKeyState()) return;
        preview.keyPressed(Keyboard.getEventKey(), Keyboard.isRepeatEvent());
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isEnabled() || !(event.gui instanceof GuiChat)) return;
        preview.render(this, event.mouseX, event.mouseY, event.gui.width, event.gui.height);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!filterLoaded) {
            filterLoaded = true;
            filter.load(filterFile(), profanity.get());
        }
        if (chatVisibilityKeybind.pressed(isEnabled() && Minecraft.getMinecraft().currentScreen == null)) chatHidden = !chatHidden;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { stacker.clear(); }
}
