package com.example.lunarforge.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.Inflater;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LunarBuild {
    private static final Logger LOG = LogManager.getLogger("LunarForge/Build");
    private static final String LAUNCH_API = "https://api.lunarclientprod.com/launcher/launch";
    private static final String LAUNCHER_VERSION = "3.3.3";
    private static final long STALE_MS = 24L * 60 * 60 * 1000;

    private static volatile String version = "2.22.42-2639";
    private static volatile String branch = "master";
    private static volatile String commit = "09550f3b2136328d460deb5bcf44b67c4db964df";

    private LunarBuild() {}

    public static String version() { return version; }

    public static String semver() { return "v" + version; }
    public static String branch() { return branch; }
    public static String commit() { return commit; }

    public static String launcherVersion() { return LAUNCHER_VERSION; }

    private static File cacheFile() { return new File(Minecraft.getMinecraft().mcDataDir, "lunarforge/lunar_build.properties"); }

    public static void init() {
        Properties cached = new Properties();
        File file = cacheFile();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) { cached.load(in); }
            catch (IOException e) { LOG.warn("Could not read {}: {}", file, e.toString()); }
        }
        apply(cached);
        long fetchedAt = 0;
        try { fetchedAt = Long.parseLong(cached.getProperty("fetchedAt", "0")); } catch (NumberFormatException ignored) {}
        if (System.currentTimeMillis() - fetchedAt < STALE_MS) return;
        Thread t = new Thread(LunarBuild::refresh, "LunarForge Build Fetch");
        t.setDaemon(true);
        t.start();
    }

    private static boolean apply(Properties p) {
        String v = p.getProperty("lunarVersion"), c = p.getProperty("fullGitHash");
        if (v == null || v.isEmpty() || c == null || c.isEmpty()) return false;
        version = v;
        commit = c;
        branch = p.getProperty("gitBranch", "master");
        return true;
    }

    private static void refresh() {
        try {
            String jar = lunarJarUrl();
            if (jar == null) return;
            Properties data = readBuildData(jar);
            if (data == null || !apply(data)) { LOG.warn("lunar.jar has no usable lunarBuildData.txt"); return; }
            Properties out = new Properties();
            out.setProperty("lunarVersion", version);
            out.setProperty("fullGitHash", commit);
            out.setProperty("gitBranch", branch);
            out.setProperty("fetchedAt", Long.toString(System.currentTimeMillis()));
            File file = cacheFile();
            file.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(file)) { out.store(o, "Lunar build presented to Lunar's services"); }
            LOG.info("Presenting as Lunar Client {} ({})", version, commit.substring(0, Math.min(7, commit.length())));
        } catch (Exception e) {
            LOG.warn("Could not fetch the current Lunar build, using {}: {}", version, e.toString());
        }
    }

    private static String lunarJarUrl() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("hwid", "0");
        body.addProperty("hwid_private", "0");
        body.addProperty("os", System.getProperty("os.name", "").toLowerCase().contains("win") ? "win32"
            : System.getProperty("os.name", "").toLowerCase().contains("mac") ? "darwin" : "linux");
        body.addProperty("arch", System.getProperty("os.arch", "").contains("aarch64") ? "arm64" : "x64");
        body.addProperty("launcher_version", LAUNCHER_VERSION);
        body.addProperty("version", "1.8.9");
        body.addProperty("branch", "master");
        body.addProperty("launch_type", "OFFLINE");
        body.addProperty("installation_id", UUID.randomUUID().toString());
        body.addProperty("os_release", System.getProperty("os.version", ""));
        body.addProperty("module", "lunar");
        HttpURLConnection conn = open(LAUNCH_API);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream o = conn.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        if (conn.getResponseCode() != 200) { LOG.warn("Launch API returned {}", conn.getResponseCode()); return null; }
        try (Reader r = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject data = new JsonParser().parse(r).getAsJsonObject().getAsJsonObject("launchTypeData");
            JsonArray artifacts = data == null ? null : data.getAsJsonArray("artifacts");
            if (artifacts == null) return null;
            for (JsonElement e : artifacts) {
                JsonObject a = e.getAsJsonObject();
                if (a.has("name") && "lunar.jar".equals(a.get("name").getAsString())) return a.get("url").getAsString();
            }
        }
        return null;
    }

    private static Properties readBuildData(String url) throws Exception {
        HttpURLConnection head = open(url);
        head.setRequestMethod("HEAD");
        long size = head.getContentLengthLong();
        if (size <= 0) return null;
        byte[] tail = range(url, Math.max(0, size - 65536), size - 1);
        int eocd = -1;
        for (int i = tail.length - 22; i >= 0; i--) {
            if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) { eocd = i; break; }
        }
        if (eocd < 0) return null;
        ByteBuffer end = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
        int cdSize = end.getInt(eocd + 12), cdOffset = end.getInt(eocd + 16);
        ByteBuffer cd = ByteBuffer.wrap(range(url, cdOffset, (long)cdOffset + cdSize - 1)).order(ByteOrder.LITTLE_ENDIAN);
        while (cd.remaining() > 46 && cd.getInt(cd.position()) == 0x02014b50) {
            int p = cd.position();
            int method = cd.getShort(p + 10) & 0xFFFF;
            int compSize = cd.getInt(p + 20);
            int nameLen = cd.getShort(p + 28) & 0xFFFF, extraLen = cd.getShort(p + 30) & 0xFFFF, commentLen = cd.getShort(p + 32) & 0xFFFF;
            int localOffset = cd.getInt(p + 42);
            byte[] name = new byte[nameLen];
            cd.position(p + 46);
            cd.get(name);
            cd.position(p + 46 + nameLen + extraLen + commentLen);
            if (!"lunarBuildData.txt".equals(new String(name, StandardCharsets.UTF_8))) continue;
            byte[] local = range(url, localOffset, (long)localOffset + 30 + 512 + compSize);
            ByteBuffer lh = ByteBuffer.wrap(local).order(ByteOrder.LITTLE_ENDIAN);
            int start = 30 + (lh.getShort(26) & 0xFFFF) + (lh.getShort(28) & 0xFFFF);
            byte[] raw = new byte[compSize];
            System.arraycopy(local, start, raw, 0, compSize);
            if (method == 8) {
                Inflater inf = new Inflater(true);
                inf.setInput(raw);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                while (!inf.finished() && !inf.needsInput()) out.write(buf, 0, inf.inflate(buf));
                inf.end();
                raw = out.toByteArray();
            }
            Properties p2 = new Properties();
            p2.load(new ByteArrayInputStream(raw));
            return p2;
        }
        return null;
    }

    private static byte[] range(String url, long from, long to) throws IOException {
        HttpURLConnection conn = open(url);
        conn.setRequestProperty("Range", "bytes=" + from + "-" + to);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "LunarClient/" + LAUNCHER_VERSION);
        return conn;
    }
}
