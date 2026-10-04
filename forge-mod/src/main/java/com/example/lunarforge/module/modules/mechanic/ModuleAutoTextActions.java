package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.util.Fields;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleAutoTextActions extends Module {
    private static final String ACTIONS = "actions";

    private static final Pattern AMPERSAND = Pattern.compile("(?i)&([0-9A-FK-OR])");

    private final List<Action> actions = new ArrayList<Action>();

    private final Set<String> titles = new LinkedHashSet<String>();
    private int titleTicks = -1;
    private long actionsRevision = Long.MIN_VALUE;

    private static final Field TITLE_TIMER = Fields.find(GuiIngame.class, "field_175195_w", "titlesTimer");
    private static final Field TITLE = Fields.find(GuiIngame.class, "field_175201_x", "displayedTitle");
    private static final Field SUBTITLE = Fields.find(GuiIngame.class, "field_175200_y", "displayedSubTitle");
    private static final Field FADE_IN = Fields.find(GuiIngame.class, "field_175199_z", "titleFadeIn");
    private static final Field DISPLAY_TIME = Fields.find(GuiIngame.class, "field_175192_A", "titleDisplayTime");
    private static final Field FADE_OUT = Fields.find(GuiIngame.class, "field_175193_B", "titleFadeOut");

    public ModuleAutoTextActions() {
        super("AUTO_TEXT_ACTIONS", false);
    }

    @Override protected void layout(Page page) {
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!isEnabled() || event.isCanceled()) return;

        if (event.type == 2) return;
        String text = event.message.getUnformattedText();
        for (Action action : currentActions()) {
            if (!action.matches(text)) continue;
            if (action.hideMessage) event.setCanceled(true);
            if (action.playSound) playSound();
            if (action.desktopNotification && Desktop.supported()) {
                Desktop.notify(lang("notificationTitle"), text);
            }

            if (action.ingameNotification && Desktop.supported()) {
                LunarNotifications.push(LunarNotifications.Type.INFO, lang("notificationTitle"), text);
            }
            if (!action.showTitleAction) continue;
            String title = action.titleText;
            if (title == null) continue;
            title = title.trim();
            if (title.isEmpty()) continue;
            titles.add(AMPERSAND.matcher(title).replaceAll("\u00a7$1"));
        }
    }

    private static void playSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("random.orb"), 1.0F));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled() || titles.isEmpty()) return;
        if (Minecraft.getMinecraft().thePlayer == null) titles.clear();
        else if (titleTicks >= 0) titleTicks--;
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !isEnabled()) return;
        if (titles.isEmpty()) {
            titleTicks = -1;
            return;
        }
        String title = titles.iterator().next();
        if (titleTicks == 0) {
            titles.remove(title);
            titleTicks = -1;
            if (titles.isEmpty()) return;
        }
        if (titleTicks == -1) {
            titleTicks = 60;
        } else if (titleTicks > 0) {
            float delta = (float)titleTicks - event.partialTicks;
            int alpha = 255;
            if (titleTicks > 50) alpha = (int)((60.0f - delta) * 255.0f / 10.0f);
            if (titleTicks <= 10) alpha = (int)(delta * 255.0f / 10.0f);
            alpha = Math.min(255, Math.max(alpha, 0));
            if (alpha > 8) {
                clearTitle();
                ScaledResolution resolution = event.resolution;
                GlStateManager.pushMatrix();
                GlStateManager.translate((float)(resolution.getScaledWidth() / 2), (float)resolution.getScaledHeight() / 2.0f, 0.0f);
                GlStateManager.scale(4.0f, 4.0f, 4.0f);
                Draw.text(title, -Draw.width(title) / 2.0f, -10.0f, 0xFFFFFF | alpha << 24 & 0xFF000000, true);
                GlStateManager.popMatrix();
            }
        }
    }

    private static void clearTitle() {
        GuiIngame gui = Minecraft.getMinecraft().ingameGUI;
        Fields.setInt(TITLE_TIMER, gui, 0);
        Fields.set(TITLE, gui, null);
        Fields.set(SUBTITLE, gui, null);
        Fields.setInt(FADE_IN, gui, 10);
        Fields.setInt(DISPLAY_TIME, gui, 70);
        Fields.setInt(FADE_OUT, gui, 20);
    }

    private List<Action> currentActions() {
        UiModel model = ModuleManager.model();
        if (model != null && model.revision != actionsRevision) {
            actionsRevision = model.revision;
            loadActions();
        }
        return actions;
    }

    private void loadActions() {
        actions.clear();
        String raw = ModuleManager.store(key(), ACTIONS, null);
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JsonArray array = new JsonParser().parse(raw).getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) actions.add(Action.from(element.getAsJsonObject()));
            }
        } catch (RuntimeException e) {
        }
    }

    private void saveActions() {
        JsonArray array = new JsonArray();
        for (Action action : actions) array.add(action.toJson());
        ModuleManager.put(key(), ACTIONS, array.toString());
        UiModel model = ModuleManager.model();
        if (model != null) actionsRevision = model.revision;
    }

    Action addAction() {
        Action action = new Action();
        actions.add(action);
        saveActions();
        return action;
    }

    void removeAction(Action action) {
        actions.remove(action);
        saveActions();
    }

    Action cloneAction(Action action) {
        Action copy = Action.from(action.toJson());
        action.active = false;
        copy.active = true;
        actions.add(copy);
        saveActions();
        return copy;
    }

    static final class Action {
        boolean active = true;
        String triggerKey = "";
        boolean regex;
        boolean contains;
        boolean caseSensitive;
        boolean hideMessage;
        boolean showTitleAction;
        String titleText = "";
        boolean desktopNotification;
        boolean ingameNotification;
        boolean playSound;
        private Pattern pattern;
        private String patternSource;

        boolean matches(String text) {
            if (!active) return false;
            String trigger = triggerKey;
            if (trigger == null || trigger.trim().isEmpty()) return false;
            if (regex) {
                validate();
                if (pattern == null) return false;
                return pattern.matcher(text).find();
            }
            String message = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
            String match = caseSensitive ? trigger : trigger.toLowerCase(Locale.ROOT);
            return contains ? message.contains(match) : message.equals(match);
        }

        private void validate() {
            if (!regex) {
                pattern = null;
                return;
            }
            if (!Objects.equals(patternSource, triggerKey)) {
                patternSource = triggerKey;
                try {
                    pattern = Pattern.compile(triggerKey);
                } catch (Exception e) {
                    pattern = null;
                }
            }
        }

        static Action from(JsonObject object) {
            Action action = new Action();
            action.active = bool(object, "active", action.active);
            action.triggerKey = string(object, "triggerKey", action.triggerKey);
            action.regex = bool(object, "regex", action.regex);
            action.contains = bool(object, "contains", action.contains);
            action.caseSensitive = bool(object, "caseSensitive", action.caseSensitive);
            action.hideMessage = bool(object, "hideMessage", action.hideMessage);
            action.showTitleAction = bool(object, "showTitleAction", action.showTitleAction);
            action.titleText = string(object, "titleText", action.titleText);
            action.desktopNotification = bool(object, "desktopNotification", action.desktopNotification);
            action.ingameNotification = bool(object, "ingameNotification", action.ingameNotification);
            action.playSound = bool(object, "playSound", action.playSound);
            return action;
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("active", active);
            object.addProperty("triggerKey", triggerKey);
            object.addProperty("regex", regex);
            object.addProperty("contains", contains);
            object.addProperty("caseSensitive", caseSensitive);
            object.addProperty("hideMessage", hideMessage);
            object.addProperty("showTitleAction", showTitleAction);
            object.addProperty("titleText", titleText);
            object.addProperty("desktopNotification", desktopNotification);
            object.addProperty("ingameNotification", ingameNotification);
            object.addProperty("playSound", playSound);
            return object;
        }

        private static boolean bool(JsonObject object, String key, boolean fallback) {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        }

        private static String string(JsonObject object, String key, String fallback) {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
        }
    }

    private static final class Desktop {
        private static final int NONE = 0, NOTIFY = 1, SYSTEM_TRAY = 2, APPLE_SCRIPT = 3;
        private static final int TYPE = detect();
        private static final Image ICON = icon();

        static boolean supported() {
            return TYPE != NONE;
        }

        static void notify(String title, String text) {
            if (TYPE == NOTIFY) {
                ProcessBuilder builder = new ProcessBuilder("notify-send", "--app-name=Lunar Client",
                        "--urgency=low", "--", title, text);
                try {
                    builder.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (TYPE == SYSTEM_TRAY) {
                try {
                    SystemTray tray = SystemTray.getSystemTray();
                    TrayIcon icon = new TrayIcon(ICON, "Lunar Client");
                    icon.setImageAutoSize(true);
                    tray.add(icon);
                    icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
                } catch (AWTException e) {
                    throw new RuntimeException(e);
                }
            } else if (TYPE == APPLE_SCRIPT) {
                ProcessBuilder builder = new ProcessBuilder("osascript", "-e",
                        "display notification (system attribute \"LUNAR_NOTIFICATION_MESSAGE\")"
                                + " with title (system attribute \"LUNAR_NOTIFICATION_TITLE\")");
                builder.environment().put("LUNAR_NOTIFICATION_TITLE", title);
                builder.environment().put("LUNAR_NOTIFICATION_MESSAGE", text);
                try {
                    builder.start();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private static int detect() {
            if (isLinux() && runs("notify-send", "--help")) return NOTIFY;
            if (SystemTray.isSupported() && icon() != null) return SYSTEM_TRAY;
            if (isMacos() && runs("osascript", "-?")) return APPLE_SCRIPT;
            return NONE;
        }

        private static boolean runs(String... command) {
            try {
                return new ProcessBuilder(command).start().waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }

        private static boolean isLinux() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
        }

        private static boolean isMacos() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        }

        private static Image icon() {
            try (InputStream in = ModuleAutoTextActions.class.getResourceAsStream("/assets/lunarforge/splash/logo-128x117.png")) {
                return in == null ? null : ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
