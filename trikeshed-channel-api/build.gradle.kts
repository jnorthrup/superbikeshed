plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm()
    macosArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-io"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
} 