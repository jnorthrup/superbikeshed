// ta4k-spacegraph-moneyfan-demo/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(libs.kotlin.stdlib.common)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.kotlin.stdlib.js)
                implementation(project(":ta4k"))
                implementation(project(":Trikeshed"))
                implementation(project(":spacegraphjs:kotlin-spacegraph"))
                implementation(npm("three", libs.versions.three.get()))
                implementation(npm("gsap", libs.versions.gsap.get()))
                implementation(npm("three-orbit-controls", libs.versions.three.orbit.controls.get()))
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core.js)
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = libs.versions.jvm.get()
    }
}

// Ensure the final JS bundle name (optional, defaults usually fine)
// tasks.named("jsBrowserProductionWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
// tasks.named("jsBrowserDevelopmentWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
//    outputFileName = "ta4k-spacegraph-moneyfan-demo.js"
// }
