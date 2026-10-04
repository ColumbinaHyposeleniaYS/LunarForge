package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ModuleServerAddress extends Module {
    private static final ResourceLocation UNKNOWN_SERVER = new ResourceLocation("minecraft", "textures/misc/unknown_server.png");
    private final BoolSetting serverIcon = bool("serverIcon", true);

    private ResourceLocation iconTexture;

    private BufferedImage iconImage;

    public ModuleServerAddress() {
        super("SERVER_ADDRESS", false);
        hud(new Hud());
    }

    @Override protected void onEnable() { loadIcon(); }

    @SubscribeEvent
    public void onServerJoin(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (!isEnabled()) return;
        loadIcon();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!isEnabled()) return;
        if (iconImage == null) return;
        if (iconTexture != null) mc().getTextureManager().deleteTexture(iconTexture);
        iconTexture = mc().getTextureManager().getDynamicTextureLocation("server-icon-thumbnail", new DynamicTexture(iconImage));
        iconImage = null;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (!isEnabled()) return;
        if (iconTexture != null) {
            mc().getTextureManager().deleteTexture(iconTexture);
            iconTexture = null;
        }
    }

    private void loadIcon() {
        ServerData data = mc().getCurrentServerData();
        if (data == null || data.getBase64EncodedIconData() == null || data.getBase64EncodedIconData().isEmpty()) {
            iconTexture = null;
        } else {
            decodeIcon(data.getBase64EncodedIconData()).ifPresent(image -> iconImage = image);
        }
    }

    private static Optional<BufferedImage> decodeIcon(String base64) {
        ByteBuf encoded = Unpooled.copiedBuffer(base64, Charsets.UTF_8);
        ByteBuf decoded = Base64.decode(encoded);
        try {
            return Optional.ofNullable(TextureUtil.readBufferedImage(new ByteBufInputStream(decoded)));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            encoded.release();
            decoded.release();
        }
        return Optional.empty();
    }

    @Override protected void layout(Page page) {
        page.section("settings", s -> s.add(serverIcon));
    }

    private final class Hud extends TextHud {
        private String line;

        Hud() { super(ModuleServerAddress.this, 0, 0, HudAnchor.TOP_LEFT, sizes(10, 18, 22, 40, 56, 62)); }

        @Override protected Boolean staticWidthFor(String text) { return Boolean.FALSE; }

        private String shown() { return hasBrackets() ? "[" + line + "]" : line; }

        @Override protected String text(boolean preview) {
            try {
                ServerData data = mc().getCurrentServerData();

                return data == null ? (preview ? "na.lunar.gg" : "") : data.serverIP;
            } catch (NullPointerException e) {
                return preview ? "na.lunar.gg" : "";
            }
        }

        @Override public boolean visible(boolean preview) {
            line = text(preview);
            if (line == null || line.isEmpty()) return false;
            size(Draw.width(shown()) + 8.0f + (serverIcon.on() ? backgroundHeight.intValue() : 0), backgroundHeight.intValue());
            return true;
        }

        @Override public void render(boolean preview) {
            if (line == null || line.isEmpty()) return;
            boolean bg = hasBackground(), withBrackets = hasBrackets();
            float textWidth = Draw.width(shown());
            float w = width(), h = height();
            float x = 0.0f, y = 0.0f;
            boolean icon = serverIcon.on();
            if (icon) {
                x += h;
                w -= h;
            }
            if (bg) {
                float offset = icon ? h : 0.0f;
                Draw.fill(backgroundColor, x - offset, y, w + offset, h);
                if (border.on()) Draw.border(borderColor, x - offset, y, w + offset, h, borderThickness.value());
            }
            if (icon) {
                Draw.texture(iconTexture == null ? UNKNOWN_SERVER : iconTexture, (int)(x - h), (int)y, (int)h, (int)h, 0xFFFFFFFF);
            }
            float textY = y + (h / 1.88f - Draw.fontHeight() / 2.0f + 0.5f);
            drawText(line, align(x, w, textWidth, bg), textY, withBrackets, textShadow.on());
        }
    }
}
