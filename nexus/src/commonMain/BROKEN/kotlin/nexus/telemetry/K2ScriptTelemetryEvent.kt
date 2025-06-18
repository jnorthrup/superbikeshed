package nexus.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class K2ScriptTelemetryEvent(
    val timestamp: Long,
    val scriptName: String, // Consider hashing for privacy if needed
    val eventType: String, // e.g., "EXEC_START", "EXEC_SUCCESS", "EXEC_ERROR", "VALIDATION_ERROR"
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val errorType: String? = null, // e.g., "FileNotFound", "ValidationFailure", "ExecutionFailure"
    val dependencies: List<String>? = null,
    val platform: String, // e.g., "INTELLIJ_PLUGIN", "CLI"
    val nexusVersion: String,
    val k2scriptVersion: String,
    val k2scriptFeaturesUsed: List<String>? = null, // e.g., ["DependsOn", "KotlinScript"]
    // Optional: Consider adding a field for more detailed host application information in the future,
    //           if simple 'platform' string is insufficient.
    // val hostApplicationInfo: Map<String, String>? = null
    // e.g., {"ideVersion": "2023.3", "pluginVersion": "1.2.0"} or {"os": "Linux", "cliVersion": "1.1"}
)
