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

rootProject.name = "dsel-moneyfan-demo-root"

// Include the new demo module
include(":ta4k-spacegraph-moneyfan-demo")

// Include existing local modules as dependencies
include(":ta4k")
include(":Review") // Provides TrikeShedCore DSEL
include(":Review:trikeshed-core") // Include the trikeshed-core subproject
include(":spacegraphjs:kotlin-spacegraph") // Provides AgentAPI for SpaceGraph

// Define project directories for these local modules
// Adjust paths if the directory structure of the overall project is different.
// These paths are assumed relative to the root project directory where this settings.gradle.kts resides.
project(":ta4k-spacegraph-moneyfan-demo").projectDir = file("ta4k-spacegraph-moneyfan-demo")
project(":ta4k").projectDir = file("ta4k") // Assuming 'ta4k' is a sibling directory to the demo module
project(":Review").projectDir = file("Review") // Assuming 'Review' is a sibling
project(":Review:trikeshed-core").projectDir = file("Review/trikeshed-core") // Include the trikeshed-core subproject
project(":spacegraphjs:kotlin-spacegraph").projectDir = file("spacegraphjs/kotlin-spacegraph") // Assuming nested structure
