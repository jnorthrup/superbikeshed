plugins {
    kotlin("multiplatform") version "2.1.21"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvm()
    js(IR) {
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
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
                implementation(npm("three", "^0.162.0"))
                implementation(npm("@types/three", "^0.162.0"))
                implementation(project(":ta4k"))
            }
        }
    }
}

tasks.named("jsBrowserDevelopmentRun") {
    dependsOn("jsBrowserDevelopmentWebpack")
}
