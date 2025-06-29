plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.versions)
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    // JVM - Primary fiduciary platform
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // WASM - Web-based fiduciary dashboards
    wasmJs {
        browser()
        binaries.executable()
    }

    // Native - High-performance fiduciary algorithms
    val hostOs = System.getProperty("os.name")
    val isMac = hostOs.startsWith("Mac OS")
    val isLinux = hostOs.startsWith("Linux")

    if (isMac) {
        macosArm64()
        macosX64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(project(":Trikeshed"))
                // implementation(libs.okio) // Okio removed as per feedback
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(libs.kotlinx.coroutines.swing)
                implementation(kotlin("reflect"))
                implementation(libs.zstd.jni) // Zstd JNI for JVM
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

// === COMPILER CONFIGURATION ===

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}

// Add JVM test task
tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

// Custom JVM run task for interactive demo
tasks.register<JavaExec>("runJvm") {
    dependsOn("jvmMainClasses")
    group = "application"
    description = "Run Fiduciary interactive demo on JVM"
    classpath = kotlin.targets["jvm"]
        .compilations["main"]
        .output.allOutputs +
        (kotlin.targets["jvm"].compilations["main"].runtimeDependencyFiles ?: files())
    mainClass.set("fiduciary.MainJvmKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
} 