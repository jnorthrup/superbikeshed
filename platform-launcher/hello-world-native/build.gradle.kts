plugins {
    kotlin("multiplatform")
}

kotlin {
    macosArm64 {
        binaries {
            executable("hello") {
                entryPoint = "main"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting
        val macosArm64Main by getting
    }
}