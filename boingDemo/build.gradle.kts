plugins {
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
    }
}

compose.desktop {
    application {
        mainClass = "com.example.boingdemo.DesktopCanvasKt"
    }
}
