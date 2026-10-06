package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.EspRenderUtil;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

/**
 * Ported from Leader-Lite (leader.module.modules.render.NameTags): camera-facing
 * world-space tags with distance/health suffixes, background, armor items and
 * potion icons for players, plus per-mob-type filters (bosses, creepers,
 * endermen, blazes, other mobs, animals). The friend/enemy manager borders are
 * not ported (LunarForge has no friend/target manager).
 *
 * Text metrics use the vanilla 9px font, so the world scale factor is doubled
 * from Leader-Lite's custom 18px font (0.0075 -> 0.0135).
 */
public final class ModuleNameTags extends Module {

    public enum DistanceMode implements ChoiceSetting.Option {
        NONE("none"), DEFAULT("default"), VAPE("vape");
        private final String id;
        DistanceMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    public enum HealthMode implements ChoiceSetting.Option {
        NONE("none"), HP("hp"), HEARTS("hearts"), TAB("tab");
        private final String id;
        HealthMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    private static final DecimalFormat HEALTH_FORMAT =
            new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private final NumberSetting scale = decimal("scale", 1.0f, 0.5f, 2.0f).label(() -> "Scale");
    private final BoolSetting autoScale = bool("autoScale", true).label(() -> "Auto Scale");
    private final NumberSetting backgroundOpacity = integer("background", 25, 0, 100).label(() -> "Background");
    private final BoolSetting shadow = bool("shadow", true).label(() -> "Shadow");
    private final ChoiceSetting<DistanceMode> distanceMode = choice("distanceMode", DistanceMode.NONE).label(() -> "Distance");
    private final ChoiceSetting<HealthMode> healthMode = choice("healthMode", HealthMode.HEARTS).label(() -> "Health");
    private final BoolSetting armor = bool("armor", true).label(() -> "Armor");
    private final BoolSetting effects = bool("effects", true).label(() -> "Effects");
    private final BoolSetting players = bool("players", true).label(() -> "Players");
    private final BoolSetting self = bool("self", false).label(() -> "Self");
    private final BoolSetting bots = bool("bots", false).label(() -> "Bots");
    private final BoolSetting bosses = bool("bosses", false).label(() -> "Bosses");
    private final BoolSetting mobs = bool("mobs", false).label(() -> "Mobs");
    private final BoolSetting creepers = bool("creepers", false).label(() -> "Creepers");
    private final BoolSetting endermen = bool("endermen", false).label(() -> "Endermen");
    private final BoolSetting blazes = bool("blazes", false).label(() -> "Blazes");
    private final BoolSetting animals = bool("animals", false).label(() -> "Animals");

    public ModuleNameTags() {
        super("NAME_TAGS", false);
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(players, self, bots, bosses, mobs, creepers, endermen, blazes, animals));
        page.section("nametagOptions", s -> s.add(scale, autoScale, backgroundOpacity, shadow, distanceMode, healthMode, armor, effects));
    }

    private boolean isBot(EntityPlayer player) {
        return Minecraft.getMinecraft().getNetHandler() == null
                || Minecraft.getMinecraft().getNetHandler().getPlayerInfo(player.getUniqueID()) == null;
    }

    /** Leader-Lite shouldRenderTags without the friend/enemy branches. */
    private boolean shouldRenderTags(EntityLivingBase entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (entity.deathTime > 0) return false;
        if (mc.getRenderViewEntity() == null || mc.getRenderViewEntity().getDistanceToEntity(entity) > 512.0F) return false;
        if (entity instanceof EntityPlayer) {
            if (entity != mc.thePlayer && entity != mc.getRenderViewEntity()) {
                return isBot((EntityPlayer) entity) ? bots.on() : players.on();
            }
            return self.on() && mc.gameSettings.thirdPersonView != 0;
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) {
            return !entity.isInvisible() && bosses.on();
        }
        if (entity instanceof EntityCreeper) return creepers.on();
        if (entity instanceof EntityEnderman) return endermen.on();
        if (entity instanceof EntityBlaze) return blazes.on();
        if (entity instanceof EntityMob || entity instanceof EntitySlime) return mobs.on();
        return (entity instanceof EntityAnimal || entity instanceof EntityBat
                || entity instanceof EntitySquid || entity instanceof EntityVillager) && animals.on();
    }

    /** Leader-Lite TeamUtil.stripName: trailing color code stripped, reset forced to white. */
    private static String stripName(Entity entity) {
        return entity.getDisplayName().getFormattedText()
                .replaceAll("\u00A7\\S$", "")
                .replaceAll("(?i)\u00A7r", "\u00A7f")
                .trim();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) return;

