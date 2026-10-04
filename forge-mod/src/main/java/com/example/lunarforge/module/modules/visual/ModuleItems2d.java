package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.ChoiceSetting;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFireworkCharge;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class ModuleItems2d extends Module {
    public enum Type implements ChoiceSetting.Option {
        SPRITE, MODEL;
        @Override public String langId() { return null; }
    }

    private static final Random RANDOM = new Random();
    private static final ResourceLocation POTION_BOTTLE = new ResourceLocation("minecraft", "items/potion_bottle_drinkable");
    private static final ResourceLocation SPAWN_EGG_OVERLAY = new ResourceLocation("minecraft", "items/spawn_egg_overlay");
    private static final ResourceLocation FIREWORK_CHARGE_OVERLAY = new ResourceLocation("minecraft", "items/fireworks_charge_overlay");

    private static final List<ResourceLocation> LEATHER_OVERLAYS = Arrays.asList(
        new ResourceLocation("minecraft", "items/leather_helmet_overlay"),
        new ResourceLocation("minecraft", "items/leather_chestplate_overlay"),
        new ResourceLocation("minecraft", "items/leather_leggings_overlay"),
        new ResourceLocation("minecraft", "items/leather_boots_overlay"));

    private final ChoiceSetting<Type> renderingOptions = choice("renderingOptions", Type.SPRITE);

    public ModuleItems2d() {
        super("2D_ITEMS", false);
    }

    @Override protected void layout(Page page) {
        page.add(renderingOptions);
    }

    public static boolean render(EntityItem entity, double x, double y, double z, float partialTicks) {
        Module module = ModuleManager.get("2D_ITEMS");
        if (!(module instanceof ModuleItems2d) || !module.isEnabled()) return false;
        Module physics = ModuleManager.get("ITEM_PHYSICS");
        if (physics != null && physics.isEnabled()) return false;
        GlStateManager.pushMatrix();
        ((ModuleItems2d)module).draw(entity, x, y, z, partialTicks);
        GlStateManager.popMatrix();
        return true;
    }

    private void draw(EntityItem entity, double x, double y, double z, float partialTicks) {
        RANDOM.setSeed(187L);
        ItemStack stack = entity.getEntityItem();
        Item item = stack.getItem();
        if (item == null) return;
        if (item instanceof ItemBlock || item instanceof ItemSkull || item instanceof ItemBed || renderingOptions.get() == Type.MODEL) {
            drawModel(entity, stack, x, y, z, partialTicks);
            return;
        }
        Minecraft mc = mc();
        TextureAtlasSprite sprite = mc.getRenderItem().getItemModelMesher().getParticleIcon(item, stack.getItemDamage());
        boolean tinted = false;
        int color = 0xFF000000;
        if (item instanceof ItemArmor || item instanceof ItemPotion || item instanceof ItemMonsterPlacer) {
            tinted = true;
            color = item.getColorFromItemStack(stack, 0);
        }
        int count = count(stack.stackSize);
        WorldRenderer buffer = Tessellator.getInstance().getWorldRenderer();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableStandardItemLighting();
        float bob = MathHelper.sin((entity.getAge() + partialTicks) / 10.0f + entity.hoverStart) * 0.1f + 0.1f;
        GL11.glTranslated(x, y + bob + 0.1, z);
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        for (int i = 0; i < count; i++) {
            GlStateManager.pushMatrix();
            faceCamera(i);

            GL11.glScalef(0.5f, 0.5f, 0.5f);
            ResourceLocation overlay = overlay(item);
            if (overlay != null) {
                TextureAtlasSprite layer = mc.getTextureMapBlocks().getAtlasSprite(overlay.toString());
                int layerColor = item.getColorFromItemStack(stack, 1);
                quad(buffer, layer, layerColor >> 16 & 0xFF, layerColor >> 8 & 0xFF, layerColor & 0xFF, true);
            }
            quad(buffer, sprite, r, g, b, tinted);
            GlStateManager.popMatrix();
        }
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private void drawModel(EntityItem entity, ItemStack stack, double x, double y, double z, float partialTicks) {
        RANDOM.setSeed(187L);
        Minecraft mc = mc();
        IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
        int count = count(stack.stackSize);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.pushMatrix();

        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        float groundScale = model.getItemCameraTransforms().ground.scale.y;
        float bob = MathHelper.sin((entity.getAge() + partialTicks) / 10.0f + entity.hoverStart) * 0.1f + 0.1f;
        GL11.glTranslated(x, y + bob + 0.25 * groundScale, z);
        for (int i = 0; i < count; i++) {
            GlStateManager.pushMatrix();
            faceCamera(i);
            if (model.isBuiltInRenderer() && !(stack.getItem() instanceof ItemSkull)) {
                GlStateManager.rotate(180.0f, 0.0f, 1.0f, 0.0f);
            } else if (stack.getItem().getUnlocalizedName().contains("Fence")) {
                GlStateManager.rotate(90.0f, 0.0f, 1.0f, 0.0f);
            }
            if (model.isGui3d()) GlStateManager.scale(0.5, 0.5, 0.5);
            model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.GROUND);
            mc.getRenderItem().renderItem(stack, model);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private static void quad(WorldRenderer buffer, TextureAtlasSprite sprite, int r, int g, int b, boolean tinted) {
        buffer.begin(7, tinted ? DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL : DefaultVertexFormats.POSITION_TEX_NORMAL);
        vertex(buffer.pos(-0.5, -0.25, 0.0).tex(sprite.getMinU(), sprite.getMaxV()), r, g, b, tinted).normal(0.0f, 1.0f, 0.0f).endVertex();
        vertex(buffer.pos(0.5, -0.25, 0.0).tex(sprite.getMaxU(), sprite.getMaxV()), r, g, b, tinted).normal(0.0f, 1.0f, 0.0f).endVertex();
        vertex(buffer.pos(0.5, 0.75, 0.0).tex(sprite.getMaxU(), sprite.getMinV()), r, g, b, tinted).normal(0.0f, 1.0f, 0.0f).endVertex();
        vertex(buffer.pos(-0.5, 0.75, 0.0).tex(sprite.getMinU(), sprite.getMinV()), r, g, b, tinted).normal(0.0f, 1.0f, 0.0f).endVertex();
        Tessellator.getInstance().draw();
    }

    private static WorldRenderer vertex(WorldRenderer buffer, int r, int g, int b, boolean tinted) {
        if (tinted) buffer.color(r, g, b, 255);
        return buffer;
    }

    private static int count(int size) {
        int n = 1;
        if (size > 1) n = 2;
        if (size > 5) n = 3;
        if (size > 20) n = 4;
        if (size > 40) n = 5;
        return n;
    }

    private static void faceCamera(int index) {
        if (index > 0) {
            float dx = (RANDOM.nextFloat() * 2.0f - 1.0f) * 0.3f;
            float dy = (RANDOM.nextFloat() * 2.0f - 1.0f) * 0.3f;
            float dz = (RANDOM.nextFloat() * 2.0f - 1.0f) * 0.3f;
            GL11.glTranslatef(dx, dy, dz);
        }
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glRotatef(180.0f - mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(-mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
    }

    private static ResourceLocation overlay(Item item) {
        if (item instanceof ItemPotion) return POTION_BOTTLE;
        if (item instanceof ItemMonsterPlacer) return SPAWN_EGG_OVERLAY;
        if (item instanceof ItemFireworkCharge) return FIREWORK_CHARGE_OVERLAY;
        if (item instanceof ItemArmor && ((ItemArmor)item).getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER) {
            return LEATHER_OVERLAYS.get(((ItemArmor)item).armorType);
        }
        return null;
    }
}
