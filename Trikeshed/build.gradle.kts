// Remove the problematic plugin line
// Remove the line referencing the JUnit platform plugin
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21"
    // Temporarily remove benmanes plugin to resolve build issue
    // id("com.benmanes.gradle.versions") version "0.46.0"
// Remove the JUnit platform plugin line
// Remove the JUnit platform plugin line
    
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    linuxX64()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
    }
}
// Add JVM target compatibility and adjust Spotless configuration