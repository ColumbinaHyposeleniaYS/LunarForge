package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.setting.ChoiceSetting;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

final class CrosshairGrid {
    enum Size implements ChoiceSetting.Option {
        SMALL("crosshairGridSmall", 7), MEDIUM("crosshairGridMedium", 15), BIG("crosshairGridBig", 31), HUGE("crosshairGridHuge", 63);
        private final String id;
        final int size;
        Size(String id, int size) { this.id = id; this.size = size; }
        @Override public String langId() { return id; }

        static Size fromGridLength(int n) {
            for (Size s : values()) if (n <= s.size) return s;
            throw new IllegalArgumentException(String.format("Invalid crosshair grid %d * %d", n, n));
        }

        Size bigger() { return values()[Math.min(values().length - 1, ordinal() + 1)]; }
    }

    final Size size;
    final boolean[] data;

    CrosshairGrid(Size size) { this(size, new boolean[size.size * size.size]); }

    CrosshairGrid(Size size, boolean[] data) {
        this.size = size;
        this.data = data;
    }

    static CrosshairGrid of(boolean[] data) { return new CrosshairGrid(Size.fromGridLength((int)Math.floor(Math.sqrt(data.length))), data); }

    static CrosshairGrid empty() { return new CrosshairGrid(Size.MEDIUM); }

    CrosshairGrid copy() { return new CrosshairGrid(size, Arrays.copyOf(data, data.length)); }

    CrosshairGrid resize(Size to, boolean stretch) {
        int from = size.size, n = to.size;
        if (from == n) return copy();
        BufferedImage src = new BufferedImage(from, from, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < from; i++) for (int j = 0; j < from; j++) src.setRGB(i, j, data[i + j * from] ? -1 : 0);
        BufferedImage dst = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        if (stretch) g.drawImage(src, 0, 0, n, n, null);
        else {
            int off = n / 2 - from / 2;
            g.drawImage(src, off, off, from, from, null);
        }
        g.dispose();
        boolean[] out = new boolean[n * n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) out[i + j * n] = dst.getRGB(i, j) != 0;
        return of(out);
    }

    boolean isEmpty() {
        for (boolean b : data) if (b) return false;
        return true;
    }

    String code() { return new CrosshairCode("LCCH", usedSize(), bytes()).toString(); }

    static CrosshairGrid parse(String s) {
        CrosshairCode code = s == null ? null : CrosshairCode.parse(s);
        if (code == null || !code.type.equals("LCCH")) return empty();
        return fromBytes(code.extra, code.data);
    }

    static CrosshairGrid fromBytes(int n, byte[] bytes) {
        CrosshairGrid g = new CrosshairGrid(Size.fromGridLength(n));
        int size = g.size.size, off = g.offset(n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int at = i + off + (j + off) * size;
                if (at < 0 || at >= size * size) continue;
                int bit = i + j * n;
                g.data[at] = (bytes[bit / 8] & 1 << bit % 8) != 0;
            }
        }
        return g;
    }

    byte[] bytes() {
        int size = this.size.size, n = usedSize(), off = offset(n);
        byte[] out = new byte[byteCount(n)];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int at = j + off + (i + off) * size;
                if (at < 0 || at >= size * size || !data[at]) continue;
                int bit = j + i * n;
                out[bit / 8] = (byte)(out[bit / 8] | 1 << bit % 8);
            }
        }
        return out;
    }

    static int byteCount(int n) { return (int)Math.ceil(n * n / 8.0f); }

    int offset(int n) { return (int)Math.floor((size.size - n) / 2.0f); }

    int usedSize() {
        int s = size.size, n = 1;
        for (int i = 0; i < data.length; i++) {
            if (!data[i]) continue;
            n = Math.max(n, span(i % s, s));
            n = Math.max(n, span(i / s, s));
        }
        return n;
    }

    private static int span(int at, int size) {
        int d = at - size / 2;
        return d <= 0 ? -d * 2 + 1 : d * 2;
    }

    @Override public boolean equals(Object o) {
        return o instanceof CrosshairGrid && ((CrosshairGrid)o).size == size && Arrays.equals(((CrosshairGrid)o).data, data);
    }

    @Override public int hashCode() { return size.hashCode() * 31 + Arrays.hashCode(data); }
}
