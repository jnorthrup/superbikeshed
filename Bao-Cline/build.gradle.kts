plugins {
    kotlin("js")
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "main.js"
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val main by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val test by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}