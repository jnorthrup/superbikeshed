// ta4k-spacegraph-moneyfan-demo/build.gradle.kts
plugins {
    kotlin("multiplatform") // Inherit version from root project
    // No application plugin here as this module primarily produces JS for the browser
}

kotlin {
    js(IR) { // Target JavaScript with the IR compiler
        browser {
            // Configure webpack tasks
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true) // Enable CSS handling
                }
            }

            // Production build configuration (optional for demo, but good practice)
            // distribution {
            //    directory = project.file("build/dist/js")
            // }
        }

        // Define the output binary for the JS code
        binaries.executable()
    }

    sourceSets {
        // Common source set (not used much in this primarily JS demo)
        // val commonMain by getting {
        //     dependencies {
        //         // implementation(kotlin("stdlib-common")) // Already included by default
        //     }
        // }

        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js")) // Standard JS library for Kotlin

                // Project dependencies
                implementation(project(":ta4k"))                          // For Kline, parsing, DSEL-native indicators
                implementation(project(":Review"))                        // For TrikeShedCore DSEL definitions
                implementation(project(":spacegraphjs:kotlin-spacegraph")) // For AgentAPI

                // NPM dependencies required by spacegraph.js (and potentially its Kotlin wrapper)
                implementation(npm("three", "0.166.1")) // Specify version used by spacegraph.js
                implementation(npm("gsap", "3.12.5"))   // Specify version used by spacegraph.js

                // Coroutines for async operations like fetch
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3") // Use js artifact
            }
            // Ensure the resources directory is correctly identified for the JS source set
            // This makes files in src/jsMain/resources available, e.g. index.html, data files.
            // The devServer.staticResourcesDirectory handles serving, but this ensures build awareness.
            resources.srcDirs(project.file("src/jsMain/resources"))
        }

        // Optional JVM source set if any JVM-specific helper tasks were needed
        // val jvmMain by getting {
        //     dependencies {
        //         implementation(kotlin("stdlib-jdk8"))
        //     }
        // }
    }
}

// Ensure the final JS bundle name (optional, defaults usually fine)
// tasks.named("jsBrowserProductionWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
// tasks.named("jsBrowserDevelopmentWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
