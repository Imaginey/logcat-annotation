# OperLog 跨 Android 平台方法日志注解框架架构设计

## 1. 项目目标

设计并实现一个可在多个 Android / 车机项目复用的统一方法日志注解框架：

```kotlin
@OperLog
```

要求业务开发者只需要在普通 Java/Kotlin 方法上声明：

```kotlin
@OperLog
fun connectBluetooth(
    address: String,
    retryCount: Int
) {
    // business code
}
```

即可自动输出：

```text
D/OperLog: → ENTER BluetoothController#connectBluetooth
D/OperLog:   thread=main
D/OperLog:   args: address="AA:BB:CC:DD:EE:FF", retryCount=3
D/OperLog: ← EXIT BluetoothController#connectBluetooth cost=12.43ms
```

异常情况下：

```text
D/OperLog: → ENTER BluetoothController#connectBluetooth
D/OperLog:   args: address="...", retryCount=3
E/OperLog: ✕ ERROR BluetoothController#connectBluetooth cost=7.21ms
E/OperLog:   exception=java.lang.IllegalStateException: bluetooth unavailable
```

---

# 2. 新版核心设计目标

本框架不是为单一 Android 项目设计。

必须满足：

```text
Android 11 / API 30
Android 14 / API 34
Android 15 / API 35
```

以及不同项目可能存在的不同：

```text
AGP
Gradle
JDK
Kotlin
Groovy DSL
Kotlin DSL
```

因此必须遵循：

> Android Runtime 兼容能力和 Build Tool 兼容能力分离。

禁止为了接入 OperLog 强制业务项目统一升级：

```text
Android Platform
AGP
Gradle
JDK
Kotlin
```

OperLog 应主动适配业务项目现有工具链。

---

# 3. 最核心架构原则

整体分为两个世界：

```text
                 OperLog Framework

        ┌────────────────┴────────────────┐
        │                                 │
        ↓                                 ↓

     Build Side                       Runtime Side

Gradle Plugin                       Runtime Library
AGP Adapter                         android.util.Log
ASM Core                            Thread
Gradle                              System.nanoTime
JDK                                 Formatter

        │                                 │
        ↓                                 ↓

受 AGP/Gradle/JDK 影响           受 Android API Level 影响
```

必须分别治理：

```text
Build Compatibility
Runtime Compatibility
```

---

# 4. Android 版本兼容策略

OperLog Runtime 必须使用尽可能基础、稳定的 API。

建议只依赖：

```kotlin
android.util.Log
Thread.currentThread()
System.nanoTime()
String
Array
Collection
Map
Throwable
```

不要依赖：

```text
Android 14 新 API
Android 15 新 API
Hidden API
系统 Framework 内部 API
厂商私有 API
```

因此同一套 Runtime 可以运行在：

```text
Android 11
Android 12
Android 13
Android 14
Android 15
```

建议：

```kotlin
minSdk = 30
```

如果未来有需要，也可以进一步降到更低 API。

---

# 5. 不要按 Android 版本拆 Runtime

禁止设计：

```text
oper-log-android11
oper-log-android14
oper-log-android15
```

因为日志 Runtime 本身没有必要按 Android 系统版本拆分。

正确方式：

```text
oper-log-runtime
```

一套 Runtime 支持：

```text
API 30 ~ API 35
```

真正需要适配的是：

```text
AGP / Gradle / JDK
```

---

# 6. 最终模块架构

建议正式拆分为：

```text
oper-log/

├── oper-log-annotation
│
│   ├── @OperLog
│   └── @OperLogIgnore
│
├── oper-log-runtime
│
│   ├── Runtime
│   ├── Formatter
│   ├── Printer
│   ├── Config
│   └── SensitiveValuePolicy
│
├── oper-log-bytecode-core
│
│   ├── ASM 核心
│   ├── ClassVisitor
│   ├── MethodVisitor
│   ├── MethodMetadata
│   ├── AnnotationParser
│   └── ClassFilter
│
├── oper-log-gradle-plugin
│
│   ├── Plugin Entry
│   ├── Extension
│   ├── Compatibility Detection
│   └── Adapter Routing
│
├── oper-log-agp-legacy
│
│   └── 老 AGP 接入适配
│
├── oper-log-agp-modern
│
│   └── 新 AGP Instrumentation API 适配
│
└── samples/
    │
    ├── sample-android11
    ├── sample-android14
    └── sample-android15
```

