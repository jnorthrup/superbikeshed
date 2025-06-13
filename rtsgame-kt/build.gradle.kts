plugins {
    kotlin("multiplatform") version "1.9.23" // Use a recent Kotlin version
}

repositories {
    mavenCentral()
}

kotlin {
    linuxX64("native") { // Define a native target, e.g., linuxX64
        binaries {
            executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Add common dependencies here if needed later
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test")) // Add Kotlin test library
            }
        }
        val nativeMain by getting {
            // Dependencies specific to the native target
        }
    }
}

// Ensure the wrapper task is available to generate Gradle wrapper scripts
tasks.wrapper {
    gradleVersion = "8.5" // Use a recent Gradle version
    distributionType = Wrapper.DistributionType.ALL
}
