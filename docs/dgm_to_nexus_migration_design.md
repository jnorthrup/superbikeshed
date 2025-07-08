# DGM Core Logic to Nexus (Kotlin Common) Migration: Design Document Outline

## 1. Introduction

This document outlines the design for migrating the core logic of the Darwin Gödel Machine (DGM) from its current Python implementation to Nexus, leveraging Kotlin Common, TrikeShed for data structures, and CCEK for service management. The goal is to integrate DGM's self-improvement capabilities directly into the Nexus universal development agent, making Nexus the primary DGM. Specialized Python components will still be utilized for tasks like specific Langchain interfacing and benchmark execution, requiring clear Kotlin-to-Python communication.

## 2. Key Kotlin Data Structures (TrikeShed Types)

This section will define the primary data structures for representing DGM's state, tasks, and results using TrikeShed's `Series<T>` and `Join<A,B>` types.

### 2.1. DGM State Representation
    - **`ArchiveEntryId`**: `typealias ArchiveEntryId = String` (or `Join<String, Int>` for versioning)
    - **`VersionMetadata`**:
        ```kotlin
        // Example structure, to be refined
        typealias VersionMetadata = Join<
            Join<ArchiveEntryId, String>, // ParentCommitId, CommitHash
            Join<Series<String>, Long>    // Tags (e.g., "improvement", "refactor"), Timestamp
        >
        ```
    - **`DgmArchive`**:
        ```kotlin
        // Maps ArchiveEntryId to its full code snapshot and metadata
        // Could be a Series of Join<ArchiveEntryId, Join<CodeSnapshot, VersionMetadata>>
        // Or leverage a CCEK service for more complex storage (e.g., key-value store abstraction)
        typealias CodeSnapshot = Series<Join<FilePath, FileContent>> // FilePath = String, FileContent = String
        typealias ArchiveEntry = Join<CodeSnapshot, VersionMetadata>
        typealias DgmArchive = Series<Join<ArchiveEntryId, ArchiveEntry>>
        ```
        *Discussion on using TrikeShed's ISAM/FlatFile for persistent storage vs. in-memory representation.*

### 2.2. DGM Task Representation
    - **`TaskId`**: `typealias TaskId = String`
    - **`BenchmarkId`**: `typealias BenchmarkId = String` (e.g., "swe-bench-123", "polyglot-python-45")
    - **`DgmTask`**:
        ```kotlin
        typealias DgmTask = Join<
            Join<TaskId, ArchiveEntryId>, // TaskId, ParentEntryId for improvement
            Join<BenchmarkId, Series<String>> // Benchmark to run, Optional parameters/config for the task
        >
        ```
    - **`ImprovementCandidate`**: (Proposed solution before validation)
        ```kotlin
        typealias ImprovementCandidate = Join<
            Join<DgmTask, CodeSnapshot>, // Original Task, Proposed Code Changes
            Join<String, Series<String>> // LLM/Proposer ID, Rationale/Explanation
        >
        ```

### 2.3. DGM Result Representation
    - **`ValidationResult`**:
        ```kotlin
        // Boolean might be too simple, could be a sealed class for Pass, Fail, Error, Timeout
        typealias BenchmarkScore = Join<String, Double> // MetricName, ScoreValue
        typealias ValidationStatus = String // e.g., "PASSED", "FAILED_TESTS", "TIMEOUT", "ERROR"
        typealias ValidationResult = Join<
            Join<ValidationStatus, Series<BenchmarkScore>>,
            String // Detailed log output or error message
        >
        ```
    - **`ArchivedImprovement`**: (Validated and accepted improvement)
        ```kotlin
        typealias ArchivedImprovement = Join<
            Join<ArchiveEntryId, ImprovementCandidate>, // New ArchiveEntryId, Original Candidate
            ValidationResult
        >
        ```

## 3. Core Kotlin Interfaces for DGM Services

This section will define the Kotlin interfaces for the core DGM services. These services will be implemented within Nexus and managed via CCEK.

