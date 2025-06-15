plugins {
    kotlin("multiplatform") version "2.1.21"
}

kotlin {
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":trikeshed"))
            }
        }
    }
} 