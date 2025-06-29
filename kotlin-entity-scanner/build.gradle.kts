@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
<<<<<<< HEAD
                // No dependencies needed
            }
        }

=======
                implementation(project(":Trikeshed"))
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> origin/feat/core-serialization-impl
