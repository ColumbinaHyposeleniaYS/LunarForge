package com.example.lunarforge.gui;

import com.example.lunarforge.feature.ClientFeature;
import com.example.lunarforge.feature.FeatureManager;
import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.UiModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class LunarMovementScreen extends GuiScreen {
    private static final int GUIDE = 0xFFD93CFF;
    private static final int SELECTED = 0x507FFFD4, SELECT_LINE = 0xFF7FFFD4;
    private static final int HOVERED = 0x80FFFFFF, IDLE = 0x20FFFFFF, BOX_LINE = 0x80000000;
    private static final int BUTTON_FILL = 0x30000000, BUTTON_FILL_HOVER = 0x50000000;
    private static final int BUTTON_LINE = 0x40252525, BUTTON_LINE_HOVER = 0x99FFFFFF, BUTTON_INNER = 0x20FFFFFF;
    private static final String COG = "ui/icons/mainmenu/cog-20x20.png", CLOSE = "ui/icons/exit-17x17-small.png";

    private static final String[][] SHORTCUTS = {
        {"Mouse1", null, "hold", "addModsToRegion"},
        {"Mouse1", null, "hold", "selectDragMods"},
        {"Mouse2", null, "click", "resetToClosest"},
        {"CTRL", "Mouse1", null, "toggleModSelection"},
        {"CTRL", "Z", null, "undoMovements"},
        {"CTRL", "Y", null, "redoMovements"},
    };
    private static String movement(String key) { return com.example.lunarforge.gui.ui.LunarLang.get("gui.movement", key); }

    private final FeatureManager features;
    private final KeyBinding openKey;

    private final GuiScreen background;
    private final LunarGfx gfx = new LunarGfx("move");
    private ScaledResolution res;

    private long openedAt, horizontalFlashAt, verticalFlashAt, lastFrame;
    private final float[] buttonHover = new float[4];

    private final List<ClientFeature> selected = new ArrayList<ClientFeature>();
    private final Map<ClientFeature, float[]> grab = new HashMap<ClientFeature, float[]>();
    private ClientFeature hovered, dragging, resizing;
    private int dragButton;
    private boolean moved;
    private long pressedAt;
    private float regionX, regionY;
    private boolean region;
    private long regionAt;
    private final List<ClientFeature> inRegion = new ArrayList<ClientFeature>();
    private float pivotX, pivotY, perScaleX, perScaleY;
    private final List<float[]> guides = new ArrayList<float[]>();

    private static final class Snapshot {
        final String id, anchor, x, y, scale; final boolean enabled; final long time;
        Snapshot(String id, String anchor, String x, String y, String scale, boolean enabled, long time) {
            this.id = id; this.anchor = anchor; this.x = x; this.y = y; this.scale = scale; this.enabled = enabled; this.time = time;
        }
    }
    private final List<Snapshot> undo = new ArrayList<Snapshot>(), redo = new ArrayList<Snapshot>(), pending = new ArrayList<Snapshot>();
    private int nudgeTicks;
    private boolean nudging;

    public LunarMovementScreen(FeatureManager features, KeyBinding openKey) { this(features, openKey, null); }

    public LunarMovementScreen(FeatureManager features, KeyBinding openKey, GuiScreen background) {
        this.features = features;
        this.openKey = openKey;
        this.background = background;
    }

    private void close() { mc.displayGuiScreen(mc.theWorld == null ? background : null); }

    @Override
    public void initGui() {
        res = new ScaledResolution(mc);
        gfx.setScale(res.getScaleFactor());
        if (openedAt == 0) openedAt = System.currentTimeMillis();
    }

    private UiModel model() { return features.ui(); }

    private float intro() {
        float t = Math.min(1, (System.currentTimeMillis() - openedAt) / 500f);
        return t < .5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    private static float pulse(long startedAt) {
        float t = (System.currentTimeMillis() - startedAt) / 1000f;
        if (startedAt == 0 || t >= 1) return 0;
        return (float)(Math.cos((t * 2 - 1) * Math.PI) + 1) / 2;
    }

    private float[] modsButton() { return new float[]{width / 2f - 50, height / 2f - 14, 100, 28}; }
    private float[] cosmeticsButton() { return new float[]{width / 2f + 54, height / 2f - 14, 28, 28}; }
    private float[] emotesButton() { return new float[]{width / 2f - 82, height / 2f - 14, 28, 28}; }
    private float[] helpButton() { return new float[]{4, height - 28, 24, 24}; }
    private float[][] buttons() { return new float[][]{modsButton(), cosmeticsButton(), emotesButton(), helpButton()}; }

    private static boolean inside(float x, float y, float[] r) {
        return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3];
    }

    private boolean overCentreButtons(float x, float y) {
        return inside(x, y, modsButton()) || inside(x, y, cosmeticsButton()) || inside(x, y, emotesButton());
    }

    private float mouseX() { return Mouse.getX() * width / (float)mc.displayWidth; }
    private float mouseY() { return height - Mouse.getY() * height / (float)mc.displayHeight - 1; }

    private List<ClientFeature> visible() {
        List<ClientFeature> out = new ArrayList<ClientFeature>();
        for (ClientFeature feature : features.getFeatures()) if (features.isShown(feature)) out.add(feature);
        return out;
    }

    private static boolean roomy(float[] b) { return b[2] > 16 && b[3] > 10; }

    private boolean[] handleCorner(ClientFeature f, float[] b) {
        HudAnchor anchor = anchorOf(f, b);
        if (anchor.horizontal == HudAnchor.Side.START) return new boolean[]{true, anchor.vertical != HudAnchor.Side.END};
        return new boolean[]{false, anchor.vertical == HudAnchor.Side.START};
    }

    private HudAnchor anchorOf(ClientFeature f, float[] b) {
        HudAnchor anchor = features.anchor(f.getId());
        return anchor != null ? anchor : HudAnchor.at(b[0] + b[2] / 2, b[1] + b[3] / 2, width, height);
    }

    private float iconY(ClientFeature f, float[] b) {
        if (roomy(b)) return handleCorner(f, b)[1] ? b[1] + 2 : b[1] + b[3] - 10;
        boolean above = anchorOf(f, b).vertical != HudAnchor.Side.START;
        if (above) return b[1] - 10 >= 0 ? b[1] - 10 : b[1] + b[3] + 2;
        return b[1] + b[3] + 12 <= height ? b[1] + b[3] + 2 : b[1] - 10;
    }

    private float[] cogIcon(ClientFeature f, float[] b) {
        return new float[]{roomy(b) ? b[0] + 1 : b[0] + b[2] / 2 - 10, iconY(f, b)};
    }

    private float[] closeIcon(ClientFeature f, float[] b) {
        return new float[]{roomy(b) ? b[0] + b[2] - 10 : b[0] + b[2] / 2 + 2, iconY(f, b)};
    }

    private static boolean overIcon(float x, float y, float[] icon) {
        return x > icon[0] - 2 && y > icon[1] - 2 && x < icon[0] + 10 && y < icon[1] + 10.25f;
    }

    private float[] handle(ClientFeature f, float[] b) {
        boolean[] corner = handleCorner(f, b);
        return new float[]{b[0] + (corner[0] ? b[2] : 0) - 2.5f, b[1] + (corner[1] ? b[3] : 0) - 2.5f};
    }

    private static boolean overHandle(float x, float y, float[] h) {
        return x >= h[0] - 2 && x <= h[0] + 7 && y >= h[1] - 2 && y <= h[1] + 7;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        res = new ScaledResolution(mc);
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : (now - lastFrame) / 125f;
        lastFrame = now;
        float x = mouseX(), y = mouseY(), a = intro();
        if (mc.theWorld == null) com.example.lunarforge.gui.home.LunarHomeScreen.drawBehind(background, this, partialTicks);

        LunarGfx.outline(1.5f, 1.5f, width - 3, height - 3, .5f, LunarGfx.alpha(0xFF00FFFF, .8f * a));
        float flashH = pulse(horizontalFlashAt), flashV = pulse(verticalFlashAt);
        if (flashH > 0) LunarGfx.rect(0, height / 2f - .25f, width, height / 2f + .25f, LunarGfx.alpha(GUIDE, flashH));
        if (flashV > 0) LunarGfx.rect(width / 2f - .25f, 0, width / 2f + .25f, height, LunarGfx.alpha(GUIDE, flashV));

        boolean regionShown = region && now - regionAt >= 100;
        if (regionShown) {
            float rx = Math.min(x, regionX), ry = Math.min(y, regionY), rw = Math.abs(x - regionX), rh = Math.abs(y - regionY);
            LunarGfx.rect(rx, ry, rx + rw, ry + rh, SELECTED);
            LunarGfx.outline(rx, ry, rw, rh, .5f, SELECT_LINE);
        }

        guides.clear();
        if (resizing != null && Mouse.isButtonDown(0)) resizeTo(x, y);
        else if (dragging != null && Mouse.isButtonDown(dragButton)) dragTo(x, y);

        List<ClientFeature> mods = visible();
        updateHover(mods, x, y);
        inRegion.clear();
        for (ClientFeature f : mods) {
            float[] b = features.bounds(f, res);
            if (regionShown && intersects(b, Math.min(x, regionX), Math.min(y, regionY), Math.max(x, regionX), Math.max(y, regionY))) inRegion.add(f);
            features.render(f, res);
            int fill = selected.contains(f) || inRegion.contains(f) ? SELECTED : f == hovered ? HOVERED : IDLE;
            LunarGfx.rect(b[0], b[1], b[0] + b[2], b[1] + b[3], fill);
            LunarGfx.outline(b[0], b[1], b[2] - .5f, b[3] - .5f, .5f, BOX_LINE);
        }
        if (hovered != null && resizing == null) {
            float[] b = features.bounds(hovered, res);
            float[] h = handle(hovered, b);
            LunarGfx.rect(h[0], h[1], h[0] + 5, h[1] + 5, SELECT_LINE);
            float[] cog = cogIcon(hovered, b), close = closeIcon(hovered, b);
            boolean onCog = overIcon(x, y, cog), onClose = overIcon(x, y, close);
            gfx.image(COG, cog[0] + 1, cog[1] + 1, 8, 8, onCog ? 0xFF000000 : 0xB3000000);
            gfx.image(CLOSE, close[0] + 1, close[1] + 1, 8, 8, onClose ? 0xFF000000 : 0xB3000000);
            gfx.image(COG, cog[0], cog[1], 8, 8, onCog ? 0xFFFFFFFF : 0x80FFFFFF);
            gfx.image(CLOSE, close[0], close[1], 8, 8, onClose ? 0xFFFF3333 : 0x80FF3333);
        } else if (resizing != null) {
            float[] h = handle(resizing, features.bounds(resizing, res));
            LunarGfx.rect(h[0], h[1], h[0] + 5, h[1] + 5, SELECT_LINE);
        }
        for (float[] g : guides) {
            if (g[0] == 0) LunarGfx.rect(g[1] - .25f, 0, g[1] + .25f, height, GUIDE);
            else LunarGfx.rect(0, g[1] - .25f, width, g[1] + .25f, GUIDE);
        }

        drawLogo(a);
        float[][] buttons = buttons();
        boolean busy = dragging != null || resizing != null || region;
        for (int i = 0; i < buttons.length; i++) {
            boolean over = !busy && inside(x, y, buttons[i]);
            buttonHover[i] = Math.max(0, Math.min(1, buttonHover[i] + (over ? dt : -dt)));
        }
        drawButton(modsButton(), buttonHover[0], "MODS", null);
        drawButton(cosmeticsButton(), buttonHover[1], null, "ui/icons/assets/cosmetic-28x28.png");
        drawButton(emotesButton(), buttonHover[2], null, "ui/icons/assets/emote-28x28.png");
        drawButton(helpButton(), buttonHover[3], "?", null);
        if (!busy && inside(x, y, helpButton())) drawShortcuts();

        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawLogo(float a) {
        if (a > .2f) {
            float w = gfx.textWidth("LUNAR", LunarGfx.RALEWAY_EXTRABOLD, 22);
            float ty = height / 2f - 40;
            int shadow = LunarGfx.alpha(0xFF000000, .4f * a), color = LunarGfx.alpha(0xFFFFFFFF, a);
            gfx.text("LUNAR", LunarGfx.RALEWAY_EXTRABOLD, 22, width / 2f - w - 2 + 1, ty + 1, shadow);
            gfx.text("LUNAR", LunarGfx.RALEWAY_EXTRABOLD, 22, width / 2f - w - 2, ty, color);
            gfx.text("CLIENT", LunarGfx.RALEWAY_LIGHT, 22, width / 2f + 2 + 1, ty + 1, shadow);
            gfx.text("CLIENT", LunarGfx.RALEWAY_LIGHT, 22, width / 2f + 2, ty, color);
        }
        gfx.image("splash/logo-128x117.png", width / 2f - 32, height / 2f - 80 - 20 * a, 64, 58.5f, 0xFFFFFFFF);
    }

    private void drawButton(float[] r, float hover, String label, String icon) {
        int fill = LunarGfx.mix(BUTTON_FILL, BUTTON_FILL_HOVER, hover);
        gfx.roundRect(r[0], r[1], r[2], r[3], 5, fill);
        gfx.roundOutline(r[0], r[1], r[2], r[3], 4, 1, LunarGfx.mix(BUTTON_LINE, BUTTON_LINE_HOVER, hover));
        gfx.roundOutline(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 2.75f, 1, BUTTON_INNER);
        if (icon != null) {
            float alpha = Math.min(1, (fill >>> 24) / 255f * 2.5f);
            gfx.image(icon, r[0] + r[2] / 2 - 7, r[1] + r[3] / 2 - 7, 14, 14, LunarGfx.alpha(0xFFFFFFFF, alpha));
        } else {
            float w = gfx.textWidth(label, LunarGfx.ROBOTO_LIGHT, 22), h = gfx.textHeight(LunarGfx.ROBOTO_LIGHT, 22);
            float tx = r[0] + r[2] / 2 - w / 2, ty = r[1] + r[3] / 2 - h / 2;
            gfx.text(label, LunarGfx.ROBOTO_LIGHT, 22, tx + 1, ty + 1, 0x20000000);
            gfx.text(label, LunarGfx.ROBOTO_LIGHT, 22, tx, ty, 0xFFFFFFFF);
        }
    }

    private void drawShortcuts() {
        int rows = SHORTCUTS.length;
        float top = height - 185, listEnd = 16 + rows * 12 + 12, panelHeight = listEnd + 12 + 16;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, top, 0);
        LunarGfx.rect(0, 0, 240, panelHeight, 0x80000000);
        gfx.text(movement("shortcutsMovement"), LunarGfx.ROBOTO_LIGHT, 16, 4, 2, 0xFFFFFFFF);
        LunarGfx.rect(4, 12, 230, .5f, 0x80FFFFFF);
        float row = 16;
        for (String[] s : SHORTCUTS) {
            keycap(s[0], 6, row);
            if (s[1] != null) {
                gfx.text("+", LunarGfx.ROBOTO_MEDIUM, 13, 30, row, 0xFFFFFFFF);
                keycap(s[1], 36, row);
            }
            float tx = 80;
            gfx.text("| ", LunarGfx.ROBOTO_MEDIUM, 13, tx, row, 0xFFFFFFFF);
            tx += gfx.textWidth("| ", LunarGfx.ROBOTO_MEDIUM, 13);
            if (s[2] != null) {
                String action = movement(s[2]) + " ";
                gfx.text(action, LunarGfx.ROBOTO_MEDIUM, 13, tx, row, 0xFF55FFFF);
                tx += gfx.textWidth(action, LunarGfx.ROBOTO_MEDIUM, 13);
            }
            gfx.text(movement(s[3]), LunarGfx.ROBOTO_MEDIUM, 13, tx, row, 0xFFFFFFFF);
            row += 12;
        }
        keycap("Up", 31, listEnd);
        keycap("Left", 6, listEnd + 12);
        keycap("Down", 26, listEnd + 12);
        keycap("Right", 51, listEnd + 12);
        gfx.text("| " + movement("moveWithPrecision"), LunarGfx.ROBOTO_MEDIUM, 13, 80, listEnd + 12, 0xFFFFFFFF);
        GlStateManager.popMatrix();
    }

    private void keycap(String key, float x, float y) {
        float w = gfx.textWidth(key, LunarGfx.ROBOTO_MEDIUM, 13) + 4;
        gfx.roundRect(x, y, w, 10, 1, 0xFFFFFFFF);
        gfx.text(key, LunarGfx.ROBOTO_MEDIUM, 13, x + 2, y, 0xFF000000);
    }

    private static boolean intersects(float[] b, float x1, float y1, float x2, float y2) {
        return b[0] < x2 && b[0] + b[2] > x1 && b[1] < y2 && b[1] + b[3] > y1;
    }

    private void updateHover(List<ClientFeature> mods, float x, float y) {
        if (dragging != null || resizing != null) { hovered = dragging != null ? dragging : resizing; return; }
        if (region || overCentreButtons(x, y)) { hovered = null; return; }
        if (hovered != null && mods.contains(hovered)) {
            float[] b = features.bounds(hovered, res);
            if (inside(x, y, b) || overIcon(x, y, cogIcon(hovered, b)) || overIcon(x, y, closeIcon(hovered, b)) || overHandle(x, y, handle(hovered, b))) return;
        }
        hovered = null;
        for (int i = mods.size() - 1; i >= 0; i--) {
            if (inside(x, y, features.bounds(mods.get(i), res))) { hovered = mods.get(i); return; }
        }
    }

    private void dragTo(float x, float y) {
        float[] off = grab.get(dragging);
        if (off == null) return;
        float[] b = features.bounds(dragging, res);
        float nx = x - off[0], ny = y - off[1];
        if (!moved) {
            if (nx == b[0] && ny == b[1]) return;
            moved = true;
        }
        boolean snapX = true, snapY = true;
        if (nx <= 4) { nx = HudAnchor.PAD; snapX = false; guides.add(new float[]{0, 1.5f}); }
        else if (nx + b[2] >= width - 4) { nx = width - HudAnchor.PAD - b[2]; snapX = false; guides.add(new float[]{0, width - 1.5f}); }
        if (ny <= 4) { ny = HudAnchor.PAD; snapY = false; guides.add(new float[]{1, 1}); }
        else if (ny + b[3] >= height - 4) { ny = height - HudAnchor.PAD - b[3]; snapY = false; guides.add(new float[]{1, height - 1.5f}); }

        if (selected.size() > 1) {
            float dx = nx - b[0], dy = ny - b[1];
            for (ClientFeature f : selected) {
                float[] o = features.bounds(f, res);
                dx = Math.max(dx, HudAnchor.PAD - o[0]);
                dx = Math.min(dx, width - HudAnchor.PAD - o[0] - o[2]);
                dy = Math.max(dy, HudAnchor.PAD - o[1]);
                dy = Math.min(dy, height - HudAnchor.PAD - o[1] - o[3]);
            }
            for (ClientFeature f : selected) {
                float[] o = features.bounds(f, res);
                features.setPosition(f, o[0] + dx, o[1] + dy, res);
            }
            return;
        }
        if (dragButton == 0) {
            float[] snapped = snap(dragging, nx, ny, b[2], b[3], snapX, snapY);
            nx = snapped[0]; ny = snapped[1];
        }
        features.setPosition(dragging, nx, ny, res);
    }

    private float[] snap(ClientFeature moving, float nx, float ny, float w, float h, boolean snapX, boolean snapY) {
        HudAnchor anchor = HudAnchor.at(nx + w / 2, ny + h / 2, width, height);
        if (snapY && anchor.vertical == HudAnchor.Side.MIDDLE && Math.abs(height / 2f - (ny + h / 2)) < 5) {
            ny = height / 2f - h / 2; snapY = false; guides.add(new float[]{1, height / 2f});
        }
        boolean centreColumn = anchor.horizontal == HudAnchor.Side.MIDDLE || anchor == HudAnchor.BOTTOM_CENTER_L || anchor == HudAnchor.BOTTOM_CENTER_R;
        if (snapX && centreColumn && Math.abs(width / 2f - (nx + w / 2)) < 5) {
            nx = width / 2f - w / 2; snapX = false; guides.add(new float[]{0, width / 2f});
        }
        final float cx = nx + w / 2, cy = ny + h / 2;
        List<ClientFeature> others = visible();
        Collections.sort(others, new Comparator<ClientFeature>() {
            @Override public int compare(ClientFeature a, ClientFeature b) {
                return Float.compare(distance(features.bounds(a, res), cx, cy), distance(features.bounds(b, res), cx, cy));
            }
        });
        for (ClientFeature other : others) {
            if (!snapX && !snapY) break;
            if (other == moving) continue;
            float[] o = features.bounds(other, res);
            if (o[2] < 1 || o[3] < 1) continue;
            if (snapX) {
                float[][] pairs = {{o[0] - nx, o[0]}, {o[0] + o[2] - (nx + w), o[0] + o[2]}, {o[0] - (nx + w), o[0]}, {o[0] + o[2] - nx, o[0] + o[2]}};
                for (float[] p : pairs) if (Math.abs(p[0]) <= 2) { nx += p[0]; snapX = false; guides.add(new float[]{0, p[1]}); break; }
            }
            if (snapY) {
                float[][] pairs = {{o[1] - ny, o[1] - .5f}, {o[1] + o[3] - (ny + h), o[1] + o[3]}, {o[1] - (ny + h), o[1]}, {o[1] + o[3] - ny, o[1] + o[3]}};
                for (float[] p : pairs) if (Math.abs(p[0]) <= 2) { ny += p[0]; snapY = false; guides.add(new float[]{1, p[1]}); break; }
            }
        }
        return new float[]{nx, ny};
    }

    private static float distance(float[] b, float x, float y) {
        float dx = Math.max(Math.abs(b[0] + b[2] / 2 - x) - b[2] / 2, 0), dy = Math.max(Math.abs(b[1] + b[3] / 2 - y) - b[3] / 2, 0);
        return dx * dx + dy * dy;
    }

    private void startResize(ClientFeature f, float[] b) {
        boolean[] corner = handleCorner(f, b);
        float scale = features.scale(f.getId());
        pivotX = corner[0] ? b[0] : b[0] + b[2];
        pivotY = corner[1] ? b[1] : b[1] + b[3];
        perScaleX = perScale((corner[0] ? b[0] + b[2] : b[0]) - pivotX, scale);
        perScaleY = perScale((corner[1] ? b[1] + b[3] : b[1]) - pivotY, scale);
        resizing = f;
    }

    private static float perScale(float distance, float scale) {
        float per = distance / scale;
        return Math.abs(per) < .5f ? 0 : per;
    }

    private void resizeTo(float x, float y) {
        float sx = perScaleX == 0 ? Float.NEGATIVE_INFINITY : (x - pivotX) / perScaleX;
        float sy = perScaleY == 0 ? Float.NEGATIVE_INFINITY : (y - pivotY) / perScaleY;
        float scale = Math.max(.5f, Math.min(3, Math.round(Math.max(sx, sy) * 100) / 100f));
        if (Float.isInfinite(scale) || scale == features.scale(resizing.getId())) return;
        model().set(resizing.getId(), "scale", "" + scale);

        float[] b = features.bounds(resizing, res);
        float nx = perScaleX >= 0 ? pivotX : pivotX - b[2], ny = perScaleY >= 0 ? pivotY : pivotY - b[3];
        features.setPosition(resizing, nx, ny, res);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        float x = mouseX(), y = mouseY();
        if (button == 0) {
            if (inside(x, y, modsButton())) { openMods(null); return; }
            if (inside(x, y, cosmeticsButton())) { mc.displayGuiScreen(new com.example.lunarforge.gui.locker.LockerScreen(this)); return; }
            if (inside(x, y, emotesButton())) { mc.displayGuiScreen(com.example.lunarforge.gui.locker.LockerScreen.emotes(this)); return; }
            if (inside(x, y, helpButton())) return;
        }
        if (button != 0 && button != 1) return;
        long now = System.currentTimeMillis();
        if (hovered != null) {
            float[] b = features.bounds(hovered, res);
            if (button == 0 && overIcon(x, y, cogIcon(hovered, b))) { openMods(hovered.getId()); return; }
            if (button == 0 && overIcon(x, y, closeIcon(hovered, b))) {
                record(hovered, now);
                model().set(hovered.getId(), "enabled", "false");
                selected.remove(hovered);
                features.save();
                hovered = null;
                return;
            }
            if (button == 0 && overHandle(x, y, handle(hovered, b))) {
                pending.clear();
                pending.add(snapshot(hovered, now));
                startResize(hovered, b);
                return;
            }
            pending.clear();
            for (ClientFeature f : features.getFeatures()) pending.add(snapshot(f, now));
            pressedAt = now;
            dragging = hovered;
            dragButton = button;
            moved = false;
            if (!selected.contains(hovered)) {
                if (!isCtrlKeyDown()) selected.clear();
                selected.add(hovered);
            }
            grab.clear();
            for (ClientFeature f : selected) {
                float[] o = features.bounds(f, res);
                grab.put(f, new float[]{x - o[0], y - o[1]});
            }
        } else {
            if (!isCtrlKeyDown()) selected.clear();
            region = true;
            regionX = x;
            regionY = y;
            regionAt = now;
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (button != 0 && button != 1) return;
        long now = System.currentTimeMillis();
        if (region) {
            region = false;
            for (ClientFeature f : inRegion) if (!selected.contains(f)) selected.add(f);
            inRegion.clear();
        }
        if (resizing != null) {
            features.reanchor(resizing, res);
            commitPending();
            resizing = null;
        }
        if (dragging != null) {
            if (moved) for (ClientFeature f : selected) features.reanchor(f, res);
            if (!moved && button == 1 && now - pressedAt <= 250) snapToAnchor(dragging);
            commitPending();
            dragging = null;
        }
        features.save();
    }

    private void snapToAnchor(ClientFeature f) {
        features.reanchor(f, res);
        HudAnchor anchor = features.anchor(f.getId());
        float x = 0, y = 0;
        if (!isShiftKeyDown()) {
            if (anchor == HudAnchor.TOP_CENTER || anchor == HudAnchor.BOTTOM_CENTER_L || anchor == HudAnchor.BOTTOM_CENTER_R) {
                verticalFlashAt = System.currentTimeMillis();
                y = model().number(f.getId(), "y", 0);
            } else if (anchor == HudAnchor.MIDDLE_LEFT || anchor == HudAnchor.MIDDLE_RIGHT) {
                horizontalFlashAt = System.currentTimeMillis();
                x = model().number(f.getId(), "x", 0);
            } else flashCentres(anchor);
        } else flashCentres(anchor);
        model().set(f.getId(), "x", "" + x);
        model().set(f.getId(), "y", "" + y);
    }

    private void flashCentres(HudAnchor anchor) {
        if (anchor.vertical == HudAnchor.Side.MIDDLE) horizontalFlashAt = System.currentTimeMillis();
        if (anchor.horizontal == HudAnchor.Side.MIDDLE) verticalFlashAt = System.currentTimeMillis();
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == openKey.getKeyCode()) { close(); return; }
        if (isCtrlKeyDown()) {
            if (key == Keyboard.KEY_Z) { step(undo, redo); return; }
            if (key == Keyboard.KEY_Y) { step(redo, undo); return; }
            if (key == Keyboard.KEY_X && !selected.isEmpty()) {
                long now = System.currentTimeMillis();
                for (ClientFeature f : selected) { record(f, now); model().set(f.getId(), "enabled", "false"); }
                selected.clear();
                features.save();
                return;
            }
        }
        int dx = key == Keyboard.KEY_LEFT ? -1 : key == Keyboard.KEY_RIGHT ? 1 : 0;
        int dy = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : 0;
        nudge(dx, dy);
    }

    @Override
    public void updateScreen() {
        if (mc.theWorld == null) com.example.lunarforge.gui.home.LunarHomeScreen.tickPanorama();
        if (selected.isEmpty()) { nudgeTicks = 0; nudging = false; return; }
        int dx = 0, dy = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) dx--;
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) dx++;
        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) dy--;
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) dy++;
        if (dx == 0 && dy == 0) {
            nudgeTicks = 0;
            if (nudging) { nudging = false; for (ClientFeature f : selected) features.reanchor(f, res); features.save(); }
            return;
        }

        if (++nudgeTicks > 10) nudge(nudgeTicks > 20 ? dx * 2 : dx, nudgeTicks > 20 ? dy * 2 : dy);
    }

    private void nudge(int dx, int dy) {
        if ((dx == 0 && dy == 0) || selected.isEmpty()) return;
        float mx = dx, my = dy;
        for (ClientFeature f : selected) {
            float[] o = features.bounds(f, res);
            mx = Math.max(mx, HudAnchor.PAD - o[0]);
            mx = Math.min(mx, width - HudAnchor.PAD - o[0] - o[2]);
            my = Math.max(my, HudAnchor.PAD - o[1]);
            my = Math.min(my, height - HudAnchor.PAD - o[1] - o[3]);
        }
        if (mx == 0 && my == 0) return;
        long now = System.currentTimeMillis();
        for (ClientFeature f : selected) {
            if (!nudging) record(f, now);
            float[] o = features.bounds(f, res);
            features.setPosition(f, o[0] + mx, o[1] + my, res);
        }
        nudging = true;
    }

    private void openMods(String options) {
        mc.displayGuiScreen(new LunarClickGui(features, openKey, background));
        if (options != null) model().action("options:" + options);
    }

    private void notice(String text) { LunarNotifications.info(text); }

    private Snapshot snapshot(ClientFeature f, long time) {
        String id = f.getId();
        return new Snapshot(id, model().value(id, "anchor", UiModel.defaultOption(id, "anchor")),
            model().value(id, "x", UiModel.defaultOption(id, "x")), model().value(id, "y", UiModel.defaultOption(id, "y")),
            model().value(id, "scale", UiModel.defaultOption(id, "scale")), features.isEnabled(id), time);
    }

    private void record(ClientFeature f, long time) { push(snapshot(f, time)); }

    private void push(Snapshot s) {
        redo.clear();
        undo.add(s);
        while (undo.size() > 50) undo.remove(0);
    }

    private void commitPending() {
        for (Snapshot s : pending) {
            ClientFeature f = find(s.id);
            if (f == null) continue;
            Snapshot now = snapshot(f, s.time);
            if (!now.anchor.equals(s.anchor) || !now.x.equals(s.x) || !now.y.equals(s.y) || !now.scale.equals(s.scale)) push(s);
        }
        pending.clear();
    }

    private void step(List<Snapshot> from, List<Snapshot> to) {
        if (from.isEmpty()) return;
        long time = from.get(from.size() - 1).time;
        for (Iterator<Snapshot> it = from.iterator(); it.hasNext(); ) {
            Snapshot s = it.next();
            if (s.time != time) continue;
            ClientFeature f = find(s.id);
            it.remove();
            if (f == null) continue;
            to.add(snapshot(f, time));
            model().set(s.id, "anchor", s.anchor);
            model().set(s.id, "x", s.x);
            model().set(s.id, "y", s.y);
            model().set(s.id, "scale", s.scale);
            model().set(s.id, "enabled", "" + s.enabled);
        }
        features.save();
    }

    private ClientFeature find(String id) {
        for (ClientFeature f : features.getFeatures()) if (f.getId().equals(id)) return f;
        return null;
    }

    @Override
    public void onGuiClosed() {
        features.save();
        gfx.release();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
