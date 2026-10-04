package com.example.lunarforge.module.modules.mechanic;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.apache.logging.log4j.LogManager;

final class CrosshairCode {
    final String type;
    final int extra;
    final byte[] data;

    CrosshairCode(String type, int extra, byte[] data) {
        this.type = type;
        this.extra = extra;
        this.data = data;
    }

    static CrosshairCode parse(String s) {
        String[] parts = s.trim().split("-");
        if (parts.length != 3) return null;
        String type = parts[0];
        if (!"LCCH".equals(type) && !"LCCS".equals(type)) return null;
        int n;
        try { n = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { return null; }
        try {
            return new CrosshairCode(type, n, decode(parts[2]));
        } catch (Exception e) {
            LogManager.getLogger("LunarForge").warn("Crosshair code data parse failed " + s, e);
            return null;
        }
    }

    @Override public String toString() {
        if (!type.equals("LCCH") && !type.equals("LCCS")) throw new IllegalArgumentException("Invalid crosshair type " + type);
        return type + "-" + extra + "-" + encode(data);
    }

    private static String encode(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setLevel(9);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        String plain = Base64.getEncoder().encodeToString(data);
        String packed = Base64.getEncoder().encodeToString(out.toByteArray()) + "#";
        return packed.length() < plain.length() ? packed : plain;
    }

    private static byte[] decode(String s) throws DataFormatException {
        if (!s.endsWith("#")) return Base64.getDecoder().decode(s);
        byte[] packed = Base64.getDecoder().decode(s.substring(0, s.length() - 1));
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!inflater.finished()) {
            int n = inflater.inflate(buf);
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
            out.write(buf, 0, n);
        }
        inflater.end();
        return out.toByteArray();
    }
}
