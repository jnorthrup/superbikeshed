plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm()
    macosArm64()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-couchdb"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime")
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
            }
        }
    }
}