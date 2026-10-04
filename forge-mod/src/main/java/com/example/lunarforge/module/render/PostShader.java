package com.example.lunarforge.module.render;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class PostShader {
    private static final String VERTEX = "#version 120\nvarying vec2 uv;\n"
        + "void main() { gl_Position = vec4(gl_Vertex.xy * 2.0 - 1.0, 0.2, 1.0); uv = gl_Vertex.xy; }\n";

    private final String name;
    private final String[] uniforms;
    private final String[] samplers;
    private final Map<String, Integer> locations = new HashMap<String, Integer>();
    private int program;
    private boolean failed;

    public PostShader(String name, String[] uniforms, String... samplers) {
        this.name = name;
        this.uniforms = uniforms;
        this.samplers = samplers;
    }

    public static boolean supported() { return OpenGlHelper.isFramebufferEnabled() && OpenGlHelper.shadersSupported; }

    private boolean compile() {
        if (program != 0) return true;
        if (failed) return false;
        try {
            StringBuilder header = new StringBuilder("#version 120\nvarying vec2 uv;\nuniform sampler2D DiffuseSampler;\n");
            for (String s : samplers) header.append("uniform sampler2D ").append(s).append(";\n");
            for (String u : uniforms) header.append("uniform ").append(u).append(";\n");
            header.append("#define SAMPLE texture2D\n#define OUT_COLOR gl_FragColor\n");
            int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
            int fragment = shader(GL20.GL_FRAGMENT_SHADER, header + read());
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vertex);
            GL20.glAttachShader(p, fragment);
            GL20.glLinkProgram(p);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(p, 4096);
                GL20.glDeleteProgram(p);
                throw new IllegalStateException(log);
            }
            program = p;
            return true;
        } catch (Exception e) {
            failed = true;
            LogManager.getLogger("LunarForge").error("Post shader {} failed to build: {}", name, e.getMessage());
            return false;
        }
    }

    private static int shader(int type, String source) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, source);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s, 4096);
            GL20.glDeleteShader(s);
            throw new IllegalStateException(log);
        }
        return s;
    }

    private String read() throws Exception {
        try (InputStream in = PostShader.class.getResourceAsStream("/assets/lunarforge/shaders/post/" + name + ".glsl")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private int location(String uniform) {
        Integer l = locations.get(uniform);
        if (l == null) { l = GL20.glGetUniformLocation(program, uniform); locations.put(uniform, l); }
        return l;
    }

    public void set(String uniform, float... v) {
        int l = location(uniform);
        if (l < 0) return;
        switch (v.length) {
            case 1: GL20.glUniform1f(l, v[0]); break;
            case 2: GL20.glUniform2f(l, v[0], v[1]); break;
            case 3: GL20.glUniform3f(l, v[0], v[1], v[2]); break;
            default: GL20.glUniform4f(l, v[0], v[1], v[2], v[3]); break;
        }
    }

    public void sampler(String sampler, Framebuffer source) {
        int unit = 2 + extraUnits++;
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        GlStateManager.bindTexture(source.framebufferTexture);
        GL20.glUniform1i(location(sampler), unit);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private int extraUnits;

    public interface Pass { void uniforms(PostShader shader); }

    public boolean apply(Framebuffer in, Framebuffer out, Pass pass) {
        if (!supported() || !compile()) return false;
        out.bindFramebuffer(true);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.colorMask(true, true, true, true);
        GL20.glUseProgram(program);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(in.framebufferTexture);
        GL20.glUniform1i(location("DiffuseSampler"), 0);
        extraUnits = 0;
        if (pass != null) pass.uniforms(this);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        wr.pos(0, 0, 0).endVertex();
        wr.pos(1, 0, 0).endVertex();
        wr.pos(1, 1, 0).endVertex();
        wr.pos(0, 1, 0).endVertex();
        tess.draw();
        GL20.glUseProgram(0);
        for (int i = 0; i < extraUnits; i++) {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit + 2 + i);
            GlStateManager.bindTexture(0);
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(0);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        return true;
    }

    public boolean applyInPlace(Framebuffer target, Framebuffer scratch, Pass pass) {
        if (!apply(target, scratch, pass)) return false;
        copy(scratch, target);
        return true;
    }

    public static void copy(Framebuffer from, Framebuffer to) {
        to.bindFramebuffer(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.bindTexture(from.framebufferTexture);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 1, 0, 1, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, 0, 0).tex(0, 0).endVertex();
        wr.pos(1, 0, 0).tex(1, 0).endVertex();
        wr.pos(1, 1, 0).tex(1, 1).endVertex();
        wr.pos(0, 1, 0).tex(0, 1).endVertex();
        tess.draw();
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.bindTexture(0);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
    }

    public static Framebuffer match(Framebuffer existing, Framebuffer like) {
        if (existing == null) {
            Framebuffer f = new Framebuffer(like.framebufferWidth, like.framebufferHeight, false);
            f.setFramebufferFilter(GL11.GL_LINEAR);
            return f;
        }
        if (existing.framebufferWidth != like.framebufferWidth || existing.framebufferHeight != like.framebufferHeight) {
            existing.createBindFramebuffer(like.framebufferWidth, like.framebufferHeight);
            existing.setFramebufferFilter(GL11.GL_LINEAR);
        }
        return existing;
    }

    public void delete() {
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        locations.clear();
    }

    static Minecraft mc() { return Minecraft.getMinecraft(); }
}
