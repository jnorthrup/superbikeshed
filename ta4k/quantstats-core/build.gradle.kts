plugins {
    kotlin("multiplatform") // Version should be inherited from parent
}

kotlin {
    jvm() // Define a JVM target
    js(IR) { // Define a JS target
        browser()
        binaries.executable()
    }
    // Minimal native target, e.g., macosX64, if easily configured
    // Or omit native for initial setup simplicity if it causes issues
    val hostOs = System.getProperty("os.name")
    if (hostOs == "Mac OS X") {
        macosX64("nativeMacos") // Example native target
    } else if (hostOs == "Linux") {
        linuxX64("nativeLinux") // Example native target
    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                // NO other kotlinx dependencies for now, per CLAUDE.md and user feedback.
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                // Dependency on smile-kotlin if needed by quantstats logic for JVM
                // implementation("com.github.haifengl:smile-kotlin:4.3.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        if (hostOs == "Mac OS X") {
            val nativeMacosMain by getting
            val nativeMacosTest by getting
        } else if (hostOs == "Linux") {
            val nativeLinuxMain by getting
            val nativeLinuxTest by getting
        }
    }
}
