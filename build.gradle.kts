plugins {
    id("com.github.ben-manes.versions") version "0.51.0" apply false
    kotlin("multiplatform") version "2.1.21" apply false
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
} 