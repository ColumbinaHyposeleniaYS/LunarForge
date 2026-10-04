package com.example.lunarforge.cosmetics.render;

import com.example.lunarforge.cosmetics.Cosmetic;
import com.example.lunarforge.cosmetics.LunarCdn;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.apache.logging.log4j.LogManager;

public final class ObjCosmetics {
    private static final Pattern PATH = Pattern.compile("cosmetics/models/(hats|bodywear)/([^/]+)/");
    private static final String[] CONDITIONS = {"world", "gui", "player", "first_person", "third_person", "helmet", "chestplate", "leggings", "boots", "skin_layer", "none"};
    private static Map<String, Entry> index;
    private static final Map<String, Object> MODELS = new HashMap<String, Object>();

    private ObjCosmetics() {}

    static final class Entry {
        String name, folder;
        boolean head, showWithArmor;
        final List<Op> ops = new ArrayList<Op>();
    }

    static final class Op {
        final char kind; final String condition; final float angle, x, y, z;
        Op(char kind, String condition, float angle, float x, float y, float z) { this.kind = kind; this.condition = condition; this.angle = angle; this.x = x; this.y = y; this.z = z; }
    }

    private static synchronized Map<String, Entry> index() {
        if (index != null) return index;
        index = new HashMap<String, Entry>();
        try (Reader in = new InputStreamReader(ObjCosmetics.class.getResourceAsStream("/assets/lunarforge/cosmetics/models.json"), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> e : new JsonParser().parse(in).getAsJsonObject().entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Entry entry = new Entry();
                entry.name = e.getKey();
                entry.folder = o.get("folder").getAsString();
                entry.head = o.get("part").getAsString().equals("HEAD");
                entry.showWithArmor = o.get("armor").getAsBoolean();
                for (JsonElement t : o.getAsJsonArray("t")) parse(entry.ops, t.getAsJsonObject());
                index.put(entry.name, entry);
            }
        } catch (Exception ex) {
            LogManager.getLogger("LunarForge/Cosmetics").error("Could not read the model index", ex);
        }
        return index;
    }

    static void parse(List<Op> ops, JsonObject t) {
        char kind = t.get("transformType").getAsString().charAt(0);
        JsonObject values = t.getAsJsonObject("values");
        boolean any = false;
        for (String c : CONDITIONS) {
            if (!values.has(c)) continue;
            any = true;
            ops.add(op(kind, c, values.getAsJsonObject(c)));
        }
        if (!any && values.has("x")) ops.add(op(kind, "none", values));
    }

    private static Op op(char kind, String condition, JsonObject v) {
        return new Op(kind, condition, v.has("angle") ? v.get("angle").getAsFloat() : 0,
            v.get("x").getAsFloat(), v.get("y").getAsFloat(), v.get("z").getAsFloat());
    }

    static Entry entry(Cosmetic c) {
        if (c.geckolib) return null;
        Matcher m = PATH.matcher(c.resource);
        return m.find() ? index().get(m.group(2)) : null;
    }

    public static boolean has(Cosmetic c) { return entry(c) != null; }

    @SuppressWarnings("unchecked")
    private static ObjModel model(Entry e) {
        String path = "cosmetics/models/" + e.folder + "/" + e.name + "/" + e.name + ".obj";
        synchronized (MODELS) {
            Object o = MODELS.get(path);
            if (o instanceof ObjModel) return (ObjModel)o;
            if (o == Boolean.FALSE) return null;
            if (o == null) {
                if (!LunarCdn.has(path)) { MODELS.put(path, Boolean.FALSE); return null; }
                MODELS.put(path, LunarCdn.fetch(path));
                return null;
            }
            Future<byte[]> f = (Future<byte[]>)o;
            if (!f.isDone()) return null;
            try {
                ObjModel m = ObjModel.parse(f.get());
                MODELS.put(path, m);
                return m;
            } catch (Exception ex) {
                LogManager.getLogger("LunarForge/Cosmetics").warn("Could not load {}: {}", path, ex.toString());
                MODELS.put(path, Boolean.FALSE);
                return null;
            }
        }
    }

    static boolean applies(String condition, EntityPlayer p) {
        switch (condition) {
            case "player": case "third_person": case "none": return true;
            case "helmet": return p.getCurrentArmor(3) != null;
            case "chestplate": return p.getCurrentArmor(2) != null;
            case "leggings": return p.getCurrentArmor(1) != null;
            case "boots": return p.getCurrentArmor(0) != null;
            default: return false;
        }
    }

    static void apply(List<Op> ops, EntityPlayer p) {
        for (Op op : ops) {
            if (!applies(op.condition, p)) continue;
            switch (op.kind) {
                case 't': GlStateManager.translate(op.x, op.y, op.z); break;
                case 's': GlStateManager.scale(op.x, op.y, op.z); break;
                case 'r': GlStateManager.rotate(op.angle, op.x, op.y, op.z); break;
                default: break;
            }
        }
    }

    static void render(EntityPlayer p, ModelBiped model, Cosmetic c) {
        Entry e = entry(c);
        if (e == null) return;
        if (!e.showWithArmor && p.getCurrentArmor(e.head ? 3 : 2) != null) return;
        ObjModel mesh = model(e);
        if (mesh == null) return;
        net.minecraft.util.ResourceLocation texture = com.example.lunarforge.cosmetics.CosmeticTextures.get(c);
        if (texture == null) return;
        GlStateManager.pushMatrix();
        if (p.isSneaking()) GlStateManager.translate(0, .2f, 0);
        if (e.head) model.bipedHead.postRender(.0625f); else model.bipedBody.postRender(.0625f);
        if (c.type.render == com.example.lunarforge.cosmetics.CosmeticType.Render.HAT) GlStateManager.rotate(90, 0, 1, 0);
        apply(e.ops, p);
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableRescaleNormal();
        GlStateManager.disableCull();
        mesh.render();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
