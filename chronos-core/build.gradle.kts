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

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            // removed: api(project(":chronos-processor")), the processor is JVM-only
            // and must only be referenced from ksp* configurations
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":chronos-testing"))
        }
        androidMain.dependencies {
            implementation(libs.workmanager.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.startup.runtime)
        }
    }

    sourceSets.commonMain {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

android {
    namespace = "io.github.sahilmakkar.chronos.core"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspJvm", libs.room.compiler)

    add("kspCommonMainMetadata", project(":chronos-processor"))
}

tasks.configureEach {
    if (name != "kspCommonMainKotlinMetadata" && (name.startsWith("ksp") || name.startsWith("compile"))) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}