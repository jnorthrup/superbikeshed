plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

val projectVersion = file("../VERSION").readText().trim()
version = projectVersion

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    macosArm64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val nativeMain by creating {
            dependsOn(getByName("commonMain"))
        }
        val macosArm64Main by getting {
            dependsOn(getByName("nativeMain"))
            dependencies {
                // Native dependencies if needed
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}