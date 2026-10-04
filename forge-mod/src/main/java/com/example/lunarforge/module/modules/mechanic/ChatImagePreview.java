package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.util.Anim;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.net.InternetDomainName;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

final class ChatImagePreview {
    private static final ExecutorService DOWNLOADER = Executors.newFixedThreadPool(1,
        new ThreadFactoryBuilder().setNameFormat("Hover Image Preview Downloader #%d").setDaemon(true).build());
    private static final ResourceLocation TEXTURE = new ResourceLocation("lunarforge", "hover_image");
    private static final List<String> HOSTS = Arrays.asList("lunr.pics", "imgur.com", "i.imgur.com", "discordapp.com", "discordapp.net",
        "media.discordapp.net", "hypixel.net", "prnt.sc", "ibb.co", "wixmp.com", "fbcdn.net", "cdninstagram.com", "redd.it", "gyazo.com",
        "twimg.com", "mcstats.com", "lunarclientcdn.com", "moonsworth.store");
    private static final Pattern URL_PATTERN = Pattern.compile("https://[^\\s\"']+");

    private final LoadingCache<String, Future<BufferedImage>> cache = CacheBuilder.newBuilder().maximumSize(10L)
        .expireAfterAccess(5L, TimeUnit.MINUTES).softValues().build(new CacheLoader<String, Future<BufferedImage>>() {
            @Override public Future<BufferedImage> load(String url) { return download(url); }
        });
    private final List<String> customHosts = new ArrayList<String>();
    private final List<String> urls = new ArrayList<String>(2);
    private final Anim fullscreen = new Anim(250L);
    private DynamicTexture texture;
    private boolean showing;
    private String url;
    private Future<BufferedImage> image;
    private int cycle;

    void hosts(String[] domains) {
        customHosts.clear();
        for (String d : domains) customHosts.add(d.toLowerCase(Locale.ROOT));
    }

    void render(ModuleChat chat, int mouseX, int mouseY, int screenW, int screenH) {
        if (!chat.hoverImagePreview.on() || !showing || image == null || !image.isDone()) return;
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiChat)) { reset(); return; }
        try {
            BufferedImage img = image.get();
            if (img == null) return;
            double h = img.getHeight(), w = img.getWidth();
            double min = chat.minImageSize.value(), max = chat.maxImageSize.value();
            if (chat.fullscreenImage.on() && max < 100.0) {
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !fullscreen.running()) {
                    if (!fullscreen.started()) {
                        fullscreen.reversed(false);
                        fullscreen.start();
                    } else if (fullscreen.finished()) {
                        fullscreen.stop();
                        fullscreen.reversed(!fullscreen.reversed());
                        fullscreen.start();
                    }
                }
                max += (100.0 - max) * fullscreen.progress();
            }
            double ratio = w / h;
            if (h / screenH > w / screenW) {
                h = clamp(h, min / 100.0 * screenH, max / 100.0 * screenH);
                w = h * ratio;
            } else {
                w = clamp(w, min / 100.0 * screenW, max / 100.0 * screenW);
                h = w / ratio;
            }
            double x = mouseX, y = mouseY - h;
            if (y < 0.0 || y + h > screenH) y = 0.0;
            if (x + w > screenW) x = screenW - w;
            if (texture == null) {
                texture = new DynamicTexture(img);
                Minecraft.getMinecraft().getTextureManager().deleteTexture(TEXTURE);
                Minecraft.getMinecraft().getTextureManager().loadTexture(TEXTURE, texture);
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0f, 0.0f, 200.0f);
            Draw.texture(TEXTURE, (float)x, (float)y, (float)w, (float)h, -1);
            GlStateManager.popMatrix();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private Future<BufferedImage> download(String address) {
        final URL target;
        String domain;
        try {
            target = new URL(address);
            domain = InternetDomainName.from(target.getHost()).topPrivateDomain().toString();
        } catch (Exception e) {
            return Futures.immediateFuture(null);
        }
        if (!HOSTS.contains(domain) && !customHosts.contains(domain)) return Futures.immediateFuture(null);
        return DOWNLOADER.submit(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection)target.openConnection();
                conn.setRequestMethod("GET");
                conn.addRequestProperty("Accept", "image/*");
                conn.addRequestProperty("User-Agent", "LunarClient-Java");
                try (InputStream in = conn.getInputStream()) {
                    return ImageIO.read(in);
                }
            } catch (Throwable t) {
                if (!(t instanceof java.io.IOException)) t.printStackTrace();
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String direct(String u) {
        if (u.startsWith("https://lunr.pics/")) return u.replace("https://lunr.pics/", "https://lunr.pics/i/") + ".png";
        if (u.startsWith("https://imgur.com/")) u = u.replace("https://imgur.com/", "https://i.imgur.com/") + ".png";
        return u;
    }

    boolean show(ModuleChat chat, String link, IChatComponent hoveredMessage) {
        if (!chat.hoverImagePreview.on()) return false;
        urls.clear();
        if (link != null) urls.add(link);
        else {
            if (hoveredMessage == null) return false;
            Matcher m = URL_PATTERN.matcher(ChatText.plain(hoveredMessage));
            while (m.find()) urls.add(direct(m.group()));
        }
        if (urls.isEmpty()) return false;
        String next = urls.get(cycle % urls.size());
        if (showing && next.equals(url)) return true;
        try {
            Future<BufferedImage> f = cache.get(next);
            if (urls.size() > 1 && showing && image != null && image.isDone() && !f.isDone()) return true;
            image = f;
            showing = true;
            url = next;
            texture = null;
            return true;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    void keyPressed(int key, boolean repeat) {
        if (!repeat && key == Keyboard.KEY_LCONTROL) cycle++;
    }

    void reset() {
        showing = false;
        image = null;
        texture = null;
        fullscreen.stop();
        fullscreen.reversed(false);
    }

    void clearCache() { cache.invalidateAll(); }
}
