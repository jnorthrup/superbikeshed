plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        binaries {
            executable {
                mainClass = "k2script.K2scriptKt"
            }
        }
    }
    
    // Native target based on host OS
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64()
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" -> linuxX64()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        jvmTest {
            // JVM-specific test dependencies
        }
    }
}
