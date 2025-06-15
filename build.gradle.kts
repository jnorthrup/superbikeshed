plugins {
    kotlin("multiplatform") version "2.1.21"
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
