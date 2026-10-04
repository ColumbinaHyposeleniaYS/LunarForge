package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.cosmetics.Cosmetic;
import com.example.lunarforge.cosmetics.CosmeticType;
import com.example.lunarforge.cosmetics.render.CosmeticLayers;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import com.example.lunarforge.util.Fields;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelHumanoidHead;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class Module3dSkins extends Module {
    private static Module3dSkins instance;
    private static final float SUIT_CAP = 1.08f;

    private final BoolSetting enableHat = bool("enableHat", true);
    private final BoolSetting enableJacket = bool("enableJacket", true);
    private final BoolSetting enableLeftSleeve = bool("enableLeftSleeve", true);
    private final BoolSetting enableRightSleeve = bool("enableRightSleeve", true);
    private final BoolSetting enableLeftPants = bool("enableLeftPants", true);
    private final BoolSetting enableRightPants = bool("enableRightPants", true);
    private final NumberSetting baseVoxelSize = decimal("baseVoxelSize", 1.15f, 1.001f, 1.4f);
    private final NumberSetting bodyVoxelWidthSize = decimal("bodyVoxelWidthSize", 1.05f, 1.001f, 1.4f);
    private final NumberSetting headVoxelSize = decimal("headVoxelSize", 1.18f, 1.001f, 1.25f);
    private final NumberSetting firstPersonVoxelSize = decimal("firstPersonVoxelSize", 1.1f, 1.001f, 1.3f);
    private final NumberSetting renderDistanceLod = integer("renderDistanceLod", 14, 5, 40);
    private final BoolSetting enableSkulls = bool("enableSkulls", true);
    private final BoolSetting enableSkullsItems = bool("enableSkullsItems", true);
    private final NumberSetting skullVoxelSize = decimal("skullVoxelSize", 1.1f, 1.001f, 1.2f);
    private final BoolSetting showOthers = bool("showOthers", true);

    private final Map<ResourceLocation, SkinVoxels.Mesh> heads = new HashMap<ResourceLocation, SkinVoxels.Mesh>();
    private final Map<String, SkinVoxels.Mesh[]> bodies = new HashMap<String, SkinVoxels.Mesh[]>();

    private static boolean skullActive;
    private static boolean installed;

    private static final Field DOWNLOADED = Fields.find(ThreadDownloadImageData.class, "bufferedImage", "field_110560_d");
    private static Field humanoidHead;

    public Module3dSkins() {
        super("3D_SKINS", false);
        instance = this;
    }

    @Override protected void layout(Page page) {
        page.section("performanceOptions", s -> s.add(renderDistanceLod, showOthers));
        page.section("playerModelOptions", s -> s.add(enableHat, enableJacket, enableLeftSleeve, enableRightSleeve, enableLeftPants, enableRightPants,
            baseVoxelSize, bodyVoxelWidthSize, headVoxelSize, firstPersonVoxelSize));
        page.section("skullModelOptions", s -> s.add(enableSkulls, enableSkullsItems, skullVoxelSize));
    }

    static Module3dSkins get() { return instance; }

    private static boolean on() { return instance != null && instance.isEnabled(); }

    @Override protected void onEnable() { install(); }

    private static void install() {
        if (installed) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getRenderManager() == null) return;
        installed = true;
        for (RenderPlayer render : mc.getRenderManager().getSkinMap().values()) {
            try {
                List<LayerRenderer<?>> layers = ReflectionHelper.getPrivateValue(RendererLivingEntity.class, render, "layerRenderers", "field_177097_h");
                layers.add(new HatLayer(render));
                layers.add(new BodyLayer(render));
            } catch (Exception e) {
                org.apache.logging.log4j.LogManager.getLogger("LunarForge").error("Could not add 3D skin layers", e);
            }
        }
    }

    private int distanceSq() { int n = renderDistanceLod.intValue(); return n * n; }

    private static boolean suit(EntityPlayer player) {
        for (Cosmetic c : CosmeticLayers.worn(player)) if (c.type == CosmeticType.SUITS) return true;
        return false;
    }

    private static float size(NumberSetting option, boolean suit) { return suit ? Math.min(option.value(), SUIT_CAP) : option.value(); }

    private boolean shows(AbstractClientPlayer player) {
        if (!isEnabled()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (!showOthers.on() && mc.thePlayer != null && !player.getUniqueID().equals(mc.thePlayer.getUniqueID())) return false;
        if (!player.hasSkin() || player.isInvisible() || !enableHat.on()) return false;
        Entity camera = mc.getRenderViewEntity();
        if (camera != null && player.getDistanceSqToEntity(camera) > distanceSq()) return false;
        return true;
    }

    @SubscribeEvent
    public void onWorld(WorldEvent.Load event) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation own = mc.thePlayer == null ? null : mc.thePlayer.getLocationSkin();
        SkinVoxels.Mesh head = own == null ? null : heads.get(own);
        for (SkinVoxels.Mesh m : heads.values()) if (m != head) m.delete();
        heads.clear();
        if (head != null) heads.put(own, head);
        Map<String, SkinVoxels.Mesh[]> keep = new HashMap<String, SkinVoxels.Mesh[]>();
        for (Map.Entry<String, SkinVoxels.Mesh[]> e : bodies.entrySet()) {
            if (own != null && e.getKey().startsWith(own + "|")) keep.put(e.getKey(), e.getValue());
            else for (SkinVoxels.Mesh m : e.getValue()) m.delete();
        }
        bodies.clear();
        bodies.putAll(keep);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof AbstractClientPlayer)) return;
        ResourceLocation skin = ((AbstractClientPlayer)event.entity).getLocationSkin();
        SkinVoxels.Mesh h = heads.remove(skin);
        if (h != null) h.delete();
        for (String key : new String[]{skin + "|true", skin + "|false"}) {
            SkinVoxels.Mesh[] b = bodies.remove(key);
            if (b != null) for (SkinVoxels.Mesh m : b) m.delete();
        }
    }

    private static SkinVoxels.Image image(ResourceLocation skin) {
        Minecraft mc = Minecraft.getMinecraft();
        ITextureObject tex = mc.getTextureManager().getTexture(skin);
        if (tex == null) return null;
        if (tex instanceof ThreadDownloadImageData) {
            BufferedImage img = (BufferedImage)Fields.get(DOWNLOADED, tex);
            if (img != null) return of(img);
        }

        GlStateManager.bindTexture(tex.getGlTextureId());
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w != 64 || h != 64) return null;
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, buf.getInt((y * w + x) * 4));
        return of(img);
    }

    private static SkinVoxels.Image of(final BufferedImage img) {
        if (img.getWidth() != 64 || img.getHeight() != 64) return null;
        return new SkinVoxels.Image() {
            @Override public int width() { return 64; }
            @Override public int height() { return 64; }
            @Override public boolean present(int x, int y) { return (img.getRGB(x, y) >>> 24) != 0; }
            @Override public boolean solid(int x, int y) { return (img.getRGB(x, y) >>> 24) == 255; }
        };
    }

    private static boolean usable(ResourceLocation skin) {
        return skin != null && !skin.equals(DefaultPlayerSkin.getDefaultSkinLegacy()) && !skin.getResourcePath().startsWith("textures/entity/alex")
            && !skin.getResourcePath().startsWith("textures/entity/steve");
    }

    private SkinVoxels.Mesh head(ResourceLocation skin) {
        if (!usable(skin)) return null;
        if (heads.containsKey(skin)) return heads.get(skin);
        SkinVoxels.Image img = image(skin);
        if (img == null) return null;
        SkinVoxels.Mesh m = SkinVoxels.head(img);
        heads.put(skin, m);
        return m;
    }

    private SkinVoxels.Mesh[] body(ResourceLocation skin, boolean slim) {
        if (!usable(skin)) return null;
        String key = skin + "|" + slim;
        if (bodies.containsKey(key)) return bodies.get(key);
        SkinVoxels.Image img = image(skin);
        if (img == null) return null;
        SkinVoxels.Mesh[] m = SkinVoxels.body(img, slim);
        bodies.put(key, m);
        return m;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRender(RenderLivingEvent.Pre<EntityLivingBase> event) {
        install();
        if (!isEnabled() || !(event.entity instanceof AbstractClientPlayer) || !((Object)event.renderer instanceof RenderPlayer)) return;
        AbstractClientPlayer player = (AbstractClientPlayer)event.entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            if (!showOthers.on() && !mc.thePlayer.getUniqueID().equals(player.getUniqueID())) return;
            if (player.getDistanceSq(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ) > distanceSq()) return;
        }
        ModelPlayer m = ((RenderPlayer)(Object)event.renderer).getMainModel();

        if (enableJacket.on()) m.bipedBodyWear.showModel = false;
        if (enableLeftSleeve.on()) m.bipedLeftArmwear.showModel = false;
        if (enableRightSleeve.on()) m.bipedRightArmwear.showModel = false;
        if (enableLeftPants.on()) m.bipedLeftLegwear.showModel = false;
        if (enableRightPants.on()) m.bipedRightLegwear.showModel = false;
    }

    private static void bindSkin(ResourceLocation skin) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableCull();
    }

    private static final class HatLayer implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;
        HatLayer(RenderPlayer render) { this.render = render; }

        @Override public void doRenderLayer(AbstractClientPlayer p, float a, float b, float pt, float age, float yaw, float pitch, float scale) {
            Module3dSkins m = instance;
            if (m == null || !m.shows(p)) return;
            ItemStack helmet = p.getCurrentArmor(3);
            if (helmet != null && helmet.getItem() instanceof ItemSkull) return;
            ModelPlayer model = render.getMainModel();
            if (!model.bipedHead.showModel || !p.isWearing(EnumPlayerModelParts.HAT)) return;
            SkinVoxels.Mesh mesh = m.head(p.getLocationSkin());
            if (mesh == null) return;
            GlStateManager.pushMatrix();
            if (p.isSneaking()) GlStateManager.translate(0.0f, 0.2f, 0.0f);
            float size = size(m.headVoxelSize, suit(p));
            mesh.copy(model.bipedHead);
            bindSkin(p.getLocationSkin());
            mesh.render(scale * size);
            GlStateManager.popMatrix();
        }

        @Override public boolean shouldCombineTextures() { return true; }
    }

    private static final class BodyLayer implements LayerRenderer<AbstractClientPlayer> {
        private final RenderPlayer render;
        BodyLayer(RenderPlayer render) { this.render = render; }

        @Override public void doRenderLayer(AbstractClientPlayer p, float a, float b, float pt, float age, float yaw, float pitch, float scale) {
            Module3dSkins m = instance;
            if (m == null || !m.shows(p)) return;
            ModelPlayer model = render.getMainModel();
            boolean slim = "slim".equals(p.getSkinType());
            SkinVoxels.Mesh[] meshes = m.body(p.getLocationSkin(), slim);
            if (meshes == null) return;
            GlStateManager.pushMatrix();
            if (p.isSneaking()) GlStateManager.translate(0.0f, 0.2f, 0.0f);
            bindSkin(p.getLocationSkin());
            boolean suit = suit(p);
            float depth = size(m.baseVoxelSize, suit), height = 1.035f, width = size(m.baseVoxelSize, suit);
            Object[][] parts = {
                {meshes[0], false, EnumPlayerModelParts.LEFT_PANTS_LEG, -0.2f, 0.0f, model.bipedLeftLeg, m.enableLeftPants},
                {meshes[1], false, EnumPlayerModelParts.RIGHT_PANTS_LEG, -0.2f, 0.0f, model.bipedRightLeg, m.enableRightPants},
                {meshes[2], false, EnumPlayerModelParts.LEFT_SLEEVE, 0.4f, slim ? 0.499f : 0.998f, model.bipedLeftArm, m.enableLeftSleeve},
                {meshes[3], true, EnumPlayerModelParts.RIGHT_SLEEVE, 0.4f, slim ? 0.499f : 0.998f, model.bipedRightArm, m.enableRightSleeve},
                {meshes[4], false, EnumPlayerModelParts.JACKET, 0.6f, 0.0f, model.bipedBody, m.enableJacket}};
            for (Object[] part : parts) {
                ModelRenderer vanilla = (ModelRenderer)part[5];
                if (!p.isWearing((EnumPlayerModelParts)part[2]) || !vanilla.showModel || !((BoolSetting)part[6]).on()) continue;
                SkinVoxels.Mesh mesh = (SkinVoxels.Mesh)part[0];
                mesh.copy(vanilla);
                float x = (Float)part[4];
                if (part[2] == EnumPlayerModelParts.JACKET) width = size(m.bodyVoxelWidthSize, suit);
                if ((Boolean)part[1]) x *= -1.0f;
                mesh.render(scale, x, (Float)part[3], width, height, depth);
            }
            GlStateManager.popMatrix();
        }

        @Override public boolean shouldCombineTextures() { return true; }
    }

    public static void beforeArm(RenderPlayer render, boolean left) {
        Module3dSkins m = instance;
        if (m == null || !m.isEnabled()) return;
        ModelPlayer model = render.getMainModel();
        if (left ? m.enableLeftSleeve.on() : m.enableRightSleeve.on()) {
            if (left) model.bipedLeftArmwear.showModel = false;
            else model.bipedRightArmwear.showModel = false;
        }
    }

    public static void afterArm(RenderPlayer render, AbstractClientPlayer player, boolean left) {
        Module3dSkins m = instance;
        if (m == null || !m.isEnabled()) return;
        ModelPlayer model = render.getMainModel();
        boolean flatShown = left ? model.bipedLeftArmwear.showModel : model.bipedRightArmwear.showModel;
        if (flatShown) return;
        boolean slim = "slim".equals(player.getSkinType());
        SkinVoxels.Mesh[] meshes = m.body(player.getLocationSkin(), slim);
        if (meshes == null) return;
        SkinVoxels.Mesh mesh = left ? meshes[2] : meshes[3];
        float size = size(m.firstPersonVoxelSize, suit(player));
        mesh.copy(left ? model.bipedLeftArm : model.bipedRightArm);
        if (!slim) mesh.x -= 0.4f;
        mesh.y += 0.4f;
        mesh.x -= 0.6f;
        float over = size - 1.0f;
        if (over > 0.0f) {
            mesh.y += 0.5f * over / 0.4f;
            mesh.x += 2.0f * over / 0.4f;
        }
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(size, 1.0f, size);
        bindSkin(player.getLocationSkin());
        mesh.render(0.0625f);
        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
        mesh.pose(0.0f, 0.0f, 0.0f);
        mesh.rotation(0.0f, 0.0f, 0.0f);
    }

    public static void skullBlock(TileEntitySkull skull) {
        Module3dSkins m = instance;
        Minecraft mc = Minecraft.getMinecraft();
        if (m == null || !m.isEnabled() || !m.enableSkulls.on() || mc.thePlayer == null) { skullActive = false; return; }
        double dx = skull.getPos().getX() - mc.thePlayer.posX, dy = skull.getPos().getY() - mc.thePlayer.posY, dz = skull.getPos().getZ() - mc.thePlayer.posZ;
        skullActive = dx * dx + dy * dy + dz * dz < m.distanceSq();
    }

    public static void skullItem() {
        Module3dSkins m = instance;
        skullActive = m != null && m.isEnabled() && m.enableSkullsItems.on();
    }

    public static void skullWorn(EntityLivingBase entity) {
        Module3dSkins m = instance;
        Minecraft mc = Minecraft.getMinecraft();
        if (m == null || !m.isEnabled() || !m.enableSkullsItems.on() || mc.thePlayer == null) { skullActive = false; return; }
        skullActive = entity.getDistanceSqToEntity(mc.thePlayer) < m.distanceSq();
    }

    private static ResourceLocation skin(GameProfile profile) {
        if (profile == null) return null;
        Minecraft mc = Minecraft.getMinecraft();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = mc.getSkinManager().loadSkinFromCache(profile);
        MinecraftProfileTexture t = map.get(MinecraftProfileTexture.Type.SKIN);
        return t == null ? null : mc.getSkinManager().loadSkin(t, MinecraftProfileTexture.Type.SKIN);
    }

    private static ModelHumanoidHead humanoid(TileEntitySkullRenderer renderer) {
        try {
            if (humanoidHead == null) {
                for (Field f : TileEntitySkullRenderer.class.getDeclaredFields()) {
                    if (f.getType() == ModelHumanoidHead.class) { f.setAccessible(true); humanoidHead = f; break; }
                }
            }
            return humanoidHead == null ? null : (ModelHumanoidHead)humanoidHead.get(renderer);
        } catch (Exception e) {
            return null;
        }
    }

    private static final Field HAT = Fields.find(ModelHumanoidHead.class, "head", "field_178717_b");

    public static void skullBefore(TileEntitySkullRenderer renderer, GameProfile profile) {
        ModelHumanoidHead head = humanoid(renderer);
        ModelRenderer hat = head == null ? null : (ModelRenderer)Fields.get(HAT, head);
        if (hat != null) hat.showModel = true;
        Module3dSkins m = instance;
        if (m == null || !m.isEnabled() || !skullActive || hat == null) return;
        SkinVoxels.Mesh mesh = m.head(skin(profile));
        if (mesh == null) return;
        hat.showModel = false;
        mesh.copy(head.skeletonHead);
    }

    public static void skullAfter(TileEntitySkullRenderer renderer, GameProfile profile) {
        if (!skullActive) return;
        skullActive = false;
        Module3dSkins m = instance;
        if (m == null) return;
        ResourceLocation skin = skin(profile);
        SkinVoxels.Mesh mesh = m.head(skin);
        ModelHumanoidHead head = humanoid(renderer);
        if (mesh == null || head == null) return;

        mesh.copy(head.skeletonHead);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.pushMatrix();
        bindSkin(skin);
        mesh.render(m.skullVoxelSize.value() / 16.0f);
        GlStateManager.popMatrix();
    }

    public static void renderSkullModel(ModelBase model, Entity entity, float a, float b, float c, float d, float e, float f,
                                        TileEntitySkullRenderer renderer, GameProfile profile) {
        skullBefore(renderer, profile);
        model.render(entity, a, b, c, d, e, f);
        skullAfter(renderer, profile);
    }
}