---

# 7. 模块依赖方向

推荐：

```text
annotation
    ↑
    │
bytecode-core

runtime
    ↑
    │
business app


bytecode-core
    ↑
    │
agp-legacy
agp-modern

agp-legacy / agp-modern
    ↑
    │
gradle-plugin
```

关键原则：

> ASM 核心不能依赖具体 AGP API。

否则一旦 AGP API 变化，整个字节码框架都要重写。

---

# 8. Annotation 模块

模块：

```text
oper-log-annotation
```

建议保持：

```text
纯 Kotlin/JVM
无 android.*
无 AGP
无 Gradle
```

定义：

```kotlin
@Target(
    AnnotationTarget.FUNCTION
)
@Retention(
    AnnotationRetention.BINARY
)
annotation class OperLog(
    val tag: String = "",
    val printArgs: Boolean = true,
    val printResult: Boolean = false,
    val printThread: Boolean = true,
    val measureTime: Boolean = true
)
```

敏感参数：

```kotlin
@Target(
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(
    AnnotationRetention.BINARY
)
annotation class OperLogIgnore
```

---

# 9. 为什么 AnnotationRetention 使用 BINARY

处理链：

```text
.kt / .java
    ↓
.class
    ↓
ASM 读取 Annotation
    ↓
插桩完成
    ↓
Runtime 不需要再反射读取
```

因此：

```kotlin
AnnotationRetention.BINARY
```

即可。

---

# 10. Runtime 模块

模块：

```text
oper-log-runtime
```

建议类：

```text
OperLogRuntime
OperLogConfig
OperLogFormatter
OperLogPrinter
SensitiveValuePolicy
```

调用关系：

```text
ASM injected code
       ↓
OperLogRuntime
       ↓
OperLogFormatter
       ↓
SensitiveValuePolicy
       ↓
OperLogPrinter
       ↓
android.util.Log
```

---

# 11. Runtime API

建议：

```kotlin
object OperLogRuntime {

    @JvmStatic
    fun enter(
        className: String,
        methodName: String,
        tag: String?,
        parameterNames: Array<String>?,
        parameterValues: Array<Any?>?,
        ignoredParameterIndexes: IntArray?,
        printArgs: Boolean,
        printThread: Boolean
    ): Long

    @JvmStatic
    fun exit(
        className: String,
        methodName: String,
        tag: String?,
        startTime: Long,
        result: Any?,
        printResult: Boolean,
        measureTime: Boolean
    )

    @JvmStatic
    fun error(
        className: String,
        methodName: String,
        tag: String?,
        startTime: Long,
        throwable: Throwable,
        measureTime: Boolean
    )
}
```

---

# 12. Runtime Android 兼容约束

Runtime 禁止主动使用：

```text
Build.VERSION >= 某新版本
Android 14 专有类
Android 15 专有类
Hidden API
Vendor private API
```

除非后续某功能明确需要。

第一版要求：

```text
API 30 ~ 35
```

同一 AAR 直接运行。

---

# 13. Runtime 日志格式

进入：

```text
D/OperLog: → ENTER BluetoothController#connectBluetooth
D/OperLog:   thread=main
D/OperLog:   args: address="AA:BB:...", retryCount=3
```

正常退出：

```text
D/OperLog: ← EXIT BluetoothController#connectBluetooth cost=12.43ms
```

异常：

```text
E/OperLog: ✕ ERROR BluetoothController#connectBluetooth cost=7.21ms
E/OperLog:   exception=IllegalStateException: bluetooth unavailable
```

