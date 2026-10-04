package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.HudElement;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

public final class ModuleDirectionHud extends Module {
    public enum Style implements ChoiceSetting.Option {
        NORMAL("normal"), LEGACY("legacy"), SIMPLE("simple"), COMPASS("realCompass");
        private final String id;
        Style(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum Placement implements ChoiceSetting.Option {
        ABOVE, BELOW, NONE;
        @Override public String langId() { return name(); }
    }

    private static final ResourceLocation LEGACY_TEXTURE = new ResourceLocation("lunarforge", "ui/icons/compass.png");
    private static final ResourceLocation MODERN_TEXTURE = new ResourceLocation("lunarforge", "ui/icons/compass-modern.png");
    private static final ResourceLocation ROUND = new ResourceLocation("lunarforge", "ui/icons/round-compass.png");
    private static final ResourceLocation POINTER = new ResourceLocation("lunarforge", "ui/icons/round-compass-pointer.png");
    private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final NumberSetting borderThickness = decimal("borderThickness", 0.5f, 0.5f, 3.0f);
    private final BoolSetting background = bool("background", false);
    private final BoolSetting border = bool("border", false);
    private final BoolSetting showWithTab = bool("showWithTab", false);
    private final ChoiceSetting<Style> hudStyle = choice("hudStyle", Style.NORMAL);
    private final NumberSetting width = decimal("width", 280.0f, 168.0f, 448.0f);
    private final BoolSetting textShadow = bool("textShadow", false);
    private final BoolSetting boldDirections = bool("boldDirections", false);
    private final BoolSetting showMarker = bool("showMarker", true);
    private final BoolSetting showMarkerValue = bool("showMarkerValue", true);
    private final ColorSetting borderColor = color("borderColor", -1627389952);
    private final ColorSetting backgroundColor = color("backgroundColor", 0x6F000000);
    private final ColorSetting directionColor = color("directionColor", -1);
    private final ColorSetting markerColor = color("markerColor", -1);
    private final ChoiceSetting<Placement> teammates = choice("teammates", Placement.ABOVE);
    private final ChoiceSetting<Placement> waypoints = choice("waypoints", Placement.ABOVE);
    private final ChoiceSetting<Placement> externalMarkers = choice("externalMarkers", Placement.BELOW);
    private final BoolSetting showTeammates = bool("showTeammates", true);
    private final BoolSetting showWaypoints = bool("showWaypoints", true);
    private final BoolSetting showExternalMarkers = bool("showExternalMarkers", true);

    public ModuleDirectionHud() {
        super("DIRECTION_HUD", true);
        hud(new Hud());
    }

    private boolean is(Style style) { return hudStyle.is(style); }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> {
            s.add(width).hideIf(() -> !is(Style.NORMAL));
            s.add(textShadow).hideIf(() -> is(Style.LEGACY) || is(Style.COMPASS));
            s.add(boldDirections).hideIf(() -> !is(Style.NORMAL));
            s.group(background, g -> g.add(border, borderThickness)).hideIf(() -> is(Style.LEGACY));
        });
        page.section("renderOptions", s -> {
            s.add(hudStyle, showWithTab);
            s.add(showMarker).hideIf(() -> is(Style.SIMPLE) || is(Style.COMPASS));
            s.add(showMarkerValue, waypoints, teammates, externalMarkers).hideIf(() -> !is(Style.NORMAL));
            s.add(showWaypoints, showTeammates, showExternalMarkers).hideIf(() -> !is(Style.COMPASS));
        });
        page.section("colorOptions", s -> {
            s.add(backgroundColor, borderColor).hideIf(() -> is(Style.LEGACY));
            s.add(markerColor).hideIf(() -> !is(Style.LEGACY) && !is(Style.NORMAL));
            s.add(directionColor).hideIf(() -> !is(Style.LEGACY) && !is(Style.SIMPLE));
        });
    }

