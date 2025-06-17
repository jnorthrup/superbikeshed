plugins {
    kotlin("multiplatform") version "1.9.23" // Use a recent Kotlin version
    kotlin("plugin.serialization") version "1.9.23" // For serialization if needed later
}

repositories {
    mavenCentral()
    google() // For Android dependencies, if ever needed
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(IR) { // IR backend is preferred
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
        binaries.executable()
    }
    // Placeholder for native targets if needed in the future
    // macosX64("nativeMacosX64")
    // linuxX64("nativeLinuxX64")
    // mingwX64("nativeMingwX64")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Example coroutines version
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Example serialization version
                // Placeholder for KMath/Multik (numerical)
                // implementation("org.jetbrains.kotlinx:kmath-core:0.3.1")
                // implementation("org.jetbrains.kotlinx:multik-api:0.2.0")
                // implementation("org.jetbrains.kotlinx:multik-default:0.2.0")

                // Placeholder for Kotlin DataFrame
                // implementation("org.jetbrains.kotlinx:dataframe:0.12.0")

                // Datetime library (official kotlinx-datetime)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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
                // JVM specific dependencies can go here
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
                // JS specific dependencies can go here
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        // Placeholder for native source sets
        // val nativeMacosX64Main by getting
        // val nativeLinuxX64Main by getting
        // val nativeMingwX64Main by getting
        // val nativeMain by creating {
        // dependsOn(commonMain)
        // nativeMacosX64Main.dependsOn(this)
        // nativeLinuxX64Main.dependsOn(this)
        // nativeMingwX64Main.dependsOn(this)
        // }
    }
}
