package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.ItemRenderHooks;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;

public final class ModuleGlintColorizer extends Module {
    private static ModuleGlintColorizer instance;

    private static final ResourceLocation VANILLA = ItemRenderHooks.GLINT;

    private static ResourceLocation alphaTexture = new ResourceLocation("lunarforge", "dynamic/alpha_glint_texture");
    private static boolean alphaBuilt;

    private final BoolSetting showGlint = bool("showGlint", true);
    private final BoolSetting useLunarEquation = bool("useLunarEquation", true);
    private final BoolSetting overrideItemGlint = bool("overrideItemGlint", true);
    private final ColorSetting itemGlintLunarColor = color("itemGlintLunarColor", -865854977);
    private final ColorSetting itemGlintVanillaColor = color("itemGlintVanillaColor", -8372020);
    private final BoolSetting overrideArmorGlint = bool("overrideArmorGlint", true);
    private final ColorSetting armorGlintLunar = color("armorGlintLunar", -865854977);
    private final ColorSetting armorGlintVanilla = color("armorGlintVanilla", -8372020);

    public ModuleGlintColorizer() {
        super("GLINT_COLORIZER", false);
        instance = this;
    }

    @Override protected void layout(Page page) {
        page.add(showGlint);
        page.add(useLunarEquation).hideIf(() -> !showGlint.on());
        page.section("glintColourOptions", s -> {
            s.group(overrideItemGlint, c -> {
                c.add(itemGlintLunarColor).hideIf(() -> !useLunarEquation.on());
                c.add(itemGlintVanillaColor).hideIf(useLunarEquation::on);
            }).hideIf(() -> !showGlint.on());
            s.group(overrideArmorGlint, c -> {
                c.add(armorGlintLunar).hideIf(() -> !useLunarEquation.on());
                c.add(armorGlintVanilla).hideIf(useLunarEquation::on);
            }).hideIf(() -> !showGlint.on());
        });
    }

    public static boolean handles(ItemRenderHooks.Target target) {
        ModuleGlintColorizer m = instance;
        if (m == null || !m.isEnabled()) return false;
        if (!m.showGlint.on()) return true;
        return target == ItemRenderHooks.Target.EQUIPPED_ARMOR ? m.overrideArmorGlint.on() : m.overrideItemGlint.on();
    }

    public static boolean glint(ItemRenderHooks.Target target, IntConsumer render, EntityLivingBase wearer, float time) {
        ModuleGlintColorizer m = instance;
        if (m == null || !m.isEnabled()) return false;
        if (!m.showGlint.on()) return true;
        if (render == null) return false;
        if (target != ItemRenderHooks.Target.EQUIPPED_ARMOR && m.overrideItemGlint.on()) {
            m.itemGlint(render);
            return true;
        }

        if (target == ItemRenderHooks.Target.EQUIPPED_ARMOR && m.overrideArmorGlint.on() && wearer != null) {
            m.armorGlint(() -> render.accept(-1), time);
            return true;
        }
        return false;
    }

    private void itemGlint(IntConsumer render) {
        if (useLunarEquation.on() && alpha() != null) lunarItem(render, itemGlintLunarColor.color(0.0f));
        else vanillaItem(render, itemGlintVanillaColor.color(0.0f));
    }

    private void armorGlint(Runnable render, float time) {
        if (useLunarEquation.on() && alpha() != null) lunarArmor(render, time, armorGlintLunar.color(0.0f));
        else vanillaArmor(render, time, armorGlintVanilla.color(0.0f));
    }

    private static void linear() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private static int darkness(int color) {
        float[] hsb = Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
        return (int)((1.0F - hsb[2] * hsb[2]) * 255.0F) * (color >> 24 & 255) / 255;
    }

    private void lunarItem(IntConsumer render, int color) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(alpha());
        linear();
        GL11.glPushMatrix();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int dark = darkness(color);
        if (dark > 10) itemPasses(render, dark << 24);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        itemPasses(render, color);
        GL11.glPopMatrix();
    }

    private void lunarArmor(Runnable render, float time, int color) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(alpha());
        linear();
        GL11.glPushMatrix();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int dark = darkness(color);
        if (dark > 10) armorPasses(render, time, dark << 24);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        armorPasses(render, time, color);
        GL11.glPopMatrix();
    }

    private void vanillaItem(IntConsumer render, int color) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(VANILLA);
        linear();
        GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        itemPasses(render, color);
    }

    private void vanillaArmor(Runnable render, float time, int color) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(VANILLA);
        linear();
        GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
        armorPasses(render, time, color);
    }

    private static void armorPasses(Runnable render, float time, int color) {
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        for (int i = 0; i < 2; i++) {
            GlStateManager.disableLighting();
            float f = 0.76F;
            GlStateManager.color((color >> 16 & 255) / 255.0F * f, (color >> 8 & 255) / 255.0F * f, (color & 255) / 255.0F * f,
                (color >> 24 & 255) / 255.0F);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            float s = 0.33333334F;
            GlStateManager.scale(s, s, s);
            GlStateManager.rotate(30.0F - i * 60.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translate(0.0, time * (0.001F + i * 0.003F) * 20.0F, 0.0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            render.run();
        }
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();
    }

    private static void itemPasses(IntConsumer render, int color) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        float scale = 8.0F;
        GlStateManager.scale(scale, scale, scale);
        float f = (float)(Minecraft.getSystemTime() % 3000L) / 3000.0F / scale;
        GlStateManager.translate(f, 0.0, 0.0);
        GlStateManager.rotate(-50.0F, 0.0F, 0.0F, 1.0F);
        render.accept(color);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        float f1 = (float)(Minecraft.getSystemTime() % 4873L) / 4873.0F / scale;
        GlStateManager.translate(-f1, 0.0, 0.0);
        GlStateManager.rotate(10.0F, 0.0F, 0.0F, 1.0F);
        render.accept(color);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
    }

    private static ResourceLocation alpha() {
        if (!alphaBuilt) {
            alphaBuilt = true;
            Minecraft mc = Minecraft.getMinecraft();
            BufferedImage in;
            try (InputStream s = mc.getResourceManager().getResource(VANILLA).getInputStream()) {
                in = ImageIO.read(s);
            } catch (Exception e) {
                LogManager.getLogger("LunarForge").warn("Couldn't find item glint resource: {}", VANILLA);
                alphaTexture = null;
                return null;
            }
            BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < in.getWidth(); x++) for (int y = 0; y < in.getHeight(); y++) {
                int c = in.getRGB(x, y);
                int g = ((c >> 16 & 255) + (c >> 8 & 255) + (c & 255)) / 3 & 255;
                out.setRGB(x, y, g << 24 | g << 16 | g << 8 | g);
            }
            mc.getTextureManager().loadTexture(alphaTexture, new DynamicTexture(out));
        }
        return alphaTexture;
    }
}
