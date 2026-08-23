# Chronos — Dependencies, Versions & Build Configuration

This document is the **ground truth** for all dependency versions and Gradle configuration before we write any project files.

> Last verified: June 2026

---

## 1. Three Critical Discoveries (Before Anything Else)

### Discovery 1 — Room 3.0 with new namespace
Room has a **major version bump to 3.0** with a new artifact namespace:
- Old: `androidx.room:room-runtime`
- New: `androidx.room3:room3-runtime`

Room 3.0 is:
- **Coroutine-first** (no more blocking APIs)
- **KSP-only** (KAPT is completely dropped)
- Built on new `androidx.sqlite` driver APIs
- Fully KMP including JS/WasmJs

**Impact on us:** We use `androidx.room3` everywhere. The Room Gradle plugin ID also changes.

### Discovery 2 — KSP2 is now the default
Since Kotlin 2.3.0+, **KSP2 is the default** and KSP1 is deprecated. KSP2:
- No longer requires strict version pinning to the exact Kotlin patch version
- Enabled via `ksp.useKSP2=true` in `gradle.properties` (already the default)
- Our custom `workmanager-processor` must be written for KSP2

### Discovery 3 — Compose Compiler is now bundled with Kotlin
Since Kotlin 2.0.0, the Compose compiler is **built into Kotlin**. No separate compose compiler version needed. Just apply `org.jetbrains.kotlin.plugin.compose` plugin.

---

## 2. Final Version Matrix

### Build Chain (Must all be compatible)

| Tool / Plugin | Version | Notes |
|---|---|---|
| **Kotlin** | `2.4.0` | Latest stable as of June 2026 |
| **KSP** | `2.3.9` | KSP2 is default — standalone versioning since Kotlin 2.3.0+ |
| **AGP** (Android Gradle Plugin) | `8.10.1` | Compatible with Kotlin 2.4.0 |
| **Gradle Wrapper** | `8.13` | Required by AGP 8.10.x |
| **Java Toolchain** | `17` | Safe with AGP 8.x — Java 21 for future upgrade |

### Core Runtime

| Dependency | Version | Scope |
|---|---|---|
| `kotlinx-coroutines-core` | `1.10.2` | `commonMain` |
| `kotlinx-datetime` | `0.7.1` | `commonMain` — for `KmpClock` / `TimeWindow` |

### Room 3.0 KMP (iOS + JVM persistence)

| Dependency | Artifact | Version | Scope |
|---|---|---|---|
| Room Runtime | `androidx.room3:room3-runtime` | `3.0.0-alpha05` | `commonMain` |
| SQLite Bundled | `androidx.sqlite:sqlite-bundled` | `2.5.1` | `commonMain` |
| Room Compiler | `androidx.room3:room3-compiler` | `3.0.0-alpha05` | `ksp` per target |
| Room Plugin | `androidx.room3` | `3.0.0-alpha05` | `plugins {}` |

### Android Only

| Dependency | Artifact | Version | Scope |
|---|---|---|---|
| WorkManager | `androidx.work:work-runtime-ktx` | `2.11.2` | `androidMain` |
| Coroutines Android | `kotlinx-coroutines-android` | `1.10.2` | `androidMain` |

### KSP Processor Module (JVM only, never ships to end users)

| Dependency | Version | Scope |
|---|---|---|
| `com.google.devtools.ksp:symbol-processing-api` | KSP2 compatible | `implementation` |
| `com.squareup:kotlinpoet-ksp` | `2.1.0` | `implementation` |

### Sample App Only (not in core library)

