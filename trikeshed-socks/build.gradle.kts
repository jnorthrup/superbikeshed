plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js { nodejs() }
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