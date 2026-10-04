package com.example.lunarforge.gui.home;

import com.example.lunarforge.LunarForgeMod;
import com.example.lunarforge.feature.FeatureManager;
import com.example.lunarforge.gui.LunarMovementScreen;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.*;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

public final class LunarHomeScreen extends GuiScreen {
    private static final int SPACE_1 = 0xFF13141A, SPACE_2 = 0xFF1A1B21, SPACE_11 = 0xFFB1B3C0, SPACE_12 = 0xFFEDEEF3;

    private static final int LEGACY_TEXT = 0xFFBEC3BD;
    private static final int FOOTER = 0x5CE3E7FD;
    private static final int TOOLTIP_BG = 0xFF0C0D12, TOOLTIP_SUB = 0xFF797B87, SHADOW = 0x420A0A0A;
    private static final int GHOST = 0x14CACBF5;
    private static final ResourceLocation[] LOGO = new ResourceLocation[9];
    static { for (int i = 0; i < 9; i++) LOGO[i] = home("home/logo-layer-" + i + ".png"); }
    private static final ResourceLocation ICON_SINGLE = home("home/singleplayer.png"), ICON_MULTI = home("home/multiplayer.png"),
        ICON_STATUS = home("home/account-status.png"), ICON_CHEVRON = home("home/account-chevron.png"),
        ICON_BRUSH = home("home/brush.png"), ICON_CLOSE = home("home/close.png"),
        NAV_LOGO = home("home/nav-logo.png"), NAV_COG = home("home/nav-cog.png"), NAV_MODS = home("home/nav-modmenu.png"),
        TYPE_LUNAR = home("home/tooltip-lunar-type-icon.png"), TYPE_VANILLA = home("home/tooltip-vanilla-type-icon.png"),
        TYPE_EXTERNAL = home("home/tooltip-external-type-icon.png");

    private static int panoramaTimer;

    private static ResourceLocation home(String path) { return new ResourceLocation(LunarForgeMod.MOD_ID, "ui/" + path); }

    private final FeatureManager features;
    private final KeyBinding menuKey;
    private final HomeGfx gfx = new HomeGfx();
    private final HomeThemes themes;
    private final ThemePicker picker;
    private final Map<String, Fade> fades = new HashMap<String, Fade>();
    private final Star[] stars = new Star[8];
    private final long opened = System.currentTimeMillis();
    private DynamicTexture viewportTexture;
    private ResourceLocation backgroundTexture;
    private ResourceLocation face;
    private float cssW, cssH, mouseCssX = -1, mouseCssY = -1;
    private final List<Hit> hits = new ArrayList<Hit>();

    private boolean accountOpen;
    private long accountClosedAt;
    private final Fade accountFade = new Fade(150);
    private float popX, popY, popW, popH, accountW;

    private static volatile BufferedImage bust;
    private static String bustFor;

    private static final class Hit {
        final float x, y, w, h; final Runnable action;
        Hit(float x, float y, float w, float h, Runnable action) { this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; }
    }

    private static final class Star {
        final float duration = (float)(Math.random() * 6 + 3), delay = (float)(Math.random() / 2);
        float opacity(float seconds) {
            float t = seconds - delay;
            if (t <= 0) return 1;
            float cycle = (t % duration) / duration;
            float segment = cycle < 0.5f ? cycle * 2 : (cycle - 0.5f) * 2;
            float eased = cubicBezier(0.42f, 0, 0.58f, 1, segment);
            return cycle < 0.5f ? 1 + (0.15f - 1) * eased : 0.15f + (1 - 0.15f) * eased;
        }
    }

    private static final class Fade {
        boolean on; float from; long start; final float ms;
        Fade() { this(200); }
        Fade(float ms) { this.ms = ms; }
        float value(boolean target) {
            long now = System.currentTimeMillis();
            float current = sample(now);
            if (target != on) { from = current; on = target; start = now; current = sample(now); }
            return current;
        }
        private float sample(long now) {
            float t = Math.min(1, (now - start) / ms);
            float eased = cubicBezier(0.25f, 0.1f, 0.25f, 1, t);
            return from + ((on ? 1 : 0) - from) * eased;
        }
    }

    public LunarHomeScreen(FeatureManager features, KeyBinding menuKey) {
        this.features = features; this.menuKey = menuKey;
        this.themes = new HomeThemes(features);
        this.picker = new ThemePicker(gfx, themes);
        for (int i = 0; i < stars.length; i++) stars[i] = new Star();
    }

