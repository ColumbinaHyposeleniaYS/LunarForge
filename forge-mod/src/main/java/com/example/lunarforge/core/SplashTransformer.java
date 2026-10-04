package com.example.lunarforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class SplashTransformer implements IClassTransformer {
    private static final String SPLASH = "net.minecraftforge.fml.client.SplashProgress";
    private static final String PROGRESS = "net.minecraftforge.fml.common.ProgressManager";
    private static final String PROGRESS_BAR = "net.minecraftforge.fml.common.ProgressManager$ProgressBar";
    private static final String HOOKS = "com/example/lunarforge/splash/LunarSplash";

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("lunarforge.splash"));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null || !FMLLaunchHandler.side().isClient()) return basicClass;
        if (SPLASH.equals(transformedName)) return patch(basicClass, Target.SPLASH);
        if (PROGRESS.equals(transformedName)) return patch(basicClass, Target.PROGRESS);
        if (PROGRESS_BAR.equals(transformedName)) return patch(basicClass, Target.PROGRESS_BAR);
        return basicClass;
    }

    private enum Target { SPLASH, PROGRESS, PROGRESS_BAR }

    private static byte[] patch(byte[] bytes, Target target) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int patched = 0;
        for (MethodNode method : node.methods) {
            switch (target) {
                case SPLASH:
                    if (method.name.equals("start") && method.desc.equals("()V")) {
                        returnEarlyIf(method, "takeOverFromFml");
                        patched++;
                    } else if (method.name.equals("drawVanillaScreen")) {
                        returnEarlyIf(method, "onVanillaScreen");
                        patched++;
                    }
                    break;
                case PROGRESS:
                    if (method.name.equals("push") || method.name.equals("pop")) {
                        redrawBeforeReturns(method);
                        patched++;
                    }
                    break;
                case PROGRESS_BAR:
                    if (method.name.equals("step")) {
                        redrawBeforeReturns(method);
                        patched++;
                    }
                    break;
            }
        }
        if (patched == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void returnEarlyIf(MethodNode method, String hook) {
        LabelNode resume = new LabelNode();
        InsnList head = new InsnList();
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, "()Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, resume));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(resume);

        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(head);
    }

    private static void redrawBeforeReturns(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            int op = insn.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                method.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "onProgress", "()V", false));
            }
        }
    }
}
