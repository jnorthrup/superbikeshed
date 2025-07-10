plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"



kotlin {
    jvm()
    
    macosArm64()
    macosX64()
    linuxX64()
    linuxArm64()
    mingwX64()
    
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-channel-api"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":trikeshed-lib"))
            }
        }
    }
}