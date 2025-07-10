// Standalone build file for native executable only
plugins {
    kotlin("multiplatform") version "1.9.23"
}

group = "borg.trikeshed"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "mcp-native-host"
            }
        }
    }
    
    sourceSets {
        val macosArm64Main by getting {
            kotlin.srcDir("src/nativeMain/kotlin")
        }
    }
}