# IntelliJ Project File Format and Parsing Strategy Research

## 1. Introduction

This document outlines the research into IntelliJ IDEA's project file formats (`.idea` directory) and the selection of an appropriate XML parsing strategy in Kotlin. This information is crucial for implementing the IntelliJ Enumeration Agent for Nexus, which needs to extract project details.

The primary files of interest for the defined scope are:
*   `.idea/modules.xml`: Lists modules in the project.
*   `*.iml`: Module-specific configuration files.
*   `.idea/misc.xml` (or `jdk.table.xml`, `projectRootManager.xml`): Often contains project-level SDK/JDK information.
*   `.idea/.name`: Sometimes contains the project name.

## 2. IntelliJ Project File Structures (Typical)

The following descriptions are based on common structures found in IntelliJ projects. Specific elements and attributes can vary slightly based on IntelliJ version, project type, and plugins.

### 2.1. `.idea/modules.xml`

This file lists all modules that are part of the project.

*   **Root Element:** `<project version="4">`
*   **Modules Component:** `<component name="ProjectModuleManager">`
    *   **Modules Element:** `<modules>`
        *   **Module Element:** `<module>`
            *   `fileurl`: Attribute specifying the path to the `.iml` file (e.g., `file://$PROJECT_DIR$/my_module/my_module.iml`).
            *   `filepath`: Attribute providing the absolute path to the `.iml` file using `$PROJECT_DIR$` macro (e.g., `$PROJECT_DIR$/my_module/my_module.iml`).

**Example Snippet:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="file://$PROJECT_DIR$/my_project.iml" filepath="$PROJECT_DIR$/my_project.iml" />
      <module fileurl="file://$PROJECT_DIR$/module1/module1.iml" filepath="$PROJECT_DIR$/module1/module1.iml" />
    </modules>
  </component>
</project>
```
**Key data to extract:** List of `.iml` file paths.

### 2.2. Module `.iml` File

Each module has its own `.iml` file, which contains detailed configuration for that module.

*   **Root Element:** `<module type="..." version="4">` (type might be `JAVA_MODULE`, etc.)
*   **Component Element:** `<component name="NewModuleRootManager" inherit-compiler-output="true">` (or similar name like "ExternalSystemSystemIdModuleRootManager" for Gradle/Maven).
    *   **`<output url="file://$MODULE_DIR$/build/classes/java/main" />`**: Specifies main output directory.
    *   **`<output-test url="file://$MODULE_DIR$/build/classes/java/test" />`**: Specifies test output directory.
    *   **`<exclude-output />`**: Indicates that output directories are excluded from content roots.
    *   **Content Root Element:** `<content url="file://$MODULE_DIR$">` (often multiple for source, test, resources)
        *   **Source Folder:** `<sourceFolder url="file://$MODULE_DIR$/src/main/java" isTestSource="false" />`
        *   **Test Folder:** `<sourceFolder url="file://$MODULE_DIR$/src/test/java" isTestSource="true" />`
        *   **Resource Folder:** `<sourceFolder url="file://$MODULE_DIR$/src/main/resources" type="java-resource" />`
        *   **Test Resource Folder:** `<sourceFolder url="file://$MODULE_DIR$/src/test/resources" type="java-test-resource" />`
        *   **Exclude Folder:** `<excludeFolder url="file://$MODULE_DIR$/.gradle" />`, `<excludeFolder url="file://$MODULE_DIR$/build" />`
    *   **Order Entry Elements (`<orderEntry>`):** Define dependencies and SDK.
        *   **Module SDK/JDK (specific to module):** `<orderEntry type="jdk" jdkName="11" jdkType="JavaSDK" />`
        *   **Inherited Project SDK/JDK:** `<orderEntry type="inheritedJdk" />`
        *   **Library Dependency:** `<orderEntry type="library" name="Gradle: org.jetbrains.kotlin:kotlin-stdlib:1.8.0" level="project" scope="COMPILE" />` (or `level="project"`, `level="module"`). The `name` attribute often contains coordinates for Maven/Gradle libraries. For local JARs, it might be a simple name.
        *   **Module-to-Module Dependency:** `<orderEntry type="module" module-name="my-other-module" scope="COMPILE" />`

**Example Snippet (`.iml`):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$">
      <sourceFolder url="file://$MODULE_DIR$/src/main/kotlin" isTestSource="false" />
      <sourceFolder url="file://$MODULE_DIR$/src/test/kotlin" isTestSource="true" />
      <sourceFolder url="file://$MODULE_DIR$/src/main/resources" type="java-resource" />
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" name="Gradle: org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20" level="project" />
    <orderEntry type="module" module-name="shared-utils" scope="TEST" />
  </component>
</module>
```
**Key data to extract:** Source/resource/test directories, module SDK/JDK, library dependencies (name, scope, version if parsable from name), module dependencies.

