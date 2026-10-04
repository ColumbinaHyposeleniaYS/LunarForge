package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.KeySetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public final class ModuleScrollableTooltips extends Module {
    private static ModuleScrollableTooltips instance;

    private final BoolSetting startAtTop = bool("startAtTop", true);
    private final BoolSetting verticalKeybind = bool("verticalKeybind", false);
    private final KeySetting horizontalScrollingKey = add(new KeySetting("horizontalScrollingKey", "LSHIFT", true));
    private final KeySetting scaleScrollingKey = add(new KeySetting("scaleScrollingKey", "LCONTROL", true));
    private final NumberSetting tooltipScale = decimal("tooltipScale", 1.0f, 0.25f, 2.5f);

    private final BoolSetting tooltipFreeScroll = bool("tooltipFreeScroll", false);
    private final BoolSetting lineShiftMode = bool("lineShiftMode", false);

    private static final class Anim {
        private double from, to;
        private long start, duration;
        Anim(double v) { from = to = v; }
        void set(double target, long ms) { from = value(); to = target; start = System.currentTimeMillis(); duration = ms; }
        double target() { return to; }
        boolean running() { return duration > 0 && System.currentTimeMillis() - start < duration; }
        boolean growing() { return to > from; }
        double value() {
            if (duration <= 0) return to;
            double t = (System.currentTimeMillis() - start) / (double)duration;
            return t >= 1.0 ? to : from + (to - from) * t;
        }
    }

    private final Anim offsetX = new Anim(0), offsetY = new Anim(0), scale = new Anim(1);
    private ItemStack stack;
    private int slot = -1, lineShift;
    private boolean positioned, shifted;
    private float lastWidth, lastHeight;

    public ModuleScrollableTooltips() {
        super("SCROLLABLE_TOOLTIPS", false);
        instance = this;
        tooltipFreeScroll.onChange(() -> { if (tooltipFreeScroll.on() && lineShiftMode.on()) lineShiftMode.set(false); });
        lineShiftMode.onChange(() -> { if (lineShiftMode.on() && tooltipFreeScroll.on()) tooltipFreeScroll.set(false); });
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(startAtTop, verticalKeybind, horizontalScrollingKey, scaleScrollingKey, tooltipScale,
            tooltipFreeScroll, lineShiftMode));
    }

    private enum Mode { VERTICAL, HORIZONTAL, SCALE }

    private Mode mode() {
        if (scaleScrollingKey.isDown()) return Mode.SCALE;
        if (verticalKeybind.on()) return horizontalScrollingKey.isDown() ? Mode.VERTICAL : Mode.HORIZONTAL;
        return horizontalScrollingKey.isDown() ? Mode.HORIZONTAL : Mode.VERTICAL;
    }

    private static int hoveredSlot() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        if (!(s instanceof GuiContainer)) return -1;
        net.minecraft.inventory.Slot slot = ((GuiContainer)s).getSlotUnderMouse();
        return slot == null ? -1 : slot.slotNumber;
    }

    private void reset() {
        positioned = false;
        lineShift = 0;
        offsetX.set(0, 0);
        offsetY.set(0, 0);
        scale.set(1, 0);
        lastWidth = lastHeight = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        if (!isEnabled()) return;
        ItemStack s = event.itemStack;
        int now = hoveredSlot();
        boolean same = s != null && stack != null && (s == stack || s.getItem() == stack.getItem() && now == slot);
        if (!same) reset();
        stack = s;
        slot = now;
        if (!lineShiftMode.on() || event.toolTip.isEmpty()) return;
        int n = Math.max(0, Math.min(lineShift, event.toolTip.size() - 1));
        lineShift = n;
        event.toolTip.subList(0, n).clear();
    }

    @SubscribeEvent
    public void onScroll(GuiScreenEvent.MouseInputEvent.Pre event) {
        int wheel = Mouse.getEventDWheel();
        if (!isEnabled() || wheel == 0 || stack == null) return;
        GuiScreen screen = event.gui;
        if (!(screen instanceof GuiContainer) || screen instanceof GuiContainerCreative) return;
        Mode mode = mode();
        if (!allowed(mode)) return;
        double amount = Math.signum(wheel);
        double d = amount * (Minecraft.getMinecraft().displayHeight / 100);
        switch (mode) {
            case VERTICAL:
                if (lineShiftMode.on()) {
                    lineShift = d > 0 ? lineShift - 1 : lineShift + 1;
                    if (lineShift < 0) lineShift = 0;
                    shifted = true;
                } else offsetY.set(offsetY.target() + d, 100);
                break;
            case HORIZONTAL:
                offsetX.set(offsetX.target() + d, 100);
                break;
            case SCALE:
                double step = 1.0 + 0.05 * Math.abs(amount);
                scale.set(scale.target() * (amount > 0 ? step : 1.0 / step), 100);
                break;
        }
    }

    private boolean allowed(Mode mode) {
        if (tooltipFreeScroll.on()) return true;
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        switch (mode) {
            case VERTICAL: return lineShiftMode.on() || lastHeight > res.getScaledHeight();
            case HORIZONTAL: return lastWidth > res.getScaledWidth();
            default: return true;
        }
    }

    private double clamp(double pos, double size, double screen) {
        double margin = 6.0 * scale.value();
        double far = screen - size - margin;
        return Math.max(Math.min(margin, far), Math.min(Math.max(margin, far), pos));
    }

    public static float[] position(int x, int y, int width, int height) {
        ModuleScrollableTooltips m = instance;
        if (m == null || !m.isEnabled()) return null;
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
        int sw = res.getScaledWidth(), sh = res.getScaledHeight();
        double s = m.scale.value();

        if (m.shifted || !m.positioned && m.startAtTop.on()) {
            if (width * s > sw) m.offsetX.set(-x + 6.0 * s, 0);
            if (height * s > sh) m.offsetY.set(-y + 6.0 * s, 0);
        }

        if (!m.positioned || !m.tooltipFreeScroll.on()) {
            double px = x + m.offsetX.value(), py = y + m.offsetY.value();
            double cx = m.clamp(px, width * s, sw), cy = m.clamp(py, height * s, sh);
            if (cx != px) m.offsetX.set(-x + cx, 0);
            if (cy != py) m.offsetY.set(-y + cy, 0);
        }
        m.positioned = true;
        m.shifted = false;
        float total = m.tooltipScale.value() * (float)s;
        m.lastWidth = total * width;
        m.lastHeight = total * height;
        return new float[]{(float)(x + m.offsetX.value()), (float)(y + m.offsetY.value()), total};
    }
}
