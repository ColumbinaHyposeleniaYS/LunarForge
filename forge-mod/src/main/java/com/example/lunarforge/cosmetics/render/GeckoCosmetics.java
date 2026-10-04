package com.example.lunarforge.cosmetics.render;

import com.example.lunarforge.cosmetics.Cosmetic;
import com.example.lunarforge.cosmetics.CosmeticTextures;
import com.example.lunarforge.cosmetics.CosmeticType;
import com.example.lunarforge.cosmetics.LunarCdn;
import com.example.lunarforge.cosmetics.gecko.GeoAnimation;
import com.example.lunarforge.cosmetics.gecko.GeoModel;
import com.example.lunarforge.cosmetics.gecko.Molang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

public final class GeckoCosmetics {
    private static final ExecutorService LOAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LunarForge gecko load"); t.setDaemon(true); return t;
    });
    private static final Map<String, Future<Definition>> DEFINITIONS = new HashMap<String, Future<Definition>>();
    private static final Map<EntityPlayer, Map<Integer, State>> STATES = new WeakHashMap<EntityPlayer, Map<Integer, State>>();
    private static Molang.Expr constants;
    private static boolean molangLoaded;

    private GeckoCosmetics() {}

    static final class Definition {
        final List<GeoModel> models = new ArrayList<GeoModel>();
        final List<String> modelConditions = new ArrayList<String>();
        Map<String, GeoAnimation> animations;
        String texture, type, attached = "NONE";
        final List<ObjCosmetics.Op> ops = new ArrayList<ObjCosmetics.Op>();

        boolean coversHead, coversBody, coversRightArm, coversLeftArm, coversRightLeg, coversLeftLeg;
        boolean offsetWithChestplate, hideHead, hideBody, hideRightArm, hideLeftArm, hideRightLeg, hideLeftLeg;

        final List<List<String[]>> controllers = new ArrayList<List<String[]>>();
    }

    static final class State {
        final Molang.Scope scope = new Molang.Scope();
        final String[] playing; final double[] started;
        State(int controllers) { playing = new String[controllers]; started = new double[controllers]; }
    }

    public static boolean has(Cosmetic c) { return c.geckolib && LunarCdn.has(c.resource); }

    private static String strip(String path) { return path.startsWith("lunar:") ? path.substring(6) : path; }

    static Definition definition(final Cosmetic c) {
        Future<Definition> f;
        synchronized (DEFINITIONS) {
            f = DEFINITIONS.get(c.resource);
            if (f == null) {
                f = LOAD.submit(() -> load(c.resource));
                DEFINITIONS.put(c.resource, f);
            }
        }
        if (!f.isDone()) return null;
        try { return f.get(); } catch (Exception e) { return null; }
    }

    private static Definition load(String path) {
        try {
            JsonObject gek = json(LunarCdn.fetch(path).get());
            if (gek == null) return null;
            Definition d = new Definition();
            d.type = gek.has("type") ? gek.get("type").getAsString() : "";
            d.texture = strip(gek.get("texture").getAsString());
            if (gek.has("attached_bone")) d.attached = gek.get("attached_bone").getAsString();
            Future<byte[]> anim = gek.has("animation") ? LunarCdn.fetch(strip(gek.get("animation").getAsString())) : null;
            JsonElement model = gek.get("model");
            if (model.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : model.getAsJsonObject().entrySet()) {
                    d.models.add(GeoModel.parse(json(LunarCdn.fetch(strip(e.getKey())).get())));
                    d.modelConditions.add(e.getValue().getAsString());
                }
            } else {
                d.models.add(GeoModel.parse(json(LunarCdn.fetch(strip(model.getAsString())).get())));
                d.modelConditions.add("1");
            }
            JsonObject anims = anim == null ? null : json(anim.get());
            d.animations = anims == null ? Collections.<String, GeoAnimation>emptyMap() : GeoAnimation.parse(anims);
            if (gek.has("transformations") && gek.get("transformations").isJsonArray())
                for (JsonElement t : gek.getAsJsonArray("transformations"))
                    if (t.isJsonObject() && t.getAsJsonObject().has("transformType")) ObjCosmetics.parse(d.ops, t.getAsJsonObject());
            d.offsetWithChestplate = flag(gek, "offset_with_chestplate");
            d.hideHead = flag(gek, "hide_head"); d.hideBody = flag(gek, "hide_body");
            d.hideRightArm = flag(gek, "hide_right_arm"); d.hideLeftArm = flag(gek, "hide_left_arm");
            d.hideRightLeg = flag(gek, "hide_right_leg"); d.hideLeftLeg = flag(gek, "hide_left_leg");
            stateMachine(d, gek);
            if ("NONE".equals(d.attached)) for (GeoModel m : d.models) {
                d.coversHead |= m.hasCubes("armorHead"); d.coversBody |= m.hasCubes("armorBody");
                d.coversRightArm |= m.hasCubes("armorRightArm"); d.coversLeftArm |= m.hasCubes("armorLeftArm");
                d.coversRightLeg |= m.hasCubes("armorRightLeg") || m.hasCubes("armorRightBoot");
                d.coversLeftLeg |= m.hasCubes("armorLeftLeg") || m.hasCubes("armorLeftBoot");
            }
            return d;
        } catch (Exception e) {
            LogManager.getLogger("LunarForge/Cosmetics").warn("Could not load {}: {}", path, e.toString());
            return null;
        }
    }

    private static boolean flag(JsonObject o, String key) { return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean(); }

    private static JsonObject json(byte[] data) {
        if (data == null) return null;
        String s = new String(data, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == 0xFEFF) s = s.substring(1);
        return new JsonParser().parse(s).getAsJsonObject();
    }

    private static void stateMachine(Definition d, JsonObject gek) {
        JsonObject sm = gek.has("state_machine") && gek.get("state_machine").isJsonObject() ? gek.getAsJsonObject("state_machine") : null;
        if (sm != null && sm.has("controllers")) {
            for (JsonElement c : sm.getAsJsonArray("controllers")) {
                List<String[]> states = new ArrayList<String[]>();
                JsonArray arr = c.getAsJsonObject().getAsJsonArray("states");
                if (arr != null) for (JsonElement s : arr) {
                    JsonObject o = s.getAsJsonObject();
                    if (!o.has("anim")) continue;
                    states.add(new String[]{o.get("anim").getAsString(), o.has("plays_when") ? o.get("plays_when").getAsString() : "1"});
                }
                d.controllers.add(states);
            }
        } else if (sm != null && sm.has("anim")) {
            d.controllers.add(Collections.singletonList(new String[]{sm.get("anim").getAsString(), "1"}));
        } else if (gek.has("default_anim") && gek.get("default_anim").isJsonObject()) {
            List<String[]> states = new ArrayList<String[]>();
            for (Map.Entry<String, JsonElement> e : gek.getAsJsonObject("default_anim").entrySet())
                states.add(new String[]{e.getKey(), e.getValue().getAsString()});
            d.controllers.add(states);
        }
    }

    private static synchronized void loadMolang() {
        if (molangLoaded) return;
        molangLoaded = true;
        Molang.loadFunctions(resource("functions.molang"));
        constants = Molang.compile(resource("constants.molang"));
    }

    private static String resource(String name) {
        try (InputStream in = GeckoCosmetics.class.getResourceAsStream("/assets/lunarforge/cosmetics/" + name)) {
            if (in == null) return "";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private static double lifeTime(EntityPlayer p, float partialTicks) {
        return p instanceof Mannequin ? System.currentTimeMillis() % 3_600_000L / 1000.0 : (p.ticksExisted + partialTicks) / 20.0;
    }

    private static Molang.Queries queries(final EntityPlayer p, final float pt) {
        return name -> {
            boolean preview = p instanceof Mannequin;
            double dx = (p.posX - p.prevPosX) * 20, dy = (p.posY - p.prevPosY) * 20, dz = (p.posZ - p.prevPosZ) * 20;
            if (preview) dx = dy = dz = 0;
            float yaw = p.renderYawOffset * (float)Math.PI / 180f;
            switch (name) {
                case "life_time": return lifeTime(p, pt);
                case "is_in_gui": case "is_in_preview_model": return preview ? 1 : 0;
                case "is_crouching": case "is_sneaking": return p.isSneaking() ? 1 : 0;
                case "is_sprinting": case "is_running": return p.isSprinting() ? 1 : 0;
                case "is_on_ground": return p.onGround || preview ? 1 : 0;
                case "is_in_water": return p.isInWater() ? 1 : 0;
                case "is_flying": return p.capabilities.isFlying ? 1 : 0;
                case "ground_speed": return Math.sqrt(dx * dx + dz * dz);
                case "is_moving": return dx * dx + dz * dz > 1e-4 ? 1 : 0;
                case "x_velocity": return dx;
                case "y_velocity": case "local_y_velocity": return dy;
                case "z_velocity": return dz;
                case "local_z_velocity": return -dx * MathHelper.sin(yaw) + dz * MathHelper.cos(yaw);
                case "local_x_velocity": return dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw);
                case "head_y_rotation": return preview ? 0 : MathHelper.wrapAngleTo180_float(p.rotationYawHead - p.renderYawOffset);
                case "head_x_rotation": return preview ? 0 : p.rotationPitch;
                case "body_y_rotation": return preview ? 0 : p.renderYawOffset;
                case "entity_y_rotation": return preview ? 0 : p.rotationYaw;
                case "is_first_person": return 0;
                case "is_slim_model": return "slim".equals(((net.minecraft.client.entity.AbstractClientPlayer)p).getSkinType()) ? 1 : 0;
                default: return 0;
            }
        };
    }

    private static GeoModel pose(EntityPlayer p, Definition d, Cosmetic c, float partialTicks) {
        loadMolang();
        Map<Integer, State> states = STATES.get(p);
        if (states == null) { states = new HashMap<Integer, State>(); STATES.put(p, states); }
        State st = states.get(c.id);
        if (st == null) { st = new State(d.controllers.size()); states.put(c.id, st); }
        st.scope.queries = queries(p, partialTicks);
        double now = lifeTime(p, partialTicks);
        try { constants.eval(st.scope); } catch (RuntimeException ignored) { }

        GeoModel geo = d.models.get(0);
        for (int i = 0; i < d.models.size(); i++)
            if (Molang.compile(d.modelConditions.get(i)).eval(st.scope) != 0) { geo = d.models.get(i); break; }
        geo.resetPose();
        for (int i = 0; i < d.controllers.size(); i++) {
            String pick = null;
            for (String[] s : d.controllers.get(i)) {
                double v;
                try { v = Molang.compile(s[1]).eval(st.scope); } catch (RuntimeException e) { v = 0; }
                if (v != 0) { pick = s[0]; break; }
            }
            if (pick == null) continue;
            if (!pick.equals(st.playing[i])) { st.playing[i] = pick; st.started[i] = now; }
            GeoAnimation anim = d.animations.get(pick);
            if (anim == null) continue;
            try { anim.apply(geo, st.scope, now - st.started[i]); } catch (RuntimeException ignored) { }
        }
        return geo;
    }

    private static void draw(GeoModel geo, ResourceLocation texture) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.alphaFunc(516, .003f);
        GlStateManager.enableRescaleNormal();
        geo.render();
        GlStateManager.alphaFunc(516, .1f);
        GlStateManager.disableBlend();
    }

    public static boolean preview(EntityPlayer viewer, Cosmetic c, float x, float y, float w, float h) {
        Definition d = definition(c);
        if (d == null || d.models.isEmpty()) return false;
        ResourceLocation texture = CosmeticTextures.get(d.texture, false);
        if (texture == null) return false;
        GeoModel geo = pose(viewer, d, c, 0);
        float[] b = geo.bounds();
        float size = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        if (size <= 0) return false;
        float s = Math.min(w, h) * .62f / size;
        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        GlStateManager.enableColorMaterial();
        GlStateManager.translate(x + w / 2, y + h / 2, 150);

        GlStateManager.scale(s, -s, s);
        GlStateManager.rotate(-15, 1, 0, 0);
        GlStateManager.rotate(215, 0, 1, 0);
        GlStateManager.translate(-(b[0] + b[3]) / 2, -(b[1] + b[4]) / 2, -(b[2] + b[5]) / 2);
        net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
        draw(geo, texture);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        return true;
    }

    static void render(EntityPlayer p, ModelBiped model, Cosmetic c, float partialTicks) {
        boolean preview = p instanceof Mannequin;
        if (c.type == CosmeticType.COMPANION && !preview) return;
        Definition d = definition(c);
        if (d == null || d.models.isEmpty()) return;
        ResourceLocation texture = CosmeticTextures.get(d.texture, false);
        if (texture == null) return;
        GeoModel geo = pose(p, d, c, partialTicks);

        GlStateManager.pushMatrix();
        if ("NONE".equals(d.attached)) syncArmorBones(geo, model);
        if (p.isSneaking()) GlStateManager.translate(0, .2f, 0);
        ModelRenderer part = part(model, d.attached);
        if (part != null) part.postRender(.0625f);
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.translate(0, -1.51f, 0);
        if (d.offsetWithChestplate && p.getCurrentArmor(2) != null) GlStateManager.translate(0, .065f, 0);
        ObjCosmetics.apply(d.ops, p);

        draw(geo, texture);
        GlStateManager.popMatrix();
    }

    private static ModelRenderer part(ModelBiped m, String attached) {
        switch (attached) {
            case "HEAD": return m.bipedHead;
            case "SHOULDER": return m.bipedBody;
            case "LEFT_ARM": return m.bipedLeftArm;
            case "RIGHT_ARM": return m.bipedRightArm;
            case "LEFT_LEG": return m.bipedLeftLeg;
            case "RIGHT_LEG": return m.bipedRightLeg;
            default: return null;
        }
    }

    private static void syncArmorBones(GeoModel geo, ModelBiped m) {
        follow(geo.bones.get("armorHead"), m.bipedHead, 0, 0);
        follow(geo.bones.get("armorBody"), m.bipedBody, 0, 0);
        follow(geo.bones.get("armorRightArm"), m.bipedRightArm, 5, 2);
        follow(geo.bones.get("armorLeftArm"), m.bipedLeftArm, -5, 2);
        follow(geo.bones.get("armorRightLeg"), m.bipedRightLeg, 2, 12);
        follow(geo.bones.get("armorRightBoot"), m.bipedRightLeg, 2, 12);
        follow(geo.bones.get("armorLeftLeg"), m.bipedLeftLeg, -2, 12);
        follow(geo.bones.get("armorLeftBoot"), m.bipedLeftLeg, -2, 12);
    }

    private static void follow(GeoModel.Bone bone, ModelRenderer part, float dx, float baseY) {
        if (bone == null) return;
        bone.rotX = -part.rotateAngleX;
        bone.rotY = -part.rotateAngleY;
        bone.rotZ = part.rotateAngleZ;
        bone.posX = part.rotationPointX + dx;
        bone.posY = baseY - part.rotationPointY;
        bone.posZ = part.rotationPointZ;
    }

    public static void hideParts(EntityPlayer p, ModelPlayer m, List<Cosmetic> worn) {
        boolean head = false, body = false, ra = false, la = false, rl = false, ll = false;
        for (Cosmetic c : worn) {
            if (!c.geckolib) continue;
            Definition d = definition(c);
            if (d == null) continue;
            head |= d.hideHead; body |= d.hideBody; ra |= d.hideRightArm; la |= d.hideLeftArm; rl |= d.hideRightLeg; ll |= d.hideLeftLeg;

            if (d.coversHead) m.bipedHeadwear.showModel = false;
            if (d.coversBody) m.bipedBodyWear.showModel = false;
            if (d.coversRightArm) m.bipedRightArmwear.showModel = false;
            if (d.coversLeftArm) m.bipedLeftArmwear.showModel = false;
            if (d.coversRightLeg) m.bipedRightLegwear.showModel = false;
            if (d.coversLeftLeg) m.bipedLeftLegwear.showModel = false;
        }
        if (head) { m.bipedHead.showModel = false; m.bipedHeadwear.showModel = false; }
        if (body) { m.bipedBody.showModel = false; m.bipedBodyWear.showModel = false; }
        if (ra) { m.bipedRightArm.showModel = false; m.bipedRightArmwear.showModel = false; }
        if (la) { m.bipedLeftArm.showModel = false; m.bipedLeftArmwear.showModel = false; }
        if (rl) { m.bipedRightLeg.showModel = false; m.bipedRightLegwear.showModel = false; }
        if (ll) { m.bipedLeftLeg.showModel = false; m.bipedLeftLegwear.showModel = false; }
    }
}
