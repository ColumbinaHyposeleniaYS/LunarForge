package com.example.lunarforge.cosmetics;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LunarCdn {
    private static final Logger LOG = LogManager.getLogger("LunarForge/CDN");
    private static final String URL_BASE = "https://textures.lunarclientcdn.com/file/";
    private static final Map<String, String> INDEX = new HashMap<String, String>();
    private static final Map<String, Future<byte[]>> PENDING = new ConcurrentHashMap<String, Future<byte[]>>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<String, Long>();
    private static final long RETRY_MS = 60000;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private int n;
        @Override public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "LunarForge CDN #" + ++n); t.setDaemon(true); return t;
        }
    });
    private static File cacheDir;

    private LunarCdn() {}

    private static synchronized void load() {
        if (!INDEX.isEmpty()) return;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                LunarCdn.class.getResourceAsStream("/assets/lunarforge/cosmetics/jit_index"), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                int space = line.indexOf(' ');
                if (space > 0) INDEX.put(line.substring(0, space), line.substring(space + 1).trim());
            }
        } catch (Exception e) { LOG.error("Could not read the bundled jit_index", e); }
        cacheDir = CosmeticFolder.cache();
    }

    public static boolean has(String path) { load(); return INDEX.containsKey(path); }

    public static Future<byte[]> fetch(final String path) {
        load();
        final String sha1 = INDEX.get(path);
        if (sha1 == null) return CompletableFuture.completedFuture(null);
        Future<byte[]> running = PENDING.get(sha1);
        Long failed = FAILED.get(sha1);
        if (running != null && (failed == null || System.currentTimeMillis() - failed < RETRY_MS)) return running;
        FAILED.remove(sha1);
        Future<byte[]> task = POOL.submit(new Callable<byte[]>() {
            @Override public byte[] call() {
                try { return read(sha1); }
                catch (Exception e) {
                    LOG.warn("Failed to fetch {} ({}): {}", path, sha1, e.toString());
                    FAILED.put(sha1, System.currentTimeMillis());
                    return null;
                }
            }
        });
        PENDING.put(sha1, task);
        return task;
    }

    private static byte[] read(String sha1) throws Exception {
        File file = new File(cacheDir, sha1.substring(0, 2) + "/" + sha1);
        if (file.isFile()) {
            byte[] data = readAll(new FileInputStream(file));
            if (sha1.equalsIgnoreCase(hash(data))) return data;
        }
        HttpURLConnection c = (HttpURLConnection)new URL(URL_BASE + sha1).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "LunarForge");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        byte[] data = readAll(c.getInputStream());
        if (!sha1.equalsIgnoreCase(hash(data))) throw new IOException("hash mismatch");
        file.getParentFile().mkdirs();
        File tmp = new File(file.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(data); }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file); }
        return data;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String hash(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b & 255));
        return sb.toString();
    }
}
