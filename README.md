# OperLog 跨平台方法日志注解框架 - 开发者接入与 API 使用文档 (Kotlin & Java)

> **文档版本**：V1.0.0  
> **适用语言**：Kotlin & Java 混合工程 / 纯 Java 工程 / 纯 Kotlin 工程  
> **适用对象**：Android 业务开发工程师、SDK 开发工程师  
> **环境要求**：JDK 8+、Android API 30+ (Android 11 ~ 15)、Gradle 7.x / 8.x

---

## 一、框架简介

`OperLog` 是公司级独立的 Android 跨平台切面日志注解框架。通过在方法上标注 `@OperLog` 注解，框架在**编译期**利用 ASM 字节码插桩技术自动注入日志代码，实现**零业务侵入**的方法执行追踪。完美支持 **Kotlin** 与 **Java** 源码。

### 核心功能
- **全自动方法追踪**：自动记录方法进入（`ENTER`）、正常退出（`EXIT`）及抛出异常（`ERROR`）。
- **性能耗时统计**：高精度测量方法执行耗时（毫秒级，保留两位小数）。
- **线程追踪**：自动获取并打印当前执行线程名称。
- **敏感参数脱敏**：配合 `@OperLogIgnore` 注解，自动将敏感参数（如密码、秘钥、身份证号）替换为 `***`，防止日志泄漏隐私。
- **大对象与防爆内存截断**：内置参数与返回值长度截断机制，防止巨型字符串或集合引发 OOM。
- **Release 零开销**：支持编译期关闭 Release 变体插桩，生产包零运行时开销。

---

## 二、接入方式一：Maven / 私服仓库接入（强烈推荐 - 全自动）

此方式适用于已将框架发布到公司 Nexus 私服或本地 Maven 仓库（`mavenLocal`）的场景，无需手动拷贝任何文件。

### 1. 在 `settings.gradle` / `settings.gradle.kts` 中配置插件仓库

需要确保 Gradle 能够在 `pluginManagement` 中找到 OperLog 插件。

#### Groovy DSL (`settings.gradle`):
```groovy
pluginManagement {
    repositories {
        mavenLocal()
        // 1. 公司内网 Nexus 私服仓库（HTTP 需设置 allowInsecureProtocol = true）
        maven {
            url 'http://10.1.74.176:8001/repository/maven-releases/'
            allowInsecureProtocol = true
        }
        maven {
            url 'http://10.1.74.176:8001/repository/maven-neusoft/'
            allowInsecureProtocol = true
        }
        // 2. 阿里云 Maven 镜像（解决网络访问与 TLS 握手问题）
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }

        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

#### Kotlin DSL (`settings.gradle.kts`):
```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        // 1. 公司内网 Nexus 私服仓库
        maven {
            url = uri("http://10.1.74.176:8001/repository/maven-releases/")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://10.1.74.176:8001/repository/maven-neusoft/")
            isAllowInsecureProtocol = true
        }
        // 2. 阿里云 Maven 镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

---

### 2. 在业务 App 模块应用插件

#### Kotlin DSL (`build.gradle.kts`):
```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("com.neusoft.oper-log") version "1.0.0"
}
```

#### Groovy DSL (`build.gradle`):
```groovy
plugins {
    id 'com.android.application'
    id 'com.neusoft.oper-log' version '1.0.0'
}
```

---

### 3. 引入 Runtime 依赖

#### Kotlin DSL (`build.gradle.kts`):
```kotlin
dependencies {
    // 引入 Runtime，注解库已由 api 自动传递引入
    implementation("com.neusoft.operlog:oper-log-runtime:1.0.0")
}
```

#### Groovy DSL (`build.gradle`):
```groovy
dependencies {
    implementation 'com.neusoft.operlog:oper-log-runtime:1.0.0'
}
```

---

## 三、接入方式二：离线文件（`libs/` 目录）手动接入

此方式适用于完全离线环境或无法使用 Maven 仓库的场景。需要将打好的 Jar 包手工拷贝到业务工程中。

### 1. 拷贝运行时 Jar 包到 `app/libs/`
将以下 2 个 Jar 文件拷贝到业务 App 模块的 `app/libs/` 目录下：
- `oper-log-runtime-1.0.0.jar`
- `oper-log-annotation-1.0.0.jar`

