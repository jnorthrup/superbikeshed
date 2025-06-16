plugins {
    kotlin("multiplatform") version "2.1.21"
}

kotlin {
    jvm()
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