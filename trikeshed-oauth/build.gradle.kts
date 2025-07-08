plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":SSH"))
                implementation(project(":trikeshed-http"))
                implementation(project(":trikeshed-json"))
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        commonTest {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-ccek"))
                implementation(project(":SSH"))
                implementation(project(":trikeshed-http"))
                implementation(project(":trikeshed-json"))
                
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
} 