---

# 14. 参数格式化

`OperLogFormatter` 负责：

```text
null
Boolean
Byte
Short
Int
Long
Float
Double
Char
String
Enum
Array
Collection
Map
普通 Object
```

参数名拿不到时：

```text
arg0
arg1
arg2
```

不能导致构建失败。

---

# 15. 参数输出安全策略

必须限制：

```text
最大字符串长度
最大 Collection 数量
最大 Array 数量
最大 Map 数量
```

例如：

```kotlin
MAX_VALUE_LENGTH = 500
MAX_COLLECTION_SIZE = 20
MAX_ARRAY_SIZE = 20
MAX_MAP_SIZE = 20
```

超限：

```text
...[truncated]
```

---

# 16. 敏感字段

支持：

```kotlin
@OperLog
fun login(
    account: String,
    @OperLogIgnore password: String
)
```

输出：

```text
account="user01", password=***
```

第一版至少支持：

```text
完全隐藏
```

后续 V2 可支持：

```text
掩码
Hash
自定义 Formatter
```

---

# 17. Bytecode Core

模块：

```text
oper-log-bytecode-core
```

这是整个框架真正的核心。

必须做到：

> 完全不知道当前项目是 Android 11、14、15，也不知道使用哪个 AGP。

它只接收：

```text
Class Bytes
```

输出：

```text
Modified Class Bytes
```

概念：

```text
byte[] input
    ↓
OperLogBytecodeTransformer
    ↓
ASM
    ↓
byte[] output
```

---

# 18. Bytecode Core 负责内容

```text
识别 @OperLog
解析 Annotation 参数
识别 Method
读取 Method descriptor
解析参数
生成参数数组
基本类型 Boxing
插入 ENTER
插入 EXIT
插入 ERROR
处理多 RETURN
处理 Throwable
Class Filter
Method Filter
```

这些逻辑只维护一份。

---

# 19. ASM 核心结构

```text
OperLogBytecodeTransformer
          ↓
OperLogClassVisitor
          ↓
visitMethod()
          ↓
OperLogMethodVisitor
          ↓
AdviceAdapter
```

---

# 20. 插桩逻辑

源码：

```kotlin
@OperLog
fun connect(
    address: String,
    retryCount: Int
): Boolean {

    return doConnect(
        address,
        retryCount
    )
}
```

插桩概念：

```kotlin
fun connect(
    address: String,
    retryCount: Int
): Boolean {

    val __start =
        OperLogRuntime.enter(
            className = "BluetoothController",
            methodName = "connect",
            ...
        )

    try {

        val result =
            doConnect(
                address,
                retryCount
            )

        OperLogRuntime.exit(
            ...,
            result
        )

        return result

    } catch (t: Throwable) {

        OperLogRuntime.error(
            ...,
            t
        )

        throw t
    }
}
```

---

# 21. Bytecode Version 兼容原则

禁止 Bytecode Core 主动修改：

```text
Class version
Target JVM version
Source compatibility
```

原则：

```text
输入 Class 是 Java 8 bytecode
↓
输出仍然 Java 8 bytecode

输入 Class 是 Java 17 bytecode
↓
输出仍然保持原版本
```

ASM 只修改方法指令。

---

# 22. JVM 参数处理

必须处理：

```text
ILOAD
LLOAD
FLOAD
DLOAD
ALOAD
```

Primitive 需要 boxing：

```text
int → Integer
boolean → Boolean
long → Long
float → Float
double → Double
```

统一传：

```text
Object[]
```

---

# 23. Instance / Static

实例方法：

```text
slot 0 = this
```

static：

```text
slot 0 = arg0
```

Long / Double：

```text
占 2 个 slot
```

禁止手写固定 index。

推荐使用：

```text
ASM Type
AdviceAdapter.loadArg()
```

---

# 24. 正常退出

覆盖所有：

```text
RETURN
IRETURN
LRETURN
FRETURN
DRETURN
ARETURN
```

