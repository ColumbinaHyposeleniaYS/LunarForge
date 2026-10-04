package com.example.lunarforge.module.modules.server;

import com.example.lunarforge.module.HypixelLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.BreakingFour;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelRotation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.util.vector.Vector3f;

public final class BedwarsBeds {
    private BedwarsBeds() {}

    static final String[] COLORS = {"yellow", "cyan", "white", "pink", "gray", "red", "blue", "green"};
    private static final double SLICE = Math.toRadians(45.0);
    private static final Map<IBlockState, IBakedModel[]> MODELS = new HashMap<IBlockState, IBakedModel[]>();
    private static final Map<String, Integer> BY_NAME = new HashMap<String, Integer>();
    private static final FaceBakery BAKERY = new FaceBakery();

    static {
        String[] names = {"yellow", "cyan", "aqua", "white", "pink", "gray", "red", "blue", "green"};
        int[] index = {0, 1, 1, 2, 3, 4, 5, 6, 7};
        for (int i = 0; i < names.length; i++) BY_NAME.put(names[i], index[i]);
    }

    private static String texture(int color) { return "lunarforge:bedwars_coloured_beds/" + COLORS[color]; }

    public static final class Stitcher {
        @SubscribeEvent
        public void onStitch(TextureStitchEvent.Pre event) {
            for (int i = 0; i < COLORS.length; i++) event.map.registerSprite(new ResourceLocation(texture(i)));
        }
    }

    public static void clear() { MODELS.clear(); }

    public static TextureAtlasSprite texture(IBlockState state, TextureAtlasSprite vanilla) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return vanilla;
        IBakedModel model = model(mc.thePlayer.getPosition(), state, null);
        return model != null ? model.getParticleTexture() : vanilla;
    }

    public static IBakedModel model(BlockPos pos, IBlockState state, IBakedModel vanilla) {
        if (pos == null || state.getBlock() != Blocks.bed) return vanilla;
        ModuleHypixelBedwars m = ModuleHypixelBedwars.get();
        if (m == null) return vanilla;
        m.beds.used = true;
        if (!m.beds.active) return vanilla;
        int n = -1;
        if (m.practice()) n = java.util.Arrays.asList(COLORS).indexOf(m.practiceBedColor.get().id);
        if (n == -1) {
            double angle = Math.atan2(pos.getZ(), pos.getX()) + Math.PI * 4;
            n = m.beds.order[(int)(angle / SLICE) % 8];
        }
        IBakedModel[] built = MODELS.get(state);
        if (built == null) MODELS.put(state, built = new IBakedModel[8]);
        if (built[n] != null) return built[n];
        if (vanilla == null) return null;
        return built[n] = bake(state, vanilla, n);
    }

    private static IBakedModel bake(IBlockState state, IBakedModel vanilla, int color) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite planks = atlas.getAtlasSprite("minecraft:blocks/planks_oak");
        TextureAtlasSprite wool = atlas.getAtlasSprite(texture(color));
        List<BakedQuad> general = new ArrayList<BakedQuad>();
        List<List<BakedQuad>> faces = new ArrayList<List<BakedQuad>>();

        for (EnumFacing f : EnumFacing.values()) {
            List<BakedQuad> side = new ArrayList<BakedQuad>();
            for (BakedQuad q : (List<BakedQuad>)vanilla.getFaceQuads(f)) side.add(new BreakingFour(q, wool));
            faces.add(side);
        }
        EnumFacing facing = state.getValue(BlockBed.FACING);
        boolean foot = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT;
        ModelRotation rotation = ModelRotation.getModelRotation(0, facing.getHorizontalIndex() * 90 - 180);
        float u = 0.25f, v = 0.25f, o = foot ? 16 : 0;
        String woolName = texture(color), planksName = "minecraft:blocks/planks_oak";
        general.add(quad(0, 3, 0, 16, 3, 16, EnumFacing.DOWN, planksName, new float[]{0, 0, 16, 16}, 0, planks, rotation));
        if (!foot) general.add(quad(0, 0, 0, 16, 9, 0, EnumFacing.NORTH, woolName, new float[]{25 * u, 9 * v, 9 * u, 0}, 0, wool, rotation));
        else general.add(quad(0, 0, 16, 16, 9, 16, EnumFacing.SOUTH, woolName, new float[]{25 * u, 50 * v, 9 * u, 41 * v}, 180, wool, rotation));
        general.add(quad(0, 9, 0, 16, 9, 16, EnumFacing.UP, woolName, new float[]{9 * u, 9 * v + o * v, 25 * u, 25 * v + o * v}, 0, wool, rotation));
        general.add(quad(0, 0, 0, 0, 9, 16, EnumFacing.WEST, woolName, new float[]{0, 9 * v + o * v, 9 * u, 25 * v + o * v}, 270, wool, rotation));
        general.add(quad(16, 0, 0, 16, 9, 16, EnumFacing.EAST, woolName, new float[]{25 * u, 9 * v + o * v, 34 * u, 25 * v + o * v}, 90, wool, rotation));

        return new SimpleBakedModel(general, faces, true, false, wool, vanilla.getItemCameraTransforms());
    }

    private static BakedQuad quad(float x1, float y1, float z1, float x2, float y2, float z2, EnumFacing face, String texture, float[] uv, int uvRotation,
                                  TextureAtlasSprite sprite, ModelRotation rotation) {
        BlockPartFace part = new BlockPartFace(face, -1, texture, new BlockFaceUV(uv, uvRotation));
        return BAKERY.makeBakedQuad(new Vector3f(x1, y1, z1), new Vector3f(x2, y2, z2), part, sprite, face, rotation, null, false, true);
    }

    static final class Layout {
        private final ModuleHypixelBedwars module;
        private boolean active;
        private int[] order = {0, 1, 2, 3, 4, 5, 6, 7};

        private boolean used;

        Layout(ModuleHypixelBedwars module) { this.module = module; }

        boolean active() { return active; }
        int[] order() { return order; }
        boolean used() { return used; }

        void update() {
            if (!module.onHypixel() || !module.isEnabled() || !module.coloredBeds.on()) { active = false; return; }
            HypixelLocation.Location l = HypixelLocation.get();
            active = l != null && "BEDWARS".equals(l.gametype) && l.map != null;
        }

        private int[] indices(JsonArray array) {
            int[] out = new int[8];
            for (int i = 0; i < array.size() && i < 8; i++) {
                JsonPrimitive p = array.get(i).getAsJsonPrimitive();
                out[i] = p.isString() ? BY_NAME.getOrDefault(p.getAsString(), 5) : p.getAsInt();
            }
            return out;
        }

        private boolean apply(JsonObject root, JsonElement e) {
            if (e == null) return false;
            if (e.isJsonArray()) { order = indices(e.getAsJsonArray()); return true; }
            JsonElement named = root.get(e.getAsString());
            if (named != null && named.isJsonArray()) { order = indices(named.getAsJsonArray()); return true; }
            return false;
        }

        void read() {
            JsonObject root = module.bedLocations;
            if (root == null) return;
            HypixelLocation.Location l = HypixelLocation.get();
            if (l == null) return;
            if (l.map != null && apply(root, root.getAsJsonObject("map").get(l.map))) return;
            if (l.mode != null) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("modes").entrySet()) {
                    if (l.mode.contains(e.getKey()) && apply(root, e.getValue())) return;
                }
            }
            order = indices(root.getAsJsonArray("default"));
        }
    }
}
