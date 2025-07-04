@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
    
    jvm {
        // JVM toolchain configured at extension level
    }
    // js(IR) {
    //     nodejs()
    //     browser()
    // }
    wasmJs {
        nodejs()
        browser()
    }
    
    // Add native targets to match other modules
    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> { 
            macosX64()
            macosArm64() 
        }
        hostOs == "Linux" -> { 
            linuxX64()
            linuxArm64() 
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-isam"))
                implementation(project(":zlib-kmp-reference"))
                implementation(project(":trikeshed-net"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
} 