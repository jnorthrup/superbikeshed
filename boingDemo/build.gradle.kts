plugins {
    kotlin("multiplatform") version "2.1.21"
    id("org.jetbrains.compose") version "1.6.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

kotlin {
    jvm {
        jvmToolchain(21)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows && isArm64 -> mingwArm64()
        isWindows -> mingwX64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val jvmTest by getting

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Connect native targets properly
        if (isMacOS && isArm64) {
            macosArm64Main {
                dependsOn(nativeMain)
            }
        } else if (isMacOS) {
            macosX64Main {
                dependsOn(nativeMain)
            }
        } else if (isLinux && isArm64) {
            linuxArm64Main {
                dependsOn(nativeMain)
            }
        } else if (isLinux) {
            linuxX64Main {
                dependsOn(nativeMain)
            }
        } else if (isWindows && isArm64) {
            mingwArm64Main {
                dependsOn(nativeMain)
            }
        } else if (isWindows) {
            mingwX64Main {
                dependsOn(nativeMain)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.boingdemo.DesktopCanvasKt"
    }
}
