@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.detekt)
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
        
        // Native source sets
        val nativeMain by creating {
            dependsOn(commonMain.get())
        }
        
        val nativeTest by creating {
            dependsOn(commonTest.get())
        }
        
        // Configure native targets
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            compilations["main"].defaultSourceSet.dependsOn(nativeMain)
            compilations["test"].defaultSourceSet.dependsOn(nativeTest)
        }
    }
}