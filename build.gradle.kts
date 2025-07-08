buildscript {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlin/kotlin-mpp") }
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
    }
}

// Versions are managed in gradle.properties

plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
}

val hostOs = System.getProperty("os.name")
val isMacOs = hostOs == "Mac OS X"
val isLinux = hostOs == "Linux"

allprojects {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
    
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-serialization-json:${findProperty("kotlinxSerializationJsonVersion")}")
            force("org.jetbrains.kotlinx:kotlinx-serialization-core:${findProperty("kotlinxSerializationJsonVersion")}")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:${findProperty("kotlinxCoroutinesCoreVersion")}")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-test:${findProperty("kotlinxCoroutinesCoreVersion")}")
            force("org.jetbrains.kotlinx:kotlinx-datetime:${findProperty("kotlinxDatetimeVersion")}")
        }
    }
    
    // Centralized Kotlin Multiplatform configuration
    afterEvaluate {
        if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
                // Disable wasmJs for all projects due to dependency issues
                // wasmJs { browser(); nodejs() } // Temporarily disabled
                
                // Configure native targets based on host OS
                when {
                    isMacOs -> {
                        macosX64()
                        macosArm64()
                    }
                    isLinux -> {
                        linuxX64()
                        linuxArm64()
                    }
                }
            }
        }
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            allWarningsAsErrors.set(false)
            suppressWarnings.set(false)  // Changed to false to see all warnings
            freeCompilerArgs.addAll(
                "-Xallow-unstable-dependencies",
                "-Xskip-prerelease-check"
            )
        }
    }
}


