plugins {
    kotlin("multiplatform") version "2.0.0" // Using a recent stable Kotlin version
    id("com.android.library") version "8.2.0" // Placeholder for Android target if needed later
    id("org.jetbrains.kotlin.native.cocoapods") version "2.0.0" // Placeholder for iOS target if needed later
}

group = "borg.trikeshed.zlib"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    
    // Configure native targets for common platforms
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMac = hostOs.startsWith("Mac OS")
    val isLinux = hostOs.startsWith("Linux")

    if (isMac) {
        macosX64()
        macosArm64()
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    } else if (isMingwX64) {
        mingwX64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Trikeshed CoreTypes dependency (assuming it's a local project dependency)
                // This will allow us to use Series as Indexed
                implementation(project(":Trikeshed"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val macosX64Main by getting
        val macosX64Test by getting
        val macosArm64Main by getting
        val macosArm64Test by getting
        val linuxX64Main by getting
        val linuxX64Test by getting
        val linuxArm64Main by getting
        val linuxArm64Test by getting
        val mingwX64Main by getting
        val mingwX64Test by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
    }
}

android {
    namespace = "borg.trikeshed.zlib"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

// Cocoapods configuration for iOS
cocoapods {
    summary = "Kotlin Multiplatform Zlib Reference Library"
    homepage = "https://github.com/superbikeshed/superbikeshed"
    ios.deploymentTarget = "14.1"
    framework {
        baseName = "ZlibKmpReference"
        isStatic = true
    }
}
