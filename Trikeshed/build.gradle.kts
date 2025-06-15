plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.21"
    // id("com.benmanes.gradle.versions") version "0.46.0" // Commented out as per instructions
}

repositories {
    mavenCentral()
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