import java.io.File
import java.util.concurrent.TimeUnit

fun getOsFamily(): String {
    val osName = System.getProperty("os.name").toLowerCase()
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
    `maven-publish`
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"


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

            defines("TARGET_OS_FAMILY", resolvedOsFamily)

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
            // Include all sources from borg/trikeshed/core and borg/trikeshed/lib
            kotlin.srcDirs(
                "$rootDir/src/commonMain/kotlin/borg/trikeshed/core",
                "$rootDir/src/commonMain/kotlin/borg/trikeshed/lib"
            )
            // Exclude conflicting general 'core' and 'com/example/trikeshedcore' from this module's compilation
            // These paths are relative to $rootDir/src/commonMain/kotlin, so they should be fine as they are
            // not under borg/trikeshed/core or borg/trikeshed/lib which are now the source dirs.
            // However, to be safe and ensure clarity, if these are meant to be excluded from the root,
            // they should be in the root build.gradle.kts. Let's assume they are for any other sources
            // that might accidentally be picked up if srcDirs was broader. Given the new specific srcDirs,
            // these excludes might not be strictly necessary here anymore but are harmless.
            kotlin {
                exclude("$rootDir/src/commonMain/kotlin/core/**") // This effectively means these paths won't be included if they aren't under the specified srcDirs.
                exclude("$rootDir/src/commonMain/kotlin/com/example/trikeshedcore/**")
            }

            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }

        val jvmMain by getting {
            kotlin.srcDir("$rootDir/src/jvmMain/kotlin")
            kotlin {
                exclude("**/QuicCurl.kt")
                exclude("**/QuicMain.kt")
            }
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }

        val linuxX64Main by getting {
            kotlin.srcDir("$rootDir/src/posixMain/kotlin")
            kotlin.srcDir("$rootDir/src/linuxMain/kotlin")
            // No specific native dependencies for coroutines/datetime listed for now,
            // relying on commonMain's. Add if build shows they are needed.
        }

        val jsMain by getting {
            // Point to specific JS sources for trikeshed-core if they exist,
            // and potentially common JS libs if needed by core.
            kotlin.setSrcDirs(files(
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/core/", // If core-specific JS exists
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/lib/",   // If lib has JS parts
                "$rootDir/src/jsMain/kotlin/lib/" // A general lib for JS too
            ))
            kotlin {
                exclude("$rootDir/src/jsMain/kotlin/com/example/trikeshedcore/**")
                exclude("$rootDir/src/jsMain/kotlin/core/**") // Exclude general core JS if it exists
            }
            dependencies {
                implementation(kotlin("stdlib-js"))
                // Add other js-specific dependencies if necessary
            }
        }

        // macOS source sets
        val nativeMain by getting

        val macosMain by creating {
            dependsOn(nativeMain)
            kotlin.srcDir("$rootDir/src/posixMain/kotlin")
            kotlin.srcDir("$rootDir/src/macosMain/kotlin")
        }

        val macosX64Main by getting {
            dependsOn(macosMain)
        }

        val macosArm64Main by getting {
            dependsOn(macosMain)
        }

        val nativeTest by getting

        val macosTest by creating {
            dependsOn(nativeTest)
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
    }
}
