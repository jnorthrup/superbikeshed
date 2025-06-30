package nexus

import nexus.runtime.NexusRuntime

actual suspend fun runInteractivePlatform() {
    // Use the ultimate Nexus Runtime with all advanced features
    NexusRuntime.runInteractive()
}