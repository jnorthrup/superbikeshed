// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "dsel-moneyfan-demo-root"

// Include the new demo module
include(":ta4k-spacegraph-moneyfan-demo")

// Include existing local modules as dependencies
include(":ta4k")
include(":Review") // Provides TrikeShedCore DSEL
include(":spacegraphjs:kotlin-spacegraph") // Provides AgentAPI for SpaceGraph

// Define project directories for these local modules
// Adjust paths if the directory structure of the overall project is different.
// These paths are assumed relative to the root project directory where this settings.gradle.kts resides.
project(":ta4k-spacegraph-moneyfan-demo").projectDir = file("ta4k-spacegraph-moneyfan-demo")
project(":ta4k").projectDir = file("ta4k") // Assuming 'ta4k' is a sibling directory to the demo module
project(":Review").projectDir = file("Review") // Assuming 'Review' is a sibling
project(":spacegraphjs:kotlin-spacegraph").projectDir = file("spacegraphjs/kotlin-spacegraph") // Assuming nested structure