不能只处理 Void。

---

# 25. 异常退出

不能只监听：

```text
ATHROW
```

必须覆盖：

```text
被调用方法抛异常并向上传播
```

因此需要：

```text
method-wide catch Throwable
```

概念：

```text
try {
    原方法
} catch (Throwable t) {
    Runtime.error(...)
    throw t
}
```

保证：

```text
ENTER + EXIT
```

或者：

```text
ENTER + ERROR
```

不能重复。

---

# 26. V1 方法过滤

第一版跳过：

```text
<init>
<clinit>
abstract
native
synthetic
bridge
```

Kotlin 生成：

```text
$default
access$
$annotations
lambda$
```

另外 V1 明确不承诺完整支持：

```text
suspend
inline
```

---

# 27. suspend 处理策略

第一版：

```text
检测 suspend 编译特征
↓
跳过
↓
Build Warning
```

原因：

```text
Coroutine State Machine
COROUTINE_SUSPENDED
resume
```

普通方法耗时统计会错误。

---

# 28. Build Compatibility Adapter

真正需要区分版本的地方：

```text
AGP Integration
```

因此设计：

```text
                 Bytecode Core
                      ↑
             ┌────────┴────────┐
             │                 │
             ↓                 ↓

     AGP Legacy Adapter   AGP Modern Adapter
```

两边最终都只做：

```text
拿到 class
↓
调用同一个 ASM Core
↓
返回修改后的 class
```

---

# 29. Legacy Adapter

模块：

```text
oper-log-agp-legacy
```

职责：

```text
适配旧 AGP 构建入口
将 class/jar 输入交给 bytecode-core
返回插桩产物
```

注意：

具体支持哪些 AGP 范围必须基于实际内部项目环境确定。

不要在架构里假设：

```text
所有 Android 11 都是 AGP 4.x
```

因为 Android Platform 和 AGP 没有一一对应关系。

---

# 30. Modern Adapter

模块：

```text
oper-log-agp-modern
```

使用适用于当前项目 AGP 的：

```text
Android Components API
Instrumentation API
AsmClassVisitorFactory
```

只负责接入。

真正：

```text
Annotation Parsing
Method Instrumentation
Parameter Handling
```

仍由 bytecode-core 完成。

---

# 31. Gradle Plugin

模块：

```text
oper-log-gradle-plugin
```

职责：

```text
读取 Extension
检测 Android Plugin
检测 AGP 能力
选择 Adapter
传递配置
注册插桩
输出兼容性日志
```

禁止在这里实现 ASM 业务逻辑。

---

# 32. Plugin 使用方式

业务项目：

```kotlin
plugins {
    id("com.neusoft.oper-log")
}
```

配置：

```kotlin
neusoftLog {

    enabled = true

    enableInRelease = false

    includePackages = listOf(
        "com.neusoft"
    )

    excludePackages = listOf(
        "com.neusoft.generated"
    )

    printArgs = true

    printThread = true

    printResult = false

    measureTime = true
}
```

---

# 33. Adapter 自动选择

理想：

```text
Plugin 启动
  ↓
检测当前 AGP/Android Gradle 能力
  ↓
Modern API 可用？
  │
  ├─ YES → Modern Adapter
  │
  └─ NO  → Legacy Adapter
```

如果当前构建环境不在支持矩阵：

```text
构建阶段直接给出清晰错误
```

例如：

```text
OperLog does not currently support AGP x.y.z.

Detected:
AGP: x.y.z
Gradle: x.y
JDK: x

Supported:
...

Please use compatible OperLog adapter version.
```

禁止：

```text
NoSuchMethodError
ClassNotFoundException
模糊 Gradle Crash
```

---

# 34. 不强制统一 JDK

框架原则：

> OperLog 不应该为了自身接入要求所有业务项目升级到统一 JDK。

但必须承认：

```text
某个 AGP 本身会要求某个 JDK
```

