package nexus.adaptation

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nexus.telemetry.K2ScriptTelemetryEvent

class IntelliJAdapter(
    private val telemetryEndpoint: String = "http://localhost:8080/api/v1/telemetry/event"
) {
    private val client = HttpClient(CIO)

    suspend fun sendTelemetry(event: K2ScriptTelemetryEvent) {
        val jsonBody = Json.encodeToString(event)
        client.post(telemetryEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
    }

    // Example usage: call this when a k2script execution starts, succeeds, or errors
    suspend fun reportExecStart(scriptName: String, nexusVersion: String, k2scriptVersion: String) {
        sendTelemetry(
            K2ScriptTelemetryEvent(
                timestamp = System.currentTimeMillis(),
                scriptName = scriptName,
                eventType = "EXEC_START",
                platform = "INTELLIJ_PLUGIN",
                nexusVersion = nexusVersion,
                k2scriptVersion = k2scriptVersion
            )
        )
    }
} 