<div align="center">
  <img src="images/banner.png" alt="Lunar" width="512">

  # Lunar Client Remake
</div>

Lunar client remake for Minecraft 1.8.9 forge as the form of a mod. This is a **remake** of Lunar Client, not the real thing so some things might not be perfectly accurate. I spent a very long time making sure a lot of it is 1:1 with the original, but expect some small differences here and there.

## Features

- **Removed bloat**
- **Every cosmetic unlocked**
- **You can see other Lunar players and they can see you!**
- **Options for legacy Lunar main menu**
- **Open source & doesn't sell your data unlike real Lunar**

## Building

You need **Java 8 JDK**
```
.\gradlew build
```

The finished jar will be in `build/libs/`.

Prebuild jar is in Releases tab


---

## Credits / 原作者出处 (Attribution)

This is **not** my original work. All credit for the LunarForge project goes to the original author.

- **Original author (原作者)**: [MegaPVP](https://codeberg.org/MegaPVP)
- **Original repository (原仓库)**: https://codeberg.org/MegaPVP/LunarForge
- **Imported from (来源版本)**: `master` branch archive, upstream commit `638b5925bf5f9460fe9154b9844e1f161825ef9e`

> [!NOTE]
> 本仓库为原作者 MegaPVP 的 LunarForge 项目的转载/镜像，原始代码与资源均保持原样未做修改，原作者出处如上。
> 上游项目未附带开源许可证（LICENSE），因此本项目不另行添加许可证，相关权利归原作者 MegaPVP 所有。

---

## 🔒 安全审计报告 (Security Audit Report)

**审计结论：本仓库不含病毒、盗号或任何损害玩家利益的恶意代码。**
（审计日期：2026-10-04，覆盖全部 214 个 Java 源文件、资源文件、构建脚本与 git 历史）

- **凭据安全**：Minecraft 会话令牌仅用于 Mojang 官方认证接口 `joinServer()`（所有 MC 客户端的标准进服流程），不会发送给任何第三方。
- **网络行为**：仅连接 Lunar 官方服务（`*.lunarclientprod.com` / `lunarclientcdn.com`，用于饰品与 Tab 头像显示），发送内容（UUID、用户名、系统信息）与正版 Lunar Client 一致；HWID 为固定 `"0"`、安装 ID 为随机 UUID，不收集真实硬件信息。可添加 JVM 参数 `-Dlunarforge.network=false` 完全禁用联网。
- **无恶意载荷**：无原生库（DLL/SO/EXE）、无混淆/加密代码、无远程类加载、无键盘记录或剪贴板监控，文件读写均限于 `.minecraft` 游戏目录。
- **上游一致性**：已与上游 [MegaPVP/LunarForge](https://codeberg.org/MegaPVP/LunarForge)（commit `638b592`）逐字节比对——资源完全一致，源码仅 6 处 MCP 映射名的等价重命名（`func_178908_a` → `splitText` 等）。

### 关于 VirusTotal 报 "Adwind.j" 的说明（误报）

VirusTotal 70+ 引擎中仅 McAfee 系两家（Skyhigh SWG、Trellix ENS，共用同一套签名库）报 `Adwind.j`，其余引擎全部 Undetected。Adwind 是 Java 远控木马家族，其通用启发式特征（运行时字节码改写、反射、Base64、网络连接、进程调用）与带 ASM CoreMod 的 Minecraft 模组的正当实现高度重叠，因此命中误报——这是 MC 模组社区广为人知的现象。本模组的 CoreMod 仅向 Minecraft 客户端类注入本模组自身的渲染/HUD 钩子，无任何远程代码加载。

### 变更说明

- 应仓库所有者要求，**Apollo**（`lunar:apollo` 服务器协议集成，向支持该协议的服务器同步模组开关状态）已全部注释禁用，默认不再注册该通道。
- 供核验：v0.1.0 Release jar（构建自 commit `d827d9e`）SHA256：
  `ca840fda47675047d7861ecb933c46b2cb1aad270473b7f3a0f84250ca37e94c`
