# Cross-Platform Telemetry for k2script

## 1. Introduction

The goal of this document is to outline a universal telemetry strategy for `k2script` usage, ensuring consistent data collection and reporting from various execution environments. These environments include:

*   **JVM-based environments:** Such as the IntelliJ IDEA plugin.
*   **Native environments:** Where `k2script` might run as a standalone native executable or be embedded via Foreign Function Interface (FFI) into other native applications.
*   **JavaScript/WASM environments:** Such as a VSCode extension or web-based tools utilizing `k2script` via JavaScript wrappers or WebAssembly.

A unified approach to telemetry allows for comprehensive analytics of `k2script` adoption, feature usage, performance, and error patterns across all platforms.

## 2. Central Nexus Telemetry Endpoint

The core of this strategy is a **central telemetry endpoint within the Nexus system**. This endpoint, conceptually defined in `nexus.telemetry.endpoint.TelemetryEndpoint.kt`, serves as the single collection point for all `k2script` telemetry events.

### Data Structure: `K2ScriptTelemetryEvent`

All telemetry data is structured according to the `K2ScriptTelemetryEvent` Kotlin data class (defined in `nexus.telemetry.K2ScriptTelemetryEvent.kt`). This data structure is designed to be generic enough for cross-platform use. Key aspects:

*   **`platform: String` Field:** This field is crucial for distinguishing the source of the telemetry event. Example values include:
    *   `"INTELLIJ_PLUGIN"`
    *   `"VSCODE_EXTENSION"`
    *   `"NATIVE_CLI"` (for standalone k2script runner)
    *   `"NATIVE_FFI_HOST"` (for applications embedding k2script via FFI)
    *   `"K2SCRIPT_HOSTED_SERVICE"` (if k2script is used as part of a backend service)
    *   `"WEB_IDE"`
    *   `"JS_WRAPPER"`
*   **Common Fields:** Fields like `timestamp`, `scriptName`, `eventType`, `durationMs`, `errorMessage`, `errorType`, `dependencies`, `k2scriptVersion`, and `nexusVersion` are applicable across platforms.

## 3. Client Integration Strategies

### 3.1. JVM (IntelliJ IDEA Plugin)

*   **Current Approach:** The `IntelliJAdapter` in Nexus (`nexus.adaptation.IDEAdapters.kt`) is responsible for collecting telemetry data during k2script executions initiated from the IntelliJ plugin.
*   **Data Transmission:** It populates a `K2ScriptTelemetryEvent` object and sends it to the Nexus `TelemetryEndpoint` via an HTTP POST request (using Ktor client).
*   **Responsibility:** The IntelliJ plugin sends the event to Nexus; Nexus is then responsible for any further processing or forwarding.

### 3.2. Native / FFI Integration

*   **Context:** This applies when `k2script` is compiled to a native executable (e.g., using Kotlin/Native) or when a native application (e.g., written in C++, Rust, Swift) embeds `k2script` functionality via an FFI.
*   **Telemetry Collection:** The native host application or the native `k2script` wrapper would be responsible for:
    *   Monitoring `k2script` execution lifecycle events (start, end, errors).
    *   Gathering data to populate the fields of a `K2ScriptTelemetryEvent` (or an equivalent structure in the native language).
    *   Retrieving platform-specific information (e.g., host application name and version, OS version).
*   **Data Transmission:**
    *   A **native HTTP client library** (e.g., libcurl, C++ REST SDK, platform-specific APIs) would be used to serialize the telemetry event (likely to JSON) and send it to the Nexus `TelemetryEndpoint` via an HTTP POST request.
    *   The `platform` field would be set accordingly (e.g., `"NATIVE_CLI"`, `"NATIVE_FFI_HOST"`).
*   **FFI Considerations:**
    *   If `k2script` is a library, the FFI layer might expose functions to the host application to report telemetry events, or the `k2script` library itself could internally handle sending telemetry.
    *   The FFI design should consider how to pass necessary contextual information (like `scriptName`, `nexusVersion`, etc.) to the telemetry reporting mechanism.

### 3.3. JavaScript / WASM (VSCode Extension, Web, Proxies)

*   **Context:** This applies to environments where `k2script` is used from JavaScript, such as:
    *   VSCode extensions.
    *   Web-based IDEs or tools.
    *   Node.js-based command-line tools or proxies that interact with `k2script`.
    *   `k2script` compiled to WebAssembly (WASM) and run in a JS environment.
