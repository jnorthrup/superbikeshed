plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    macosArm64()
    macosX64()
    js(IR) {
        browser()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                // JVM-specific dependencies
            }
        }
        macosArm64Main {
            dependencies {
                // Native dependencies for macOS
            }
        }
        macosX64Main {
            dependencies {
                // Native dependencies for macOS
            }
        }
        jsMain {
            dependencies {
                // JS-specific dependencies
            }
        }
    }
}
