package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.gui.ui.MenuCanvas;
import com.example.lunarforge.gui.ui.MenuWidget;
import com.example.lunarforge.util.Anim;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

final class CrosshairWidgets {
    private CrosshairWidgets() {}

    static void body(MenuCanvas c, float x, float y, float w, float h, float r, int outer, int inner, int fill) {
        c.fill(x, y, w, h, r + 1, fill);
        c.ring(x, y, w, h, r, outer);
        c.ring(x + 1, y + 1, w - 2, h - 2, r - 1.25f, inner);
    }

    static BufferedImage pixels(CrosshairGrid g) {
        int n = g.size.size;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < n * n; i++) if (g.data[i]) img.setRGB(i % n, i / n, -1);
        return img;
    }

    static final class Presets implements MenuWidget {
        private static List<Object[]> presets;
        private final CrosshairChild owner;

        Presets(CrosshairChild owner) { this.owner = owner; }

        private static List<Object[]> presets() {
            if (presets == null) {
                List<Object[]> out = new ArrayList<Object[]>();
                for (String code : CrosshairPresets.PRESETS) {
                    CrosshairGrid g = CrosshairGrid.parse(code);
                    out.add(new Object[]{g, g.isEmpty() ? null : pixels(g)});
                }
                presets = out;
            }
            return presets;
        }

        @Override public float height(float width) { return 30.0f; }

        private int at(float x, float y, float mx, float my) {
            if ((mx - x) % 22.75f > 15.0f || (my - (y + 3.0f)) % 25.0f > 15.0f) return -1;
            int col = (int)Math.floor((mx - x) / 22.75f), row = (int)Math.floor((my - (y + 3.0f)) / 25.0f);
            return col >= 0 && col < 14 && row == 0 ? col : -1;
        }

        private float lastX, lastY;

        @Override public void paint(MenuCanvas c, float x, float y, float w, float h, float mx, float my) {
            lastX = x;
            lastY = y;
            int over = at(x, y, mx, my);
            List<Object[]> list = presets();
            for (int i = 0; i < 14; i++) {
                float tx = x + 1.0f + i * 22.75f;
                body(c, tx, y + 3.0f, 15.0f, 15.0f, 4.0f, 0x40252525, 0x20FFFFFF, over == i ? 0x45FFFFFF : 0x20FFFFFF);
                Object[] p = list.get(i);
                if (p[1] == null) continue;
                int n = ((CrosshairGrid)p[0]).size.size, half = n / 2;
                c.bitmap((BufferedImage)p[1], tx + 6.85f - half, y + 10.0f - half, n, n, -1);
            }
        }

        @Override public void press(float mx, float my, int button) {
            int i = at(lastX, lastY, mx, my);
            if (i < 0 || i >= presets().size()) return;
            CrosshairGrid g = (CrosshairGrid)presets().get(i)[0];
            CrosshairGrid.Size size = g.size == CrosshairGrid.Size.SMALL ? g.size.bigger() : g.size;
            owner.setGrid(g.resize(size, false));
        }
    }

    static final class Preview implements MenuWidget {
        private static final String[] BACKDROPS = {"previews/biome_0.png", "previews/biome_1.png", "previews/biome_2.png", "previews/biome_3.png"};
        private final CrosshairChild owner;
        private float panX, panY, goalX, goalY;
        private boolean following;
        private long lastFrame;
        private float lastX, lastY, lastW, lastH;

        Preview(CrosshairChild owner) { this.owner = owner; }

        @Override public float height(float width) { return 96.0f; }

        @Override public void paint(MenuCanvas c, float x, float y, float w, float h, float mx, float my) {
            lastX = x; lastY = y; lastW = w; lastH = h;
            float f = w - 85.0f;
            boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
            following = over && mx < x + f;
            int index = Math.min(3, Math.max(0, owner.crosshairPreview.intValue()));
            if (following) {
                goalX = (mx - x) / f * 0.4f - 0.2f;
                goalY = (my - y) / h * 0.4f - 0.2f;
            } else {
                goalX = 0.0f;
                goalY = 0.0f;
            }

            long now = System.currentTimeMillis();
            float ticks = lastFrame == 0 ? 1.0f : Math.min(4.0f, (now - lastFrame) / 50.0f);
            lastFrame = now;
            float k = 1.0f - (float)Math.pow(1.0f - (following ? 0.35f : 0.15f), ticks);
            panX += (goalX - panX) * k;
            panY += (goalY - panY) * k;
            c.imageRegion(BACKDROPS[index], x, y, f, h, 0.2f + panX, 0.2f + panY, 0.8f + panX, 0.8f + panY);
            for (int i = 0; i < 4; i++) {
                c.imageRegion(BACKDROPS[i], x + w - 85.0f, y + i * h / 4.0f, 85.0f, h / 4.0f, 0.0f, 0.2f, 1.0f, 0.8f);
                if (index != i) continue;
                float bx = x + f, by = y + i * h / 4.0f, bw = 85.0f, bh = h / 4.0f - 1.0f;
                c.rect(bx - 1, by - 1, bw + 2, 1, 0xFFFFD700);
                c.rect(bx - 1, by + bh, bw + 2, 1, 0xFFFFD700);
                c.rect(bx - 1, by, 1, bh, 0xFFFFD700);
                c.rect(bx + bw, by, 1, bh, 0xFFFFD700);
            }
            Graphics2D g = c.graphics();
            Shape clip = g.getClip();
            g.clip(new Rectangle2D.Float(x, y, f, h));
            Java2DSurface surface = new Java2DSurface(g);
            if (following) owner.draw(surface, mx, my, true, 1.0f);
            else {
                float off = owner.mode() == CrosshairChild.Mode.SIMPLE && owner.shapes.evenCentered() ? 0.5f : 0.0f;
                owner.draw(surface, x + f / 2.0f - off, y + h / 2.0f - off, true, 1.0f);
            }
            g.setClip(clip);
        }

        @Override public void press(float mx, float my, int button) {
            if (mx < lastX + lastW - 85.0f || mx > lastX + lastW) return;
            owner.crosshairPreview.set("" + (int)Math.floor((my - lastY) * 4.0 / lastH));
        }

        @Override public boolean animating() {
            return following || Math.abs(panX - goalX) > 0.001f || Math.abs(panY - goalY) > 0.001f || owner.color.chroma();
        }
    }

    static final class Editor implements MenuWidget {
        private enum Mirror { NONE("none"), HORIZONTAL("horizontal"), VERTICAL("vertical"), QUADRANT("quadrant");
            final String id; Mirror(String id) { this.id = id; } }

        private final CrosshairChild owner;

        private final Anim help = new Anim(200L, 1.0f);
        private final QuickFade boxFade = new QuickFade(), resetFade = new QuickFade(), saveFade = new QuickFade(), loadFade = new QuickFade();
        private boolean pressed;
        private float pressX, pressY;
        private int lock = -1;
        private boolean canLoad;
        private Mirror mirror = Mirror.NONE;
        private boolean guides = true;

        private final Deque<List<int[]>> undo = new ArrayDeque<List<int[]>>(), redo = new ArrayDeque<List<int[]>>();
        private List<int[]> stroke;
        private CrosshairGrid.Size lastSize;
        private long clipboardChecked;
        private float lastX, lastY, lastW, lastH;

        Editor(CrosshairChild owner) {
            this.owner = owner;
            help.start();
        }

        private float helpOpen() {
            boolean open = !"false".equals(owner.crosshairDraw.get());
            float p = help.progress();
            return open ? p : 1.0f - p;
        }

        @Override public float height(float width) { return 164.0f; }

        private String edit(String key) { return LunarLang.get("gui.crosshair_edit", key); }

        @Override public void paint(MenuCanvas c, float x, float y, float w, float h, float mx, float my) {
            lastX = x; lastY = y; lastW = w; lastH = h;
            long now = System.currentTimeMillis();
            if (now - clipboardChecked > 500L) {
                clipboardChecked = now;
                String clip = net.minecraft.client.Minecraft.getMinecraft() != null ? GuiScreen.getClipboardString() : "";
                canLoad = clip != null && clip.trim().startsWith("LCCH-");
            }
            String tooltip = null;
            CrosshairGrid grid = owner.grid();
            if (grid.size != lastSize) { clearHistory(); lastSize = grid.size; }
            int n = grid.size.size;
            float cell = (h - 5.5f) / n;
            float gridX = x + (w - (h - 6.0f)) / 2.0f + 20.0f;
            int col = (int)Math.floor((mx - gridX) / cell), row = (int)Math.floor((my - y - 4.0f) / cell);
            boolean live = net.minecraft.client.Minecraft.getMinecraft() != null;
            boolean left = live && Mouse.isButtonDown(0), right = live && Mouse.isButtonDown(1);
            boolean shift = live && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            if ((left || right) && shift) {
                int pc = (int)Math.floor((pressX - gridX) / cell), pr = (int)Math.floor((pressY - y - 4.0f) / cell);
                if (lock == -1) {
                    if (pc != col) lock = 1;
                    else if (pr != row) lock = 0;
                }
                if (lock == 0) col = pc;
                else if (lock == 1) row = pr;
            } else if (!left && !right || !shift) {
                lock = -1;
            }
            int[] targets = {-1, -1, -1, -1};
            if (col >= 0 && col < n && row >= 0 && row < n) {
                int k = 0;
                targets[k++] = col + row * n;
                boolean quad = mirror == Mirror.QUADRANT;
                if (mirror == Mirror.HORIZONTAL || quad) targets[k++] = n - 1 - col + row * n;
                if (mirror == Mirror.VERTICAL || quad) targets[k++] = col + (n - 1 - row) * n;
                if (quad) targets[k] = n - 1 - col + (n - 1 - row) * n;
            }
            if (pressed) {
                if (left || right) {
                    if (stroke == null) stroke = new ArrayList<int[]>();
                    boolean changed = false;
                    for (int t : targets) {
                        if (t == -1 || grid.data[t] == left) continue;
                        stroke.add(new int[]{t, grid.data[t] ? 1 : 0, left ? 1 : 0});
                        grid.data[t] = left;
                        changed = true;
                    }
                    if (changed) owner.gridChanged();
                } else {
                    pressed = false;
                    push(undo, stroke);
                    stroke = null;
                }
            }
            float open = helpOpen();
            float boxW = 16.0f + (h - 87.0f) * open, boxH = 16.0f + 122.5f * open;
            boolean overBox = mx > x && mx < x + boxW && my > y + 3.0f && my < y + 3.0f + boxH;
            c.fill(x, y + 3.0f, boxW, boxH, 5.0f, boxFade.color(overBox));
            c.ring(x, y + 3.0f, boxW, boxH, 5.0f, 0x40252525);
            if (open > 0.1f) {
                String title = edit("help").toUpperCase(Locale.ROOT).replace("", " ").trim();
                centered(c, title, MenuCanvas.BOLD, 14, x + boxW / 2.0f, y + 5.0f, 0xFFBEC3BD);
                if (open > 0.9f) {
                    c.text(edit("description1"), MenuCanvas.MEDIUM, 13, x + 5.0f, y + 14.0f, 0xFFBEC3BD);
                    c.text(edit("description2"), MenuCanvas.MEDIUM, 13, x + 5.0f, y + 22.0f, 0xFFBEC3BD);
                    String[][] rows = {{"mouse1", "clickToSet"}, {"mouse2", "clickToErase"}, {"del", "clearAll"}, {"tab", "showPreview"},
                        {"s", "toggleGuides"}, {"shift", "lockMode"}, {"z", "undo"}, {"y", "redo"}};
                    for (int i = 0; i < rows.length; i++) {
                        keyBox(c, edit(rows[i][0]), x + 5.0f, y + 34.0f + i * 11.0f);
                        c.text(edit(rows[i][1]), MenuCanvas.MEDIUM, 13, x + 40.0f, y + 34.0f + i * 11.0f, 0xFFBEC3BD);
                    }
                    keyBox(c, edit("w"), x + 5.0f, y + 122.0f);
                    String label = edit("mirrorMode"), mode = "(" + edit(mirror.id) + ")";
                    float shift2 = c.textWidth(label, MenuCanvas.MEDIUM, 13) - c.textWidth(mode, MenuCanvas.MEDIUM, 13);
                    c.text(label, MenuCanvas.MEDIUM, 13, x + 40.0f, y + 122.0f, 0xFFBEC3BD);
                    c.text(mode, MenuCanvas.MEDIUM, 13, x + 40.0f + shift2 / 2.0f - 1.0f, y + 131.0f, 0xFF67EBE9);
                }
            } else {
                centered(c, "?", MenuCanvas.MEDIUM, 18, x + boxW / 2.0f, y + 5.5f, 0xFFBEC3BD);
            }
            float by = y + 7.0f + boxH - (open == 0.0f ? 19 : 0), bs = 16.0f;
            boolean inRow = my >= by && my <= by + bs - 1.0f;
            float rx = x + w - 272.0f;
            boolean overReset = inRow && mx >= rx && mx <= rx + 15.0f;
            if (overReset) tooltip = edit("resetDescription");
            c.fill(rx, by, bs, bs, 5.0f, resetFade.color(overReset));
            c.ring(rx, by, bs, bs, 5.0f, 0x40252525);
            c.asset("icons/reset-settings-24x24.png", rx + 2.0f, by + 2.0f, 12.0f, 12.0f, 0xFFBEC3BD);
            float sx = rx + bs + 2.0f;
            boolean overSave = inRow && mx >= sx && mx <= sx + 15.0f;
            if (overSave) tooltip = edit("saveDescription");
            c.fill(sx, by, bs, bs, 5.0f, saveFade.color(overSave));
            c.ring(sx, by, bs, bs, 5.0f, 0x40252525);
            c.asset("icons/share-24.png", sx + 2.0f, by + 2.0f, 12.0f, 12.0f, 0xFFBEC3BD);
            float lx = sx + bs + 2.0f;
            boolean overLoad = inRow && mx >= lx && mx <= lx + 15.0f;
            if (overLoad) tooltip = edit("loadDescription");
            c.fill(lx, by, bs, bs, 5.0f, canLoad ? loadFade.color(overLoad) : 0x20000000);
            if (canLoad) c.ring(lx, by, bs, bs, 5.0f, 0x40252525);
            c.asset("icons/load-24.png", lx + 2.0f, by + 2.0f, 12.0f, 12.0f, canLoad ? 0xFFBEC3BD : 0xFF5E635D);
            float side = h - 5.5f;
            float gx = x + w / 2.0f - (h - 6.0f) / 2.0f + 20.0f;
            c.fill(gx, y + 3.0f, side, side, 5.0f, 0x20FFFFFF);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int at = i + j * n;
                    boolean on = grid.data[at], hot = false;
                    for (int t : targets) if (t == at) { hot = true; break; }
                    if (!on && !hot) continue;
                    int color = on ? 0xFFAAAAAA : 0x55AAAAAA;
                    if (hot && on) color = 0xFFCCCCCC;
                    boolean tl = i == 0 && j == 0, tr = i == n - 1 && j == 0, bl = i == 0 && j == n - 1, br = i == n - 1 && j == n - 1;
                    if (tl || tr || bl || br) c.fill(gx + i * cell, y + 3.0f + j * cell, cell, cell, 5.0f, color, tl, tr, bl, br);
                    else c.rect(gx + i * cell, y + 3.0f + j * cell, cell, cell, color);
                }
            }
            for (int i = 1; i <= n; i++) {
                c.rect(gx + i * cell, y + 3.0f, 0.5f, h - 5.0f, 0x40252525);
                c.rect(gx, y + 3.0f + i * cell, h - 5.0f, 0.5f, 0x40252525);
            }
            c.ring(gx, y + 3.0f, side, side, 5.0f, 0x40252525);
            if (col >= 0 && col < n && row >= 0 && row < n) {
                boolean center = col == n / 2 && row == n / 2;
                float tx = x + w - 55.0f, ty = y + h - 11.0f;
                tx = segment(c, "(", tx, ty, 0xFFBEC3BD);
                tx = segment(c, String.valueOf(col), tx, ty, col == n / 2 ? 0xFF55FFFF : 0xFFBEC3BD);
                tx = segment(c, ",", tx, ty, 0xFFBEC3BD);
                tx = segment(c, String.valueOf(row), tx, ty, row == n / 2 ? 0xFF55FFFF : 0xFFBEC3BD);
                tx = segment(c, ")", tx, ty, 0xFFBEC3BD);
                if (center) {
                    tx = segment(c, " (", tx, ty, 0xFFBEC3BD);
                    tx = segment(c, "center", tx, ty, 0xFF55FFFF);
                    segment(c, ")", tx, ty, 0xFFBEC3BD);
                }
                if (guides) {
                    float cx = gx + col * cell + cell / 2.0f - 0.25f, cy = y + 3.0f + row * cell + cell / 2.0f - 0.25f;
                    c.rect(gx, cy, h - 6.5f, 0.5f, 0x3367EBE9);
                    c.rect(cx, y + 2.0f, 0.5f, h - 5.0f, 0x3367EBE9);
                }
            }
            if (guides) {
                float mid = y + 3.0f + n / 2.0f * cell, midX = gx + n / 2.0f * cell;
                c.rect(gx - 3.0f, mid, 2.0f, 0.5f, 0xFF67EBE9);
                c.rect(gx + h - 5.5f, mid, 2.0f, 0.5f, 0xFF67EBE9);
                c.rect(midX, y - 1.0f, 0.5f, 4.0f, 0xFF67EBE9);
                c.rect(midX, y + h - 3.5f, 0.5f, 4.0f, 0xFF67EBE9);
            }
            Graphics2D g = c.graphics();
            g.setColor(new Color(0x3367EBE9, true));
            float d = (2.5f * 2.0f - 0.75f) / 2.0f;
            g.fill(new Ellipse2D.Float(gx + n / 2.0f * cell - d / 2.0f, y + 3.0f + n / 2.0f * cell - d / 2.0f, d, d));
            if (tooltip != null) {
                List<String> lines = wrap(c, tooltip, 150.0f);
                float tw = lines.size() > 1 ? 150.0f : c.textWidth(tooltip, MenuCanvas.LIGHT, 12);
                c.fill(mx + 8.0f, my + 6.0f, tw + 10.0f, 8 * lines.size() + 5, 4.0f, 0x90000000);
                for (int i = 0; i < lines.size(); i++) c.text(lines.get(i).trim(), MenuCanvas.LIGHT, 12, mx + 12.5f, my + 9.0f + i * 8, -1);
            }
        }

        private static float segment(MenuCanvas c, String s, float x, float y, int color) {
            c.text(s, MenuCanvas.MEDIUM, 13, x, y, color);
            return x + c.textWidth(s, MenuCanvas.MEDIUM, 13);
        }

        private static void centered(MenuCanvas c, String s, String font, float size, float cx, float y, int color) {
            c.text(s, font, size, cx - c.textWidth(s, font, size) / 2.0f, y, color);
        }

        private static void keyBox(MenuCanvas c, String key, float x, float y) {
            c.fill(x, y, 31.0f, 9.0f, 2.0f, 0xFFCCCCCC);
            if (c.textWidth(key, MenuCanvas.MEDIUM, 13) >= 30.0f) centered(c, key, MenuCanvas.MEDIUM, 10, x + 15.5f, y + 1.0f, 0xFF222222);
            else centered(c, key, MenuCanvas.MEDIUM, 13, x + 15.5f, y, 0xFF222222);
        }

        private static List<String> wrap(MenuCanvas c, String text, float width) {
            List<String> out = new ArrayList<String>();
            for (String para : text.split("\n")) {
                StringBuilder line = new StringBuilder();
                for (String word : para.trim().split(" ")) {
                    String next = line.length() == 0 ? word : line + " " + word;
                    if (c.textWidth(next, MenuCanvas.LIGHT, 12) > width && line.length() > 0) {
                        out.add(line.toString());
                        line = new StringBuilder(word);
                    } else {
                        line = new StringBuilder(next);
                    }
                }
                out.add(line.toString());
            }
            return out;
        }

        @Override public void press(float mx, float my, int button) {
            pressed = true;
            pressX = mx;
            pressY = my;
            float x = lastX, y = lastY, w = lastW, h = lastH;
            float open = helpOpen();
            float boxW = 16.0f + (h - 87.0f) * open, boxH = 16.0f + 122.5f * open;
            if (mx > x && mx < x + boxW && my > y + 3.0f && my < y + 3.0f + boxH) {
                owner.crosshairDraw.set("false".equals(owner.crosshairDraw.get()) ? "true" : "false");
                help.start();
                return;
            }
            float rx = x + w - 272.0f, by = y + 7.0f + boxH - (open == 0.0f ? 19 : 0);
            if (my < by || my > by + 15.0f) return;
            if (mx >= rx && mx <= rx + 15.0f) {
                owner.setGrid(CrosshairGrid.parse(owner.defaultCode()));
                clearHistory();
            } else if (mx >= rx + 18.0f && mx <= rx + 31.0f) {
                owner.exportCode();
            } else if (mx >= rx + 36.0f && mx <= rx + 47.0f && canLoad) {
                owner.importCode();
                clearHistory();
            }
        }

        @Override public boolean key(char character, int code) {
            boolean changed = false, used = true;
            if (code == Keyboard.KEY_DELETE) changed = clearAll();
            else if (code == Keyboard.KEY_W) mirror = Mirror.values()[(mirror.ordinal() + 1) % Mirror.values().length];
            else if (code == Keyboard.KEY_S) guides = !guides;
            else if (Character.toLowerCase(character) == 'z') changed = replay(undo, redo);
            else if (Character.toLowerCase(character) == 'y' && !redo.isEmpty()) changed = replay(redo, undo);
            else used = false;
            if (changed) owner.gridChanged();
            return used;
        }

        @Override public boolean animating() { return true; }

        private boolean clearAll() {
            boolean[] data = owner.grid().data;
            List<int[]> changes = new ArrayList<int[]>();
            for (int i = 0; i < data.length; i++) if (data[i]) changes.add(new int[]{i, 1, 0});
            if (changes.isEmpty()) return false;
            Arrays.fill(data, false);
            push(undo, changes);
            return true;
        }

        private boolean replay(Deque<List<int[]>> from, Deque<List<int[]>> to) {
            if (from.isEmpty()) return false;
            List<int[]> changes = from.pop();
            List<int[]> back = new ArrayList<int[]>();
            boolean[] data = owner.grid().data;
            for (int[] ch : changes) {
                boolean old = ch[1] == 1;
                if (data[ch[0]] == old) continue;
                back.add(new int[]{ch[0], data[ch[0]] ? 1 : 0, old ? 1 : 0});
                data[ch[0]] = old;
            }
            if (back.isEmpty()) return false;
            push(to, back);
            return true;
        }

        private static void push(Deque<List<int[]>> stack, List<int[]> changes) {
            if (changes == null || changes.isEmpty()) return;
            stack.push(changes);
            while (stack.size() > 10) stack.removeLast();
        }

        private void clearHistory() {
            undo.clear();
            redo.clear();
            stroke = null;
        }
    }

    private static final class QuickFade {
        private boolean on;
        private long changed;
        int color(boolean hover) {
            long now = System.currentTimeMillis();
            if (hover != on) { on = hover; changed = now; }
            float t = Math.min(1.0f, (now - changed) / 125.0f);
            float k = hover ? t : 1.0f - t;
            int a = Math.round(0x20 + (0x45 - 0x20) * k);
            return a << 24 | 0xFFFFFF;
        }
    }

    static final class Java2DSurface implements CrosshairSurface {
        private static final Map<String, BufferedImage> TEXTURES = new HashMap<String, BufferedImage>();
        private static final Map<String, Object[]> TINTED = new HashMap<String, Object[]>();
        private final Graphics2D g;
        private final Deque<AffineTransform> stack = new ArrayDeque<AffineTransform>();

        Java2DSurface(Graphics2D g) { this.g = g; }

        @Override public void push() { stack.push(g.getTransform()); }
        @Override public void pop() { g.setTransform(stack.pop()); }
        @Override public void translate(float x, float y) { g.translate(x, y); }
        @Override public void scale(float x, float y) { g.scale(x, y); }
        @Override public void rotate(float degrees) { g.rotate(Math.toRadians(degrees)); }

        @Override public void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4, boolean invert) {
            Path2D.Float p = new Path2D.Float();
            p.moveTo(x1, y1);
            p.lineTo(x2, y2);
            p.lineTo(x3, y3);
            p.lineTo(x4, y4);
            p.closePath();
            g.setColor(new Color(c1, true));
            g.fill(p);
        }

        private static BufferedImage texture(String name) {
            BufferedImage img = TEXTURES.get(name);
            if (img == null) {
                try (InputStream in = Java2DSurface.class.getResourceAsStream("/assets/lunarforge/textures/crosshair/" + name + ".png")) {
                    img = ImageIO.read(in);
                } catch (Exception e) {
                    img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                }
                TEXTURES.put(name, img);
            }
            return img;
        }

        private static BufferedImage tint(String key, BufferedImage src, int argb) {
            Object[] held = TINTED.get(key);
            if (held != null && held[0] == src && (Integer)held[1] == argb) return (BufferedImage)held[2];
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            float a = (argb >>> 24) / 255.0f;
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int c = src.getRGB(x, y);
                    int alpha = Math.round((c >>> 24) * a);
                    int r = (c >> 16 & 255) * (argb >> 16 & 255) / 255, gr = (c >> 8 & 255) * (argb >> 8 & 255) / 255, b = (c & 255) * (argb & 255) / 255;
                    out.setRGB(x, y, alpha << 24 | r << 16 | gr << 8 | b);
                }
            }
            TINTED.put(key, new Object[]{src, argb, out});
            return out;
        }

        private void draw(BufferedImage img, float x, float y, float w, float h) {
            AffineTransform t = new AffineTransform();
            t.translate(x, y);
            t.scale(w / img.getWidth(), h / img.getHeight());
            g.drawImage(img, t, null);
        }

        @Override public void texture(String name, float x, float y, float w, float h, int argb, boolean invert) {
            draw(tint("t:" + name, texture(name), argb), x, y, w, h);
        }

        @Override public void image(String key, BufferedImage image, float x, float y, float w, float h, int tl, int tr, int br, int bl, boolean invert) {
            Object hint = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            draw(tint("i:" + key, image, tl), x, y, w, h);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, hint);
        }
    }
}
