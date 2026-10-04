package com.example.lunarforge.module.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;

public final class TooltipHooks {
    public interface Component { void draw(int x, int y); }

    private static final List<Component> COMPONENTS = new ArrayList<Component>();
    private static final char BASE = '';

    private TooltipHooks() {}

    public static void register() {
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(new Object() {
            @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
            public void frame(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
                if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START) COMPONENTS.clear();
            }
        });
    }

    public static void insert(List<String> lines, int index, int width, int height, Component component) {
        int id = COMPONENTS.size();
        COMPONENTS.add(component);
        StringBuilder blank = new StringBuilder();
        for (int i = 0; i < (width + 3) / 4; i++) blank.append(' ');
        int rows = Math.max(1, (height + 9) / 10);
        index = Math.min(index, lines.size());
        lines.add(index, blank + "§" + (char)(BASE + id));
        for (int i = 1; i < rows; i++) lines.add(index + i, blank.toString());
    }

    private static int depth;
    private static boolean pushed;

    public static void begin(List<String> lines, int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth, FontRenderer font) {
        pushed = false;
        if (lines == null || lines.isEmpty() || depth++ > 0) return;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.getStringWidth(line));
        boolean wrap = false;
        int x = mouseX + 12, count = lines.size(), titleLines = 1;
        if (x + width + 4 > screenWidth) {
            x = mouseX - 16 - width;
            if (x < 4) { width = mouseX > screenWidth / 2 ? mouseX - 12 - 8 : screenWidth - 16 - mouseX; wrap = true; }
        }
        if (maxTextWidth > 0 && width > maxTextWidth) { width = maxTextWidth; wrap = true; }
        if (wrap) {
            int wrapped = 0;
            count = 0;
            for (int i = 0; i < lines.size(); i++) {
                List<String> parts = font.listFormattedStringToWidth(lines.get(i), width);
                if (i == 0) titleLines = parts.size();
                for (String part : parts) { wrapped = Math.max(wrapped, font.getStringWidth(part)); count++; }
            }
            width = wrapped;
            x = mouseX > screenWidth / 2 ? mouseX - 16 - width : mouseX + 12;
        }
        int y = mouseY - 12, height = 8;
        if (count > 1) { height += (count - 1) * 10; if (count > titleLines) height += 2; }
        if (y + height + 6 > screenHeight) y = screenHeight - height - 6;
        float[] at = com.example.lunarforge.module.modules.visual.ModuleScrollableTooltips.position(x, y, width, height);
        if (at == null) return;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(at[0], at[1], 0);
        net.minecraft.client.renderer.GlStateManager.scale(at[2], at[2], 1);
        net.minecraft.client.renderer.GlStateManager.translate(-x, -y, 0);
        pushed = true;
    }

    public static void end() {
        if (depth > 0) depth--;
        if (depth == 0 && pushed) {
            pushed = false;
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    public static int drawLine(FontRenderer font, String text, float x, float y, int color) {
        int at = text.lastIndexOf('§');
        if (at >= 0 && at + 1 < text.length()) {
            int id = text.charAt(at + 1) - BASE;
            if (id >= 0 && id < COMPONENTS.size()) {
                COMPONENTS.get(id).draw((int)x, (int)y);
                return (int)x;
            }
        }
        return font.drawStringWithShadow(text, x, y, color);
    }
}
