plugins {
    kotlin("multiplatform")
    id("com.github.ben-manes.versions") version "0.51.0"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    wasmJs {
        browser()
        nodejs()
    }
    
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
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            implementation("org.ta4j:ta4j-core:0.15")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
        }
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
    classpath = kotlin.targets["jvm"].compilations["main"].output.allOutputs + 
                 (kotlin.targets["jvm"].compilations["main"].runtimeDependencyFiles ?: files())
    mainClass.set("moneyfan.MainJvmKt")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}