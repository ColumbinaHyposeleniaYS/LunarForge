package com.example.lunarforge.gui.locker;

import com.example.lunarforge.cosmetics.*;
import com.example.lunarforge.cosmetics.emote.Emotes;
import com.example.lunarforge.cosmetics.render.Mannequin;
import com.example.lunarforge.cosmetics.render.ModelView;
import com.example.lunarforge.gui.LunarNotifications;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class LockerScreen extends GuiScreen {
    private final LockerView view = new LockerView();
    private final GuiScreen parent;
    private Layer base, overlay;
    private float scale, left, top;
    private int factor;
    private String signature = "";
    private Mannequin astronaut, self;

    private String dragging;
    private float dragX, dragY, dragA, dragB;
    private int lastClickId;
    private long lastClickTime;

    public LockerScreen(GuiScreen parent) { this.parent = parent; }

    public static LockerScreen emotes(GuiScreen parent) {
        LockerScreen s = new LockerScreen(parent);
        s.view.tab = LockerView.Tab.EMOTES;
        Emotes.ready();
        return s;
    }

    private static final class Layer {
        DynamicTexture texture; ResourceLocation location; int w, h;
    }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        com.example.lunarforge.cosmetics.render.CosmeticLayers.install();
        astronaut = Mannequin.astronaut();
        self = Mannequin.self();
        layout();
    }

    private void layout() {
        factor = new ScaledResolution(mc).getScaleFactor();
        float dw = mc.displayWidth, dh = mc.displayHeight;
        scale = Math.min(1f, Math.min((dw - 32) / LockerView.BASE_W, (dh - 32) / LockerView.BASE_H));
        if (view.fullscreen) { view.w = dw / scale; view.h = dh / scale; view.winX = view.winY = 0; }
        else { view.w = LockerView.BASE_W; view.h = LockerView.BASE_H; }
        left = (dw - view.w * scale) / 2 + view.winX * scale;
        top = (dh - view.h * scale) / 2 + view.winY * scale;
        signature = "";
    }

    private float cssX() { return (Mouse.getX() - left) / scale; }
    private float cssY() { return (mc.displayHeight - Mouse.getY() - 1 - top) / scale; }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        float mx = cssX(), my = cssY();
        if (dragging != null) drag(mx, my);

        if (Math.abs(view.scrollTarget - view.scroll) > .5f) view.scroll += (view.scrollTarget - view.scroll) * .35f;
        else view.scroll = view.scrollTarget;

        LockerView.Hit hov = hitAt(mx, my, view.popoverOpen());
        String sig = (hov == null ? "-" : hov.action + "@" + (int)hov.rect.x + "," + (int)hov.rect.y) + ":" + view.gridClip.contains(mx, my) + ":" + view.scroll + ":" + view.sideScroll + ":" + Loadout.revision + ":" + stateKey()
            + (view.searchFocused ? ":" + System.currentTimeMillis() / 500 % 2 : "");
        if (!sig.equals(signature) || base == null) {
            signature = sig;
            base = upload(base, view.paint(scale, mx, my, false), "lunarforge_locker");
            overlay = view.popoverOpen() ? upload(overlay, view.paint(scale, mx, my, true), "lunarforge_locker_menu") : overlay;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(left / factor, top / factor, 0);
        GlStateManager.scale(scale / factor, scale / factor, 1);
        blit(base);
        models(partialTicks);
        if (view.popoverOpen() && overlay != null) {
            GlStateManager.translate(0, 0, 400);
            blit(overlay);
        }
        GlStateManager.popMatrix();
    }

    private String stateKey() {
        return view.tab + "|" + view.subType + "|" + view.search + "|" + view.sort + "|" + view.sortOpen + view.typeOpen + view.filterOpen
            + view.showMoreColors + view.showMoreTags + view.colorFilter + view.tagFilter + view.animatedFilter + view.scalableFilter + "|"
            + (view.selected == null ? 0 : view.selected.id) + "|" + (view.selectedEmote == null ? 0 : view.selectedEmote.id) + ":" + Emotes.revision + "|" + view.cosmeticsOpen + view.highlights + view.equippedList
            + view.itemOpen + view.itemType + view.itemMaterial + view.fullscreen + view.winX + "," + view.winY;
    }

    private Layer upload(Layer layer, BufferedImage img, String name) {
        if (layer == null || layer.w != img.getWidth() || layer.h != img.getHeight()) {
            if (layer != null) mc.getTextureManager().deleteTexture(layer.location);
            layer = new Layer();
            layer.w = img.getWidth(); layer.h = img.getHeight();
            layer.texture = new DynamicTexture(layer.w, layer.h);
            layer.location = mc.getTextureManager().getDynamicTextureLocation(name, layer.texture);
        }
        img.getRGB(0, 0, layer.w, layer.h, layer.texture.getTextureData(), 0, layer.w);
        layer.texture.updateDynamicTexture();
        return layer;
    }

    private void blit(Layer layer) {
        float pad = view.fullscreen ? 0 : LockerView.PAD;
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(layer.location);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-pad, -pad, 0);
        GlStateManager.scale((view.w + pad * 2) / layer.w, (view.h + pad * 2) / layer.h, 1);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, layer.w, layer.h, layer.w, layer.h);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
    }

    private void models(float partialTicks) {
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        List<LockerView.Slot> slots = new ArrayList<LockerView.Slot>(view.slots);
        for (LockerView.Slot s : slots) {
            if (s.kind.equals("tile")) {
                Display d = s.cosmetic.type.display;
                if (d == null) {
                    scissor(view.gridClip.x, view.gridClip.y, view.gridClip.width, view.gridClip.height, s.x, s.y, s.w, s.h);
                    com.example.lunarforge.cosmetics.render.GeckoCosmetics.preview(astronaut, s.cosmetic, s.x, s.y, s.w, s.h);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    continue;
                }
                astronaut.wear(java.util.Collections.singletonList(s.cosmetic));
                astronaut.setCurrentItemOrArmor(0, null);
                scissor(view.gridClip.x, view.gridClip.y, view.gridClip.width, view.gridClip.height, s.x, s.y, s.w, s.h);
                ModelView.draw(astronaut, d, s.x, s.y, s.w, s.h, 1f, 0, 0, 0, 0);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else if (s.kind.equals("loadout")) {
                List<Cosmetic> wear = new ArrayList<Cosmetic>(Loadout.equipped());
                Cosmetic sel = view.selected;
                if (sel != null && !Loadout.isEquipped(sel)) { wear.removeIf(c -> c.slot() == sel.slot()); wear.add(sel); }
                self.wear(wear);
                Emotes.Emote pe = view.tab == LockerView.Tab.EMOTES ? view.selectedEmote : null;
                if (pe == null) Emotes.stop(self);
                else if (Emotes.playing(self) != pe) { Emotes.ready(); Emotes.play(self, pe); }
                self.setCurrentItemOrArmor(0, heldItem(view.itemMaterial, view.itemType));
                scissor(s.x, s.y, s.w, s.h, s.x, s.y, s.w, s.h);
                ModelView.draw(self, Display.DUMMY, s.x, s.y, s.w, s.h, .65f * view.zoom, view.yaw, view.pitch, view.panX, view.panY);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
        for (LockerView.Slot s : slots) {
            if (s.kind.startsWith("emote:")) {
                Emotes.Emote e = Emotes.get(Integer.parseInt(s.kind.substring(6)));
                ResourceLocation icon = e == null ? null : CosmeticTextures.get(e.icon(), false);
                if (icon == null) continue;
                scissor(view.gridClip.x, view.gridClip.y, view.gridClip.width, view.gridClip.height, s.x, s.y, s.w, s.h);
                GlStateManager.enableBlend();
                GlStateManager.color(1, 1, 1, 1);
                mc.getTextureManager().bindTexture(icon);
                GlStateManager.pushMatrix();
                GlStateManager.translate(s.x, s.y, 200);
                GlStateManager.scale(s.w / 64f, s.h / 64f, 1);
                Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, 64, 64, 64, 64);
                GlStateManager.popMatrix();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else if (s.kind.startsWith("item:")) {
                String[] p = s.kind.split(":");
                ItemStack stack = heldItem(p[1], p[2]);
                if (stack == null) continue;
                GlStateManager.pushMatrix();
                GlStateManager.translate(s.x, s.y, 200);
                GlStateManager.scale(s.w / 16f, s.h / 16f, 1);
                RenderHelper.enableGUIStandardItemLighting();
                GlStateManager.color(1, 1, 1, .6f);
                itemRender.renderItemAndEffectIntoGUI(stack, 0, 0);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
            } else if (s.kind.equals("face") && mc.thePlayer != null) {
                GlStateManager.enableBlend();
                GlStateManager.color(1, 1, 1, 1);
                mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
                Gui.drawScaledCustomSizeModalRect((int)s.x, (int)s.y, 8, 8, 8, 8, (int)s.w, (int)s.h, 64, 64);
                Gui.drawScaledCustomSizeModalRect((int)s.x, (int)s.y, 40, 8, 8, 8, (int)s.w, (int)s.h, 64, 64);
            }
        }
        GlStateManager.disableDepth();
    }

    private static ItemStack heldItem(String material, String type) {
        if (material == null || type == null) return null;
        String m = material.equals("WOODEN") ? "wooden" : material.equals("GOLDEN") ? "golden" : material.toLowerCase();
        Item item = Item.getByNameOrId("minecraft:" + m + "_" + type.toLowerCase());
        return item == null ? null : new ItemStack(item);
    }

    private void scissor(float cx, float cy, float cw, float ch, float x, float y, float w, float h) {
        float x0 = Math.max(cx, x), y0 = Math.max(cy, y), x1 = Math.min(cx + cw, x + w), y1 = Math.min(cy + ch, y + h);
        if (x1 <= x0 || y1 <= y0) { x0 = x1 = y0 = y1 = 0; }
        int px = Math.round(left + x0 * scale), py = Math.round(top + y1 * scale);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px, mc.displayHeight - py, Math.max(0, Math.round((x1 - x0) * scale)), Math.max(0, Math.round((y1 - y0) * scale)));
    }

    private LockerView.Hit hitAt(float x, float y, boolean overlayOnly) {
        for (int i = view.hits.size() - 1; i >= 0; i--) {
            LockerView.Hit h = view.hits.get(i);
            if (overlayOnly && !h.overlay) continue;
            if (h.rect.contains(x, y)) return h;
        }
        return null;
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        float x = cssX(), y = cssY();
        signature = "";
        if (view.popoverOpen()) {
            LockerView.Hit h = hitAt(x, y, true);
            if (h == null) { view.sortOpen = view.typeOpen = view.filterOpen = false; return; }
            if (button == 0) action(h.action, x, y, button);
            return;
        }
        LockerView.Hit h = hitAt(x, y, false);
        if (h == null || !h.action.equals("search")) view.searchFocused = false;
        if (h == null) return;
        if (button == 1 && h.action.equals("loadoutModel")) { startDrag("pan", x, y, view.panX, view.panY); return; }
        if (button == 1 && h.action.equals("search")) { view.search = ""; return; }
        if (button == 0) action(h.action, x, y, button);
    }

    private void startDrag(String what, float x, float y, float a, float b) { dragging = what; dragX = x; dragY = y; dragA = a; dragB = b; }

    private void drag(float x, float y) {
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) { dragging = null; return; }
        switch (dragging) {
            case "window": view.winX = dragA + x - dragX; view.winY = dragB + y - dragY; layout(); break;
            case "rotate": view.yaw = dragA - (x - dragX) * .8f; view.pitch = Math.max(-60, Math.min(60, dragB - (y - dragY) * .5f)); break;
            case "pan": view.panX = dragA + (x - dragX); view.panY = dragB - (y - dragY); break;
            case "gridScroll": {
                float track = view.gridH(), content = view.maxScroll + track;
                float thumb = Math.max(36, track * track / content);
                view.scroll = view.scrollTarget = Math.max(0, Math.min(view.maxScroll, dragA + (y - dragY) * view.maxScroll / Math.max(1, track - thumb)));
                break;
            }
            case "sidebarScroll": view.sideScroll = Math.max(0, Math.min(view.sideMax, dragA + (y - dragY))); break;
            default: break;
        }
    }

    private void action(String a, float x, float y, int button) {
        String arg = a.contains(":") ? a.substring(a.indexOf(':') + 1) : "";
        String cmd = a.contains(":") ? a.substring(0, a.indexOf(':')) : a;
        switch (cmd) {
            case "drag": startDrag("window", x, y, view.winX, view.winY); break;
            case "close": mc.displayGuiScreen(null); break;
            case "expand":
                if (arg.equals("COSMETICS")) view.cosmeticsOpen = !view.cosmeticsOpen;
                break;
            case "tab":
                view.tab = LockerView.Tab.valueOf(arg); view.subType = null; view.scroll = view.scrollTarget = 0;
                if (view.tab == LockerView.Tab.EMOTES) Emotes.ready();
                break;
            case "type":
                view.tab = LockerView.Tab.COSMETICS; view.subType = CosmeticType.valueOf(arg); view.scroll = view.scrollTarget = 0;
                break;
            case "typePicker": view.typeOpen = !view.typeOpen; break;
            case "pickType":
                view.subType = arg.equals("ALL") ? null : CosmeticType.valueOf(arg); view.typeOpen = false; view.scroll = view.scrollTarget = 0;
                break;
            case "search": view.searchFocused = true; break;
            case "sortMenu": view.sortOpen = !view.sortOpen; break;
            case "pickSort": view.sort = arg; view.sortOpen = false; view.scroll = view.scrollTarget = 0; break;
            case "filterMenu": view.filterOpen = !view.filterOpen; break;
            case "filterColor": toggle(view.colorFilter, arg); break;
            case "filterTag": toggle(view.tagFilter, arg); break;
            case "filterAnimated": view.animatedFilter = !view.animatedFilter; break;
            case "filterScalable": view.scalableFilter = !view.scalableFilter; break;
            case "moreColors": view.showMoreColors = !view.showMoreColors; break;
            case "moreTags": view.showMoreTags = !view.showMoreTags; break;
            case "tile": clickTile(CosmeticCatalog.get(Integer.parseInt(arg))); break;
            case "toggleSelected":
                if (view.tab == LockerView.Tab.EMOTES) { if (view.selectedEmote != null) toggleEmote(view.selectedEmote); }
                else if (view.selected != null) Loadout.toggle(view.selected);
                break;
            case "emote": clickEmote(Emotes.get(Integer.parseInt(arg))); break;
            case "gridScroll": {
                float track = view.gridH(), content = view.maxScroll + track;
                float thumb = Math.max(36, track * track / content), thumbY = view.gridY() + (track - thumb) * view.scroll / Math.max(1, view.maxScroll);

                if (y < thumbY || y > thumbY + thumb)
                    view.scroll = view.scrollTarget = Math.max(0, Math.min(view.maxScroll, (y - view.gridY() - thumb / 2) * view.maxScroll / Math.max(1, track - thumb)));
                startDrag("gridScroll", x, y, view.scroll, 0);
                break;
            }
            case "sidebarScroll": startDrag("sidebarScroll", x, y, view.sideScroll, 0); break;
            case "loadoutModel": startDrag("rotate", x, y, view.yaw, view.pitch); break;
            case "highlights": view.highlights = !view.highlights; break;
            case "equippedList": view.equippedList = !view.equippedList; break;
            case "equippedPick": view.selected = CosmeticCatalog.get(Integer.parseInt(arg)); break;
            case "itemPicker": view.itemOpen = !view.itemOpen; break;
            case "material": view.itemMaterial = arg; break;
            case "itemType": view.itemType = arg; break;
            case "itemNone": view.itemType = null; view.itemOpen = false; break;
            case "swapOutfit": case "newFit":

                LunarNotifications.info("Not available in the Forge port yet.");
                break;
            default: break;
        }
    }

    private static void toggle(java.util.Set<String> set, String value) { if (!set.remove(value)) set.add(value); }

    private static void toggleEmote(Emotes.Emote e) {
        if (!Emotes.toggle(e)) LunarNotifications.info("The emote wheel is full (" + Emotes.WHEEL + "). Unequip one first.");
    }

    private void clickEmote(Emotes.Emote e) {
        if (e == null) return;
        long now = System.currentTimeMillis();
        if (lastClickId == -e.id && now - lastClickTime < 400) { lastClickTime = 0; toggleEmote(e); view.selectedEmote = e; return; }
        lastClickId = -e.id; lastClickTime = now;
        view.selectedEmote = view.selectedEmote != null && view.selectedEmote.id == e.id ? null : e;
    }

    private void clickTile(Cosmetic c) {
        if (c == null) return;
        long now = System.currentTimeMillis();
        boolean selectedNow = view.selected != null && view.selected.id == c.id;
        if (lastClickId == c.id && now - lastClickTime < 400) {
            lastClickTime = 0;
            boolean wasOn = Loadout.isEquipped(c);
            Loadout.toggle(c);
            view.selected = wasOn ? null : c;
            return;
        }
        lastClickId = c.id; lastClickTime = now;
        view.selected = selectedNow ? null : c;
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        float x = cssX(), y = cssY(), notches = wheel / 120f;
        signature = "";
        if (view.popoverOpen()) return;
        float lx = view.loadoutX();
        if (x >= lx && x < lx + 350 && y >= 76 && y < view.h - 76) {
            view.zoom = Math.max(.5f, Math.min(3f, view.zoom * (float)Math.pow(1.1, notches)));
        } else if (x < 256) {
            view.sideScroll = Math.max(0, Math.min(view.sideMax, view.sideScroll - notches * 40));
        } else if (view.gridClip.contains(x, y)) {
            view.scrollTarget = Math.max(0, Math.min(view.maxScroll, view.scrollTarget - notches * 100));
        }
    }

    @Override protected void keyTyped(char c, int key) throws IOException {
        signature = "";
        if (key == Keyboard.KEY_ESCAPE) {
            if (view.popoverOpen()) { view.sortOpen = view.typeOpen = view.filterOpen = false; return; }
            if (view.searchFocused) { view.searchFocused = false; return; }
            mc.displayGuiScreen(null);
            return;
        }
        if (isCtrlKeyDown() && key == Keyboard.KEY_F) { view.searchFocused = true; return; }
        if (!view.searchFocused) return;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { view.searchFocused = false; return; }
        String s = view.search;
        if (key == Keyboard.KEY_BACK) s = s.isEmpty() ? s : isCtrlKeyDown() ? "" : s.substring(0, s.length() - 1);
        else if (isCtrlKeyDown() && key == Keyboard.KEY_V) s += getClipboardString().replaceAll("[\\r\\n\\t]", "");
        else if (c >= 32 && c != 127 && !isCtrlKeyDown()) s += c;
        if (s.length() > 64) s = s.substring(0, 64);
        if (!s.equals(view.search)) { view.search = s; view.scroll = view.scrollTarget = 0; }
    }

    @Override public void onResize(net.minecraft.client.Minecraft mcIn, int w, int h) { super.onResize(mcIn, w, h); layout(); }

    @Override public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        for (Layer l : new Layer[]{base, overlay}) if (l != null) mc.getTextureManager().deleteTexture(l.location);
        base = overlay = null;
    }

    @Override public boolean doesGuiPauseGame() { return false; }
}
