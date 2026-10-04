package com.example.lunarforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.apache.logging.log4j.LogManager;

public final class CameraCombatTransformer implements IClassTransformer {
    private static final String MECHANIC = "com/example/lunarforge/module/modules/mechanic/";
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        return patch(transformedName, bytes);
    }
    static byte[] patch(String transformedName, byte[] bytes) {
        boolean camera = "net.minecraft.client.renderer.EntityRenderer".equals(transformedName);
        boolean packets = "net.minecraft.client.network.NetHandlerPlayClient".equals(transformedName);
        boolean gui = "net.minecraft.client.gui.GuiIngame".equals(transformedName);
        if (!camera && !packets && !gui) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            if (gui && named(method, "renderScoreboard", "func_180475_a")
                    && method.desc.equals("(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V")) {
                InsnList hook = new InsnList();
                hook.add(invoke("com/example/lunarforge/module/modules/hud/ModuleScoreboard", "replacesVanilla", "()Z"));
                LabelNode vanilla = new LabelNode(); hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                hook.add(new InsnNode(Opcodes.RETURN)); hook.add(vanilla);
                hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(hook); count++;
            }
            if (packets && named(method, "handleEntityStatus", "func_147236_a")
                    && method.desc.equals("(Lnet/minecraft/network/play/server/S19PacketEntityStatus;)V")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    hook.add(invoke("com/example/lunarforge/module/CombatHooks", "status", method.desc));
                    method.instructions.insertBefore(insn, hook); count++;
                }
            }
            if (!camera) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (named(method, "hurtCameraEffect", "func_78482_e") && insn instanceof LdcInsnNode
                        && Float.valueOf(14).equals(((LdcInsnNode)insn).cst)) {
                    method.instructions.insert(insn, invoke(MECHANIC + "ModuleHurtCam", "shake", "(F)F")); count++;
                }
                if (named(method, "updateCameraAndRender", "func_181560_a")) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)insn;
                        if ((call.name.equals("setAngles") || call.name.equals("func_70082_c")) && call.desc.equals("(FF)V")) {
                            method.instructions.set(insn, invoke(MECHANIC + "ModuleFreelook", "turn", "(Lnet/minecraft/entity/Entity;FF)V")); count++;
                        }
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode)insn;
                        if (field.getOpcode() == Opcodes.GETFIELD && field.desc.equals("F")
                                && (field.name.equals("mouseSensitivity") || field.name.equals("field_74341_c"))) {
                            method.instructions.insert(insn, invoke(MECHANIC + "ModuleZoom", "sensitivity", "(F)F")); count++;
                        }
                    }
                }
                if (named(method, "orientCamera", "func_78467_g") && insn instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode)insn;
                    if (field.getOpcode() != Opcodes.GETFIELD || !field.desc.equals("F")) continue;
                    String hook = rotation(field.name);
                    if (hook != null) { method.instructions.set(insn, invoke(MECHANIC + "ModuleFreelook", hook, "(Lnet/minecraft/entity/Entity;)F")); count++; }
                }
            }
        }
        if (count == 0) return bytes;
        LogManager.getLogger("LunarForge").info("Camera/combat: {} hooks in {}", count, transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static boolean named(MethodNode m, String mcp, String srg) { return m.name.equals(mcp) || m.name.equals(srg); }
    private static MethodInsnNode invoke(String owner, String method, String desc) { return new MethodInsnNode(Opcodes.INVOKESTATIC, owner, method, desc, false); }
    private static String rotation(String field) {
        if (field.equals("rotationYaw") || field.equals("field_70177_z")) return "yaw";
        if (field.equals("rotationPitch") || field.equals("field_70125_A")) return "pitch";
        if (field.equals("prevRotationYaw") || field.equals("field_70126_B")) return "previousYaw";
        if (field.equals("prevRotationPitch") || field.equals("field_70127_C")) return "previousPitch";
        return null;
    }
}
