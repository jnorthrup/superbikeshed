plugins {
    id("com.github.ben-manes.versions") version "0.51.0" apply false
    kotlin("multiplatform") version "1.9.22" apply false
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
} 