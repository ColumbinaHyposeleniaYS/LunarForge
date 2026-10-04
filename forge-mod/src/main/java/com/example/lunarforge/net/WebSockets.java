package com.example.lunarforge.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.URI;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

final class WebSockets {
    private static final int MAX_FRAME = 16 * 1024 * 1024;

    interface Listener {
        void onOpen(Channel channel) throws Exception;
        void onMessage(Channel channel, byte[] data) throws Exception;
    }

    private WebSockets() {}

    static void send(Channel channel, byte[] data) {
        channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
    }

    static Channel connect(EventLoopGroup group, final String host, String path, Map<String, String> headers, final Listener listener)
            throws Exception {
        URI uri = new URI("wss://" + host + path);
        DefaultHttpHeaders http = new DefaultHttpHeaders();
        for (Map.Entry<String, String> h : headers.entrySet()) http.add(h.getKey(), h.getValue());
        final WebSocketClientHandshaker handshaker =
            WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, http, MAX_FRAME);
        return new Bootstrap().group(group).channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(final SocketChannel ch) throws Exception {
                    SSLEngine engine = SSLContext.getDefault().createSSLEngine(host, 443);
                    engine.setUseClientMode(true);
                    SSLParameters params = engine.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    engine.setSSLParameters(params);
                    SslHandler ssl = new SslHandler(engine);

                    ssl.handshakeFuture().addListener(new GenericFutureListener<Future<Channel>>() {
                        @Override public void operationComplete(Future<Channel> f) {
                            if (f.isSuccess()) handshaker.handshake(ch);
                            else ch.close();
                        }
                    });
                    ch.pipeline().addLast(ssl, new HttpClientCodec(), new HttpObjectAggregator(65536),
                        new WebSocketFrameAggregator(MAX_FRAME), new Handler(handshaker, listener));
                }
            }).connect(host, 443).sync().channel();
    }

    private static final class Handler extends SimpleChannelInboundHandler<Object> {
        private final WebSocketClientHandshaker handshaker;
        private final Listener listener;

        Handler(WebSocketClientHandshaker handshaker, Listener listener) {
            this.handshaker = handshaker;
            this.listener = listener;
        }

        @Override protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!handshaker.isHandshakeComplete()) {
                handshaker.finishHandshake(ctx.channel(), (FullHttpResponse)msg);
                listener.onOpen(ctx.channel());
            } else if (msg instanceof BinaryWebSocketFrame) {
                BinaryWebSocketFrame frame = (BinaryWebSocketFrame)msg;
                byte[] data = new byte[frame.content().readableBytes()];
                frame.content().readBytes(data);
                listener.onMessage(ctx.channel(), data);
            } else if (msg instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(((PingWebSocketFrame)msg).content().retain()));
            } else if (msg instanceof CloseWebSocketFrame) {
                ctx.close();
            }
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LunarNetwork.LOG.warn("Websocket error: {}", cause.toString());
            ctx.close();
        }
    }
}
