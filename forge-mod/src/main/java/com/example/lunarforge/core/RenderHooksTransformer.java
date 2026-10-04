package com.example.lunarforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class RenderHooksTransformer implements IClassTransformer {
    static final String RENDER = "com/example/lunarforge/module/render/RenderHooks";
    static final String VISUAL = "com/example/lunarforge/module/modules/visual/";
    static final String ITEMS = "com/example/lunarforge/module/render/ItemRenderHooks";
    static final String RENDER_PKG = "com/example/lunarforge/module/render/";
    static final String HUD = "com/example/lunarforge/module/modules/hud/";
    static final String NAMETAGS = "com/example/lunarforge/module/render/NametagHooks";
    static final String MECHANIC_PKG = "com/example/lunarforge/module/modules/mechanic/";
    static final String WEATHER = MECHANIC_PKG + "ModuleWeatherChanger";
    static final String SKINS = "com/example/lunarforge/module/modules/visual/Module3dSkins";
    static final String BEDS = "com/example/lunarforge/module/modules/server/BedwarsBeds";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        return patch(transformedName, bytes);
    }

    static byte[] patch(String transformedName, byte[] bytes) {
        boolean renderer = "net.minecraft.client.renderer.EntityRenderer".equals(transformedName);
        boolean screen = "net.minecraft.client.gui.GuiScreen".equals(transformedName);
        boolean living = "net.minecraft.client.renderer.entity.RendererLivingEntity".equals(transformedName);
        boolean armor = "net.minecraft.client.renderer.entity.layers.LayerArmorBase".equals(transformedName);
        boolean item = "net.minecraft.client.renderer.entity.RenderItem".equals(transformedName);
        boolean manager = "net.minecraft.client.renderer.entity.RenderManager".equals(transformedName);
        boolean global = "net.minecraft.client.renderer.RenderGlobal".equals(transformedName);
        boolean particle = "net.minecraft.client.particle.EntityFX".equals(transformedName);
        boolean effects = "net.minecraft.client.particle.EffectRenderer".equals(transformedName);
        boolean tooltip = "net.minecraftforge.fml.client.config.GuiUtils".equals(transformedName);
        boolean ingame = "net.minecraftforge.client.GuiIngameForge".equals(transformedName);
        boolean render = "net.minecraft.client.renderer.entity.Render".equals(transformedName);
        boolean worldClass = "net.minecraft.world.World".equals(transformedName);
        boolean player = "net.minecraft.client.renderer.entity.RenderPlayer".equals(transformedName);
        boolean skull = "net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer".equals(transformedName);
        boolean customHead = "net.minecraft.client.renderer.entity.layers.LayerCustomHead".equals(transformedName);
        boolean itemRenderer = "net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer".equals(transformedName);
        boolean dispatcher = "net.minecraft.client.renderer.BlockRendererDispatcher".equals(transformedName);
        boolean shapes = "net.minecraft.client.renderer.BlockModelShapes".equals(transformedName);
        if (!worldClass && !player && !skull && !customHead && !itemRenderer && !dispatcher && !shapes && !render && !renderer && !screen && !living && !armor && !item && !manager && !global && !particle && !effects && !tooltip && !ingame) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        if (particle) count += particleTag(node);
        String world = null;
        if (effects) for (org.objectweb.asm.tree.FieldNode f : node.fields) if (f.desc.equals("Lnet/minecraft/world/World;")) world = f.name;
        for (MethodNode m : node.methods) {
            if (renderer && named(m, "updateCameraAndRender", "func_181560_a") && m.desc.equals("(FJ)V")) count += postProcess(m);
            if (screen && named(m, "drawWorldBackground", "func_146270_b") && m.desc.equals("(I)V")) count += worldBackground(m);
            if (living && named(m, "setBrightness", "func_177092_a") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;FZ)Z")) count += hitColor(m);
            if (living && named(m, "doRender", "func_76986_a") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && named((MethodInsnNode)insn, "preRenderCallback", "func_77041_b")) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        hook.add(invoke(VISUAL + "ModuleMobSize", "scale", "(Lnet/minecraft/entity/EntityLivingBase;)V"));
                        m.instructions.insertBefore(insn, hook);
                        count++;
                    }
                }
            }
            if (living && named(m, "renderName", "func_177067_a") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;DDD)V")) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.DLOAD, 4));
                hook.add(invoke(NAMETAGS, "y", "(Lnet/minecraft/entity/EntityLivingBase;D)D"));
                hook.add(new VarInsnNode(Opcodes.DSTORE, 4));
                m.instructions.insert(hook);
                count++;
            }
            if (worldClass && named(m, "getRainStrength", "func_72867_j") && m.desc.equals("(F)F")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() != Opcodes.FRETURN) continue;
                    InsnList wrap = new InsnList();
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    wrap.add(invoke(WEATHER, "worldRain", "(FLnet/minecraft/world/World;)F"));
                    m.instructions.insertBefore(insn, wrap);
                    count++;
                }
            }
            if (renderer && named(m, "addRainParticles", "func_78484_h") && m.desc.equals("()V")) {
                InsnList cond = new InsnList();
                cond.add(invoke(WEATHER, "clear", "()Z"));
                count += returnIf(m, cond);
            }
            if (renderer && named(m, "renderRainSnow", "func_78474_d") && m.desc.equals("(F)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if (named(call, "getRainStrength", "func_72867_j") && call.desc.equals("(F)F")) {
                        m.instructions.insert(call, invoke(WEATHER, "renderStrength", "(F)F"));
                        count++;
                    } else if (named(call, "canSpawnLightningBolt", "func_76738_d") && call.desc.equals("()Z")) {
                        m.instructions.insert(call, invoke(WEATHER, "canRain", "(Z)Z"));
                        count++;
                    } else if (named(call, "getTemperatureAtHeight", "func_76939_a") && call.desc.equals("(FI)F")) {
                        m.instructions.insert(call, invoke(WEATHER, "temperature", "(F)F"));
                        count++;
                    } else if (call.owner.equals("net/minecraft/client/renderer/WorldRenderer") && named(call, "color", "func_181666_a")
                            && call.desc.equals("(FFFF)Lnet/minecraft/client/renderer/WorldRenderer;")) {
                        m.instructions.set(call, invoke(WEATHER, "color", "(Lnet/minecraft/client/renderer/WorldRenderer;FFFF)Lnet/minecraft/client/renderer/WorldRenderer;"));
                        count++;
                    }
                }
                InsnList cond = new InsnList();
                cond.add(invoke(WEATHER, "clear", "()Z"));
                count += returnIf(m, cond);
            }
            if (global && named(m, "renderSky", "func_174976_a") && m.desc.equals("(FI)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if (named(call, "getDimensionId", "func_177502_q") && call.desc.equals("()I")) {
                        m.instructions.insert(call, invoke(MECHANIC_PKG + "ModuleTimeChanger", "skyDimension", "(I)I"));
                        count++;
                    } else if (named(call, "isSurfaceWorld", "func_76569_d") && call.desc.equals("()Z")) {
                        m.instructions.insert(call, invoke(MECHANIC_PKG + "ModuleTimeChanger", "skySurface", "(Z)Z"));
                        count++;
                    }
                }
            }
            if (player && (named(m, "renderRightArm", "func_177138_b") || named(m, "renderLeftArm", "func_177139_c"))
                    && m.desc.equals("(Lnet/minecraft/client/entity/AbstractClientPlayer;)V")) {
                boolean left = named(m, "renderLeftArm", "func_177139_c");
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && named((MethodInsnNode)insn, "setModelVisibilities", "func_177137_d")) {
                        InsnList after = new InsnList();
                        after.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        after.add(new InsnNode(left ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                        after.add(invoke(SKINS, "beforeArm", "(Lnet/minecraft/client/renderer/entity/RenderPlayer;Z)V"));
                        m.instructions.insert(insn, after);
                        count++;
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList tail = new InsnList();
                        tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        tail.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        tail.add(new InsnNode(left ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                        tail.add(invoke(SKINS, "afterArm", "(Lnet/minecraft/client/renderer/entity/RenderPlayer;Lnet/minecraft/client/entity/AbstractClientPlayer;Z)V"));
                        m.instructions.insertBefore(insn, tail);
                        count++;
                    }
                }
            }
            if (skull && named(m, "renderTileEntityAt", "func_180535_a") && m.desc.equals("(Lnet/minecraft/tileentity/TileEntitySkull;DDDFI)V")) {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(Opcodes.ALOAD, 1));
                head.add(invoke(SKINS, "skullBlock", "(Lnet/minecraft/tileentity/TileEntitySkull;)V"));
                m.instructions.insert(head);
                count++;
            }
            if (skull && named(m, "renderSkull", "func_180543_a") && m.desc.equals("(FFFLnet/minecraft/util/EnumFacing;FILcom/mojang/authlib/GameProfile;I)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode) || !named((MethodInsnNode)insn, "render", "func_78088_a")
                            || !((MethodInsnNode)insn).desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")) continue;
                    InsnList wrap = new InsnList();
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 7));
                    wrap.add(invoke(SKINS, "renderSkullModel", "(Lnet/minecraft/client/model/ModelBase;Lnet/minecraft/entity/Entity;FFFFFFLnet/minecraft/client/renderer/tileentity/TileEntitySkullRenderer;Lcom/mojang/authlib/GameProfile;)V"));
                    m.instructions.insertBefore(insn, wrap);
                    m.instructions.remove(insn);
                    count++;
                }
            }
            if ((customHead && named(m, "doRenderLayer", "func_177141_a")) || (itemRenderer && named(m, "renderByItem", "func_179022_a"))) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (!(insn instanceof MethodInsnNode) || !named((MethodInsnNode)insn, "renderSkull", "func_180543_a")) continue;
                    InsnList before = new InsnList();
                    if (customHead) {
                        before.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        before.add(invoke(SKINS, "skullWorn", "(Lnet/minecraft/entity/EntityLivingBase;)V"));
                    } else {
                        before.add(invoke(SKINS, "skullItem", "()V"));
                    }
                    m.instructions.insertBefore(insn, before);
                    count++;
                }
            }
            if (dispatcher && named(m, "getModelFromBlockState", "func_175022_a")
                    && m.desc.equals("(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/BlockPos;)Lnet/minecraft/client/resources/model/IBakedModel;")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() != Opcodes.ARETURN) continue;
                    InsnList wrap = new InsnList();
                    wrap.add(new VarInsnNode(Opcodes.ASTORE, m.maxLocals));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, m.maxLocals));
                    wrap.add(invoke(BEDS, "model", "(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/client/resources/model/IBakedModel;)Lnet/minecraft/client/resources/model/IBakedModel;"));
                    m.instructions.insertBefore(insn, wrap);
                    count++;
                }
                m.maxLocals++;
            }
            if (shapes && named(m, "getTexture", "func_178122_a")
                    && m.desc.equals("(Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() != Opcodes.ARETURN) continue;
                    InsnList wrap = new InsnList();
                    wrap.add(new VarInsnNode(Opcodes.ASTORE, m.maxLocals));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    wrap.add(new VarInsnNode(Opcodes.ALOAD, m.maxLocals));
                    wrap.add(invoke(BEDS, "texture", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"));
                    m.instructions.insertBefore(insn, wrap);
                    count++;
                }
                m.maxLocals++;
            }
            if (shapes && named(m, "reloadModels", "func_178124_c") && m.desc.equals("()V")) {
                m.instructions.insert(invoke(BEDS, "clear", "()V"));
                count++;
            }
            if (ingame && m.name.equals("renderHealth") && m.desc.equals("(II)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && named((MethodInsnNode)insn, "isHardcoreModeEnabled", "func_76093_s")) {
                        m.instructions.insert(insn, invoke("com/example/lunarforge/module/modules/server/ModuleHypixelBedwars", "hardcore", "(Z)Z"));
                        count++;
                    }
                }
            }
            if (effects && world != null) count += effectRenderer(m, world);
            if (ingame && m.name.equals("renderTitle") && m.desc.equals("(IIF)V")) {
                InsnList cond = new InsnList();
                cond.add(invoke(HUD + "ModuleTitles", "replacesVanilla", "()Z"));
                count += returnIf(m, cond);
            }
            if (ingame && m.name.equals("renderRecordOverlay") && m.desc.equals("(IIF)V")) {
                InsnList cond = new InsnList();
                cond.add(invoke(HUD + "ModuleActionBar", "replacesVanilla", "()Z"));
                count += returnIf(m, cond);
            }
            if (tooltip && m.name.equals("drawHoveringText") && m.desc.equals("(Ljava/util/List;IIIIILnet/minecraft/client/gui/FontRenderer;)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && named((MethodInsnNode)insn, "drawStringWithShadow", "func_175063_a")
                            && ((MethodInsnNode)insn).desc.equals("(Ljava/lang/String;FFI)I")) {
                        m.instructions.set(insn, invoke(RENDER_PKG + "TooltipHooks", "drawLine", "(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;FFI)I"));
                        count++;
                    }
                }
                InsnList head = new InsnList();
                for (int i = 0; i < 7; i++) head.add(new VarInsnNode(i == 0 || i == 6 ? Opcodes.ALOAD : Opcodes.ILOAD, i));
                head.add(invoke(RENDER_PKG + "TooltipHooks", "begin", "(Ljava/util/List;IIIIILnet/minecraft/client/gui/FontRenderer;)V"));
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) m.instructions.insertBefore(insn, invoke(RENDER_PKG + "TooltipHooks", "end", "()V"));
                }
                m.instructions.insert(head);
                count++;
            }
            if (global && named(m, "drawSelectionBoundingBox", "func_181561_a") && m.desc.equals("(Lnet/minecraft/util/AxisAlignedBB;)V")) {
                InsnList cond = new InsnList();
                cond.add(new VarInsnNode(Opcodes.ALOAD, 0));
                cond.add(invoke(VISUAL + "ModuleBlockOutline", "draw", "(Lnet/minecraft/util/AxisAlignedBB;)Z"));
                count += returnIf(m, cond);
            }
            if (manager && named(m, "cacheActiveRenderInfo", "func_180597_a")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (!(insn instanceof org.objectweb.asm.tree.FieldInsnNode) || insn.getOpcode() != Opcodes.GETFIELD) continue;
                    org.objectweb.asm.tree.FieldInsnNode f = (org.objectweb.asm.tree.FieldInsnNode)insn;
                    if (!f.desc.equals("F")) continue;
                    String hook = f.name.equals("rotationYaw") || f.name.equals("field_70177_z") ? "yaw"
                        : f.name.equals("prevRotationYaw") || f.name.equals("field_70126_B") ? "previousYaw"
                        : f.name.equals("rotationPitch") || f.name.equals("field_70125_A") ? "pitch"
                        : f.name.equals("prevRotationPitch") || f.name.equals("field_70127_C") ? "previousPitch" : null;
                    if (hook == null) continue;
                    m.instructions.set(insn, invoke(MECHANIC_PKG + "ModuleFreelook", hook, "(Lnet/minecraft/entity/Entity;)F"));
                    count++;
                }
            }
            if (manager && named(m, "renderDebugBoundingBox", "func_85094_b") && m.desc.equals("(Lnet/minecraft/entity/Entity;DDDFF)V")) {
                InsnList cond = new InsnList();
                cond.add(new VarInsnNode(Opcodes.ALOAD, 1));
                cond.add(new VarInsnNode(Opcodes.DLOAD, 2));
                cond.add(new VarInsnNode(Opcodes.DLOAD, 4));
                cond.add(new VarInsnNode(Opcodes.DLOAD, 6));
                cond.add(new VarInsnNode(Opcodes.FLOAD, 8));
                cond.add(new VarInsnNode(Opcodes.FLOAD, 9));
                cond.add(invoke(VISUAL + "ModuleHitbox", "render", "(Lnet/minecraft/entity/Entity;DDDFF)Z"));
                count += returnIf(m, cond);
            }
            if (armor && named(m, "shouldCombineTextures", "func_177142_b") && m.desc.equals("()Z")) {
                m.instructions.clear(); m.tryCatchBlocks.clear(); if (m.localVariables != null) m.localVariables.clear();
                m.instructions.add(invoke(VISUAL + "ModuleHitColor", "combineArmor", "()Z"));
                m.instructions.add(new InsnNode(Opcodes.IRETURN));
                count++;
            }
            if (armor && m.name.equals("func_177183_a") && m.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V")) {
                InsnList cond = new InsnList();
                cond.add(new VarInsnNode(Opcodes.ALOAD, 1));
                cond.add(new VarInsnNode(Opcodes.ALOAD, 2));
                for (int i = 3; i <= 9; i++) cond.add(new VarInsnNode(Opcodes.FLOAD, i));
                cond.add(invoke(ITEMS, "armorGlint", "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)Z"));
                count += returnIf(m, cond);
            }
            if (item && named(m, "renderItem", "func_180454_a") && m.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)V")) {
                InsnList cond = new InsnList();
                cond.add(new VarInsnNode(Opcodes.ALOAD, 0));
                cond.add(new VarInsnNode(Opcodes.ALOAD, 1));
                cond.add(new VarInsnNode(Opcodes.ALOAD, 2));
                cond.add(invoke(ITEMS, "renderItem", "(Lnet/minecraft/client/renderer/entity/RenderItem;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/resources/model/IBakedModel;)Z"));
                count += returnIf(m, cond);
            }
            if (item && named(m, "renderItemIntoGUI", "func_175042_a") && m.desc.equals("(Lnet/minecraft/item/ItemStack;II)V")) {
                count += around(m, invoke(ITEMS, "guiStart", "()V"), ITEMS, "guiEnd");
            }
        }
        if (count == 0) return bytes;
        LogManager.getLogger("LunarForge").info("Render hooks: {} hook(s) in {}", count, transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static boolean named(MethodNode m, String mcp, String srg) { return m.name.equals(mcp) || m.name.equals(srg); }

    static boolean named(MethodInsnNode m, String mcp, String srg) { return m.name.equals(mcp) || m.name.equals(srg); }

    static MethodInsnNode invoke(String owner, String method, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, owner, method, desc, false);
    }

    private static int postProcess(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && "gui".equals(((LdcInsnNode)insn).cst)) {
                AbstractInsnNode at = insn;
                while (at.getPrevious() != null && !(at.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)at).var == 0)) at = at.getPrevious();
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.FLOAD, 1));
                hook.add(invoke(RENDER, "postProcess", "(F)V"));
                m.instructions.insertBefore(at, hook);
                return 1;
            }
        }
        return 0;
    }

    static final String PARTICLES = VISUAL + "ModuleParticleChanger";
    static final String FX = "net/minecraft/client/particle/EntityFX";
    static final String EFFECTS = "net/minecraft/client/particle/EffectRenderer";

    private static int particleTag(ClassNode node) {
        String tag = "com/example/lunarforge/module/render/ParticleTag";
        if (node.interfaces.contains(tag)) return 0;
        node.interfaces.add(tag);
        node.fields.add(new org.objectweb.asm.tree.FieldNode(Opcodes.ACC_PRIVATE, "lunarforge$type", "I", null, null));

        for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>")) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.RETURN) continue;
                InsnList set = new InsnList();
                set.add(new VarInsnNode(Opcodes.ALOAD, 0));
                set.add(new InsnNode(Opcodes.ICONST_M1));
                set.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, node.name, "lunarforge$type", "I"));
                m.instructions.insertBefore(insn, set);
            }
        }
        String red = field(node, "particleRed", "field_70552_h"), green = field(node, "particleGreen", "field_70553_i");
        String blue = field(node, "particleBlue", "field_70551_j"), alpha = field(node, "particleAlpha", "field_82339_as");
        String scale = field(node, "particleScale", "field_70544_f");
        getter(node, "lunarforge$type", "lunarforge$type", "I", Opcodes.IRETURN);
        setter(node, "lunarforge$setType", new String[]{"lunarforge$type"}, "I");
        getter(node, "lunarforge$red", red, "F", Opcodes.FRETURN);
        getter(node, "lunarforge$green", green, "F", Opcodes.FRETURN);
        getter(node, "lunarforge$blue", blue, "F", Opcodes.FRETURN);
        getter(node, "lunarforge$alpha", alpha, "F", Opcodes.FRETURN);
        getter(node, "lunarforge$scale", scale, "F", Opcodes.FRETURN);
        setter(node, "lunarforge$setScale", new String[]{scale}, "F");
        setter(node, "lunarforge$setColor", new String[]{red, green, blue, alpha}, "F");
        return 1;
    }

    private static String field(ClassNode node, String mcp, String srg) {
        for (org.objectweb.asm.tree.FieldNode f : node.fields) if (f.name.equals(mcp)) return mcp;
        return srg;
    }

    private static void getter(ClassNode node, String name, String field, String desc, int ret) {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, "()" + desc, null, null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, node.name, field, desc));
        m.instructions.add(new InsnNode(ret));
        node.methods.add(m);
    }

    private static void setter(ClassNode node, String name, String[] fields, String desc) {
        StringBuilder args = new StringBuilder("(");
        for (int i = 0; i < fields.length; i++) args.append(desc);
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, args + ")V", null, null);
        int load = desc.equals("F") ? Opcodes.FLOAD : Opcodes.ILOAD;
        for (int i = 0; i < fields.length; i++) {
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            m.instructions.add(new VarInsnNode(load, i + 1));
            m.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, node.name, fields[i], desc));
        }
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(m);
    }

    private static int effectRenderer(MethodNode m, String world) {
        int n = 0;
        boolean spawn = named(m, "spawnEffectParticle", "func_178927_a") && m.desc.equals("(IDDDDDD[I)L" + FX + ";");
        boolean render = (named(m, "renderParticles", "func_78874_a") || named(m, "renderLitParticles", "func_78872_b"))
            && m.desc.equals("(Lnet/minecraft/entity/Entity;F)V");
        boolean dig = named(m, "addBlockDestroyEffects", "func_180533_a") && m.desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V")
            || named(m, "addBlockHitEffects", "func_180532_a") && m.desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)V");
        if (!spawn && !render && !dig) return 0;
        int factory = -1;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (spawn && insn.getOpcode() == Opcodes.CHECKCAST
                    && ((org.objectweb.asm.tree.TypeInsnNode)insn).desc.equals("net/minecraft/client/particle/IParticleFactory")
                    && insn.getNext() instanceof VarInsnNode && insn.getNext().getOpcode() == Opcodes.ASTORE) {
                factory = ((VarInsnNode)insn.getNext()).var;
            }
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            boolean addEffect = call.owner.equals(EFFECTS) && named(call, "addEffect", "func_78873_a") && call.desc.equals("(L" + FX + ";)V");
            if (spawn && addEffect && factory >= 0) {
                InsnList args = new InsnList();
                args.add(new VarInsnNode(Opcodes.ILOAD, 1));
                for (int i = 0; i < 6; i++) args.add(new VarInsnNode(Opcodes.DLOAD, 2 + i * 2));
                args.add(new VarInsnNode(Opcodes.ALOAD, 14));
                args.add(new VarInsnNode(Opcodes.ALOAD, factory));
                args.add(new VarInsnNode(Opcodes.ALOAD, 0));
                args.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD, EFFECTS, world, "Lnet/minecraft/world/World;"));
                m.instructions.insertBefore(call, args);
                m.instructions.set(call, invoke(PARTICLES, "spawn",
                    "(L" + EFFECTS + ";L" + FX + ";IDDDDDD[ILnet/minecraft/client/particle/IParticleFactory;Lnet/minecraft/world/World;)V"));
                n++;
            } else if (dig && addEffect) {
                m.instructions.set(call, invoke(PARTICLES, "addDigging", "(L" + EFFECTS + ";L" + FX + ";)V"));
                n++;
            } else if (render && call.owner.equals(FX) && named(call, "renderParticle", "func_180434_a")
                    && call.desc.equals("(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V")) {
                m.instructions.set(call, invoke(PARTICLES, "render",
                    "(L" + FX + ";Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/entity/Entity;FFFFFF)V"));
                n++;
            }
        }
        if (dig) {
            InsnList cond = new InsnList();
            cond.add(invoke(PARTICLES, "hideDigging", "()Z"));
            n += returnIf(m, cond);
        }
        return n;
    }

    static int returnIf(MethodNode m, InsnList condition) {
        LabelNode go = new LabelNode();
        condition.add(new JumpInsnNode(Opcodes.IFEQ, go));
        condition.add(new InsnNode(Opcodes.RETURN));
        condition.add(go);
        condition.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(condition);
        return 1;
    }

    static int around(MethodNode m, MethodInsnNode head, String owner, String tail) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) m.instructions.insertBefore(insn, invoke(owner, tail, "()V"));
        }
        m.instructions.insert(head);
        return 1;
    }

    private static int hitColor(MethodNode m) {
        String[] hooks = {"red", "green", "blue", "alpha"};
        int puts = 0, n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (puts < 4 && call.owner.equals("java/nio/FloatBuffer") && call.name.equals("put") && call.desc.equals("(F)Ljava/nio/FloatBuffer;")) {
                m.instructions.insertBefore(call, invoke(VISUAL + "ModuleHitColor", hooks[puts++], "(F)F"));
                n++;
            } else if (call.getOpcode() == Opcodes.INVOKESTATIC && call.name.equals("setEntityColor") && call.desc.equals("(FFFF)V")
                    && call.owner.endsWith("/Shaders")) {
                call.owner = VISUAL + "ModuleHitColor";
                call.name = "shadersEntityColor";
                n++;
            }
        }
        return n;
    }

    private static int worldBackground(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof LdcInsnNode) || !(((LdcInsnNode)insn).cst instanceof Integer)) continue;
            int value = (Integer)((LdcInsnNode)insn).cst;
            String hook = value == 0xC0101010 ? "gradientTop" : value == 0xD0101010 ? "gradientBottom" : null;
            if (hook == null) continue;
            InsnList list = new InsnList();
            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
            list.add(new InsnNode(Opcodes.SWAP));
            list.add(invoke(VISUAL + "ModuleMenuBlur", hook, "(Lnet/minecraft/client/gui/GuiScreen;I)I"));
            m.instructions.insert(insn, list);
            n++;
        }
        return n;
    }
}
