package com.example.lunarforge.core;

import java.util.Arrays;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class OneSevenTransformer implements IClassTransformer {
    private static final Logger LOG = LogManager.getLogger("LunarForge");
    private static final String HOOKS = "com/example/lunarforge/module/modules/visual/OneSevenHooks";
    private static final String MC = "net/minecraft/";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        if ("net.minecraft.client.renderer.ItemRenderer".equals(transformedName)) return patch(bytes, transformedName, Target.ITEM_RENDERER);
        if ("net.minecraft.client.renderer.EntityRenderer".equals(transformedName)) return patch(bytes, transformedName, Target.ENTITY_RENDERER);
        if ("net.minecraft.client.renderer.entity.RendererLivingEntity".equals(transformedName)) return patch(bytes, transformedName, Target.LIVING_RENDERER);
        if ("net.minecraft.client.renderer.entity.layers.LayerHeldItem".equals(transformedName)) return patch(bytes, transformedName, Target.HELD_ITEM);
        if ("net.minecraftforge.client.GuiIngameForge".equals(transformedName)) return patch(bytes, transformedName, Target.GUI_INGAME);
        if ("net.minecraft.client.Minecraft".equals(transformedName)) return patch(bytes, transformedName, Target.MINECRAFT);
        if ("net.minecraft.client.renderer.entity.RenderItem".equals(transformedName)) return patch(bytes, transformedName, Target.RENDER_ITEM);
        return bytes;
    }

    private enum Target { ITEM_RENDERER, ENTITY_RENDERER, LIVING_RENDERER, HELD_ITEM, GUI_INGAME, MINECRAFT, RENDER_ITEM }

    private static boolean is(String name, String... names) { return Arrays.asList(names).contains(name); }

    private static byte[] patch(byte[] bytes, String className, Target target) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode m : node.methods) {
            switch (target) {
                case ITEM_RENDERER:
                    if (is(m.name, "renderItemInFirstPerson", "func_78440_a") && m.desc.equals("(F)V")) patched += firstPerson(m);
                    break;
                case ENTITY_RENDERER:
                    if (is(m.name, "hurtCameraEffect", "func_78482_e") && m.desc.equals("(F)V"))
                        patched += replaceCalls(m, MC + "util/MathHelper", new String[] {"sin", "func_76126_a"}, "(F)F", "hurtSin", "(F)F");
                    else if (is(m.name, "orientCamera", "func_78467_g") && m.desc.equals("(F)V")) patched += eyeTranslate(m);
                    break;
                case LIVING_RENDERER:
                    if (is(m.name, "getSwingProgress", "func_77040_d") && m.desc.equals("(L" + MC + "entity/EntityLivingBase;F)F")) patched += swingProgress(m);
                    break;
                case HELD_ITEM:
                    if (is(m.name, "doRenderLayer", "func_177141_a") && m.desc.startsWith("(L" + MC + "entity/EntityLivingBase;")) patched += heldItem(m);
                    break;
                case GUI_INGAME:
                    if (m.name.equals("renderHealth")) patched += lastHealth(m);
                    break;
                case MINECRAFT:
                    if (is(m.name, "runTick", "func_71407_l") && m.desc.equals("()V")) patched += runTick(m);
                    else if (is(m.name, "rightClickMouse", "func_147121_ag") && m.desc.equals("()V"))
                        patched += replaceCalls(m, MC + "client/multiplayer/PlayerControllerMP", new String[] {"func_181040_m"}, "()Z",
                            "hittingBlock", "(L" + MC + "client/multiplayer/PlayerControllerMP;)Z");
                    else if (is(m.name, "clickMouse", "func_147116_af") && m.desc.equals("()V")) patched += clickMouse(m);
                    else if (is(m.name, "sendClickBlockToController", "func_147115_a") && m.desc.equals("(Z)V")) patched += clickBlock(m);
                    break;
                case RENDER_ITEM:
                    if (is(m.name, "renderItem", "func_180454_a") && m.desc.equals("(L" + MC + "item/ItemStack;L" + MC + "client/resources/model/IBakedModel;)V"))
                        patched += replaceCalls(m, MC + "client/renderer/entity/RenderItem", new String[] {"renderEffect", "func_180451_a"},
                            "(L" + MC + "client/resources/model/IBakedModel;)V", "renderEffect",
                            "(L" + MC + "client/renderer/entity/RenderItem;L" + MC + "client/resources/model/IBakedModel;)V");
                    else if (is(m.name, "renderItemIntoGUI", "func_175042_a")) patched += guiItem(m);
                    break;
            }
        }
        LOG.info("1.7 Visuals: {} hook(s) in {}", patched, className);
        if (patched == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodInsnNode hook(String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, desc, false);
    }

    private static boolean isCall(AbstractInsnNode insn, String owner, String[] names, String desc) {
        if (!(insn instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode)insn;
        return (owner == null || call.owner.equals(owner)) && is(call.name, names) && call.desc.equals(desc);
    }

    private static int replaceCalls(MethodNode m, String owner, String[] names, String desc, String hook, String hookDesc) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (isCall(insn, owner, names, desc)) {
                m.instructions.set(insn, hook(hook, hookDesc));
                n++;
            }
        }
        return n;
    }

    private static void beforeReturns(MethodNode m, int returnOp, InsnList... code) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() != returnOp) continue;
            InsnList copy = new InsnList();
            for (InsnList c : code) for (AbstractInsnNode i : c.toArray()) copy.add(i.clone(null));
            m.instructions.insertBefore(insn, copy);
        }
    }

    private static int firstPerson(MethodNode m) {
        LabelNode resume = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.FLOAD, 1));
        head.add(hook("renderFirstPerson", "(L" + MC + "client/renderer/ItemRenderer;F)Z"));
        head.add(new JumpInsnNode(Opcodes.IFEQ, resume));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(resume);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        return 1;
    }

    private static int eyeTranslate(MethodNode m) {
        MethodInsnNode last = null;
        for (AbstractInsnNode insn : m.instructions.toArray())
            if (isCall(insn, MC + "client/renderer/GlStateManager", new String[] {"translate", "func_179109_b"}, "(FFF)V")) last = (MethodInsnNode)insn;
        if (last == null) return 0;
        m.instructions.insertBefore(last, new VarInsnNode(Opcodes.FLOAD, 1));
        m.instructions.set(last, hook("eyeTranslate", "(FFFF)V"));
        return 1;
    }

    private static int swingProgress(MethodNode m) {
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.FLOAD, 2));
        code.add(hook("swingProgress", "(FL" + MC + "entity/EntityLivingBase;F)F"));
        beforeReturns(m, Opcodes.FRETURN, code);
        return 1;
    }

    private static int heldItem(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (isCall(insn, null, new String[] {"isSneaking", "func_70093_af"}, "()Z")) {
                InsnList code = new InsnList();
                code.add(new InsnNode(Opcodes.DUP));
                code.add(hook("thirdPerson", "(L" + MC + "entity/EntityLivingBase;)V"));
                m.instructions.insertBefore(insn, code);
                return 1;
            }
        }
        return 0;
    }

    private static int lastHealth(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.GETFIELD && is(((FieldInsnNode)insn).name, "lastPlayerHealth", "field_175189_D")
                && insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.ISTORE) {
                m.instructions.insert(insn, hook("lastHealth", "(I)I"));
                return 1;
            }
        }
        return 0;
    }

    private static int runTick(MethodNode m) {
        m.instructions.insert(hook("runTickStart", "()V"));
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (isCall(insn, null, new String[] {"isUsingItem", "func_71039_bw"}, "()Z")) {
                m.instructions.insertBefore(insn, hook("runTickInput", "()V"));
                return 2;
            }
        }
        return 1;
    }

    private static int clickMouse(MethodNode m) {
        m.instructions.insert(hook("clickStart", "()V"));
        return 1 + replaceCalls(m, MC + "block/Block", new String[] {"getMaterial", "func_149688_o"}, "()L" + MC + "block/material/Material;",
            "clickMaterial", "(L" + MC + "block/Block;)L" + MC + "block/material/Material;");
    }

    private static int clickBlock(MethodNode m) {
        int n = 0;
        String type = MC + "util/MovingObjectPosition$MovingObjectType";
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)insn).owner.equals(type) && ((FieldInsnNode)insn).name.equals("BLOCK")) {
                m.instructions.set(insn, hook("blockType", "()L" + type + ";"));
                n++;
            }
        }
        InsnList done = new InsnList();
        done.add(hook("clickBlockDone", "()V"));
        beforeReturns(m, Opcodes.RETURN, done);
        return n + 1;
    }

    private static int guiItem(MethodNode m) {
        InsnList head = new InsnList();
        head.add(new InsnNode(Opcodes.ICONST_1));
        head.add(hook("guiItem", "(Z)V"));
        m.instructions.insert(head);
        InsnList tail = new InsnList();
        tail.add(new InsnNode(Opcodes.ICONST_0));
        tail.add(hook("guiItem", "(Z)V"));
        beforeReturns(m, Opcodes.RETURN, tail);
        return 1;
    }
}
