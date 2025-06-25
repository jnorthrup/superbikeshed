# IntelliJ Project Enumerator CLI

## Overview

The IntelliJ Project Enumerator is a command-line interface (CLI) tool designed to parse IntelliJ IDEA project files. It extracts detailed structural information about a project, its modules, source organization, SDK configurations, and dependencies (from both IntelliJ's own metadata and common build files like Maven's `pom.xml` and Gradle's `build.gradle`/`.kts`).

This tool is intended to be used by developer tools and scripts that need programmatic access to IntelliJ project configurations, such as the Nexus Universal Development Agent.

## Features

The enumerator extracts the following information:

*   **Project Details:**
    *   Project Name
    *   Project Root Path
    *   Project SDK Name and JDK Version
    *   Project Group ID and Version (from build files)
*   **Module Details (for each module):**
    *   Module Name
    *   Path to the `.iml` file
    *   Module Group ID and Version (from module-specific build files)
    *   Source, Resource, Test Source, and Test Resource directories
    *   Module-specific SDK/JDK or indication if it inherits the project SDK
*   **Build System Information:**
    *   Type of build system (Maven, Gradle, IntelliJ Native)
    *   Path to the primary build file (e.g., `pom.xml`, `build.gradle`) for the project and for individual modules if they have their own.
*   **Dependencies (for each module):**
    *   Name (e.g., library coordinates like `group:artifact`, or module name)
    *   Version
    *   Scope (e.g., COMPILE, TEST, RUNTIME, PROVIDED)
    *   Type (Module or Library)
    *   Information is sourced from both `.iml` files and build files (`pom.xml`, `build.gradle`/`.kts`), with build file data typically taking precedence.

## Building

The tool is built using Gradle. Common ways to build it:

1.  **Create a distributable JAR (includes dependencies):**
    ```bash
    ./gradlew :tools:intellij-project-enumerator:shadowJar
    ```
    (Requires the `shadowJar` plugin to be configured in `tools/intellij-project-enumerator/build.gradle.kts`. If not using shadow, `jar` task creates a thin jar).
    A typical "all-in-one" JAR might be found in `tools/intellij-project-enumerator/build/libs/intellij-project-enumerator-all.jar` or similar, depending on JAR plugin configuration.

2.  **Create distribution archives (zip/tar):**
    ```bash
    ./gradlew :tools:intellij-project-enumerator:distZip
    ./gradlew :tools:intellij-project-enumerator:distTar
    ```
    This creates archives in `tools/intellij-project-enumerator/build/distributions/` containing scripts to run the application and all necessary JARs.

3.  **Run directly via Gradle (for development):**
    ```bash
    ./gradlew :tools:intellij-project-enumerator:run --args="--project-path /path/to/your/project"
    ```

*(Note: The `build.gradle.kts` needs to be appropriately configured with the `application` plugin and potentially the `shadowJar` plugin for easy distribution. The current setup uses the `application` plugin, which generates run scripts and a basic JAR).*

## Usage

### Command-Line Syntax

```bash
java -jar /path/to/intellij-project-enumerator-all.jar --project-path <path_to_project_root>
```
Or, if using scripts from `distZip`/`distTar`:
```bash
/path/to/extracted_dist/bin/intellij-project-enumerator --project-path <path_to_project_root>
```

### Arguments

*   `--project-path <path_to_project_root>`: **(Required)** Specifies the absolute path to the root directory of the IntelliJ project you want to enumerate.
*   `--help`: Displays usage information and a list of available arguments.

## Output Format

On successful execution, the tool prints a JSON object to standard output (`stdout`). This JSON represents the `IntelliJProjectDetails` data structure.

### Example JSON Output:

```json
{
  "projectName": "MyAwesomeProject",
  "projectRootPath": "/Users/developer/IdeaProjects/MyAwesomeProject",
  "projectGroupId": "com.example",
  "projectVersion": "1.0.0-SNAPSHOT",
  "projectSdkName": "corretto-11",
  "projectJdkVersion": "corretto-11", // May be same as name or more specific
  "modules": [
    {
      "moduleName": "MyAwesomeProject-main",
      "imlPath": "/Users/developer/IdeaProjects/MyAwesomeProject/MyAwesomeProject-main.iml",
      "moduleGroupId": "com.example.module", // Can be null if not in module's build file
      "moduleVersion": "1.0.0",        // Can be null
      "sourceDirs": [
        "/Users/developer/IdeaProjects/MyAwesomeProject/src/main/kotlin"
      ],
      "resourceDirs": [
        "/Users/developer/IdeaProjects/MyAwesomeProject/src/main/resources"
      ],
      "testSourceDirs": [],
      "testResourceDirs": [],
      "moduleSdkName": null, // Null if inherited project SDK
      "moduleJdkVersion": null,
      "dependencies": [
        {
          "name": "org.jetbrains.kotlin:kotlin-stdlib-jdk8",
          "version": "1.8.20",
          "scope": "COMPILE",
          "type": "LIBRARY",
          "libraryPath": null
        },
        {
          "name": "another-module-in-project",
          "version": null,
          "scope": "TEST",
          "type": "MODULE",
          "libraryPath": null
        }
      ],
      "buildSystemInfo": {
        "type": "GRADLE",
        "buildFilePath": "/Users/developer/IdeaProjects/MyAwesomeProject/build.gradle.kts"
      }
    }
    // ... more modules
  ],
  "buildSystemInfo": {
    "type": "GRADLE",
    "buildFilePath": "/Users/developer/IdeaProjects/MyAwesomeProject/build.gradle.kts"
  }
}
```
*(Note: The `encodeDefaults = true` setting for JSON serialization ensures that fields with default values (like empty lists or nulls if they were defaults) are present in the output.)*

## Error Handling

*   **Standard Error (`stderr`):** If an error occurs, a descriptive message is printed to `stderr`.
*   **Exit Codes:**
    *   `0`: Successful enumeration. JSON output is on `stdout`.
    *   `1`: Generic error, often related to invalid command-line arguments.
    *   `2`: Invalid project path (e.g., path does not exist, not a directory, or crucial `.idea` subfolder is missing).
    *   `3`: Error parsing project files (e.g., corrupted XML, critical configuration files like `modules.xml` are unreadable or fundamentally flawed).

## Limitations

*   **Gradle Parsing:** The current Gradle parser uses regular expressions to extract information from `build.gradle` and `build.gradle.kts` files. This approach is inherently fragile and may not work correctly for complex build scripts that involve:
    *   Variables and property substitutions for versions or group IDs.
    *   Dependencies defined in external files (`apply from: ...`).
    *   Custom logic, conditional blocks, or plugins that apply dependencies programmatically.
    *   Complex dependency notations beyond simple string or map formats.
    A more robust solution would involve using the Gradle Tooling API, which is significantly more complex to integrate into a standalone tool.
*   **Maven Parsing:** The Maven `pom.xml` parser is also basic. It does not handle:
    *   Parent POM inheritance for all properties (though basic GAV for the project itself might be found).
    *   `dependencyManagement` sections.
    *   Build profiles that might alter dependencies.
    *   Import scope for BOMs (Bill of Materials) in great detail.
*   **IntelliJ Configuration Variations:** IntelliJ project structures can vary. While this tool aims to cover common setups, highly customized or older project formats might not be fully parsed.
*   **SDK Version Resolution:** The "version" of an SDK (especially JDKs) might be derived from its name. A more precise resolution might require inspecting IntelliJ's JDK table configurations, which is currently out of scope.
*   **File Encodings:** Assumes default system encoding for XML and build files.

This tool provides a best-effort enumeration based on common IntelliJ project patterns. For critical production use cases requiring absolute accuracy with complex build setups, consider more deeply integrated solutions or the Gradle/Maven Tooling APIs directly.
