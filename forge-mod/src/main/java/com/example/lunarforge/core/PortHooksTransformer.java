package com.example.lunarforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PortHooksTransformer implements IClassTransformer {
    static final String HUD = "com/example/lunarforge/module/modules/hud/";
    static final String CHAT = "com/example/lunarforge/module/modules/mechanic/ChatHooks";
    static final String COMPONENT = "Lnet/minecraft/util/IChatComponent;";
    static final String FONT = "net/minecraft/client/gui/FontRenderer";
    static final String NICK = "com/example/lunarforge/module/modules/mechanic/ModuleNickHider";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !FMLLaunchHandler.side().isClient()) return bytes;
        return patch(transformedName, bytes);
    }

    static byte[] patch(String transformedName, byte[] bytes) {
        boolean effects = "net.minecraft.client.renderer.InventoryEffectRenderer".equals(transformedName);
        boolean chatLine = "net.minecraft.client.gui.ChatLine".equals(transformedName);
        boolean newChat = "net.minecraft.client.gui.GuiNewChat".equals(transformedName);
        boolean guiChat = "net.minecraft.client.gui.GuiChat".equals(transformedName);
        boolean c01 = "net.minecraft.network.play.client.C01PacketChatMessage".equals(transformedName);
        boolean net = "net.minecraft.client.network.NetHandlerPlayClient".equals(transformedName);
        boolean netManager = "net.minecraft.network.NetworkManager".equals(transformedName);
        boolean screen = "net.minecraft.client.gui.GuiScreen".equals(transformedName);
        boolean font = "net.minecraft.client.gui.FontRenderer".equals(transformedName);
        boolean info = "net.minecraft.client.network.NetworkPlayerInfo".equals(transformedName);
        if (!effects && !chatLine && !newChat && !guiChat && !c01 && !net && !netManager && !screen && !font && !info) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        if (chatLine) count += chatLineTag(node);
        for (MethodNode m : node.methods) {
            if (effects && RenderHooksTransformer.named(m, "drawActivePotionEffects", "func_147044_g") && m.desc.equals("()V")) count += potionRows(m);
            if (newChat && RenderHooksTransformer.named(m, "setChatLine", "func_146237_a") && m.desc.equals("(" + COMPONENT + "IIZ)V")) count += setChatLine(m);
            if (newChat && RenderHooksTransformer.named(m, "drawChat", "func_146230_a") && m.desc.equals("(I)V")) count += drawChat(m);
            if (newChat && RenderHooksTransformer.named(m, "getChatComponent", "func_146236_a") && m.desc.equals("(II)" + COMPONENT)) count += chatComponent(m);
            if (newChat && RenderHooksTransformer.named(m, "getChatOpen", "func_146241_e") && m.desc.equals("()Z")) {
                InsnList cond = new InsnList();
                cond.add(RenderHooksTransformer.invoke(CHAT, "peek", "()Z"));
                count += returnTrueIf(m, cond);
            }
            if (guiChat && RenderHooksTransformer.named(m, "drawScreen", "func_73863_a") && m.desc.equals("(IIF)V")) count += chatScreen(m);
            if (guiChat && RenderHooksTransformer.named(m, "initGui", "func_73866_w_") && m.desc.equals("()V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && RenderHooksTransformer.named((MethodInsnNode)insn, "setMaxStringLength", "func_146203_f")) {
                        m.instructions.insertBefore(insn, RenderHooksTransformer.invoke(CHAT, "maxInput", "(I)I"));
                        count++;
                    }
                }
            }
            if (c01 && m.name.equals("<init>") && m.desc.equals("(Ljava/lang/String;)V")) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).owner.equals("java/lang/String") && ((MethodInsnNode)insn).name.equals("substring")
                            && ((MethodInsnNode)insn).desc.equals("(II)Ljava/lang/String;")) {
                        m.instructions.set(insn, RenderHooksTransformer.invoke(CHAT, "trimOutgoing", "(Ljava/lang/String;II)Ljava/lang/String;"));
                        count++;
                    }
                }
            }
            if (screen && RenderHooksTransformer.named(m, "sendChatMessage", "func_175281_b") && m.desc.equals("(Ljava/lang/String;Z)V")) {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(Opcodes.ALOAD, 1));
                head.add(RenderHooksTransformer.invoke("com/example/lunarforge/module/ChatSendEvent", "fire", "(Ljava/lang/String;)Ljava/lang/String;"));
                head.add(new InsnNode(Opcodes.DUP));
                head.add(new VarInsnNode(Opcodes.ASTORE, 1));
                LabelNode go = new LabelNode();
                head.add(new JumpInsnNode(Opcodes.IFNONNULL, go));
                head.add(new InsnNode(Opcodes.RETURN));
                head.add(go);
                head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                m.instructions.insert(head);
                count++;
            }
            if (font && (RenderHooksTransformer.named(m, "renderString", "func_180455_b") && m.desc.equals("(Ljava/lang/String;FFIZ)I")
                    || RenderHooksTransformer.named(m, "getStringWidth", "func_78256_a") && m.desc.equals("(Ljava/lang/String;)I"))) {
                InsnList head = new InsnList();
                head.add(new VarInsnNode(Opcodes.ALOAD, 1));
                head.add(RenderHooksTransformer.invoke(NICK, "replace", "(Ljava/lang/String;)Ljava/lang/String;"));
                head.add(new VarInsnNode(Opcodes.ASTORE, 1));
                m.instructions.insert(head);
                count++;
            }
            if (info && RenderHooksTransformer.named(m, "getLocationSkin", "func_178837_g") && m.desc.equals("()Lnet/minecraft/util/ResourceLocation;")) {
                count += wrapReturn(m, NICK, "skin", "(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/network/NetworkPlayerInfo;)Lnet/minecraft/util/ResourceLocation;");
            }
            if (info && RenderHooksTransformer.named(m, "getSkinType", "func_178851_f") && m.desc.equals("()Ljava/lang/String;")) {
                count += wrapReturn(m, NICK, "skinType", "(Ljava/lang/String;Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;");
            }
            if (net && RenderHooksTransformer.named(m, "handleCloseWindow", "func_147276_a")
                    && m.desc.equals("(Lnet/minecraft/network/play/server/S2EPacketCloseWindow;)V")) count += closeWindow(m);
            if (net && m.desc.equals("(Lnet/minecraft/network/play/server/S12PacketEntityVelocity;)V")
                    && callsCheckThread(m)) count += velocityHook(m);
            if (netManager && RenderHooksTransformer.named(m, "sendPacket", "func_179290_a")
                    && m.desc.equals("(Lnet/minecraft/network/Packet;)V")) count += sendPacketHook(m);
        }
        if (count == 0) return bytes;
        LogManager.getLogger("LunarForge").info("Port hooks: {} hook(s) in {}", count, transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static int wrapReturn(MethodNode m, String owner, String name, String desc) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.ARETURN) continue;
            InsnList wrap = new InsnList();
            wrap.add(new VarInsnNode(Opcodes.ALOAD, 0));
            wrap.add(RenderHooksTransformer.invoke(owner, name, desc));
            m.instructions.insertBefore(insn, wrap);
            n++;
        }
        return n;
    }

    static int returnTrueIf(MethodNode m, InsnList condition) {
        LabelNode go = new LabelNode();
        condition.add(new JumpInsnNode(Opcodes.IFEQ, go));
        condition.add(new InsnNode(Opcodes.ICONST_1));
        condition.add(new InsnNode(Opcodes.IRETURN));
        condition.add(go);
        condition.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(condition);
        return 1;
    }

    private static int chatLineTag(ClassNode node) {
        String tag = "com/example/lunarforge/module/render/ChatLineTag";
        if (node.interfaces.contains(tag)) return 0;
        node.interfaces.add(tag);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "lunarforge$id", "I", null, null));
        MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC, "lunarforge$id", "()I", null, null);
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        get.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "lunarforge$id", "I"));
        get.instructions.add(new InsnNode(Opcodes.IRETURN));
        node.methods.add(get);
        MethodNode set = new MethodNode(Opcodes.ACC_PUBLIC, "lunarforge$setId", "(I)V", null, null);
        set.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        set.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        set.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, "lunarforge$id", "I"));
        set.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(set);
        return 1;
    }

    private static int setChatLine(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.RETURN) m.instructions.insertBefore(insn, RenderHooksTransformer.invoke(CHAT, "lineDone", "()V"));
            else if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode)insn).operand == 100) {
                m.instructions.insert(insn, RenderHooksTransformer.invoke(CHAT, "maxLines", "(I)I"));
                n++;
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("java/util/List") && call.name.equals("add") && call.desc.equals("(ILjava/lang/Object;)V")) {
                    InsnList tag = new InsnList();
                    tag.add(new InsnNode(Opcodes.DUP));
                    tag.add(RenderHooksTransformer.invoke(CHAT, "tag", "(Ljava/lang/Object;)V"));
                    m.instructions.insertBefore(call, tag);
                    n++;
                } else if (RenderHooksTransformer.named(call, "splitText", "func_178908_a")
                        && call.desc.equals("(" + COMPONENT + "IL" + FONT + ";ZZ)Ljava/util/List;")) {
                    call.owner = CHAT;
                    call.name = "splitText";
                    n++;
                }
            }
        }
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.ALOAD, 1));
        head.add(new VarInsnNode(Opcodes.ILOAD, 4));
        head.add(RenderHooksTransformer.invoke(CHAT, "message", "(Lnet/minecraft/client/gui/GuiNewChat;" + COMPONENT + "Z)" + COMPONENT));
        head.add(new InsnNode(Opcodes.DUP));
        head.add(new VarInsnNode(Opcodes.ASTORE, 1));
        LabelNode go = new LabelNode();
        head.add(new JumpInsnNode(Opcodes.IFNONNULL, go));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(go);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        return n + 1;
    }

    private static int drawChat(MethodNode m) {
        int n = 0;
        boolean translated = false, background = false;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (!translated && RenderHooksTransformer.named(call, "translate", "func_179109_b") && call.desc.equals("(FFF)V")) {
                translated = true;
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "translate", "(FFF)V"));
                n++;
            } else if (RenderHooksTransformer.named(call, "scale", "func_179152_a") && call.desc.equals("(FFF)V")) {
                m.instructions.insertBefore(call, new VarInsnNode(Opcodes.ILOAD, 1));
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "scale", "(FFFI)V"));
                n++;
            } else if (!background && call.getOpcode() == Opcodes.INVOKESTATIC && RenderHooksTransformer.named(call, "drawRect", "func_73734_a")
                    && call.desc.equals("(IIIII)V")) {
                background = true;
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "lineBackground", "(IIIII)V"));
                n++;
            } else if (call.owner.equals(FONT) && RenderHooksTransformer.named(call, "drawStringWithShadow", "func_175063_a")
                    && call.desc.equals("(Ljava/lang/String;FFI)I")) {
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "drawLine", "(L" + FONT + ";Ljava/lang/String;FFI)I"));
                n++;
            }
        }
        InsnList cond = new InsnList();
        cond.add(RenderHooksTransformer.invoke(CHAT, "skipDraw", "()Z"));
        return n + RenderHooksTransformer.returnIf(m, cond);
    }

    private static int chatComponent(MethodNode m) {
        int n = 0, floors = 0;
        boolean hovered = false;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (RenderHooksTransformer.named(call, "floor_float", "func_76141_d") && call.desc.equals("(F)I") && floors++ == 1) {
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "hoverY", "(F)I"));
                n++;
            } else if (call.owner.equals("java/util/List") && call.name.equals("get") && call.desc.equals("(I)Ljava/lang/Object;")) {
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "hoverLine", "(Ljava/util/List;I)Ljava/lang/Object;"));
                n++;
            } else if (call.owner.equals(FONT) && RenderHooksTransformer.named(call, "getStringWidth", "func_78256_a")) {
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "hoverWidth", "(L" + FONT + ";Ljava/lang/String;)I"));
                n++;
            } else if (!hovered && call.owner.equals("net/minecraft/client/gui/ChatLine") && RenderHooksTransformer.named(call, "getChatComponent", "func_151461_a")) {
                hovered = true;
                InsnList tag = new InsnList();
                tag.add(new InsnNode(Opcodes.DUP));
                tag.add(RenderHooksTransformer.invoke(CHAT, "hovered", "(Ljava/lang/Object;)V"));
                m.instructions.insertBefore(call, tag);
                n++;
            }
        }
        m.instructions.insert(RenderHooksTransformer.invoke(CHAT, "hoverStart", "()V"));
        return n + 1;
    }

    private static int chatScreen(MethodNode m) {
        int n = 0;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (call.getOpcode() == Opcodes.INVOKESTATIC && RenderHooksTransformer.named(call, "drawRect", "func_73734_a") && call.desc.equals("(IIIII)V")) {
                m.instructions.set(call, RenderHooksTransformer.invoke(CHAT, "inputBackground", "(IIIII)V"));
                n++;
            } else if (RenderHooksTransformer.named(call, "getChatComponent", "func_146236_a") && call.desc.equals("(II)" + COMPONENT)) {
                m.instructions.insert(call, RenderHooksTransformer.invoke(CHAT, "hoverComponent", "(" + COMPONENT + ")" + COMPONENT));
                n++;
            }
        }
        return n;
    }

    private static boolean callsCheckThread(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                String name = ((MethodInsnNode) insn).name;
                if (name.equals("checkThreadAndEnqueue") || name.equals("func_180031_a")) return true;
            }
        }
        return false;
    }

    /**
     * handleEntityVelocity: right after PacketThreadUtil.checkThreadAndEnqueue
     * (only reached on the main thread) ask CombatHooks.velocity whether the
     * packet was absorbed by Knockback Delay and skip the vanilla application
     * in that case.
     */
    private static int velocityHook(MethodNode m) {
        AbstractInsnNode after = null;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof MethodInsnNode) {
                String name = ((MethodInsnNode) insn).name;
                if (name.equals("checkThreadAndEnqueue") || name.equals("func_180031_a")) {
                    after = insn;
                    break;
                }
            }
        }
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(RenderHooksTransformer.invoke("com/example/lunarforge/module/CombatHooks", "velocity",
                "(Lnet/minecraft/network/play/server/S12PacketEntityVelocity;)Z"));
        LabelNode vanilla = new LabelNode();
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(vanilla);
        hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        if (after != null) m.instructions.insert(after, hook); else m.instructions.insert(hook);
        return 1;
    }

    /**
     * NetworkManager.sendPacket: injected at the head. PacketHooks decides
     * whether the packet is absorbed (Block Hit Mode's Lag buffering holds it
     * back and re-sends it later through the same manager) or sent normally;
     * the tracker also observes outgoing attack packets on this path.
     */
    private static int sendPacketHook(MethodNode m) {
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(RenderHooksTransformer.invoke("com/example/lunarforge/module/PacketHooks", "sendPacket",
                "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/Packet;)Z"));
        LabelNode vanilla = new LabelNode();
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(vanilla);
        hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(hook);
        return 1;
    }

    private static int closeWindow(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn instanceof MethodInsnNode && RenderHooksTransformer.named((MethodInsnNode)insn, "checkThreadAndEnqueue", "func_180031_a")) {
                InsnList cond = new InsnList();
                cond.add(RenderHooksTransformer.invoke(CHAT, "keepChatOpen", "()Z"));
                LabelNode go = new LabelNode();
                cond.add(new JumpInsnNode(Opcodes.IFEQ, go));
                cond.add(new InsnNode(Opcodes.RETURN));
                cond.add(go);
                cond.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                m.instructions.insert(insn, cond);
                return 1;
            }
        }
        return 0;
    }

    private static int potionRows(MethodNode m) {
        int n = 0, effect = -1;
        for (AbstractInsnNode insn : m.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.CHECKCAST && ((TypeInsnNode)insn).desc.equals("net/minecraft/potion/PotionEffect")
                    && insn.getNext() instanceof VarInsnNode && insn.getNext().getOpcode() == Opcodes.ASTORE) {
                effect = ((VarInsnNode)insn.getNext()).var;
            }
            if (effect < 0 || !(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if (RenderHooksTransformer.named(call, "drawTexturedModalRect", "func_73729_b") && call.desc.equals("(IIIIII)V")) {
                m.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, effect));
                m.instructions.set(call, RenderHooksTransformer.invoke(HUD + "ModulePotionEffects", "inventoryRow",
                    "(Lnet/minecraft/client/renderer/InventoryEffectRenderer;IIIIIILnet/minecraft/potion/PotionEffect;)V"));
                n++;
                break;
            }
        }
        InsnList cond = new InsnList();
        cond.add(RenderHooksTransformer.invoke(HUD + "ModulePotionEffects", "hideInInventory", "()Z"));
        return n + RenderHooksTransformer.returnIf(m, cond);
    }
}
