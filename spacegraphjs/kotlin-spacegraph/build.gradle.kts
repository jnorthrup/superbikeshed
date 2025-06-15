import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension

plugins {
    kotlin("multiplatform")
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
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                // For kotlinx.html if needed for typed HTML building:
                // implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0") // Check for latest version
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        // Add native source sets
        val nativeMain by creating {
            dependsOn(commonMain)
            // Define source directories if you have common native code, e.g.
            // kotlin.srcDir("src/nativeMain/kotlin")
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Linux source sets
        findByName("linuxX64Main")?.let { linuxX64Main ->
            linuxX64Main.dependsOn(nativeMain)
        }
        findByName("linuxArm64Main")?.let { linuxArm64Main ->
            linuxArm64Main.dependsOn(nativeMain)
        }

        // macOS source sets
        findByName("macosX64Main")?.let { macosX64Main ->
            macosX64Main.dependsOn(nativeMain)
        }
        findByName("macosArm64Main")?.let { macosArm64Main ->
            macosArm64Main.dependsOn(nativeMain)
        }

        // Corresponding test source sets
        findByName("linuxX64Test")?.let { linuxX64Test ->
            linuxX64Test.dependsOn(nativeTest)
        }
        findByName("linuxArm64Test")?.let { linuxArm64Test ->
            linuxArm64Test.dependsOn(nativeTest)
        }
        findByName("macosX64Test")?.let { macosX64Test ->
            macosX64Test.dependsOn(nativeTest)
        }
        findByName("macosArm64Test")?.let { macosArm64Test ->
            macosArm64Test.dependsOn(nativeTest)
        }
    }
}

// Ensure the wrapper task for browser support is available
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    args += "--ignore-scripts"
}

