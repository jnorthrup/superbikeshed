plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

val hostOs = System.getProperty("os.name")
val hostArch = System.getProperty("os.arch")

kotlin {
    jvm()
    when {
        hostOs == "Mac OS X" -> macosArm64()
        hostOs == "Linux" && (hostArch == "amd64" || hostArch == "x86_64") -> linuxX64()
        hostOs == "Linux" && (hostArch == "aarch64" || hostArch == "arm64") -> linuxArm64()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
} 