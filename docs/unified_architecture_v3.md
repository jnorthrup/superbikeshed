# Unified Architecture v3: DGM in Nexus

## 1. Introduction

This document describes the DGM-in-Nexus architecture, where the core logic of the Darwin Gödel Machine (DGM) for self-improvement has been integrated into Nexus, the universal development agent. Nexus, built on Kotlin Multiplatform (KMP), now serves as the primary orchestrator of the DGM loop. It leverages specialized Python components for tasks requiring specific Python-based ecosystems, such as complex LLM interactions via Langchain and standardized benchmark execution.

This evolution centralizes the DGM's state management, evolutionary orchestration, and workspace operations within the type-safe and performant Kotlin environment, while still harnessing the power of Python's rich AI/ML tooling.

For the original design considerations and detailed interface definitions, please refer to the [DGM Core Logic to Nexus Migration Design Document](./dgm_to_nexus_migration_design.md).

## 2. Core Architectural Shift: DGM within Nexus

The primary DGM control loop and state are now managed by Nexus. This is orchestrated by `EvolutionEngineImpl` and a suite of Kotlin-based services.

### 2.1. Nexus as the DGM Orchestrator
`nexus.core.dgm.engine.EvolutionEngineImpl` is the heart of the DGM functionality within Nexus. It drives the iterative self-improvement cycle:
- Selecting parent code versions from the archive.
- Choosing or generating tasks/benchmarks.
- Orchestrating solution proposal using LLMs.
- Managing code changes in a temporary workspace.
- Triggering validation against benchmarks.
- Deciding whether to accept and archive improvements.

### 2.2. Key Kotlin DGM Services
These services, defined in `nexus.core.dgm.services`, implement the core DGM functionalities:

- **`DgmStateService`**: Responsible for managing the DGM archive.
    - _Implementation Example_: `InMemoryDgmStateService` stores the archive (code snapshots and metadata) in memory.
- **`SolutionProposerService`**: Responsible for generating potential solutions (code changes) to a given DGM task.
    - _Implementation Example_: `RemoteSolutionProposerService` delegates this to an external Python script, preparing the request and interpreting the response.
- **`WorkspaceService`**: Manages the local filesystem workspace where code is checked out, modified, and built for validation.
    - _Implementation Example_: `LocalWorkspaceService` uses file system primitives (abstracted via `expect/actual FileSystemUtils` for KMP) to manage temporary directories and file operations.
- **`BenchmarkValidationService`**: Responsible for executing benchmarks against the modified code in the workspace and returning validation results.
    - _Implementation Example_: `RemoteBenchmarkValidationService` delegates this to an external Python script, handling request/response and workspace path context.

### 2.3. DGM Data Structures (TrikeShed)
Core DGM concepts like code snapshots, archive entries, tasks, and results are represented using Kotlin data classes and typealiases, primarily leveraging TrikeShed's immutable `Series<T>` and `Join<A,B>` types. These are defined in `nexus.core.dgm.DgmTypes.kt`. This ensures efficient and type-safe data manipulation within the Kotlin DGM components.

## 3. Specialized Python Components

Nexus DGM delegates certain specialized tasks to external Python scripts. These scripts act as services callable by Nexus.

### 3.1. Solution Proposal Service (`mock_solution_proposer.py`)
- **Role:** To interact with Large Language Models (LLMs), potentially using the Langchain framework, to generate code modifications based on a given task and codebase.
- **Interface:** Called by `JvmExternalSolutionProposer` (the JVM `actual` for `RemoteSolutionProposerService`).
- **Interaction:** Receives a `DgmTaskForPython` JSON object and is expected to return a `ProposedChangesFromPython` JSON object.

### 3.2. Benchmark Execution Service (`mock_benchmark_runner.py`)
- **Role:** To execute specific benchmarks (e.g., SWE-bench, Polyglot) against the code provided in a designated workspace.
- **Interface:** Called by `JvmExternalBenchmarkExecutor` (the JVM `actual` for `RemoteBenchmarkValidationService`).
- **Interaction:** Receives a `BenchmarkInvocation` JSON object (including the workspace path and benchmark ID) and is expected to return a `BenchmarkResultFromPython` JSON object.

## 4. Kotlin-Python Interaction Mechanism

The interaction between Nexus (Kotlin) and the Python components is managed via command-line interface (CLI) calls orchestrated by the `ProcessExecutionService`.

- **Mechanism:** Kotlin services like `JvmExternalSolutionProposer` and `JvmExternalBenchmarkExecutor` use an implementation of `ProcessExecutionService` (e.g., `JvmProcessExecutionService`) to execute the Python scripts.
- **Data Exchange:**
    - The Kotlin service serializes a request data class (e.g., `DgmTaskForPython`, `BenchmarkInvocation`) into a JSON string.
    - This JSON string is passed as a command-line argument (e.g., `--request_json '{...}'`) to the Python script.
    - The Python script parses this JSON, performs its task, and prints its result (e.g., `ProposedChangesFromPython`, `BenchmarkResultFromPython`) as a JSON string to its standard output.
    - The Kotlin service captures the stdout from the Python script and deserializes it back into the appropriate Kotlin data class.
- **Error Handling:** Errors during script execution (non-zero exit codes, stderr output, timeouts) are captured by `ProcessExecutionService` and propagated. JSON parsing errors are also handled.

### JSON Contracts:
- **Request to Solution Proposer:** `DgmTaskForPython` (defined in `RemoteSolutionProposerService.kt`)
- **Response from Solution Proposer:** `ProposedChangesFromPython` (defined in `RemoteSolutionProposerService.kt`)
- **Request to Benchmark Executor:** `BenchmarkInvocation` (defined in `RemoteBenchmarkValidationService.kt`)
- **Response from Benchmark Executor:** `BenchmarkResultFromPython` (defined in `RemoteBenchmarkValidationService.kt`)