### 2.3. `.idea/misc.xml` (or similar for Project SDK)

Project-level settings, including the project SDK, are often found here, or in files like `jdk.table.xml` or `project.xml`. The structure can vary. A common pattern for SDK is:

*   **Root Element:** `<project version="4">`
*   **Component for Project SDK:** `<component name="ProjectRootManager" version="2" languageLevel="JDK_11" default="true" project-jdk-name="corretto-11" project-jdk-type="JavaSDK">`
    *   `project-jdk-name`: Name of the project JDK.
    *   `project-jdk-type`: Type of JDK (e.g., "JavaSDK").
    *   `languageLevel`: Project language level.
*   **Component for JDK Table (if separate, e.g. in `jdk.table.xml`):** `<component name="ProjectJdkTable">`
    *   `<jdk version="2">...</jdk>` elements detailing configured JDKs.

**Example Snippet (`misc.xml`):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_1_8" project-jdk-name="1.8" project-jdk-type="JavaSDK">
    <output url="file://$PROJECT_DIR$/out" />
  </component>
  <!-- Other components -->
</project>
```
**Key data to extract:** Project SDK name, version.

### 2.4. Project Name

*   `.idea/.name`: This simple text file, if it exists, directly contains the project name.
*   Directory Name: If `.idea/.name` is not present, the name of the project's root directory (the parent of `.idea`) is often used as the project name.

## 3. XML Parsing Strategy Selection

For parsing these XML files in Kotlin, several options exist:

1.  **`javax.xml.parsers.DocumentBuilderFactory` (DOM):**
    *   **Pros:** Easy to navigate the XML tree once parsed. Random access to nodes.
    *   **Cons:** Loads the entire XML file into memory, which can be inefficient for very large files (though IntelliJ config files are usually not excessively large). More verbose API.
2.  **`javax.xml.stream.XMLInputFactory` (StAX - Streaming API for XML):**
    *   **Pros:** Memory efficient as it reads the XML as a stream of events. Good performance. Allows processing XML event by event (start element, end element, characters, etc.). Provides good control over parsing.
    *   **Cons:** Forward-only access. API can be more complex to work with for deeply nested structures or when needing to jump around.
3.  **`org.xml.sax.helpers.DefaultHandler` (SAX):**
    *   **Pros:** Also event-based and memory efficient.
    *   **Cons:** Can be cumbersome to manage state with handler callbacks. Generally less preferred in modern Java/Kotlin than StAX for manual parsing.
4.  **`kotlinx.serialization` with an XML format:**
    *   **Pros:** Can directly deserialize XML into Kotlin data classes if the XML structure is regular and matches the classes. Type-safe.
    *   **Cons:** IntelliJ XML files can have some irregularities or use attributes in ways that might not map perfectly to simple serialization. Requires an XML format library compatible with `kotlinx.serialization` (e.g., `XML ゆ (Yaxb)` or `kotlinx.xml.serialization` if one becomes mature and stable). Might be overkill if only a few specific attributes/elements are needed.
5.  **Third-party Kotlin XML libraries (e.g., `xmlutil`, `ktxml`):**
    *   **Pros:** May offer more Kotlin-idiomatic APIs, extension functions, or simpler ways to extract data.
    *   **Cons:** Adds an external dependency. Need to evaluate maturity and maintenance.

**Chosen Strategy: StAX (`javax.xml.stream.XMLInputFactory`)**

**Justification:**

*   **Efficiency:** StAX is memory efficient, which is good practice even if current config files are small. It avoids loading the entire DOM.
*   **Control:** It provides fine-grained control over the parsing process, allowing us to iterate through events and extract exactly the information needed (specific elements and attributes) while ignoring irrelevant parts. This is useful for the semi-structured nature of IntelliJ XML files where we are targeting specific `<component>` and `<orderEntry>` tags.
*   **Standard Library:** It's part of the standard Java library (and thus available in Kotlin/JVM) without needing extra dependencies for basic functionality.
*   **Sufficient for Task:** While not as convenient as full data binding, its event-based nature is well-suited for pulling out specific pieces of information from different parts of the XML files. We don't need complex XPath queries or full DOM manipulation for the defined scope.

## 4. Locating Build Files

The agent needs to identify the build system (Maven, Gradle) and locate the primary build file.

*   **Strategy:**
    1.  **Project Root:** Check the project's root directory (where `.idea` is located) for:
        *   `pom.xml` (for Maven)
        *   `build.gradle` or `build.gradle.kts` (for Gradle)
    2.  **Module Directories:** If a project-level build file isn't found, or if the project might contain modules with their own build systems (less common for the primary build file but possible for multi-project setups opened as a single project):
        *   For each module, check its root directory (derived from the `.iml` file path) for `pom.xml` or `build.gradle`/`build.gradle.kts`. This is more relevant for identifying if a module *is* a Maven/Gradle module rather than finding the *project's* main build file.
    3.  **Priority:** Typically, a project-level build file defines the main build system.
    4.  **BuildSystemType:** The presence of `pom.xml` indicates `BuildSystemType.MAVEN`. The presence of `build.gradle` or `build.gradle.kts` indicates `BuildSystemType.GRADLE`. If neither is found at the project level, it might be `BuildSystemType.INTELLIJ_NATIVE` or `UNKNOWN`.

## 5. Summary of Key Information for Parsing

*   **`modules.xml`:**
    *   Parse `<module>` elements under `/project/component[@name='ProjectModuleManager']/modules`.
    *   Extract `filepath` attribute from each `<module>`.
*   **`.iml` files:**
    *   Parse `<component name="NewModuleRootManager">` (or similar).
    *   **Source/Resource Folders:**
        *   `<sourceFolder>` elements: get `url` attribute.
        *   Check `isTestSource` attribute (true/false).
        *   Check `type` attribute (e.g., "java-resource", "java-test-resource").
    *   **SDK/JDK:**
        *   `<orderEntry type="jdk"`: get `jdkName`, `jdkType`.
        *   `<orderEntry type="inheritedJdk"`: indicates use of project JDK.
    *   **Library Dependencies:**
        *   `<orderEntry type="library"`: get `name`, `scope`, `level`. Version might need to be parsed from `name`.
    *   **Module Dependencies:**
        *   `<orderEntry type="module"`: get `module-name`, `scope`.
*   **`misc.xml` (or similar):**
    *   Parse `<component name="ProjectRootManager">`.
    *   Extract `project-jdk-name`, `project-jdk-type`, `languageLevel`.
*   **Project Name:**
    *   Read content of `.idea/.name` file.
    *   If not found, use the name of the project root directory.
*   **Build Files:**
    *   Check for `pom.xml`, `build.gradle`, `build.gradle.kts` in the project root.

This research provides a solid foundation for implementing the XML parsing logic for the IntelliJ Enumeration Agent.**Research Document Creation**

The research on IntelliJ project file formats and XML parsing strategies has been compiled. The chosen parsing strategy is StAX (`javax.xml.stream.XMLInputFactory`) due to its efficiency, control, and status as a standard library component. The typical structures of `modules.xml`, `.iml` files, and `misc.xml` have been outlined, along with a strategy for locating build files.

This information has been compiled into `nexus/docs/intellij_project_format_research.md`.

All deliverables for this subtask are complete.
