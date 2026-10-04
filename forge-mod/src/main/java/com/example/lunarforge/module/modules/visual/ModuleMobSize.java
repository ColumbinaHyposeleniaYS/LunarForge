package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.cosmetics.render.Mannequin;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.Server;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.text.WordUtils;

public final class ModuleMobSize extends Module {
    private static ModuleMobSize instance;
    private static final Map<String, String> DISPLAY = new HashMap<String, String>();
    static {
        DISPLAY.put("Entity Horse", "Horse");
        DISPLAY.put("Ozelot", "Ocelot");
        DISPLAY.put("Lava Slime", "Magma Cube");
        DISPLAY.put("Wither Boss", "Wither");
        DISPLAY.put("Villager Golem", "Iron Golem");
        DISPLAY.put("Pig Zombie", "Zombie Pigman");
        DISPLAY.put("Creaking Transient", "Creaking");
        DISPLAY.put("Mushroom Cow", "Mooshroom");
    }

    private final NumberSetting playerSize = decimal("playerSize", 1.0f, 0.5f, 1.0f);
    private final NumberSetting otherPlayerSize = decimal("otherPlayerSize", 1.0f, 0.5f, 1.0f);
    private final BoolSetting changeNpcSize = bool("changeNpcSize", false);
    private final BoolSetting skyblockOnly = bool("playerSizeSkyblockOnly", false);
    private final Map<Class<?>, NumberSetting> mobSizes = new HashMap<Class<?>, NumberSetting>();
    private final List<NumberSetting> mobOptions = new ArrayList<NumberSetting>();

    private final Map<Entity, Float> lunarScale = new WeakHashMap<Entity, Float>();

    @SuppressWarnings("unchecked")
    public ModuleMobSize() {
        super("MOB_SIZE", false);
        instance = this;
        Map<String, Class<? extends Entity>> names;
        try {
            Field f = ReflectionHelper.findField(EntityList.class, "stringToClassMapping", "field_75625_b");
            names = (Map<String, Class<? extends Entity>>)f.get(null);
        } catch (Exception e) {
            names = Collections.emptyMap();
        }
        for (Map.Entry<String, Class<? extends Entity>> e : names.entrySet()) {
            Class<?> c = e.getValue();
            if (e.getKey().equals("Mob") || EntityPlayer.class.isAssignableFrom(c) || !EntityLivingBase.class.isAssignableFrom(c)) continue;
            NumberSetting size = decimal(label(e.getKey()), 1.0f, 0.5f, 1.0f);
            mobSizes.put(c, size);
            mobOptions.add(size);
        }
        Collections.sort(mobOptions, new Comparator<NumberSetting>() {
            @Override public int compare(NumberSetting a, NumberSetting b) { return a.key.compareTo(b.key); }
        });
    }

    private static String label(String name) {
        String spaced = Character.isUpperCase(name.charAt(0)) ? name.replaceAll("(.)([A-Z])", "$1 $2")
            : WordUtils.capitalizeFully(name.replace('_', ' '));
        String display = DISPLAY.get(spaced);
        return (display == null ? spaced : display) + " Size";
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(skyblockOnly));
        page.section("playerOptions", s -> s.add(playerSize, otherPlayerSize, changeNpcSize));
        page.section("mobOptions", s -> s.add(mobOptions.toArray(new NumberSetting[0])));
    }

    private static boolean npc(EntityPlayer p, boolean hypixel) {
        if (p == Minecraft.getMinecraft().thePlayer) return false;
        Team team = p.getTeam();
        String teamName = team == null ? "" : team.getRegisteredName();
        if (teamName.contains("npc") || teamName.startsWith("CIT-")) return true;
        String name = p.getName();
        if (p.getUniqueID().version() == 4 || hypixel && p.getUniqueID().version() == 1) {
            return name.contains(" ") || name.trim().isEmpty() || name.contains("§");
        }
        return true;
    }

    private float scaleOf(EntityLivingBase entity) {
        if (skyblockOnly.on() && !Server.skyblock()) return 1.0f;
        if (entity instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer)entity;
            if (p instanceof Mannequin) return 1.0f;
            if (!changeNpcSize.on() && npc(p, Server.hypixel())) return 1.0f;
            return p == Minecraft.getMinecraft().thePlayer ? playerSize.value() : otherPlayerSize.value();
        }
        NumberSetting size = mobSizes.get(entity.getClass());
        return size == null ? 1.0f : size.value();
    }

    public static void scale(EntityLivingBase entity) {
        ModuleMobSize m = instance;
        if (m == null) return;
        float s = m.isEnabled() ? m.scaleOf(entity) : 1.0f;
        m.lunarScale.put(entity, s);
        if (s != 1.0f) GlStateManager.scale(s, s, s);
    }

    public static double nameTagY(EntityLivingBase entity, double y) {
        ModuleMobSize m = instance;
        if (m == null || !m.isEnabled() || m.skyblockOnly.on() && !Server.skyblock()) return y;
        Float s = m.lunarScale.get(entity);
        return y - entity.height * (1.0f - (s == null ? 1.0f : s));
    }
}