        List<EntityLivingBase> entities = new ArrayList<EntityLivingBase>();
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityLivingBase && shouldRenderTags((EntityLivingBase) o)) {
                entities.add((EntityLivingBase) o);
            }
        }
        if (entities.isEmpty()) return;
        final Minecraft finalMc = mc;
        Collections.sort(entities, new Comparator<EntityLivingBase>() {
            @Override public int compare(EntityLivingBase a, EntityLivingBase b) {
                return Float.compare(finalMc.getRenderViewEntity().getDistanceToEntity(b),
                        finalMc.getRenderViewEntity().getDistanceToEntity(a));
            }
        });

        for (EntityLivingBase entity : entities) {
            if (!entity.ignoreFrustumCheck && !EspRenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0)) continue;
            String teamName = stripName(entity);
            if (StringUtils.isBlank(net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) continue;

            double[] c = EspRenderUtil.cameraRelative(entity, event);
            double distance = mc.getRenderViewEntity().getDistanceToEntity(entity);
            GlStateManager.pushMatrix();
            GlStateManager.translate(c[0], c[1] + entity.getEyeHeight() + (entity.isSneaking() ? 0.225 : 0.4), c[2]);
            GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
            float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
            GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);
            double worldScale = Math.pow(
                    Math.min(Math.max(autoScale.on() ? distance : 0.0, 6.0), 128.0), 0.75) * 0.0135;
            GlStateManager.scale(-worldScale * scale.value(), -worldScale * scale.value(), 1.0);

            String distanceText = "";
            DistanceMode distanceMode = this.distanceMode.get();
            if (distanceMode == DistanceMode.DEFAULT) {
                distanceText = String.format("&7%dm&r ", (int) distance);
            } else if (distanceMode == DistanceMode.VAPE) {
                distanceText = String.format("&a[&f%d&a]&r ", (int) distance);
            }

            float health = entity.getHealth();
            float absorption = entity.getAbsorptionAmount();
            float max = entity.getMaxHealth();
            float percent = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
            String healText = "";
            HealthMode healthMode = this.healthMode.get();
            if (healthMode == HealthMode.HP) {
                healText = String.format(" %d%s", (int) health,
                        absorption > 0.0F ? String.format(" &6%d&r", (int) absorption) : "&r");
            } else if (healthMode == HealthMode.HEARTS) {
                healText = String.format(" %s%s", HEALTH_FORMAT.format(health / 2.0),
                        absorption > 0.0F ? String.format(" &6%s&r", HEALTH_FORMAT.format(absorption / 2.0)) : "&r");
            } else if (healthMode == HealthMode.TAB && entity instanceof EntityPlayer) {
                net.minecraft.scoreboard.Scoreboard scoreboard = mc.theWorld.getScoreboard();
                if (scoreboard != null) {
                    ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
                    if (objective != null) {
                        Score score = scoreboard.getValueFromObjective(entity.getName(), objective);
                        healText = String.format(" &e%d&r", score.getScorePoints());
                    }
                }
            }

            String text = String.format("%s&f%s&r%s", distanceText, teamName, healText).replace('&', '\u00A7');
            int width = mc.fontRendererObj.getStringWidth(text);
            int fontHeight = mc.fontRendererObj.FONT_HEIGHT;

            if (backgroundOpacity.intValue() > 0) {
                int alpha = (int) (backgroundOpacity.intValue() / 100.0F * 255.0F) & 0xFF;
                int bg = !entity.isSneaking() && !entity.isInvisible()
                        ? alpha << 24
                        : alpha << 24 | 0x540054;
                EspRenderUtil.enableRenderState();
                EspRenderUtil.drawRect(-width / 2.0F - 1.0F, -fontHeight - 1.0F,
                        width / 2.0F + (shadow.on() ? 1.0F : 0.0F), shadow.on() ? 0.0F : -1.0F, bg);
                EspRenderUtil.disableRenderState();
            }

            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            mc.fontRendererObj.drawString(text, -width / 2.0F, -fontHeight,
                    EspRenderUtil.healthBlend(percent), shadow.on());
            GlStateManager.enableDepth();

            if (entity instanceof EntityPlayer) {
                drawArmorAndEffects(mc, (EntityPlayer) entity, fontHeight);
            }
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
        }
    }

    /** Held item + armor slots above the tag, potion icons above those (Leader-Lite layout). */
    private void drawArmorAndEffects(Minecraft mc, EntityPlayer player, int fontHeight) {
        int height = fontHeight + 2;
        if (armor.on()) {
            List<ItemStack> items = new ArrayList<ItemStack>();
            for (int i = 4; i >= 0; i--) {
                ItemStack stack = i == 0 ? player.getHeldItem() : player.inventory.armorInventory[i - 1];
                if (stack != null) items.add(stack);
            }
            if (!items.isEmpty()) {
                int offset = items.size() * -8;
                for (int i = 0; i < items.size(); i++) {
                    EspRenderUtil.renderItemInGUI(items.get(i), offset + i * 16, -height - 16);
                }
                height += 16;
            }
        }
        if (effects.on()) {
            List<PotionEffect> potionEffects = new ArrayList<PotionEffect>();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (Potion.potionTypes[effect.getPotionID()].hasStatusIcon()) potionEffects.add(effect);
            }
            if (!potionEffects.isEmpty()) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5F, 0.5F, 1.0F);
                int offset = potionEffects.size() * -9;
                for (int i = 0; i < potionEffects.size(); i++) {
                    EspRenderUtil.renderPotionEffect(potionEffects.get(i), offset + i * 18, -(height * 2) - 18);
                }
                GlStateManager.popMatrix();
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