这是 Android 构建工具链约束，框架无法消除。

正确方式：

```text
OperLog Adapter
↓
跟随该 AGP 自身要求
```

而不是：

```text
OperLog 强制业务工程升级
```

---

# 35. Compatibility Matrix

项目必须维护：

```text
docs/compatibility.md
```

例如：

| OperLog | Adapter | AGP Range | JDK | Android Runtime |
|---|---|---|---|---|
| 1.x | Legacy | 待内部验证 | 跟随 AGP | API 30~35 |
| 1.x | Modern | 待内部验证 | 跟随 AGP | API 30~35 |

注意：

> 不要在未验证前随意声称具体 AGP/JDK 范围。

由自动化 Sample/CI 验证后填写真实矩阵。

---

# 36. Sample Matrix

至少建立三个验证项目：

```text
sample-android11
sample-android14
sample-android15
```

但 Sample 的意义是：

```text
验证 Runtime Platform
```

还应该额外建立 Build Matrix：

```text
legacy build sample
modern build sample
```

因为：

```text
Android Version != AGP Version
```

---

# 37. 推荐测试矩阵

维度一：

```text
Android Runtime

API 30
API 34
API 35
```

维度二：

```text
AGP Adapter

Legacy
Modern
```

维度三：

```text
Language

Java
Kotlin
```

维度四：

```text
Method Type

Unit/Void
primitive return
object return
private
public
static
multiple return
exception
nested call
```

---

# 38. 核心测试

## Java

```java
@OperLog
public void test(String name, int count) {
}
```

## Kotlin

```kotlin
@OperLog
fun test(
    name: String,
    count: Int
) {
}
```

必须输出一致格式。

---

# 39. 多项目一致体验

Android 11：

```kotlin
@OperLog
fun updateHvacState(state: Int)
```

Android 14：

```kotlin
@OperLog
fun updateHvacState(state: Int)
```

Android 15：

```kotlin
@OperLog
fun updateHvacState(state: Int)
```

业务代码使用方式必须完全一致。

底层：

```text
不同 Build Adapter
```

对业务开发者透明。

---

# 40. Class Filter

默认只处理：

```text
includePackages
```

排除：

```text
android.*
androidx.*
java.*
javax.*
kotlin.*
kotlinx.*
dagger.*
hilt.*
```

生成类：

```text
R.class
R$*.class
BuildConfig.class
*Binding.class
*_Factory.class
*_Impl.class
Hilt_*.class
Dagger*.class
```

---

# 41. Debug / Release

支持：

```text
Debug
↓
默认启用

Release
↓
默认关闭
```

最好在 Release 关闭时：

```text
完全不插桩
```

而不是：

```text
插桩后 Runtime 判断 enabled=false
```

这样 Release 无额外方法调用成本。

---

# 42. 编译性能

必须遵循：

```text
只扫描 includePackages
只处理 Project Scope 为默认
只对有 @OperLog 的方法插桩
```

不要默认处理所有第三方库。

后续若需要：

```text
ALL scope
```

再单独支持。

---

# 43. Bytecode Core 单元测试

核心 ASM 不依赖 Android，因此应该有纯 JVM 测试。

测试：

```text
输入 class bytes
↓
transform
↓
输出 class bytes
↓
ASM CheckClassAdapter
↓
验证字节码合法
```

重点覆盖：

```text
primitive args
object args
multiple return
exception
static
private
long/double local slots
```

---

# 44. Adapter 集成测试

Legacy：

```text
真实旧工程 Build
```

Modern：

```text
真实新工程 Build
```

验证：

```text
assembleDebug
assembleRelease
incremental build
clean build
```

---

# 45. Runtime 设备测试

API 30：

```text
安装 APK
执行注解方法
验证 Logcat
```

API 34、35 同样。

---

# 46. 版本发布策略

不建议：

```text
一个 Plugin jar 永远兼容全部历史 AGP
```

更实际的是：

