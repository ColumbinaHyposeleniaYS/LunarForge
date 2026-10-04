package com.example.lunarforge.cosmetics.gecko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.*;

public final class GeoAnimation {
    public final String name;
    public double length;

    public final boolean loop;
    private final Map<String, Channel[]> bones = new LinkedHashMap<String, Channel[]>();

    private GeoAnimation(String name, double length, boolean loop) { this.name = name; this.length = length; this.loop = loop; }

    static final class Key {
        final double time; final Molang.Expr[] value; final String easing; final double[] easingArgs;
        Key(double time, Molang.Expr[] value, String easing, double[] easingArgs) { this.time = time; this.value = value; this.easing = easing; this.easingArgs = easingArgs; }
    }

    static final class Channel {
        final List<Key> keys = new ArrayList<Key>();
        double[] eval(Molang.Scope sc, double t) {
            if (keys.size() == 1 || t <= keys.get(0).time) return vec(keys.get(0).value, sc);
            Key last = keys.get(keys.size() - 1);
            if (t >= last.time) return vec(last.value, sc);
            for (int i = 1; i < keys.size(); i++) {
                Key b = keys.get(i);
                if (t > b.time) continue;
                Key a = keys.get(i - 1);
                double span = b.time - a.time, p = span <= 0 ? 1 : (t - a.time) / span;
                p = Easing.apply(b.easing, b.easingArgs, p);
                double[] va = vec(a.value, sc), vb = vec(b.value, sc);
                return new double[]{va[0] + (vb[0] - va[0]) * p, va[1] + (vb[1] - va[1]) * p, va[2] + (vb[2] - va[2]) * p};
            }
            return vec(last.value, sc);
        }
    }

    private static double[] vec(Molang.Expr[] v, Molang.Scope sc) { return new double[]{v[0].eval(sc), v[1].eval(sc), v[2].eval(sc)}; }

