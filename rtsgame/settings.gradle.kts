rootProject.name = "rtsgame"

// Include Trikeshed project
include(":Trikeshed")
project(":Trikeshed").projectDir = file("../Trikeshed")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
} 