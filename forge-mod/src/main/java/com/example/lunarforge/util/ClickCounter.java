package com.example.lunarforge.util;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ClickCounter {
    private static final Deque<Long> LEFT = new ArrayDeque<Long>(), LEFT_NOT_USING = new ArrayDeque<Long>(), RIGHT = new ArrayDeque<Long>();
    public static final ClickCounter INSTANCE = new ClickCounter();

    private ClickCounter() {}

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.buttonstate || mc.currentScreen != null) return;
        long now = System.currentTimeMillis();
        if (event.button == 0) {
            LEFT.addLast(now);
            if (mc.thePlayer != null && !mc.thePlayer.isUsingItem()) LEFT_NOT_USING.addLast(now);
        } else if (event.button == 1) {
            RIGHT.addLast(now);
        }
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.removeFirst();
        return clicks.size();
    }

    public static int left(boolean ignoreWhileUsing) { return count(ignoreWhileUsing ? LEFT_NOT_USING : LEFT); }

    public static int right() { return count(RIGHT); }

    public static int of(boolean left, boolean ignoreWhileUsing) { return left ? left(ignoreWhileUsing) : right(); }

    /**
     * Registers a simulated click (0 = left, 1 = right) performed by gameplay modules
     * (Auto Clicker, Fast Place, Block Hit, ...) that never produces a MouseEvent.
     * Mirrors the bookkeeping of {@link #onMouse(MouseEvent) onMouse}.
     */
    public static void register(int button) {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        if (button == 0) {
            LEFT.addLast(now);
            if (mc.thePlayer != null && !mc.thePlayer.isUsingItem()) LEFT_NOT_USING.addLast(now);
        } else if (button == 1) {
            RIGHT.addLast(now);
        }
    }

    public static void clearRight() { RIGHT.clear(); }
}
