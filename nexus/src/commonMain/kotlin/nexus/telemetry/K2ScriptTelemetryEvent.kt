package nexus.telemetry

import kotlinx.serialization.Serializable

data class K2ScriptTelemetryEvent(
    val timestamp: Long,
    val scriptName: String,
    val eventType: String,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val errorType: String? = null,
    val dependencies: List<String>? = null,
    val platform: String, // e.g., "INTELLIJ_PLUGIN", "VSCODE_EXTENSION", "NATIVE_CLI"
    val nexusVersion: String,
    val k2scriptVersion: String,
    val k2scriptFeaturesUsed: List<String>? = null
    // val hostApplicationInfo: Map<String, String>? = null // Future consideration
) 
