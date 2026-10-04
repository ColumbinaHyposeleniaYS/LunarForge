package com.example.lunarforge.gui.locker;

import static com.example.lunarforge.gui.locker.LockerStyle.*;

import com.example.lunarforge.cosmetics.Display;
import com.example.lunarforge.cosmetics.emote.Emotes;
import com.example.lunarforge.cosmetics.render.Mannequin;
import com.example.lunarforge.cosmetics.render.ModelView;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class EmoteWheelScreen extends GuiScreen {
    private static final int PER_PAGE = 8;
    private static final float R = 240, HOLE = 97;
    private static final int BLUE_BG = rgba(0, 171, 252, .24f), BLUE_BG_HOVER = rgba(0, 171, 252, .32f), BLUE = 0xFF27CBFF;
    private final KeyBinding key;
    private final Mannequin[] models = new Mannequin[PER_PAGE];
    private int page, hovered = -1;
    private String hoverKey = "";
    private float scale, cw, ch;
    private DynamicTexture texture;
    private ResourceLocation location;
    private int texW, texH;
    private boolean released;

    public EmoteWheelScreen(KeyBinding key) { this.key = key; }

    private long opened;

    @Override public void initGui() { Emotes.ready(); hoverKey = ""; if (opened == 0) opened = System.currentTimeMillis(); }

    private float fade() { float t = Math.min(1f, (System.currentTimeMillis() - opened) / 150f); return t * t; }

    private List<Emotes.Emote> slots() { return Emotes.equipped(); }
    private int pages() { return Math.max(1, (slots().size() + PER_PAGE - 1) / PER_PAGE); }
    private Emotes.Emote slot(int i) { int k = page * PER_PAGE + i; List<Emotes.Emote> l = slots(); return k < l.size() ? l.get(k) : null; }

    private float top() { return (ch - 773) / 2; }
    private float wheelX() { return (cw - 480) / 2; }
    private float wheelY() { return top() + 87; }

    private static float[] at(int slot, float c, float d) {
        double a = Math.toRadians(slot * 45 - 90);
        double h = R - c, x = R + h * Math.cos(a), y = R + h * Math.sin(a);
        return new float[]{(float)(x - d * Math.sin(a)), (float)(y + d * Math.cos(a))};
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        page = Math.max(0, Math.min(page, pages() - 1));
        scale = Math.min(1f, Math.min(mc.displayWidth / 520f, mc.displayHeight / 800f));
        cw = mc.displayWidth / scale; ch = mc.displayHeight / scale;
        float mx = Mouse.getX() / scale, my = (mc.displayHeight - Mouse.getY() - 1) / scale;
        float dx = mx - wheelX() - R, dy = my - wheelY() - R, dist = (float)Math.sqrt(dx * dx + dy * dy);
        hovered = -1;
        if (dist > HOLE && dist < R) {
            double a = Math.toDegrees(Math.atan2(dy, dx)) + 90;
            int i = (int)Math.round(((a % 360) + 360) % 360 / 45) % PER_PAGE;
            if (slot(i) != null) hovered = i;
        }
        String keyNow = hovered + ":" + page + ":" + pages() + ":" + Emotes.revision + ":" + hoverButton(mx, my) + ":" + mc.displayWidth + "x" + mc.displayHeight;
        if (!keyNow.equals(hoverKey) || location == null) { hoverKey = keyNow; upload(paint(mx, my)); }

        int factor = new ScaledResolution(mc).getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale / factor, scale / factor, 1);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        float alpha = fade();

        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(location);
        GlStateManager.pushMatrix();
        GlStateManager.scale(cw / texW, ch / texH, 1);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, texW, texH, texW, texH);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.color(1, 1, 1, 1);

        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        for (int i = 0; i < PER_PAGE; i++) {
            Emotes.Emote e = slot(i);
            if (e == null) continue;
            if (models[i] == null) models[i] = Mannequin.astronaut();
            if (Emotes.playing(models[i]) != e) Emotes.play(models[i], e);
            float[] p = at(i, 75, 0);
            float x = wheelX() + p[0] - 45, y = wheelY() + p[1] - 45;
            ModelView.draw(models[i], Display.FRONT, x, y, 90, 90, 1f, 0, 0, 0, 0);
            if (i != hovered || alpha < 1) {
                GlStateManager.enableDepth();
                GlStateManager.depthFunc(GL11.GL_GREATER);
                GlStateManager.depthMask(false);
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, -900);
                int a = Math.round(255 * (i != hovered ? 1 - .5f * alpha : 1 - alpha));
                Gui.drawRect((int)x, (int)y, (int)(x + 90), (int)(y + 90), a << 24 | 0x13141A);
                GlStateManager.popMatrix();
                GlStateManager.depthMask(true);
                GlStateManager.depthFunc(GL11.GL_LEQUAL);
            }
        }
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private String hoverButton(float mx, float my) {
        float[] b = equipButton();
        if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return "equip";
        float[] p = pill();
        if (my >= p[1] && my < p[1] + 37) { if (mx >= p[0] && mx < p[0] + 40) return "prev"; if (mx >= p[0] + p[2] - 40 && mx < p[0] + p[2]) return "next"; }
        return "";
    }

    private float[] equipButton() { return new float[]{(cw - 199) / 2, top() + 737, 199, 36}; }
    private float[] pill() { return new float[]{(cw - 155) / 2, top(), 155, 37}; }

    private BufferedImage paint(float mx, float my) {
        int w = mc.displayWidth, h = mc.displayHeight;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.scale(scale, scale);

        AffineTransform ellipse = new AffineTransform();
        ellipse.translate(cw / 2, ch);
        ellipse.scale(.6222 * cw, .6222 * ch);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), 1, new Point2D.Float(0, 0), new float[]{0, 1},
            new Color[]{new Color(0, 0, 0, 161), new Color(0, 0, 0, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE,
            MultipleGradientPaint.ColorSpaceType.SRGB, ellipse));
        g.fill(new Rectangle2D.Float(0, 0, cw, ch));

        float[] p = pill();
        box(g, p[0], p[1], p[2], p[3], 8, WINDOW);
        icon(g, "wheel/left.png", p[0] + 12, p[1] + 8, 21, 20, 0, page > 0 ? 1 : .35f);
        String label = "Page " + (page + 1);
        float lw = width(g, label, 700, 16);
        text(g, label, p[0] + 45 + (65 - lw) / 2, p[1] + 8, 700, 16, SPACE_11);
        icon(g, "wheel/right.png", p[0] + 122, p[1] + 8, 21, 20, 0, page < pages() - 1 ? 1 : .35f);

        float wx = wheelX(), wy = wheelY();
        Area ring = new Area(new Ellipse2D.Float(wx, wy, 2 * R, 2 * R));
        ring.subtract(new Area(new Ellipse2D.Float(wx + R - HOLE, wy + R - HOLE, 2 * HOLE, 2 * HOLE)));
        color(g, WINDOW);
        g.fill(ring);
        if (hovered >= 0) hover(g, wx, wy, hovered);
        for (int i = 0; i < PER_PAGE; i++) {
            float[] n = at(i, 30, -60);
            String s = String.valueOf(page * PER_PAGE + i + 1);
            float sw = width(g, s, 700, 20);
            text(g, s, wx + n[0] - sw / 2, wy + n[1] - lineHeight(g, 700, 20) / 2, 700, 20, SPACE_11);
        }
        icon(g, "wheel/logo.png", wx + R - 39, wy + R - 36.5f, 78, 73, 0, 1);

        if (hovered >= 0) {
            String name = slot(hovered).name;
            float nw = width(g, name, 700, 32);
            text(g, name, (cw - nw) / 2, top() + 617, 700, 32, SPACE_12);
        } else if (slots().isEmpty()) {
            String hint = "Equip emotes to add them to your wheel";
            float nw = width(g, hint, 500, 16);
            text(g, hint, (cw - nw) / 2, top() + 627, 500, 16, SPACE_11);
        }

        float[] b = equipButton();
        boolean bh = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
        box(g, b[0], b[1], b[2], b[3], 8, bh ? BLUE_BG_HOVER : BLUE_BG);
        icon(g, "wheel/edit.png", b[0] + 35, b[1] + 10, 17, 16, 0, 1);
        text(g, "Equip Emotes", b[0] + 60, b[1] + 7, 500, 16, BLUE);
        g.dispose();
        return img;
    }

    private static void hover(Graphics2D g, float wx, float wy, int slot) {
        double mid = slot * 45 - 90, cx = wx + R, cy = wy + R;
        Area wedge = new Area(new Arc2D.Double(cx - R, cy - R, 2 * R, 2 * R, -mid - 22.5, 45, Arc2D.PIE));
        wedge.subtract(new Area(new Ellipse2D.Double(cx - HOLE, cy - HOLE, 2 * HOLE, 2 * HOLE)));
        double a = Math.toRadians(mid);
        Point2D.Double outer = new Point2D.Double(cx + R * Math.cos(a), cy + R * Math.sin(a));
        Point2D.Double inner = new Point2D.Double(cx + (HOLE + 1) * Math.cos(a), cy + (HOLE + 1) * Math.sin(a));
        g.setPaint(new GradientPaint(outer, new Color(255, 255, 255, 0), inner, new Color(255, 255, 255, 38)));
        g.fill(wedge);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        double ar = 98.5;
        g.draw(new Arc2D.Double(cx - ar, cy - ar, 2 * ar, 2 * ar, -mid - 20.8, 41.6, Arc2D.OPEN));
        g.setStroke(old);

        AffineTransform t = g.getTransform();
        g.translate(cx, cy);
        g.rotate(a + Math.PI / 2);
        Path2D.Float tri = new Path2D.Float();
        tri.moveTo(0, -106.5); tri.lineTo(5.5, -99.5); tri.lineTo(-5.5, -99.5); tri.closePath();
        g.setColor(new Color(0xEDEEF3));
        g.fill(tri);
        g.setTransform(t);
    }

    private void upload(BufferedImage img) {
        if (texture == null || texW != img.getWidth() || texH != img.getHeight()) {
            if (location != null) mc.getTextureManager().deleteTexture(location);
            texW = img.getWidth(); texH = img.getHeight();
            texture = new DynamicTexture(texW, texH);
            location = mc.getTextureManager().getDynamicTextureLocation("lunarforge_emote_wheel", texture);
        }
        img.getRGB(0, 0, texW, texH, texture.getTextureData(), 0, texW);
        texture.updateDynamicTexture();
    }

    @Override public void updateScreen() {
        if (!released && key != null && key.getKeyCode() > 0 && !Keyboard.isKeyDown(key.getKeyCode())) { released = true; choose(); }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) return;
        float mx = Mouse.getX() / scale, my = (mc.displayHeight - Mouse.getY() - 1) / scale;
        switch (hoverButton(mx, my)) {
            case "equip": mc.displayGuiScreen(LockerScreen.emotes(null)); return;
            case "prev": if (page > 0) page--; return;
            case "next": if (page < pages() - 1) page++; return;
            default: if (hovered >= 0) choose();
        }
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d < 0) page = Math.min(pages() - 1, page + 1); else if (d > 0) page = Math.max(0, page - 1);
    }

    private void choose() {
        Emotes.Emote e = hovered >= 0 ? slot(hovered) : null;
        if (e != null && mc.thePlayer != null) Emotes.play(mc.thePlayer, e);
        mc.displayGuiScreen(null);
    }

    @Override public void onGuiClosed() {
        if (location != null) mc.getTextureManager().deleteTexture(location);
        for (Mannequin m : models) if (m != null) Emotes.stop(m);
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
