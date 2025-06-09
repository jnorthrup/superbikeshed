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
        // Optionally, log the stack trace or handle specific exceptions
    }
    return details
}

val currentOsFamily = getOsFamily()
val currentOsVersionDetails = getOsVersion(currentOsFamily)
println("Detected OS Family: $currentOsFamily")
println("Detected OS Version Details: $currentOsVersionDetails")

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
    macosX64() // New target
    macosArm64() // New target for Apple Silicon
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

        // OS-specific cinterop settings
        compilations["main"].cinterops.configureEach {
            val resolvedOsFamily = getOsFamily() // Renamed to avoid conflict
            // val resolvedOsVersion = getOsVersion(resolvedOsFamily) // Potentially needed

            compilerOpts("-DTARGET_OS_FAMILY=${resolvedOsFamily}") // Use compilerOpts with -D

            println("Cinterop for $name on $konanTarget (OS: $resolvedOsFamily): Applying settings...")

            when (resolvedOsFamily) {
                "linux" -> {
                    compilerOpts("-DENABLE_IO_URING=1")
                    println("  Applied Linux specific: -DENABLE_IO_URING=1")
                }
                "macos" -> {
                    compilerOpts("-DAPPLE_ASYNC_POSIX=1")
                    println("  Applied macOS specific: -DAPPLE_ASYNC_POSIX=1")
                }
                else -> {
                    println("  No specific OS cinterop flags applied for $resolvedOsFamily.")
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            // Default srcDir is "src/commonMain/kotlin" which is "Review/trikeshed-core/src/commonMain/kotlin"
            // All new files like TrikeShedCore.kt, serialization/JsonSerialization.kt, services/IncrementalDataService.kt
            // are correctly placed under this path. No need to reference $rootDir for these.
            // Original conflicting files from the root project will not be included here.
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
            // Default srcDir is "src/linuxX64Main/kotlin". For any trikeshed-core specific linuxX64 code.
            // Do NOT include $rootDir/src/linuxMain/kotlin etc. here.
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
            // Default srcDir for macosMain would be something like "src/macosMain/kotlin".
            // Do NOT include $rootDir/src/macosMain/kotlin etc. here.
        }

        val macosX64Main by getting {
            dependsOn(macosMain)
        }

        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        // Removed the problematic 'val nativeTest by getting' block

        val nativeTestShared by creating { // Renamed from nativeTest for clarity
            dependsOn(commonTest)
            // If you have shared native test sources:
            // kotlin.srcDir("$rootDir/src/nativeTest/kotlin")
        }

        // Ensure specific native test source sets like linuxX64Test, macosX64Test depend on nativeTestShared
        // Example for linuxX64Test (if it were defined, add if needed):
        // val linuxX64Test by getting {
        //     dependsOn(nativeTestShared)
        //     kotlin.srcDir("$rootDir/src/linuxX64Test/kotlin") // Or similar
        // }


        val macosTest by creating {
            dependsOn(nativeTestShared) // Ensure nativeTest exists and is correctly configured
            kotlin.srcDir("$rootDir/src/macosTest/kotlin")
        }

        val macosX64Test by getting {
            dependsOn(macosTest)
        }

        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

        // Define other source sets (nativeMain, etc.) similarly if they should also
        // draw from the root project's structure for this module.
        // For now, focus on common, jvm, linuxX64, js.
        // Ensure all test source sets are correctly defined and dependent.
        // For example, jvmTest should depend on commonTest.
        val jvmTest by getting {
            dependsOn(commonTest) // Explicit dependency
            dependencies {
                implementation(kotlin("test-junit")) // For running tests on JVM
            }
        }
        val jsTest by getting {
            dependsOn(commonTest) // Explicit dependency
            dependencies {
                implementation(kotlin("test-js")) // For running tests on JS
            }
        }

        // Define linuxX64Test and other native test source sets
        val linuxX64Test by getting {
            dependsOn(nativeTestShared)
            // kotlin.srcDir("$rootDir/src/linuxX64Test/kotlin") // if specific sources exist
        }

        // macosX64Test and macosArm64Test were already correctly defined earlier and depend on macosTest
        // which in turn depends on nativeTestShared. No need to redefine them here.
    }
    targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java) {
        binaries.executable() // Ensure test executables are built for native targets
    }
}
