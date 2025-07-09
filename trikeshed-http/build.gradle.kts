plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    // js { nodejs() }  // Disabled - focus on JVM/native
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