*   **Telemetry Collection & Transmission:**
    *   **`packages/telemetry/TelemetryService.ts`:** This existing TypeScript library is the preferred method for sending telemetry from JS environments. It should be configured to send events to the Nexus `TelemetryEndpoint` URL.
    *   The JS environment would gather data to construct an object matching the `K2ScriptTelemetryEvent` structure and use `TelemetryService.ts` to send it.
    *   The `platform` field would be set to values like `"VSCODE_EXTENSION"`, `"WEB_IDE"`, `"JS_WRAPPER"`.
    *   **`k2script_js_wrapper.js`:** This wrapper (mentioned in the `k2script` project) could be enhanced or used in conjunction with other JS code to capture `k2script` execution details and trigger telemetry events.
*   **WASM Considerations:**
    *   If `k2script` (or parts of it) is compiled to WASM for performance, the WASM module would typically be instantiated and controlled by JavaScript.
    *   Telemetry would still likely be initiated from the JavaScript side, which would interact with the WASM module and then use a JS HTTP client (or `TelemetryService.ts`) to send data to the Nexus endpoint. The WASM module might export functions to signal events to the JS host.

## 4. Nexus to PostHog Forwarding (or other backends)

A critical role of the Nexus `TelemetryEndpoint` is to act as a gateway, potentially validating and then **forwarding the received telemetry events to a final analytics backend**, such as PostHog.

*   **Responsibility:** Nexus (specifically, the `TelemetryEndpoint` implementation) is responsible for this forwarding. Clients (IntelliJ, native, JS) only need to know about the Nexus endpoint.
*   **Forwarding Options for Nexus:**
    1.  **Kotlin-native HTTP Client:** Nexus can use a Kotlin HTTP client (like Ktor Client, already used in `IntelliJAdapter`) to make direct API calls to the PostHog event ingestion API.
    2.  **Dedicated PostHog Kotlin Library:** If a mature and maintained Kotlin library for PostHog exists, it could simplify integration.
    3.  **Bridging to `packages/telemetry/` (less likely for server-side Kotlin):**
        *   While powerful, integrating a TypeScript library directly into a Kotlin backend (Nexus) can be complex.
        *   Options like GraalVM's Polyglot capabilities could enable this, allowing Nexus to run the TypeScript code from `TelemetryService.ts`. However, this adds significant complexity and dependencies (Node.js runtime via GraalVM).
        *   A lightweight embedded JS engine (e.g., Rhino, Nashorn - though Nashorn is deprecated) could run simple JS for API calls but might not fully support the `TelemetryService.ts` if it has browser/Node.js specific dependencies.
        *   This option is generally less favorable than using native Kotlin HTTP clients or libraries unless there's a compelling reason.
*   **Secrets Management:**
    *   Nexus will need to securely manage the API key for the PostHog backend (or any other backend).
    *   Refer to `nexus/src/commonMain/kotlin/nexus/telemetry/SecretsManagementNotes.md` for details on how Nexus should store and access such secrets (e.g., environment variables, configuration files, secrets management systems).

## 5. Consistency and Future Considerations

*   **Event Structure:** Maintaining a consistent `K2ScriptTelemetryEvent` structure across all platforms is paramount for unified data analysis.
*   **`platform` Field:** Accurate and consistent use of the `platform` field is essential for segmenting and understanding telemetry data from different sources.
*   **Schema Evolution:** As new telemetry needs arise, the `K2ScriptTelemetryEvent` structure may evolve. Changes should be backward compatible if possible (e.g., adding new optional fields).
*   **Batching:** High-volume clients might consider batching multiple telemetry events into a single HTTP request to reduce network overhead, if the Nexus endpoint supports it.
*   **Offline Support:** For clients that might operate offline temporarily (e.g., native CLIs), a strategy for caching telemetry events locally and sending them when connectivity is restored could be considered.

This cross-platform telemetry strategy ensures that `k2script` usage can be holistically monitored and analyzed, providing valuable insights for its development and improvement.

## 6. Architectural Diagram Considerations

To better visualize the telemetry data flow, relevant architectural diagrams within the Nexus documentation (or a dedicated diagram in this document) should be updated or created.

**Recommended Diagram Elements:**

*   **Clients:** Show different client environments (IntelliJ Plugin, VSCode Extension, Native CLI, FFI Host, JS/Web applications).
*   **Data Flow Arrows:** Illustrate these clients sending `K2ScriptTelemetryEvent` data (or equivalent) to the central Nexus Telemetry Endpoint.
*   **Nexus Telemetry Endpoint:** Depict this as a component within the Nexus system.
*   **Nexus Internal Processing:** Briefly show that Nexus might validate, process, or temporarily store these events.
*   **Forwarding to Backend:** Illustrate Nexus forwarding the processed events to an external analytics backend (e.g., PostHog).
*   **Configuration Points:** Indicate where configurations like endpoint URLs and API keys are managed for both client-to-Nexus and Nexus-to-Backend communication.

Such a diagram would provide a clear visual summary of the entire telemetry pipeline and the interaction points between different components and systems.
This visual aid would complement the textual descriptions in this document and other architecture overviews.
