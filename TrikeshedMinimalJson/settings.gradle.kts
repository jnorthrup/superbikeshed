pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        // maven("https.maven.pkg.jetbrains.space/public/p/compose/dev") // Removed as per CLAUDE.md general deps
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // maven("https.maven.pkg.jetbrains.space/public/p/compose/dev") // Removed as per CLAUDE.md general deps
    }
}

rootProject.name = "TrikeshedMinimalJson"
