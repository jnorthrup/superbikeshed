plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                // Add common dependencies here
            }
        }
        commonTest {
            dependencies {
                implementation(project(":trikeshed-lib"))
                kotlin("test")
            }
        }
    }
}