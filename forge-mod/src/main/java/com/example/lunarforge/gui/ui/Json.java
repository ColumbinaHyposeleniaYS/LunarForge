package com.example.lunarforge.gui.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) { return new Json(text).value(); }

    private Object value() {
        ws();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        int start = i;
        while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
        String token = s.substring(start, i);
        if (token.equals("null")) return null;
        if (token.equals("true") || token.equals("false")) return Boolean.valueOf(token);
        return token;
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        i++;
        ws();
        if (s.charAt(i) == '}') { i++; return map; }
        while (true) {
            ws();
            String key = string();
            ws(); i++;
            map.put(key, value());
            ws();
            if (s.charAt(i++) == '}') return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        i++;
        ws();
        if (s.charAt(i) == ']') { i++; return list; }
        while (true) {
            list.add(value());
            ws();
            if (s.charAt(i++) == ']') return list;
        }
    }

    private String string() {
        StringBuilder out = new StringBuilder();
        i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u': out.append((char)Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                default: out.append(e);
            }
        }
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
}
