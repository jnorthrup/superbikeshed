plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvm()
    wasmJs {
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
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(npm("three", "^0.162.0"))
                implementation(npm("@types/three", "^0.162.0"))
                implementation(project(":ta4k"))
            }
        }
    }
}

tasks.named("wasmJsBrowserDevelopmentRun") {
    dependsOn("wasmJsBrowserDevelopmentWebpack")
}