```text
OperLog API 保持稳定
Bytecode Core 保持稳定
Runtime 保持稳定
Adapter 可独立演进
```

例如：

```text
annotation 1.0
runtime 1.0
bytecode-core 1.0

plugin/adapter 按兼容性升级
```

---

# 47. API 稳定性原则

业务端：

```kotlin
@OperLog
```

必须长期稳定。

即使：

```text
AGP 4
→ AGP 7
→ AGP 8
→ 后续 AGP
```

发生变化：

```text
业务源码不需要改
```

只升级：

```text
OperLog Plugin / Adapter
```

---

# 48. 最终开发架构图

```text
                         Business Code

                         @OperLog
                              │
                              ↓
                      Annotation Module
                              │
                              ↓
                           .class
                              │
                  ┌───────────┴───────────┐
                  │                       │
                  ↓                       ↓

          AGP Legacy Adapter      AGP Modern Adapter
                  │                       │
                  └───────────┬───────────┘
                              ↓
                  OperLog Bytecode Core
                              │
                              ↓
                         ASM Transform
                              │
                ┌─────────────┼─────────────┐
                ↓             ↓             ↓
              ENTER         RETURN        ERROR
                │             │             │
                └─────────────┼─────────────┘
                              ↓
                    Modified Class Bytes
                              ↓
                          D8 / R8
                              ↓
                             DEX
                              ↓
                             APK
                              ↓
                   OperLog Runtime
                              ↓
                     Formatter / Mask
                              ↓
                           Logcat
```

---

# 49. Codex / AI 实现要求

实现前必须首先扫描当前目标仓库：

```text
gradle-wrapper.properties
settings.gradle / settings.gradle.kts
build.gradle / build.gradle.kts
libs.versions.toml
gradle.properties
```

获取：

```text
Android Gradle Plugin
Gradle
JDK
Kotlin
compileSdk
minSdk
targetSdk
Groovy/Kotlin DSL
```

但注意：

> 这些信息只用于确定当前仓库应该使用哪个 Adapter，不允许据此把整个 OperLog 核心绑定死。

---

# 50. 实现顺序

## Phase 1

```text
annotation
runtime
bytecode-core
```

纯 JVM 验证 ASM。

## Phase 2

```text
Modern AGP Adapter
```

在当前新项目跑通。

## Phase 3

```text
Legacy Adapter
```

在旧项目验证。

## Phase 4

```text
Unified Gradle Plugin
Adapter Routing
```

## Phase 5

```text
参数打印
Formatter
敏感参数
```

## Phase 6

```text
EXIT
ERROR
duration
```

## Phase 7

```text
Android 11 / 14 / 15 Sample
```

## Phase 8

```text
Compatibility Matrix
CI
Release
```

---

# 51. 禁止项

禁止：

```text
❌ 把所有 ASM 逻辑写进 AGP Plugin
❌ 按 Android 11/14/15 分别维护三套 Runtime
❌ 为了 OperLog 强制所有业务项目升级 JDK
❌ 为了 OperLog 强制所有业务项目升级 AGP
❌ 把 Android Platform Version 当成 AGP Version
❌ 用 Runtime Reflection 扫描每次方法调用
❌ 用 Java Proxy 作为核心
❌ 每个 Adapter 各维护一套 ASM 插桩逻辑
❌ 修改原始 Class bytecode version
❌ 吞掉业务异常
❌ 改变原方法返回值
```

---

# 52. 最终验收标准

必须满足：

1. Android 11 可运行。
2. Android 14 可运行。
3. Android 15 可运行。
4. 三个平台业务使用方式一致。
5. Annotation Module 不依赖 Android。
6. Runtime 不依赖 AGP。
7. ASM Core 不依赖 AGP。
8. Legacy/Modern Adapter 只负责构建接入。
9. ASM 插桩逻辑只有一套。
10. 普通 Kotlin 方法可用。
11. 普通 Java 方法可用。
12. 参数可打印。
13. 敏感参数可隐藏。
14. 多 return 正确。
15. 异常退出正确。
16. 原异常继续抛出。
17. 返回值不改变。
18. Debug 可开启。
19. Release 可完全关闭插桩。
20. 不需要 Proxy。
21. 不需要 BaseClass。
22. 不要求业务开发者理解 AGP Adapter。
23. 不要求所有项目统一 JDK。
24. 不要求所有项目统一 AGP。
25. Compatibility Matrix 有真实测试依据。

