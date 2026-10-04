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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class TabListTransformer implements IClassTransformer {
    private static final Logger LOG = LogManager.getLogger("LunarForge");
    private static final String HOOKS = "com/example/lunarforge/net/TabLogos";
    private static final String TAB = "net/minecraft/client/gui/GuiPlayerTabOverlay";
    private static final String FONT = "net/minecraft/client/gui/FontRenderer";
    private static final String INFO = "net/minecraft/client/network/NetworkPlayerInfo";
    private static final String TAB_MOD = "com/example/lunarforge/module/modules/mechanic/ModuleTab";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        return patch(transformedName, bytes);
    }

    static byte[] patch(String transformedName, byte[] bytes) {
        if ("net.minecraftforge.client.GuiIngameForge".equals(transformedName)) return ingame(bytes, transformedName);
        if (!"net.minecraft.client.gui.GuiPlayerTabOverlay".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode m : node.methods) {
            if ((m.name.equals("drawPing") || m.name.equals("func_175245_a")) && m.desc.equals("(IIIL" + INFO + ";)V")) {
                patched += drawPing(m);
                continue;
            }
            if (!(m.name.equals("renderPlayerlist") || m.name.equals("func_175249_a"))
                    || !m.desc.equals("(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V")) continue;
            patched += tabModule(m);
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode call = (MethodInsnNode)insn;
                String hook = null, desc = null;
                if (call.owner.equals(TAB) && is(call, "getPlayerName", "func_175243_a", "(L" + INFO + ";)Ljava/lang/String;")) {
                    hook = "playerName";
                    desc = "(L" + TAB + ";L" + INFO + ";)Ljava/lang/String;";
                } else if (call.owner.equals(FONT) && is(call, "getStringWidth", "func_78256_a", "(Ljava/lang/String;)I")) {
                    hook = "stringWidth";
                    desc = "(L" + FONT + ";Ljava/lang/String;)I";
                } else if (call.owner.equals(FONT) && is(call, "drawStringWithShadow", "func_175063_a", "(Ljava/lang/String;FFI)I")) {
                    hook = "drawName";
                    desc = "(L" + FONT + ";Ljava/lang/String;FFI)I";
                }
                if (hook == null) continue;
                m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, desc, false));
                patched++;
            }
        }
        LOG.info("Tab list hooks: {} call(s) in {}", patched, transformedName);
        if (patched == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int tabModule(MethodNode m) {
        int count = 0, nines = 0, rects = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode)insn).operand == 9) {
                if (nines == 0 || nines == 3) { m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "nine", "(I)I", false)); count++; }
                nines++;
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("com/google/common/collect/Ordering") && call.name.equals("sortedCopy")) {
                    m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "sorted", "(Lcom/google/common/collect/Ordering;Ljava/lang/Iterable;)Ljava/util/List;", false));
                    count++;
                } else if (call.owner.equals("net/minecraft/client/Minecraft") && is(call, "isIntegratedServerRunning", "func_71387_A", "()Z")
                        || call.owner.equals("net/minecraft/network/NetworkManager") && is(call, "getIsencrypted", "func_179292_f", "()Z")) {
                    m.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "heads", "(Z)Z", false));
                    count++;
                } else if (call.getOpcode() == Opcodes.INVOKESTATIC && (call.owner.equals("net/minecraft/client/gui/Gui") || call.owner.equals(TAB))
                        && is(call, "drawRect", "func_73734_a", "(IIIII)V") && rects < 4) {
                    InsnList list = new InsnList();
                    list.add(new IntInsnNode(Opcodes.BIPUSH, rects++));
                    list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "rect", "(IIIIII)V", false));
                    m.instructions.insertBefore(call, list);
                    m.instructions.remove(call);
                    count++;
                }
            }
        }
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.RETURN) continue;
            InsnList end = new InsnList();
            end.add(new VarInsnNode(Opcodes.ALOAD, 0));
            end.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "end", "(L" + TAB + ";)V", false));
            m.instructions.insertBefore(insn, end);
            count++;
        }
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "begin", "(L" + TAB + ";)V", false));
        m.instructions.insert(begin);
        return count + 1;
    }

    private static int drawPing(MethodNode m) {
        InsnList head = new InsnList();
        LabelNode go = new LabelNode();
        for (int i = 1; i <= 3; i++) head.add(new VarInsnNode(Opcodes.ILOAD, i));
        head.add(new VarInsnNode(Opcodes.ALOAD, 4));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "ping", "(IIIL" + INFO + ";)I", false));
        head.add(new InsnNode(Opcodes.DUP));
        head.add(new VarInsnNode(Opcodes.ISTORE, 1));
        head.add(new LdcInsnNode(Integer.MIN_VALUE));
        head.add(new JumpInsnNode(Opcodes.IF_ICMPNE, go));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(go);
        head.add(new org.objectweb.asm.tree.FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        return 1;
    }

    private static byte[] ingame(byte[] bytes, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode m : node.methods) {
            if (!m.name.equals("renderPlayerList")) continue;
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("net/minecraft/client/settings/KeyBinding") && is(call, "isKeyDown", "func_151470_d", "()Z")) {
                    m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, TAB_MOD, "listKey", "(Lnet/minecraft/client/settings/KeyBinding;)Z", false));
                    patched++;
                }
            }
        }
        LOG.info("Tab key hooks: {} call(s) in {}", patched, name);
        if (patched == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean is(MethodInsnNode call, String mcp, String srg, String desc) {
        return (call.name.equals(mcp) || call.name.equals(srg)) && call.desc.equals(desc);
    }
}
