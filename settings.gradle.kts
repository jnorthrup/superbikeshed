// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/eap")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://jitpack.io")
    }
}

rootProject.name = "superbikeshed"

// Include the Trikeshed module
include(":Trikeshed")
project(":Trikeshed").projectDir = file("Trikeshed")

// Include other modules as needed
include(":ta4k")
include(":spacegraphjs:kotlin-spacegraph")
project(":ta4k").projectDir = file("ta4k")
project(":spacegraphjs:kotlin-spacegraph").projectDir = file("spacegraphjs/kotlin-spacegraph")