---

# 53. 给实现 AI / Codex 的核心 Prompt

请实现一个公司级 Android 方法日志注解框架 `OperLog`。

业务目标：

```kotlin
@OperLog
fun connectBluetooth(
    address: String,
    retryCount: Int
) {
}
```

自动输出：

```text
→ ENTER BluetoothController#connectBluetooth
  thread=main
  args: address="...", retryCount=3

← EXIT BluetoothController#connectBluetooth cost=xx.xxms
```

异常：

```text
✕ ERROR BluetoothController#connectBluetooth cost=xx.xxms
exception=...
```

架构必须支持 Android 11/API30、Android 14/API34、Android 15/API35。

不要把 Android Platform Version 和 AGP Version 绑定。

框架必须拆分：

```text
oper-log-annotation
oper-log-runtime
oper-log-bytecode-core
oper-log-gradle-plugin
oper-log-agp-legacy
oper-log-agp-modern
samples
```

核心原则：

```text
Annotation
Runtime
ASM Core
AGP Adapter
```

必须解耦。

ASM Core 只能维护一套。

AGP Legacy/Modern Adapter 只负责把编译产物交给 ASM Core，不允许各自重新实现插桩规则。

Runtime 必须使用基础 Android/Java API，保证 API30~35 使用同一实现。

不允许为了接入 OperLog 强制业务工程升级 Android Platform、AGP、Gradle、JDK 或 Kotlin。

但 Adapter 可以跟随当前业务项目 AGP 自身要求的 JDK。

实现前必须分析当前仓库的：

```text
AGP
Gradle
JDK
Kotlin
compileSdk
minSdk
targetSdk
DSL
```

然后选择适配方案。

技术核心：

```text
Custom Annotation
+
Gradle Plugin
+
AGP Compatibility Adapter
+
ASM
+
AdviceAdapter
+
Runtime Logger
```

不要使用 Runtime Proxy 作为核心。

支持：

```text
Java
Kotlin
method arguments
thread
duration
normal exit
exception exit
sensitive parameter ignore
Debug/Release
include/exclude
```

第一版跳过：

```text
constructor
class initializer
abstract
native
synthetic
bridge
suspend
inline 特殊场景
```

异常处理不能只监听 ATHROW，需要覆盖调用链异常向上传播情况。

原 Throwable 必须重新抛出。

不允许改变业务方法返回值。

业务层长期只感知：

```kotlin
@OperLog
```

未来即使 AGP Adapter 升级，业务源码也不应该修改。

最终输出：

1. 总体架构
2. 模块说明
3. 兼容性设计
4. Adapter 策略
5. ASM 插桩流程
6. 参数读取
7. 异常处理
8. Runtime API
9. Gradle 配置
10. Compatibility Matrix
11. Android 11/14/15 Sample
12. 测试结果
13. 当前限制
14. 后续规划

---

# 54. 最终结论

OperLog 应该被设计成：

> 一套业务 API + 一套 Runtime + 一套 ASM Core + 多个 Build Adapter。

而不是：

> 一个只能在某个 AGP/JDK/Android 版本项目里工作的 Gradle 小插件。

最终目标：

```text
Android 11 项目
Android 14 项目
Android 15 项目

       ↓

全部统一写：

@OperLog

       ↓

构建阶段由对应 Adapter 接入

       ↓

统一 ASM Core 插桩

       ↓

统一 Runtime 输出日志
```

这样才能真正成为多个 Neusoft Android / 车机项目可复用的基础日志能力。
