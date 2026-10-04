package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.render.TooltipHooks;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class ModuleSaturation extends Module {
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private final BoolSetting showSaturationOverlay = bool("showSaturationOverlay", true);
    private final BoolSetting showHeldItemHunger = bool("showHeldItemHunger", true);
    private final BoolSetting showHeldItemSaturation = bool("showHeldItemSaturation", true);
    private final BoolSetting showAppleskinTooltip = bool("showAppleskinTooltip", true);
    private final BoolSetting useInverseColor = bool("useInverseColor", false);
    private final ColorSetting saturationColor = color("saturationColor", -14848);
    private final Outline outline = new Outline();
    private float flash, flashAlpha;
    private byte flashDirection = 1;

    public ModuleSaturation() {
        super("SATURATION", true);
        child(new Hud(), null);
    }

    @Override protected void layout(Page page) {
        page.section("appleSkinOptions", s -> {
            s.add(showSaturationOverlay, showHeldItemHunger, showHeldItemSaturation, showAppleskinTooltip, useInverseColor);
            s.add(saturationColor).hideIf(useInverseColor::on);
        });
    }

    static final class Hud extends Module {
        Hud() {
            super("SATURATION_HUD_CHILD", false);
            hud(new TextHud(this, 0, 0, HudAnchor.TOP_RIGHT, TextHud.sizes(10, 18, 22, 40, 56, 62)) {
                @Override protected String text(boolean preview) {
                    if (preview) return "0";
                    EntityPlayer p = Minecraft.getMinecraft().thePlayer;
                    if (p == null || p.getFoodStats() == null) return null;
                    return "" + (int)Math.ceil(p.getFoodStats().getSaturationLevel());
                }
            });
        }
    }

    private static boolean food(ItemStack stack, EntityPlayer player) {
        return stack != null && stack.getItem() instanceof ItemFood && player.canEat(alwaysEdible((ItemFood)stack.getItem()));
    }

    private static java.lang.reflect.Field alwaysEdible;

    private static boolean alwaysEdible(ItemFood food) {
        try {
            if (alwaysEdible == null) alwaysEdible = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ItemFood.class, "alwaysEdible", "field_77852_bZ");
            return alwaysEdible.getBoolean(food);
        } catch (Exception e) {
            return false;
        }
    }

    private static int hunger(ItemStack stack) { return stack.getItem() instanceof ItemFood ? ((ItemFood)stack.getItem()).getHealAmount(stack) : 0; }

    private static float saturation(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFood)) return 0.0f;
        ItemFood food = (ItemFood)stack.getItem();
        return food.getHealAmount(stack) * food.getSaturationModifier(stack) * 2.0f;
    }

    private static java.lang.reflect.Field potionId;

    private static boolean rotten(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFood)) return false;
        try {
            if (potionId == null) potionId = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ItemFood.class, "potionId", "field_77851_ca");
            int id = potionId.getInt(stack.getItem());
            return id > 0 && Potion.potionTypes[id] != null && Potion.potionTypes[id].isBadEffect();
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        flash += flashDirection * 0.125f;
        if (flash >= 1.5f) flashDirection = -1;
        else if (flash <= -0.5f) flashDirection = 1;
        flashAlpha = Math.max(0.0f, Math.min(1.0f, flash)) * 0.85f;
    }

    private void resetFlash() { flash = 0; flashAlpha = 0; flashDirection = 1; }

    private boolean foodDrawn;

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type == RenderGameOverlayEvent.ElementType.FOOD) foodDrawn = true;
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        boolean drawn = foodDrawn;
        foodDrawn = false;
        if (!isEnabled()) return;
        if (!drawn) { resetFlash(); return; }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        ScaledResolution res = event.resolution;
        int top = res.getScaledHeight() - 39, right = res.getScaledWidth() / 2 + 91;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (showSaturationOverlay.on()) saturationIcons(player, 0.0f, right, top, 1.0f);
        ItemStack held = player.getHeldItem();
        if (held == null || !food(held, player)) { resetFlash(); return; }
        int hunger = hunger(held);
        float saturation = saturation(held);
        if (showHeldItemHunger.on()) hungerIcons(player, hunger, right, top, flashAlpha, rotten(held));
        if (showHeldItemSaturation.on()) {
            FoodStats stats = player.getFoodStats();
            int level = stats.getFoodLevel() + hunger;
            float total = stats.getSaturationLevel() + saturation;
            float added = total > level ? level - stats.getSaturationLevel() : saturation;
            saturationIcons(player, added, right, top, flashAlpha);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private void saturationIcons(EntityPlayer player, float added, int right, int top, float alpha) {
        FoodStats stats = player.getFoodStats();
        float saturation = stats.getSaturationLevel();
        if (saturation + added < 0.0f) return;
        int start = added != 0.0f ? (int)Math.max(saturation / 2.0f, 0.0f) : 0;
        float total = Math.max(0.0f, Math.min(20.0f, saturation + added));
        int end = (int)Math.ceil(total / 2.0f);
        if (start >= end) return;
        boolean hungry = player.isPotionActive(Potion.hunger);
        for (int i = start; i < end; ++i) {
            float x = right - i * 8 - 9;
            float y = jitter(top, stats);
            int color = withAlpha(colour(hungry, x + y), alpha);
            outline.draw(hungry, x, y, total / 2.0f - i, color);
        }
    }

    private void hungerIcons(EntityPlayer player, int hunger, int right, int top, float alpha, boolean rotten) {
        if (hunger <= 0) return;
        FoodStats stats = player.getFoodStats();
        int level = stats.getFoodLevel();
        int total = Math.max(0, Math.min(20, level + hunger));
        int start = Math.max(0, level / 2), end = (int)Math.ceil(total / 2.0f);
        for (int i = start; i < end; ++i) {
            float x = right - i * 8 - 9;
            float y = jitter(top, stats);
            boolean half = i * 2 + 1 == total;

            sprite(rotten ? 133 : 25, x, y, (int)(alpha * 0.25f * 255.0f) << 24 | 0xFFFFFF);
            sprite(half ? (rotten ? 97 : 61) : (rotten ? 88 : 52), x, y, (int)(alpha * 255.0f) << 24 | 0xFFFFFF);
        }
    }

    private float jitter(float y, FoodStats stats) {
        int ticks = Minecraft.getMinecraft().ingameGUI.getUpdateCounter();
        if (stats.getSaturationLevel() <= 0.0f && ticks % (stats.getFoodLevel() * 3 + 1) == 0) {
            y += ThreadLocalRandom.current().nextInt(3) - 1;
        }
        return y;
    }

    int colour(boolean hunger, float position) {
        return useInverseColor.on() ? outline.inverse(hunger) : saturationColor.color(position);
    }

    private static int withAlpha(int c, float f) {
        if (f == 1.0f) return c;
        int a = Math.max(0, Math.min(255, (int)((c >>> 24) * f)));
        return a << 24 | c & 0xFFFFFF;
    }

    private static void sprite(int u, float x, float y, int argb) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ICONS);
        Draw.blit(ICONS, x, y, u, 27, 9, 9, 256, 256, argb);
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!isEnabled() || !showAppleskinTooltip.on() || event.itemStack == null || event.toolTip.isEmpty()) return;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        ItemStack stack = event.itemStack;
        if (player == null || !(stack.getItem() instanceof ItemFood) || hasLore(stack)) return;
        int hunger = hunger(stack);
        int hungerBars = (int)Math.ceil(Math.abs(hunger) / 2.0f);
        if (hungerBars == 0) return;
        float saturation = saturation(stack);
        int saturationBars = (int)Math.ceil(Math.abs(saturation) / 2.0f);
        String hungerLabel = null, saturationLabel = null;
        float hungerWidth, saturationWidth;
        if (hungerBars > 10) {
            hungerLabel = "x" + (hunger < 0 ? -hungerBars : hungerBars);
            hungerWidth = 10.0f + Draw.width(hungerLabel);
        } else hungerWidth = hungerBars * 9;
        if (saturationBars > 10 || saturationBars == 0) {
            saturationLabel = "x" + (saturation < 0.0f ? -saturationBars : saturationBars);
            saturationWidth = 10.0f + Draw.width(saturationLabel);
        } else saturationWidth = saturationBars * 9;
        final boolean rotten = rotten(stack);
        final int hb = hungerBars > 10 ? 1 : hungerBars, sb = saturationBars > 10 ? 1 : saturationBars;
        final String hl = hungerLabel, sl = saturationLabel;
        final int modified = hunger, base = hunger;
        final float sat = saturation;
        TooltipHooks.insert(event.toolTip, 1, (int)Math.ceil(Math.max(hungerWidth, saturationWidth)), 20,
            (x, y) -> drawTooltip(x, y, rotten, hb, sb, hl, sl, modified, base, sat));
    }

    private static boolean hasLore(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("display", 10)
            && stack.getTagCompound().getCompoundTag("display").hasKey("Lore", 9);
    }

    private void drawTooltip(int x0, int y0, boolean rotten, int hungerBars, int saturationBars, String hungerLabel, String saturationLabel,
                             int modified, int base, float saturation) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x0, y0, 0);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int x = (hungerBars - 1) * 9;
        for (int i = 0; i < hungerBars * 2; i += 2) {
            sprite(modified < 0 ? 43 : rotten ? 133 : 16, x, 0, -1);

            int marker = (rotten ? 106 : 70) + (base - 1 == i ? 9 : 0);
            sprite(marker, x, 0, 0x40FFFFFF);
            if (modified > i) {
                boolean half = modified - 1 == i;
                sprite(half ? (rotten ? 97 : 61) : (rotten ? 88 : 52), x, 0, -1);
            }
            x -= 9;
        }
        if (hungerLabel != null) label(x + 18, 1, hungerLabel, 2);
        x = (saturationBars - 1) * 9;
        float f = Math.abs(saturation);
        for (int i = 0; i < saturationBars * 2; i += 2) {
            int c = saturation < 0.0f ? -5565952 : colour(rotten, x + 10);
            float alpha = f <= i ? 0.5f : 1.0f;
            sprite(rotten ? 88 : 52, x, 10, withAlpha(-14145496, alpha));
            outline.draw(rotten, x, 10, 1.0f, withAlpha(-16119286, alpha));
            outline.draw(rotten, x, 10, (f - i) / 2.0f, withAlpha(c, alpha));
            x -= 9;
        }
        if (saturationLabel != null) label(x + 18, 11, saturationLabel, 1);
        GlStateManager.popMatrix();
    }

    private static void label(int x, int y, String text, int dy) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.75f, 0.75f, 1.0f);
        Draw.text(text, 3, dy, -5592406, false);
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Post event) { outline.reset(); }

    private static final class Outline {
        private Texture plain, hunger;

        private static final class Texture {
            final ResourceLocation location; final boolean empty; final int resolution, inverse;
            Texture(ResourceLocation location, boolean empty, int resolution, int inverse) {
                this.location = location; this.empty = empty; this.resolution = resolution; this.inverse = inverse;
            }
        }

        void reset() {
            TextureManagerHolder.delete(plain);
            TextureManagerHolder.delete(hunger);
            plain = hunger = null;
        }

        int inverse(boolean hungerSprite) { return texture(hungerSprite).inverse; }

        private Texture texture(boolean hungerSprite) {
            if (hungerSprite) { if (hunger == null) hunger = build(true); return hunger; }
            if (plain == null) plain = build(false);
            return plain;
        }

        void draw(boolean hungerSprite, float x, float y, float amount, int color) {
            if (amount <= 0.0f) return;
            Texture t = texture(hungerSprite);
            if (t.empty) return;
            int n = t.resolution;
            int stage = amount >= 1.0f ? 3 : Math.max(0, Math.min(2, (int)Math.ceil(amount * 4.0f) - 1));
            float s = 9.0f / n;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(s, s, 1);
            Minecraft.getMinecraft().getTextureManager().bindTexture(t.location);
            GlStateManager.color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
            float u0 = stage * n / (float)(n * 4), u1 = (stage + 1) * n / (float)(n * 4);
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            wr.pos(0, n, 0).tex(u0, 1).endVertex();
            wr.pos(n, n, 0).tex(u1, 1).endVertex();
            wr.pos(n, 0, 0).tex(u1, 0).endVertex();
            wr.pos(0, 0, 0).tex(u0, 0).endVertex();
            tess.draw();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }

        private Texture build(boolean hungerSprite) {
            ResourceLocation location = new ResourceLocation("lunarforge", hungerSprite ? "saturation_outline_hunger" : "saturation_outline");
            BufferedImage icons = load(ICONS);
            int fullU = hungerSprite ? 88 : 52, emptyU = hungerSprite ? 133 : 16;
            int n = icons == null ? 9 : Math.max(9, Math.min(36, Math.round(9.0f * (icons.getWidth() / 256.0f))));
            BitSet mask = new BitSet(n * n);
            int emptyInverse = mask(mask, icons, emptyU, n);
            int fullInverse = mask(mask, icons, fullU, n);
            int thickness = (int)Math.ceil(n / 9.0f);
            int inverse = blend(emptyInverse, fullInverse);
            List<int[]> edge = new ArrayList<int[]>();
            for (int y = 0; y < n; ++y) for (int x = 0; x < n; ++x) if (edge(mask, n, thickness, x, y)) edge.add(new int[]{x, y});
            if (edge.isEmpty()) return new Texture(location, true, n, inverse);
            BufferedImage out = new BufferedImage(n * 4, n, BufferedImage.TYPE_INT_ARGB);
            for (int[] p : edge) {
                float f = p[1] + 0.25f * (p[0] - n / 2.0f);
                int g = Math.round(255.0f - 115.0f * Math.max(0.0f, Math.min(1.0f, f / n)));
                int c = 0xFF000000 | g << 16 | g << 8 | g;
                float s = ((n - p[0]) - 0.25f * (p[1] - n / 2.0f)) / n;
                for (int i = Math.max(0, Math.min(3, (int)Math.ceil(s * 4.0f) - 1)); i < 4; ++i) out.setRGB(i * n + p[0], p[1], c);
            }
            Minecraft.getMinecraft().getTextureManager().loadTexture(location, new DynamicTexture(out));
            return new Texture(location, false, n, inverse);
        }

        private static boolean set(BitSet mask, int n, int x, int y) { return x >= 0 && y >= 0 && x < n && y < n && mask.get(x + y * n); }

        private static boolean edge(BitSet mask, int n, int t, int x, int y) {
            if (!set(mask, n, x, y)) return false;
            for (int dy = -t; dy <= t; ++dy) for (int dx = -t; dx <= t; ++dx) {
                if (dx * dx + dy * dy <= t * t && !set(mask, n, x + dx, y + dy)) return true;
            }
            return false;
        }

        private static int mask(BitSet mask, BufferedImage img, int u, int n) {
            if (img == null) return -1;
            float f = img.getWidth() / 256.0f, size = 9.0f * f, x0 = u * f, y0 = 27 * f;
            int r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < n; ++y) for (int x = 0; x < n; ++x) {
                int px = (int)(x0 + (x + 0.5f) * size / n), py = (int)(y0 + (y + 0.5f) * size / n);
                if (px >= img.getWidth() || py >= img.getHeight()) continue;
                int c = img.getRGB(px, py);
                if ((c >>> 24) < 128) continue;
                mask.set(x + y * n);
                r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; ++count;
            }
            if (count == 0) return -1;
            return 0xFF000000 | 255 - r / count << 16 | 255 - g / count << 8 | 255 - b / count;
        }

        private static int blend(int a, int b) {
            int r = (int)((a >> 16 & 255) * 0.5f + (b >> 16 & 255) * 0.5f), g = (int)((a >> 8 & 255) * 0.5f + (b >> 8 & 255) * 0.5f),
                bl = (int)((a & 255) * 0.5f + (b & 255) * 0.5f);
            return 0xFF000000 | r << 16 | g << 8 | bl;
        }

        private static BufferedImage load(ResourceLocation location) {
            try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
                return ImageIO.read(in);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final class TextureManagerHolder {
        static void delete(Outline.Texture t) {
            if (t != null && !t.empty) Minecraft.getMinecraft().getTextureManager().deleteTexture(t.location);
        }
    }
}
