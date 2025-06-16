rootProject.name = "rtsgame"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            version("kotlin", "1.9.22")
            version("coroutines", "1.7.3")
            version("serialization", "1.6.0")
        }
    }
} 