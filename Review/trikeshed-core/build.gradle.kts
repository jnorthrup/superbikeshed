import java.io.File
import java.util.concurrent.TimeUnit

// Removed OS detection functions and variables to avoid configuration cache issues
// We'll use target-specific properties instead

plugins {
    kotlin("multiplatform")
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
        compilations["main"].cinterops {
            val native by creating {
                val resolvedOsFamily = getOsFamily()
                defFile(project.file("src/linuxMain/cinterop/native.def"))
                packageName("cinterop.native")
                compilerOpts("-DTARGET_OS_FAMILY=$resolvedOsFamily")
            }
        }

        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                val resolvedOsFamily = getOsFamily()
                val currentTarget = this@withType
                println("Cinterop for ${currentTarget.name} on ${currentTarget.konanTarget} (OS: $resolvedOsFamily): Applying settings...")

                when (resolvedOsFamily) {
                    "linux" -> {
                        freeCompilerArgs.add("-DENABLE_IO_URING=1")
                        println("  Applied Linux specific: -DENABLE_IO_URING=1")
                    }
                    "macos" -> {
                        freeCompilerArgs.add("-DAPPLE_ASYNC_POSIX=1")
                        println("  Applied macOS specific: -DAPPLE_ASYNC_POSIX=1")
                    }
                    else -> {
                        println("  No specific OS cinterop flags applied for $resolvedOsFamily.")
                    }
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
            kotlin {
                exclude("$rootDir/src/commonMain/kotlin/core/**")
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
        }

        val jsMain by getting {
            kotlin.setSrcDirs(files(
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/core/",
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/lib/",
                "$rootDir/src/jsMain/kotlin/lib/"
            ))
            kotlin {
                exclude("$rootDir/src/jsMain/kotlin/com/example/trikeshedcore/**")
                exclude("$rootDir/src/jsMain/kotlin/core/**")
            }
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }

        // Create nativeMain source set
        val nativeMain by creating {
            dependsOn(commonMain)
        }

        // macOS source sets
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

        // Create nativeTest source set
        val nativeTest by creating {
            dependsOn(commonTest)
        }

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
    }
}
