plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
}

kotlin {
    jvm()
    
    if (project.findProperty("enableNative") == "true") {
        val currentOs = System.getProperty("os.name")
        if (currentOs.contains("mac", ignoreCase = true)) {
            macosX64()
            macosArm64()
        }
        if (currentOs.contains("linux", ignoreCase = true)) {
            linuxX64()
        }
    }
    
    // wasmJs disabled until trikeshed-lib supports it
    // wasmJs {
    //     browser()
    //     nodejs()
    // }
    
    sourceSets {
        commonMain {
            dependencies {
                api(project(":trikeshed-lib"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                api("org.jetbrains.kotlinx:kotlinx-serialization-core")
                api("org.jetbrains.kotlinx:kotlinx-datetime")
            }
        }
        
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }
        
        jvmMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
            }
        }
        
        nativeMain {
            dependencies {
                // Native-specific async dependencies
            }
        }
        
        
    }
}