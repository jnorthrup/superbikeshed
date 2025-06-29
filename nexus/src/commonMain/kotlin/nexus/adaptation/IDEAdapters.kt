package nexus.adaptation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nexus.telemetry.K2ScriptTelemetryEvent

class IntelliJAdapter(
    private val telemetryEndpoint: String = "http://localhost:8080/api/v1/telemetry/event"
) {
    // Stubbed implementation - would require Ktor dependencies for full functionality
    suspend fun sendTelemetry(event: K2ScriptTelemetryEvent) {
        val jsonBody = Json.encodeToString(event)
        println("Telemetry stub: would send to $telemetryEndpoint - $jsonBody")
    }

    // Example usage: call this when a k2script execution starts, succeeds, or errors
    suspend fun reportExecStart(scriptName: String, nexusVersion: String, k2scriptVersion: String) {
        sendTelemetry(
            K2ScriptTelemetryEvent(
                timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                scriptName = scriptName,
                eventType = "EXEC_START",
                platform = "INTELLIJ_PLUGIN",
                nexusVersion = nexusVersion,
                k2scriptVersion = k2scriptVersion
            )
        )
    }
} 