# 两条工具链的差异与踩过的坑

## 1. 同名不同物：`TransferFullSlotMessage` vs `TransferFullSlotPayload`

同一个缺陷在两条 MC 线上是两套实现，不能互相移植：

| | 1.20.x（Forge） | 1.21.x（NeoForge） |
| --- | --- | --- |
| 类 | `net.p3pp3rf1y.sophisticatedcore.network.TransferFullSlotMessage` | `...network.TransferFullSlotPayload` |
| 形态 | 普通 class，`encode` / `decode` / `onMessage` | `record (int slotId)`，`STREAM_CODEC` |
| 注册 | `PacketHandler.INSTANCE.registerMessage(...)`（Forge `SimpleChannel`） | `registrar.playToServer(TYPE, STREAM_CODEC, ::handlePayload)` |
| 发送 | `PacketHandler.INSTANCE.sendToServer(msg)` | `PacketDistributor.sendToServer(payload)` |
| 幽灵槽消息 | `compat.recipeviewers.common.SetGhostSlotMessage(ItemStack,int)` | `...SetGhostSlotPayload(ItemStack,int)` |
| 生产命名 | SRG（`getSlot` → `m_38853_`） | Mojang official（`getSlot` 原名） |

1.20.1 的生产字节码里，服务端处理器长这样（`javap -c`）：

```
invokevirtual  StorageContainerMenuBase.m_38853_:(I)Lnet/minecraft/world/inventory/Slot;   // getSlot
invokevirtual  StorageContainerMenuBase.m_7648_:(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;  // quickMoveStack
…
// 整段没有 mayPickup，没有 IFilterSlot 判断
```

1.21.1 的 `1.21.x` 分支源码到本文写作时仍是同一段逻辑。

## 2. 为什么 1.20.1 必须 `fg.deobf` + `reobfJar`，1.21.1 不用

- **Forge 1.20.1**：生产环境是 SRG 命名，发行 mod jar 里 MC 成员都是 `m_` / `f_`。编译期必须
  `fg.deobf(files('libs/core.jar'))` 把依赖重映射到 official，产物再由 `reobfJar` 映射回 SRG。
  直接 `compileOnly files(...)` 能编过一部分，但运行时 `NoSuchMethodError`。
- **NeoForge 1.20.2+**：生产环境就是 Mojang official 名，不需要 reobf。所以 1.21.1 版
  `compileOnly files(...)` 直接编，产物即成品。
- 代价：1.20.1 版只能用 ForgeGradle（Gradle 8.1 + JDK 17），1.21.1 版用 ModDevGradle 2（Gradle 8.10+ + JDK 21）。

## 3. `fg.deobf` 不能配 flatDir 字符串

失败写法：

```groovy
compileOnly fg.deobf('local:sophisticatedcore-1.20.1-1.5.17.2435')   // → Could not find local:…:null_mapped_official_1.20.1
```

可用写法：

```groovy
compileOnly fg.deobf(files('libs/sophisticatedcore.jar'))
```

## 4. `javac` 的可见性

`ClientSetup` 被另一个包（`local.bpdupe`）引用，必须是 `public`。第一次构建就败在这里：
`ClientSetup 在 local.bpdupe.client 中不是公共的`。

## 5. 构建卡死 20 分钟以上：Java 不读系统代理

本机 Windows Internet 设置里有 `127.0.0.1:7890` 代理，`Invoke-WebRequest` 走它，所以能下载；
但 JVM 完全不读这个设置，直连 `maven.neoforged.net` 的 A 记录（当时是不可达的 CDN 边缘），
连接停在 `SYN_SENT`，Gradle 默认又没有连接超时，于是 `:createMinecraftArtifacts` 挂了 37 分钟才报错。

修法（`gradle.properties`）：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
systemProp.http.nonProxyHosts=localhost|127.*|[::1]
systemProp.org.gradle.internal.http.connectionTimeout=20000
systemProp.org.gradle.internal.http.socketTimeout=120000
```

注意 `neoform-runtime` 是 ModDevGradle **另外派生的子 JVM**，它不继承 Gradle 的 system properties，
要另外给环境变量：

```
JAVA_TOOL_OPTIONS=-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890
```

配好之后 `:createMinecraftArtifacts` 4 分钟跑完。

## 6. 槽位号从哪来

`StorageContainerMenuBase` 把升级槽保存在单独的 `upgradeSlots` 列表里，添加时执行
`slot.index = getTotalSlotsNumber()`，因此它们的索引**排在 `slots` 列表之外**（例如 156 个普通槽之后）。
读取时 `getSlot(id)` 被重写：`id >= getInventorySlotsSize()` 时取 `upgradeSlots.get(id - size)`。

所以按键模组直接遍历 `menu.upgradeSlots`、用 `slot.index` 发消息即可，不需要自己算偏移；
这也解释了为什么 `slotId = -1` 会抛 `IndexOutOfBoundsException`（长度是 `slots.size()`），
而 `slotId = 164` 在 156 个普通槽的菜单里指向 `upgradeSlots[8]`。

## 7. 为什么按键要裸轮询 GLFW

原版 `KeyboardHandler.keyPress` 只在没有 GUI 打开时更新 `KeyMapping` 的按下状态，所以在背包界面里
`KeyMapping.isDown()` / `consumeClick()` 永远拿不到事件。可行的做法是拿 `KeyMapping.getKey().getValue()`
读回当前绑定的键码，再用 `InputConstants.isKeyDown(windowHandle, keyCode)` 自己轮询。