    @Override public void initGui() {
        if (viewportTexture == null) {
            viewportTexture = new DynamicTexture(256, 256);
            backgroundTexture = mc.getTextureManager().getDynamicTextureLocation("background", viewportTexture);
        }
        if (face == null) loadFace();
        loadBust();
    }

    private void loadBust() {
        UUID id = mc.getSession().getProfile().getId();
        if (id == null || id.toString().equals(bustFor)) return;
        final String uuid = bustFor = id.toString();
        bust = null;
        Thread thread = new Thread(new Runnable() { public void run() {
            try {
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection)new java.net.URL(
                    "https://skins.mcstats.com/bust/" + uuid + "?disableCosmeticType=all").openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "LunarForge/" + LunarForgeMod.VERSION);
                try (InputStream in = connection.getInputStream()) {
                    BufferedImage image = ImageIO.read(in);
                    if (image != null && uuid.equals(bustFor)) bust = image;
                }
            } catch (Exception ignored) {  }
        } }, "LunarForge bust");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadFace() {
        GameProfile profile = mc.getSession().getProfile();
        face = DefaultPlayerSkin.getDefaultSkin(profile.getId() == null ? UUID.randomUUID() : profile.getId());
        if (profile.getId() == null) return;
        mc.getSkinManager().loadProfileTextures(profile, new SkinManager.SkinAvailableCallback() {
            @Override public void skinAvailable(MinecraftProfileTexture.Type type, ResourceLocation location, MinecraftProfileTexture texture) {
                if (type == MinecraftProfileTexture.Type.SKIN) face = location;
            }
        }, true);
    }

    @Override public void updateScreen() { ++panoramaTimer; }

    public static void tickPanorama() { ++panoramaTimer; }

    public static void drawBehind(GuiScreen background, GuiScreen screen, float partialTicks) {
        if (background instanceof LunarHomeScreen) {
            LunarHomeScreen home = (LunarHomeScreen)background;
            home.width = screen.width; home.height = screen.height;
            if (home.viewportTexture != null) {
                HomeGfx.resyncTextureState();
                GlStateManager.disableAlpha();
                home.renderSkybox(partialTicks);
                GlStateManager.enableAlpha();
                return;
            }
        }
        screen.drawBackground(0);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        HomeGfx.resyncTextureState();
        GlStateManager.disableAlpha();
        renderSkybox(partialTicks);
        GlStateManager.enableAlpha();

        int scale = new ScaledResolution(mc).getScaleFactor();
        gfx.dsf = Math.max(1f, scale / 2f);
        cssW = mc.displayWidth / gfx.dsf; cssH = mc.displayHeight / gfx.dsf;
        mouseCssX = Mouse.getX() / gfx.dsf; mouseCssY = (mc.displayHeight - Mouse.getY() - 1) / gfx.dsf;
        hits.clear();
        layoutAccountPopover();

        GlStateManager.pushMatrix();
        float k = gfx.dsf / scale;
        GlStateManager.scale(k, k, 1);
        GlStateManager.disableDepth();

        GlStateManager.disableAlpha();
        drawAccount();
        drawTopButton(cssW - 115, ICON_BRUSH, "brush", new Runnable() { public void run() { picker.show(); } });
        drawTopButton(cssW - 63, ICON_CLOSE, "close", new Runnable() { public void run() { mc.shutdown(); } });
        drawCentre();
        drawFooter(3);
        drawNavigation();
        drawAccountPopover();
        picker.draw(cssW, cssH, mouseCssX, mouseCssY);
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
        devCapture();
    }

    private void devCapture() {
        java.io.File flag = new java.io.File(mc.mcDataDir, "lunarforge-homeshot.txt");
        if (!flag.exists()) return;
        long age = System.currentTimeMillis() - opened;
        if (captureStage == -1) {
            try { org.lwjgl.opengl.Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(1280, 720)); } catch (Exception ignored) { }
            mc.resize(1280, 720); captureStage = 0;
        } else if (age > 5000 && captureStage == 0) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "lunarforge-home.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            accountOpen = true; captureStage = 1;
        } else if (age > 6000 && captureStage == 1) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "lunarforge-account.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            closeAccountPopover(); picker.show(); captureStage = 3;
        } else if (age > 8500 && captureStage == 3) {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, "lunarforge-picker.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            captureStage = 4; flag.delete(); mc.shutdown();
        }
    }
    private int captureStage = -1;

    private boolean popoverMounted() { return accountOpen || System.currentTimeMillis() - accountClosedAt < 200; }
    private boolean inPopover(float mx, float my) { return popoverMounted() && mx >= popX && my >= popY && mx < popX + popW && my < popY + popH; }
    private boolean over(float x, float y, float w, float h) { return !picker.blocksHome() && !inPopover(mouseCssX, mouseCssY) && overRaw(x, y, w, h); }
    private boolean overRaw(float x, float y, float w, float h) { return mouseCssX >= x && mouseCssY >= y && mouseCssX < x + w && mouseCssY < y + h; }
    private float fade(String key, boolean on) { Fade f = fades.get(key); if (f == null) { f = new Fade(); fades.put(key, f); } return f.value(on); }

    private void drawAccount() {
        String name = mc.getSession().getUsername();
        boolean legacy = picker.legacyButtons();
        float nameW = legacy ? gfx.legacyTextWidth(name, 14) : gfx.textWidth(name, false, 16);
        float w = accountW = Math.max(140, nameW + 92), x = 20, y = 20;
        boolean hover = over(x, y, w, 40);
        float t = fade("account", hover);
        if (legacy) legacyFrame(x, y, w, 40, 8, t);
        else gfx.draw(gfx.box(w, 40, 8), x, y, lerp(SPACE_1, SPACE_2, t));
        int fg = legacy ? lerp(LEGACY_TEXT, 0xFFFFFFFF, t) : lerp(SPACE_11, SPACE_12, t);

        GlStateManager.color(1, 1, 1, 1);
        gfx.roundedRegion(face, 32, 32, 16, 16, 5, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f);
        gfx.roundedRegion(face, 32, 32, 16, 16, 5, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f);
        gfx.draw(ICON_STATUS, 52, 32, 16, 16, fg);
        if (legacy) {
            HomeGfx.Tex text = gfx.legacyText(name, 14);
            float ty = y + (40 - text.height) / 2;
            gfx.draw(text, 73, ty + 1, 0x20000000);
            gfx.draw(text, 72, ty, fg);
        } else gfx.draw(gfx.text(name, false, 16), 72, 29.5f, fg);
        gfx.draw(ICON_CHEVRON, 80 + nameW, 32, 16, 16, fg);
    }

    private void layoutAccountPopover() {
        float nameW = gfx.textWidth(mc.getSession().getUsername(), false, 18);

        popW = Math.max(200, 89 + 16 + 18 + 4 + nameW + 32); popH = 89 + 32; popX = 20;
        popY = 68 - 10 * (1 - accountFade.value(accountOpen));
    }

    private void drawAccountPopover() {
        if (!popoverMounted()) return;
        float t = accountFade.value(accountOpen), x = popX, y = popY, w = popW, h = popH;
        float pad = HomeGfx.shadowPad(24);
        gfx.draw(gfx.shadow(w, h, 8, 8, 24, 0), x - pad, y - pad, withAlpha(0x4D0A0A0A, t));
        gfx.draw(gfx.shadow(w, h, 8, 2, 8, 0), x - HomeGfx.shadowPad(8), y - HomeGfx.shadowPad(8), withAlpha(0x260A0A0A, t));
        gfx.draw(gfx.box(w, h, 8), x, y, withAlpha(SPACE_1, t));
        float cx = x + 16, cy = y + 16;

        BufferedImage image = bust;
        if (image != null) gfx.draw(gfx.circleImage("bust:" + bustFor, image, 89, GHOST), cx, cy, withAlpha(0xFFFFFFFF, t));
        else gfx.draw(gfx.box(89, 89, 44.5f), cx, cy, withAlpha(GHOST, t));

        float rowY = cy + 32.5f;
        gfx.draw(ICON_STATUS, cx + 105, rowY + 3, 18, 18, withAlpha(0xFFFFFFFF, t));
        gfx.draw(gfx.text(mc.getSession().getUsername(), false, 18), cx + 127, rowY, withAlpha(SPACE_12, t));
    }

    private void toggleAccountPopover() { if (accountOpen) closeAccountPopover(); else accountOpen = true; }
    private void closeAccountPopover() { if (accountOpen) { accountOpen = false; accountClosedAt = System.currentTimeMillis(); } }

    private static int withAlpha(int argb, float t) { return Math.round((argb >>> 24) * t) << 24 | (argb & 0xFFFFFF); }

    private void drawTopButton(float x, ResourceLocation icon, String key, Runnable action) {
        float t = fade(key, over(x, 20, 42, 40));
        if (picker.legacyButtons()) {
            legacyFrame(x, 20, 42, 40, 8, t);
            gfx.draw(icon, x + 12, 31, 18, 18, lerp(LEGACY_TEXT, 0xFFFFFFFF, t));
        } else {
            gfx.draw(gfx.box(42, 40, 8), x, 20, lerp(SPACE_1, SPACE_2, t));
            gfx.draw(icon, x + 12, 31, 18, 18, lerp(SPACE_11, SPACE_12, t));
        }
        hits.add(new Hit(x, 20, 42, 40, action));
    }

    private void drawCentre() {
        float top = cssH / 2 - 44, left = cssW / 2 - 200;
        float seconds = (System.currentTimeMillis() - opened) / 1000f;
        float logoX = cssW / 2 - 62, logoY = top - 152;
        gfx.draw(LOGO[0], logoX, logoY, 124, 124, 0xFFFFFFFF);
        for (int i = 0; i < stars.length; i++)
            gfx.draw(LOGO[i + 1], logoX, logoY, 124, 124, ((int)(stars[i].opacity(seconds) * 255) << 24) | 0xFFFFFF);
        Runnable single = new Runnable() { public void run() { mc.displayGuiScreen(new GuiSelectWorld(LunarHomeScreen.this)); } };
        Runnable multi = new Runnable() { public void run() { mc.displayGuiScreen(new GuiMultiplayer(LunarHomeScreen.this)); } };
        if (picker.legacyButtons()) {
            legacyButton(left, top, "SINGLEPLAYER", single);
            legacyButton(left, top + 46, "MULTIPLAYER", multi);
        } else {
            mainButton(left, top, "Singleplayer", ICON_SINGLE, single);
            mainButton(left, top + 48, "Multiplayer", ICON_MULTI, multi);
        }
    }

    private void legacyButton(float x, float y, String label, Runnable action) {
        float w = 400, h = 34, r = 8;
        float t = fade("legacy:" + label, over(x, y, w, h));
        legacyFrame(x, y, w, h, r, t);
        String spaced = label.replace("", " ").trim();
        HomeGfx.Tex text = gfx.legacyText(spaced, 14);
        float tx = x + (w - gfx.legacyTextWidth(spaced, 14)) / 2, ty = y + (h - text.height) / 2;
        gfx.draw(text, tx + 1, ty + 1, 0x20000000);
        gfx.draw(text, tx, ty, LEGACY_TEXT);
        hits.add(new Hit(x, y, w, h, action));
    }

    private void legacyFrame(float x, float y, float w, float h, float r, float hover) {
        gfx.draw(gfx.box(w, h, r), x, y, lerp(0x20FFFFFF, 0x45FFFFFF, hover));
        gfx.draw(gfx.ring(w, h, r, 0, 1), x, y, 0x40252525);
        gfx.draw(gfx.ring(w, h, r, 1, 1), x, y, 0x20FFFFFF);
    }

    private void mainButton(float x, float y, String label, ResourceLocation icon, Runnable action) {
        float t = fade(label, over(x, y, 400, 40));
        gfx.draw(gfx.box(400, 40, 8), x, y, lerp(SPACE_1, SPACE_2, t));
        float textW = gfx.textWidth(label, false, 18), contentX = x + 200 - (16 + 8 + textW) / 2;
        gfx.draw(icon, contentX, y + 12, 16, 16, SPACE_11);
        gfx.draw(gfx.text(label, false, 18), contentX + 24, y + 8, SPACE_12);
        hits.add(new Hit(x, y, 400, 40, action));
    }

    private void drawFooter(int navCount) {
        float navW = navCount * 38 + (navCount - 1) * 8, column = (cssW - 30 - navW - 16) / 2;
        List<String> left = wrap("Lunar Client 1.8.9 (" + LunarForgeMod.VERSION + ")", column);
        for (int i = 0; i < left.size(); i++)
            gfx.draw(gfx.text(left.get(i), false, 16), 15, cssH - 20 - 21 * (left.size() - i), FOOTER);
        List<String> right = wrap("Not affiliated with Mojang or Microsoft. Do not distribute!", column);
        for (int i = 0; i < right.size(); i++) {
            String line = right.get(i);
            gfx.draw(gfx.text(line, false, 16), cssW - 15 - gfx.textWidth(line, false, 16), cssH - 20 - 21 * (right.size() - i), FOOTER);
        }
    }

    private List<String> wrap(String text, float width) {
        List<String> lines = new ArrayList<String>();
        String line = "";
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (gfx.textWidth(candidate, false, 16) <= width || line.isEmpty() && width <= 0) { line = candidate; continue; }
            if (!line.isEmpty()) lines.add(line);
            line = word;
            while (width > 0 && gfx.textWidth(line, false, 16) > width && line.length() > 1) {
                int cut = line.length() - 1;
                while (cut > 1 && gfx.textWidth(line.substring(0, cut), false, 16) > width) cut--;
                lines.add(line.substring(0, cut)); line = line.substring(cut);
            }
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private void drawNavigation() {
        String[][] items = {{"Lunar Settings", "Lunar"}, {"Minecraft Settings", "Minecraft"}, {"Forge Mod Menu", "External"}};
        ResourceLocation[] icons = {NAV_LOGO, NAV_COG, NAV_MODS};
        ResourceLocation[] types = {TYPE_LUNAR, TYPE_VANILLA, TYPE_EXTERNAL};
        Runnable[] actions = {
            new Runnable() { public void run() { mc.displayGuiScreen(new LunarMovementScreen(features, menuKey, LunarHomeScreen.this)); } },
            new Runnable() { public void run() { mc.displayGuiScreen(new GuiOptions(LunarHomeScreen.this, mc.gameSettings)); } },
            new Runnable() { public void run() { mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(LunarHomeScreen.this)); } }};
        float total = items.length * 38 + (items.length - 1) * 8, x0 = cssW / 2 - total / 2, y = cssH - 58;
        int tooltip = -1;
        for (int i = 0; i < items.length; i++) {
            float x = x0 + i * 46;
            boolean hover = over(x, y, 38, 38);
            if (hover) tooltip = i;
            float t = fade("nav" + i, hover);
            if (picker.legacyButtons()) {
                legacyFrame(x, y, 38, 38, 8, t);
                gfx.draw(icons[i], x + 11, y + 11, 16, 16, lerp(LEGACY_TEXT, 0xFFFFFFFF, t));
            } else {
                gfx.draw(gfx.box(38, 38, 8), x, y, lerp(SPACE_1, SPACE_2, t));
                gfx.draw(icons[i], x + 11, y + 11, 16, 16, 0xA6FFFFFF);
            }
            hits.add(new Hit(x, y, 38, 38, actions[i]));
        }
        if (tooltip >= 0) drawTooltip(x0 + tooltip * 46 + 19, y - 10, items[tooltip][0], items[tooltip][1], types[tooltip]);
    }

    private void drawTooltip(float centreX, float bottom, String title, String type, ResourceLocation typeIcon) {
        float titleW = gfx.textWidth(title, true, 16), rowW = 14 + 4 + gfx.textWidth(type, false, 14);
        float w = Math.max(titleW, rowW) + 24, h = 58, x = centreX - w / 2, y = bottom - h;
        float pad = HomeGfx.shadowPad(15.9f);
        gfx.draw(gfx.shadow(w, h, 8, 7, 15.9f, -5), x - pad, y - pad, SHADOW);
        gfx.draw(gfx.box(w, h, 8), x, y, TOOLTIP_BG);
        gfx.draw(gfx.text(title, true, 16), x + 12, y + 9, SPACE_11);
        gfx.draw(typeIcon, x + 12, y + 34, 14, 14, TOOLTIP_SUB);
        gfx.draw(gfx.text(type, false, 14), x + 30, y + 32, TOOLTIP_SUB);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) return;
        if (picker.blocksHome()) { picker.mouseClicked(mouseCssX, mouseCssY); return; }

        if (inPopover(mouseCssX, mouseCssY)) return;
        if (overRaw(20, 20, accountW, 40)) { toggleAccountPopover(); return; }
        closeAccountPopover();
        for (Hit hit : hits) {
            if (mouseCssX >= hit.x && mouseCssY >= hit.y && mouseCssX < hit.x + hit.w && mouseCssY < hit.y + hit.h) {
                mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
                hit.action.run();
                return;
            }
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) {
        picker.keyTyped(typedChar, keyCode);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) picker.mouseWheel(Integer.signum(wheel));
    }

    @Override public void onGuiClosed() { gfx.release(); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void drawPanorama(float partialTicks) {
        HomeThemes.Theme theme = themes.current();
        ResourceLocation[] panorama = new ResourceLocation[6];
        for (int face = 0; face < 6; face++) panorama[face] = theme.panorama(face);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int i = 8;
        for (int j = 0; j < i * i; ++j) {
            GlStateManager.pushMatrix();
            float f = ((float)(j % i) / (float)i - 0.5F) / 64.0F;
            float f1 = ((float)(j / i) / (float)i - 0.5F) / 64.0F;
            GlStateManager.translate(f, f1, 0.0F);
            GlStateManager.rotate(MathHelper.sin(((float)panoramaTimer + partialTicks) / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-((float)panoramaTimer + partialTicks) * 0.1F, 0.0F, 1.0F, 0.0F);
            for (int k = 0; k < 6; ++k) {
                GlStateManager.pushMatrix();
                if (k == 1) GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                if (k == 2) GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                if (k == 3) GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                if (k == 4) GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                if (k == 5) GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                mc.getTextureManager().bindTexture(panorama[k]);
                worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int l = 255 / (j + 1);
                worldrenderer.pos(-1.0D, -1.0D, 1.0D).tex(0.0D, 0.0D).color(255, 255, 255, l).endVertex();
                worldrenderer.pos(1.0D, -1.0D, 1.0D).tex(1.0D, 0.0D).color(255, 255, 255, l).endVertex();
                worldrenderer.pos(1.0D, 1.0D, 1.0D).tex(1.0D, 1.0D).color(255, 255, 255, l).endVertex();
                worldrenderer.pos(-1.0D, 1.0D, 1.0D).tex(0.0D, 1.0D).color(255, 255, 255, l).endVertex();
                tessellator.draw();
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }
        worldrenderer.setTranslation(0.0D, 0.0D, 0.0D);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private void rotateAndBlurSkybox() {
        mc.getTextureManager().bindTexture(backgroundTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 256, 256);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        GlStateManager.disableAlpha();
        int i = 3;
        for (int j = 0; j < i; ++j) {
            float f = 1.0F / (float)(j + 1);
            int k = width, l = height;
            float f1 = (float)(j - i / 2) / 256.0F;
            worldrenderer.pos((double)k, (double)l, (double)zLevel).tex((double)(0.0F + f1), 1.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos((double)k, 0.0D, (double)zLevel).tex((double)(1.0F + f1), 1.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos(0.0D, 0.0D, (double)zLevel).tex((double)(1.0F + f1), 0.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos(0.0D, (double)l, (double)zLevel).tex((double)(0.0F + f1), 0.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableAlpha();
        GlStateManager.colorMask(true, true, true, true);
    }

    private void renderSkybox(float partialTicks) {
        mc.getFramebuffer().unbindFramebuffer();
        GlStateManager.viewport(0, 0, 256, 256);
        drawPanorama(partialTicks);
        for (int pass = 0; pass < 7; pass++) rotateAndBlurSkybox();
        mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        float f = width > height ? 120.0F / (float)width : 120.0F / (float)height;
        float f1 = (float)height * f / 256.0F;
        float f2 = (float)width * f / 256.0F;
        int i = width, j = height;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        worldrenderer.pos(0.0D, (double)j, (double)zLevel).tex((double)(0.5F - f1), (double)(0.5F + f2)).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos((double)i, (double)j, (double)zLevel).tex((double)(0.5F - f1), (double)(0.5F - f2)).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos((double)i, 0.0D, (double)zLevel).tex((double)(0.5F + f1), (double)(0.5F - f2)).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos(0.0D, 0.0D, (double)zLevel).tex((double)(0.5F + f1), (double)(0.5F + f2)).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        tessellator.draw();
    }

    static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8)
            out |= Math.round(((a >>> shift) & 0xFF) * (1 - t) + ((b >>> shift) & 0xFF) * t) << shift;
        return out;
    }

    static float cubicBezier(float x1, float y1, float x2, float y2, float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        float t = x;
        for (int i = 0; i < 8; i++) {
            float cx = bezier(x1, x2, t) - x, d = bezierSlope(x1, x2, t);
            if (Math.abs(cx) < 1e-5f) break;
            if (Math.abs(d) < 1e-6f) break;
            t -= cx / d;
        }
        if (t < 0 || t > 1 || Math.abs(bezier(x1, x2, t) - x) > 1e-3f) {
            float lo = 0, hi = 1; t = x;
            for (int i = 0; i < 30; i++) { if (bezier(x1, x2, t) < x) lo = t; else hi = t; t = (lo + hi) / 2; }
        }
        return bezier(y1, y2, t);
    }
    private static float bezier(float p1, float p2, float t) { float u = 1 - t; return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t; }
    private static float bezierSlope(float p1, float p2, float t) { float u = 1 - t; return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2); }
}
