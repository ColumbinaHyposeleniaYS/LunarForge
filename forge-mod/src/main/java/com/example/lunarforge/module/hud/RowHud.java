package com.example.lunarforge.module.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.item.ItemStack;

public abstract class RowHud extends HudElement {
    public enum Alignment implements ChoiceSetting.Option {
        LEFT("alignLeft"), CENTER("alignCenter"), RIGHT("alignRight");
        private final String id;
        Alignment(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public interface Piece {
        void draw(float x, float y);
        float width();
        float height();
    }

    public static final class Text implements Piece {
        final String text;
        final ColorSetting color;
        final boolean shadow;
        final float width;

        public Text(String text, ColorSetting color, boolean shadow) {
            this.text = text; this.color = color; this.shadow = shadow; this.width = Draw.width(text);
        }

        @Override public void draw(float x, float y) { Draw.text(color, text, x, y, shadow); }
        @Override public float width() { return width; }
        @Override public float height() { return 10.0f; }
    }

    public static final class Row implements Piece {
        final List<Piece> pieces;
        final int padding;
        final float width, height;

        public Row(int padding, List<Piece> pieces) {
            this.pieces = pieces; this.padding = padding;
            float w = 0.0f, h = 0.0f;
            for (Piece p : pieces) { w += p.width(); h = Math.max(h, p.height()); }
            width = w + padding; height = h;
        }

        @Override public void draw(float x, float y) {
            for (Piece p : pieces) { p.draw(x, y + (height - p.height()) / 2.0f); x += p.width(); }
        }
        @Override public float width() { return width; }
        @Override public float height() { return height; }
    }

    public static final class Space implements Piece {
        final int width;
        public Space(int width) { this.width = width; }
        @Override public void draw(float x, float y) {}
        @Override public float width() { return width; }
        @Override public float height() { return 10.0f; }
    }

    public static final class Item implements Piece {
        final ItemStack stack;
        public Item(ItemStack stack) { this.stack = stack; }
        @Override public void draw(float x, float y) { Draw.item(stack, x, y - 2.0f); }
        @Override public float width() { return 16.0f; }
        @Override public float height() { return 15.0f; }
    }

    public static Text text(String text, ColorSetting color, boolean shadow) { return new Text(text, color, shadow); }

    public static Row row(int padding, Piece... pieces) { return new Row(padding, new ArrayList<Piece>(Arrays.asList(pieces))); }

    public static Row row(int padding, List<Piece> pieces) { return new Row(padding, pieces); }

    private List<Piece> rows;

    protected RowHud(Module module, float x, float y, HudAnchor anchor) { super(module, x, y, anchor); }

    protected abstract List<Piece> rows(boolean preview);

    protected abstract boolean autoAlign();
    protected abstract Alignment alignment();
    protected abstract boolean background();
    protected abstract ColorSetting backgroundColor();
    protected abstract boolean border();
    protected abstract float borderThickness();
    protected abstract ColorSetting borderColor();

    public static Alignment effective(boolean auto, HudAnchor anchor, Alignment alignment) {
        if (!auto) return alignment;
        switch (anchor.horizontal) {
            case START: return Alignment.LEFT;
            case END: return Alignment.RIGHT;
            default: return Alignment.CENTER;
        }
    }

    @Override public boolean visible(boolean preview) {
        rows = rows(preview);
        if (rows == null || rows.isEmpty()) return false;
        float w = 6.0f;
        int h = 6;
        for (Piece p : rows) { h = (int)(h + p.height()); w = Math.max(w, 6.0f + p.width()); }
        size(w, h);
        return true;
    }

    @Override public void render(boolean preview) {
        float w = width(), h = height();
        if (background()) {
            Draw.fill(backgroundColor(), 0, 0, w, h);
            if (border()) Draw.border(borderColor(), 0, 0, w, h, borderThickness());
        }
        Alignment align = effective(autoAlign(), currentAnchor(), alignment());
        float x = 3.0f, y = 3.0f, inner = w - 6.0f;
        for (Piece p : rows) {
            int padding = p instanceof Row ? ((Row)p).padding : 0;
            switch (align) {
                case LEFT: p.draw(x + padding, y); break;
                case CENTER: p.draw(x + inner / 2.0f - p.width() / 2.0f + padding / 2, y); break;
                case RIGHT: p.draw(x + inner - p.width(), y); break;
            }
            y += p.height();
        }
    }
}
