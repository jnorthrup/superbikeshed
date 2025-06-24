# Nexus Agent Actions

This document describes standard actions that can be dispatched to a Nexus Agent (like `DefaultNexusAgent`) and how the agent is expected to handle them. Actions are typically represented by a `Join<String, Series<String>>` where the string is the action name and the series contains arguments.

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
