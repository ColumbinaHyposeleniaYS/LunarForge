package com.example.lunarforge.cosmetics.gecko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class GeoModel {
    public final List<Bone> roots = new ArrayList<Bone>();
    public final Map<String, Bone> bones = new HashMap<String, Bone>();

    public static final class Bone {
        public final String name;
        final float pivotX, pivotY, pivotZ;
        final float initRotX, initRotY, initRotZ;
        final List<Bone> children = new ArrayList<Bone>();

        final List<float[]> quads = new ArrayList<float[]>();
        int list = -1;

        public float rotX, rotY, rotZ, posX, posY, posZ, scaleX = 1, scaleY = 1, scaleZ = 1;
        public boolean hidden;

        Bone(String name, float[] pivot, float[] rot) {
            this.name = name;
            pivotX = -pivot[0]; pivotY = pivot[1]; pivotZ = pivot[2];
            initRotX = (float)Math.toRadians(-rot[0]); initRotY = (float)Math.toRadians(-rot[1]); initRotZ = (float)Math.toRadians(rot[2]);
            reset();
        }

        public void reset() {
            rotX = initRotX; rotY = initRotY; rotZ = initRotZ;
            posX = posY = posZ = 0; scaleX = scaleY = scaleZ = 1;
        }

        public float initRotX() { return initRotX; }
        public float initRotY() { return initRotY; }
        public float initRotZ() { return initRotZ; }
    }

    public static GeoModel parse(JsonObject root) {
        GeoModel model = new GeoModel();
        JsonObject geo = null;
        float texW = 64, texH = 64;
        if (root.has("minecraft:geometry")) {
            geo = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject d = geo.getAsJsonObject("description");
            if (d != null) {
                if (d.has("texture_width")) texW = d.get("texture_width").getAsFloat();
                if (d.has("texture_height")) texH = d.get("texture_height").getAsFloat();
            }
        } else {
            for (Map.Entry<String, JsonElement> e : root.entrySet())
                if (e.getKey().startsWith("geometry.") && e.getValue().isJsonObject()) { geo = e.getValue().getAsJsonObject(); break; }
            if (geo != null) {
                if (geo.has("texturewidth")) texW = geo.get("texturewidth").getAsFloat();
                if (geo.has("textureheight")) texH = geo.get("textureheight").getAsFloat();
            }
        }
        if (geo == null || !geo.has("bones")) return model;
        Map<String, String> parents = new LinkedHashMap<String, String>();
        for (JsonElement el : geo.getAsJsonArray("bones")) {
            JsonObject b = el.getAsJsonObject();
            String name = b.get("name").getAsString();
            Bone bone = new Bone(name, vec(b, "pivot", 0), vec(b, "rotation", 0));
            boolean boneMirror = b.has("mirror") && b.get("mirror").getAsBoolean();
            float boneInflate = b.has("inflate") ? b.get("inflate").getAsFloat() : 0;
            if (b.has("cubes"))
                for (JsonElement c : b.getAsJsonArray("cubes")) cube(bone, c.getAsJsonObject(), texW, texH, boneMirror, boneInflate);
            if (b.has("neverRender") && b.get("neverRender").getAsBoolean()) bone.hidden = true;
            model.bones.put(name, bone);
            parents.put(name, b.has("parent") ? b.get("parent").getAsString() : null);
        }
        for (Map.Entry<String, String> e : parents.entrySet()) {
            Bone bone = model.bones.get(e.getKey());
            Bone parent = e.getValue() == null ? null : model.bones.get(e.getValue());
            if (parent != null && parent != bone) parent.children.add(bone); else model.roots.add(bone);
        }
        return model;
    }

    private static float[] vec(JsonObject o, String key, float def) {
        if (!o.has(key) || !o.get(key).isJsonArray()) return new float[]{def, def, def};
        JsonArray a = o.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static void cube(Bone bone, JsonObject c, float texW, float texH, boolean boneMirror, float boneInflate) {
        float[] origin = vec(c, "origin", 0), size = vec(c, "size", 0);
        float inflate = (c.has("inflate") ? c.get("inflate").getAsFloat() : boneInflate) / 16f;
        boolean mirror = c.has("mirror") ? c.get("mirror").getAsBoolean() : boneMirror;
        float ox = -(origin[0] + size[0]) / 16f, oy = origin[1] / 16f, oz = origin[2] / 16f;
        float sx = size[0] / 16f, sy = size[1] / 16f, sz = size[2] / 16f;
        float x0 = ox - inflate, y0 = oy - inflate, z0 = oz - inflate, x1 = ox + sx + inflate, y1 = oy + sy + inflate, z1 = oz + sz + inflate;
        float[] P1 = {x0, y0, z0}, P2 = {x0, y0, z1}, P3 = {x0, y1, z0}, P4 = {x0, y1, z1},
                P5 = {x1, y0, z0}, P6 = {x1, y0, z1}, P7 = {x1, y1, z0}, P8 = {x1, y1, z1};

        float[][] faceUv = new float[6][];
        JsonElement uv = c.get("uv");
        if (uv != null && uv.isJsonArray()) {
            float u = uv.getAsJsonArray().get(0).getAsFloat(), v = uv.getAsJsonArray().get(1).getAsFloat();
            float w = (float)Math.floor(size[0]), h = (float)Math.floor(size[1]), d = (float)Math.floor(size[2]);
            faceUv[0] = new float[]{u + d + w, v + d, d, h};
            faceUv[1] = new float[]{u, v + d, d, h};
            faceUv[2] = new float[]{u + d, v + d, w, h};
            faceUv[3] = new float[]{u + d + w + d, v + d, w, h};
            faceUv[4] = new float[]{u + d, v, w, d};
            faceUv[5] = new float[]{u + d + w, v + d, w, -d};
        } else if (uv != null && uv.isJsonObject()) {
            String[] names = {"west", "east", "north", "south", "up", "down"};
            for (int k = 0; k < 6; k++) {
                JsonObject f = uv.getAsJsonObject().getAsJsonObject(names[k]);
                if (f == null || !f.has("uv")) continue;
                JsonArray a = f.getAsJsonArray("uv");
                JsonArray s = f.has("uv_size") ? f.getAsJsonArray("uv_size") : null;
                faceUv[k] = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), s == null ? 0 : s.get(0).getAsFloat(), s == null ? 0 : s.get(1).getAsFloat()};
            }
        }
        float[][][] verts = {
            {P4, P3, P1, P2}, {P7, P8, P6, P5}, {P3, P7, P5, P1}, {P8, P4, P2, P6}, {P4, P8, P7, P3}, {P1, P5, P6, P2}};
        float[][] normals = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, -1, 0}};

        float[] pv = vec(c, "pivot", 0), rot = vec(c, "rotation", 0);
        float px = -pv[0] / 16f, py = pv[1] / 16f, pz = pv[2] / 16f;
        float rx = (float)Math.toRadians(-rot[0]), ry = (float)Math.toRadians(-rot[1]), rz = (float)Math.toRadians(rot[2]);

        for (int k = 0; k < 6; k++) {
            float[] f = faceUv[k];
            if (f == null) continue;
            float u0 = f[0] / texW, v0 = f[1] / texH, u1 = (f[0] + f[2]) / texW, v1 = (f[1] + f[3]) / texH;
            float[][] q = verts[k].clone();
            float[][] tex = {{u1, v0}, {u0, v0}, {u0, v1}, {u1, v1}};
            float[] n = normals[k].clone();
            if (mirror) {
                tex = new float[][]{{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                n[0] = -n[0];
            }
            float[] out = new float[4 * 5 + 3];
            for (int j = 0; j < 4; j++) {
                float[] p = rotate(q[j], px, py, pz, rx, ry, rz);
                out[j * 5] = p[0]; out[j * 5 + 1] = p[1]; out[j * 5 + 2] = p[2];
                out[j * 5 + 3] = tex[j][0]; out[j * 5 + 4] = tex[j][1];
            }
            float[] rn = rotate(n, 0, 0, 0, rx, ry, rz);
            out[20] = rn[0]; out[21] = rn[1]; out[22] = rn[2];
            bone.quads.add(out);
        }
    }

    private static float[] rotate(float[] p, float px, float py, float pz, float rx, float ry, float rz) {
        double x = p[0] - px, y = p[1] - py, z = p[2] - pz;
        if (rx != 0) { double c = Math.cos(rx), s = Math.sin(rx), ny = y * c - z * s, nz = y * s + z * c; y = ny; z = nz; }
        if (ry != 0) { double c = Math.cos(ry), s = Math.sin(ry), nx = x * c + z * s, nz = -x * s + z * c; x = nx; z = nz; }
        if (rz != 0) { double c = Math.cos(rz), s = Math.sin(rz), nx = x * c - y * s, ny = x * s + y * c; x = nx; y = ny; }
        return new float[]{(float)(x + px), (float)(y + py), (float)(z + pz)};
    }

    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Bone bone : bones.values())
            for (float[] q : bone.quads)
                for (int j = 0; j < 4; j++)
                    for (int k = 0; k < 3; k++) { b[k] = Math.min(b[k], q[j * 5 + k]); b[k + 3] = Math.max(b[k + 3], q[j * 5 + k]); }
        return b[0] > b[3] ? new float[6] : b;
    }

    public boolean hasCubes(String name) { Bone b = bones.get(name); return b != null && hasCubes(b); }

    private static boolean hasCubes(Bone b) {
        if (!b.quads.isEmpty()) return true;
        for (Bone c : b.children) if (hasCubes(c)) return true;
        return false;
    }

    public void resetPose() { for (Bone b : bones.values()) b.reset(); }

    public void render() { for (Bone b : roots) render(b); }

    private static void render(Bone b) {
        if (b.hidden) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-b.posX / 16f, b.posY / 16f, b.posZ / 16f);
        GlStateManager.translate(b.pivotX / 16f, b.pivotY / 16f, b.pivotZ / 16f);
        if (b.rotZ != 0) GlStateManager.rotate((float)Math.toDegrees(b.rotZ), 0, 0, 1);
        if (b.rotY != 0) GlStateManager.rotate((float)Math.toDegrees(b.rotY), 0, 1, 0);
        if (b.rotX != 0) GlStateManager.rotate((float)Math.toDegrees(b.rotX), 1, 0, 0);
        GlStateManager.scale(b.scaleX, b.scaleY, b.scaleZ);
        GlStateManager.translate(-b.pivotX / 16f, -b.pivotY / 16f, -b.pivotZ / 16f);
        if (!b.quads.isEmpty()) {
            if (b.list < 0) {
                b.list = GLAllocation.generateDisplayLists(1);
                GL11.glNewList(b.list, GL11.GL_COMPILE);
                WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
                for (float[] q : b.quads)
                    for (int j = 0; j < 4; j++)
                        wr.pos(q[j * 5], q[j * 5 + 1], q[j * 5 + 2]).tex(q[j * 5 + 3], q[j * 5 + 4]).normal(q[20], q[21], q[22]).endVertex();
                Tessellator.getInstance().draw();
                GL11.glEndList();
            }
            GL11.glCallList(b.list);
        }
        for (Bone child : b.children) render(child);
        GlStateManager.popMatrix();
    }

    public void delete() {
        for (Bone b : bones.values()) if (b.list >= 0) { GLAllocation.deleteDisplayLists(b.list); b.list = -1; }
    }
}
