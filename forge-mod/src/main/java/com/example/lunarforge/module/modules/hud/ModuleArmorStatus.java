package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.modules.hud.armorstatus.ArmorBars;
import com.example.lunarforge.module.modules.hud.armorstatus.ArmorElement;
import com.example.lunarforge.module.modules.hud.armorstatus.ArmorProtection;
import com.example.lunarforge.module.modules.hud.armorstatus.ArmorSlotChild;
import com.example.lunarforge.module.modules.hud.armorstatus.HotbarStrip;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class ModuleArmorStatus extends Module {
    public enum Slot {
        HELD_ITEM(-1, "heldItem"), HELMET(3, "helmet"), CHESTPLATE(2, "chestplate"), LEGGINGS(1, "leggings"), BOOTS(0, "boots");
        public final int armorSlot;
        public final String langId;
        Slot(int armorSlot, String langId) { this.armorSlot = armorSlot; this.langId = langId; }
        public boolean held() { return this == HELD_ITEM; }
    }

    public enum ListMode implements ChoiceSetting.Option {
        VERTICAL("vertical"), HORIZONTAL("horizontal");
        private final String id; ListMode(String id) { this.id = id; } @Override public String langId() { return id; }
    }

    public enum Position implements ChoiceSetting.Option {
        TOP("top"), BOTTOM("bottom"), RIGHT("right"), LEFT("left");
        private final String id; Position(String id) { this.id = id; } @Override public String langId() { return id; }
    }

    public enum Side implements ChoiceSetting.Option {
        RIGHT("right"), LEFT("left");
        private final String id; Side(String id) { this.id = id; } @Override public String langId() { return id; }
    }

    public enum Display implements ChoiceSetting.Option {
        VALUE("value"), PERCENT("percent"), NONE("none");
        private final String id; Display(String id) { this.id = id; } @Override public String langId() { return id; }
    }

    public final BoolSetting itemName = bool("itemName", false);
    public final BoolSetting itemCount = bool("itemCount", true);
    public final BoolSetting showWhileTyping = bool("showWhileTyping", false);
    public final BoolSetting moveArmorIndividually = bool("moveArmorIndividually", false);
    public final BoolSetting textShadow = bool("textShadow", true);
    public final BoolSetting damageOverlay = bool("damageOverlay", true);
    public final BoolSetting itemDamage = bool("itemDamage", true);
    public final BoolSetting armorDamage = bool("armorDamage", true);
    public final BoolSetting maxDamage = bool("maxDamage", false);
    public final BoolSetting hideUnbreakableDurability = bool("hideUnbreakableDurability", true);
    private final ChoiceSetting<Position> durabilityPosition = choice("durabilityPosition", Position.RIGHT);
    private final ChoiceSetting<ListMode> listMode = choice("listMode", ListMode.VERTICAL);
    public final ChoiceSetting<Display> damageDisplay = choice("damageDisplay", Display.VALUE);
    private final BoolSetting background = bool("background", false);
    private final BoolSetting border = bool("border", false);
    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting borderColor = color("borderColor", 0x9F000000);
    public final ColorSetting nameTextColor = color("nameTextColor", 0xFFFFFFFF);
    public final BoolSetting vanillaMode = bool("vanillaMode", false);
    private final BoolSetting hotbarAnchor = bool("hotbarAnchor", true);
    private final ChoiceSetting<Side> hotbarPosition = choice("hotbarPosition", Side.LEFT);
    private final BoolSetting hideEmptySlots = bool("hideEmptySlots", false);
    private final BoolSetting roundedCorners = bool("roundedCorners", true);
    private final BoolSetting lowDurabilityIndicator = bool("lowDurabilityIndicator", true);
    private final NumberSetting lowDurabilityThreshold = integer("lowDurabilityThreshold", 10, 1, 100);
    private final BoolSetting staticDamageColors = bool("staticDamageColors", false);
    private final ColorSetting lowestColor = color("lowestColor", -5636096);
    private final ColorSetting lowColor = color("lowColor", -43691);
    private final ColorSetting mediumLowColor = color("mediumLowColor", -22016);
    private final ColorSetting mediumColor = color("mediumColor", -171);
    private final ColorSetting highColor = color("highColor", -11141291);
    private final ColorSetting highestColor = color("highestColor", -1);

    private final Map<Slot, ArmorSlotChild> slots = new EnumMap<Slot, ArmorSlotChild>(Slot.class);
    private final Map<Slot, ArmorElement> elements = new EnumMap<Slot, ArmorElement>(Slot.class);
    private final Map<Slot, ArmorElement> previewElements = new EnumMap<Slot, ArmorElement>(Slot.class);
    private boolean previewStale = true;
    private final Hud hud;

    public ModuleArmorStatus() {
        super("ARMORSTATUS", true);
        hud = hud(new Hud());
        child(new ArmorProtection(), "generalOptions");
        child(new ArmorBars(this), "generalOptions");
        for (Slot slot : Slot.values()) slots.put(slot, child(new ArmorSlotChild(this, slot), "hudOptions"));
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {});
        page.section("hudOptions", s -> {
            s.add(moveArmorIndividually).hideIf(vanillaMode::on);
            s.add(vanillaMode);
            s.add(listMode).hideIf(() -> moveArmorIndividually.on() && !vanillaMode.on());
            s.add(hotbarPosition).hideIf(() -> !vanillaMode.on() || !hotbarAnchor.on());
            s.add(hotbarAnchor, roundedCorners, hideEmptySlots).hideIf(() -> !vanillaMode.on());
            s.add(itemName).hideIf(vanillaMode::on);
            s.add(itemCount, showWhileTyping, textShadow);
            s.group(background, g -> {
                g.add(backgroundColor);
                g.group(border, b -> b.add(borderThickness, borderColor));
            }).hideIf(() -> moveArmorIndividually.on() || vanillaMode.on());
            s.add(hud.scale).hideIf(() -> moveArmorIndividually.on() || vanillaMode.on() && hotbarAnchor.on());
        });
        page.section("damageOptions", s -> {
            s.add(damageDisplay).hideIf(() -> !itemDamage.on() && !armorDamage.on());
            s.add(durabilityPosition).hideIf(() -> listMode.is(ListMode.HORIZONTAL) || noDamage() || moveArmorIndividually.on() || vanillaMode.on());
            s.add(damageOverlay);
            s.group(lowDurabilityIndicator, g -> g.add(lowDurabilityThreshold)).hideIf(() -> !vanillaMode.on());
            s.add(itemDamage, armorDamage).hideIf(() -> damageDisplay.is(Display.NONE));
            s.add(maxDamage).hideIf(() -> damageDisplay.is(Display.PERCENT) || noDamage() || vanillaMode.on());
            s.add(hideUnbreakableDurability).hideIf(this::noDamage);
        });
        page.section("colorOptions", s -> {
            s.add(nameTextColor).hideIf(() -> !itemName.on() || vanillaMode.on());
            s.add(highestColor);
            s.add(highColor, mediumColor, mediumLowColor, lowColor, lowestColor).hideIf(() -> staticDamageColors.on() || noDamage());
            s.add(staticDamageColors).hideIf(this::noDamage);
        });
    }

    private boolean noDamage() { return damageDisplay.is(Display.NONE) || !itemDamage.on() && !armorDamage.on(); }

    public ColorSetting damageColor(int percent) {
        if (staticDamageColors.on()) return highestColor;
        if (percent <= 10) return lowestColor;
        if (percent <= 25) return lowColor;
        if (percent <= 40) return mediumLowColor;
        if (percent <= 60) return mediumColor;
        if (percent <= 80) return highColor;
        return highestColor;
    }

    public static boolean lowDurability(ItemStack item, int threshold) {
        int max = item == null ? 0 : item.getMaxDamage();
        if (max <= 0 || !item.isItemStackDamageable()) return false;
        return (max - item.getItemDamage()) * 100 / max <= threshold;
    }

    public static String stored(String id, String option) { return ModuleManager.store(id, option, null); }

    public static HudAnchor anchor(String id, HudAnchor fallback) {
        HudAnchor a = HudAnchor.fromId(ModuleManager.store(id, "anchor", UiModel.defaultOption(id, "anchor")));
        return a == null ? fallback : a;
    }

    public Position durabilityPosition() {
        if (stored(key(), "durabilityPosition") == null && anchor(key(), hud.anchor).horizontal == HudAnchor.Side.END) return Position.LEFT;
        return durabilityPosition.get();
    }

    private static ItemStack itemIn(EntityPlayerSP player, Slot slot) {
        return slot.held() ? player.getCurrentEquippedItem() : player.inventory.armorInventory[slot.armorSlot];
    }

    private static ItemStack sample(Slot slot) {
        Item item;
        switch (slot) {
            case HELD_ITEM: item = Items.diamond_sword; break;
            case HELMET: item = Items.diamond_helmet; break;
            case CHESTPLATE: item = Items.diamond_chestplate; break;
            case LEGGINGS: item = Items.diamond_leggings; break;
            default: item = Items.diamond_boots; break;
        }
        return new ItemStack(item);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        elements.clear();
        previewStale = true;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null || !isEnabled()) return;
        boolean vanilla = vanillaMode.on();
        for (ArmorSlotChild child : slots.values()) {
            if (!child.isEnabled()) continue;
            ItemStack item = itemIn(player, child.slot);
            if (item == null && !vanilla) continue;
            elements.put(child.slot, new ArmorElement(this, child.slot, item, !vanilla));
        }
    }

    public Map<Slot, ArmorElement> elements(boolean preview) {
        if (!preview) return elements;
        if (previewStale) {
            previewElements.clear();
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            boolean vanilla = vanillaMode.on();
            for (ArmorSlotChild child : slots.values()) {
                if (!child.isEnabled()) continue;
                ItemStack item = child.slot.held() && player != null ? itemIn(player, child.slot) : null;
                if (item == null) item = sample(child.slot);
                previewElements.put(child.slot, new ArmorElement(this, child.slot, item, !vanilla));
            }
            previewStale = false;
        }
        return previewElements;
    }

    private final class Hud extends HudElement {
        private final Vanilla vanilla = new Vanilla();

        Hud() { super(ModuleArmorStatus.this, 0, 0, HudAnchor.BOTTOM_RIGHT); }

        @Override public void layout(Page page) {}

        @Override public boolean editable() { return vanillaMode.on() ? !hotbarAnchor.on() : !moveArmorIndividually.on(); }

        @Override public boolean screenSpace() { return vanillaMode.on() && hotbarAnchor.on(); }

        @Override public boolean visible(boolean preview) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean chatOpen = mc.ingameGUI != null && mc.ingameGUI.getChatGUI().getChatOpen();
            if (!showWhileTyping.on() && chatOpen) return false;
            if (vanillaMode.on()) return vanilla.prepare(elements(preview).values());
            if (moveArmorIndividually.on()) { size(0, 0); return false; }
            Collection<ArmorElement> list = elements(preview).values();
            if (list.isEmpty()) return false;
            measure(list);
            return true;
        }

        private Position measure(Collection<ArmorElement> list) {
            boolean vertical = listMode.is(ListMode.VERTICAL);
            Position pos = vertical ? durabilityPosition() : anchor(key(), anchor).horizontal == HudAnchor.Side.END ? Position.LEFT : Position.RIGHT;
            float w = 0, h = 0;
            for (ArmorElement e : list) {
                e.layout(pos);
                if (vertical) { w = Math.max(w, e.width()); h += (h == 0 ? 0 : 2) + e.height(); }
                else { w += (w == 0 ? 0 : 2) + e.width(); h = Math.max(h, e.height()); }
            }
            size(w + 4, h + 4);
            return pos;
        }

        @Override public void render(boolean preview) {
            Collection<ArmorElement> list = elements(preview).values();
            if (vanillaMode.on()) { vanilla.render(list); return; }
            Position pos = measure(list);
            boolean vertical = listMode.is(ListMode.VERTICAL);
            float maxW = width() - 4;
            if (background.on()) {
                Draw.fill(backgroundColor, 0, 0, width(), height());
                if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            }
            float x = 2, y = 3, offset = 0;
            for (ArmorElement e : list) {
                if (vertical) {
                    float dx;
                    switch (pos) {
                        case LEFT: dx = maxW - e.width(); break;
                        case RIGHT: dx = 0; break;
                        default: dx = itemName.on() ? 0 : (maxW - e.width()) / 2; break;
                    }
                    e.draw(x + dx, y + offset);
                    offset += e.height() + 2;
                } else {
                    e.draw(x + offset, y);
                    offset += e.width() + 2;
                }
            }
            GlStateManager.color(1, 1, 1, 1);
        }

        private final class Vanilla {
            private boolean vertical, attached, leftSide;
            private int count;
            private float textScale, stripWidth, stripHeight, above;

            boolean prepare(Collection<ArmorElement> list) {
                vertical = listMode.is(ListMode.VERTICAL);
                attached = hotbarAnchor.on();
                leftSide = attached ? hotbarPosition.is(Side.LEFT) : anchor(key(), anchor).horizontal == HudAnchor.Side.END;
                boolean hideEmpty = hideEmptySlots.on(), anyText = false, anyLow = false;
                float widest = 0;
                count = 0;
                for (ArmorElement e : list) {
                    if (hideEmpty && e.item == null) continue;
                    ++count;
                    if (vertical) continue;
                    String[] text = new String[1];
                    e.durability(false, text);
                    widest = Math.max(widest, Draw.width(text[0]));
                    anyText |= !text[0].isEmpty();
                    anyLow |= low(e.item);
                }
                stripWidth = HotbarStrip.width(vertical, count);
                stripHeight = HotbarStrip.height(vertical, count);
                textScale = 1;
                above = 0;
                if (!vertical) {
                    textScale = widest <= 20 ? 1 : widest * 0.75f <= 20 ? 0.75f : 0.5f;
                    if (anyText) above += Math.round(Draw.fontHeight() * textScale);
                    if (anyLow) above += 8;
                }
                if (count == 0 || attached) size(0, 0);
                else size(stripWidth, stripHeight + above);
                return count > 0;
            }

            void render(Collection<ArmorElement> list) {
                if (!prepare(list)) return;
                GlStateManager.pushMatrix();
                if (attached) toHotbar();
                else GlStateManager.translate(0, above, 0);
                HotbarStrip.draw(0, 0, vertical, count, roundedCorners.on(), 1.0f);
                int i = 0;
                for (ArmorElement e : list) {
                    if (e.item == null && hideEmptySlots.on()) continue;
                    int step = i++ * 20;
                    int x = 3 + (vertical ? 0 : step), y = 3 + (vertical ? step : 0);
                    if (e.item == null) {
                        ResourceLocation icon = emptyIcon(e.slot);
                        if (icon != null) Draw.texture(icon, x, y, 16, 16, 0xFFFFFFFF);
                        continue;
                    }
                    e.drawItem(x, y);
                    durability(e, x, y);
                }
                GlStateManager.popMatrix();
                GlStateManager.color(1, 1, 1, 1);
            }

            private void toHotbar() {
                ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());

                float f = 1.0f;
                int w = Math.round(res.getScaledWidth() / f), h = Math.round(res.getScaledHeight() / f);
                float hotbarX = w / 2 - 91, hotbarY = h - 22;
                float x = leftSide ? hotbarX - 7 - stripWidth : hotbarX + 182 + 7;
                GlStateManager.scale(f, f, 1.0f);
                GlStateManager.translate(x, hotbarY + 22 - stripHeight, 0);
            }

            private boolean low(ItemStack item) { return lowDurabilityIndicator.on() && lowDurability(item, lowDurabilityThreshold.intValue()); }

            private void durability(ArmorElement e, int x, int y) {
                String[] out = new String[1];
                ColorSetting color = e.durability(false, out);
                String text = out[0];
                boolean warn = low(e.item);
                if (text.isEmpty() && !warn) return;
                float tw = Draw.width(text) * textScale, th = text.isEmpty() ? 0 : Math.round(Draw.fontHeight() * textScale);
                if (!vertical) {
                    text(color, text, x + center(tw), -th);
                    if (warn) warning(x + center(7) - 1, -th - 1 - 7);
                    return;
                }
                float tx = leftSide ? -tw - 1 : stripWidth + 2;
                float wx = leftSide ? -tw - 7 - 3 : stripWidth + tw + 3;
                text(color, text, tx, y + center(th));
                if (warn) warning(wx, y + center(7) - 0.5f);
            }

            private float center(float size) { return Math.round((16 - size) / 2); }

            private void text(ColorSetting color, String text, float x, float y) {
                if (text.isEmpty()) return;
                GlStateManager.pushMatrix();
                GlStateManager.scale(textScale, textScale, 1);
                Draw.text(color, text, Math.round(x / textScale), Math.round(y / textScale), textShadow.on());
                GlStateManager.popMatrix();
            }

            private void warning(float x, float y) {
                int dark = 0xFF3F1A1A, red = 0xFFFF6B6B;
                for (int i = 0; i < 6; i++) {
                    float w = Math.min(7, 3 + i / 2 * 2), fx = x + (7 - w) / 2;
                    Draw.rect(fx + 1, y + i + 1, w, 1, dark);
                    Draw.rect(fx, y + i, w, 1, red);
                }
                float cx = x + 3.5f;
                Draw.rect(cx - 0.5f, y + 1, 1, 2, dark);
                Draw.rect(cx - 0.5f, y + 4, 1, 1, dark);
            }
        }
    }

    private static ResourceLocation emptyIcon(Slot slot) {
        if (slot.held()) return null;
        return new ResourceLocation("minecraft", "textures/items/empty_armor_slot_" + slot.langId + ".png");
    }

    public List<ArmorSlotChild> slotChildren() { return new ArrayList<ArmorSlotChild>(slots.values()); }
}
