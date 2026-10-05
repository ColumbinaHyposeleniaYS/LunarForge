package com.example.lunarforge.gui.ui;

import com.example.lunarforge.module.setting.ItemListSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/**
 * Editable item list row for the module options page (blacklist/whitelist
 * UI). Painted with the same MenuCanvas primitives as the rest of the menu:
 * 0x20FFFFFF chips, 10px rounded fills, Roboto text and the shared green/red
 * accent colors. Entries can be toggled, removed, typed in by registry id or
 * display name, or added from the held stack.
 */
public final class ItemListWidget implements MenuWidget {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LABEL = 0xFFC1C0BE;
    private static final int MUTED = 0xFFBEC3BD;
    private static final int CHIP = 0x20FFFFFF;
    private static final int CHIP_HOVER = 0x35FFFFFF;
    private static final int GREEN = 0xFF29D67A;
    private static final int RED = 0xFFDE2152;

    private static final float ROW = 13.0f;
    private static final float INPUT_H = 14.0f;
    private static final float PAD = 6.0f;

    private final ItemListSetting setting;
    private final String title;
    private final boolean allowList;

    private boolean expanded;
    private boolean inputFocused;
    private String input = "";

    // geometry of the last paint, reused by press()
    private float gx, gy, gw;
    private float inputY, inputX, inputW;
    private float addX, addW, heldX, heldW, chevronX;
    private float firstRowY;
    private int firstRow;

    public ItemListWidget(ItemListSetting setting, String title, boolean allowList) {
        this.setting = setting;
        this.title = title;
        this.allowList = allowList;
    }

    private int accent() { return allowList ? GREEN : RED; }

    @Override public float height(float width) {
        int rows = expanded ? setting.entries().size() : 0;
        return 16.0f + rows * ROW + INPUT_H + 7.0f;
    }

    @Override public void paint(MenuCanvas c, float x, float y, float w, float h, float mx, float my) {
        gx = x; gy = y; gw = w;
        c.fill(x, y, w, h, 6.0f, 0x14000000);

        // ===== header =====
        int count = setting.entries().size();
        c.text(title, MenuCanvas.MEDIUM, 9.5f, x + PAD, y + 3.5f, WHITE);
        String countLabel = count == 0 ? "empty" : count + (count == 1 ? " entry" : " entries");
        c.text(countLabel, MenuCanvas.LIGHT, 8.0f, x + w - PAD - c.textWidth(countLabel, MenuCanvas.LIGHT, 8.0f) - 12.0f, y + 4.5f, LABEL);
        chevronX = x + w - PAD - 8.0f;
        boolean overChevron = mx >= chevronX - 2.0f && mx < chevronX + 10.0f && my >= y && my < y + 16.0f;
        c.fill(chevronX - 2.0f, y + 2.0f, 12.0f, 12.0f, 3.0f, overChevron ? CHIP_HOVER : 0x00FFFFFF);
        String chevron = expanded ? "-" : "+";
        float cw = c.textWidth(chevron, MenuCanvas.MEDIUM, 9.0f);
        c.text(chevron, MenuCanvas.MEDIUM, 9.0f, chevronX + 4.0f - cw / 2.0f, y + 4.5f, MUTED);

        float cursorY = y + 16.0f;
        firstRowY = cursorY;
        firstRow = 0;

        // ===== entry rows =====
        java.util.List<ItemListSetting.Entry> entries = setting.entries();
        if (expanded) {
            for (int i = 0; i < entries.size(); i++) {
                ItemListSetting.Entry e = entries.get(i);
                boolean overRow = mx >= x && mx < x + w && my >= cursorY && my < cursorY + ROW;
                if (overRow) c.fill(x + 2.0f, cursorY, w - 4.0f, ROW - 1.0f, 3.0f, 0x10FFFFFF);

                boolean overChip = mx >= x + PAD && mx < x + PAD + 9.0f && my >= cursorY + 1.5f && my < cursorY + 10.5f;
                int chipColor = e.enabled ? (overChip ? 0xFF3FB584 : 0xB03FB584) : (overChip ? 0x50FFFFFF : 0x28FFFFFF);
                c.fill(x + PAD, cursorY + 1.5f, 9.0f, 9.0f, 2.5f, chipColor);
                if (e.enabled) c.fill(x + PAD + 2.5f, cursorY + 4.0f, 4.0f, 4.0f, 1.5f, WHITE);

                String label = e.name;
                float labelMax = w - PAD * 3.0f - 30.0f;
                if (c.textWidth(label, MenuCanvas.MEDIUM, 9.0f) > labelMax) {
                    while (label.length() > 3 && c.textWidth(label + "…", MenuCanvas.MEDIUM, 9.0f) > labelMax) {
                        label = label.substring(0, label.length() - 1);
                    }
                    label = label + "…";
                }
                c.text(label, MenuCanvas.MEDIUM, 9.0f, x + PAD + 14.0f, cursorY + 2.5f, e.enabled ? WHITE : 0x70FFFFFF);

                float rmX = x + w - PAD - 8.0f;
                boolean overRemove = mx >= rmX && mx < rmX + 8.0f && my >= cursorY + 1.5f && my < cursorY + 10.5f;
                if (overRemove) c.fill(rmX - 2.0f, cursorY + 1.0f, 12.0f, 10.0f, 3.0f, 0x30DE2152);
                String cross = "x";
                float xw = c.textWidth(cross, MenuCanvas.LIGHT, 8.0f);
                c.text(cross, MenuCanvas.LIGHT, 8.0f, rmX + 4.0f - xw / 2.0f, cursorY + 3.0f, overRemove ? RED : 0x80FFFFFF);
                cursorY += ROW;
            }
        }

        // ===== input row =====
        inputY = cursorY + 1.0f;
        inputX = x + PAD;
        heldW = 30.0f;
        addW = 26.0f;
        float right = x + w - PAD;
        inputW = w - PAD * 2.0f - addW - heldW - 8.0f;
        addX = right - addW;
        heldX = addX - 4.0f - heldW;

        boolean overField = mx >= inputX && mx < inputX + inputW && my >= inputY && my < inputY + INPUT_H;
        c.fill(inputX, inputY, inputW, INPUT_H, 4.0f, inputFocused ? 0x30FFFFFF : CHIP);
        c.ring(inputX, inputY, inputW, INPUT_H, 4.0f, inputFocused ? 0x60FFFFFF : 0x18FFFFFF);

        String placeholder = allowList ? "add whitelist item…" : "add blacklist item…";
        String shown = input;
        int argb = WHITE;
        if (input.isEmpty() && !inputFocused) {
            shown = placeholder;
            argb = 0x60FFFFFF;
        } else if (input.isEmpty() && inputFocused) {
            shown = "|";
            argb = 0x90FFFFFF;
        }
        // clip typed text into the field
        while (shown.length() > 1 && c.textWidth(shown, MenuCanvas.MEDIUM, 8.5f) > inputW - 10.0f) {
            shown = shown.substring(1);
        }
        c.text(shown, MenuCanvas.MEDIUM, 8.5f, inputX + 5.0f, inputY + 3.0f, argb);

        boolean overHeld = mx >= heldX && mx < heldX + heldW && my >= inputY && my < inputY + INPUT_H;
        c.fill(heldX, inputY, heldW, INPUT_H, 4.0f, overHeld ? CHIP_HOVER : CHIP);
        String held = "+ Held";
        c.text(held, MenuCanvas.MEDIUM, 8.0f, heldX + heldW / 2.0f - c.textWidth(held, MenuCanvas.MEDIUM, 8.0f) / 2.0f, inputY + 3.2f, MUTED);

        boolean overAdd = mx >= addX && mx < addX + addW && my >= inputY && my < inputY + INPUT_H;
        boolean canAdd = !input.trim().isEmpty();
        c.fill(addX, inputY, addW, INPUT_H, 4.0f, canAdd ? accent() : (overAdd ? CHIP_HOVER : CHIP));
        String add = "Add";
        c.text(add, MenuCanvas.MEDIUM, 8.0f, addX + addW / 2.0f - c.textWidth(add, MenuCanvas.MEDIUM, 8.0f) / 2.0f, inputY + 3.2f, canAdd ? 0xFF0D1A12 : 0x70FFFFFF);
    }

