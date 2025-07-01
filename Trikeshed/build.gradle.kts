plugins {
    kotlin("multiplatform")
    id("io.gitlab.arturbosch.detekt")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    // Configure native targets for macOS and Linux
    // This will create macosX64, macosArm64, linuxX64, linuxArm64 targets
    // based on the host OS and architecture.
    val hostOs = System.getProperty("os.name")
    val isMac = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isArm64 = System.getProperty("os.arch") == "aarch64"

    if (isMac) {
        macosX64()
        macosArm64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val jvmMain by getting {
            dependsOn(commonMain)
        }
        val nativeMain by getting {
            dependsOn(commonMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
        val nativeTest by getting {
            dependsOn(nativeMain)
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
