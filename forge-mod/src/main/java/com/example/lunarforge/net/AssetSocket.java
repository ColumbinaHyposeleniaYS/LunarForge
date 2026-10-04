package com.example.lunarforge.net;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

final class AssetSocket {
    private static final String HOST = "websocket.lunarclientprod.com";
    private static final String COSMETICS = "lunarclient.websocket.cosmetic.v2.CosmeticService";
    private static final String HEARTBEAT = "lunarclient.websocket.heartbeat.v1.HeartbeatService";

    private final AtomicInteger nextRequest = new AtomicInteger(1);
    private final Map<String, String> pending = new ConcurrentHashMap<String, String>();
    private volatile Channel channel;
    private volatile boolean ready;

    boolean isReady() { Channel c = channel; return ready && c != null && c.isActive(); }

    void run(Session session, String jwt) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("User-Agent", "Lunar Client " + LunarBuild.semver());
            final byte[] handshake = handshake(session, jwt, Minecraft.getMinecraft().gameSettings.language);
            channel = WebSockets.connect(group, HOST, "/game", headers, new WebSockets.Listener() {
                @Override public void onOpen(Channel ch) {
                    WebSockets.send(ch, handshake);
                    ready = true;
                    LunarNetwork.LOG.info("Connected to Lunar as {}", session.getUsername());

                    send("Login", COSMETICS, new byte[0]);
                    TabLogos.reset();
                }

                @Override public void onMessage(Channel ch, byte[] data) throws Exception { receive(data); }
            });

            channel.eventLoop().scheduleAtFixedRate(() -> {
                if (isReady()) send("GameHeartbeat", HEARTBEAT, new byte[0]);
            }, 60, 60, TimeUnit.SECONDS);
            channel.closeFuture().sync();
        } finally {
            ready = false;
            channel = null;
            pending.clear();
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }

    void close() {
        Channel c = channel;
        if (c != null) c.close();
    }

    void loadTabLogos(Collection<UUID> players) {
        Proto.Writer request = new Proto.Writer();
        for (UUID id : players) request.message(1, Proto.uuid(id));
        send("LoadTabLogos", COSMETICS, request.toByteArray());
    }

    private void send(String method, String service, byte[] input) {
        Channel c = channel;
        if (c == null) return;
        String id = Integer.toString(nextRequest.getAndIncrement());
        pending.put(id, method);
        WebSockets.send(c, new Proto.Writer().bytes(1, id.getBytes(StandardCharsets.UTF_8)).string(2, service)
            .string(3, method).bytes(4, input).toByteArray());
    }

    private void receive(byte[] data) throws Exception {
        Proto.Reader in = new Proto.Reader(data);
        while (in.next()) {
            if (in.field() != 1) { in.skip(); continue; }
            Proto.Reader rpc = new Proto.Reader(in.bytes());
            String id = null;
            byte[] output = new byte[0];
            while (rpc.next()) {
                if (rpc.field() == 1) id = new String(rpc.bytes(), StandardCharsets.UTF_8);
                else if (rpc.field() == 2) output = rpc.bytes();
                else rpc.skip();
            }
            String method = id == null ? null : pending.remove(id);
            if ("Login".equals(method)) TabLogos.ownLogin(output);
            else if ("LoadTabLogos".equals(method)) TabLogos.loaded(output);
        }
    }

    static byte[] handshake(Session session, String jwt, String language) {
        Proto.Writer identity = new Proto.Writer()
            .message(1, new Proto.Writer().message(1, Proto.uuid(session.getProfile().getId())).string(2, session.getUsername()))
            .int32(2, 2)
            .string(3, jwt);

        Proto.Writer game = new Proto.Writer()
            .message(1, new Proto.Writer().string(1, "v1_8"))
            .message(2, lunarClientVersion());
        String arch = System.getenv("PROCESSOR_ARCHITECTURE");

        return new Proto.Writer()
            .message(1, identity)
            .message(2, new Proto.Writer().string(1, LunarBuild.launcherVersion()))
            .string(5, System.getProperty("os.name"))
            .string(6, arch != null ? arch : System.getProperty("os.arch"))
            .message(7, new Proto.Writer().string(2, language))
            .message(8, game)
            .string(11, System.getProperty("os.version"))
            .toByteArray();
    }

    static Proto.Writer lunarClientVersion() {
        return new Proto.Writer().string(1, LunarBuild.branch()).string(2, LunarBuild.commit()).string(3, LunarBuild.semver());
    }
}
