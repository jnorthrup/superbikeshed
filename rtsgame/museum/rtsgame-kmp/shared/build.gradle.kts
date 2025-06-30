plugins {
    kotlin("multiplatform") version "2.1.21"
}

kotlin {
    wasmJs {
        browser()
        nodejs()
    }
    jvm()
    
    // Platform detection for native targets
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows -> mingwX64()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                // Common dependencies
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                // WASM JS-specific dependencies
            }
        }
        
        val jvmMain by getting {
            dependencies {
                // JVM-specific dependencies
            }
        }
    }
} 