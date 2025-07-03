plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    }
}

detekt {
    config = files("detekt.yml")
    buildUponDefaultConfig = true
}