| Dependency | Version | Scope |
|---|---|---|
| Compose Multiplatform | `1.11.1` | `sample` module only |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.9.0` | `sample` module only |
| `androidx.navigation:navigation-compose` | `2.9.0` | `sample` module only |

### Testing

| Dependency | Version | Scope |
|---|---|---|
| `kotlin-test` | bundled with `2.4.0` | `commonTest` |
| `kotlinx-coroutines-test` | `1.10.2` | `commonTest` |

### Publishing

| Plugin | Version |
|---|---|
| `vanniktech/gradle-maven-publish-plugin` | `0.30.0` |

---

## 3. `libs.versions.toml` (Version Catalog)

```toml
[versions]
# ── Build chain ─────────────────────────────────────────────
kotlin                  = "2.4.0"
agp                     = "8.10.1"
ksp                     = "2.3.9"           # KSP2 — standalone versioning since Kotlin 2.3.0+

# ── Core runtime ─────────────────────────────────────────────
coroutines              = "1.10.2"
kotlinxDatetime         = "0.7.1"

# ── Room 3.0 KMP ─────────────────────────────────────────────
room                    = "3.0.0-alpha05"
sqlite                  = "2.5.1"

# ── Android ──────────────────────────────────────────────────
workmanager             = "2.11.2"
androidxLifecycle       = "2.9.0"
androidxNavigation      = "2.9.0"

# ── KSP Processor ────────────────────────────────────────────
kotlinpoet              = "2.1.0"

# ── Sample App ───────────────────────────────────────────────
composeMultiplatform    = "1.11.1"

# ── Testing ──────────────────────────────────────────────────
coroutinesTest          = "1.10.2"

# ── Publishing ───────────────────────────────────────────────
mavenPublish            = "0.30.0"

# ── Compile targets ──────────────────────────────────────────
compileSdk              = "35"
minSdk                  = "23"
targetSdk               = "35"

[libraries]
# ── Core runtime ─────────────────────────────────────────────
kotlinx-coroutines-core     = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",     version.ref = "coroutines" }
kotlinx-coroutines-android  = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android",  version.ref = "coroutines" }
kotlinx-coroutines-test     = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test",     version.ref = "coroutinesTest" }
kotlinx-datetime            = { module = "org.jetbrains.kotlinx:kotlinx-datetime",            version.ref = "kotlinxDatetime" }

# ── Room 3.0 ─────────────────────────────────────────────────
room-runtime                = { module = "androidx.room3:room3-runtime",                       version.ref = "room" }
room-compiler               = { module = "androidx.room3:room3-compiler",                      version.ref = "room" }
sqlite-bundled              = { module = "androidx.sqlite:sqlite-bundled",                     version.ref = "sqlite" }

# ── Android WorkManager ───────────────────────────────────────
workmanager-ktx             = { module = "androidx.work:work-runtime-ktx",                    version.ref = "workmanager" }

# ── KSP Processor module ──────────────────────────────────────
ksp-api                     = { module = "com.google.devtools.ksp:symbol-processing-api",     version.ref = "ksp" }
kotlinpoet-ksp              = { module = "com.squareup:kotlinpoet-ksp",                       version.ref = "kotlinpoet" }

# ── Sample app ────────────────────────────────────────────────
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose",    version.ref = "androidxLifecycle" }
navigation-compose          = { module = "androidx.navigation:navigation-compose",             version.ref = "androidxNavigation" }

[plugins]
# ── Kotlin ───────────────────────────────────────────────────
kotlinMultiplatform         = { id = "org.jetbrains.kotlin.multiplatform",     version.ref = "kotlin" }
kotlinAndroid               = { id = "org.jetbrains.kotlin.android",           version.ref = "kotlin" }
kotlinPluginCompose         = { id = "org.jetbrains.kotlin.plugin.compose",    version.ref = "kotlin" }

# ── Android ──────────────────────────────────────────────────
androidLibrary              = { id = "com.android.library",                    version.ref = "agp" }
androidApplication          = { id = "com.android.application",                version.ref = "agp" }

# ── KSP ──────────────────────────────────────────────────────
ksp                         = { id = "com.google.devtools.ksp",                version.ref = "ksp" }

# ── Room ─────────────────────────────────────────────────────
room                        = { id = "androidx.room3",                         version.ref = "room" }

