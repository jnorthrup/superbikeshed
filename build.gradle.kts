plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
}
repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}
// Detect if a single target (e.g., JVM only) is requested via project property
val singleTarget: String? = findProperty("singleTarget") as String?

subprojects {
    // Propagate the singleTarget property to all subprojects
    extensions.extraProperties["singleTarget"] = singleTarget
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
// Root build file