package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.util.EspRenderUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Ported from Leader-Lite (leader.module.modules.render.ESP), players only:
 * 2D (projected bounding box), 3D (interpolated box outline), FAKECORNER and
 * FAKE2D (camera-facing world-space shapes) plus 2D and RAVEN-style 3D health
 * bars. Colors: scoreboard team color, same-team blue/red, or a custom color
 * (Leader-Lite's HUD rainbow mode is covered by the color's chroma toggle).
 *
 * Differences from Leader-Lite: the OUTLINE shader mode and the friend/enemy
 * manager filters are not ported - OUTLINE needs coremod shader hooks inside
 * RendererLivingEntity (bind a flat-color program around the model draw), and
 * LunarForge has no friend/target manager. 2D drawing happens in
 * RenderWorldLastEvent: the camera matrices are captured, an ortho overlay is
 * set up, and the matrices are restored, replacing Leader-Lite's private
 * setupCameraTransform accessor.
 */
public final class ModuleESP extends Module {

    public enum Mode implements ChoiceSetting.Option {
        NONE("none"), D2("2d"), D3("3d"), FAKECORNER("fakecorner"), FAKE2D("fake2d");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum ColorMode implements ChoiceSetting.Option {
        TEAM("team"), TEAMS("teams"), CUSTOM("custom");
        private final String id;
        ColorMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum HealthBar implements ChoiceSetting.Option {
        NONE("none"), D2("2d"), RAVEN("raven");
        private final String id;
        HealthBar(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private final ChoiceSetting<Mode> mode = choice("espMode", Mode.D3).label(() -> "Mode");
    private final ChoiceSetting<ColorMode> colorMode = choice("colorMode", ColorMode.TEAM).label(() -> "Color");
    private final ColorSetting customColor = color("customColor", 0xFFFF5555)
            .label(() -> "Custom Color").hideIf(() -> colorMode.get() != ColorMode.CUSTOM);
    private final ChoiceSetting<HealthBar> healthBar = choice("healthBar", HealthBar.NONE).label(() -> "Health Bar");
    private final BoolSetting players = bool("players", true).label(() -> "Players");
    private final BoolSetting self = bool("self", false).label(() -> "Self");
    private final BoolSetting bots = bool("bots", false).label(() -> "Bots");

    public ModuleESP() {
        super("ESP", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(players, self, bots));
        page.section("renderOptions", s -> s.add(mode, healthBar));
        page.section("colorOptions", s -> s.add(colorMode, customColor));
    }

    private boolean isBot(EntityPlayer player) {
        return Minecraft.getMinecraft().getNetHandler() == null
                || Minecraft.getMinecraft().getNetHandler().getPlayerInfo(player.getUniqueID()) == null;
    }

    private boolean shouldRender(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player.deathTime > 0) return false;
        if (mc.getRenderViewEntity() == null || mc.getRenderViewEntity().getDistanceToEntity(player) > 512.0F) return false;
        if (!player.ignoreFrustumCheck && !EspRenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) return false;
        if (player != mc.thePlayer && player != mc.getRenderViewEntity()) {
            return isBot(player) ? bots.on() : players.on();
        }
        return self.on() && mc.gameSettings.thirdPersonView != 0;
    }

    private boolean sameTeam(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || player == mc.thePlayer) return false;
        net.minecraft.scoreboard.Scoreboard scoreboard = mc.theWorld.getScoreboard();
        net.minecraft.scoreboard.ScorePlayerTeam mine = scoreboard.getPlayersTeam(mc.thePlayer.getName());
        net.minecraft.scoreboard.ScorePlayerTeam theirs = scoreboard.getPlayersTeam(player.getName());
        return mine != null && theirs != null && mine.getTeamName().equals(theirs.getTeamName());
    }

    private int entityColor(EntityPlayer player) {
        ColorMode mode = colorMode.get();
        if (mode == ColorMode.TEAM) return EspRenderUtil.teamColor(player.getName());
        if (mode == ColorMode.TEAMS) return sameTeam(player) ? 0xFF5555FF : 0xFFFF5555;
        return customColor.argb();
    }

    private List<EntityPlayer> targets() {
        Minecraft mc = Minecraft.getMinecraft();
        List<EntityPlayer> out = new ArrayList<EntityPlayer>();
        if (mc.theWorld == null) return out;
        for (Object o : mc.theWorld.playerEntities) {
            if (o instanceof EntityPlayer && shouldRender((EntityPlayer) o)) out.add((EntityPlayer) o);
        }
        // far to near, so the closest players draw last
        Collections.sort(out, new Comparator<EntityPlayer>() {
            @Override public int compare(EntityPlayer a, EntityPlayer b) {
                return Float.compare(mc.getRenderViewEntity().getDistanceToEntity(b),
                        mc.getRenderViewEntity().getDistanceToEntity(a));
            }
        });
        return out;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Mode mode = this.mode.get();
        HealthBar healthBar = this.healthBar.get();
        if (mode == Mode.NONE && healthBar != HealthBar.RAVEN) return;
        List<EntityPlayer> targets = targets();
        if (targets.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();

        // world-space modes
        if (mode == Mode.D3 || mode == Mode.FAKECORNER || mode == Mode.FAKE2D || healthBar == HealthBar.RAVEN) {
            EspRenderUtil.enableRenderState();
            for (EntityPlayer player : targets) {
                if (!player.ignoreFrustumCheck && !EspRenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) continue;
                int color = entityColor(player);
                if (mode == Mode.D3) {
                    EspRenderUtil.drawEntityBoundingBox(player, event, color >> 16 & 255, color >> 8 & 255,
                            color & 255, color >>> 24, 1.5F, 0.1F);
                    GlStateManager.resetColor();
                } else if (mode == Mode.FAKECORNER) {
                    EspRenderUtil.drawCornerESP(player, event, (color >> 16 & 255) / 255.0F,
                            (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F);
                } else if (mode == Mode.FAKE2D) {
                    EspRenderUtil.drawFake2DESP(player, event, (color >> 16 & 255) / 255.0F,
                            (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F);
                }
                if (healthBar == HealthBar.RAVEN) {
                    drawRavenBar(mc, player, event);
                }
            }
            EspRenderUtil.disableRenderState();
        }

        // screen-space modes: project while the camera matrices are current,
        // then swap to an ortho overlay and restore the camera afterwards
        if (mode == Mode.D2 || healthBar == HealthBar.D2) {
            float scale = EspRenderUtil.guiScale();
            List<float[]> boxes = new ArrayList<float[]>();
            List<EntityPlayer> projected = new ArrayList<EntityPlayer>();
            for (EntityPlayer player : targets) {
                float[] box = EspRenderUtil.projectToScreen(player, event, scale);
                if (box != null) {
                    boxes.add(box);
                    projected.add(player);
                }
            }
            if (boxes.isEmpty()) return;
            final List<float[]> finalBoxes = boxes;
            final List<EntityPlayer> finalProjected = projected;
            EspRenderUtil.withOverlay(new Runnable() {
                @Override public void run() {
                    EspRenderUtil.enableRenderState();
                    for (int i = 0; i < finalBoxes.size(); i++) {
                        float[] box = finalBoxes.get(i);
                        EntityPlayer player = finalProjected.get(i);
                        int color = entityColor(player);
                        if (mode == Mode.D2) {
                            EspRenderUtil.drawOutlineRect(box[0], box[1], box[2], box[3], 3.0F, 0,
                                    (color & 16579836) >> 2 | color & 0xFF000000);
                            EspRenderUtil.drawOutlineRect(box[0], box[1], box[2], box[3], 1.5F, 0, color);
                        }
                        if (healthBar == HealthBar.D2) {
                            float heal = player.getHealth() + player.getAbsorptionAmount();
                            float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                            float offset = (box[2] - box[0]) * 0.08F;
                            int healthColor = EspRenderUtil.healthBlend(percent);
                            EspRenderUtil.drawLine(box[0] - offset, box[1], box[0] - offset, box[3], 3.0F,
                                    EspRenderUtil.darker(healthColor, 0.2F));
                            EspRenderUtil.drawLine(box[0] - offset, box[3], box[0] - offset,
                                    box[3] + (box[1] - box[3]) * percent, 1.5F, healthColor);
                        }
                    }
                    EspRenderUtil.disableRenderState();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
            });
        }
    }

    /** Leader-Lite's RAVEN health bar: a billboard bar floating at the player's feet. */
    private void drawRavenBar(Minecraft mc, EntityPlayer player, RenderWorldLastEvent event) {
        RenderManager rm = mc.getRenderManager();
        double[] c = EspRenderUtil.cameraRelative(player, event);
        GlStateManager.pushMatrix();
        GlStateManager.translate(c[0], c[1] - 0.1, c[2]);
        GlStateManager.rotate(rm.playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
        float heal = player.getHealth() + player.getAbsorptionAmount();
        float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
        int healthColor = EspRenderUtil.healthBlend(percent);
        float height = player.height + 0.2F;
        EspRenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, 0xFF000000);
        EspRenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, 0xFF404040);
        EspRenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor);
        GlStateManager.popMatrix();
    }
}
