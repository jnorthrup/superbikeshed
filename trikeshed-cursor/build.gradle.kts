@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)
    
    jvm {
        // JVM toolchain configured at extension level
    }
    js(IR) {
        nodejs()
        browser()
    }
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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}