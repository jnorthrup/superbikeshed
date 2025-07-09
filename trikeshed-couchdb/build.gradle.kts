plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        // binaries block removed; not needed for JVM library/module
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":trikeshed-net"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
}

