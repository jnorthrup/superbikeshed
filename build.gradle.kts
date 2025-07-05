plugins {
    id("com.github.ben-manes.versions") version "0.52.0"
    kotlin("multiplatform") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        config = files("detekt.yml")
        buildUponDefaultConfig = false
    }
}
