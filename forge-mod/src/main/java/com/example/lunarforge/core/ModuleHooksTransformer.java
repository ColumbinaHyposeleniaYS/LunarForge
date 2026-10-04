package com.example.lunarforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ModuleHooksTransformer implements IClassTransformer {
    private static final Logger LOG = LogManager.getLogger("LunarForge");
    private static final String PVP_INFO = "com/example/lunarforge/module/modules/hud/ModulePvpInfo";
    private static final String LIGHTING = "com/example/lunarforge/module/modules/mechanic/ModuleLighting";
    private static final String SETTINGS = "net/minecraft/client/settings/GameSettings";
    private static final String ITEM_PHYSICS = "com/example/lunarforge/module/modules/visual/ModuleItemPhysics";
    private static final String ITEMS_2D = "com/example/lunarforge/module/modules/visual/ModuleItems2d";
    private static final String GL_STATE = "net/minecraft/client/renderer/GlStateManager";
    private static final String ENTITY_ITEM = "net/minecraft/entity/item/EntityItem";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        boolean arrow = "net.minecraft.entity.projectile.EntityArrow".equals(transformedName);
        boolean settings = "net.minecraft.client.settings.GameSettings".equals(transformedName);
        boolean item = "net.minecraft.client.renderer.entity.RenderEntityItem".equals(transformedName);
        if (!arrow && !settings && !item) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode m : node.methods) {
            if (arrow && is(m, "onUpdate", "func_70071_h_", "()V")) patched += arrowImpact(m);
            if (settings && is(m, "saveOptions", "func_74303_b", "()V")) patched += aroundSave(m);
            if (item && is(m, "func_177077_a", "func_177077_a", "(L" + ENTITY_ITEM + ";DDDFLnet/minecraft/client/resources/model/IBakedModel;)I")) patched += itemTransform(m);
            if (item && is(m, "doRender", "func_76986_a", "(L" + ENTITY_ITEM + ";DDDFF)V")) patched += itemRender(m);
            if (settings && is(m, "setOptionFloatValue", "func_74304_a", "(L" + SETTINGS + "$Options;F)V")) patched += afterSetOption(m);
        }
        LOG.info("Module hooks: {} hook(s) in {}", patched, transformedName);
        if (patched == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean is(MethodNode m, String mcp, String srg, String desc) {
        return (m.name.equals(mcp) || m.name.equals(srg)) && m.desc.equals(desc);
    }

    private static int arrowImpact(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (!(call.name.equals("attackEntityFrom") || call.name.equals("func_70097_a"))
                    || !call.desc.equals("(Lnet/minecraft/util/DamageSource;F)Z")) continue;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PVP_INFO, "arrowImpact", "(Lnet/minecraft/entity/projectile/EntityArrow;)V", false));
            m.instructions.insertBefore(call, hook);
            n++;
        }
        return n;
    }

    private static InsnList call(String owner, String hook, String desc, int... loads) {
        InsnList list = new InsnList();
        for (int i = 0; i < loads.length; i += 2) list.add(new VarInsnNode(loads[i], loads[i + 1]));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, hook, desc, false));
        return list;
    }

    private static int aroundSave(MethodNode m) {
        String desc = "(L" + SETTINGS + ";)V";
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) m.instructions.insertBefore(insn, call(LIGHTING, "afterSave", desc, Opcodes.ALOAD, 0));
        }
        m.instructions.insert(call(LIGHTING, "beforeSave", desc, Opcodes.ALOAD, 0));
        return 1;
    }

    private static int afterSetOption(MethodNode m) {
        String desc = "(L" + SETTINGS + ";L" + SETTINGS + "$Options;)V";
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.RETURN) continue;
            m.instructions.insertBefore(insn, call(LIGHTING, "afterSetOption", desc, Opcodes.ALOAD, 0, Opcodes.ALOAD, 1));
            n++;
        }
        return n;
    }

    private static int itemTransform(MethodNode m) {
        int n = 0;
        boolean translated = false;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (!call.owner.equals(GL_STATE)) continue;
            if (!translated && (call.name.equals("translate") || call.name.equals("func_179109_b")) && call.desc.equals("(FFF)V")) {
                translated = true;
                m.instructions.insertBefore(call, loads(Opcodes.ALOAD, 1, Opcodes.DLOAD, 4));
                redirect(call, ITEM_PHYSICS, "translate", "(FFFL" + ENTITY_ITEM + ";D)V");
                n++;
            } else if ((call.name.equals("rotate") || call.name.equals("func_179114_b")) && call.desc.equals("(FFFF)V")) {
                m.instructions.insertBefore(call, loads(Opcodes.ALOAD, 1, Opcodes.FLOAD, 8));
                redirect(call, ITEM_PHYSICS, "rotate", "(FFFFL" + ENTITY_ITEM + ";F)V");
                n++;
            }
        }
        return n;
    }

    private static int itemRender(MethodNode m) {
        InsnList head = loads(Opcodes.ALOAD, 1, Opcodes.DLOAD, 2, Opcodes.DLOAD, 4, Opcodes.DLOAD, 6, Opcodes.FLOAD, 9);
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ITEMS_2D, "render", "(L" + ENTITY_ITEM + ";DDDF)Z", false));
        LabelNode skip = new LabelNode();
        head.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(skip);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        int n = 1;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof LdcInsnNode && Float.valueOf(0.15f).equals(((LdcInsnNode)insn).cst)) {
                m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, ITEM_PHYSICS, "clumpSpread", "(F)F", false));
                n++;
            } else if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("net/minecraftforge/client/ForgeHooksClient")
                    && ((MethodInsnNode)insn).name.equals("handleCameraTransforms")) {
                ((MethodInsnNode)insn).owner = ITEM_PHYSICS;
                n++;
            }
        }
        return n;
    }

    private static InsnList loads(int... loads) {
        InsnList list = new InsnList();
        for (int i = 0; i < loads.length; i += 2) list.add(new VarInsnNode(loads[i], loads[i + 1]));
        return list;
    }

    private static void redirect(MethodInsnNode call, String owner, String name, String desc) {
        call.owner = owner;
        call.name = name;
        call.desc = desc;
    }
}
