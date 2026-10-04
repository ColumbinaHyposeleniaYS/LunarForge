// ============================================================================
// Apollo (lunar:apollo server protocol integration) - DISABLED
//
// 全部注释禁用（应仓库所有者要求，2026-10-04）。
// 该类原本仅向支持 lunar:apollo 协议的服务器（如 Hypixel）同步模组开关状态，
// 安全审计确认其无害；禁用后模组不再注册该通道、不再发送玩家握手。
// 如需恢复：取消本文件所有行的注释，并恢复 LunarNetwork.init() 中的注册行。
// ============================================================================
// package com.example.lunarforge.net;
//
// import com.example.lunarforge.module.Module;
// import com.example.lunarforge.module.ModuleManager;
// import com.example.lunarforge.module.setting.BoolSetting;
// import com.example.lunarforge.module.setting.ColorSetting;
// import com.example.lunarforge.module.setting.NumberSetting;
// import com.example.lunarforge.module.setting.Setting;
// import com.google.common.base.CaseFormat;
// import io.netty.buffer.Unpooled;
// import java.io.BufferedReader;
// import java.io.InputStreamReader;
// import java.nio.charset.StandardCharsets;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import net.minecraft.network.NetworkManager;
// import net.minecraft.network.PacketBuffer;
// import net.minecraft.network.play.client.C17PacketCustomPayload;
// import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
// import net.minecraftforge.fml.common.network.FMLNetworkEvent;
//
// public final class Apollo {
//     public static final String CHANNEL = "lunar:apollo";
//     public static final String CHANNEL_JSON = "apollo:json";
//
//     public static final String CHANNEL_PM = "lunarclient:pm";
//
//     private static Map<String, Character> modStatusOptions;
//
//     @SubscribeEvent
//     public void onRegister(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
//         if (!"REGISTER".equals(event.operation) || event.side.isServer()) return;
//         Set<String> server = event.registrations;
//         List<String> reply = new ArrayList<String>();
//
//         for (String ch : new String[] {CHANNEL, CHANNEL_JSON}) if (server.contains(ch)) reply.add(ch);
//         if (server.contains(CHANNEL_PM)) reply.add(CHANNEL_PM);
//         if (reply.isEmpty()) return;
//         NetworkManager manager = event.manager;
//         String joined = String.join("\0", reply);
//         manager.sendPacket(new C17PacketCustomPayload("REGISTER", new PacketBuffer(Unpooled.wrappedBuffer(joined.getBytes(StandardCharsets.UTF_8)))));
//         manager.sendPacket(new C17PacketCustomPayload(CHANNEL, new PacketBuffer(Unpooled.wrappedBuffer(handshake()))));
//         LunarNetwork.LOG.info("Apollo: registered {} and sent the player handshake", reply);
//     }
//
//     static byte[] handshake() {
//         Proto.Writer message = new Proto.Writer()
//             .message(1, new Proto.Writer().string(1, "v1_8"))
//             .message(2, AssetSocket.lunarClientVersion());
//         for (Map.Entry<String, Proto.Writer> e : modStatus().entrySet()) {
//             message.message(5, new Proto.Writer().string(1, e.getKey()).message(2, e.getValue()));
//         }
//         return new Proto.Writer()
//             .string(1, "type.googleapis.com/lunarclient.apollo.player.v1.PlayerHandshakeMessage")
//             .bytes(2, message.toByteArray())
//             .toByteArray();
//     }
//
//     private static Map<String, Proto.Writer> modStatus() {
//         Map<String, Character> known = modStatusOptions();
//         Map<String, Proto.Writer> status = new HashMap<String, Proto.Writer>();
//         List<Module> modules = new ArrayList<Module>(ModuleManager.modules());
//         for (int i = 0; i < modules.size(); i++) {
//             Module module = modules.get(i);
//             modules.addAll(module.children());
//             String prefix = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, module.id) + ".";
//             if (module.isEnabled() != module.defaultEnabled() && known.get(prefix + "enabled") != null) {
//                 status.put(prefix + "enabled", new Proto.Writer().oneofBool(4, module.isEnabled()));
//             }
//             for (Setting<?> setting : module.settings()) {
//                 if (setting.isDefault()) continue;
//                 String key = prefix + CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, setting.key);
//                 Character kind = known.get(key);
//                 if (kind == null) continue;
//
//                 if (kind == 'B' && setting instanceof BoolSetting) {
//                     status.put(key, new Proto.Writer().oneofBool(4, ((BoolSetting)setting).get()));
//                 } else if (kind == 'N' && setting instanceof NumberSetting) {
//                     status.put(key, new Proto.Writer().oneofDouble(2, ((NumberSetting)setting).get()));
//                 } else if (kind == 'C' && setting instanceof ColorSetting) {
//                     status.put(key, new Proto.Writer().oneofString(3, Integer.toHexString(((ColorSetting)setting).get())));
//                 }
//             }
//         }
//         return status;
//     }
//
//     private static synchronized Map<String, Character> modStatusOptions() {
//         if (modStatusOptions != null) return modStatusOptions;
//         Map<String, Character> map = new HashMap<String, Character>();
//         try (BufferedReader in = new BufferedReader(new InputStreamReader(
//                 Apollo.class.getResourceAsStream("/assets/lunarforge/apollo/mod_status.txt"), StandardCharsets.UTF_8))) {
//             for (String line; (line = in.readLine()) != null; ) {
//                 int space = line.indexOf(' ');
//                 if (!line.startsWith("#") && space > 0) map.put(line.substring(0, space), line.charAt(space + 1));
//             }
//         } catch (Exception e) {
//             LunarNetwork.LOG.warn("Could not read the Apollo mod-status options: {}", e.toString());
//         }
//         return modStatusOptions = map;
//     }
// }
