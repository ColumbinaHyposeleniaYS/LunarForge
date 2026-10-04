package com.example.lunarforge.cosmetics.emote;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Bobj {
    public final List<Bone> bones = new ArrayList<Bone>();
    public final Map<String, Integer> boneIndex = new HashMap<String, Integer>();
    public final Map<String, Mesh> meshes = new LinkedHashMap<String, Mesh>();
    public final Map<String, Action> actions = new HashMap<String, Action>();

    public static final class Bone {
        public final String name; public final int parent;

        final float[] rest, invRest, rel;
        Bone(String name, int parent, float[] rest, float[] invRest, float[] rel) { this.name = name; this.parent = parent; this.rest = rest; this.invRest = invRest; this.rel = rel; }
    }

    public static final class Mesh {
        public final String name;
        float[] pos;
        int[][] weightBones;
        float[][] weights;
        float[] uv, normals;
        int[] tris;
        Mesh(String name) { this.name = name; }
    }

    static final class Channel {
        float[] k = new float[0]; int n;
        void add(float frame, float value, int interp, float lx, float ly, float rx, float ry) {
            if ((n + 1) * 7 > k.length) k = Arrays.copyOf(k, Math.max(28, k.length * 2));
            int o = n++ * 7;
            k[o] = frame; k[o + 1] = value; k[o + 2] = interp; k[o + 3] = lx; k[o + 4] = ly; k[o + 5] = rx; k[o + 6] = ry;
        }

        float eval(float f) {
            if (n == 0) return 0;
            if (f <= k[0]) return k[1];
            int last = (n - 1) * 7;
            if (f >= k[last]) return k[last + 1];
            int i = 0;
            while (i + 1 < n && k[(i + 1) * 7] < f) i++;
            int a = i * 7, b = (i + 1) * 7;
            float f0 = k[a], v0 = k[a + 1], f1 = k[b], v1 = k[b + 1];
            switch ((int)k[a + 2]) {
                case 0: return v0;
                case 1: return v0 + (v1 - v0) * (f - f0) / (f1 - f0);
                default: {
                    float x1 = k[a + 5], y1 = k[a + 6], x2 = k[b + 3], y2 = k[b + 4];
                    float lo = 0, hi = 1, t = .5f;
                    for (int it = 0; it < 24; it++) {
                        t = (lo + hi) / 2;
                        float x = bez(f0, x1, x2, f1, t);
                        if (x < f) lo = t; else hi = t;
                    }
                    return bez(v0, y1, y2, v1, t);
                }
            }
        }

        private static float bez(float p0, float p1, float p2, float p3, float t) {
            float u = 1 - t;
            return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
        }

        float lastFrame() { return n == 0 ? 0 : k[(n - 1) * 7]; }
    }

    public static final class Action {
        public final String name;
        final Map<String, Channel[]> bones = new HashMap<String, Channel[]>();
        float length;
        Action(String name) { this.name = name; }
    }

    public Bobj() {}

    public Bobj(Bobj armature) { bones.addAll(armature.bones); boneIndex.putAll(armature.boneIndex); }

    public static Bobj parse(byte[] data) throws IOException { Bobj b = new Bobj(); b.read(data); return b; }

    public void read(byte[] data) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), 1 << 16);
        List<float[]> v = new ArrayList<float[]>(), vt = new ArrayList<float[]>(), vn = new ArrayList<float[]>();
        List<int[]> vwBones = new ArrayList<int[]>(); List<float[]> vwW = new ArrayList<float[]>();
        List<String> pendingNames = new ArrayList<String>(); List<Float> pendingW = new ArrayList<Float>();
        List<String> vwNames = null;
        Map<Mesh, List<Integer>> faces = new LinkedHashMap<Mesh, List<Integer>>();
        List<String[]> rawBones = new ArrayList<String[]>();
        List<List<String>> rawWeightNames = new ArrayList<List<String>>();
        Mesh mesh = null; Action action = null; Channel[] bone = null; Channel channel = null;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "arm_bone": rawBones.add(line.trim().split(" ", -1)); break;
                case "o": mesh = new Mesh(p[1]); meshes.put(p[1], mesh); faces.put(mesh, new ArrayList<Integer>()); break;
                case "v":
                    flushWeights(vwW, rawWeightNames, pendingNames, pendingW, v.size() > 0);
                    v.add(new float[]{f(p[1]), f(p[2]), f(p[3])});
                    break;
                case "vw": pendingNames.add(p[1]); pendingW.add(f(p[2])); break;
                case "vt": vt.add(new float[]{f(p[1]), 1 - f(p[2])}); break;
                case "vn": vn.add(new float[]{f(p[1]), f(p[2]), f(p[3])}); break;
                case "f":
                    if (mesh == null) break;
                    for (int i = 1; i <= 3; i++) {
                        String[] c = p[i].split("/");
                        List<Integer> fl = faces.get(mesh);
                        fl.add(Integer.parseInt(c[0]) - 1);
                        fl.add(c.length > 1 && !c[1].isEmpty() ? Integer.parseInt(c[1]) - 1 : -1);
                        fl.add(c.length > 2 && !c[2].isEmpty() ? Integer.parseInt(c[2]) - 1 : -1);
                    }
                    break;
                case "an": action = actions.get(p[1]); if (action == null) { action = new Action(p[1]); actions.put(p[1], action); } break;
                case "ao": if (action != null) { bone = action.bones.get(p[1]); if (bone == null) { bone = new Channel[9]; action.bones.put(p[1], bone); } } break;
                case "ag": {
                    if (bone == null) break;
                    int base = p[1].equals("location") ? 0 : p[1].equals("rotation") ? 3 : p[1].equals("scale") ? 6 : -1;
                    int idx = Integer.parseInt(p[2]);
                    channel = base < 0 || idx > 2 ? null : (bone[base + idx] = new Channel());
                    break;
                }
                case "kf": {
                    if (channel == null) break;
                    int interp = p[3].equals("CONSTANT") ? 0 : p[3].equals("LINEAR") ? 1 : 2;
                    float fr = f(p[1]);
                    channel.add(fr, f(p[2]), interp, p.length > 4 ? f(p[4]) : fr, p.length > 5 ? f(p[5]) : 0, p.length > 6 ? f(p[6]) : fr, p.length > 7 ? f(p[7]) : 0);
                    if (fr > action.length) action.length = fr;
                    break;
                }
                default: break;
            }
        }
        flushWeights(vwW, rawWeightNames, pendingNames, pendingW, v.size() > 0);
        if (bones.isEmpty() && !rawBones.isEmpty()) buildArmature(rawBones);

        float[] pos = new float[v.size() * 3];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, pos, i * 3, 3);
        int[][] wb = new int[v.size()][]; float[][] ww = new float[v.size()][];
        for (int i = 0; i < v.size(); i++) {
            List<String> names = i < rawWeightNames.size() ? rawWeightNames.get(i) : Collections.<String>emptyList();
            float[] w = i < vwW.size() ? vwW.get(i) : new float[0];
            List<Integer> bi = new ArrayList<Integer>(); List<Float> bw = new ArrayList<Float>();
            for (int k = 0; k < names.size(); k++) {
                Integer b = boneIndex.get(names.get(k));
                if (b != null && w[k] > 0) { bi.add(b); bw.add(w[k]); }
            }
            wb[i] = new int[bi.size()]; ww[i] = new float[bi.size()];
            for (int k = 0; k < bi.size(); k++) { wb[i][k] = bi.get(k); ww[i][k] = bw.get(k); }
        }
        float[] uv = new float[vt.size() * 2]; for (int i = 0; i < vt.size(); i++) System.arraycopy(vt.get(i), 0, uv, i * 2, 2);
        float[] nm = new float[vn.size() * 3]; for (int i = 0; i < vn.size(); i++) System.arraycopy(vn.get(i), 0, nm, i * 3, 3);
        for (Map.Entry<Mesh, List<Integer>> e : faces.entrySet()) {
            Mesh m = e.getKey();
            m.pos = pos; m.weightBones = wb; m.weights = ww; m.uv = uv; m.normals = nm;
            m.tris = new int[e.getValue().size()];
            for (int i = 0; i < m.tris.length; i++) m.tris[i] = e.getValue().get(i);
        }
    }

    private static void flushWeights(List<float[]> vwW, List<List<String>> names, List<String> pendingNames, List<Float> pendingW, boolean hadVertex) {
        if (!hadVertex) return;
        float[] w = new float[pendingW.size()];
        for (int i = 0; i < w.length; i++) w[i] = pendingW.get(i);
        vwW.add(w); names.add(new ArrayList<String>(pendingNames));
        pendingNames.clear(); pendingW.clear();
    }

    private void buildArmature(List<String[]> raw) {
        List<float[]> rests = new ArrayList<float[]>();
        List<String> parents = new ArrayList<String>();
        for (String[] r : raw) {
            List<String> t = new ArrayList<String>(Arrays.asList(r));
            String name = t.get(1), parent = t.get(2);
            int start = 3;
            float[] m = new float[16];
            for (int i = 0; i < 16; i++) {
                float val = Float.parseFloat(t.get(start + 3 + i));
                m[(i % 4) * 4 + i / 4] = val;
            }
            boneIndex.put(name, bones.size());
            bones.add(null);
            rests.add(m); parents.add(parent);
            bones.set(bones.size() - 1, new Bone(name, -1, m, null, null));
        }
        for (int i = 0; i < bones.size(); i++) {
            Integer p = parents.get(i).isEmpty() ? null : boneIndex.get(parents.get(i));
            float[] rest = rests.get(i), inv = Mat.invert(rest);
            float[] rel = p == null ? rest : Mat.mul(Mat.invert(rests.get(p)), rest);
            bones.set(i, new Bone(bones.get(i).name, p == null ? -1 : p, rest, inv, rel));
        }
    }

    private static float f(String s) { return Float.parseFloat(s); }

    public float[][] pose(Action action, float frame) {
        float[][] world = new float[bones.size()][], skin = new float[bones.size()][];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            float[] local = Mat.identity();
            Channel[] c = action == null ? null : action.bones.get(b.name);
            if (c != null) {
                float lx = val(c[0], frame, 0), ly = val(c[1], frame, 0), lz = val(c[2], frame, 0);
                float rx = val(c[3], frame, 0), ry = val(c[4], frame, 0), rz = val(c[5], frame, 0);
                float sx = val(c[6], frame, 1), sy = val(c[7], frame, 1), sz = val(c[8], frame, 1);
                local = Mat.mul(Mat.translate(lx, ly, lz), Mat.mul(Mat.rotZ(rz), Mat.mul(Mat.rotY(ry), Mat.mul(Mat.rotX(rx), Mat.scale(sx, sy, sz)))));
            }
            float[] rel = Mat.mul(b.rel, local);
            world[i] = b.parent < 0 ? rel : Mat.mul(world[b.parent], rel);
            skin[i] = Mat.mul(world[i], b.invRest);
        }
        return skin;
    }

    private static float val(Channel c, float frame, float def) { return c == null ? def : c.eval(frame); }

    static final class Mat {
        static float[] identity() { float[] m = new float[16]; m[0] = m[5] = m[10] = m[15] = 1; return m; }
        static float[] translate(float x, float y, float z) { float[] m = identity(); m[12] = x; m[13] = y; m[14] = z; return m; }
        static float[] scale(float x, float y, float z) { float[] m = identity(); m[0] = x; m[5] = y; m[10] = z; return m; }
        static float[] rotX(float a) { float[] m = identity(); float c = (float)Math.cos(a), s = (float)Math.sin(a); m[5] = c; m[6] = s; m[9] = -s; m[10] = c; return m; }
        static float[] rotY(float a) { float[] m = identity(); float c = (float)Math.cos(a), s = (float)Math.sin(a); m[0] = c; m[2] = -s; m[8] = s; m[10] = c; return m; }
        static float[] rotZ(float a) { float[] m = identity(); float c = (float)Math.cos(a), s = (float)Math.sin(a); m[0] = c; m[1] = s; m[4] = -s; m[5] = c; return m; }
        static float[] mul(float[] a, float[] b) {
            float[] r = new float[16];
            for (int c = 0; c < 4; c++)
                for (int row = 0; row < 4; row++)
                    r[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] + a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3];
            return r;
        }

        static float[] invert(float[] m) {
            float[] inv = new float[16];
            inv[0] = m[5]*m[10]*m[15] - m[5]*m[11]*m[14] - m[9]*m[6]*m[15] + m[9]*m[7]*m[14] + m[13]*m[6]*m[11] - m[13]*m[7]*m[10];
            inv[4] = -m[4]*m[10]*m[15] + m[4]*m[11]*m[14] + m[8]*m[6]*m[15] - m[8]*m[7]*m[14] - m[12]*m[6]*m[11] + m[12]*m[7]*m[10];
            inv[8] = m[4]*m[9]*m[15] - m[4]*m[11]*m[13] - m[8]*m[5]*m[15] + m[8]*m[7]*m[13] + m[12]*m[5]*m[11] - m[12]*m[7]*m[9];
            inv[12] = -m[4]*m[9]*m[14] + m[4]*m[10]*m[13] + m[8]*m[5]*m[14] - m[8]*m[6]*m[13] - m[12]*m[5]*m[10] + m[12]*m[6]*m[9];
            inv[1] = -m[1]*m[10]*m[15] + m[1]*m[11]*m[14] + m[9]*m[2]*m[15] - m[9]*m[3]*m[14] - m[13]*m[2]*m[11] + m[13]*m[3]*m[10];
            inv[5] = m[0]*m[10]*m[15] - m[0]*m[11]*m[14] - m[8]*m[2]*m[15] + m[8]*m[3]*m[14] + m[12]*m[2]*m[11] - m[12]*m[3]*m[10];
            inv[9] = -m[0]*m[9]*m[15] + m[0]*m[11]*m[13] + m[8]*m[1]*m[15] - m[8]*m[3]*m[13] - m[12]*m[1]*m[11] + m[12]*m[3]*m[9];
            inv[13] = m[0]*m[9]*m[14] - m[0]*m[10]*m[13] - m[8]*m[1]*m[14] + m[8]*m[2]*m[13] + m[12]*m[1]*m[10] - m[12]*m[2]*m[9];
            inv[2] = m[1]*m[6]*m[15] - m[1]*m[7]*m[14] - m[5]*m[2]*m[15] + m[5]*m[3]*m[14] + m[13]*m[2]*m[7] - m[13]*m[3]*m[6];
            inv[6] = -m[0]*m[6]*m[15] + m[0]*m[7]*m[14] + m[4]*m[2]*m[15] - m[4]*m[3]*m[14] - m[12]*m[2]*m[7] + m[12]*m[3]*m[6];
            inv[10] = m[0]*m[5]*m[15] - m[0]*m[7]*m[13] - m[4]*m[1]*m[15] + m[4]*m[3]*m[13] + m[12]*m[1]*m[7] - m[12]*m[3]*m[5];
            inv[14] = -m[0]*m[5]*m[14] + m[0]*m[6]*m[13] + m[4]*m[1]*m[14] - m[4]*m[2]*m[13] - m[12]*m[1]*m[6] + m[12]*m[2]*m[5];
            inv[3] = -m[1]*m[6]*m[11] + m[1]*m[7]*m[10] + m[5]*m[2]*m[11] - m[5]*m[3]*m[10] - m[9]*m[2]*m[7] + m[9]*m[3]*m[6];
            inv[7] = m[0]*m[6]*m[11] - m[0]*m[7]*m[10] - m[4]*m[2]*m[11] + m[4]*m[3]*m[10] + m[8]*m[2]*m[7] - m[8]*m[3]*m[6];
            inv[11] = -m[0]*m[5]*m[11] + m[0]*m[7]*m[9] + m[4]*m[1]*m[11] - m[4]*m[3]*m[9] - m[8]*m[1]*m[7] + m[8]*m[3]*m[5];
            inv[15] = m[0]*m[5]*m[10] - m[0]*m[6]*m[9] - m[4]*m[1]*m[10] + m[4]*m[2]*m[9] + m[8]*m[1]*m[6] - m[8]*m[2]*m[5];
            float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
            if (det == 0) return identity();
            for (int i = 0; i < 16; i++) inv[i] /= det;
            return inv;
        }
    }
}
