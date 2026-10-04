package com.example.lunarforge.splash;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.ProgressManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

public final class LunarSplash {
    private static final Logger LOG = LogManager.getLogger("LunarSplash");
    private static final String ASSETS = "/assets/lunarforge/";
    private static final long FRAME_NANOS = TimeUnit.SECONDS.toNanos(1) / 60;

    private static final float BAR_EASE_SECONDS = .15f;

    private static final float MAX_FRAME_SECONDS = .05f;

    private static final float LOGO_WIDTH = 128, LOGO_HEIGHT = 117;
    private static final float TEXT_SIZE = 18;
    private static final float BAR_WIDTH = 300, BAR_HEIGHT = 12;
    private static final int LOGO_SHADOW = 0x33000000;
    private static final int BAR_TRACK = 0x60A0A0A0;
    private static final int BAR_FILL = 0xAFFFFFFF;

    private static boolean failed;
    private static boolean finished;
    private static long lastFrame;

    private static int background = -1, logo = -1, text = -1;
    private static String textValue;
    private static int textWidth, textHeight;
    private static Font font;
    private static boolean fontFailed;

    private static Object topBar;
    private static int completedStages;
    private static int expectedStages = -1;
    private static float progress;

    private static float shownProgress;
    private static long lastBarFrame;
    private static String status = "Starting Minecraft";

    private LunarSplash() {
    }

    public static boolean takeOverFromFml() {
        return true;
    }

    public static boolean onVanillaScreen() {
        if (failed || finished) return false;
        draw(true);
        return !failed;
    }

    public static void onProgress() {
        if (failed || finished) return;
        try {
            if (updateProgress()) draw(true);
            else draw(false);
        } catch (Throwable t) {
            fail(t);
        }
    }

    public static void finish() {
        if (finished) return;
        finished = true;
        if (topBar != null) completedStages++;
        if (completedStages > 0 && completedStages != expectedStages) saveExpectedStages(completedStages);
        int[] textures = {background, logo, text};
        for (int texture : textures) if (texture != -1) GL11.glDeleteTextures(texture);
        background = logo = text = -1;
        syncWindowSize();
    }