在 App 模块的 `build.gradle` / `build.gradle.kts` 中依赖：

```groovy
// Groovy DSL (app/build.gradle)
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
}
```

```kotlin
// Kotlin DSL (app/build.gradle.kts)
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
```

### 2. 配置本地插件到根目录 `buildscript`
将以下插件及其支撑包拷贝到工程根目录 `gradle/plugins/` 文件夹：
- `oper-log-gradle-plugin-1.0.0.jar`
- `oper-log-bytecode-core-1.0.0.jar`
- `oper-log-agp-modern-1.0.0.jar`
- `oper-log-agp-legacy-1.0.0.jar`

在工程**根目录**的 `build.gradle` / `build.gradle.kts` 中配置 classpath 并应用插件：

```groovy
// 根目录 build.gradle (Groovy DSL)
buildscript {
    dependencies {
        classpath files('gradle/plugins/oper-log-gradle-plugin-1.0.0.jar')
        classpath files('gradle/plugins/oper-log-bytecode-core-1.0.0.jar')
        classpath files('gradle/plugins/oper-log-agp-modern-1.0.0.jar')
        classpath files('gradle/plugins/oper-log-agp-legacy-1.0.0.jar')
    }
}
```

在 **App 模块**的 `build.gradle` 中应用插件：
```groovy
apply plugin: 'com.neusoft.oper-log'
```

---

## 四、注解 API 详细说明

框架提供两个核心注解，位于 `com.neusoft.operlog.annotation` 包下：

### 1. `@OperLog`
标注需要进行日志切面追踪的方法/函数。可用于 Kotlin 方法和 Java 方法。

```java
// Java / Kotlin 均通用定义
public @interface OperLog {
    String tag() default "";
    boolean printArgs() default true;
    boolean printResult() default false;
    boolean printThread() default true;
    boolean measureTime() default true;
}
```

| 属性名称 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| **`tag`** | `String` | `""` | Logcat 日志标签。若留空，默认使用当前**类名**作为 Tag。 |
| **`printArgs`** | `boolean` | `true` | 是否打印方法入参及其参数值。 |
| **`printResult`** | `boolean` | `false` | 是否打印方法正常退出时的返回值（建议大对象保持关闭）。 |
| **`printThread`** | `boolean` | `true` | 是否打印当前方法执行所在的线程名称。 |
| **`measureTime`** | `boolean` | `true` | 是否统计并打印方法执行耗时。 |

---

### 2. `@OperLogIgnore`
标注方法参数中涉及隐私或敏感数据的入参。

#### 脱敏效果：
被标注 `@OperLogIgnore` 的参数在日志输出时，其真实值将被统一替换为 `***`。

---

## 五、典型使用场景范例（Kotlin & Java 对照）

### 场景 1：常规方法追踪与耗时统计

#### Java 示例代码：
```java
package com.neusoft.sample;

import com.neusoft.operlog.annotation.OperLog;

public class BluetoothController {

    @OperLog(
        tag = "Bluetooth",
        printArgs = true,
        printResult = true,
        measureTime = true
    )
    public boolean connectDevice(String macAddress, int timeoutMs) {
        // 业务逻辑...
        return true;
    }
}
```

#### Kotlin 示例代码：
```kotlin
package com.neusoft.sample

import com.neusoft.operlog.annotation.OperLog

class BluetoothController {

    @OperLog(
        tag = "Bluetooth",
        printArgs = true,
        printResult = true,
        measureTime = true
    )
    fun connectDevice(macAddress: String, timeoutMs: Int): Boolean {
        // 业务逻辑...
        return true
    }
}
```

#### Logcat 输出效果（Java / Kotlin 完全一致）：
```text
D/Bluetooth: → ENTER BluetoothController#connectDevice
D/Bluetooth:   thread=main
D/Bluetooth:   args: macAddress="AA:BB:CC:DD:EE:FF", timeoutMs=5000
D/Bluetooth: ← EXIT BluetoothController#connectDevice cost=18.45ms
D/Bluetooth:   result=true
```

---

### 场景 2：敏感数据（密码、身份证、Token）脱敏

