package com.example.lunarforge.cosmetics.render;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class ObjModel {
    private final float[] data;
    private int list = -1;

    private ObjModel(float[] data) { this.data = data; }

    public static ObjModel parse(byte[] bytes) throws IOException {
        List<float[]> v = new ArrayList<float[]>(), vt = new ArrayList<float[]>(), vn = new ArrayList<float[]>();
        List<Float> out = new ArrayList<Float>();
        BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "v": v.add(new float[]{f(p[1]), f(p[2]), f(p[3])}); break;
                case "vt": vt.add(new float[]{f(p[1]), 1f - f(p[2])}); break;
                case "vn": vn.add(new float[]{f(p[1]), f(p[2]), f(p[3])}); break;
                case "f": {
                    int n = p.length - 1;

                    for (int i = 1; i + 1 < n; i++) {
                        vertex(out, p[1], v, vt, vn);
                        vertex(out, p[1 + i], v, vt, vn);
                        vertex(out, p[2 + i], v, vt, vn);
                    }
                    break;
                }
                default: break;
            }
        }
        float[] data = new float[out.size()];
        for (int i = 0; i < data.length; i++) data[i] = out.get(i);
        return new ObjModel(data);
    }

    private static float f(String s) { return Float.parseFloat(s); }

    private static void vertex(List<Float> out, String spec, List<float[]> v, List<float[]> vt, List<float[]> vn) {
        String[] i = spec.split("/", -1);
        float[] pos = v.get(index(i[0], v.size()));
        float[] uv = i.length > 1 && !i[1].isEmpty() ? vt.get(index(i[1], vt.size())) : new float[]{0, 0};
        float[] n = i.length > 2 && !i[2].isEmpty() ? vn.get(index(i[2], vn.size())) : new float[]{0, 1, 0};
        out.add(pos[0]); out.add(pos[1]); out.add(pos[2]);
        out.add(uv[0]); out.add(uv[1]);
        out.add(n[0]); out.add(n[1]); out.add(n[2]);
    }

    private static int index(String s, int size) { int i = Integer.parseInt(s); return i < 0 ? size + i : i - 1; }

    public void render() {
        if (list < 0) {
            list = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(list, GL11.GL_COMPILE);
            WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
            wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
            for (int i = 0; i < data.length; i += 8)
                wr.pos(data[i], data[i + 1], data[i + 2]).tex(data[i + 3], data[i + 4]).normal(data[i + 5], data[i + 6], data[i + 7]).endVertex();
            Tessellator.getInstance().draw();
            GL11.glEndList();
        }
        GL11.glCallList(list);
    }

    public void delete() { if (list >= 0) { GLAllocation.deleteDisplayLists(list); list = -1; } }
}