# ── Compose Multiplatform ────────────────────────────────────
jetbrainsCompose            = { id = "org.jetbrains.compose",                  version.ref = "composeMultiplatform" }

# ── Publishing ───────────────────────────────────────────────
mavenPublish                = { id = "com.vanniktech.maven.publish",           version.ref = "mavenPublish" }
```

---

## 4. `gradle.properties`

```properties
# ── JVM ──────────────────────────────────────────────────────
# KSP + KMP compilation is memory-heavy — 4GB is the safe minimum
org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC

# ── Build performance ─────────────────────────────────────────
org.gradle.parallel=true
org.gradle.caching=true

# Note: configuration-cache disabled for now
# Some plugins (Room, KSP) have known issues with it
# Re-enable after verifying compatibility
# org.gradle.configuration-cache=true

# ── KSP2 ─────────────────────────────────────────────────────
# KSP2 is the default since Kotlin 2.3.0 — kept explicit for clarity
ksp.useKSP2=true

# ── Kotlin Multiplatform ──────────────────────────────────────
# Suppresses stability warnings for KMP target names
kotlin.mpp.stability.nowarn=true
kotlin.suppressGradlePluginWarnings=DeprecatedGradleVersionWarning

# Allow building Android/JVM on non-Mac machines without iOS SDK failure
kotlin.native.ignoreDisabledTargets=true

# ── Android ───────────────────────────────────────────────────
android.useAndroidX=true
android.nonTransitiveRClass=true

# Disable BuildConfig generation by default for faster builds
android.defaults.buildfeatures.buildconfig=false
```

---

## 5. Root `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Chronos"

include(":chronos-core")
include(":chronos-processor")
include(":chronos-testing")
include(":sample")
```

---

## 6. Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)   apply false
    alias(libs.plugins.kotlinAndroid)         apply false
    alias(libs.plugins.kotlinPluginCompose)   apply false
    alias(libs.plugins.androidLibrary)        apply false
    alias(libs.plugins.androidApplication)    apply false
    alias(libs.plugins.ksp)                   apply false
    alias(libs.plugins.room)                  apply false
    alias(libs.plugins.jetbrainsCompose)      apply false
    alias(libs.plugins.mavenPublish)          apply false
}
```

---

## 7. `chronos-core` Module `build.gradle.kts` (Outline)

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(17)

    androidTarget()

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.workmanager.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        // iosMain, jvmMain — no extra deps beyond commonMain
    }
}

android {
    namespace = "io.github.sahilmakkar.chronos.core"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

// Room 3.0 schema export
room3 {
    schemaDirectory("$projectDir/schemas")
}

// KSP per target — Room compiler for all platforms
dependencies {
    add("kspAndroid",              libs.room.compiler)
    add("kspIosArm64",             libs.room.compiler)
    add("kspIosSimulatorArm64",    libs.room.compiler)
    add("kspJvm",                  libs.room.compiler)
}
```

---

## 8. `chronos-processor` Module `build.gradle.kts` (Outline)

```kotlin
// Pure JVM module — not KMP
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet.ksp)
}
```

---

## 9. iOS Target Decision

We keep **two iOS targets** — `iosX64` dropped since Apple Silicon is now the standard:

| Target | Purpose |
|---|---|
| `iosArm64` | Physical iPhone / iPad |
| `iosSimulatorArm64` | iPhone Simulator on Apple Silicon Macs |

---

## 10. Open Items to Verify During Phase 2b Spike

- [ ] Verify exact KSP2 version compatible with Kotlin `2.4.0`
- [ ] Verify Room 3.0 artifact IDs (`androidx.room3`) on Maven Central before use
- [ ] Verify `sqlite-bundled 2.5.1` works correctly on iOS under Room 3.0
- [ ] Confirm Room 3.0 Gradle plugin ID (`androidx.room3` vs `androidx.room`)
- [ ] Confirm `ksp.useKSP2=true` is compatible with our custom processor design
