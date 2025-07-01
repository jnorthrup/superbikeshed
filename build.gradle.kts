plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
    kotlin("multiplatform") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}