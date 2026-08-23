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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":chronos-testing"))
        }

        val roomBackedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }

        androidMain.dependencies {
            implementation(libs.workmanager.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }

        jvmMain.get().dependsOn(roomBackedMain)
        appleMain.get().dependsOn(roomBackedMain)
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