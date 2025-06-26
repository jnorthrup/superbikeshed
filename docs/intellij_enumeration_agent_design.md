# IntelliJ Project Enumeration Agent Design

This document outlines the design for the IntelliJ Project Enumeration Agent, a tool responsible for extracting structural information from IntelliJ IDEA project files.

## 1. Agent Type and Rationale

* **Agent Type:** Standalone Kotlin Command-Line Interface (CLI) application.
* **Rationale:**
  * **Separation of Concerns:** A standalone application clearly separates the concern of IntelliJ project parsing from the core Nexus agent logic. Nexus doesn't need to be aware of IntelliJ's internal project file formats.
  * **Dependency Management:** The CLI can manage its own dependencies (e.g., XML parsers) without affecting Nexus's dependencies. This is crucial as Nexus aims to be universal and lightweight.
  * **Testability:** A separate CLI is easier to test in isolation. We can provide various sample project structures and verify the output independently of Nexus.
  * **Portability/Flexibility:** While initially for Nexus, a standalone CLI could potentially be used by other tools or scripts if needed.
  * **Environment Independence:** It runs in its own process, minimizing interference with the Nexus agent's environment or the IntelliJ environment itself (it operates on files, not a running IDE).
  * **Ease of Invocation:** Nexus can easily invoke it as a child process.

## 2. Invocation Mechanism from Nexus

The Nexus system will trigger the IntelliJ Project Enumeration Agent via a specific action handled by an agent like `DefaultNexusAgent`.

* **New ActionType for Nexus:**
  * **Name:** `ActionNames.ENUMERATE_INTELLIJ_PROJECT`
  * **Definition:** This constant should be added to `nexus.core.ActionNames` in `NexusTypes.kt`.

        ```kotlin
        // In nexus.core.ActionNames
        const val ENUMERATE_INTELLIJ_PROJECT = "ENUMERATE_INTELLIJ_PROJECT"
        ```

* **Handling by `DefaultNexusAgent`:**
    1. **Action Reception:** `DefaultNexusAgent.executeAction(action: Action)` will have a case for `ActionNames.ENUMERATE_INTELLIJ_PROJECT`.
    2. **Project Path Extraction:**
        * The `action.b` (a `Series<String>`) associated with this action is expected to contain the absolute path to the root of the IntelliJ project to be enumerated.
        * Example: `action.b.firstOrNull()` would provide the project path.
        * Error handling: If the path is missing or invalid, the agent should return an error `Outcome`.
    3. **Agent Launch:**
        * `DefaultNexusAgent` will construct a command to execute the standalone IntelliJ Project Enumeration Agent CLI.
        * Command: `java -jar /path/to/intellij-project-enumerator.jar --project-path "/actual/project/path"` or simply `/path/to/intellij-project-enumerator-native --project-path "/actual/project/path"` if compiled to native.
        * The path to the enumerator tool (`.jar` or native executable) must be configurable within Nexus (e.g., environment variable, agent configuration).
        * Nexus will use `ProcessBuilder` (or a similar utility) to launch the CLI agent as a separate process.
    4. **Output Consumption:**
        * **Success:** `DefaultNexusAgent` will read the standard output (stdout) of the CLI agent. This output is expected to be a JSON string representing the `IntelliJProjectDetails` data structure. Nexus will then parse this JSON (e.g., using `kotlinx.serialization`) and can wrap it in an `Outcome` object, perhaps as a single string element or structured if `Outcome` supports complex data.
        * **Failure:**
            * If the CLI agent exits with a non-zero status code, `DefaultNexusAgent` will capture this.
            * Standard error (stderr) from the CLI agent should be captured and included in the error `Outcome` returned by Nexus.
            * The `Outcome` will indicate failure and include details from stderr and the exit code.

## 3. Agent Input (Command-Line Arguments)

The IntelliJ Project Enumeration Agent CLI will accept the following command-line arguments:

* **`--project-path <path>` (Required):**
  * Specifies the absolute path to the root directory of the IntelliJ project that needs to be enumerated.
  * Example: `--project-path "/Users/developer/IdeaProjects/MyAwesomeProject"`
* **`--help` (Optional):**
  * Displays usage information for the CLI.

## 4. Agent Output

The agent communicates its results via standard output, standard error, and exit codes.

* **Standard Output (stdout) on Success:**
  * If the enumeration is successful, the agent will print a single JSON string to stdout.
  * This JSON string will be the serialized representation of the `nexus.intellij.project_model.IntelliJProjectDetails` data class, containing all the extracted project information.
  * **Example (conceptual):**

        ```json
        {
          "projectName": "MyAwesomeProject",
          "projectRootPath": "/Users/developer/IdeaProjects/MyAwesomeProject",
          "projectJdkVersion": "11.0.10",
          "modules": [ /* ... ModuleDetails objects ... */ ],
          "buildSystemInfo": { "type": "GRADLE", "buildFilePath": "/Users/developer/IdeaProjects/MyAwesomeProject/build.gradle.kts" }
        }
        ```

