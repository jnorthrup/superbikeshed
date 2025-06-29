plugins {
    kotlin("multiplatform") version "2.1.21"
}

group = "com.example"
version = "1.0-SNAPSHOT"

kotlin {
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    wasmJs {
        browser()
        binaries.executable()
    }
    if (hostOs == "Mac OS X") {
        if (hostArch == "aarch64") {
            macosArm64()
        } else {
            macosX64()
        }
    } else if (hostOs == "Linux") {
        if (hostArch == "aarch64") {
            linuxArm64()
        } else {
            linuxX64()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-dom:0.0.20")
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-dom:0.0.20")
                implementation(npm("webgpu", "0.1.34"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}

// Ensure the wrapper task for browser support is available
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    args += "--ignore-scripts"
}
