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
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation(project(":ta4k"))
                implementation(project(":Trikeshed"))
                implementation(project(":spacegraphjs:kotlin-spacegraph"))
                implementation(npm("three", "0.166.1"))
                implementation(npm("gsap", "3.12.5"))
                implementation(npm("three-orbit-controls", "82.1.0"))
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.2.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
            }
            resources.srcDirs(project.file("src/jsMain/resources"))
        }

        // Native targets for future multiplatform support
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Configure platform-specific source sets only if they exist
        findByName("linuxX64Main")?.let { linuxX64Main ->
            linuxX64Main.dependsOn(nativeMain)
        }
        findByName("linuxArm64Main")?.let { linuxArm64Main ->
            linuxArm64Main.dependsOn(nativeMain)
        }
        findByName("macosX64Main")?.let { macosX64Main ->
            macosX64Main.dependsOn(nativeMain)
        }
        findByName("macosArm64Main")?.let { macosArm64Main ->
            macosArm64Main.dependsOn(nativeMain)
        }

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

// Ensure the final JS bundle name (optional, defaults usually fine)
// tasks.named("jsBrowserProductionWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
// tasks.named("jsBrowserDevelopmentWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
