# Nexus Agent Actions

This document describes standard actions that can be dispatched to a Nexus Agent (like `DefaultNexusAgent`) and how the agent is expected to handle them. Actions are typically represented by a `Join<String, Series<String>>` where the string is the action name and the series contains arguments.

## Implementation Status (2024-12-19)

### ✅ **Fully Implemented Actions**
- **K2SCRIPT_EXECUTE**: Complete with error handling and output capture
- **ENUMERATE_INTELLIJ_PROJECT**: Complete with JSON output parsing

### 🔄 **In Progress**
- **TELEMETRY_COLLECT**: Cross-platform strategy defined, implementation pending
- **COUCHDB_OPERATIONS**: Test framework in place, API implementation pending

### 📋 **Planned Actions**
- **FILE_ANALYSIS**: Code analysis and pattern detection
- **NETWORK_SYNC**: Distributed synchronization operations
- **TENSOR_OPERATIONS**: Advanced tensor-based computations

## Action: `K2SCRIPT_EXECUTE`

*   **Purpose:** Executes a `k2script` (.kts Kotlin script) file using the configured k2script runner.
*   **Action Name Constant:** `ActionNames.K2SCRIPT_EXECUTE` (defined in `nexus.core.NexusTypes.kt`)
*   **Action Data (`action.b`: `Series<String>`):**
    *   **Element 0:** The path to the k2script file to be executed (e.g., `"scripts/my_task.kts"`).
    *   **Element 1 onwards (Optional):** Arguments to be passed to the k2script.
