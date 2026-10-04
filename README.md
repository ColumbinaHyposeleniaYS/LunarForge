<div align="center">
  <img src="images/banner.png" alt="LunarForge" width="512">

  # LunarForge

  **Lunar Client Remake · Minecraft 1.8.9 Forge Mod**

  **[简体中文](#zh) | [English](#en)**
</div>

<a id="zh"></a>

## 简体中文

LunarForge 是 Lunar Client 的 Minecraft 1.8.9 Forge 重制版，以模组形式发布。这是 Lunar Client 的**重制版**，并非官方客户端，因此部分内容可能不完全准确。原作者花了很长时间确保大部分内容与原版 1:1 还原，但仍可能存在少量细节差异。

### 特性

- **移除臃肿内容**
- **全部饰品解锁**
- **你和其他 Lunar 玩家可以互相看到！**
- **支持旧版 Lunar 主菜单选项**
- **源码公开，且不像正版 Lunar 那样出售你的数据**（本仓库未附带 LICENSE，仅源码公开可审计，权限归属见文末说明）

### 安装

1. 安装 [Minecraft Forge 1.8.9](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html)。
2. 从 Releases（发布页）下载 `lunarforge-*.jar`。
3. 将 jar 放入 `.minecraft/mods/` 目录，启动游戏即可。
4. 模组的配置与缓存保存在 `.minecraft/lunarforge/` 目录。

### 构建

需要 **Java 8 JDK**（必须是 1.8，较新版本的 JDK 无法运行本项目使用的 ForgeGradle 2.1）：

```text
# Windows
.\gradlew build

# Linux / macOS
./gradlew build
```

构建完成的 jar 位于 `build/libs/` 目录。本仓库自带 GitHub Actions 构建工作流，也可在 Actions 页面手动触发（Run workflow）。

### 🔒 安全审计报告

**审计结论：本仓库不含病毒、盗号或任何损害玩家利益的恶意代码。**
（审计日期：2026-10-04，覆盖全部 214 个 Java 源文件、资源文件、构建脚本与 git 历史）

- **凭据安全**：Minecraft 会话令牌仅用于 Mojang 官方认证接口 `joinServer()`（所有 MC 客户端的标准进服流程），不会发送给任何第三方。
- **网络行为**：仅连接 Lunar 官方服务（`*.lunarclientprod.com` / `lunarclientcdn.com`，用于饰品与 Tab 头像显示），发送内容（UUID、用户名、系统信息）与正版 Lunar Client 一致；HWID 为固定 `"0"`、安装 ID 为随机 UUID，不收集真实硬件信息。可添加 JVM 参数 `-Dlunarforge.network=false` 完全禁用联网。
- **无恶意载荷**：无原生库（DLL/SO/EXE）、无混淆/加密代码、无远程类加载、无键盘记录或剪贴板监控，文件读写均限于 `.minecraft` 游戏目录。
- **上游一致性**：已与上游 [MegaPVP/LunarForge](https://codeberg.org/MegaPVP/LunarForge)（commit `638b592`）逐字节比对——资源完全一致，源码仅 6 处 MCP 映射名的等价重命名（`func_178908_a` → `splitText` 等）。

#### 关于 VirusTotal 报 "Adwind.j" 的说明（误报）

VirusTotal 70+ 引擎中仅 McAfee 系两家（Skyhigh SWG、Trellix ENS，共用同一套签名库）报 `Adwind.j`，其余引擎全部 Undetected。Adwind 是 Java 远控木马家族，其通用启发式特征（运行时字节码改写、反射、Base64、网络连接、进程调用）与带 ASM CoreMod 的 Minecraft 模组的正当实现高度重叠，因此命中误报——这是 MC 模组社区广为人知的现象。本模组的 CoreMod 仅向 Minecraft 客户端类注入本模组自身的渲染/HUD 钩子，无任何远程代码加载。

#### 变更说明

- 应仓库所有者要求，**Apollo**（`lunar:apollo` 服务器协议集成，向支持该协议的服务器同步模组开关状态）已全部注释禁用，默认不再注册该通道。
- 供核验：v0.1.0 Release jar（构建自 commit `d827d9e`，**早于 Apollo 禁用**，因此该 jar 内仍包含 Apollo 功能）SHA256：

  `ca840fda47675047d7861ecb933c46b2cb1aad270473b7f3a0f84250ca37e94c`

  此后源码已有变更；后续新版本的核验哈希请以对应 Release 页面标注为准。

---

<a id="en"></a>

## English

LunarForge is a Lunar Client remake for Minecraft 1.8.9 Forge, in the form of a mod. This is a **remake** of Lunar Client, not the real thing, so some things might not be perfectly accurate. The original author spent a very long time making sure most of it is 1:1 with the original, but expect some small differences here and there.

### Features

- **Removed bloat**
- **Every cosmetic unlocked**
- **You can see other Lunar players and they can see you!**
- **Options for legacy Lunar main menu**
- **Source available, and doesn't sell your data unlike real Lunar** (no LICENSE is shipped in this repository — the code is publicly auditable only; see the attribution note below)

### Installation

1. Install [Minecraft Forge 1.8.9](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html).
2. Download `lunarforge-*.jar` from the Releases tab.
3. Drop the jar into your `.minecraft/mods/` folder and start the game.
4. Module data and caches are stored under `.minecraft/lunarforge/`.

### Building

You need the **Java 8 JDK** (exactly 1.8 — newer JDKs cannot run ForgeGradle 2.1, which this project uses):

```text
# Windows
.\gradlew build

# Linux / macOS
./gradlew build
```

The finished jar will be in `build/libs/`. The repository also ships a GitHub Actions workflow, which can be triggered manually from the Actions page (Run workflow).

### 🔒 Security Audit Report

**Conclusion: this repository contains no viruses, no account/credential stealing, and no malicious code of any kind that could harm players.**
(Audit date: 2026-10-04, covering all 214 Java source files, resource files, build scripts, and the full git history)

- **Credential safety**: the Minecraft session token is only used with Mojang's official authentication endpoint `joinServer()` (the standard server-join flow of every MC client) and is never sent to any third party.
- **Network behavior**: connections go only to Lunar's official services (`*.lunarclientprod.com` / `lunarclientcdn.com`, used for cosmetics and tab logos); the data sent (UUID, username, OS info) is identical to the real Lunar Client. The HWID is a fixed `"0"` and the installation ID is a random UUID, so no real hardware data is collected. Add the JVM argument `-Dlunarforge.network=false` to fully disable all networking.
- **No malicious payloads**: no native libraries (DLL/SO/EXE), no obfuscated or encrypted code, no remote class loading, no keylogging or clipboard monitoring; all file I/O stays inside the `.minecraft` directory.
- **Upstream consistency**: byte-for-byte compared against upstream [MegaPVP/LunarForge](https://codeberg.org/MegaPVP/LunarForge) (commit `638b592`) — resources are identical, and the source differs only by 6 equivalent MCP mapping renames (`func_178908_a` → `splitText`, etc.).

#### About the VirusTotal "Adwind.j" detection (false positive)

Out of 70+ engines on VirusTotal, only two McAfee-lineage vendors (Skyhigh SWG and Trellix ENS, which share the same signature database) flag `Adwind.j`; every other engine reports Undetected. Adwind is a Java RAT family whose generic heuristics (runtime bytecode rewriting, reflection, Base64, networking, process spawning) heavily overlap with the legitimate implementation of Minecraft mods that ship an ASM coremod, so the signature misfires — a well-known phenomenon in the MC modding community. This mod's coremod only injects the mod's own rendering/HUD hooks into Minecraft client classes; no remote code is ever loaded.

#### Changes made in this repository

- At the repository owner's request, **Apollo** (the `lunar:apollo` server protocol integration that syncs module toggle states to supporting servers) has been fully commented out and disabled by default.
- For verification: the SHA256 of the v0.1.0 release jar (built from commit `d827d9e`, **before** the Apollo disable — that jar still contains the Apollo feature):

  `ca840fda47675047d7861ecb933c46b2cb1aad270473b7f3a0f84250ca37e94c`

  The source has changed since then; for future releases, use the hash noted on the corresponding Release page.

---

## Credits / 原作者出处 (Attribution)

This is **not** my original work. All credit for the LunarForge project goes to the original author.

- **Original author (原作者)**: [MegaPVP](https://codeberg.org/MegaPVP)
- **Original repository (原仓库)**: https://codeberg.org/MegaPVP/LunarForge
- **Imported from (来源版本)**: `master` branch archive, upstream commit `638b5925bf5f9460fe9154b9844e1f161825ef9e`

> [!NOTE]
> 本仓库为原作者 MegaPVP 的 LunarForge 项目的转载/镜像。除少量构建修复及本文所述变更（Apollo 禁用等）外，原始代码与资源保持原样，原作者出处如上。
> 上游项目未附带开源许可证（LICENSE），因此本项目不另行添加许可证，相关权利归原作者 MegaPVP 所有。
>
> This repository is a mirror/repost of MegaPVP's LunarForge project. Apart from a few build fixes and the changes described above (e.g. the Apollo disable), the original code and resources are kept as-is.
> The upstream project ships no LICENSE, so no license is added here; all rights belong to the original author MegaPVP.