### 3.1. `EvolutionEngine`
    ```kotlin
    interface EvolutionEngine {
        suspend fun initializeArchive(): CCEKContext // Or return initial ArchiveEntryId
        suspend fun runIteration(context: CCEKContext): ArchivedImprovement? // Returns new archive entry if successful
        suspend fun selectParentEntry(archive: DgmArchive, context: CCEKContext): ArchiveEntryId
        suspend fun selectBenchmarkEntry(context: CCEKContext): BenchmarkId // Could be more complex based on strategy
    }
    ```

### 3.2. `SolutionProposerService`
    ```kotlin
    interface SolutionProposerService {
        // Proposes changes to the codebase for a given task
        suspend fun proposeSolution(task: DgmTask, parentCode: CodeSnapshot, context: CCEKContext): ImprovementCandidate
    }
    ```

### 3.3. `WorkspaceService`
    ```kotlin
    interface WorkspaceService {
        // Manages the working directory for DGM operations
        suspend fun setupWorkspace(parentCode: CodeSnapshot, context: CCEKContext): FilePath // Returns root path of workspace
        suspend fun applyChanges(workspacePath: FilePath, changes: CodeSnapshot, context: CCEKContext): Boolean
        suspend fun getCurrentSnapshot(workspacePath: FilePath, context: CCEKContext): CodeSnapshot
        suspend fun cleanupWorkspace(workspacePath: FilePath, context: CCEKContext)
    }
    ```

### 3.4. `BenchmarkValidationService`
    ```kotlin
    interface BenchmarkValidationService {
        // Validates a solution against a benchmark
        suspend fun validateSolution(candidate: ImprovementCandidate, workspacePath: FilePath, benchmarkId: BenchmarkId, context: CCEKContext): ValidationResult
    }
    ```

### 3.5. `DgmStateService` (Archive Management)
    ```kotlin
    interface DgmStateService {
        suspend fun loadArchive(context: CCEKContext): DgmArchive
        suspend fun saveArchiveEntry(entry: ArchivedImprovement, context: CCEKContext): ArchiveEntryId
        suspend fun getEntrySnapshot(entryId: ArchiveEntryId, context: CCEKContext): CodeSnapshot?
        suspend fun getEntryMetadata(entryId: ArchiveEntryId, context: CCEKContext): VersionMetadata?
    }
    ```

## 4. CCEK Integration Plan

This section will detail how the DGM services will integrate with CCEK.

### 4.1. Service Key Definitions
    - Each DGM interface above will have a companion `CoroutineContext.Key`.
        ```kotlin
        // Example for EvolutionEngine
        companion object Key : CoroutineContext.Key<EvolutionEngine>
        ```

### 4.2. Dependencies on Existing CCEK Services
    - **`WorkspaceService`**: Will heavily rely on `NioService.Key` (for file I/O) and potentially `AsyncIoEngine.Key` for non-blocking operations. It might also need a process execution service (new or existing CCEK key) for applying patches or interacting with version control systems within the workspace.
    - **`SolutionProposerService`**:
        - Will need an LLM interaction service. This could be a new `LlmService.Key` within CCEK, or it could interface with the Python DGM components for Langchain execution (see Section 6).
        - May use `VulkanService.Key` if any local model inference is offloaded to GPU via CCEK compute services.
    - **`BenchmarkValidationService`**:
        - Will need a robust process execution service (new or existing CCEK key) to run benchmark scripts (potentially Python scripts).
        - Will use `NioService.Key` for accessing benchmark logs and results.
    - **`DgmStateService`**:
        - For in-memory archives, no direct CCEK service dependency beyond context.
        - For persistent archives, could use `NioService.Key` for local file storage or integrate with a future `DatabaseService.Key` or `KeyValueStoreService.Key` if such CCEK services are developed.
    - **`EvolutionEngine`**: Will aggregate other DGM services and use the `DispatcherRegistry.Key` for invoking methods on them.

