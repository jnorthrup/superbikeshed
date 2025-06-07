plugins {
    kotlin("multiplatform") version "2.2.0-RC2"
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                // No specific dependencies needed for this simple case
            }
        }
        val jsMain by getting {
            dependencies {
                // No specific JS dependencies needed
            }
        }
    }
}
