plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.versions)
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"
=======
    kotlin("multiplatform")
    id("com.github.ben-manes.versions") version "0.51.0"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
>>>>>>> origin/feat/core-serialization-impl

repositories {
    mavenCentral()
}

kotlin {
<<<<<<< HEAD
    jvmToolchain(21)

    // JVM - Primary trading platform
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // WASM - Web-based trading dashboards
    wasmJs {
        browser()
        binaries.executable()
    }

    // Native - High-performance trading algorithms
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
=======
    jvm()
    // wasmJs {
    //     browser()
    //     nodejs()
    // }
    
    // Example test for platform tuple
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(project(":Trikeshed"))
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
        }
>>>>>>> origin/feat/core-serialization-impl
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
    description = "Run Moneyfan interactive trading demo on JVM"
<<<<<<< HEAD
    classpath = kotlin.targets["jvm"]
        .compilations["main"]
        .output.allOutputs +
        (kotlin.targets["jvm"].compilations["main"].runtimeDependencyFiles ?: files())
    mainClass.set("moneyfan.MainJvmKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
=======
    classpath = kotlin.targets["jvm"].compilations["main"].output.allOutputs + 
                 (kotlin.targets["jvm"].compilations["main"].runtimeDependencyFiles ?: files())
    mainClass.set("moneyfan.MainJvmKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
>>>>>>> origin/feat/core-serialization-impl
