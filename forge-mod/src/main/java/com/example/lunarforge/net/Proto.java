package com.example.lunarforge.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Proto {
    private Proto() {}

    private static final int VARINT = 0, FIXED64 = 1, BYTES = 2, FIXED32 = 5;

    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private void tag(int field, int type) { varint((long)field << 3 | type); }

        private void varint(long v) {
            while ((v & ~0x7FL) != 0) { out.write((int)(v & 0x7F) | 0x80); v >>>= 7; }
            out.write((int)v);
        }

        private void fixed64(long v) { for (int i = 0; i < 8; i++) out.write((int)(v >>> (8 * i))); }

        public Writer int32(int field, int v) {
            if (v != 0) { tag(field, VARINT); varint(v); }
            return this;
        }

        public Writer bool(int field, boolean v) {
            if (v) { tag(field, VARINT); out.write(1); }
            return this;
        }

        public Writer fixed64(int field, long v) {
            if (v != 0) { tag(field, FIXED64); fixed64(v); }
            return this;
        }

        public Writer dbl(int field, double v) {
            if (Double.doubleToRawLongBits(v) != 0) { tag(field, FIXED64); fixed64(Double.doubleToRawLongBits(v)); }
            return this;
        }

        public Writer oneofBool(int field, boolean v) {
            tag(field, VARINT);
            out.write(v ? 1 : 0);
            return this;
        }

        public Writer oneofDouble(int field, double v) {
            tag(field, FIXED64);
            fixed64(Double.doubleToRawLongBits(v));
            return this;
        }

        public Writer oneofString(int field, String v) {
            byte[] b = v.getBytes(StandardCharsets.UTF_8);
            tag(field, BYTES);
            varint(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        public Writer string(int field, String v) {
            return v == null || v.isEmpty() ? this : bytes(field, v.getBytes(StandardCharsets.UTF_8));
        }

        public Writer bytes(int field, byte[] v) {
            if (v == null || v.length == 0) return this;
            tag(field, BYTES);
            varint(v.length);
            out.write(v, 0, v.length);
            return this;
        }

        public Writer message(int field, Writer v) {
            byte[] b = v.toByteArray();
            tag(field, BYTES);
            varint(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        public byte[] toByteArray() { return out.toByteArray(); }
    }

    public static Writer uuid(UUID id) {
        return new Writer().fixed64(1, id.getMostSignificantBits()).fixed64(2, id.getLeastSignificantBits());
    }

    public static UUID readUuid(byte[] data) throws IOException {
        Reader in = new Reader(data);
        long high = 0, low = 0;
        while (in.next()) {
            if (in.field() == 1) high = in.fixed64();
            else if (in.field() == 2) low = in.fixed64();
            else in.skip();
        }
        return new UUID(high, low);
    }

    public static final class Reader {
        private final byte[] buf;
        private int pos, tag;

        public Reader(byte[] buf) { this.buf = buf; }

        public boolean next() throws IOException {
            if (pos >= buf.length) return false;
            tag = (int)varint();
            return true;
        }

        public int field() { return tag >>> 3; }

        public long varint() throws IOException {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= buf.length) throw new IOException("Truncated varint");
                byte b = buf[pos++];
                v |= (long)(b & 0x7F) << shift;
                if ((b & 0x80) == 0) return v;
            }
            throw new IOException("Malformed varint");
        }

        public long fixed64() throws IOException {
            if (pos + 8 > buf.length) throw new IOException("Truncated fixed64");
            long v = 0;
            for (int i = 0; i < 8; i++) v |= (buf[pos++] & 0xFFL) << (8 * i);
            return v;
        }

        public byte[] bytes() throws IOException {
            int len = (int)varint();
            if (len < 0 || pos + len > buf.length) throw new IOException("Truncated field");
            byte[] v = new byte[len];
            System.arraycopy(buf, pos, v, 0, len);
            pos += len;
            return v;
        }

        public String string() throws IOException { return new String(bytes(), StandardCharsets.UTF_8); }

        public void skip() throws IOException {
            switch (tag & 7) {
                case VARINT: varint(); break;
                case FIXED64: pos += 8; break;
                case BYTES: bytes(); break;
                case FIXED32: pos += 4; break;
                default: throw new IOException("Unsupported wire type " + (tag & 7));
            }
        }
    }
}
