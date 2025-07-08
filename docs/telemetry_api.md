# Nexus Telemetry Endpoint API

## Endpoint: `POST /api/v1/telemetry/event`

**Note:** This specific path `/api/v1/telemetry/event` is the currently assumed path by clients like the `IntelliJAdapter`. The actual exposed path would depend on how the `TelemetryEndpoint` logic is integrated into the Nexus HTTP server routing configuration.

## Description

This endpoint is responsible for receiving telemetry events related to `k2script` usage and potentially other operations within the Nexus ecosystem. It serves as a central collection point for telemetry data from various clients (e.g., IntelliJ Plugin, VSCode Extension, CLI tools).

## Request Body

*   **`Content-Type: application/json`**

The request body must be a JSON object representing the `K2ScriptTelemetryEvent` data structure.

### `K2ScriptTelemetryEvent` Structure:

```kotlin
// Defined in nexus/src/commonMain/kotlin/nexus/telemetry/K2ScriptTelemetryEvent.kt
@Serializable
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
    val k2scriptFeaturesUsed: List<String>? = null,
    // val hostApplicationInfo: Map<String, String>? = null // Future consideration
)
```

### Example JSON Request Body:

```json
{
  "timestamp": 1678886400000,
  "scriptName": "my_script.kts",
  "eventType": "EXEC_SUCCESS",
  "durationMs": 1250,
  "platform": "INTELLIJ_PLUGIN",
  "nexusVersion": "0.2.0",
  "k2scriptVersion": "1.1.0",
  "dependencies": ["com.example:mylib:1.0"],
  "k2scriptFeaturesUsed": ["DependsOn"]
}
```

## Authentication

*   **Current Status:** No authentication is implemented for this endpoint in its current conceptual definition.
*   **Future Plans:** Secure communication is planned. This will likely involve API Key-based authentication, where the client includes an API key in an HTTP header (e.g., `X-Nexus-Telemetry-Key`).
*   For detailed discussion on secrets management for this endpoint, please refer to `nexus/src/commonMain/kotlin/nexus/telemetry/SecretsManagementNotes.md`.

## Responses

*   **`202 Accepted`**:
    *   The telemetry event was received successfully by the endpoint.
    *   **Note:** This only indicates receipt, not final processing or forwarding to a backend.
*   **`400 Bad Request`**:
    *   The request body is malformed, not valid JSON, or does not conform to the `K2ScriptTelemetryEvent` structure (e.g., missing required fields).
    *   The response body may contain details about the validation error.
*   **`401 Unauthorized`** (Future):
    *   Authentication failed (e.g., missing or invalid API key).
*   **`500 Internal ServerError`**:
    *   An unexpected error occurred on the server while trying to process or log the event.
    *   The response body may contain error details.

## Implementation Notes

*   The logic for this endpoint is conceptually defined in `nexus.telemetry.endpoint.TelemetryEndpoint.kt`.
*   This definition outlines the intended behavior (receiving, basic validation, logging/placeholder for forwarding).
*   To become operational, this conceptual endpoint needs to be integrated with an actual HTTP server framework used by Nexus (e.g., Ktor, Spring Boot, etc.). This involves:
    *   Setting up routing to map the `POST /api/v1/telemetry/event` path to the handler logic.
    *   Implementing proper JSON deserialization from the request body to the `K2ScriptTelemetryEvent` object.
    *   Handling HTTP response codes and bodies correctly.
    *   Integrating the actual forwarding logic to a telemetry backend like PostHog.
*   The current placeholder implementation in `TelemetryEndpoint.kt` logs received events to the console.**Step 2: Create/Update IntelliJ Integration Documentation**

I will create `nexus/docs/intellij_integration_guide.md` as it likely doesn't exist.