### 4.3. New CCEK Services (Potential)
    - **`ProcessExecutionService.Key`**: A generic service for running external processes, managing stdin/stdout/stderr, and timeouts. Essential for `WorkspaceService` and `BenchmarkValidationService`.
    - **`LlmInteractionService.Key`**: If LLM calls are to be made directly from Kotlin. This service would abstract different LLM providers and handle API calls. Could also be a facade to Python-based Langchain.
    - **`RemoteBenchmarkService.Key`**: If benchmarks are executed on a separate Python environment, this service would manage the communication.

## 5. High-Level Flow of DGM Loop within Nexus (Kotlin)

This section describes the sequence of operations in a single DGM self-improvement iteration managed by Nexus.

1.  **Initialization**: `EvolutionEngine.initializeArchive()` ensures a starting point for the archive.
2.  **Iteration Start**: `EvolutionEngine.runIteration()` is called.
    a.  **Parent Selection**: `EvolutionEngine.selectParentEntry()` chooses a promising entry from the `DgmArchive` (managed by `DgmStateService`).
    b.  **Benchmark/Task Selection**: `EvolutionEngine.selectBenchmarkEntry()` (or a more sophisticated task generation mechanism) defines the current goal. A `DgmTask` is formulated.
    c.  **Workspace Setup**:
        i.  `DgmStateService.getEntrySnapshot()` retrieves the parent code.
        ii. `WorkspaceService.setupWorkspace()` prepares a temporary working directory with the parent code.
    d.  **Solution Proposal**:
        i.  `SolutionProposerService.proposeSolution()` generates an `ImprovementCandidate`. This might involve:
            -   Formatting prompts.
            -   Interacting with an LLM (via `LlmInteractionService.Key` or Python bridge).
            -   Parsing LLM output into a `CodeSnapshot` of changes.
    e.  **Applying Changes (Trial)**: `WorkspaceService.applyChanges()` applies the proposed `CodeSnapshot` to the workspace.
    f.  **Validation**:
        i.  `BenchmarkValidationService.validateSolution()` executes the relevant benchmark against the modified code in the workspace. This involves communication with the Python benchmark execution environment.
        ii. A `ValidationResult` is returned.
    g.  **Archive Update**:
        i.  If `ValidationResult` is positive (e.g., "PASSED" and score improved):
            1.  `WorkspaceService.getCurrentSnapshot()` gets the final validated code.
            2.  An `ArchivedImprovement` object is created.
            3.  `DgmStateService.saveArchiveEntry()` adds the new entry to the archive, returning a new `ArchiveEntryId`.
            4.  The `ArchivedImprovement` (or its ID) is returned by `runIteration`.
        ii. If validation fails, the failure is logged, and `runIteration` might return `null` or an error status.
    h.  **Workspace Cleanup**: `WorkspaceService.cleanupWorkspace()` cleans the temporary directory.
3.  **Loop**: Repeat from step 2.

## 6. Interface Definition for Kotlin-to-Python Communication

This section defines how Nexus (Kotlin) will communicate with specialized Python DGM components, particularly for Langchain execution and benchmark invocation.

### 6.1. Communication Method
    - **Recommendation**: gRPC or a simple HTTP/JSON-RPC API.
        - **gRPC**: Type-safe, efficient, good for streaming (e.g., benchmark logs). Requires protobuf definitions.
        - **HTTP/JSON-RPC**: Simpler to implement initially, widely supported. Less efficient for large data or streams.
    - **Alternative**: Local process execution with structured JSON/protobuf over stdin/stdout if Python components are CLI tools.

