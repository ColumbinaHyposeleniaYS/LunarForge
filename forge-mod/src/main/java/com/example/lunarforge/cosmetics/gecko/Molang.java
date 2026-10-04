package com.example.lunarforge.cosmetics.gecko;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Molang {
    public interface Expr { double eval(Scope s); }

    public static final class Scope {
        public final Map<String, Double> vars = new HashMap<String, Double>();
        public Queries queries;
        double get(String name) {
            if (name.startsWith("query.")) return queries == null ? 0 : queries.query(name.substring(6));
            Double d = vars.get(name);
            return d == null ? 0 : d;
        }
    }

    public interface Queries { double query(String name); }

    private static final Map<String, Expr> CACHE = new HashMap<String, Expr>();
    private static final Random RANDOM = new Random();
    public static final Expr ZERO = s -> 0;

    private Molang() {}

    public static Expr constant(final double v) { return s -> v; }

    public static synchronized Expr compile(String src) {
        Expr e = CACHE.get(src);
        if (e == null) {
            try { e = new Parser(src).program(); }
            catch (RuntimeException ex) {
                org.apache.logging.log4j.LogManager.getLogger("LunarForge/Molang").debug("Could not parse '{}': {}", src, ex.toString());
                e = ZERO;
            }
            CACHE.put(src, e);
        }
        return e;
    }

    private static final class Var implements Expr {
        final String key;
        Var(String key) { this.key = key; }
        @Override public double eval(Scope s) { return s.get(key); }
    }

    private static final class Function {
        final String[] params; final Expr body;
        Function(String[] params, Expr body) { this.params = params; this.body = body; }
    }

    private static final Map<String, Function> FUNCTIONS = new HashMap<String, Function>();

    public static synchronized void loadFunctions(String text) {
        String name = null; String[] params = null; StringBuilder body = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))) {
                if (name != null) FUNCTIONS.put(name, new Function(params, compile(body.toString())));
                int open = line.indexOf('('), close = line.indexOf(')');
                if (open < 0 || close < open) { name = null; continue; }
                name = line.substring(0, open).trim().toLowerCase();
                String list = line.substring(open + 1, close).trim();
                params = list.isEmpty() ? new String[0] : list.toLowerCase().split("\\s*,\\s*");
                body.setLength(0);
            } else if (name != null) body.append(line).append('\n');
        }
        if (name != null) FUNCTIONS.put(name, new Function(params, compile(body.toString())));
    }

    private static final class Return extends RuntimeException {
        final double value;
        Return(double value) { super(null, null, false, false); this.value = value; }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s.toLowerCase(); }

        Expr program() {
            final List<Expr> statements = new ArrayList<Expr>();
            while (true) {
                skip();
                if (i >= s.length()) break;
                if (peek(';')) { i++; continue; }
                statements.add(statement());
            }
            if (statements.isEmpty()) return ZERO;
            return sc -> {
                double last = 0;
                try { for (Expr e : statements) last = e.eval(sc); }
                catch (Return r) { return r.value; }
                return last;
            };
        }

        Expr statement() {
            skip();
            if (s.startsWith("return", i) && !Character.isLetterOrDigit(at(i + 6)) && at(i + 6) != '_') {
                i += 6;
                final Expr v = ternary();
                return sc -> { throw new Return(v.eval(sc)); };
            }
            if (s.startsWith("if", i) && !Character.isLetterOrDigit(at(i + 2)) && at(i + 2) != '_') {
                i += 2;
                final Expr cond = primary();
                final Expr then = primary();
                skip();
                Expr other = ZERO;
                if (s.startsWith("else", i)) { i += 4; skip(); other = s.startsWith("if", i) ? statement() : primary(); }
                final Expr otherwise = other;
                return sc -> cond.eval(sc) != 0 ? then.eval(sc) : otherwise.eval(sc);
            }
            return ternary();
        }

        Expr ternary() {
            final Expr c = coalesce();
            skip();
            if (peek('?') && at(i + 1) != '?') {
                i++;
                final Expr a = ternary();
                skip();
                if (!peek(':')) return sc -> c.eval(sc) != 0 ? a.eval(sc) : 0;
                i++;
                final Expr b = ternary();
                return sc -> c.eval(sc) != 0 ? a.eval(sc) : b.eval(sc);
            }
            return c;
        }

        Expr coalesce() {
            Expr l = or();
            while (true) {
                skip();
                if (!s.startsWith("??", i)) return l;
                i += 2;
                final Expr a = l, b = or();
                l = a instanceof Var
                    ? sc -> sc.vars.containsKey(((Var)a).key) ? a.eval(sc) : b.eval(sc)
                    : sc -> { double v = a.eval(sc); return v != 0 ? v : b.eval(sc); };
            }
        }

        Expr or() {
            Expr l = and();
            while (true) {
                skip();
                if (!s.startsWith("||", i)) return l;
                i += 2;
                final Expr a = l, b = and();
                l = sc -> a.eval(sc) != 0 || b.eval(sc) != 0 ? 1 : 0;
            }
        }

        Expr and() {
            Expr l = compare();
            while (true) {
                skip();
                if (!s.startsWith("&&", i)) return l;
                i += 2;
                final Expr a = l, b = compare();
                l = sc -> a.eval(sc) != 0 && b.eval(sc) != 0 ? 1 : 0;
            }
        }

        Expr compare() {
            Expr l = additive();
            while (true) {
                skip();
                final String op;
                if (s.startsWith("==", i) || s.startsWith("!=", i) || s.startsWith("<=", i) || s.startsWith(">=", i)) op = s.substring(i, i + 2);
                else if (peek('<') || peek('>')) op = s.substring(i, i + 1);
                else return l;
                i += op.length();
                final Expr a = l, b = additive();
                switch (op) {
                    case "==": l = sc -> a.eval(sc) == b.eval(sc) ? 1 : 0; break;
                    case "!=": l = sc -> a.eval(sc) != b.eval(sc) ? 1 : 0; break;
                    case "<=": l = sc -> a.eval(sc) <= b.eval(sc) ? 1 : 0; break;
                    case ">=": l = sc -> a.eval(sc) >= b.eval(sc) ? 1 : 0; break;
                    case "<": l = sc -> a.eval(sc) < b.eval(sc) ? 1 : 0; break;
                    default: l = sc -> a.eval(sc) > b.eval(sc) ? 1 : 0; break;
                }
            }
        }

        Expr additive() {
            Expr l = multiplicative();
            while (true) {
                skip();
                if (peek('+')) { i++; final Expr a = l, b = multiplicative(); l = sc -> a.eval(sc) + b.eval(sc); }
                else if (peek('-')) { i++; final Expr a = l, b = multiplicative(); l = sc -> a.eval(sc) - b.eval(sc); }
                else return l;
            }
        }

        Expr multiplicative() {
            Expr l = unary();
            while (true) {
                skip();
                if (peek('*')) { i++; final Expr a = l, b = unary(); l = sc -> a.eval(sc) * b.eval(sc); }
                else if (peek('/')) { i++; final Expr a = l, b = unary(); l = sc -> { double d = b.eval(sc); return d == 0 ? 0 : a.eval(sc) / d; }; }
                else if (peek('%')) { i++; final Expr a = l, b = unary(); l = sc -> { double d = b.eval(sc); return d == 0 ? 0 : a.eval(sc) % d; }; }
                else return l;
            }
        }

        Expr unary() {
            skip();
            if (peek('-')) { i++; final Expr e = unary(); return sc -> -e.eval(sc); }
            if (peek('+')) { i++; return unary(); }
            if (peek('!')) { i++; final Expr e = unary(); return sc -> e.eval(sc) == 0 ? 1 : 0; }
            return primary();
        }

        Expr primary() {
            skip();
            if (peek('(')) {
                i++;
                Expr e = ternary();
                skip();
                if (peek(')')) i++;
                return e;
            }
            if (peek('{')) {
                int depth = 0, start = ++i;
                while (i < s.length() && !(s.charAt(i) == '}' && depth == 0)) { if (s.charAt(i) == '{') depth++; if (s.charAt(i) == '}') depth--; i++; }
                Expr e = new Parser(s.substring(start, Math.min(i, s.length()))).program();
                i++;
                return e;
            }
            if (peek('\'')) {
                int end = s.indexOf('\'', i + 1);
                i = end < 0 ? s.length() : end + 1;
                return ZERO;
            }
            char c = at(i);
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                if (i < s.length() && s.charAt(i) == 'f') i++;
                final double v = Double.parseDouble(s.substring(start, i).replace("f", ""));
                return constant(v);
            }
            String name = identifier();
            if (name == null) throw new IllegalArgumentException("Unexpected '" + c + "' in " + s);
            if (name.equals("true")) return constant(1);
            if (name.equals("false")) return constant(0);
            skip();
            if (peek('(')) {
                i++;
                List<Expr> args = new ArrayList<Expr>();
                skip();
                while (!peek(')') && i < s.length()) {
                    args.add(ternary());
                    skip();
                    if (peek(',')) i++;
                    skip();
                }
                i++;
                return call(normalise(name), args.toArray(new Expr[0]));
            }
            final String key = normalise(name);
            if (key.equals("math.pi")) return constant(Math.PI);
            if (peek('=') && at(i + 1) != '=') {
                i++;
                final Expr v = ternary();
                return sc -> { double d = v.eval(sc); sc.vars.put(key, d); return d; };
            }
            return new Var(key);
        }

        String identifier() {
            skip();
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
            if (i == start || Character.isDigit(s.charAt(start))) { i = start; return null; }
            return s.substring(start, i);
        }

        void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        char at(int k) { return k < s.length() ? s.charAt(k) : '\0'; }
    }

    static String normalise(String name) {
        if (name.startsWith("q.")) return "query." + name.substring(2);
        if (name.startsWith("v.")) return "variable." + name.substring(2);
        if (name.startsWith("t.")) return "temp." + name.substring(2);
        if (name.startsWith("c.")) return "context." + name.substring(2);
        return name;
    }

    private static Expr call(final String fn, final Expr[] a) {
        switch (fn) {
            case "math.cos": return sc -> Math.cos(Math.toRadians(arg(a, 0, sc)));
            case "math.sin": return sc -> Math.sin(Math.toRadians(arg(a, 0, sc)));
            case "math.acos": return sc -> Math.toDegrees(Math.acos(arg(a, 0, sc)));
            case "math.asin": return sc -> Math.toDegrees(Math.asin(arg(a, 0, sc)));
            case "math.atan": return sc -> Math.toDegrees(Math.atan(arg(a, 0, sc)));
            case "math.atan2": return sc -> Math.toDegrees(Math.atan2(arg(a, 0, sc), arg(a, 1, sc)));
            case "math.abs": return sc -> Math.abs(arg(a, 0, sc));
            case "math.ceil": return sc -> Math.ceil(arg(a, 0, sc));
            case "math.floor": return sc -> Math.floor(arg(a, 0, sc));
            case "math.round": return sc -> Math.round(arg(a, 0, sc));
            case "math.trunc": return sc -> { double v = arg(a, 0, sc); return v < 0 ? Math.ceil(v) : Math.floor(v); };
            case "math.sqrt": return sc -> Math.sqrt(arg(a, 0, sc));
            case "math.exp": return sc -> Math.exp(arg(a, 0, sc));
            case "math.ln": return sc -> Math.log(arg(a, 0, sc));
            case "math.pow": return sc -> Math.pow(arg(a, 0, sc), arg(a, 1, sc));
            case "math.mod": return sc -> { double d = arg(a, 1, sc); return d == 0 ? 0 : arg(a, 0, sc) % d; };
            case "math.min": return sc -> Math.min(arg(a, 0, sc), arg(a, 1, sc));
            case "math.max": return sc -> Math.max(arg(a, 0, sc), arg(a, 1, sc));
            case "math.clamp": return sc -> Math.max(arg(a, 1, sc), Math.min(arg(a, 2, sc), arg(a, 0, sc)));
            case "math.lerp": return sc -> { double x = arg(a, 0, sc); return x + (arg(a, 1, sc) - x) * arg(a, 2, sc); };
            case "math.lerprotate": return sc -> {
                double from = arg(a, 0, sc), to = arg(a, 1, sc), t = arg(a, 2, sc);
                double d = ((to - from) % 360 + 540) % 360 - 180;
                return from + d * t;
            };
            case "math.hermite_blend": return sc -> { double t = arg(a, 0, sc); return 3 * t * t - 2 * t * t * t; };
            case "math.min_angle": return sc -> { double v = arg(a, 0, sc) % 360; if (v >= 180) v -= 360; if (v < -180) v += 360; return v; };
            case "math.sign": return sc -> Math.signum(arg(a, 0, sc));
            case "math.random": return sc -> { double lo = arg(a, 0, sc), hi = arg(a, 1, sc); return lo + RANDOM.nextDouble() * (hi - lo); };
            case "math.random_integer": return sc -> { double lo = arg(a, 0, sc), hi = arg(a, 1, sc); return Math.floor(lo + RANDOM.nextDouble() * (hi - lo + 1)); };
            case "math.die_roll": return sc -> { double n = arg(a, 0, sc), lo = arg(a, 1, sc), hi = arg(a, 2, sc), t = 0; for (int k = 0; k < n; k++) t += lo + RANDOM.nextDouble() * (hi - lo); return t; };
            default:
                if (fn.startsWith("lunar.")) return sc -> {
                    Function f = FUNCTIONS.get(fn);
                    if (f == null) return 0;
                    double[] values = new double[f.params.length];
                    for (int k = 0; k < values.length; k++) values[k] = arg(a, k, sc);
                    Double[] saved = new Double[values.length];
                    for (int k = 0; k < values.length; k++) { saved[k] = sc.vars.get(f.params[k]); sc.vars.put(f.params[k], values[k]); }
                    try { return f.body.eval(sc); }
                    finally { for (int k = 0; k < values.length; k++) { if (saved[k] == null) sc.vars.remove(f.params[k]); else sc.vars.put(f.params[k], saved[k]); } }
                };

                return sc -> fn.startsWith("query.") && sc.queries != null && a.length == 0 ? sc.queries.query(fn.substring(6)) : 0;
        }
    }

    private static double arg(Expr[] a, int k, Scope sc) { return k < a.length ? a[k].eval(sc) : 0; }
}
