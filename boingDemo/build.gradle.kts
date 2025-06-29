plugins {
<<<<<<< HEAD
    kotlin("multiplatform") version "2.1.21"
    id("org.jetbrains.compose") version "1.6.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
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
                implementation(kotlin("stdlib-common"))
=======
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.6.0"
}

kotlin {
    jvm {
        jvmToolchain(21)
        withJava()
    }
    // wasmJs {
    //     browser()
    //     nodejs()
    // }
    
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
>>>>>>> origin/feat/core-serialization-impl
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

<<<<<<< HEAD
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
=======
        jvmMain {
            dependsOn(commonMain.get())
        }

        val desktopMain by creating {
            dependsOn(commonMain.get())
>>>>>>> origin/feat/core-serialization-impl
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

<<<<<<< HEAD
        val jvmTest by getting

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        if (isMac) {
            val macosArm64Main by getting { dependsOn(nativeMain) }
            val macosX64Main by getting { dependsOn(nativeMain) }
            val macosArm64Test by getting { dependsOn(nativeTest) }
            val macosX64Test by getting { dependsOn(nativeTest) }
        }

        if (isLinux) {
            val linuxX64Main by getting { dependsOn(nativeMain) }
            val linuxArm64Main by getting { dependsOn(nativeMain) }
            val linuxX64Test by getting { dependsOn(nativeTest) }
            val linuxArm64Test by getting { dependsOn(nativeTest) }
        }

        if (isMingwX64) {
            val mingwX64Main by getting { dependsOn(nativeMain) }
            val mingwX64Test by getting { dependsOn(nativeTest) }
        }
=======
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

        // wasmJsMain { 
        //     dependsOn(commonMain.get())
        //     dependencies {
        //         implementation(compose.html.core)
        //     }
        // }
>>>>>>> origin/feat/core-serialization-impl
    }
}

compose.desktop {
    application {
        mainClass = "com.example.boingdemo.DesktopCanvasKt"
    }
}
