plugins {
    kotlin("multiplatform")
    id("io.gitlab.arturbosch.detekt")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}