// Standard multiplatform build template for v2superbikeshed projects
// Copy this template when creating new modules

@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") // Optional - remove if not needed
}

group = "borg.trikeshed" // Or "borg.rtsgame" for game modules

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    // WASM support - uncomment when WASM dependencies are ready
    // wasmJs {
    //     browser()
    //     nodejs()
    // }
    
    // Native targets based on host OS (defined in root build.gradle.kts)
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
                // Add other dependencies as needed
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmMain {
            dependencies {
                // JVM-specific dependencies
            }
        }
        
        jvmTest {
            dependencies {
                // No explicit kotlin("test-junit5") needed
            }
        }
        
        // Native-specific source sets if needed
        // nativeMain { ... }
        // nativeTest { ... }
    }
}