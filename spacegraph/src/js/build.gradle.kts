import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension

plugins {
    kotlin("multiplatform") version "2.1.21"
}

group = "com.example"
version = "1.0-SNAPSHOT"


kotlin {
    js(IR) { // Use IR compiler
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")

    if (hostOs == "Mac OS X") {
        if (hostArch == "aarch64") {
            macosArm64() // Defines macosArm64 target
        } else {
            macosX64()   // Defines macosX64 target
        }
    } else if (hostOs == "Linux") {
        if (hostArch == "aarch64") {
            linuxArm64() // Defines linuxArm64 target
        } else {
            linuxX64()   // Defines linuxX64 target
        }
    }
    // No mingwX64 target will be added as per the focused request for Linux and Mac.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-dom:0.0.20")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0")
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

