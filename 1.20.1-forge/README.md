# bpdupe — Sophisticated Backpacks 过滤槽整槽转移（1.20.1 / Forge）

客户端按键：把当前背包里过滤槽（ghost slot）的样板，通过 `sophisticatedcore:transfer_full_slot`
这条消息当普通物品来源交给服务端。未打补丁的 Sophisticated Core 会把它合并进真实库存，
于是**样板还在、库存多一份**。

- 适用：Minecraft **1.20.1** + **Forge 47.x** + Sophisticated Core 1.20.1（实测 `1.5.17.2435`）
  + Sophisticated Backpacks 1.20.1
- 只装客户端。服务端装不装这个 mod 都不影响（它不发任何自定义通道，复用的是 Core 的通道与协议号）。
- 默认键 **K**（可在「控制 → 背包过滤槽按键」里改）：一次把当前背包里所有装着样板的过滤槽各发一次
  `transfer_full_slot`，即每个槽 +1。
- **Shift+K 按住**：每 3 tick 重复一次。
- **Ctrl+K**：把剪贴板里的物品 ID（例 `minecraft:diamond`）写成幽灵样板
  （`set_ghost_slot`）再立刻转移，等于凭空造一个你从未拥有的物品。
  需要服务端注册了这条消息，即服务端也装了 JEI / EMI / REI。

## 原理（一句话）

`TransferFullSlotMessage.handleMessage` 只校验「玩家当前打开的菜单是不是 StorageContainerMenuBase」，
然后拿客户端给的槽位号直接 `quickMoveStack`；`quickMoveStack` 全程不问 `mayPickup()`，
过滤槽因此从「只能写、不能取」的样板格变成了真实物品来源。

## 构建

需要 JDK 17（ForgeGradle 6 只吃 17）。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
gradle build            # 或 .\gradlew build
```

产物：`build/libs/bpdupe-1.20.1-1.0.0.jar`

`libs/sophisticatedcore.jar` 是编译期的 Core 1.20.1 发行 jar（Modrinth `sophisticated-core`
版本 `1.20.1-1.5.17.2435`，SHA-256 `943847f6b3b8d6038f0ae22291dd303bd2604c974a1830a664eb01ad0f8299ef`），
用 `fg.deobf` 做 SRG→official 重映射；产物由 `reobfJar` 映射回 SRG，所以能直接进生产环境。

## 用法

1. 背包里装任意带过滤槽的升级（拾取 / 过滤 / 磁铁 / 虚空 / 喂食）。
2. 打开该升级的设置页，把一个物品点进过滤槽（样板，不消耗）。
3. 按 K。聊天栏会显示 `sent transfer_full_slot xN`，背包库存 +N，手上的物品一个不少。
4. 想批量：按住 Shift+K。

## 说明

- 一次发包 = 一个过滤槽 +1（样板数量是 1 时）。要更快就多装几个带过滤槽的升级，一次发多个槽。
- 只对「还没打补丁」的 Core 有效。补丁长这样（上游 PR #518 / `sophisticated-filterslot-fix`）：
  入口和 `quickMoveStack` 各加一次 `IFilterSlot` / `mayPickup` 拒绝，这套就整条失效。
- 服务端如果开着会记录未知/异常 payload 的插件或反作弊，包内容本身是合法消息（stock 客户端也在发），
  异常点只在「槽位号指向了过滤槽」这一件事上。
