plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    // Native target based on host OS
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64()
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" -> linuxX64()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                // Core library has minimal dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}