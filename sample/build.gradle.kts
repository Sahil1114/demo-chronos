plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinPluginCompose)
}

kotlin {
    androidTarget()
    
    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(project(":chronos-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.core)
        }
    }

    // Make generated common code visible to every compilation
    sourceSets.commonMain {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

dependencies {
    // Worker processor runs once, on common code only
    add("kspCommonMainMetadata", project(":chronos-processor"))
}

// Every compilation and KSP task must wait for common KSP run to finish
tasks.configureEach {
    if (name != "kspCommonMainKotlinMetadata" && (name.startsWith("ksp") || name.startsWith("compile"))) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

android {
    namespace = "io.github.sahilmakkar.chronos.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.sahilmakkar.chronos.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.sahilmakkar.chronos.sample.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "ChronosSample"
            packageVersion = "1.0.0"
        }
    }
}
