package nexus

import nexus.interactive.NexusInteractiveLLM

actual suspend fun runInteractivePlatform() {
    NexusInteractiveLLM.runInteractive()
}