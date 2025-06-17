# Nexus IntelliJ Plugin Integration Guide

This document provides guidance on how the Nexus IntelliJ Plugin integrates with the broader Nexus system, particularly focusing on k2script execution and telemetry.

## Overview

The Nexus IntelliJ Plugin aims to provide a seamless experience for developers using k2script within the IntelliJ IDEA environment. It leverages the Nexus Agent for functionalities like script execution and contributes to the Nexus telemetry system.

## k2script Execution

When the Nexus IntelliJ Plugin is active and configured to communicate with a Nexus instance:

1.  **Action Trigger:** Users can trigger k2script execution through IntelliJ actions (e.g., context menu on a `.kts` file, a dedicated run button).
2.  **Nexus Agent Invocation:** The plugin (specifically the `IntelliJAdapter` and `IntelliJConnection` components) translates this IDE action into an `Action` object with `ActionNames.K2SCRIPT_EXECUTE`.
3.  **Execution by Nexus:** This action is sent to the connected `NexusAgent` (e.g., `DefaultNexusAgent`), which then handles the actual execution of the k2script using the configured `k2script` command-line runner.
4.  **Results:** The outcome of the script execution (stdout, stderr, exit code) is returned to the plugin and can be displayed in an IntelliJ console or tool window.

For more details on how Nexus executes k2scripts and how to configure the k2script runner, refer to the [Nexus k2script Execution Guide](./k2script_execution_guide.md).

## Telemetry

The Nexus IntelliJ Plugin participates in sending telemetry data for `k2script` usage to a central Nexus telemetry endpoint. This helps in understanding script usage patterns, performance, and potential issues.

### Telemetry Events

The following types of telemetry events are captured for k2script executions:

*   **`EXEC_START`**: Sent when a k2script execution is initiated.
*   **`EXEC_SUCCESS`**: Sent when a k2script completes successfully. Includes execution duration.
*   **`EXEC_ERROR`**: Sent when a k2script fails to execute or completes with an error. Includes execution duration, error message, and error type.

The data for these events is structured according to the `K2ScriptTelemetryEvent` format. For API details of the telemetry endpoint, see [Nexus Telemetry Endpoint API](./api/telemetry_api.md).

### Configuration

*   **Telemetry Endpoint URL:**
    *   **Current Default:** The plugin currently attempts to send telemetry data to `http://localhost:8080/api/v1/telemetry/event`. This is hardcoded in `IntelliJAdapter.kt`.
    *   **TODO (Configuration):** This URL needs to be made configurable. Potential options include:
        *   An IntelliJ settings panel specific to the Nexus plugin.
        *   IDE-level environment variables or properties.
        *   Project-specific settings if applicable.
*   **Security:**
    *   **Current Status:** Telemetry is sent over HTTP without specific authentication tokens.
    *   **Future Requirement:** Secure communication will be implemented, likely requiring an API key to be configured in the plugin and sent as an HTTP header to the Nexus telemetry endpoint. Refer to `nexus/src/commonMain/kotlin/nexus/telemetry/SecretsManagementNotes.md` for more details on planned security measures.

### Data Flow

1.  `IntelliJAdapter` (within the plugin context) detects a k2script execution.
2.  It constructs a `K2ScriptTelemetryEvent` object.
3.  It sends this event as an HTTP POST request to the configured Nexus telemetry endpoint URL.
4.  The Nexus telemetry endpoint receives the event and is responsible for any further processing or forwarding to a backend analytics system (e.g., PostHog).

Regularly reviewing and ensuring the plugin can connect to the configured Nexus telemetry endpoint is crucial for maintaining data flow.
