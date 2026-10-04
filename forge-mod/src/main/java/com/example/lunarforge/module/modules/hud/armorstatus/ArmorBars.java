package com.example.lunarforge.module.modules.hud.armorstatus;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.modules.hud.ModuleArmorStatus;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ArmorBars extends Module {
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation GLINT = new ResourceLocation("minecraft", "textures/misc/enchanted_item_glint.png");
    private static final Map<ItemArmor.ArmorMaterial, Integer> DEFAULTS = new EnumMap<ItemArmor.ArmorMaterial, Integer>(ItemArmor.ArmorMaterial.class);
    static {
        DEFAULTS.put(ItemArmor.ArmorMaterial.LEATHER, -7644629);
        DEFAULTS.put(ItemArmor.ArmorMaterial.CHAIN, -9079435);
        DEFAULTS.put(ItemArmor.ArmorMaterial.IRON, -2565928);
        DEFAULTS.put(ItemArmor.ArmorMaterial.GOLD, -1062853);
        DEFAULTS.put(ItemArmor.ArmorMaterial.DIAMOND, -11608621);
    }

    private final ModuleArmorStatus status;
    private final BoolSetting smartArmorColors = bool("smartArmorColors", true);
    private final BoolSetting showGlint = bool("showGlint", true);
    private final BoolSetting lowDurabilityIndicator = bool("lowDurabilityIndicator", true);
    private final NumberSetting lowDurabilityThreshold = integer("lowDurabilityThreshold", 10, 1, 100);

    private static final class Point {
        final int color; final boolean glint, warning;
        Point(int color, boolean glint, boolean warning) { this.color = color; this.glint = glint; this.warning = warning; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Point)) return false;
            Point p = (Point)o;
            return p.color == color && p.glint == glint && p.warning == warning;
        }
        @Override public int hashCode() { return color * 31 + (glint ? 2 : 0) + (warning ? 1 : 0); }
    }

    private final Point[] points = new Point[20];
    private final Map<ItemArmor.ArmorMaterial, Integer> colors = new EnumMap<ItemArmor.ArmorMaterial, Integer>(ItemArmor.ArmorMaterial.class);
    private boolean lastSmart;

    private int iconBrightness = -1, splitX = 4;
    private boolean[] iconPixels = new boolean[81];

    public ArmorBars(ModuleArmorStatus status) {
        super("ARMORSTATUS_BARS_CHILD", false);
        this.status = status;
    }

    @Override protected void layout(Page page) {
        page.add(smartArmorColors, showGlint);
        page.group(lowDurabilityIndicator, g -> g.add(lowDurabilityThreshold));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Arrays.fill(points, null);
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !isEnabled()) return;
        boolean smart = smartArmorColors.on();
        if (smart != lastSmart) { colors.clear(); lastSmart = smart; }
        int total = Math.min(points.length, player.getTotalArmorValue()), n = 0;

        for (int slot = 3; slot >= 0; slot--) {
            ItemStack stack = player.inventory.armorInventory[slot];
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) continue;
            ItemArmor armor = (ItemArmor)stack.getItem();
            int value = armor.damageReduceAmount;
            if (value <= 0) continue;
            Point p = new Point(color(armor.getArmorMaterial(), smart), showGlint.on() && stack.isItemEnchanted(),
                lowDurabilityIndicator.on() && ModuleArmorStatus.lowDurability(stack, lowDurabilityThreshold.intValue()));
            for (int i = value; i > 0 && n < total; --i) points[n++] = p;
        }
        Point plain = new Point(-1, false, false);
        while (n < total) points[n++] = plain;
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ARMOR || !isEnabled() || points[0] == null) return;
        event.setCanceled(true);
        ScaledResolution res = event.resolution;
        int left = res.getScaledWidth() / 2 - 91, top = res.getScaledHeight() - GuiIngameForge.left_height;
        GlStateManager.enableBlend();
        for (int i = 0; i < 10; i++) icon(i, left + i * 8, top);
        GuiIngameForge.left_height += 10;
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICONS);
    }

    private enum Part { LEFT, RIGHT, WHOLE }

    private void icon(int n, int x, int y) {
        Point a = points[n * 2], b = n * 2 + 1 < points.length ? points[n * 2 + 1] : null;
        if (a == null || a.equals(b)) draw(x, y, Part.WHOLE, a);
        else { draw(x, y, Part.LEFT, a); draw(x, y, Part.RIGHT, b); }
    }

    private void draw(int x, int y, Part part, Point point) {
        analyseIcon();
        int from = part == Part.RIGHT ? splitX : 0, to = part == Part.LEFT ? splitX : 9;
        Draw.blit(ICONS, x + from, y, 16 + from, 9, to - from, 9, 256, 256, 0xFFFFFFFF);
        if (point == null) return;
        int color = point.color;
        float pulse = 1;
        if (point.warning) {
            double t = (System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2;
            pulse = (float)Math.sin(t) * 0.5f + 0.5f;
            color = (int)((color >>> 24) * pulse) << 24 | color & 0xFFFFFF;
        }
        Draw.blit(ICONS, x + from, y, 34 + from, 9, to - from, 9, 256, 256, tint(color));
        if (point.glint) glint(x, y, from, to, pulse);
    }

    private int tint(int color) {
        int alpha = color >>> 24;
        if (iconBrightness <= 0) return alpha << 24 | 0xFFFFFF;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255, max = Math.max(r, Math.max(g, b));
        float f = Math.min(255.0f / iconBrightness, 255.0f / Math.max(1, max));
        return alpha << 24 | (int)(r * f) << 16 | (int)(g * f) << 8 | (int)(b * f);
    }

    private void glint(int x, int y, int from, int to, float strength) {
        float f = strength >= 1 ? 1 : (float)Math.sqrt(Math.max(0, strength));
        int color = strength >= 1 ? 0xFF000000 | 9468104 : 0xFF000000 | (int)(144 * f) << 16 | (int)(120 * f) << 8 | (int)(200 * f);
        long now = Minecraft.getSystemTime();
        float s1 = Math.floorMod(now, 13750L) / 13750.0f * 96.0f, s2 = Math.floorMod(now, 3750L) / 3750.0f * 96.0f;
        GlStateManager.blendFunc(768, 1);
        for (int row = 0; row < 9; row++) {
            int start = -1;
            for (int col = from; col <= to; col++) {
                boolean set = col < to && iconPixels[col + row * 9];
                if (set && start < 0) { start = col; continue; }
                if (set || start < 0) continue;
                Draw.blit(GLINT, x + start, y + row, start + s1, row + 96 - s2, col - start, 1, 96, 96, color);
                Draw.blit(GLINT, x + start, y + row, start + 96 - s1, row + s2, col - start, 1, 96, 96, color);
                start = -1;
            }
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    }

    private void analyseIcon() {
        if (iconBrightness >= 0) return;
        iconBrightness = 0;
        BufferedImage image = image(ICONS);
        if (image == null) return;
        int scale = Math.max(1, image.getWidth() / 256), ox = 34 * scale, oy = 9 * scale;
        iconBrightness = brightness(image, ox, oy, 9 * scale);
        if (iconBrightness == 0) return;
        int min = 9, max = -1;
        for (int i = 0; i < 9; i++) for (int j = 0; j < 9; j++) {
            int px = ox + i * scale, py = oy + j * scale;
            boolean set = px < image.getWidth() && py < image.getHeight() && (image.getRGB(px, py) >>> 24) >= 128;
            iconPixels[i + j * 9] = set;
            if (set) { min = Math.min(min, i); max = Math.max(max, i); }
        }
        splitX = min <= max ? (min + max + 1) / 2 : 4;
    }

    private static int brightness(BufferedImage image, int x, int y, int size) {
        int x2 = Math.min(x + size, image.getWidth()), y2 = Math.min(y + size, image.getHeight());
        int[] values = new int[Math.max(0, x2 - x) * Math.max(0, y2 - y)];
        int n = 0;
        for (int i = x; i < x2; i++) for (int j = y; j < y2; j++) {
            int c = image.getRGB(i, j);
            if ((c >>> 24) < 128) continue;
            values[n++] = Math.max(c >> 16 & 255, Math.max(c >> 8 & 255, c & 255));
        }
        if (n == 0) return 0;
        Arrays.sort(values, 0, n);
        return Math.max(1, values[Math.round((n - 1) * 0.8f)]);
    }

    private int color(ItemArmor.ArmorMaterial material, boolean smart) {
        Integer cached = colors.get(material);
        if (cached != null) return cached;
        int c = smart ? smartColor(material) : DEFAULTS.containsKey(material) ? DEFAULTS.get(material) : 0;
        colors.put(material, c);
        return c;
    }

    private int smartColor(ItemArmor.ArmorMaterial material) {
        String name = material == ItemArmor.ArmorMaterial.CHAIN ? "chainmail" : material.name().toLowerCase(java.util.Locale.ROOT);
        for (String piece : new String[]{"chestplate", "helmet", "leggings", "boots"}) {
            BufferedImage image = image(new ResourceLocation("minecraft", "textures/items/" + name + "_" + piece + ".png"));
            int c;
            if (image == null || (c = average(image)) == 0) continue;
            if (material == ItemArmor.ArmorMaterial.LEATHER)
                c = 0xFF000000 | (c >> 16 & 255) * 138 / 255 << 16 | (c >> 8 & 255) * 87 / 255 << 8 | (c & 255) * 48 / 255;
            Integer fixed = DEFAULTS.get(material);
            if (fixed == null || fixed == 0) return c;
            float value = Math.max(fixed >> 16 & 255, Math.max(fixed >> 8 & 255, fixed & 255)) / 255.0f;
            float[] hsb = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
            return Color.HSBtoRGB(hsb[0], hsb[1], value);
        }
        return DEFAULTS.containsKey(material) ? DEFAULTS.get(material) : 0;
    }

    private static int average(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight(), n = 0;
        float[] values = new float[w * h];
        long r = 0, g = 0, b = 0, weight = 0;
        for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
            int c = image.getRGB(i, j);
            if ((c >>> 24) < 128) continue;
            int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255, max = Math.max(cr, Math.max(cg, cb));
            values[n++] = max / 255.0f;
            int wgt = 1 + max - Math.min(cr, Math.min(cg, cb));
            r += (long)cr * wgt; g += (long)cg * wgt; b += (long)cb * wgt; weight += wgt;
        }
        if (weight == 0) return 0;
        float[] hsb = Color.RGBtoHSB((int)(r / weight), (int)(g / weight), (int)(b / weight), null);
        Arrays.sort(values, 0, n);
        return Color.HSBtoRGB(hsb[0], Math.min(1.0f, hsb[1] * 1.25f), values[Math.round((n - 1) * 0.8f)]);
    }

    private static BufferedImage image(ResourceLocation location) {
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }
}
