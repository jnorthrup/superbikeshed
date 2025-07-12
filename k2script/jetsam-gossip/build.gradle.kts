plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "org.k2script.jetsamgossip"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation("com.google.code.gson:gson")
                implementation("com.fasterxml.jackson.core:jackson-databind")
            }
        }
    }
} 