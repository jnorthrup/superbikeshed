plugins {
    kotlin("multiplatform") version "1.9.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    id("com.github.ben-manes.versions") version "0.51.0"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs += listOf(
                    "-Xinline-classes",
                    "-Xopt-in=kotlin.RequiresOptIn"
                )
            }
        }
    }
    
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
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                // JVM-specific dependencies
            }
        }
        
        val jsMain by getting {
            dependencies {
                // JS-specific dependencies
            }
        }
    }
}

tasks {
    register("buildAll") {
        dependsOn("build")
        dependsOn("jsBrowserProductionWebpack")
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-Xinline-classes",
                "-Xopt-in=kotlin.RequiresOptIn"
            )
        }
    }
    
    // Clean task to remove all build artifacts
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${project.buildDir}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
        }
    }
} 