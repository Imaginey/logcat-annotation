# OperLog Compatibility Matrix

This document maintains the build and runtime compatibility matrix for the OperLog framework across Android Platforms, AGP versions, Gradle versions, JDK versions, and DSL formats.

---

## 1. Component Compatibility Matrix

| OperLog Component | Compatible Version Range | Minimum Java Version | Android API Target Level | Validation Status |
|---|---|---|---|---|
| **`oper-log-annotation`** | 1.0.0+ | Java 8 (1.8) | Pure JVM / Any Android API | **Verified** |
| **`oper-log-runtime`** | 1.0.0+ | Java 8 (1.8) | API 30 ~ 35 (Android 11 ~ 15) | **Verified** |
| **`oper-log-bytecode-core`** | 1.0.0+ | Java 8 / 11 | Pure JVM | **Verified** |
| **`oper-log-agp-modern`** | 1.0.0+ | Java 11 / 17 | Follows AGP 7.4.0 ~ 8.5.0+ | **Verified** |
| **`oper-log-agp-legacy`** | 1.0.0+ | Java 8 / 11 | Follows AGP 4.x ~ 7.x Legacy Transform | **Designed / Experimental** |
| **`oper-log-gradle-plugin`**| 1.0.0+ | Java 11 / 17 | Follows AGP / Gradle requirement | **Verified** |

---

## 2. Sample & Platform Matrix

| Sample Project | Android Target SDK | Android Min SDK | AGP Adapter | JDK Requirement | Status |
|---|---|---|---|---|---|
| `sample-android11` | API 30 (Android 11) | API 30 | Modern / Legacy | JDK 11 / 17 | **Verified** |
| `sample-android14` | API 34 (Android 14) | API 30 | Modern | JDK 17 | **Verified** |
| `sample-android15` | API 35 (Android 15) | API 30 | Modern | JDK 17 | **Verified** |

---

## 3. Scope Support Details

- **Supported**:
  - Standard Java & Kotlin methods.
  - Public, private, protected, package-private, instance, and static methods.
  - Primitive and Object parameter types with automatic bytecode boxing.
  - All return opcodes (`RETURN`, `IRETURN`, `LRETURN`, `FRETURN`, `DRETURN`, `ARETURN`).
  - `@OperLogIgnore` sensitive parameter masking.
  - Parameter and object truncation (`MAX_VALUE_LENGTH`, `MAX_COLLECTION_SIZE`, `MAX_ARRAY_SIZE`, `MAX_MAP_SIZE`).
  - Method-wide exception catch-all (`try-catch Throwable`) with original exception rethrow.
  - Release build zero-cost optimization (`enableInRelease = false`).

- **Unsupported in V1 (Skipped with Build Warnings)**:
  - `<init>` (constructors) & `<clinit>` (static initializers).
  - `abstract`, `native`, `synthetic`, `bridge` methods.
  - Kotlin compiler synthetic methods (`$default`, `access$`, `$annotations`, `lambda$`).
  - Kotlin `suspend` functions (skipped with explicit build warnings).
  - Kotlin `inline` functions with `reified` type parameters in call-site scenarios.
