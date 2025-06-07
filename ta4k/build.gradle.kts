plugins {
    kotlin("multiplatform") version "2.2.0-RC2"
    `maven-publish`
}

group = "org.ta4k"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://mvnrepository.com/artifact/org.jetbrains.kotlinx/")
    mavenCentral()
    gradlePluginPortal()
    google()
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_18)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
//    js(BOTH) {
//        browser {
//            commonWebpackConfig {
//                cssSupport.enabled = true
//            }
//        }
//    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            // kotlin.srcDirs are now conventional: src/commonMain/kotlin
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(project(":Review:trikeshed-core"))
            }
        }
        val commonTest by getting {
            // kotlin.srcDirs are now conventional: src/commonTest/kotlin
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDirs("src/jvmMain/kotlin") // JVM-specific code
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("com.github.haifengl:smile-kotlin:4.3.0")
            }
        }
        val jvmTest by getting {
            kotlin.srcDirs("src/jvmTest/kotlin") // JVM-specific tests
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-junit5")
                implementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
            }
        }
        val nativeMain by getting {
             // kotlin.srcDirs are now conventional: src/nativeMain/kotlin
        }
        val nativeTest by getting {
             // kotlin.srcDirs are now conventional: src/nativeTest/kotlin
        }
    }
}
