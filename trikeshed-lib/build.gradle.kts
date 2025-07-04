@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    wasmJs {
        browser()
        nodejs()
    }
    
    // Native targets based on host OS
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
                // Core library has minimal dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

 