*   **Nexus Configuration (for `DefaultNexusAgent`):**
    *   **k2script Runner Path:** The path to the `k2script` executable.
        *   **Current Implementation:** Hardcoded in `DefaultNexusAgent.kt` as `"k2script"` (assuming it's in the system PATH).
        *   **TODO:** This path needs to be made configurable (e.g., via an environment variable like `K2SCRIPT_EXEC_PATH` or an agent configuration setting). Refer to `nexus/src/commonMain/kotlin/nexus/core/K2ScriptConfigurationNotes.md`.
    *   **Working Directory:** The directory from which the script will be executed.
        *   **Current Implementation:** Hardcoded to `File(".")` (the Nexus agent's current working directory).
        *   **TODO:** This should also be configurable or determined contextually.
*   **Outcome (`Outcome`: `Series<String>`):**
    *   Contains lines detailing the execution:
        *   Script path and arguments.
        *   Exit code from the k2script process.
        *   Combined standard output and standard error from the script.
        *   A status message (e.g., "Result: Success", "Result: Failure", "Result: Failure (Timeout)").
        *   A timestamp.
    *   Refer to `nexus/docs/k2script_execution_guide.md` for more details.

### Usage Example
```kotlin
// Execute a k2script file
val action = ActionNames.K2SCRIPT_EXECUTE j listOf("scripts/build.kts", "--verbose").toSeries()
val outcome = agent.executeAction(action)

// Expected outcome format:
// [
//   "Executing: scripts/build.kts --verbose",
//   "Exit code: 0",
//   "Output: [build output here]",
//   "Result: Success",
//   "2024-12-19T10:30:00Z"
// ]
```

## Action: `ENUMERATE_INTELLIJ_PROJECT`

*   **Purpose:** Gathers detailed structural information about an IntelliJ IDEA project by invoking the standalone `intellij-project-enumerator` tool.
*   **Action Name Constant:** `ActionNames.ENUMERATE_INTELLIJ_PROJECT` (defined in `nexus.core.NexusTypes.kt`)
*   **Action Data (`action.b`: `Series<String>`):**
    *   **Element 0:** The absolute path to the root directory of the IntelliJ project to be enumerated (e.g., `"/path/to/my/intellij_project"`).
*   **Nexus Configuration (for `DefaultNexusAgent`):**
    *   **`intellij-project-enumerator` Tool Path:** The command or path required to execute the enumerator tool.
        *   **Current Implementation:** Hardcoded in `DefaultNexusAgent.kt` as `"java -jar tools/intellij-project-enumerator/build/libs/intellij-project-enumerator-all.jar"`. This assumes the JAR is built and present at that relative location from where Nexus is run.
        *   **TODO:** This path must be made configurable (e.g., via an environment variable `INTELLIJ_ENUMERATOR_PATH` or an agent configuration setting).
*   **Outcome (`Outcome`: `Series<String>`):**
    *   **On Success (enumerator tool exit code 0):**
        *   The `Outcome` series will contain:
            *   A success message indicating the project path.
            *   The exit code (0).
            *   The detailed project information as a **JSON string**, which is the direct standard output of the enumerator tool.
            *   A status message "Result: Success".
            *   A timestamp.
        *   **TODO for Nexus Agent:** Currently, the JSON string is returned raw. In the future, `DefaultNexusAgent` should deserialize this JSON into the `IntelliJProjectDetails` Kotlin data structure (once data classes are in a shared module accessible to `nexus.core`) for internal use or further processing by Nexus, though the raw JSON might still be part of the `Outcome` for transparency.
    *   **On Failure (enumerator tool non-zero exit code or Nexus-side error):**
        *   The `Outcome` series will contain:
            *   An error message detailing the issue (e.g., timeout, tool execution exception, non-zero exit from tool).
            *   The project path attempted.
            *   The exit code from the tool (if available).
            *   The standard error output from the tool (if available).
            *   A status message "Result: Failure (Reason)".
            *   A timestamp.
*   For details on the `intellij-project-enumerator` CLI itself (usage, output JSON structure, exit codes), refer to `tools/intellij-project-enumerator/README.md`.
*   For the design of the enumeration agent, refer to `nexus/docs/intellij_enumeration_agent_design.md`.

### Usage Example
```kotlin
// Enumerate an IntelliJ project
val action = ActionNames.ENUMERATE_INTELLIJ_PROJECT j listOf("/path/to/project").toSeries()
val outcome = agent.executeAction(action)

// Expected outcome format:
// [
//   "Enumerating project: /path/to/project",
//   "Exit code: 0",
//   "Project details: {\"name\":\"my-project\",\"modules\":[...]}",
//   "Result: Success",
//   "2024-12-19T10:30:00Z"
// ]
```

## Action: `TELEMETRY_COLLECT`

*   **Purpose:** Collects telemetry data from k2script executions across different platforms.
*   **Status:** 🔄 **In Progress** - Strategy defined, implementation pending
*   **Action Name Constant:** `ActionNames.TELEMETRY_COLLECT` (planned)
*   **Action Data (`action.b`: `Series<String>`):**
    *   **Element 0:** Platform identifier (e.g., `"INTELLIJ_PLUGIN"`, `"VSCODE_EXTENSION"`)
    *   **Element 1:** Event type (e.g., `"SCRIPT_EXECUTION"`, `"ERROR"`, `"PERFORMANCE"`)
    *   **Element 2:** JSON payload with telemetry data
*   **Implementation:** Refer to `nexus/docs/telemetry_cross_platform.md` for detailed strategy

## Action: `COUCHDB_OPERATIONS`

*   **Purpose:** Performs CouchDB-compatible operations through the IPFS bridge.
*   **Status:** 🔄 **In Progress** - Test framework in place, API implementation pending
*   **Action Name Constant:** `ActionNames.COUCHDB_OPERATIONS` (planned)
*   **Action Data (`action.b`: `Series<String>`):**
    *   **Element 0:** Operation type (e.g., `"CREATE_DB"`, `"PUT_DOC"`, `"GET_DOC"`)
    *   **Element 1:** Database name
    *   **Element 2:** Document ID (for document operations)
    *   **Element 3:** JSON document content (for write operations)
*   **Implementation:** Refer to `nexus/docs/COUCHDB_TEST_PLAN.md` for current test status
