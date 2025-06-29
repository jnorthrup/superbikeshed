plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

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
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlin:kotlin-test-common")
            implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.junit.jupiter.api)
            runtimeOnly(libs.junit.jupiter.engine)
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
