package com.example.lunarforge.util;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.InputStream;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

public final class DesktopNotify {
    private enum Backend { NONE, NOTIFY, SYSTEM_TRAY, APPLE_SCRIPT }

    private static Backend backend;
    private static Image icon;
    private static TrayIcon tray;

    private DesktopNotify() {}

    private static synchronized Backend backend() {
        if (backend != null) return backend;
        backend = Backend.NONE;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux") && run("notify-send", "--help")) backend = Backend.NOTIFY;
        else if (logo() != null && trayWorks()) backend = Backend.SYSTEM_TRAY;
        else if (os.contains("mac") && appleScript()) backend = Backend.APPLE_SCRIPT;
        return backend;
    }

    private static boolean trayWorks() {
        try { return SystemTray.isSupported(); } catch (Throwable t) { return false; }
    }

    private static boolean run(String... cmd) {
        try { return Runtime.getRuntime().exec(cmd).waitFor() == 0; } catch (Exception e) { return false; }
    }

    private static boolean appleScript() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"osascript", "-?"});
            return p.waitFor() != 2 && p.exitValue() != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Image logo() {
        if (icon != null) return icon;
        try (InputStream in = Minecraft.getMinecraft().getResourceManager()
                .getResource(new ResourceLocation("lunarforge", "textures/logo/logo-64x64.png")).getInputStream()) {
            icon = ImageIO.read(in);
        } catch (Exception e) {
            icon = null;
        }
        return icon;
    }

    public static boolean supported() { return backend() != Backend.NONE; }

    public static void send(String title, String message, TrayIcon.MessageType type) {
        try {
            switch (backend()) {
                case NOTIFY: {
                    String urgency = type == TrayIcon.MessageType.ERROR ? "critical" : type == TrayIcon.MessageType.WARNING ? "normal" : "low";
                    new ProcessBuilder("notify-send", "--app-name=Lunar Client", "--urgency=" + urgency, "--", title, message).start();
                    break;
                }
                case SYSTEM_TRAY: {
                    synchronized (DesktopNotify.class) {
                        if (tray == null) {
                            TrayIcon t = new TrayIcon(logo(), "Lunar Client");
                            t.setImageAutoSize(true);
                            SystemTray.getSystemTray().add(t);
                            tray = t;
                        }
                        tray.displayMessage(title, message, type);
                    }
                    break;
                }
                case APPLE_SCRIPT: {
                    ProcessBuilder pb = new ProcessBuilder("osascript", "-e",
                        "display notification (system attribute \"LUNAR_NOTIFICATION_MESSAGE\") with title (system attribute \"LUNAR_NOTIFICATION_TITLE\")");
                    pb.environment().put("LUNAR_NOTIFICATION_TITLE", title);
                    pb.environment().put("LUNAR_NOTIFICATION_MESSAGE", message);
                    pb.start();
                    break;
                }
                default:
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
