package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.UiModel;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

abstract class PvpInfoStatsHud extends HudElement {
    enum Alignment implements ChoiceSetting.Option {
        LEFT("alignLeft"), CENTER("alignCenter"), RIGHT("alignRight");

        private final String id;

        Alignment(String id) { this.id = id; }

        @Override public String langId() { return id; }
    }

    protected static final class Piece {
        final String text;
        final ColorSetting color;
        final boolean shadow;

        Piece(String text, ColorSetting color, boolean shadow) {
            this.text = text;
            this.color = color;
            this.shadow = shadow;
        }

        float width() { return Draw.width(text); }
        float height() { return 10.0f; }
        void draw(float x, float y) { Draw.text(color, text, x, y, shadow); }
    }

    protected static final class Row {
        final List<Piece> pieces;
        final int padding;

        Row(int padding, List<Piece> pieces) {
            this.padding = padding;
            this.pieces = pieces;
        }

        float width() {
            float w = padding;
            for (Piece piece : pieces) w += piece.width();
            return w;
        }

        float height() {
            float h = 0.0f;
            for (Piece piece : pieces) h = Math.max(h, piece.height());
            return h;
        }

        void draw(float x, float y) {
            float pieceY = y + (height() - 10.0f) / 2.0f;
            for (Piece piece : pieces) {
                piece.draw(x, pieceY);
                x += piece.width();
            }
        }
    }

    protected final ModulePvpInfo parent;
    private final ResourceLocation icon;

    protected final BoolSetting minimizeStats;
    protected final BoolSetting displayIcon;
    protected final BoolSetting textShadow;
    protected final BoolSetting background;
    protected final BoolSetting border;
    protected final NumberSetting borderThickness;
    protected final BoolSetting autoAlign;
    protected final BoolSetting boldTitle;
    protected final BoolSetting showTimePeriod;
    protected final ChoiceSetting<Alignment> alignment;
    protected final ColorSetting headingColor;
    protected final ColorSetting statColor;
    protected final ColorSetting numberColor;
    protected final ColorSetting backgroundColor;
    protected final ColorSetting borderColor;

    private List<Row> rows = new ArrayList<Row>();

    private float boxWidth, boxHeight;

    private boolean minimized = true;
    private Boolean iconPresent;

    protected PvpInfoStatsHud(Module module, ModulePvpInfo parent) {
        super(module, 0.0f, 0.0f, HudAnchor.TOP_RIGHT);
        this.parent = parent;

        this.icon = new ResourceLocation("lunarforge", "ui/icons/features/" + module.key() + "-18x18.png");
        this.minimizeStats = module.bool("minimizeStats", true);
        this.displayIcon = module.bool("displayIcon", true);
        this.textShadow = module.bool("textShadow", true);
        this.background = module.bool("background", true);
        this.border = module.bool("border", false);
        this.borderThickness = module.decimal("borderThickness", 0.5f, 0.5f, 3.0f);
        this.autoAlign = module.bool("autoAlign", true);
        this.boldTitle = module.bool("boldTitle", true);
        this.showTimePeriod = module.bool("showTimePeriod", true);
        this.alignment = module.choice("alignment", Alignment.LEFT);
        this.headingColor = module.color("headingColor", 0xFFFFFF55);
        this.statColor = module.color("statColor", 0xFFFFFFFF);
        this.numberColor = module.color("numberColor", 0xFF55FF55);
        this.backgroundColor = module.color("backgroundColor", 0x6F000000);
        this.borderColor = module.color("borderColor", 0x9F000000);
    }

    @Override public void layout(Page page) {
        page.addFirst(scale);
        page.group(minimizeStats, g -> g.add(displayIcon));
        page.section("displayOptions", s -> {
            s.add(textShadow);
            s.group(background, g -> g.group(border, b -> b.add(borderThickness)));
            s.add(autoAlign, boldTitle, showTimePeriod);
            s.add(alignment).hideIf(autoAlign::on);
        });
        statsLayout(page);
        page.section("colorOptions", s -> {
            s.add(headingColor, statColor, numberColor);
            colorLayout(s);
            s.add(backgroundColor).hideIf(() -> !background.on());
            s.add(borderColor).hideIf(() -> !border.on());
        });
    }

    protected abstract void statsLayout(Page page);

    protected void colorLayout(Page page) {}

    protected abstract boolean showing();

    protected abstract List<Row> rows();

    protected final String heading(String info) {
        StringBuilder text = new StringBuilder();
        if (boldTitle.on()) text.append("\u00a7l");
        text.append(info);
        if (showTimePeriod.on()) {
            text.append(" (");
            text.append(parent.periodText());
            text.append(")");
        }
        return text.toString();
    }

    protected final Alignment align() {
        if (!autoAlign.on()) return alignment.get();
        HudAnchor anchor = currentAnchor();
        switch (anchor.horizontal) {
            case START: return Alignment.LEFT;
            case END: return Alignment.RIGHT;
            default: return Alignment.CENTER;
        }
    }

    private boolean iconShown() { return minimizeStats.on() || displayIcon.on(); }

