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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
