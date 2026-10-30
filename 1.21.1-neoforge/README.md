# bpdupe1211 — Sophisticated Backpacks 过滤槽整槽转移（1.21.1 / NeoForge）

客户端按键：把当前背包过滤槽（ghost slot）的样板，用 `sophisticatedcore:transfer_full_slot`
这一条**合法模组消息**交给服务端处理。未打补丁的 Sophisticated Core 会把它当普通物品来源合并进真实库存，
于是样板被清空、库存多一份。

- 适用：Minecraft **1.21.1** + **NeoForge 21.1.x** + Sophisticated Core 1.21.1（编译对照 `1.5.19.2434`）
  + Sophisticated Backpacks 1.21.1
- 只装客户端；服务端不需要这个 mod（它不注册任何通道，复用的是 Core 自己的通道 + Core 的协议号）
- 默认键 **K**（可在「控制 → 背包过滤槽按键」改）
- 与 1.20.1 版的差异：这里走 NeoForge 的 payload 体系（`PacketDistributor.sendToServer` +
  `TransferFullSlotPayload` record），1.20.1 那边是 Forge `SimpleChannel` + `TransferFullSlotMessage`

## 按键

| 组合 | 行为 |
|---|---|
| **K** | 当前菜单里每个「装着样板的过滤槽」各发一次 `transfer_full_slot` → 每个槽 +1 |
| **Shift+K**（按住） | 每 3 tick 重复一次 |
| **Ctrl+K** | 把剪贴板里的物品 ID（例 `minecraft:diamond`）写成幽灵样板（`set_ghost_slot`）并立刻转移 → 凭空造物。要求服务端注册了这条消息（服务端装 JEI/EMI/REI） |

按键是裸轮询 GLFW 的：原版只在没有 GUI 打开时喂 `KeyMapping`，普通绑定在容器界面里收不到事件。
绑定的键仍然从 `KeyMapping.getKey()` 读回来，所以改键有效。

## 构建

JDK 21 + Gradle 8.10+ + ModDevGradle 2.0.x（`net.neoforged.moddev`）。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
gradle build
```

产物：`build/libs/bpdupe-1.21.1-1.0.0.jar`

`libs/sophisticatedcore-1.21.1-1.5.19.2434.jar` 是编译期依赖（Modrinth 发行版，
SHA-256 `fe544662d105d9791c8a1db75f297351957d3e702546154f1493cc9869d4f2af`）。
NeoForge 1.20.2 之后生产环境就是 Mojang official 名，所以**不需要** ForgeGradle 那套 deobf/reobf，
用 `compileOnly files(...)` 直接编即可。

## 前提与失效条件

能刷的三个前提：服务端装着 Sophisticated Core + Backpacks（否则背包界面都不会出现）、
Core 还没打来源槽校验补丁、你打开了带过滤槽的升级页并放了样板。

一旦服务端打了补丁（上游 PR #518 的两处 `IFilterSlot` / `mayPickup` 拒绝，或
`sophisticated-filterslot-fix` 的修复 jar），发包照旧但收益为 0：样板不动、库存不动。
这就是判断服务端是否已修复的简单方法——拿一个不值钱的物品按一次 K 看有没有 +1。
