import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.korge)
}

dependencies {
    add("commonMainApi", platform(libs.korge.bom))
    add("commonMainApi", libs.korge.box2d)
}

korge {
    supportBox2d()
}

kotlin {
    jvm {
        jvmToolchain(21)
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    wasmJs {
        browser()
        nodejs()
    }

    // Determine host OS
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isLinuxX64 = hostOs == "Linux"
    val isMacOsX64 = hostOs == "Mac OS X" && System.getProperty("os.arch") == "x86_64"
    val isMacOsArm64 = hostOs == "Mac OS X" && System.getProperty("os.arch") == "aarch64"

    when {
        isMacOsArm64 -> macosArm64("native")
        isMacOsX64 -> macosX64("native")
        isLinuxX64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":spacegraph-kmp"))
                implementation(project(":Trikeshed"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }

    languageSettings.languageVersion = "2.1"
    languageSettings.apiVersion = "2.1"
}
