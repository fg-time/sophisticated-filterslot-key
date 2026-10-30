# Sophisticated Backpacks 过滤槽整槽转移 —— 客户端按键（1.20.1 Forge / 1.21.1 NeoForge）

Minecraft 模组 **Sophisticated Core** 的服务端 `transfer_full_slot` 处理器把客户端给的槽位号直接交给
`quickMoveStack`，而那条路径从不问 `mayPickup()`。过滤槽（filter / ghost slot）的 `mayPickup` 是
`false` —— 它本该是"只能写、不能取"的样板格，但整槽转移入口根本不问，于是样板被合并进真实库存：
**样板清空、库存多一份，可重复**。

本仓库提供两个客户端按键模组，把这个消息按过滤槽的索引发出去；服务端未打补丁时即可复现。
只装客户端，服务端不需要本模组（复用的是 Core 自己的通道与协议号）。

## 下载

| 版本 | 加载器 | 文件 |
| --- | --- | --- |
| **1.20.1** | Forge 47.x | [Releases → `v1.20.1-1.0.0`](../../releases/tag/v1.20.1-1.0.0) · `bpdupe-1.20.1-1.0.0.jar` |
| **1.21.1** | NeoForge 21.1.x | [Releases → `v1.21.1-1.0.0`](../../releases/tag/v1.21.1-1.0.0) · `bpdupe-1.21.1-1.0.0.jar` |

两个版本都放进客户端 `mods/`，服务端装同名模组本体即可（见下方"前提"）。

## 按键

| 组合 | 行为 |
| --- | --- |
| **K** | 当前菜单里每个"装着样板的过滤槽"各发一次 `transfer_full_slot` → 每个槽 +1 |
| **Shift+K**（按住） | 每 3 tick 重复一次 |
| **Ctrl+K** | 把剪贴板里的物品 ID（例 `minecraft:diamond`）写成幽灵样板（`set_ghost_slot`）并立刻转移 → 凭空造一个没拥有过的物品。要求服务端注册了这条消息（服务端装 JEI / EMI / REI） |

默认键 K，可在「控制 → 背包过滤槽按键」里改。

按键是**裸轮询 GLFW** 的，不是普通 `KeyMapping` 事件：原版只在没有 GUI 打开时喂 `KeyMapping` 状态，
普通绑定在容器界面里收不到事件。绑定的键仍从 `KeyMapping.getKey()` 读回，所以改键有效。

## 前提与失效条件

三个前提：

1. 服务端装着 **Sophisticated Core + Sophisticated Backpacks**（否则背包界面都不会出现，自证）；
2. 服务端 **Core 未打来源槽校验补丁**；
3. 你打开了带过滤槽的升级页（拾取 / 过滤 / 磁铁 / 虚空 / 喂食），并把一个物品点进过滤槽作为样板。

一旦服务端打了补丁（入口与 `quickMoveStack` 各加一次 `IFilterSlot` / `mayPickup` 拒绝，见上游
PR #518 与 `sophisticated-filterslot-fix`），发包照旧但**收益为 0**：样板不动、库存不动。
这就是判断服务端是否已修复最简单的办法——拿个不值钱的物品按一次 K 看有没有 +1。

## 代码结构

```
1.20.1-forge/     Forge 47.x 版：SimpleChannel + TransferFullSlotMessage(int)
1.21.1-neoforge/  NeoForge 21.1.x 版：payload 体系 + TransferFullSlotPayload(int) record
docs/             两条工具链的差异、构建踩的坑、槽位号映射说明
```

两版的差异不只是包名，是整套网络栈不同：

| | 1.20.1 / Forge | 1.21.1 / NeoForge |
| --- | --- | --- |
| 消息类 | `TransferFullSlotMessage` (class) | `TransferFullSlotPayload` (record) |
| 发送 | `PacketHandler.INSTANCE.sendToServer(...)` | `PacketDistributor.sendToServer(...)` |
| 生产环境命名 | SRG（`m_38853_`），需 `reobfJar` | Mojang official，无需 reobf |
| 构建 | ForgeGradle 6 + `fg.deobf` | ModDevGradle 2 + `compileOnly files(...)` |

细节见 [docs/technical-notes.md](docs/technical-notes.md)。

## 构建

两个版本各自独立构建，注意 JDK 版本不同：

```powershell
# 1.20.1 —— 需要 JDK 17
cd 1.20.1-forge
# 把 Sophisticated Core 1.20.1 发行 jar 放到 libs/sophisticatedcore.jar（不随仓库分发）
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
gradle build

# 1.21.1 —— 需要 JDK 21
cd ..\1.21.1-neoforge
# 把 Sophisticated Core 1.21.1 发行 jar 放到 libs/ 并改名，或直接改 build.gradle 里的文件名
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
gradle build
```

Core 发行 jar 的 SHA-256（用于核对）：

| 版本 | 文件 | SHA-256 |
| --- | --- | --- |
| 1.20.1-1.5.17.2435 | `sophisticatedcore-1.20.1-1.5.17.2435.jar` | `943847f6b3b8d6038f0ae22291dd303bd2604c974a1830a664eb01ad0f8299ef` |
| 1.21.1-1.5.19.2434 | `sophisticatedcore-1.21.1-1.5.19.2434.jar` | `fe544662d105d9791c8a1db75f297351957d3e702546154f1493cc9869d4f2af` |

## 已验证

- 1.20.1 与 1.21.1 两版均在真实 Forge / NeoForge 工具链下编译通过，产物字节码用 `javap` 核对过：
  `upgradeSlots`、`IFilterSlot`、`TransferFullSlotMessage.<init>(I)` / `TransferFullSlotPayload.<init>(I)`、
  `sendToServer` 引用全部正确；1.20.1 产物已被 MC 命名重映射（`m_` / `f_`），1.21.1 产物保持 official 名。
- 1.20.1 版在单人游戏里实测可刷（发送 → 服务端合并 → 库存 +1，手持物品不变）。
- 单人游戏与远程专用服务器走**同一个**解码器与同一个 `handlePayload`，唯一差别是传输地址；
  因此单人跑通即证明载荷与服务端逻辑正确，远程服剩下的变量只有环境（版本、是否已修复、代理）。

## 许可

MIT。仓库不含 Sophisticated Core / Backpacks 的任何代码或二进制，只在其编译产物上引用公开类名。
