plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"



kotlin {
    jvm()
    
    
    // Native targets based on host OS
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64()
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" && hostArch == "aarch64" -> linuxArm64()
        hostOs == "Linux" -> linuxX64()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                                implementation(project(":trikeshed-lib"))
                                implementation(project(":trikeshed-io"))
                                implementation(project(":trikeshed-channel-api"))
                                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                                implementation("org.jetbrains.kotlinx:kotlinx-datetime")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

