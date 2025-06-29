plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
=======
    kotlin("multiplatform") version "2.1.21"
>>>>>>> origin/feat/core-serialization-impl
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvm()
<<<<<<< HEAD
    wasmJs {
=======
    js(IR) {
>>>>>>> origin/feat/core-serialization-impl
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
<<<<<<< HEAD
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
=======
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
>>>>>>> origin/feat/core-serialization-impl
                implementation(npm("three", "^0.162.0"))
                implementation(npm("@types/three", "^0.162.0"))
                implementation(project(":ta4k"))
            }
        }
    }
}

<<<<<<< HEAD
tasks.named("wasmJsBrowserDevelopmentRun") {
    dependsOn("wasmJsBrowserDevelopmentWebpack")
=======
tasks.named("jsBrowserDevelopmentRun") {
    dependsOn("jsBrowserDevelopmentWebpack")
>>>>>>> origin/feat/core-serialization-impl
}
