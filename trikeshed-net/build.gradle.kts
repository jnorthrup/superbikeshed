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
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-channels:1.8.0")
                implementation(project(":trikeshed-quic"))
                implementation(project(":trikeshed-reactor"))
            }
        }
        commonTest {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(kotlin("test"))
            }
        }
    }
}
