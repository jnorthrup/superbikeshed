plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    // wasmJs disabled - trash until they accept jvminline
    // wasmJs {
    //     nodejs()
    // }
    
    // Native targets based on host OS
    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> {
            macosX64()
            macosArm64()
        }
        hostOs == "Linux" -> {
            linuxX64()
            linuxArm64()
        }
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

