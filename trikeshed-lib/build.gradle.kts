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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
} 