    @Override public void press(float mx, float my, int button) {
        if (button != 0) return;
        if (mx >= chevronX - 2.0f && mx < chevronX + 10.0f && my >= gy && my < gy + 16.0f) {
            expanded = !expanded;
            return;
        }
        if (expanded && my >= firstRowY && my < inputY) {
            int index = (int) ((my - firstRowY) / ROW) + firstRow;
            java.util.List<ItemListSetting.Entry> entries = setting.entries();
            if (index >= 0 && index < entries.size()) {
                float rowY = firstRowY + (index - firstRow) * ROW;
                if (mx >= gx + PAD && mx < gx + PAD + 9.0f && my >= rowY + 1.5f && my < rowY + 10.5f) {
                    setting.setEnabled(index, !entries.get(index).enabled);
                    return;
                }
                float rmX = gx + gw - PAD - 8.0f;
                if (mx >= rmX - 2.0f && mx < rmX + 10.0f && my >= rowY + 1.5f && my < rowY + 10.5f) {
                    setting.remove(index);
                    return;
                }
            }
        }
        if (my >= inputY && my < inputY + INPUT_H) {
            if (mx >= addX && mx < addX + addW) {
                if (!input.trim().isEmpty()) {
                    setting.add(input, -1, true);
                    input = "";
                    inputFocused = false;
                }
                return;
            }
            if (mx >= heldX && mx < heldX + heldW) {
                Minecraft mc = Minecraft.getMinecraft();
                ItemStack held = mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null;
                if (held != null) setting.addHeld(held);
                return;
            }
            if (mx >= inputX && mx < inputX + inputW) {
                inputFocused = true;
                return;
            }
        }
        inputFocused = false;
    }

    @Override public boolean key(char character, int code) {
        if (!inputFocused) return false;
        if (code == org.lwjgl.input.Keyboard.KEY_ESCAPE || code == org.lwjgl.input.Keyboard.KEY_RETURN
                || code == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
            if (code != org.lwjgl.input.Keyboard.KEY_ESCAPE && !input.trim().isEmpty()) {
                setting.add(input, -1, true);
            }
            input = "";
            inputFocused = false;
            return true;
        }
        if (code == org.lwjgl.input.Keyboard.KEY_BACK || code == org.lwjgl.input.Keyboard.KEY_DELETE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (character >= 32 && character != 127 && input.length() < 48) {
            input += character;
            return true;
        }
        return false;
    }
}
