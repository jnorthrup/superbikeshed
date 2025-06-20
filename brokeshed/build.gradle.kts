plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    
    // Always include these targets to support cross-platform builds
    linuxX64()
    macosArm64()
    
    // Additional targets based on host platform
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && !isArm64 -> macosX64() // Only add if not already present
        isLinux && isArm64 -> linuxArm64()
        isWindows -> mingwX64()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.27.0")
                // BrokeShed provides implementations for TrikeShed, not vice versa
            }
        }
    }
}