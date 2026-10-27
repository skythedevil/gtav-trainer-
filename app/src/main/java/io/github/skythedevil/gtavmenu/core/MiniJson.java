package io.github.skythedevil.gtavmenu.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny JSON reader so the installer logic has no outside dependencies
 * (it runs the same on a phone and in our desktop self-test).
 * Objects become Map, arrays become List, whole numbers become Long.
 */
public final class MiniJson {
    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("Unexpected extra data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map)) throw new InstallException("Expected a JSON object");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        if (!(o instanceof List)) throw new InstallException("Expected a JSON list");
        return (List<Object>) o;
    }

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : null;
    }

    public static long num(Map<String, Object> m, String key, long fallback) {
        Object v = m.get(key);
        return v instanceof Long ? (Long) v : fallback;
    }

    private InstallException err(String msg) { return new InstallException("Invalid JSON at position " + i + ": " + msg); }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private Object value() {
        if (i >= s.length()) throw err("Unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw err("Expected a key");
            String k = string(); ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("Expected ':'");
            i++; ws();
            m.put(k, value()); ws();
            if (i >= s.length()) throw err("Unexpected end");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("Expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws(); l.add(value()); ws();
            if (i >= s.length()) throw err("Unexpected end");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("Expected ',' or ']'");
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw err("Unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) throw err("Unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw err("Bad \\u escape");
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: throw err("Bad escape");
            }
        }
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("Unexpected character");
        String t = s.substring(st, i);
        try {
            if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) return Double.valueOf(t);
            return Long.valueOf(t);
        } catch (NumberFormatException ex) { throw err("Bad number"); }
    }
}
