package nexus

import nexus.flow.runNexusFlow

actual suspend fun runInteractivePlatform() {
    // Use the new flow-based interactive system
    runNexusFlow()
}