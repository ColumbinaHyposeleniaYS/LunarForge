package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.ModuleManager;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.render.ParticleTag;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.ChoiceSetting;
import com.example.lunarforge.module.setting.ColorSetting;
import com.example.lunarforge.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFirework;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.text.WordUtils;

public final class ModuleParticleChanger extends Module {
    private static ModuleParticleChanger instance;

    enum Kind {
        AMBIENT_ENTITY_EFFECT(16, "ambient_entity_effect"), ANGRY_VILLAGER(20, "angry_villager"), BARRIER(35, "barrier"),
        BLOCK(38, "block"), BUBBLE(4, "bubble"), CLOUD(29, "cloud"), CRIT(9, "critical"), DRIPPING_LAVA(19, "dripping_lava"),
        DRIPPING_WATER(18, "dripping_water"), FALLING_WATER(39, "falling_water"), DUST(30, "dust"), EFFECT(13, "potion_effect"),
        ENCHANTED_HIT(10, "sharpness"), ENCHANT(25, "enchant"), ENTITY_EFFECT(15, "entity_effect"), EXPLOSION(0, "explosion"),
        FIREWORK(3, "firework_trail"), FISHING(6, "fishing"), FLAME(26, "flame"), HAPPY_VILLAGER(21, "happy_villager"),
        HEART(34, "heart"), INSTANT_EFFECT(14, "instant_effect"), ITEM(36, "item"), ITEM_SLIME(33, "item_slime"),
        ITEM_SNOWBALL(31, "item_snowball"), LARGE_SMOKE(12, "large_smoke"), LAVA(27, "lava"), MYCELIUM(22, "mycelium"),
        NOTE(23, "note"), POOF(32, "poof"), PORTAL(24, "portal"), SMOKE(11, "smoke"), UNDERWATER(8, "underwater"),
        SPLASH(5, "splash"), WITCH(17, "witch"), FOOTSTEP(28, "footstep");

        final int legacyId;

        final String alias;
        Kind(int legacyId, String alias) { this.legacyId = legacyId; this.alias = alias; }

        String displayName() { return WordUtils.capitalizeFully(alias).replaceAll("_", " "); }

        boolean disallowed() {
            Minecraft mc = Minecraft.getMinecraft();
            return (this == BLOCK || this == BUBBLE) && mc != null && mc.getCurrentServerData() != null;
        }
    }

    public enum ColorMode implements ChoiceSetting.Option {
        OVERLAY("overlay"), RECOLOR("recolor");
        final String id;
        ColorMode(String id) { this.id = id; }
        @Override public String langId() { return id; }
    }

    class Style extends Module {
        final Kind kind;
        final NumberSetting scale = decimal("scale", 1.0f, 0.25f, 2.0f);
        final NumberSetting particleMultiplier = decimal("particleMultiplier", 1.0f, 0.25f, 10.0f);
        final BoolSetting hideParticle = bool("hideParticle", false);
        final BoolSetting overlayColor = bool("overlayColor", false);
        final ColorSetting color = color("color", -1);
        final ChoiceSetting<ColorMode> colorMode = choice("colorMode", ColorMode.OVERLAY);

        Style(String id, Kind kind) {
            super(id, false);
            this.kind = kind;
            if (kind != null) LunarLang.registerName(id, kind::displayName);
        }

        @Override protected void layout(Page page) {
            page.add(scale, particleMultiplier).hideIf(this::disallowed);
            page.add(hideParticle);
            page.group(overlayColor, c -> c.add(color, colorMode)).hideIf(this::disallowed);
        }

        boolean disallowed() { return kind != null && kind.disallowed(); }

        private long seen = Long.MIN_VALUE;
        private boolean cEnabled, cHidden, cOverlay, cChroma, cRecolor;
        private int cColor;
        private float cScale;

        private void refresh() {
            long revision = ModuleManager.model() == null ? 0 : ModuleManager.model().revision;
            if (revision == seen) return;
            seen = revision;
            cEnabled = isEnabled();
            cHidden = cEnabled && hideParticle.on();
            cOverlay = overlayColor.on();
            cChroma = color.chroma();
            cColor = color.color(0.0f);
            cRecolor = colorMode.get() == ColorMode.RECOLOR;
            cScale = scale.value();
        }

        boolean active() { refresh(); return cEnabled && !disallowed(); }

        boolean hidden() { refresh(); return cHidden; }

        int colour() { return cChroma ? color.color(0.0f) : cColor; }

