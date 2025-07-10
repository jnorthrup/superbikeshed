plugins {
    kotlin("multiplatform") version "1.9.23"
}

repositories {
    mavenCentral()
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
}