# Nexus k2script Execution Guide

## Overview

Nexus provides the capability to execute `k2script` (.kts) files through its agent system, specifically using implementations like `DefaultNexusAgent`. This allows for programmatic triggering and management of k2script tasks as part of broader Nexus workflows or in response to IDE actions.

## Triggering k2script Execution

To execute a k2script via Nexus, an `Action` object must be dispatched to a `NexusAgent` that supports this functionality.

*   **Action Name:** The action name must be `ActionNames.K2SCRIPT_EXECUTE`.
    *   `ActionNames` is an object defined in `nexus/src/commonMain/kotlin/nexus/core/NexusTypes.kt`.
    *   `ActionNames.K2SCRIPT_EXECUTE` has the string value `"K2SCRIPT_EXECUTE"`.

*   **Action Data (`action.b`):** The data part of the action is a `Series<String>` (from TrikeShed core types, essentially an ordered list of strings). This series is structured as follows:
    *   **Element 0:** The path to the k2script file to be executed (e.g., `"path/to/my/script.kts"`).
    *   **Element 1 onwards (Optional):** Arguments to be passed to the k2script (e.g., `"arg1"`, `"--option"`, `"value"`).

### Example `Action` Object Construction (Kotlin):

```kotlin
import nexus.core.ActionNames
import borg.trikeshed.core.seriesOf // or nexus.core.get // Assuming 'get' is an alias for seriesOf or similar
import borg.trikeshed.core.j // For the 'join' infix function to create Action

// ... inside code that can access a NexusAgent instance ...

val scriptPath = "scripts/data_processing.kts"
val arg1 = "--input-file"
val arg2 = "data/source.csv"
val arg3 = "--verbose"

// Constructing the arguments Series<String>
val scriptExecutionArgs = seriesOf(scriptPath, arg1, arg2, arg3)

// Creating the Action object
val k2scriptAction = ActionNames.K2SCRIPT_EXECUTE j scriptExecutionArgs
// 'j' is an infix function creating a Join<String, Series<String>>, which is typealiased to Action.

// This action can now be sent to a NexusAgent:
// val outcome = nexusAgent.executeAction(k2scriptAction)
```

## Execution Process by `DefaultNexusAgent`

When `DefaultNexusAgent` receives an `Action` with `ActionNames.K2SCRIPT_EXECUTE`:

1.  **Argument Parsing:** It extracts the script path and any arguments from `action.b`.
2.  **Command Construction:** It forms a command list to be executed by the operating system. This typically looks like `[<k2script_runner_path>, <scriptPath>, <arg1>, <arg2>, ...]`.
3.  **Process Invocation:** It uses `java.lang.ProcessBuilder` to launch the `k2script` runner as a separate process.
4.  **Output Capturing:** Standard output (stdout) and standard error (stderr) from the k2script process are merged and captured.
5.  **Timeout Handling:** A configurable timeout (default 60 seconds) is applied to the script execution. If the script exceeds this timeout, it's terminated.
6.  **Result Aggregation:** The exit code from the script, along with its combined stdout/stderr output, is collected.

## Configuration

The execution of k2scripts by `DefaultNexusAgent` depends on certain configurations:

*   **`k2script` Executable Path:** The agent needs to know where to find the `k2script` command-line runner.
*   **Working Directory:** The directory from which the `k2script` will be executed.

These aspects are detailed in the **`nexus/src/commonMain/kotlin/nexus/core/K2ScriptConfigurationNotes.md`** document. It discusses options such as using environment variables (e.g., `K2SCRIPT_EXEC_PATH`) or agent configuration settings to specify these paths. Currently, `DefaultNexusAgent` has placeholders and TODO comments indicating where these configurations will be fully implemented.

## Outcome of Execution

The `executeAction` method of `DefaultNexusAgent` returns an `Outcome` object, which is a `Series<String>`. For a `K2SCRIPT_EXECUTE` action, the `Outcome` series typically contains the following information:

*   A message indicating the script path and arguments.
*   The exit code of the script (`Exit Code: 0` for success).
*   The combined output (stdout and stderr) from the script.
*   A status message (e.g., "Result: Success", "Result: Failure", "Result: Failure (Timeout)").
*   A timestamp.

### Example `Outcome` (materialized as a list of strings):

```
[
  "K2Script execution finished for script: scripts/data_processing.kts",
  "Args: --input-file data/source.csv --verbose",
  "Exit Code: 0",
  "Output:\nProcessing data from data/source.csv...\nVerbose mode enabled.\nData processing complete.\n",
  "Result: Success",
  "Timestamp: 1678886401234"
]
```

Or in case of an error:

```
[
  "K2Script execution finished for script: scripts/error_script.kts",
  "Args: ",
  "Exit Code: 1",
  "Output:\nError: Required input file not found.\n",
  "Result: Failure",
  "Timestamp: 1678886402345"
]
```

Clients receiving this `Outcome` can parse it to determine the success status and retrieve the script's output.
