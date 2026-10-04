package com.example.lunarforge.net;

import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import net.minecraft.client.Minecraft;
import net.minecraft.util.CryptManager;
import net.minecraft.util.Session;

final class LunarAuth {
    private static final String HOST = "authenticator.lunarclientprod.com";

    private static final String INITIATOR = "GAME_WEBSOCKET";

    private LunarAuth() {}

    static String fetchJwt(final Session session) throws Exception {
        final CompletableFuture<String> jwt = new CompletableFuture<String>();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            headers.put("Accept", "application/x-protobuf");
            headers.put("User-Agent", "Lunar Client " + LunarBuild.semver());
            headers.put("X-Initiator", INITIATOR);
            Channel ch = WebSockets.connect(group, HOST, "/game", headers, new WebSockets.Listener() {
                @Override public void onOpen(Channel channel) { WebSockets.send(channel, hello(session)); }

                @Override public void onMessage(Channel channel, byte[] data) throws Exception {
                    Proto.Reader in = new Proto.Reader(data);
                    while (in.next()) {
                        if (in.field() == 1) encryptionRequest(channel, session, in.bytes());
                        else if (in.field() == 2) jwt.complete(readJwt(in.bytes()));
                        else in.skip();
                    }
                }
            });
            ch.closeFuture().addListener(f -> jwt.completeExceptionally(new IllegalStateException("Authenticator closed the connection")));
            try {
                return jwt.get(20, TimeUnit.SECONDS);
            } finally {
                ch.close();
            }
        } finally {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
    }

    static byte[] hello(Session session) {
        Proto.Writer identity = new Proto.Writer().message(1, Proto.uuid(session.getProfile().getId())).string(2, session.getUsername());
        return new Proto.Writer().message(1, new Proto.Writer().message(1, identity).string(2, INITIATOR)).toByteArray();
    }

    private static void encryptionRequest(Channel channel, Session session, byte[] data) throws Exception {
        byte[] keyBytes = null, random = null;
        Proto.Reader in = new Proto.Reader(data);
        while (in.next()) {
            if (in.field() == 1) keyBytes = in.bytes();
            else if (in.field() == 2) random = in.bytes();
            else in.skip();
        }
        if (keyBytes == null || random == null) {
            WebSockets.send(channel, new Proto.Writer().message(3, new Proto.Writer().string(1, "Malformed encryption request")).toByteArray());
            return;
        }
        PublicKey key = CryptManager.decodePublicKey(keyBytes);
        SecretKey secret = CryptManager.createNewSharedKey();
        String serverId = new BigInteger(CryptManager.getServerIdHash("", key, secret)).toString(16);
        Minecraft.getMinecraft().getSessionService().joinServer(session.getProfile(), session.getToken(), serverId);
        Proto.Writer response = new Proto.Writer()
            .bytes(1, CryptManager.encryptData(key, secret.getEncoded()))
            .bytes(2, CryptManager.encryptData(key, random));
        WebSockets.send(channel, new Proto.Writer().message(2, response).toByteArray());
    }

    private static String readJwt(byte[] data) throws Exception {
        Proto.Reader in = new Proto.Reader(data);
        while (in.next()) {
            if (in.field() == 1) return in.string();
            in.skip();
        }
        throw new IllegalStateException("AuthSuccessMessage without a JWT");
    }
}
