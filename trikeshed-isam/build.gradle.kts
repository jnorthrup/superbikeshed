plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm { }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-cursor"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime")
            }
        }
        commonTest {
            // Removed conflicting kotlin("test") dependency
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M1")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0-M1")
            }
        }
    }
}
