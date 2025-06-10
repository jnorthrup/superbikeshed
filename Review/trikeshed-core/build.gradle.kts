import java.io.File
import java.util.concurrent.TimeUnit

fun getOsFamily(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") || osName.contains("darwin") -> "macos"
        osName.contains("nix") || osName.contains("nux") -> "linux"
        osName.contains("win") -> "windows"
        else -> "unknown"
    }
}

fun getOsVersion(osFamily: String): Map<String, String> {
    val details = mutableMapOf<String, String>()
    try {
        when (osFamily) {
            "linux" -> {
                File("/etc/os-release").forEachLine { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().removeSurrounding("\"")
                        if (key == "ID") details["id"] = value
                        if (key == "VERSION_ID") details["versionId"] = value
                    }
                }
            }
            "macos" -> {
                val process = ProcessBuilder("sw_vers", "-productVersion").start()
                process.waitFor(5, TimeUnit.SECONDS)
                val output = process.inputStream.bufferedReader().readText().trim()
                if (output.isNotEmpty()) {
                    details["productVersion"] = output
                }
                val error = process.errorStream.bufferedReader().readText().trim()
                if (error.isNotEmpty()) {
                    println("Error getting macOS version: $error")
                }
            }
        }
    } catch (e: Exception) {
        println("Error getting OS version: ${e.message}")
    }
    return details
}

val currentOsFamily = getOsFamily()
val currentOsVersionDetails = getOsVersion(currentOsFamily)

plugins {
    kotlin("multiplatform")
    // id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20" // Removed
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google() // Often needed for Android or other Google libraries, good to have
    // You might also need specific repositories like:
    // maven("https://plugins.gradle.org/m2/") for Gradle plugins if not automatically resolved
    // maven("https://europe-west3-maven.pkg.dev/androidx-dev/androidx-public") for androidx snapshot
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    linuxX64()
    macosX64()
    macosArm64()
    js(IR) { // Use the IR compiler
        browser { // Target browser environment
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
        outputModuleName.set("trikeshedCore") // Define a module name for JS
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        // Common compiler options for all native targets can be set here if needed
        // compilerOptions.options.add("-linker-option-...") // Example

        compilations["main"].cinterops.configureEach {
            val resolvedOsFamily = getOsFamily()
            compilerOpts("-DTARGET_OS_FAMILY=${resolvedOsFamily}")

            when (resolvedOsFamily) {
                "linux" -> {
                    compilerOpts("-DENABLE_IO_URING=1")
                }
                "macos" -> {
                    compilerOpts("-DAPPLE_ASYNC_POSIX=1")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0") // Removed
            }
        }

        val jvmMain by getting {
            // Default srcDir is "src/jvmMain/kotlin". If trikeshed-core needs specific JVM implementations
            // for its common code, they would go into "Review/trikeshed-core/src/jvmMain/kotlin".
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val nativeMainShared by creating {
            dependsOn(commonMain)
            // If you have shared native sources for trikeshed-core, they go in "Review/trikeshed-core/src/nativeMainShared/kotlin" (or similar)
            // e.g. kotlin.srcDir("src/nativeMainShared/kotlin")
        }

        val linuxX64Main by getting {
            dependsOn(nativeMainShared)
        }

        val jsMain by getting {
            // Default srcDir is "src/jsMain/kotlin". For any trikeshed-core specific JS code.
            // Do NOT include $rootDir/src/jsMain/kotlin/* here.
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }

        // macOS source sets
        val macosMain by creating {
            dependsOn(nativeMainShared)
        }

        val macosX64Main by getting {
            dependsOn(macosMain)
        }

        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        val nativeTestShared by creating {
            dependsOn(commonTest)
        }

        val macosTest by creating {
            dependsOn(nativeTestShared)
            kotlin.srcDir("$rootDir/src/macosTest/kotlin")
        }

        val macosX64Test by getting {
            dependsOn(macosTest)
        }

        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

        val jvmTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        val linuxX64Test by getting {
            dependsOn(nativeTestShared)
        }
    }
    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java) {
        binaries.executable()
    }
}
