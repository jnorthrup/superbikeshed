plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0-RC2"
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

kotlin {
    jvm {
        compilations.all {
            compilerOptions.configure {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}