(Refer to the respective Kotlin files for detailed structure of these data classes).

## 5. Langchain Usage

Langchain is primarily utilized within the Python ecosystem, specifically by the conceptual `dgm_agent_service.py` (represented by `mock_solution_proposer.py` in the current implementation).

- **Orchestration:** Nexus (Kotlin), through `RemoteSolutionProposerService` and its `actual` JVM implementation `JvmExternalSolutionProposer`, initiates a request for a solution.
- **Execution:** The `JvmExternalSolutionProposer` calls the Python script. This Python script is responsible for leveraging Langchain's capabilities:
    - Managing prompts for LLMs.
    - Orchestrating chains of LLM calls.
    - Utilizing Langchain agents and tools for complex reasoning or interaction with other external APIs if needed.
- **Abstraction:** Nexus does not directly interact with Langchain APIs in Kotlin. Instead, it relies on the Python component to abstract these interactions, providing a clear service boundary. The `LangchainAgentConfigForPython` data structure within `DgmTaskForPython` allows Nexus to pass configuration hints to the Python/Langchain layer.

## 6. Foundational Technologies

### 6.1. CCEK (Coroutine Context Enabled Kotlin)
- **Service Management:** All core DGM services in Kotlin (`EvolutionEngine`, `DgmStateService`, etc.) and supporting services like `ProcessExecutionService` are designed as interfaces with a `companion object Key: CoroutineContext.Key<InterfaceName>`. This pattern allows for their implementations to be injected and accessed via Kotlin's coroutine context, promoting loose coupling and easier testability.
- **Contextual Execution:** The `CCEKContext` (currently a typealias for `CoroutineContext`) is passed through service method calls, enabling services to access other required services or contextual data.

### 6.2. TrikeShed
- **Immutable Data Structures:** TrikeShed's `Series<T>` and `Join<A,B>` are the fundamental building blocks for DGM's core data types in Kotlin, as defined in `nexus.core.dgm.DgmTypes.kt`. This includes:
    - `CodeSnapshot`: Representing the state of a collection of files.
    - `ArchiveEntry`, `DgmArchive`: Structuring the DGM's memory of past states and improvements.
    - `DgmTask`, `ImprovementCandidate`, `ValidationResult`: Representing the various stages and outcomes of the DGM loop.
- **Benefits:** Using TrikeShed ensures that DGM operations within Nexus are performed on immutable, well-defined, and potentially highly optimized data structures, suitable for complex queries and transformations if needed in the future.

## 7. Updated Mermaid Diagram

```mermaid
graph TD
    subgraph "Nexus Core (Kotlin/KMP)"
        A1[EvolutionEngineImpl]
        A2[DgmStateService (InMemoryDgmStateService)]
        A3[SolutionProposerService (RemoteSolutionProposerService)]
        A4[WorkspaceService (LocalWorkspaceService)]
        A5[BenchmarkValidationService (RemoteBenchmarkValidationService)]
        A6[ProcessExecutionService (JvmProcessExecutionService - JVM Actual)]

        A1 --> A2
        A1 --> A3
        A1 --> A4
        A1 --> A5

        A3 --> A6
        A5 --> A6
        A4 -.-> B1_FS[FileSystem (via expect/actual FileSystemUtils)]
    end

    subgraph "Python Environment"
        B1_FS
        B2[mock_solution_proposer.py <br> (Langchain Interaction)]
        B3[mock_benchmark_runner.py <br> (Benchmark Execution)]
    end

    subgraph "Data Structures (TrikeShed based)"
        C1[DgmTypes.kt <br> (ArchiveEntry, CodeSnapshot, DgmTask, etc.)]
    end

    subgraph "CCEK Framework"
        D1[Service Keys & Context]
    end

    A6 -- "CLI Call (JSON Request)" --> B2
    B2 -- "CLI Response (JSON Result)" --> A6

    A6 -- "CLI Call (JSON Request, Workspace Path)" --> B3
    B3 -- "CLI Response (JSON Result)" --> A6

    A1 ..-> C1
    A2 ..-> C1
    A3 ..-> C1
    A4 ..-> C1
    A5 ..-> C1

    A1 -.-> D1
    A2 -.-> D1
    A3 -.-> D1
    A4 -.-> D1
    A5 -.-> D1
    A6 -.-> D1

    classDef nexusCore fill:#aliceblue,stroke:#666,stroke-width:2px;
    classDef pythonEnv fill:#lightyellow,stroke:#666,stroke-width:2px;
    classDef dataTypes fill:#e6ffe6,stroke:#666,stroke-width:2px;
    classDef ccekFramework fill:#f3e5f5,stroke:#666,stroke-width:2px;

    class A1,A2,A3,A4,A5,A6 nexusCore;
    class B1_FS,B2,B3 pythonEnv;
    class C1 dataTypes;
    class D1 ccekFramework;
```

## 8. Conclusion

Migrating the DGM core to Nexus (Kotlin/KMP) establishes a robust, type-safe, and extensible foundation for self-improving agent development. The architecture clearly defines the responsibilities between the Kotlin-based orchestration/state management and specialized Python components for LLM/Langchain and benchmark execution. The CLI/JSON interaction model provides a flexible bridge between these two environments. This setup leverages the strengths of both Kotlin (performance, type safety, KMP) and Python (rich AI/ML ecosystem) to build a powerful and adaptable DGM system.
