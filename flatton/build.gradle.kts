plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    jvm()
    wasmJs {
        browser()
        binaries.executable()
    }

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMac = hostOs.startsWith("Mac OS")
    val isLinux = hostOs.startsWith("Linux")

    if (isMac) {
        macosArm64()
        macosX64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    } else if (isMingwX64) {
        mingwX64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlinx-serialization-scanner"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        if (isMac) {
            val macosArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }

            val macosX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }

        if (isLinux) {
            val linuxX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }

            val linuxArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }

        if (isMingwX64) {
            val mingwX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
    }
}

// Disable linting to keep code terse
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}
