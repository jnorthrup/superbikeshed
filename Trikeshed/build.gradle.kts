@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

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
            val sdkPath = project.providers.exec {
                commandLine("xcrun", "--sdk", "macosx", "--show-sdk-path")
            }.standardOutput.asText.get().trim()
            macosX64 {
                binaries {
                    executable()
                }
                compilations.getByName("main").cinterops {
                    val kqueue by creating {
                        defFile("src/nativeInterop/cinterop/kqueue.def")
                        compilerOpts.add("-I$sdkPath/usr/include")
                    }
                }
            }
            macosArm64 {
                binaries {
                    executable()
                }
                compilations.getByName("main").cinterops {
                    val kqueue by creating {
                        defFile("src/nativeInterop/cinterop/kqueue.def")
                        compilerOpts.add("-I$sdkPath/usr/include")
                    }
                }
            }
        }
        hostOs == "Linux" -> {
            linuxX64 {
                binaries {
                    executable()
                }
                compilations.getByName("main").cinterops.create("liburing") {
                    defFile = file("src/nativeInterop/cinterop/liburing.def")
                }
            }
            linuxArm64 {
                binaries {
                    executable()
                }
                compilations.getByName("main").cinterops.create("liburing") {
                    defFile = file("src/nativeInterop/cinterop/liburing.def")
                }
            }
        }
    }
    
    
    
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmTest {
            // Removed explicit dependency on kotlin("test-junit5") to resolve conflict
        }
        
        // Native source sets configuration is handled automatically by Kotlin's hierarchy template
    }
}