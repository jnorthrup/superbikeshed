import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension

plugins {
    kotlin("multiplatform") // Inherit version from root project
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":Review:trikeshed-core"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                // For kotlinx.html if needed for typed HTML building:
                // implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0") // Check for latest version
            }
        }
    }
}

// Ensure the wrapper task for browser support is available
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask> {
    args += "--ignore-scripts"
}