    private void build() {
        rows = rows();
        float width = 6.0f, height = 6.0f;
        for (Row row : rows) {
            height += row.height();
            float rowWidth = 6.0f + row.width();
            if (rowWidth > width) width = rowWidth;
        }
        boxWidth = width;
        boxHeight = height;
        size(iconShown() ? width + 18.0f : width, height);
    }

    @Override public boolean visible(boolean preview) {
        if (!showing()) {
            size(0.0f, 0.0f);
            return false;
        }
        build();
        return true;
    }

    @Override public void render(boolean preview) {
        build();
        Alignment align = align();
        boolean withIcon = iconShown();
        float iconX = withIcon && align == Alignment.RIGHT ? boxWidth : 0.0f;
        updateMinimized(align);
        if (withIcon) {
            if (background.on()) Draw.fill(backgroundColor, iconX, 0.0f, 18.0f, 18.0f);
            if (iconPresent()) Draw.texture(icon, (int) iconX, 0, 18.0f, 18.0f, 0xFFFFFFFF);
        }
        if (minimized) return;
        float rowsX = withIcon && align != Alignment.RIGHT ? 18.0f : 0.0f;
        if (background.on()) Draw.fill(backgroundColor, rowsX, 0.0f, boxWidth, boxHeight);
        if (border.on()) Draw.border(borderColor, rowsX, 0.0f, boxWidth, boxHeight, borderThickness.value());
        drawRows(rows, rowsX + 3.0f, 3.0f, boxWidth - 6.0f, align);
    }

    private static void drawRows(List<Row> rows, float x, float y, float width, Alignment align) {
        for (Row row : rows) {
            switch (align) {
                case LEFT: row.draw(x + row.padding, y); break;
                case CENTER: row.draw(x + width / 2.0f - row.width() / 2.0f + row.padding / 2.0f, y); break;
                case RIGHT: row.draw(x + width - row.width(), y); break;
            }
            y += row.height();
        }
    }

    private void updateMinimized(Alignment align) {
        Minecraft mc = Minecraft.getMinecraft();
        double mouseX = mouseX(mc), mouseY = mouseY(mc);
        float scale = scale();
        float[] position = screenPosition();
        boolean iconHit = iconHovered(mouseX, mouseY, align);
        if (minimized) {
            minimized = !iconHit;
        } else if (!iconHit) {
            float iconOffset = align == Alignment.RIGHT ? boxWidth * scale : 0.0f;
            if ((align == Alignment.LEFT || align == Alignment.CENTER) && mouseX < position[0] + 18.0f * scale) {
                minimized = true;
            } else if (align == Alignment.RIGHT && mouseX > position[0] + iconOffset) {
                minimized = true;
            } else {
                minimized = !boxHovered(mouseX, mouseY);
            }
        }
        if (!minimizeStats.on()) minimized = false;
    }

    private boolean iconHovered(double mouseX, double mouseY, Alignment align) {
        if (Minecraft.getMinecraft().currentScreen == null) return false;
        float scale = scale();
        float[] position = screenPosition();
        float offset = align == Alignment.RIGHT ? boxWidth * scale : 0.0f;
        return mouseX > position[0] + offset && mouseX < position[0] + 18.0f * scale + offset
                && mouseY > position[1] && mouseY < position[1] + 18.0f * scale;
    }

    private boolean boxHovered(double mouseX, double mouseY) {
        if (Minecraft.getMinecraft().currentScreen == null) return false;
        float[] position = screenPosition();
        return mouseX > position[0] && mouseY > position[1]
                && mouseX < position[0] + width() && mouseY < position[1] + height();
    }

    private float[] screenPosition() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = scale();
        float width = width() * scale, height = height() * scale;
        float x = 0.0f, y = 0.0f;
        UiModel model = ModuleManager.model();
        if (model != null) {
            x = model.number(getId(), "x", Float.parseFloat(UiModel.defaultOption(getId(), "x")));
            y = model.number(getId(), "y", Float.parseFloat(UiModel.defaultOption(getId(), "y")));
            HudAnchor anchor = HudAnchor.fromId(model.value(getId(), "anchor", UiModel.defaultOption(getId(), "anchor")));
            if (anchor != null) {
                x += anchor.originX(resolution.getScaledWidth(), width);
                y += anchor.originY(resolution.getScaledHeight(), height);
            }
        }
        return new float[]{x, y};
    }

    private static double mouseX(Minecraft mc) {
        ScaledResolution resolution = new ScaledResolution(mc);
        return (double) Mouse.getX() * resolution.getScaledWidth() / mc.displayWidth;
    }

    private static double mouseY(Minecraft mc) {
        ScaledResolution resolution = new ScaledResolution(mc);
        return resolution.getScaledHeight() - (double) Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1.0;
    }

    private boolean iconPresent() {
        if (iconPresent == null) {
            try {
                Minecraft.getMinecraft().getResourceManager().getResource(icon);
                iconPresent = Boolean.TRUE;
            } catch (Exception e) {
                iconPresent = Boolean.FALSE;
            }
        }
        return iconPresent;
    }

    protected final Piece piece(String text, ColorSetting color) {
        return new Piece(text, color, textShadow.on());
    }

    protected final Row row(int padding, Piece... pieces) {
        List<Piece> list = new ArrayList<Piece>();
        for (Piece piece : pieces) list.add(piece);
        return new Row(padding, list);
    }
}