        int count(Random random) {
            if (!active()) return 1;
            float f = particleMultiplier.value();
            int n = (int)f;
            if (random.nextFloat() <= f - n) ++n;
            return n;
        }

        private float channel(float value, int component) {
            if (!cOverlay) return value;
            float c = component / 255.0f;
            return cRecolor ? c : value * c;
        }
    }

    final class Blood extends Style {
        final BoolSetting playerBloodParticles = bool("playerBloodParticles", true);
        final BoolSetting entityBloodParticles = bool("entityBloodParticles", true);
        final BoolSetting selfBloodParticles = bool("selfBloodParticles", true);
        final BoolSetting playBloodSound = bool("playBloodSound", false);
        private Entity lastTarget;
        private Vec3 lastHit;

        Blood() {
            super("PARTICLE_CHANGER_BLOOD_CHILD", null);
            LunarLang.registerName(id, () -> LunarLang.get("features.PARTICLE_CHANGER.info", "bloodDisplayName"));
        }

        @Override protected void layout(Page page) {
            page.add(playerBloodParticles, entityBloodParticles, selfBloodParticles, playBloodSound);
            super.layout(page);
        }

        @SubscribeEvent
        public void onAttack(AttackEntityEvent event) {
            if (!isEnabled() || event.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
            lastTarget = event.target;
            lastHit = Minecraft.getMinecraft().objectMouseOver == null ? null : Minecraft.getMinecraft().objectMouseOver.hitVec;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null && !hidden()) {
                for (Entity e : mc.theWorld.loadedEntityList) {
                    if (e == mc.thePlayer && (!selfBloodParticles.on() || hideFirstPersonParticles())) continue;
                    if (e.isInvisible() || !e.isEntityAlive() || !justHurt(e)) continue;
                    if (e instanceof EntityPlayer && !playerBloodParticles.on()) continue;
                    if (e instanceof EntityLivingBase && !(e instanceof EntityPlayer) && !entityBloodParticles.on()) continue;
                    double x, y, z;
                    if (lastTarget == e && lastHit != null) {
                        x = lastHit.xCoord; y = lastHit.yCoord; z = lastHit.zCoord;
                    } else {
                        x = e.posX; y = e.posY + e.height / 2.0; z = e.posZ;
                    }
                    spawnBlood(e.worldObj, x, y, z);
                }
            }
            lastTarget = null;
            lastHit = null;
        }

        private boolean justHurt(Entity e) {
            if (!(e instanceof EntityLivingBase)) return false;
            EntityLivingBase l = (EntityLivingBase)e;
            return l.hurtTime > 0 && l.hurtTime == l.maxHurtTime;
        }

        private void spawnBlood(World world, double x, double y, double z) {
            Block block = Blocks.redstone_block;
            Minecraft mc = Minecraft.getMinecraft();
            if (isEnabled() && playBloodSound.on()) {
                Block.SoundType sound = block.stepSound;
                mc.getSoundHandler().playSound(new PositionedSoundRecord(new ResourceLocation(sound.getBreakSound()),
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getFrequency() * 0.8F, (float)x, (float)y, (float)z));
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 27; i++) {
                int n = count(random);
                for (int j = 0; j < n; j++) {
                    double vx = random.nextFloat() * 2.0F - 1.0F, vy = random.nextFloat() * 2.0F - 1.0F, vz = random.nextFloat() * 2.0F - 1.0F;
                    EntityDiggingFX fx = (EntityDiggingFX)new EntityDiggingFX.Factory().getEntityFX(0, world, x, y, z, vx, vy, vz, Block.getStateId(block.getDefaultState()));
                    fx.setBlockPos(new BlockPos(x, y, z));
                    mc.effectRenderer.addEffect(fx);
                }
            }
        }
    }

    private final BoolSetting alwaysEnchantStrikes = bool("alwaysEnchantStrikes", false);
    private final BoolSetting hideFirstPersonParticles = bool("hideFirstPersonParticles", false);
    private final BoolSetting hideBlockBreakParticles = bool("hideBlockBreakParticles", false);
    private final BoolSetting hideAllParticles = bool("hideAllParticles", false);
    private final Blood blood = child(new Blood(), null);
    private final Map<Integer, Style> byLegacyId = new HashMap<Integer, Style>();
    private final List<Style> styles = new ArrayList<Style>();