* **Standard Error (stderr) on Failure:**
  * If any error occurs during the enumeration process, the agent will print a descriptive error message to stderr.
  * This message should be human-readable and clearly indicate the cause of the error.
* **Exit Codes:**
  * **`0`**: Successful enumeration. The JSON output will be on stdout.
  * **`1`**: Generic error (e.g., invalid arguments, unexpected issue).
  * **`2`**: Invalid project path (e.g., path does not exist, not a directory, no `.idea` folder).
  * **`3`**: Error parsing project files (e.g., corrupted XML, missing critical files like `modules.xml`).
  * Other non-zero codes can be used for more specific error conditions if needed.

## 5. Error Handling Strategy (Agent-Side)

The IntelliJ Project Enumeration Agent CLI must gracefully handle various error conditions:

* **Invalid Project Path:**
  * Path does not exist, is not a directory, or does not contain an `.idea` subfolder.
  * **Behavior:** Print error to stderr, exit with code `2`.
* **Missing Critical Configuration Files:**
  * e.g., `.idea/modules.xml` is not found.
  * **Behavior:** Print error to stderr (e.g., "Critical file modules.xml not found."), exit with code `3`.
* **XML Parsing Errors:**
  * Malformed XML in `.iml`, `modules.xml`, or other parsed files.
  * **Behavior:** Print error to stderr (e.g., "Error parsing file X: [parser message]"), exit with code `3`.
* **Incomplete Data:**
  * If some non-critical information cannot be found (e.g., a specific attribute for a dependency), the agent should attempt to continue and report the data it could find. Missing optional fields in the output JSON will be `null` or empty lists as per the `IntelliJProjectDetails` data class design.
  * If essential information is missing that makes further parsing illogical, it might be treated as a parsing error.
* **I/O Errors:**
  * Permission issues reading files.
  * **Behavior:** Print error to stderr, exit with a generic error code like `1` or a specific I/O error code.
* **Invalid Command-Line Arguments:**
  * `--project-path` not provided.
  * **Behavior:** Print usage information and error to stderr, exit with code `1`.

Error messages on stderr should be clear and informative to help diagnose the issue.

## 6. Proposed Project Structure for the Agent

A new Gradle subproject is proposed to house the IntelliJ Project Enumeration Agent.

* **Subproject Name:** `intellij-project-enumerator`
* **Location:** `tools/intellij-project-enumerator` (relative to the `nexus` root project)

    ```
    nexus/
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── src/
    │   └── ... (core Nexus modules)
    └── tools/
        └── intellij-project-enumerator/
            ├── build.gradle.kts
            └── src/
                ├── main/
                │   ├── kotlin/
                │   │   └── nexus/enumerator/intellij/Main.kt
                │   └── resources/
                └── test/
                    ├── kotlin/
                    └── resources/
    ```

* **Purpose:**
  * Contains all the logic for parsing IntelliJ project files.
  * Builds into a standalone CLI application (JAR or native executable).
* **Potential Key Dependencies (in `tools/intellij-project-enumerator/build.gradle.kts`):**
  * `org.jetbrains.kotlin:kotlin-stdlib`
  * `org.jetbrains.kotlinx:kotlinx-cli` (for parsing command-line arguments)
  * `org.jetbrains.kotlinx:kotlinx-serialization-json` (for serializing the output to JSON)
  * Standard XML parsing capabilities (StAX is part of JDK, so no explicit dependency unless a wrapper library is chosen).
  * The `nexus.intellij.project_model` data classes (defined in `nexus/src/commonMain/kotlin/nexus/intellij/project_model/`) will need to be accessible. This could be achieved by:
    * Publishing `nexus.core` (or a shared models module) as a local Maven artifact that `intellij-project-enumerator` can depend on.
    * Setting up a Gradle multi-project build where `intellij-project-enumerator` has a project dependency on the module containing `IntelliJProjectDetails.kt`. This is the preferred approach if the enumerator is tightly coupled with Nexus.

## 7. Summary

This design specifies a standalone Kotlin CLI, the "IntelliJ Project Enumeration Agent," invoked by Nexus to extract project structure details. It communicates via command-line arguments for input and JSON on stdout for success, using stderr and exit codes for errors. A new Gradle subproject under `tools/` will house this agent. This approach ensures modularity, testability, and clear separation from the core Nexus agent.All design decisions have been consolidated into the `nexus/docs/intellij_enumeration_agent_design.md` document. This includes:

1. Agent Type: Standalone Kotlin CLI.
2. Invocation: New `ActionNames.ENUMERATE_INTELLIJ_PROJECT` handled by `DefaultNexusAgent` to launch the CLI.
3. Agent Input: `--project-path` CLI argument.
4. Agent Output: JSON representation of `IntelliJProjectDetails` on stdout for success; error messages on stderr and non-zero exit codes for failures.
5. Error Handling: Strategy for common errors like invalid paths, missing files, and parsing issues.
6. Project Structure: Proposed a new Gradle subproject `tools/intellij-project-enumerator`.
7. The design document itself is the deliverable.

I am ready to submit the report.
