plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm()
    
    // Native targets
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
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
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}