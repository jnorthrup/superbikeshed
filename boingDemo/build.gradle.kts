plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.6.0"
}

kotlin {
    jvm {
        jvmToolchain(21)
        withJava()
    }
    wasmJs {
        browser()
        nodejs()
    }
    
    // Platform detection for native target
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
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

        jvmMain {
            dependsOn(commonMain.get())
        }

        val desktopMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val nativeMain by creating {
            dependsOn(desktopMain)
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

        wasmJsMain { 
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.html.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.boingdemo.DesktopCanvasKt"
    }
}
