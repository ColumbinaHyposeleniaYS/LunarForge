package com.example.lunarforge.module.modules.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

final class SkinVoxels {
    private SkinVoxels() {}

    interface Image {
        int width();
        int height();
        boolean present(int x, int y);
        boolean solid(int x, int y);
    }

    enum Dir {
        DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1), WEST(0, -1), EAST(0, 1);
        final int axis, sign;
        Dir(int axis, int sign) { this.axis = axis; this.sign = sign; }
        int stepX() { return axis == 0 ? sign : 0; }
        int stepY() { return axis == 1 ? sign : 0; }
        int stepZ() { return axis == 2 ? sign : 0; }
        Dir opposite() {
            switch (this) {
                case UP: return DOWN;
                case DOWN: return UP;
                case NORTH: return SOUTH;
                case SOUTH: return NORTH;
                case EAST: return WEST;
                default: return EAST;
            }
        }
        static double choose(int axis, double x, double y, double z) { return axis == 0 ? x : axis == 1 ? y : z; }
    }

    static final class Mesh {
        float x, y, z, xRot, yRot, zRot;
        boolean visible = true;
        private final List<Voxel> voxels;
        private int list = -1;
        private float listScale;

        Mesh(List<Voxel> voxels) { this.voxels = voxels; }

        void copy(ModelRenderer part) {
            xRot = part.rotateAngleX; yRot = part.rotateAngleY; zRot = part.rotateAngleZ;
            x = part.rotationPointX; y = part.rotationPointY; z = part.rotationPointZ;
        }

        void pose(float px, float py, float pz) { x = px; y = py; z = pz; }
        void rotation(float rx, float ry, float rz) { xRot = rx; yRot = ry; zRot = rz; }

        void render(float scale) { render(scale, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f); }

        void render(float scale, float offsetX, float offsetY, float sx, float sy, float sz) {
            if (!visible || voxels.isEmpty()) return;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x * scale, y * scale, z * scale);
            if (yRot != 0.0f) GlStateManager.rotate(yRot * 57.29578f, 0.0f, 1.0f, 0.0f);
            if (xRot != 0.0f) GlStateManager.rotate(xRot * 57.29578f, 1.0f, 0.0f, 0.0f);
            if (zRot != 0.0f) GlStateManager.rotate(zRot * 57.29578f, 0.0f, 0.0f, 1.0f);
            GlStateManager.translate(offsetX * scale, offsetY * scale, 0.0f);
            GlStateManager.scale(sx, sy, sz);
            if (list < 0 || scale != listScale) {
                if (list >= 0) GLAllocation.deleteDisplayLists(list);
                list = GLAllocation.generateDisplayLists(1);
                listScale = scale;
                GL11.glNewList(list, GL11.GL_COMPILE);
                WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
                wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
                for (Voxel v : voxels) v.emit(wr, scale);
                Tessellator.getInstance().draw();
                GL11.glEndList();
            }
            GlStateManager.callList(list);
            GlStateManager.popMatrix();
        }

        void delete() {
            if (list >= 0) GLAllocation.deleteDisplayLists(list);
            list = -1;
        }
    }

    private static final class Vertex {
        final float x, y, z, u, v;
        Vertex(float x, float y, float z, float u, float v) { this.x = x; this.y = y; this.z = z; this.u = u; this.v = v; }
        Vertex uv(float nu, float nv) { return new Vertex(x, y, z, nu, nv); }
        float axis(int a) { return a == 0 ? x : a == 1 ? y : z; }
    }

    private static final class Face {
        final Vertex[] vertices;
        final float nx, ny, nz;
        Face(Vertex[] v, float u, float vv, float u2, float v2, float texW, float texH, boolean mirror, Dir dir) {
            vertices = v;
            v[0] = v[0].uv(u2 / texW, vv / texH);
            v[1] = v[1].uv(u / texW, vv / texH);
            v[2] = v[2].uv(u / texW, v2 / texH);
            v[3] = v[3].uv(u2 / texW, v2 / texH);
            if (mirror) {
                for (int i = 0; i < v.length / 2; i++) { Vertex t = v[i]; v[i] = v[v.length - 1 - i]; v[v.length - 1 - i] = t; }
            }
            nx = mirror ? -dir.stepX() : dir.stepX();
            ny = dir.stepY();
            nz = dir.stepZ();
        }
    }

    static final class Voxel {
        private final Face[] faces;

        Voxel(int u, int v, float x, float y, float z, float w, float h, float d, boolean mirror, float texW, float texH, Set<Dir> hidden, List<Dir[]> corners) {
            float x2 = x + w, y2 = y + h, z2 = z + d;
            if (mirror) { float t = x2; x2 = x; x = t; }
            Vertex a = new Vertex(x, y, z, 0, 0), b = new Vertex(x2, y, z, 0, 8), c = new Vertex(x2, y2, z, 8, 8), e = new Vertex(x, y2, z, 8, 0);
            Vertex f = new Vertex(x, y, z2, 0, 0), g = new Vertex(x2, y, z2, 0, 8), hh = new Vertex(x2, y2, z2, 8, 8), i = new Vertex(x, y2, z2, 8, 0);
            float u2 = u + 1.0f, v2 = v + 1.0f;
            Map<Integer, Dir[]> byAxis = new HashMap<Integer, Dir[]>();
            outer:
            for (Dir[] pair : corners) {
                inner:
                for (int axis = 0; axis < 3; axis++) {
                    for (Dir dir : pair) if (dir.axis == axis) continue inner;
                    byAxis.put(axis, pair);
                    continue outer;
                }
            }
            List<Face> out = new ArrayList<Face>();
            if (!hidden.contains(Dir.DOWN)) out.add(new Face(fold(new Vertex[]{g, f, a, b}, byAxis.get(1)), u, v, u2, v2, texW, texH, mirror, Dir.DOWN));
            if (!hidden.contains(Dir.UP)) out.add(new Face(fold(new Vertex[]{c, e, i, hh}, byAxis.get(1)), u, v, u2, v2, texW, texH, mirror, Dir.UP));
            if (!hidden.contains(Dir.NORTH)) out.add(new Face(fold(new Vertex[]{b, a, e, c}, byAxis.get(2)), u, v, u2, v2, texW, texH, mirror, Dir.NORTH));
            if (!hidden.contains(Dir.SOUTH)) out.add(new Face(fold(new Vertex[]{f, g, hh, i}, byAxis.get(2)), u, v, u2, v2, texW, texH, mirror, Dir.SOUTH));
            if (!hidden.contains(Dir.WEST)) out.add(new Face(fold(new Vertex[]{a, f, i, e}, byAxis.get(0)), u, v, u2, v2, texW, texH, mirror, Dir.WEST));
            if (!hidden.contains(Dir.EAST)) out.add(new Face(fold(new Vertex[]{g, b, c, hh}, byAxis.get(0)), u, v, u2, v2, texW, texH, mirror, Dir.EAST));
            faces = out.toArray(new Face[0]);
        }

        private static Vertex[] fold(Vertex[] v, Dir[] pair) {
            if (pair == null) return v;
            Vertex far = v[0];
            for (int n = 1; n < 4; n++) far = further(far, v[n], pair);
            int n = 0;
            for (int i = 0; i < 4; i++) if (v[i] != far) v[n++] = v[i];
            v[3] = v[2];
            return v;
        }

        private static Vertex further(Vertex a, Vertex b, Dir[] pair) {
            for (Dir dir : pair) {
                double d = (a.axis(dir.axis) - b.axis(dir.axis)) * dir.sign;
                if (d > 0.0) return a;
                if (d < 0.0) return b;
            }
            return a;
        }

        void emit(WorldRenderer wr, float scale) {
            for (Face face : faces) {
                for (int i = 0; i < 4; i++) {
                    Vertex v = face.vertices[i];
                    wr.pos(v.x * scale, v.y * scale, v.z * scale).tex(v.u, v.v).normal(face.nx, face.ny, face.nz).endVertex();
                }
            }
        }
    }

    private static final class UV { final int u, v; UV(int u, int v) { this.u = u; this.v = v; } }
    private static final class Pos { final int x, y, z; Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; } }

    static Mesh build(Image image, int w, int h, int d, int u, int v, boolean topPivot, float offsetY) {
        List<Voxel> voxels = new ArrayList<Voxel>();
        float ox = -w / 2.0f, oy = topPivot ? offsetY : -h + offsetY, oz = -d / 2.0f;
        try {
            for (Dir dir : Dir.values()) {
                UV size = faceSize(w, h, d, dir);
                for (int i = 0; i < size.u; i++) {
                    for (int j = 0; j < size.v; j++) pixel(image, voxels, ox, oy, oz, dir, w, h, d, new UV(i, j), new UV(u, v), size);
                }
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("LunarForge").warn("SkinLayers3D: " + e.getMessage());
            return new Mesh(new ArrayList<Voxel>());
        }
        return new Mesh(voxels);
    }

    private static UV faceSize(int w, int h, int d, Dir dir) {
        switch (dir) {
            case DOWN: case UP: return new UV(w, d);
            case NORTH: case SOUTH: return new UV(w, h);
            default: return new UV(d, h);
        }
    }

    private static UV texture(UV base, UV c, int w, int h, int d, Dir dir) {
        switch (dir) {
            case DOWN: return new UV(base.u + d + c.u, base.v + c.v);
            case UP: return new UV(base.u + w + d + c.u, base.v + c.v);
            case NORTH: return new UV(base.u + d + c.u, base.v + d + c.v);
            case SOUTH: return new UV(base.u + d + w + d + c.u, base.v + d + c.v);
            case WEST: return new UV(base.u + c.u, base.v + d + c.v);
            default: return new UV(base.u + d + w + c.u, base.v + d + c.v);
        }
    }

    private static Pos voxel(UV c, int w, int h, int d, Dir dir) {
        switch (dir) {
            case DOWN: return new Pos(c.u, 0, d - 1 - c.v);
            case UP: return new Pos(c.u, h - 1, d - 1 - c.v);
            case NORTH: return new Pos(c.u, c.v, 0);
            case SOUTH: return new Pos(w - 1 - c.u, c.v, d - 1);
            case WEST: return new Pos(0, c.v, d - 1 - c.u);
            default: return new Pos(w - 1, c.v, c.u);
        }
    }

    private static UV onFace(Pos p, int w, int h, int d, Dir dir) {
        switch (dir) {
            case DOWN: case UP: return new UV(p.x, d - 1 - p.z);
            case NORTH: return new UV(p.x, p.y);
            case SOUTH: return new UV(w - 1 - p.x, p.y);
            case WEST: return new UV(d - 1 - p.z, p.y);
            default: return new UV(p.z, p.y);
        }
    }

    private static boolean inside(UV c, UV size) { return c.u >= 0 && c.u < size.u && c.v >= 0 && c.v < size.v; }
    private static boolean present(Image img, UV t) { return img.present(t.u, t.v); }
    private static boolean solid(Image img, UV t) { return img.solid(t.u, t.v); }

    private static void pixel(Image img, List<Voxel> out, float ox, float oy, float oz, Dir dir, int w, int h, int d, UV c, UV base, UV size) {
        UV tex = texture(base, c, w, h, d, dir);
        if (!present(img, tex)) return;
        Pos p = voxel(c, w, h, d, dir);
        float x = ox + p.x, y = oy + p.y, z = oz + p.z;
        boolean isSolid = solid(img, tex);
        Set<Dir> hidden = new HashSet<Dir>();
        List<Dir[]> corners = new ArrayList<Dir[]>();
        boolean edge = false, wraps = false;
        for (Dir side : Dir.values()) {
            if (side.axis == dir.axis) continue;
            Pos n = new Pos(p.x + side.stepX(), p.y + side.stepY(), p.z + side.stepZ());
            UV nc = onFace(n, w, h, d, dir);
            if (inside(nc, size)) {
                UV nt = texture(base, nc, w, h, d, dir);
                if (present(img, nt)) {
                    if (isSolid && !solid(img, nt)) continue;
                    hidden.add(side);
                    continue;
                }
                Pos n2 = new Pos(n.x + side.stepX(), n.y + side.stepY(), n.z + side.stepZ());
                UV c2 = onFace(n2, w, h, d, dir);
                if (inside(c2, size)) continue;
                c2 = onFace(n2, w, h, d, side);
                UV t2 = texture(base, c2, w, h, d, side);
                if (!present(img, t2) || isSolid && !solid(img, t2)) continue;
                hidden.add(side);
                continue;
            }
            edge = true;
            UV around = onFace(p, w, h, d, side);
            if (present(img, texture(base, around, w, h, d, side))) {
                wraps = true;
                hidden.add(side);
                corners.add(new Dir[]{dir.opposite(), side});
                continue;
            }
            UV behind = onFace(new Pos(p.x - dir.stepX(), p.y - dir.stepY(), p.z - dir.stepZ()), w, h, d, side);
            if (!present(img, texture(base, behind, w, h, d, side))) continue;
            wraps = true;
        }
        if (!edge || wraps) hidden.add(dir.opposite());
        out.add(new Voxel(tex.u, tex.v, x, y, z, 1.0f, 1.0f, 1.0f, false, img.width(), img.height(), hidden, corners));
    }

    static Mesh[] body(Image image, boolean slim) {
        Mesh[] m = new Mesh[5];
        m[0] = build(image, 4, 12, 4, 0, 48, true, 0.0f);
        m[1] = build(image, 4, 12, 4, 0, 32, true, 0.0f);
        int arm = slim ? 3 : 4;
        m[2] = build(image, arm, 12, 4, 48, 48, true, -2.5f);
        m[3] = build(image, arm, 12, 4, 40, 32, true, -2.5f);
        m[4] = build(image, 8, 12, 4, 16, 32, true, -0.8f);
        return m;
    }

    static Mesh head(Image image) { return build(image, 8, 8, 8, 32, 0, false, 0.6f); }
}
