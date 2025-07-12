pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "1.9.22"
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    }
}

rootProject.name = "v2superbikeshed"

// Include k2script as a subproject
include(":k2script")
include(":trikeshed-lib")
include(":platform-launcher")
include(":trikeshed-io")
include(":trikeshed-cursor")
include(":trikeshed-reactor")
include(":trikeshed-net")
include(":trikeshed-services")
include(":trikeshed-json")
include(":trikeshed-rest")
include(":trikeshed-wave")
include(":trikeshed-sumo")
include(":trikeshed-ccek")
include(":trikeshed-couchdb")
include(":trikeshed-torrent")
include(":trikeshed-channel-api")
include(":trikeshed-dht")
include(":trikeshed-uring")
include(":trikeshed-lsmr")
include(":fiduciary")
// Add more as needed if referenced