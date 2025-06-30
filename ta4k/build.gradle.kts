plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
    alias(libs.plugins.versions)
=======
    kotlin("multiplatform")
    `maven-publish`
    id("com.github.ben-manes.versions")
>>>>>>> origin/feat/core-serialization-impl
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

group = "org.ta4k"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
<<<<<<< HEAD

=======
    
>>>>>>> origin/feat/core-serialization-impl
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
<<<<<<< HEAD

    wasmJs {
        browser()
        binaries.executable()
    }

=======
    
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }
    
>>>>>>> origin/feat/core-serialization-impl
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
            dependencies {
<<<<<<< HEAD
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

                api("org.knowm.xchange:xchange-core:5.2.0")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                api("org.slf4j:slf4j-api:2.0.9")

                implementation(kotlin("stdlib-common"))
                implementation("com.ionspin.kotlin:bignum:0.3.9")
                
                // Add dependency on Trikeshed for Indexed<T> and other core types
                implementation(project(":Trikeshed"))
            }
        }

=======
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.datetime.get()}")
                implementation("com.ionspin.kotlin:bignum:0.3.9")
                implementation(project(":Trikeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
<<<<<<< HEAD

=======
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("com.github.haifengl:smile-kotlin:4.3.0")
                implementation("com.binance.api:binance-api-client:1.0.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${libs.versions.coroutines.get()}")
            }
        }
<<<<<<< HEAD

        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.datetime.get()}")
                // Removed Trikeshed dependency
            }
        }

=======
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.datetime.get()}")
                implementation(project(":Trikeshed"))
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:${libs.versions.junit.get()}")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:${libs.versions.junit.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${libs.versions.coroutines.get()}")
            }
        }
    }
}
