package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.gui.LunarConfirmScreen;
import com.example.lunarforge.gui.LunarNotifications;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ButtonSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.module.setting.Setting;
import com.example.lunarforge.module.setting.WidgetSetting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public class CrosshairChild extends Module {
    enum Mode implements ChoiceSetting.Option {
        SIMPLE("crosshairModeSimple"), PRESET("crosshairModePreset"), CUSTOM("crosshairModeCustom");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    enum Scale implements ChoiceSetting.Option {
        SMALL("small", 1), NORMAL("normal", 2), LARGE("large", 3), AUTO("auto", 4);
        private final String id;
        final int scale;
        Scale(String id, int scale) { this.id = id; this.scale = scale; }
        @Override public String langId() { return id; }
    }

    enum Kind { NORMAL, FRIENDLY, ENEMY }

    final Kind kind;
    final ChoiceSetting<Mode> crosshairMode = choice("crosshairMode", Mode.SIMPLE);
    final ColorSetting color = color("color", -1);
    final BoolSetting crosshairOutline = bool("crosshairOutline", false);
    final NumberSetting outlineThickness = decimal("outlineThickness", 0.5f, 0.0f, 1.0f);
    final ColorSetting outlineColor = color("outlineColor", 0x88000000);
    final BoolSetting crosshairColorVanilla = bool("crosshairColorVanilla", true);
    final BoolSetting vanillaBlendingColor = bool("vanillaBlendingColor", false);
    final BoolSetting customScale = bool("customScale", false);
    final ChoiceSetting<Scale> crosshairScale = choice("crosshairScale", Scale.NORMAL);
    final BoolSetting healthColorShift = bool("healthColorShift", false);
    final ColorSetting healthShiftColor = color("healthShiftColor", 0xFFFF0000);
    final NumberSetting healthShiftThreshold = integer("healthShiftThreshold", 5, 1, 20);
    final BoolSetting crosshairDynamicBow = bool("crosshairDynamicBow", false);
    final NumberSetting dynamicBowScale = decimal("dynamicBowScale", 2.0f, 1.0f, 10.0f);
    final BoolSetting crosshairDynamicAttack = bool("crosshairDynamicAttack", false);
    final NumberSetting dynamicAttackScale = decimal("dynamicAttackScale", 2.0f, 1.0f, 10.0f);
    final BoolSetting visibleInSpectator = bool("visibleInSpectator", true);
    final ChoiceSetting<CrosshairGrid.Size> gridSize = choice("gridSize", CrosshairGrid.Size.MEDIUM);
    final BoolSetting gridStretch = bool("gridStretch", true);
    final CrosshairShapes shapes = new CrosshairShapes(this);
    final CrosshairCustom custom;

    final WidgetSetting crosshairPreview, crosshairPresets, crosshairDraw;
    private final ButtonSetting copyCode = add(new ButtonSetting("copyCode", this::exportCode));
    private final ButtonSetting importCode = add(new ButtonSetting("importCode", this::importCode));
    private final ButtonSetting copyFromNormal;

    private CrosshairGrid grid;

    private float lastSpread, lastBow;

    CrosshairChild(ModuleCrosshair parent, Kind kind, String id, boolean enabled) {
        super(id, enabled);
        this.kind = kind;
        custom = new CrosshairCustom(this, id);
        crosshairPreview = add(new WidgetSetting("crosshairPreview", "0", new CrosshairWidgets.Preview(this)));
        crosshairPresets = add(new WidgetSetting("crosshairPresets", "0", new CrosshairWidgets.Presets(this)));
        crosshairDraw = add(new WidgetSetting("crosshairDraw", "true", new CrosshairWidgets.Editor(this)));
        copyFromNormal = kind == Kind.NORMAL ? null : add(new ButtonSetting("copyFromNormal", () -> copyFrom(parent.normal)).width(100.0f));
        grid = CrosshairGrid.parse(ModuleManager.store(key(), "customCrosshair", defaultCode()));
        crosshairOutline.onChange(custom::reload);
        outlineThickness.onChange(custom::reload);
        gridSize.onChange(() -> { if (gridSize.get() != grid.size) setGrid(grid.resize(gridSize.get(), gridStretch.on())); });
        crosshairMode.onChange(() -> { if (grid.isEmpty()) setGrid(CrosshairGrid.parse(defaultCode())); });
    }

    String defaultCode() { return CrosshairPresets.DEFAULT; }

    CrosshairGrid grid() { return grid; }

    Mode mode() { return crosshairMode.get(); }

    void setGrid(CrosshairGrid g) {
        grid = g;
        ModuleManager.put(key(), "customCrosshair", g.code());
        if (gridSize.get() != g.size) gridSize.set(g.size);
        custom.reload();
    }

    void gridChanged() {
        ModuleManager.put(key(), "customCrosshair", grid.code());
        custom.reload();
    }

    private static boolean tabHeld() { return Minecraft.getMinecraft() != null && Keyboard.isKeyDown(Keyboard.KEY_TAB); }

    @Override protected void layout(Page page) {
        page.section("crosshairPreviewLabel", s -> s.add(crosshairPreview).hideIf(() -> mode() == Mode.CUSTOM && !tabHeld()));
        page.section("generalOptions", s -> {
            s.add(crosshairMode);
            s.add(crosshairPresets).hideIf(() -> mode() != Mode.PRESET);
            s.add(crosshairDraw, gridSize, gridStretch).hideIf(() -> mode() != Mode.CUSTOM || tabHeld());
            s.add(shapes.crosshairShape).hideIf(() -> mode() != Mode.SIMPLE);
            s.add(shapes.crosshairGap).hideIf(() -> mode() != Mode.SIMPLE || !shapes.hasGap());
            s.add(shapes.crosshairThickness).hideIf(() -> mode() != Mode.SIMPLE || !shapes.hasThickness());
            s.add(shapes.crosshairWidth, shapes.crosshairHeight).hideIf(() -> mode() != Mode.SIMPLE || !shapes.hasSize());
            s.add(color).hideIf(() -> crosshairColorVanilla.on() && !vanillaBlendingColor.on());
            s.group(shapes.crosshairDot, d -> {
                d.add(shapes.dotSize, shapes.dotCircle, shapes.dynamicDot);
                d.group(shapes.customDotColor, c -> c.add(shapes.dotColor)).hideIf(crosshairColorVanilla::on);
                d.group(shapes.dotOutline, o -> o.add(shapes.dotOutlineThickness)).hideIf(() -> !outlineOn());
            }).hideIf(() -> mode() != Mode.SIMPLE || !shapes.hasDot());
            s.group(crosshairOutline, o -> o.add(outlineThickness, outlineColor));
            s.add(copyCode, importCode).hideIf(() -> mode() != Mode.SIMPLE);
        });
        extraOptions(page);
        if (copyFromNormal != null) page.add(copyFromNormal);
    }

    protected void extraOptions(Page page) {
        page.section("extraRenderOptions", s -> {
            s.group(crosshairColorVanilla, g -> g.add(vanillaBlendingColor));
            s.group(healthColorShift, g -> g.add(healthShiftThreshold, healthShiftColor));
            s.group(crosshairDynamicBow, g -> g.add(dynamicBowScale));
            s.group(crosshairDynamicAttack, g -> g.add(dynamicAttackScale));
            s.add(visibleInSpectator);
            s.group(customScale, g -> g.add(crosshairScale));
        });
    }

    @Override protected void onEnable() { custom.reload(); }

    @Override protected void onDisable() { custom.reset(); }

    CrosshairPaint paint() {
        EntityPlayerSP p = Minecraft.getMinecraft() == null ? null : Minecraft.getMinecraft().thePlayer;
        if (healthColorShift.on() && p != null && p.getHealth() < healthShiftThreshold.intValue()) {
            return new CrosshairPaint(healthShiftColor, crosshairColorVanilla.on(), true);
        }
        return new CrosshairPaint(color, crosshairColorVanilla.on(), vanillaBlendingColor.on());
    }

    boolean outlineOn() { return crosshairOutline.on() && outlineThickness.value() > 0.0f; }

    CrosshairPaint outlinePaint() { return outlineOn() ? new CrosshairPaint(outlineColor, false, false) : null; }

    float outlineWidth() { return outlineThickness.value() * 0.8f + 0.2f; }

    float outlineRing() { return outlineThickness.value() * 2.0f; }

    float scale(boolean preview) {
        int gui = Minecraft.getMinecraft() == null ? 2 : new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
        if (preview) return customScale.on() ? crosshairScale.get().scale / 2.0f : gui / 2.0f;
        return customScale.on() ? (float)crosshairScale.get().scale / gui : 1.0f;
    }

    void draw(CrosshairSurface s, float x, float y, boolean preview, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.thePlayer;
        if (!visibleInSpectator.on() && player != null && player.isSpectator()) return;
        float spread = 0.0f;
        if (player != null && mc.currentScreen == null) {
            if (crosshairDynamicBow.on()) {
                ItemStack held = player.getHeldItem();
                int left = player.getItemInUseCount();
                if (held != null && left > 0 && held.getItem() == Items.bow) {
                    float pulled = held.getMaxItemUseDuration() - left;
                    float smooth = lastBow + (pulled - lastBow) * partialTicks;
                    lastBow = pulled;
                    float t = Math.min(1.0f, smooth / 20.0f);
                    spread += dynamicBowScale.value() * (1.0f - t * t);
                }
            }
            float swing;
            if (crosshairDynamicAttack.on() && (swing = player.getSwingProgress(partialTicks)) != 0.0f) {
                float left = 1.0f - swing;
                spread += dynamicAttackScale.value() * left * left;
            }
        }
        float smooth = lastSpread + (spread - lastSpread) * partialTicks;
        lastSpread = spread;
        spread = smooth;
        CrosshairPaint paint = paint();
        float scale = scale(preview);
        if (mode() == Mode.SIMPLE) shapes.draw(s, x, y, scale, spread * 2.0f, paint);
        else custom.draw(s, x, y, scale + spread / 2.0f, paint);
    }

    private static String edit(String key, Object... args) { return LunarLang.get("gui.crosshair_edit", key, args); }

    void exportCode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            String type;
            int extra;
            if (mode() == Mode.SIMPLE) {
                type = "LCCS";
                extra = 0;
            } else {
                if (grid.isEmpty() || CrosshairPresets.banned(grid)) return;
                type = "LCCH";
                extra = grid.usedSize();
                out.write(grid.bytes());
            }
            write(out);
            out.close();
            GuiScreen.setClipboardString(new CrosshairCode(type, extra, bytes.toByteArray()).toString());
            LunarNotifications.info(edit("copiedToClipboard"));
        } catch (Throwable t) {
            LunarNotifications.push(LunarNotifications.Type.ERROR, null, edit("error", t.getMessage()));
        }
    }

    void importCode() {
        final String text = GuiScreen.getClipboardString();
        final GuiScreen back = Minecraft.getMinecraft().currentScreen;
        Minecraft.getMinecraft().displayGuiScreen(new LunarConfirmScreen("crosshairConfirm", ok -> {
            try {
                if (!ok) return;
                CrosshairCode code = CrosshairCode.parse(text);
                if (code == null) {
                    LunarNotifications.push(LunarNotifications.Type.WARNING, null, edit("invalidCode"));
                    return;
                }
                int skip = 0;
                if ("LCCH".equals(code.type)) {
                    CrosshairGrid g = CrosshairGrid.fromBytes(code.extra, code.data);
                    if (g.isEmpty() || CrosshairPresets.banned(g)) return;
                    skip = CrosshairGrid.byteCount(code.extra);
                    setGrid(g);
                    crosshairMode.set(Mode.CUSTOM);
                } else {
                    crosshairMode.set(Mode.SIMPLE);
                }
                ByteArrayInputStream in = new ByteArrayInputStream(code.data);
                in.skip(skip);
                if (in.available() > 0) {
                    DataInputStream data = new DataInputStream(in);
                    read(data);
                    data.close();
                }
                LunarNotifications.info(edit("loadedFromClipboard"));
            } catch (Throwable t) {
                LunarNotifications.push(LunarNotifications.Type.ERROR, null, edit("error", t.getMessage()));
            } finally {
                Minecraft.getMinecraft().displayGuiScreen(back);
            }
        }));
    }

    protected void read(DataInputStream in) throws IOException {
        if (in.readBoolean()) {
            shapes.crosshairShape.set(CrosshairShapes.Shape.values()[in.readInt()]);
            shapes.crosshairThickness.set((float)in.readInt());
            shapes.crosshairWidth.set((float)in.readInt());
            shapes.crosshairHeight.set((float)in.readInt());
            shapes.crosshairGap.set((float)in.readInt());
            shapes.crosshairDot.set(in.readBoolean());
            shapes.dotSize.set((float)in.readInt());
            shapes.dotCircle.set(in.readBoolean());
            shapes.dynamicDot.set(in.readBoolean());
            shapes.customDotColor.set(in.readBoolean());
            readColor(in, shapes.dotColor);
            shapes.dotOutline.set(in.readBoolean());
            shapes.dotOutlineThickness.set(in.readFloat());
        }
        readColor(in, color);
        crosshairOutline.set(in.readBoolean());
        outlineThickness.set(in.readFloat());
        readColor(in, outlineColor);
        crosshairColorVanilla.set(in.readBoolean());
        vanillaBlendingColor.set(in.readBoolean());
        customScale.set(in.readBoolean());
        crosshairScale.set(Scale.values()[in.readInt()]);
        healthColorShift.set(in.readBoolean());
        readColor(in, healthShiftColor);
        healthShiftThreshold.set((float)in.readInt());
        crosshairDynamicBow.set(in.readBoolean());
        dynamicBowScale.set(in.readFloat());
        crosshairDynamicAttack.set(in.readBoolean());
        dynamicAttackScale.set(in.readFloat());
        visibleInSpectator.set(in.readBoolean());
    }

    protected void write(DataOutputStream out) throws IOException {
        boolean simple = mode() == Mode.SIMPLE;
        out.writeBoolean(simple);
        if (simple) {
            out.writeInt(shapes.crosshairShape.get().ordinal());
            out.writeInt(shapes.crosshairThickness.intValue());
            out.writeInt(shapes.crosshairWidth.intValue());
            out.writeInt(shapes.crosshairHeight.intValue());
            out.writeInt(shapes.crosshairGap.intValue());
            out.writeBoolean(shapes.crosshairDot.on());
            out.writeInt(shapes.dotSize.intValue());
            out.writeBoolean(shapes.dotCircle.on());
            out.writeBoolean(shapes.dynamicDot.on());
            out.writeBoolean(shapes.customDotColor.on());
            writeColor(out, shapes.dotColor);
            out.writeBoolean(shapes.dotOutline.on());
            out.writeFloat(shapes.dotOutlineThickness.value());
        }
        writeColor(out, color);
        out.writeBoolean(crosshairOutline.on());
        out.writeFloat(outlineThickness.value());
        writeColor(out, outlineColor);
        out.writeBoolean(crosshairColorVanilla.on());
        out.writeBoolean(vanillaBlendingColor.on());
        out.writeBoolean(customScale.on());
        out.writeInt(crosshairScale.get().ordinal());
        out.writeBoolean(healthColorShift.on());
        writeColor(out, healthShiftColor);
        out.writeInt(healthShiftThreshold.intValue());
        out.writeBoolean(crosshairDynamicBow.on());
        out.writeFloat(dynamicBowScale.value());
        out.writeBoolean(crosshairDynamicAttack.on());
        out.writeFloat(dynamicAttackScale.value());
        out.writeBoolean(visibleInSpectator.on());
    }

    private void readColor(DataInputStream in, ColorSetting c) throws IOException {
        c.set(in.readInt());
        ModuleManager.put(key(), c.key + "Chroma", "" + in.readBoolean());
        ModuleManager.put(key(), c.key + "ChromaSpeed", "" + in.readInt());
        ModuleManager.put(key(), c.key + "ChromaType", in.readInt() == 1 ? "SHIFT" : "WAVE");
    }

    private void writeColor(DataOutputStream out, ColorSetting c) throws IOException {
        out.writeInt(c.argb());
        out.writeBoolean(c.chroma());
        int speed;
        try { speed = Math.round(Float.parseFloat(ModuleManager.store(key(), c.key + "ChromaSpeed", "40"))); } catch (NumberFormatException e) { speed = 40; }
        out.writeInt(speed);
        out.writeInt("SHIFT".equals(ModuleManager.store(key(), c.key + "ChromaType", "WAVE")) ? 1 : 0);
    }

    private void copyFrom(CrosshairChild normal) {
        for (Setting<?> mine : settings()) {
            if (mine.key.equals("enabled")) continue;
            for (Setting<?> theirs : normal.settings()) {
                if (!theirs.key.equals(mine.key)) continue;
                String value = ModuleManager.store(normal.key(), theirs.key, null);
                if (value == null) ModuleManager.remove(key(), mine.key); else ModuleManager.put(key(), mine.key, value);
                for (String suffix : new String[]{"Chroma", "ChromaSpeed", "ChromaType"}) {
                    String v = ModuleManager.store(normal.key(), theirs.key + suffix, null);
                    if (v != null) ModuleManager.put(key(), theirs.key + suffix, v);
                }
            }
        }
        setGrid(normal.grid.copy());
    }
}
