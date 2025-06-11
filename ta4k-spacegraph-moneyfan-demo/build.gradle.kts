// ta4k-spacegraph-moneyfan-demo/build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.2.0-RC2"
    kotlin("plugin.serialization") version "2.2.0-RC2"
}

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
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

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")

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
<<<<<<< HEAD
<<<<<<< HEAD
=======
        val commonMain by creating {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

>>>>>>> jules_wip_15441682621147348775
        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-js")) // Standard JS library for Kotlin
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0") // Add serialization
=======
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
>>>>>>> origin/jules_wip_12008771546559725757

                // NPM dependencies required by spacegraph.js (and potentially its Kotlin wrapper)
                implementation(npm("three", "0.166.1")) // Specify version used by spacegraph.js
                implementation(npm("gsap", "3.12.5"))   // Specify version used by spacegraph.js
<<<<<<< HEAD
                implementation(npm("three-orbit-controls", "82.1.0")) // Add OrbitControls

                // Coroutines for async operations like fetch
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.2.1") // Browser APIs
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3") // Use js artifact
            }
            resources.srcDirs(project.file("src/jsMain/resources"))
        }
<<<<<<< HEAD
=======

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
>>>>>>> origin/jules_wip_12008771546559725757
=======

        val commonTest by creating { // Create commonTest
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val linuxX64Main by creating { dependsOn(nativeMain) }
        val linuxArm64Main by creating { dependsOn(nativeMain) }
        val macosX64Main by creating { dependsOn(nativeMain) }
        val macosArm64Main by creating { dependsOn(nativeMain) }

        val linuxX64Test by creating { dependsOn(nativeTest) }
        val linuxArm64Test by creating { dependsOn(nativeTest) }
        val macosX64Test by creating { dependsOn(nativeTest) }
        val macosArm64Test by creating { dependsOn(nativeTest) }
>>>>>>> jules_wip_15441682621147348775
    }
}

// Ensure the final JS bundle name (optional, defaults usually fine)
// tasks.named("jsBrowserProductionWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
// tasks.named("jsBrowserDevelopmentWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
