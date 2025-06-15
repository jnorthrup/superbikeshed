plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0" apply false
    id("com.diffplug.spotless") version "6.13.0"
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

kotlin {
    jvm()
}

spotless {
    kotlin {
        target("**/*.kt")
        ktfmt()
    }
}
