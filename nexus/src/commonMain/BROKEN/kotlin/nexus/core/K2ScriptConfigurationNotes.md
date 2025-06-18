# K2Script Executable Path Configuration Notes

When the `NexusAgent` (e.g., `DefaultNexusAgent`) executes a k2script using `ActionNames.K2SCRIPT_EXECUTE`, it needs to know the location of the `k2script` command-line runner.

In the current implementation within `DefaultNexusAgent.kt`, this path is hardcoded:
```kotlin
val k2scriptCommandPath = "k2script" // Assuming k2script is in PATH
```

This assumes that `k2script` is available in the system's PATH environment variable where the Nexus agent is running. This might not always be the case or desirable.

## Configuration Options:

1.  **Environment Variable:**
    *   Nexus Agent can read an environment variable (e.g., `K2SCRIPT_HOME` or `K2SCRIPT_EXEC_PATH`) at startup.
    *   **Example:** `K2SCRIPT_EXEC_PATH=/opt/k2script/bin/k2script`
    *   **Pros:** Standard way to configure paths for applications, easy to set in different deployment environments (Docker, systemd services, etc.).
    *   **Cons:** Requires environment setup.

2.  **Agent Configuration:**
    *   The path could be part of the `AgentConfiguration` that `NexusAgent` receives or loads.
    *   This means the configuration mechanism for Nexus (e.g., a properties file, a central configuration service) would need to store this path.
    *   **Example in `AgentConfiguration` (Series of Key-Value pairs):**
        `"k2script.executable.path" j "/usr/local/bin/k2script"`
    *   **Pros:** Centralized configuration within the Nexus ecosystem.
    *   **Cons:** Requires the configuration system to be in place and populated.

3.  **Fixed Path (with documentation):**
    *   Use a fixed, documented path (e.g., `/usr/local/bin/k2script` or a path relative to Nexus installation).
    *   **Pros:** Simplest to implement initially.
    *   **Cons:** Least flexible; requires users to install `k2script` to a specific location.

4.  **Discovery Mechanism (More Complex):**
    *   Attempt to find `k2script` in common locations (e.g., check PATH, then common install directories).
    *   **Pros:** User-friendly if it works.
    *   **Cons:** More complex to implement reliably across different operating systems and environments; can be unpredictable.

## Recommendation:

A combination is often best:
1.  **Primary:** Read from an environment variable (e.g., `K2SCRIPT_EXEC_PATH`).
2.  **Fallback:** If the environment variable is not set, try a default value (e.g., `"k2script"` assuming it's in PATH).
3.  **Documentation:** Clearly document how to set the environment variable and what the default behavior is.

The `DefaultNexusAgent.kt` should be updated to reflect this configurable approach rather than a hardcoded path. A `TODO` comment for this is already present in the code.

## Working Directory:

Similarly, the working directory for the `ProcessBuilder` is currently hardcoded to `File(".")` (current working directory of the Nexus agent). This should also ideally be configurable or determined based on the context of the script execution (e.g., project root if available). A `TODO` for this is also present.