#### Java 示例代码：
```java
package com.neusoft.sample;

import com.neusoft.operlog.annotation.OperLog;
import com.neusoft.operlog.annotation.OperLogIgnore;

public class AccountService {

    @OperLog(tag = "Auth", printArgs = true)
    public String login(
        String accountName,
        @OperLogIgnore String passwordSecret,
        @OperLogIgnore String idCard
    ) {
        return "Token_v1_889922";
    }
}
```

#### Kotlin 示例代码：
```kotlin
package com.neusoft.sample

import com.neusoft.operlog.annotation.OperLog
import com.neusoft.operlog.annotation.OperLogIgnore

class AccountService {

    @OperLog(tag = "Auth", printArgs = true)
    fun login(
        accountName: String,
        @OperLogIgnore passwordSecret: String,
        @OperLogIgnore idCard: String
    ): String {
        return "Token_v1_889922"
    }
}
```

#### Logcat 输出效果（Java / Kotlin 完全一致）：
```text
D/Auth: → ENTER AccountService#login
D/Auth:   thread=main
D/Auth:   args: accountName="driver01", passwordSecret=***, idCard=***
```

---

### 场景 3：异常诊断与调用链跟踪

当 Java 或 Kotlin 方法内部抛出异常时，OperLog 会先格式化输出 `ERROR` 诊断日志，再原样重新抛出异常（不影响崩溃上报系统）。

#### Java 示例代码：
```java
package com.neusoft.sample;

import com.neusoft.operlog.annotation.OperLog;

public class HvacController {

    @OperLog(tag = "Hvac", printArgs = true)
    public void setTemperature(int celsius) {
        if (celsius < 16 || celsius > 30) {
            throw new IllegalArgumentException("Temperature " + celsius + " is out of valid range (16-30)");
        }
    }
}
```

#### Kotlin 示例代码：
```kotlin
package com.neusoft.sample

import com.neusoft.operlog.annotation.OperLog

class HvacController {

    @OperLog(tag = "Hvac", printArgs = true)
    fun setTemperature(celsius: Int) {
        if (celsius !in 16..30) {
            throw IllegalArgumentException("Temperature $celsius is out of valid range (16-30)")
        }
    }
}
```

#### Logcat 输出效果（Java / Kotlin 完全一致）：
```text
D/Hvac: → ENTER HvacController#setTemperature
D/Hvac:   thread=main
D/Hvac:   args: celsius=35
E/Hvac: ✕ ERROR HvacController#setTemperature cost=0.32ms
E/Hvac:   exception=java.lang.IllegalArgumentException: Temperature 35 is out of valid range (16-30)
```

---

## 六、Gradle 插件 DSL 配置

无论项目是用 Kotlin DSL (`build.gradle.kts`) 还是 Groovy DSL (`build.gradle`)，都可以配置全局控制闭包：

#### Kotlin DSL (`build.gradle.kts`):
```kotlin
operLog {
    enabled = true
    enableInRelease = false
    includePackages = listOf("com.neusoft.sample", "com.neusoft.bluetooth")
    excludePackages = listOf("com.neusoft.sample.generated")
    printArgs = true
    printThread = true
    printResult = false
    measureTime = true
}
```

#### Groovy DSL (`build.gradle`):
```groovy
operLog {
    enabled = true
    enableInRelease = false
    includePackages = ["com.neusoft.sample", "com.neusoft.bluetooth"]
    excludePackages = ["com.neusoft.sample.generated"]
    printArgs = true
    printThread = true
    printResult = false
    measureTime = true
}
```

---

## 七、开发注意事项与最佳实践

1. **Java / Kotlin 完全通用**：OperLog 的字节码插桩发生在 `.class` 字节码层级，因此对 Java 编写的代码和 Kotlin 编写的代码效果**完全相同**。
2. **强烈推荐配置 `includePackages`**：显式配置公司主包名（如 `["com.neusoft"]`），可避免扫描无用第三方库，提升 50%+ 编译打包速度。
3. **敏感信息安全**：任何接收密码、密钥、身份证、银行卡号等敏感信息的参数，无论 Java 还是 Kotlin，**必须**标注 `@OperLogIgnore` 注解。
