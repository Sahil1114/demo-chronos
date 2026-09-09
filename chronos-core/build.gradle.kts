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
            implementation(dependencyNotation = libs.room.runtime)
            implementation(dependencyNotation = libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(dependencyNotation = kotlin("test"))
            implementation(dependencyNotation = libs.kotlinx.coroutines.test)
            implementation(dependencyNotation = project(":chronos-testing"))
        }


        androidMain.dependencies {
            implementation(dependencyNotation = libs.workmanager.ktx)
            implementation(dependencyNotation = libs.kotlinx.coroutines.android)
            implementation(dependencyNotation=libs.androidx.startup.runtime)

        }
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
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}