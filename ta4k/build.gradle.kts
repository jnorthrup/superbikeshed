plugins {
    kotlin("multiplatform") version "2.1.21"
    `maven-publish`
    id("com.github.ben-manes.versions")

}

group = "org.ta4k"
version = "1.0-SNAPSHOT"


kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    wasmJs {
        browser()
        // If commonWebpackConfig is needed, it can be configured here, for example:
        // browser {
        //     commonWebpackConfig {
        //         cssSupport {
        //             enabled.set(true)
        //         }
        //     }
        // }
        binaries.executable() // Ensure this is valid for wasmJs, or adjust if needed
    }
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" -> {
            if (hostArch == "aarch64") {
                macosArm64()
            } else {
                macosX64()
            }
        }
        hostOs == "Linux" -> {
            if (hostArch == "aarch64") {
                linuxArm64()
            } else {
                linuxX64()
            }
        }
        isMingwX64 -> mingwX64()
        else -> throw GradleException("Host OS is not supported in Kotlin/Native ($hostOs, $hostArch)")
    }

    sourceSets {
        val commonMain by getting {
            // kotlin.srcDirs are now conventional: src/commonMain/kotlin
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("com.ionspin.kotlin:bignum:0.3.9")
            }
        }
        val commonTest by getting {
            // kotlin.srcDirs are now conventional: src/commonTest/kotlin
            dependencies {
                implementation(kotlin("test")) // This should cover common test needs
            }
        }
        val jvmMain by getting {
            // kotlin.srcDirs("src/jvmMain/kotlin") // Conventional, no need to specify if following convention
            dependencies {
                implementation(kotlin("stdlib-jdk8")) // For JVM specific APIs if needed beyond common
                implementation("com.github.haifengl:smile-kotlin:4.3.0")
            }
        }
        val jvmTest by getting {
            // kotlin.srcDirs("src/jvmTest/kotlin") // Conventional
            dependencies {
                implementation(kotlin("test-junit5")) // JUnit 5 for JVM tests
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.2") // Align with moneyfan
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2") // Align with moneyfan
            }
        }
        // jsMain and jsTest are removed in favor of wasmJsMain and wasmJsTest
        val wasmJsMain by getting {
            dependencies {
                // stdlib-js is usually added by default with wasmJs target
            }
        }
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test")) // Common test for wasmJs
            }
        }
    }
}