### 6.2. Data Formats
    - **General**: JSON is a good default for simplicity. Protobuf for performance if gRPC is chosen.
    - **`CodeSnapshot`**: Can be serialized as a list of `{"filePath": "...", "fileContent": "..."}` objects. For large snapshots, consider sending diffs or using a shared filesystem accessible by both Kotlin and Python environments (if co-located).
    - **`DgmTaskForPython`**: A subset of `DgmTask` relevant to the Python component.
        ```json
        // Example for Langchain solution proposal
        {
          "taskId": "task123",
          "parentEntryId": "entryXYZ",
          "benchmarkId": "swe-bench-abc", // For context
          "prompt": "Refactor this function to improve readability...", // Specific instruction for LLM
          "codeFiles": [ // Relevant files from CodeSnapshot
            {"filePath": "src/main/kotlin/FileA.kt", "content": "..."},
            {"filePath": "src/test/kotlin/TestA.kt", "content": "..."}
          ],
          "langchainAgentConfig": { // Optional: specific Langchain agent/tool setup
            "agentName": "code-refactor-agent",
            "tools": ["file-read", "code-edit"]
          }
        }
        ```
    - **`ProposedChangesFromPython`**:
        ```json
        {
          "taskId": "task123",
          "changedFiles": [ // CodeSnapshot format
            {"filePath": "src/main/kotlin/FileA.kt", "content": "... updated content ..."},
            {"filePath": "src/test/kotlin/TestA.kt", "content": "... new test content ..."}
          ],
          "llmOutputLog": "...", // Raw LLM interaction log
          "status": "SUCCESS" // or "ERROR"
          "errorMessage": "..." // if status is ERROR
        }
        ```
    - **`BenchmarkInvocation`**:
        ```json
        {
          "benchmarkId": "swe-bench-abc",
          "workspacePath": "/path/to/dgm/workspace/on/shared/volume", // Path accessible to Python
          "timeoutSeconds": 300
        }
        ```
    - **`BenchmarkResultFromPython`**:
        ```json
        {
          "benchmarkId": "swe-bench-abc",
          "status": "PASSED", // "FAILED_TESTS", "TIMEOUT", "ERROR"
          "scores": [
            {"metricName": "pass_rate", "value": 1.0},
            {"metricName": "exec_time_ms", "value": 12345.0}
          ],
          "logOutput": "...", // Stdout/stderr from benchmark run
          "errorDetails": "..." // if status is ERROR
        }
        ```

### 6.3. Python Service Endpoints (Conceptual)

    - **`/propose_solution` (for Langchain/LLM based solution proposal)**
        - Input: `DgmTaskForPython`
        - Output: `ProposedChangesFromPython`
    - **`/run_benchmark` (for benchmark execution)**
        - Input: `BenchmarkInvocation`
        - Output: `BenchmarkResultFromPython` (can be streamed if using gRPC or chunked HTTP response)

### 6.4. Langchain Orchestration Clarification
    - **Nexus (Kotlin)** is the primary orchestrator of the DGM loop.
    - For CCEK executive service scaffolding (and potentially other complex code gen tasks), Nexus might use its `SolutionProposerService`.
    - This service, when needing LLM interaction, can either:
        1. Call a Kotlin-native LLM service (if `LlmInteractionService.Key` is implemented in Kotlin).
        2. Delegate to the Python DGM component via the defined Kotlin-to-Python interface (e.g., calling the `/propose_solution` endpoint). This Python component would then use its existing Langchain tools and agents.
    - This allows Nexus to leverage mature Python-based Langchain capabilities without reimplementing them in Kotlin, while still keeping the main control flow and state management within the Kotlin/KMP environment.

## 7. Error Handling and Resilience

- Define strategies for handling timeouts, errors from Python components, LLM failures, and benchmark crashes.
- Retry mechanisms and fallback strategies.

## 8. Testing Strategy

- Unit tests for individual Kotlin services and data structures.
- Integration tests for the DGM loop within Nexus (mocking Python interfaces).
- End-to-end tests involving actual Kotlin-Python communication.

## 9. Future Considerations

- Distributed execution of DGM tasks.
- More sophisticated parent/task selection strategies.
- Integration with Nexus's broader context learning and human-in-the-loop capabilities.

This outline provides a foundational structure for the design document. Each section will be expanded with more detailed specifications, code examples (pseudocode or actual Kotlin), and diagrams where appropriate.
