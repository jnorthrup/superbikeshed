plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
}

group = "org.k2script.jetsamgossip"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                // implementation(project(":trikeshed-lib")) // Not present yet
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation("com.google.code.gson:gson:2.10.1")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
            }
        }
    }
} 