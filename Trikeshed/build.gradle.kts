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
        val jvmMain by getting {
            dependencies {
                implementation("com.github.luben:zstd-jni:1.5.2-5")
                implementation("org.lz4:lz4-java:1.8.0")
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}