package com.example.lunarforge.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

public final class CosmeticTextures {
    private static final int MAX_FRAME = 256;
    private static final int CACHE = 96;
    private static final ExecutorService DECODE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LunarForge cosmetic decode"); t.setDaemon(true); return t;
    });
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>(16, .75f, true);

    private CosmeticTextures() {}

    private static final class Entry {
        volatile Future<Frames> loading;
        Frames frames;
        boolean failed;
        DynamicTexture texture;
        ResourceLocation location;
        int shownFrame = -1;
    }

    static final class Frames {
        final int width, height, frameTime;
        final int[][] pixels;
        Frames(int width, int height, int frameTime, int[][] pixels) { this.width = width; this.height = height; this.frameTime = frameTime; this.pixels = pixels; }
    }

    public static ResourceLocation get(Cosmetic c) { return get(c.resource, c.type == CosmeticType.CLOAK); }

    public static ResourceLocation get(final String path, final boolean cloak) {
        Entry e = ENTRIES.get(path);
        if (e == null) {
            e = new Entry();
            ENTRIES.put(path, e);
            final Future<byte[]> image = LunarCdn.fetch(path);
            final Future<byte[]> meta = LunarCdn.has(path + ".mcmeta") ? LunarCdn.fetch(path + ".mcmeta") : null;
            e.loading = DECODE.submit(() -> decode(cloak, image.get(), meta == null ? null : meta.get()));
            evict();
        }
        if (e.failed) return null;
        if (e.frames == null) {
            if (!e.loading.isDone()) return null;
            try { e.frames = e.loading.get(); } catch (Exception ex) { e.frames = null; }
            if (e.frames == null) { e.failed = true; return null; }
        }
        if (e.texture == null) {
            e.texture = new DynamicTexture(e.frames.width, e.frames.height);
            e.location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("lunarforge_cosmetic_" + Integer.toHexString(path.hashCode()), e.texture);
        }
        int frame = e.frames.pixels.length == 1 ? 0
            : (int)(Minecraft.getMinecraft().theWorld == null ? System.currentTimeMillis() / 50 : Minecraft.getMinecraft().theWorld.getTotalWorldTime())
              / e.frames.frameTime % e.frames.pixels.length;
        if (frame != e.shownFrame) {
            System.arraycopy(e.frames.pixels[frame], 0, e.texture.getTextureData(), 0, e.frames.pixels[frame].length);
            e.texture.updateDynamicTexture();
            e.shownFrame = frame;
        }
        return e.location;
    }

    private static void evict() {
        Iterator<Map.Entry<String, Entry>> it = ENTRIES.entrySet().iterator();
        while (ENTRIES.size() > CACHE && it.hasNext()) {
            Entry old = it.next().getValue();
            if (old.location != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(old.location);
            it.remove();
        }
    }

    private static Frames decode(boolean cloak, byte[] data, byte[] meta) throws Exception {
        if (data == null) return null;
        BufferedImage img = read(data);
        if (img == null) return null;
        int w = img.getWidth(), frameH = img.getHeight(), frameTime = 1;
        int[] order = null;
        if (meta != null) {
            JsonObject anim = new JsonParser().parse(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animation");
            if (anim != null) {
                if (anim.has("frametime")) frameTime = Math.max(1, anim.get("frametime").getAsInt());

                if (anim.has("width") && anim.has("height")) frameH = Math.round(w * anim.get("height").getAsFloat() / anim.get("width").getAsFloat());
                else frameH = w;
                if (anim.has("frames")) {
                    List<Integer> list = new ArrayList<Integer>();
                    for (JsonElement f : anim.getAsJsonArray("frames"))
                        list.add(f.isJsonObject() ? f.getAsJsonObject().get("index").getAsInt() : f.getAsInt());
                    order = new int[list.size()];
                    for (int i = 0; i < order.length; i++) order[i] = list.get(i);
                }
            }
        }
        frameH = Math.max(1, Math.min(frameH, img.getHeight()));
        int count = Math.max(1, img.getHeight() / frameH);
        if (order == null) { order = new int[count]; for (int i = 0; i < count; i++) order[i] = i; }
        BufferedImage[] frames = new BufferedImage[count];
        for (int i = 0; i < count; i++) frames[i] = img.getSubimage(0, i * frameH, w, frameH);

        int outW, outH;
        if (cloak) {
            boolean full = Math.abs((float)w / frameH - 2f) < .01f;
            outW = full ? w : Math.round(w * 64f / 22f);
            outH = full ? frameH : Math.round(frameH * 32f / 17f);
        } else { outW = w; outH = frameH; }
        float shrink = Math.min(1f, (float)MAX_FRAME * (cloak ? 64f / 22f : 1f) / Math.max(outW, outH));
        int tw = Math.max(1, Math.round(outW * shrink)), th = Math.max(1, Math.round(outH * shrink));
        int[][] pixels = new int[order.length][];
        for (int i = 0; i < order.length; i++) {
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, shrink < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(frames[Math.min(order[i], count - 1)], 0, 0, Math.round(w * shrink), Math.round(frameH * shrink), null);
            g.dispose();
            pixels[i] = out.getRGB(0, 0, tw, th, null, 0, tw);
        }
        return new Frames(tw, th, frameTime, pixels);
    }

    static BufferedImage read(byte[] data) throws Exception {
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E') {
            ImageReader reader = new com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi().createReaderInstance(null);
            try {
                reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(data)));
                return reader.read(0);
            } finally { reader.dispose(); }
        }
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}