    public ModuleParticleChanger() {
        super("PARTICLE_CHANGER", false);
        instance = this;
        childRowsLast = true;
        for (Kind k : Kind.values()) {
            Style s = child(new Style("PARTICLE_CHANGER_" + k.alias.toUpperCase() + "_CHILD", k), null);
            styles.add(s);
            byLegacyId.put(k.legacyId, s);
        }
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(alwaysEnchantStrikes, hideFirstPersonParticles, hideBlockBreakParticles, hideAllParticles));
    }

    boolean hideFirstPersonParticles() { return isEnabled() && hideFirstPersonParticles.on(); }

    private long seenAll = Long.MIN_VALUE;
    private boolean hideAll;

    private boolean hideAll() {
        long revision = ModuleManager.model() == null ? 0 : ModuleManager.model().revision;
        if (revision != seenAll) { seenAll = revision; hideAll = isEnabled() && hideAllParticles.on(); }
        return hideAll;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || !alwaysEnchantStrikes.on() || event.entityPlayer != Minecraft.getMinecraft().thePlayer) return;
        Entity target = event.target;
        if (!(target instanceof EntityLivingBase) || target.isInvisible() || !target.isEntityAlive()) return;
        Style style = byLegacyId.get(Kind.ENCHANTED_HIT.legacyId);
        float f = style.count(ThreadLocalRandom.current());
        for (int n = 0; n < f; ++n) Minecraft.getMinecraft().effectRenderer.emitParticleAtEntity(target, EnumParticleTypes.CRIT_MAGIC);
    }

    private static Style style(int legacyId) {
        ModuleParticleChanger m = instance;
        return m == null ? null : m.byLegacyId.get(legacyId);
    }

    private static boolean firework(EntityFX fx) {
        return fx instanceof EntityFirework.StarterFX || fx instanceof EntityFirework.OverlayFX || fx instanceof EntityFirework.SparkFX;
    }

    public static void spawn(EffectRenderer renderer, EntityFX fx, int id, double x, double y, double z, double vx, double vy, double vz,
                             int[] params, IParticleFactory factory, World world) {
        ((ParticleTag)fx).lunarforge$setType(id);
        Style style = style(id);
        if (style != null && style.active()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int count = style.count(random);
            if (count == 0) return;
            if (count > 1) {
                for (int i = 0; i < count; i++) {
                    EntityFX copy = factory.getEntityFX(id, world, x + jitter(random), y + jitter(random), z + jitter(random),
                        vx * spread(random), vy * spread(random), vz * spread(random), params);
                    if (copy != null) {
                        ((ParticleTag)copy).lunarforge$setType(id);
                        renderer.addEffect(copy);
                    }
                }
            }
        }
        renderer.addEffect(fx);
    }

    private static double jitter(Random r) { return -0.2f + r.nextFloat() * 0.4f; }

    private static double spread(Random r) { return 0.6f + r.nextFloat() * 0.7f; }

    public static boolean hideDigging() {
        ModuleParticleChanger m = instance;
        return m != null && m.isEnabled() && m.hideBlockBreakParticles.on();
    }

    public static void addDigging(EffectRenderer renderer, EntityFX fx) {
        ((ParticleTag)fx).lunarforge$setType(38);
        renderer.addEffect(fx);
    }

    public static void render(EntityFX fx, net.minecraft.client.renderer.WorldRenderer wr, Entity entity, float partialTicks,
                              float a, float b, float c, float d, float e) {
        ModuleParticleChanger m = instance;
        ParticleTag tag = (ParticleTag)fx;
        Style style = m == null ? null : m.byLegacyId.get(tag.lunarforge$type());
        if (m != null && m.hideAll() || style != null && style.hidden()) return;
        if (style == null || !style.active() || firework(fx)) {
            fx.renderParticle(wr, entity, partialTicks, a, b, c, d, e);
            return;
        }
        float red = tag.lunarforge$red(), green = tag.lunarforge$green(), blue = tag.lunarforge$blue(), alpha = tag.lunarforge$alpha();
        float scale = tag.lunarforge$scale();
        int col = style.colour();
        tag.lunarforge$setColor(style.channel(red, col >> 16 & 255), style.channel(green, col >> 8 & 255), style.channel(blue, col & 255),
            style.cOverlay ? (col >>> 24) / 255.0f : alpha);
        tag.lunarforge$setScale(scale * style.cScale);
        try {
            fx.renderParticle(wr, entity, partialTicks, a, b, c, d, e);
        } finally {
            tag.lunarforge$setColor(red, green, blue, alpha);
            tag.lunarforge$setScale(scale);
        }
    }
}