    private static boolean tabVisible() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || !mc.gameSettings.keyBindPlayerList.isKeyDown()) return false;
        boolean list = mc.thePlayer.sendQueue.getPlayerInfoMap().size() > 1
            || mc.theWorld != null && mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(0) != null;
        return !mc.isIntegratedServerRunning() || list;
    }

    private final class Hud extends HudElement {
        Hud() { super(ModuleDirectionHud.this, 0, 0, HudAnchor.TOP_CENTER); }

        @Override public boolean visible(boolean preview) {
            switch (hudStyle.get()) {
                case LEGACY: size(65, 12); break;
                case SIMPLE: size(24, 24); break;
                case COMPASS: size(128, 128); break;
                default: size(width.value(), 29); break;
            }
            if (preview || showWithTab.on()) return true;
            return !tabVisible();
        }

        @Override public void render(boolean preview) {
            double yaw = 180.0;
            if (!preview) {
                Entity view = mc().getRenderViewEntity();
                if (view != null) yaw = view.rotationYaw;
                else if (mc().thePlayer != null) yaw = mc().thePlayer.rotationYaw;
            }
            Style style = hudStyle.get();
            if (style != Style.LEGACY && background.on()) {
                Draw.fill(backgroundColor, 0, 0, width(), height());
                if (border.on()) Draw.border(borderColor, 0, 0, width(), height(), borderThickness.value());
            }
            GlStateManager.enableBlend();
            switch (style) {
                case LEGACY: legacy(yaw); break;
                case SIMPLE: simple(yaw); break;
                case COMPASS: compass(yaw); break;
                default: normal(yaw); break;
            }
            GlStateManager.color(1, 1, 1, 1);
        }

        private void legacy(double yaw) {
            float u = MathHelper.floor_float((float)(yaw * 256.0 / 360.0 + 0.5)) & 0xFF;
            int tint = directionColor.color(0) & 0xFFFFFF | 0xFF000000;
            if (u < 128) {
                Draw.blit(LEGACY_TEXTURE, 0, 0, (int)u, 0, 65, 12, 256, 256, -1);
                Draw.blit(LEGACY_TEXTURE, 0, 0, (int)u, 24, 65, 12, 256, 256, tint);
            } else {
                Draw.blit(LEGACY_TEXTURE, 0, 0, (int)(u - 128), 12, 65, 12, 256, 256, -1);
                Draw.blit(LEGACY_TEXTURE, 0, 0, (int)(u - 128), 36, 65, 12, 256, 256, tint);
            }
            if (showMarker.on()) {
                Draw.text(markerColor, "|", 32, 1, false);
                Draw.text(markerColor, "|", 32, 5, false);
            }
        }

        private void simple(double yaw) {
            String text = DIRECTIONS[(int)Math.floor(yaw * 4.0 / 180.0 + 0.5) & 7];
            Draw.text(directionColor, text, width() / 2 - Draw.width(text) / 2,
                height() / 2 - Draw.fontHeight() / 2.0f + 1, textShadow.on());
        }

        private void compass(double yaw) {
            float w = width(), h = height();
            Draw.blit(ROUND, 0, 0, 0, 0, w, h, w, h, -1);
            GlStateManager.pushMatrix();
            GlStateManager.translate(w / 2, h / 2, 0);
            GlStateManager.rotate((float)yaw, 0, 0, 1);
            GlStateManager.translate(-w / 2, -h / 2, 0);
            Draw.blit(POINTER, 0, 0, 0, 0, w, h, w, h, -1);
            GlStateManager.popMatrix();
        }

        private void normal(double yaw) {
            float heading = (float)yaw - MathHelper.floor_float((float)yaw / 360.0f) * 360.0f;
            if (heading == 360.0f) heading = 0.0f;
            float w = width.value();
            if (showMarkerValue.on()) {
                Draw.centered(markerColor, String.valueOf((int)heading), w / 2, -Draw.fontHeight() - 7, textShadow.on());
            }
            int v = boldDirections.on() ? 23 : 69;
            if (textShadow.on()) v -= 23;
            if (showMarker.on()) {
                float cx = w / 2, size = 6.0f;
                Draw.triangle(cx, -6 + size / 1.5f, cx + size / 2, -6, cx - size / 2, -6, markerColor.color(0));
            }
            float edge = w * 0.25f, inner = w - edge;
            float u = (heading - 0) / 360.0f * (966.0f - 218.0f) + 218.0f - (inner - edge) / 2.0f - edge + 1.0f;
            gradient(0, 3, u, v, edge, 23, 0x00FFFFFF, -1, -1, 0x00FFFFFF);
            gradient(edge + inner - edge, 3, u + inner, v, edge, 23, -1, 0x00FFFFFF, 0x00FFFFFF, -1);
            gradient(edge, 3, u + edge, v, inner - edge, 23, -1, -1, -1, -1);
        }

        private void gradient(float x, float y, float u, float v, float w, float h, int bl, int br, int tr, int tl) {
            mc().getTextureManager().bindTexture(MODERN_TEXTURE);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.shadeModel(7425);
            float fu = 1.0f / 1186.0f, fv = 1.0f / 122.0f;
            WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
            wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            vertex(wr, x, y + h, u * fu, (v + h) * fv, bl);
            vertex(wr, x + w, y + h, (u + w) * fu, (v + h) * fv, br);
            vertex(wr, x + w, y, (u + w) * fu, v * fv, tr);
            vertex(wr, x, y, u * fu, v * fv, tl);
            Tessellator.getInstance().draw();
            GlStateManager.shadeModel(7424);
        }

        private void vertex(WorldRenderer wr, float x, float y, float u, float v, int c) {
            wr.pos(x, y, 0).tex(u, v).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).endVertex();
        }
    }
}
