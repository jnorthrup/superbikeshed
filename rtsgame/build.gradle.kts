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
        
    }
}
