package com.example.lunarforge.gui;

import com.example.lunarforge.feature.FeatureManager;
import com.example.lunarforge.gui.ui.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class LunarClickGui extends GuiScreen {
    private static final float PAGE_X = 110, PAGE_Y = 42.5f, PAGE_W = 377, PAGE_H = 255;
    private final FeatureManager features;
    private final KeyBinding openKey;
    private final UiModel model;
    private final UiRenderer renderer = new UiRenderer();
    private final UiViewport viewport = new UiViewport();
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private UiRenderer.Hit drag;
    private String previousSignature = "";
    private boolean selectAll;
    private long lastPaint;
    private int rasterScale;

    private final GuiScreen background;
    public LunarClickGui(FeatureManager features, KeyBinding openKey) { this(features, openKey, null); }
    public LunarClickGui(FeatureManager features, KeyBinding openKey, GuiScreen background) {
        this.features = features; this.openKey = openKey; this.model = features.ui(); this.background = background;
    }
    private void close() { mc.displayGuiScreen(mc.theWorld == null ? background : null); }
    @Override public void updateScreen() { if (mc.theWorld == null) com.example.lunarforge.gui.home.LunarHomeScreen.tickPanorama(); }
    @Override public void initGui() {
        viewport.resize(width, height);
        rasterScale = Math.max(2, Math.min(4, (int)Math.ceil(viewport.scale * new ScaledResolution(mc).getScaleFactor())));
        deleteTexture();
        model.closeRequested = false; model.editHudRequested = false; model.captureKey = false;
        model.inWorld = mc.theWorld != null;
        model.store.put("menuKeyName", Keyboard.getKeyName(openKey.getKeyCode()) == null ? "NONE" : Keyboard.getKeyName(openKey.getKeyCode()));
        KeyBinding wheel = emoteKey();
        if (wheel != null) model.store.put("emoteKeyName", wheel.getKeyCode() <= 0 ? "NONE" : Keyboard.getKeyName(wheel.getKeyCode()));
        Keyboard.enableRepeatEvents(true);
    }
    private boolean editing() { return model.searchFocused || !model.editorFocus.isEmpty() || !model.textEditing.isEmpty() || !model.hexEditing.isEmpty(); }
    private void paint(float mouseX, float mouseY, boolean force) {
        boolean blink = System.currentTimeMillis() / 500 % 2 == 0;
        LunarScroller sc = model.scroller;
        String signature = model.revision + ":" + (int)mouseX + ":" + (int)mouseY + ":" + (editing() && blink)
                + ":" + sc.offset + ":" + (renderer.animating ? System.nanoTime() : 0);
        long now = System.currentTimeMillis();

        boolean gliding = sc.gliding() || sc.dragging || drag != null;
        if (!force && (signature.equals(previousSignature) || (!gliding && now - lastPaint < 33))) return;
        BufferedImage image = renderer.render(model, mouseX, mouseY, rasterScale, blink);
        if (texture == null) {
            texture = new DynamicTexture(image.getWidth(), image.getHeight());
            textureLocation = mc.getTextureManager().getDynamicTextureLocation("lunarforge_menu", texture);
            texture.setBlurMipmap(true, false);
        }
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), texture.getTextureData(), 0, image.getWidth());
        texture.updateDynamicTexture(); previousSignature = signature; lastPaint = now;
    }
    private long toastSeen;
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (model.toastUntil != toastSeen) { toastSeen = model.toastUntil; if (!model.toast.isEmpty()) LunarNotifications.info(model.toast); }
        if (mc.theWorld == null) com.example.lunarforge.gui.home.LunarHomeScreen.drawBehind(background, this, partialTicks);
        if (model.flag("global", "menuDim", true)) drawRect(0, 0, width, height, 0x65000000);
        float x = viewport.x(mouseX), y = viewport.y(mouseY);

        LunarScroller sc = model.scroller;
        if (sc.dragging) { if (Mouse.isButtonDown(0)) sc.drag(y - renderer.scrollTop); else sc.dragging = false; }

        if (drag != null && drag.action.startsWith("slider:")) {
            if (Mouse.isButtonDown(0)) dragTo(x, y); else mouseReleased(mouseX, mouseY, 0);
        }
        sc.frame(overPage(x, y));
        paint(x, y, texture == null);
        GlStateManager.pushMatrix(); GlStateManager.translate(viewport.left, viewport.top, 0);
        GlStateManager.scale(viewport.scale, viewport.scale, 1);
        GlStateManager.disableDepth(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(textureLocation);
        int p = UiRenderer.PAD, w = UiRenderer.WIDTH + p * 2, h = UiRenderer.HEIGHT + p * 2;
        Gui.drawScaledCustomSizeModalRect(-p, -p, 0, 0, w * rasterScale, h * rasterScale, w, h, w * rasterScale, h * rasterScale);
        if (!model.keybindPopup.isEmpty() && model.editor.isEmpty()) {
            int far = 100000, dim = 0xD0000000;
            drawRect(-far, -far, far, -p, dim); drawRect(-far, h - p, far, far, dim);
            drawRect(-far, -p, -p, h - p, dim); drawRect(w - p, -p, far, h - p, dim);
        }
        GlStateManager.disableBlend(); GlStateManager.enableDepth(); GlStateManager.popMatrix();
    }
    private boolean overPage(float x, float y) {
        return model.editor.isEmpty() && model.keybindPopup.isEmpty()
            && x >= PAGE_X && x <= PAGE_X + PAGE_W && y > PAGE_Y && y < PAGE_Y + PAGE_H;
    }
    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        float x = viewport.x(mouseX), y = viewport.y(mouseY); paint(x, y, true);
        model.clickX = x; model.clickY = y;
        UiRenderer.Hit hit = renderer.hitAt(x, y);
        String action = hit == null ? "" : hit.action;
        if (!model.hexEditing.isEmpty() && !action.startsWith("hex:" + model.hexEditing)) commitHex();
        if (!model.textEditing.isEmpty() && !action.startsWith("editText:" + model.textEditing)) commitText();

        if (!model.keyListening.isEmpty() && button >= 2 && button <= 5) {
            String[] field = model.keyListening.split(":");
            model.set(field[0], field[1], "MOUSE" + (button + 1)); model.keyListening = ""; model.store.save(); return;
        }
        if (!model.editor.isEmpty()) {
            if (button == 0) model.action(hit == null ? "editorNone" : action);
            return;
        }
        if (hit == null) { model.searchFocused = false; model.revision++; return; }
        if (action.startsWith("widget:")) {
            int index = Integer.parseInt(action.substring(7));
            if (index < renderer.shownWidgets.size()) renderer.shownWidgets.get(index).press(x, y, button);
            model.revision++; return;
        }
        if (button == 1) {
            if (action.equals("search")) model.setSearch("");

            else if (action.startsWith("listenKey:") && !action.substring(10).equals(model.keyListening)) {
                String[] field = action.substring(10).split(":"); model.set(field[0], field[1], "NONE"); model.store.save();
            } else if (action.startsWith("keybindPopup:")) { model.set(action.substring(13), "toggleKeybind", "NONE"); model.store.save(); }
            else if (action.startsWith("listenKey:")) { model.keyListening = ""; model.revision++; }
            return;
        }
        if (button != 0) return;
        if (action.startsWith("listenKey:") && action.substring(10).equals(model.keyListening)) { model.keyListening = ""; model.revision++; return; }
        if (action.equals("scrollbar")) { model.scroller.dragging = true; model.scroller.drag(y - renderer.scrollTop); model.revision++; return; }
        if (action.startsWith("slider:")) {
            String[] parts = action.split(":");
            model.sliderHeld = parts[1] + "." + parts[2];
        }
        if (action.startsWith("slider:") || action.startsWith("sb:") || action.startsWith("hue:") || action.startsWith("alpha:")) { drag = hit; dragTo(x, y); return; }
        if (action.equals("videoSettings")) { mc.displayGuiScreen(new GuiVideoSettings(this, mc.gameSettings)); return; }
        model.action(action); selectAll = false;
        if (model.closeRequested) close();
        else if (model.editHudRequested) { model.editHudRequested = false; mc.displayGuiScreen(new LunarMovementScreen(features, openKey, background)); }
    }
    private void dragTo(float x, float y) {
        if (drag.action.startsWith("sb:") || drag.action.startsWith("hue:") || drag.action.startsWith("alpha:")) {
            String[] parts = drag.action.split(":");
            String id = parts[1], option = parts[2];
            int argb = (int)Long.parseLong(model.color(id, option, "FFFFFFFF"), 16);
            float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
            float hue = model.hue(id, option, argb), fx = Math.max(0, Math.min(1, (x - drag.rect.x) / drag.rect.width));
            float fy = Math.max(0, Math.min(1, (y - drag.rect.y) / drag.rect.height));
            int alpha = argb >>> 24;
            if (parts[0].equals("sb")) model.setHsb(id, option, hue, fx, 1 - fy, alpha);
            else if (parts[0].equals("hue")) model.setHsb(id, option, fy, hsb[1], hsb[2], alpha);
            else model.setHsb(id, option, hue, hsb[1], hsb[2], Math.round((1 - fy) * 255));
        } else {
            String[] parts = drag.action.split(":");
            float min = Float.parseFloat(parts[3]), max = Float.parseFloat(parts[4]), height = Float.parseFloat(parts[6]);
            boolean whole = parts[5].equals("int");
            double value = LunarSlider.valueAt(x, drag.rect.x, drag.rect.width, height, min, max, whole, isShiftKeyDown());
            String text = whole ? "" + Math.round(value) : "" + (float)value;
            if (!text.equals(model.value(parts[1], parts[2], null))) model.set(parts[1], parts[2], text);
        }
    }
    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) { if (drag != null) dragTo(viewport.x(x), viewport.y(y)); }
    @Override protected void mouseReleased(int x, int y, int button) {
        model.scroller.dragging = false;
        if (!model.sliderHeld.isEmpty()) { model.sliderHeld = ""; model.revision++; }
        if (drag == null) return;
        String[] parts = drag.action.split(":");
        if (parts[0].equals("sb") || parts[0].equals("hue") || parts[0].equals("alpha")) model.addRecent(model.color(parts[1], parts[2], "FFFFFFFF"));
        drag = null; model.store.save();
    }

    private void commitHex() {
        String[] field = model.hexEditing.split(":");
        String typed = model.input.toUpperCase(java.util.Locale.ROOT).replaceAll("[^0-9A-F]", "");
        if (!typed.isEmpty()) {
            while (typed.length() < 8) typed += "0";
            if (!model.hexAlpha) typed = "FF" + typed.substring(2);
            model.setColor(field[0], field[1], typed);
            model.addRecent(typed);
        }
        model.hexEditing = ""; model.input = ""; model.revision++; model.store.save();
    }
    private void commitText() {
        String[] field = model.textEditing.split(":");
        model.set(field[0], field[1], model.input);
        model.textEditing = ""; model.input = ""; model.revision++; model.store.save();
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            float x = viewport.x(Mouse.getEventX() * width / mc.displayWidth);
            float y = viewport.y(height - Mouse.getEventY() * height / mc.displayHeight - 1);
            if (overPage(x, y) && model.scroller.wheel(wheel)) model.revision++;
        }
    }

    private String type(String current, char character, int key, int max) {
        if (isCtrlKeyDown() && key == Keyboard.KEY_A) { selectAll = true; return current; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_C) { setClipboardString(current); return current; }
        String next;
        if (isCtrlKeyDown() && key == Keyboard.KEY_X) { setClipboardString(current); next = ""; }
        else if (isCtrlKeyDown() && key == Keyboard.KEY_V) next = (selectAll ? "" : current) + getClipboardString().replaceAll("[\\r\\n\\t]", "");
        else if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) next = selectAll ? "" : current.isEmpty() ? "" : current.substring(0, current.length() - 1);
        else if (character >= 32 && character != 127 && !isCtrlKeyDown()) next = (selectAll ? "" : current) + character;
        else return null;
        selectAll = false;
        return max > 0 && next.length() > max ? next.substring(0, max) : next;
    }

    private KeyBinding emoteKey() {
        for (KeyBinding k : mc.gameSettings.keyBindings) if (k.getKeyDescription().equals("key.lunarforge.emote_wheel")) return k;
        return null;
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (model.captureKey) {
            model.captureKey = false;
            if (key != Keyboard.KEY_ESCAPE && key != Keyboard.KEY_NONE && model.captureTarget.equals("emote")) {
                KeyBinding wheel = emoteKey();
                if (wheel != null) { wheel.setKeyCode(key); KeyBinding.resetKeyBindingArrayAndHash(); mc.gameSettings.saveOptions(); }
                model.store.put("emoteKeyName", Keyboard.getKeyName(key)); model.store.save();
            } else if (key != Keyboard.KEY_ESCAPE && key != Keyboard.KEY_NONE) {
                openKey.setKeyCode(key); KeyBinding.resetKeyBindingArrayAndHash(); mc.gameSettings.saveOptions();
                model.store.put("menuKeyName", Keyboard.getKeyName(key)); model.store.put("menuKey", "" + key); model.store.save();
            }
            model.revision++; return;
        }
        if (!model.editor.isEmpty()) {
            if (key == Keyboard.KEY_ESCAPE) { model.editorEscape(); model.store.save(); return; }
            if (model.editorFocus.isEmpty() || !model.confirm.isEmpty()) return;
            boolean name = model.editorFocus.equals("name");
            String next = type(name ? model.editorName : model.editorServer, character, key, name ? 20 : 60);
            if (next != null) model.editorType(next);
            return;
        }

        if (!model.keyListening.isEmpty()) {
            String[] field = model.keyListening.split(":");
            String name = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_NONE ? "NONE" : Keyboard.getKeyName(key);
            model.set(field[0], field[1], name == null ? "NONE" : name);
            model.keyListening = ""; model.store.save(); return;
        }
        if (!model.hexEditing.isEmpty()) {
            if (key == Keyboard.KEY_ESCAPE) { model.hexEditing = ""; model.input = ""; model.revision++; return; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { commitHex(); return; }
            if (key == Keyboard.KEY_BACK) { if (!model.input.isEmpty()) model.input = model.input.substring(0, model.input.length() - 1); }
            else if (Character.digit(character, 16) >= 0 && model.input.length() < 8) model.input += Character.toUpperCase(character);
            model.revision++; return;
        }
        if (!model.textEditing.isEmpty()) {
            if (key == Keyboard.KEY_ESCAPE) { model.textEditing = ""; model.input = ""; model.revision++; return; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { commitText(); return; }
            String next = type(model.input, character, key, model.textMax);
            if (next != null) { model.input = next; model.revision++; }
            return;
        }

        if (key != Keyboard.KEY_ESCAPE && !model.searchFocused) {
            for (MenuWidget w : renderer.shownWidgets) if (w.key(character, key)) { model.revision++; return; }
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (!model.keybindPopup.isEmpty()) model.action("keybindPopupDone");
            else if (!model.selected.isEmpty()) model.action("back"); else close();
            return;
        }
        if (key == openKey.getKeyCode() && !model.searchFocused) { close(); return; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_F) { model.searchFocused = true; model.revision++; return; }
        if (model.searchFocused) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { model.searchFocused = false; model.revision++; return; }
            String next = type(model.search, character, key, 64);
            if (next != null) model.setSearch(next);
        }
    }
    private void deleteTexture() {
        if (textureLocation != null) mc.getTextureManager().deleteTexture(textureLocation);
        texture = null; textureLocation = null; previousSignature = "";
    }
    @Override public void onGuiClosed() {
        if (!model.hexEditing.isEmpty()) commitHex();
        if (!model.textEditing.isEmpty()) commitText();
        model.keyListening = ""; model.keybindPopup = ""; model.scroller.velocity = 0;
        Keyboard.enableRepeatEvents(false); model.store.save(); deleteTexture(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