    private static void syncWindowSize() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.isFullScreen() || !Display.isCreated()) return;
            int width = Display.getWidth(), height = Display.getHeight();
            if (width > 0 && height > 0 && (width != mc.displayWidth || height != mc.displayHeight)) mc.resize(width, height);
        } catch (Throwable t) {
            LOG.error("Could not apply a window resize made while loading", t);
        }
    }

    private static boolean updateProgress() {
        List<ProgressManager.ProgressBar> bars = new ArrayList<ProgressManager.ProgressBar>();
        for (Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator(); it.hasNext(); ) bars.add(it.next());

        if (bars.isEmpty()) {
            if (topBar != null) {
                completedStages++;
                topBar = null;
            }
        } else if (bars.get(0) != topBar) {
            if (topBar != null) completedStages++;
            topBar = bars.get(0);
        }

        float stage = 0;
        for (int i = bars.size() - 1; i >= 0; i--) {
            ProgressManager.ProgressBar bar = bars.get(i);
            int steps = Math.max(1, bar.getSteps());
            float done = i == bars.size() - 1 ? bar.getStep() : Math.max(0, bar.getStep() - 1) + stage;
            stage = Math.min(1, done / steps);
        }
        int running = bars.isEmpty() ? 0 : 1;
        int total = Math.max(expectedStages(), completedStages + running);
        progress = Math.max(progress, Math.min(1, (completedStages + stage) / total));

        if (bars.isEmpty()) return false;
        ProgressManager.ProgressBar top = bars.get(0);
        String next = top.getMessage() == null || top.getMessage().isEmpty() ? top.getTitle() : top.getMessage();
        if (next == null || next.equals(status)) return false;
        status = next;
        return true;
    }

    private static void draw(boolean force) {
        long now = System.nanoTime();
        if (!force && lastFrame != 0 && now - lastFrame < FRAME_NANOS) return;
        try {
            if (!Display.isCreated()) return;
            ContextCapabilities caps;
            try {
                caps = GLContext.getCapabilities();
            } catch (RuntimeException noContextOnThisThread) {
                return;
            }
            lastFrame = now;
            easeBar(now);
            render(caps);
            Display.update();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void easeBar(long now) {
        float dt = lastBarFrame == 0 ? 0 : Math.min(MAX_FRAME_SECONDS, (now - lastBarFrame) / 1e9f);
        lastBarFrame = now;
        shownProgress += (progress - shownProgress) * (1 - (float)Math.exp(-dt / BAR_EASE_SECONDS));
        if (progress - shownProgress < .001f) shownProgress = progress;
    }

    private static void render(ContextCapabilities caps) {
        int width = Display.getWidth(), height = Display.getHeight();
        if (width <= 0 || height <= 0) return;

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        int framebuffer = bindFramebuffer(caps, 0);
        try {
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glClearColor(0, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            if (background == -1) background = loadTexture("splash/splash.png");
            if (logo == -1) logo = loadTexture("splash/logo-128x117.png");

            texturedRect(background, 0, 0, width, height, -1);
            float logoX = (int) (width / 2f - LOGO_WIDTH / 2f);
            float logoY = Math.round(height / 2f - LOGO_HEIGHT / 2f - 16);
            texturedRect(logo, logoX + 1, logoY + 1, LOGO_WIDTH, LOGO_HEIGHT, LOGO_SHADOW);
            texturedRect(logo, logoX, logoY, LOGO_WIDTH, LOGO_HEIGHT, -1);

            float base = height / 2f + 48;
            float barX = width / 2f - BAR_WIDTH / 2f;
            float barY = base + 48;
            if (prepareText(status)) {
                texturedRect(text, Math.round(width / 2f - textWidth / 2f), Math.round(base + 20), textWidth, textHeight, -1);
            }
            roundedRect(barX, barY, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT, BAR_TRACK);
            if (shownProgress > 0) roundedRect(barX, barY, Math.max(5, BAR_WIDTH * shownProgress), BAR_HEIGHT, BAR_HEIGHT, BAR_FILL);
        } finally {
            bindFramebuffer(caps, framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static int bindFramebuffer(ContextCapabilities caps, int target) {
        if (target < 0) return -1;
        if (caps.OpenGL30) {
            int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            return previous;
        }
        if (caps.GL_ARB_framebuffer_object) {
            int previous = GL11.glGetInteger(ARBFramebufferObject.GL_FRAMEBUFFER_BINDING);
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, target);
            return previous;
        }
        if (caps.GL_EXT_framebuffer_object) {
            int previous = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, target);
            return previous;
        }
        return -1;
    }

    private static void texturedRect(int texture, float x, float y, float w, float h, int color) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    private static void roundedRect(float x, float y, float w, float h, float radius, int color) {
        float r = Math.min(radius, Math.min(w, h) / 2f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2f, y + h / 2f);
        outline(x, y, w, h, r);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        outline(x, y, w, h, r);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private static void outline(float x, float y, float w, float h, float r) {
        final int segments = 12;
        float[][] corners = {
            {x + w - r, y + r, -90},
            {x + w - r, y + h - r, 0},
            {x + r, y + h - r, 90},
            {x + r, y + r, 180},
        };
        for (float[] corner : corners) {
            for (int i = 0; i <= segments; i++) {
                double angle = Math.toRadians(corner[2] + 90.0 * i / segments);
                GL11.glVertex2d(corner[0] + Math.cos(angle) * r, corner[1] + Math.sin(angle) * r);
            }
        }
        GL11.glVertex2f(x + w - r, y);
    }

    private static void color(int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    private static boolean prepareText(String value) {
        if (value == null || value.isEmpty() || fontFailed) return false;
        if (value.equals(textValue) && text != -1) return true;
        try {
            if (font == null) {
                InputStream in = LunarSplash.class.getResourceAsStream(ASSETS + "ui/fonts/roboto-medium.ttf");
                try {
                    font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(TEXT_SIZE);
                } finally {
                    in.close();
                }
            }
            Graphics2D measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            measure.setFont(font);
            FontMetrics metrics = measure.getFontMetrics();
            int w = Math.max(1, metrics.stringWidth(value) + 2);
            int h = Math.max(1, metrics.getAscent() + metrics.getDescent());
            measure.dispose();

            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setFont(font);
            g.setColor(Color.WHITE);
            g.drawString(value, 1, metrics.getAscent());
            g.dispose();

            if (text == -1) text = GL11.glGenTextures();
            upload(text, image);
            textValue = value;
            textWidth = w;
            textHeight = h;
            return true;
        } catch (Throwable t) {
            LOG.warn("Loading screen text disabled", t);
            fontFailed = true;
            return false;
        }
    }

    private static int loadTexture(String path) {
        try {
            InputStream in = LunarSplash.class.getResourceAsStream(ASSETS + path);
            if (in == null) throw new IllegalStateException("Missing " + ASSETS + path);
            BufferedImage image;
            try {
                image = ImageIO.read(in);
            } finally {
                in.close();
            }
            int id = GL11.glGenTextures();
            upload(id, image);
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load loading screen texture " + path, e);
        }
    }

    private static void upload(int id, BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int pixel : argb) {
            pixels.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >>> 24));
        }
        pixels.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }

    private static void fail(Throwable t) {
        failed = true;
        LOG.error("Lunar loading screen failed; the rest of loading continues without it", t);
    }

    private static File stagesFile() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "lunarforge-splash.properties");
    }

    private static int expectedStages() {
        if (expectedStages != -1) return expectedStages;
        expectedStages = 1;
        File file = stagesFile();
        if (file.isFile()) {
            Properties props = new Properties();
            try {
                InputStream in = new FileInputStream(file);
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
                expectedStages = Math.max(1, Integer.parseInt(props.getProperty("stages", "1").trim()));
            } catch (Exception e) {
                LOG.debug("Ignoring unreadable {}", file, e);
            }
        }
        return expectedStages;
    }

    private static void saveExpectedStages(int stages) {
        File file = stagesFile();
        Properties props = new Properties();
        props.setProperty("stages", Integer.toString(stages));
        try {
            file.getParentFile().mkdirs();
            OutputStream out = new FileOutputStream(file);
            try {
                props.store(out, "Top-level loading stages seen last launch; sizes the Lunar loading bar");
            } finally {
                out.close();
            }
        } catch (Exception e) {
            LOG.debug("Could not save {}", file, e);
        }
    }
}