    public static Map<String, GeoAnimation> parse(JsonObject root) {
        Map<String, GeoAnimation> out = new HashMap<String, GeoAnimation>();
        JsonObject anims = root.getAsJsonObject("animations");
        if (anims == null) return out;
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject a = e.getValue().getAsJsonObject();
            JsonElement loopEl = a.get("loop");
            boolean loop = loopEl != null && loopEl.isJsonPrimitive() && loopEl.getAsJsonPrimitive().isBoolean() && loopEl.getAsBoolean();
            double declared = a.has("animation_length") ? a.get("animation_length").getAsDouble() : -1;
            GeoAnimation anim = new GeoAnimation(e.getKey(), declared, loop);
            double longest = 0;
            JsonObject bones = a.getAsJsonObject("bones");
            if (bones != null) for (Map.Entry<String, JsonElement> b : bones.entrySet()) {
                if (!b.getValue().isJsonObject()) continue;
                JsonObject ch = b.getValue().getAsJsonObject();
                Channel[] channels = new Channel[3];
                String[] names = {"rotation", "position", "scale"};
                for (int k = 0; k < 3; k++) {
                    if (!ch.has(names[k])) continue;
                    channels[k] = channel(ch.get(names[k]), k == 2);
                    if (channels[k] != null) for (Key key : channels[k].keys) longest = Math.max(longest, key.time);
                }
                anim.bones.put(b.getKey(), channels);
            }
            if (declared <= 0) anim.length = longest;
            out.put(e.getKey(), anim);
        }
        return out;
    }

    private static Channel channel(JsonElement el, boolean scale) {
        Channel c = new Channel();
        if (el.isJsonObject() && !el.getAsJsonObject().has("vector") && !el.getAsJsonObject().has("post") && !el.getAsJsonObject().has("pre")) {
            List<Map.Entry<String, JsonElement>> frames = new ArrayList<Map.Entry<String, JsonElement>>(el.getAsJsonObject().entrySet());
            for (Map.Entry<String, JsonElement> f : frames) {
                double t;
                try { t = Double.parseDouble(f.getKey()); } catch (NumberFormatException ex) { continue; }
                JsonElement v = f.getValue();
                String easing = null; double[] args = null;
                if (v.isJsonObject()) {
                    JsonObject o = v.getAsJsonObject();
                    if (o.has("easing")) easing = o.get("easing").getAsString();
                    if (o.has("easingArgs")) {
                        JsonArray ea = o.getAsJsonArray("easingArgs");
                        args = new double[ea.size()];
                        for (int i = 0; i < args.length; i++) args[i] = ea.get(i).getAsDouble();
                    }
                    if (o.has("lerp_mode") && o.get("lerp_mode").getAsString().equals("step")) easing = "step";
                }
                c.keys.add(new Key(t, vector(v, scale), easing, args));
            }
            Collections.sort(c.keys, (a, b) -> Double.compare(a.time, b.time));
            if (c.keys.isEmpty()) return null;
        } else {
            c.keys.add(new Key(0, vector(el, scale), null, null));
        }
        return c;
    }

    private static Molang.Expr[] vector(JsonElement v, boolean scale) {
        if (v.isJsonObject()) {
            JsonObject o = v.getAsJsonObject();
            v = o.has("vector") ? o.get("vector") : o.has("post") ? o.get("post") : o.has("pre") ? o.get("pre") : new JsonPrimitive(scale ? 1 : 0);
            if (v.isJsonObject()) return vector(v, scale);
        }
        if (v.isJsonArray()) {
            JsonArray a = v.getAsJsonArray();
            Molang.Expr x = expr(a.get(0)), y = a.size() > 1 ? expr(a.get(1)) : x, z = a.size() > 2 ? expr(a.get(2)) : x;
            return new Molang.Expr[]{x, y, z};
        }
        Molang.Expr e = expr(v);
        return new Molang.Expr[]{e, e, e};
    }

    private static Molang.Expr expr(JsonElement e) {
        if (e == null || e.isJsonNull()) return Molang.ZERO;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) return Molang.constant(p.getAsDouble());
        if (p.isBoolean()) return Molang.constant(p.getAsBoolean() ? 1 : 0);
        String s = p.getAsString().trim();
        try { return Molang.constant(Double.parseDouble(s)); } catch (NumberFormatException ignored) { }
        return Molang.compile(s);
    }

    public void apply(GeoModel model, Molang.Scope sc, double time) {
        double t = length > 0 ? (loop ? time % length : Math.min(time, length)) : time;
        sc.vars.put("query.anim_time", t);
        for (Map.Entry<String, Channel[]> e : bones.entrySet()) {
            GeoModel.Bone bone = model.bones.get(e.getKey());
            if (bone == null) continue;
            Channel[] c = e.getValue();
            if (c[0] != null) {
                double[] r = c[0].eval(sc, t);
                bone.rotX = bone.initRotX() + (float)Math.toRadians(-r[0]);
                bone.rotY = bone.initRotY() + (float)Math.toRadians(-r[1]);
                bone.rotZ = bone.initRotZ() + (float)Math.toRadians(r[2]);
            }
            if (c[1] != null) {
                double[] p = c[1].eval(sc, t);
                bone.posX = (float)p[0]; bone.posY = (float)p[1]; bone.posZ = (float)p[2];
            }
            if (c[2] != null) {
                double[] s = c[2].eval(sc, t);
                bone.scaleX = (float)s[0]; bone.scaleY = (float)s[1]; bone.scaleZ = (float)s[2];
            }
        }
    }

    static final class Easing {
        static double apply(String name, double[] args, double t) {
            if (name == null) return t;
            switch (name) {
                case "step": return t >= 1 ? 1 : 0;
                case "linear": return t;
                case "easeInSine": return 1 - Math.cos(t * Math.PI / 2);
                case "easeOutSine": return Math.sin(t * Math.PI / 2);
                case "easeInOutSine": return -(Math.cos(Math.PI * t) - 1) / 2;
                case "easeInQuad": return t * t;
                case "easeOutQuad": return 1 - (1 - t) * (1 - t);
                case "easeInOutQuad": return t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
                case "easeInCubic": return t * t * t;
                case "easeOutCubic": return 1 - Math.pow(1 - t, 3);
                case "easeInOutCubic": return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
                case "easeInQuart": return t * t * t * t;
                case "easeOutQuart": return 1 - Math.pow(1 - t, 4);
                case "easeInOutQuart": return t < .5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
                case "easeInQuint": return Math.pow(t, 5);
                case "easeOutQuint": return 1 - Math.pow(1 - t, 5);
                case "easeInOutQuint": return t < .5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
                case "easeInExpo": return t == 0 ? 0 : Math.pow(2, 10 * t - 10);
                case "easeOutExpo": return t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
                case "easeInOutExpo": return t == 0 ? 0 : t == 1 ? 1 : t < .5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
                case "easeInCirc": return 1 - Math.sqrt(1 - t * t);
                case "easeOutCirc": return Math.sqrt(1 - Math.pow(t - 1, 2));
                case "easeInOutCirc": return t < .5 ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2 : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
                case "easeInBack": { double c = arg(args, 1.70158); return (c + 1) * t * t * t - c * t * t; }
                case "easeOutBack": { double c = arg(args, 1.70158); return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2); }
                case "easeInOutBack": {
                    double c = arg(args, 1.70158) * 1.525;
                    return t < .5 ? Math.pow(2 * t, 2) * ((c + 1) * 2 * t - c) / 2 : (Math.pow(2 * t - 2, 2) * ((c + 1) * (t * 2 - 2) + c) + 2) / 2;
                }
                case "easeInElastic": return t == 0 ? 0 : t == 1 ? 1 : -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3));
                case "easeOutElastic": return t == 0 ? 0 : t == 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * (2 * Math.PI / 3)) + 1;
                case "easeInOutElastic": {
                    double c = 2 * Math.PI / 4.5;
                    return t == 0 ? 0 : t == 1 ? 1 : t < .5 ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c)) / 2 : Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c) / 2 + 1;
                }
                case "easeInBounce": return 1 - bounce(1 - t);
                case "easeOutBounce": return bounce(t);
                case "easeInOutBounce": return t < .5 ? (1 - bounce(1 - 2 * t)) / 2 : (1 + bounce(2 * t - 1)) / 2;
                default: return t;
            }
        }

        private static double arg(double[] args, double def) { return args != null && args.length > 0 ? args[0] : def; }

        private static double bounce(double t) {
            double n = 7.5625, d = 2.75;
            if (t < 1 / d) return n * t * t;
            if (t < 2 / d) return n * (t -= 1.5 / d) * t + .75;
            if (t < 2.5 / d) return n * (t -= 2.25 / d) * t + .9375;
            return n * (t -= 2.625 / d) * t + .984375;
        }
    }
}
