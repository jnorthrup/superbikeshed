# Omnibus Task List

This document outlines prioritized tasks for the Superbikeshed project, organized by functional categories. P0 and P1 tasks are detailed for immediate action, while P2-P4 tasks are summarized by theme or epic to provide a broader roadmap.

## 1. `CORE_TYPESYSTEM_AND_DATAMODEL`

**Focus:** Foundational data structures, type system compliance (especially `CLAUDE.md`), and core data processing logic. This is primarily TrikeShed (both Kotlin and TypeScript versions).

---
### P0 Tasks: Foundational Compliance

- [x] **Task 1:** TrikeShed Core: Implement `List<T>` to `Series<T>` Migration
- [x] **Task 2:** TrikeShed Core: Implement `MutableList<T>` to `Series<T>` with Alpha Transforms Migration
- [x] **Task 3:** TrikeShed Core: Implement `Pair<A,B>` to `Join<A,B>` Migration
- [x] **Task 4:** TrikeShed Core: Eliminate Raw Collection Usage
- [x] **Task 5:** TrikeShed Core: Enforce `Join<A,B>` via `j` as Only Composition Operator
- [x] **Task 6:** TrikeShed Core: Enforce Alpha Transform (`.α`) as Only Transformation Operator
- [x] **Task 7:** TrikeShed Core: Implement Play Button (`▶`) Materialization
- [x] **Task 8:** TrikeShed Core: Convert Wrappers to `@JvmInline value class`
- [x] **Task 9:** TrikeShed Core: Define Permanent Type Aliases for Primitives
- [x] **Task 10:** TrikeShed Core: Define `Tensor<T>` as `Join<IntArray,(IntArray)->T>`
- [x] **Task 11:** TrikeShed Core: Clarify `Cursor` vs `Series<RowVec>`
- [x] **Task 12:** TrikeShed Core: Remove "Quic" Prefix from `QuicInstant`

**Task 13 (P0)**
*   **Title:** TrikeShed Compliance: Eliminate Simulated Benchmarks
*   **Goal:** Remove any benchmark code from TrikeShed that simulates performance metrics rather than measuring actual running code, adhering to `CLAUDE.md`.
*   **References:** `todo/trikeshed_todos.md`, `CLAUDE.md`
*   **Acceptance Criteria:**
    1.  Codebase audited for performance measurement code.
    2.  Code generating "fake" performance data is removed.
    3.  Only benchmarks running against actual operations are retained.
    4.  Builds and tests pass.

**Task 14 (P0)**
*   **Title:** TrikeShed Compliance: Eliminate Fake Demonstrations
*   **Goal:** Remove or refactor any code in TrikeShed that only simulates behavior to ensure all code is functional or clearly marked as TODO, as per `CLAUDE.md`.
*   **References:** `todo/trikeshed_todos.md`, `CLAUDE.md`
*   **Acceptance Criteria:**
    1.  Codebase audited for demo code faking functionality.
    2.  Such code is removed or refactored into working implementations.
    3.  If functionality cannot be implemented, it's marked as TODO.
    4.  Builds and tests pass.

**Task 15 (P0)**
*   **Title:** TrikeShed Compliance: Remove Mock Functionality & Placeholder Responses
*   **Goal:** Eradicate any mock implementations or placeholder responses in TrikeShed that return hardcoded success or data without real operations, aligning with `CLAUDE.md`.
*   **References:** `todo/trikeshed_todos.md`, `CLAUDE.md`
*   **Acceptance Criteria:**
    1.  Audit identifies mock methods and placeholder responses.
    2.  These are replaced with real implementations or proper error handling/TODOs.
    3.  System behaves genuinely.
    4.  Builds and tests pass.

**Task 16 (P0)**
*   **Title:** TrikeShed Compliance: Convert Demo-Only Code
*   **Goal:** Identify and convert TrikeShed code that is purely for demonstration and cannot perform real work into functional implementations or remove it, following `CLAUDE.md`.
*   **References:** `todo/trikeshed_todos.md`, `CLAUDE.md`
*   **Acceptance Criteria:**
    1.  Audit identifies demo-only code paths.
    2.  Paths are made operational or removed.
    3.  Remaining TODO items are clearly marked.
    4.  Builds and tests pass.

---
### P1 Tasks: Core Integration & System Foundations

**Task 25 (P1)**
*   **Title:** TrikeShed Integrations: Initial DGM Support
*   **Goal:** Ensure TrikeShed's core types and operations (once P0 compliance is met) are usable by DGM, focusing on efficient data serialization/deserialization and support for incremental data processing if required by DGM's operations.
*   **References:** `todo/trikeshed_todos.md` (Integration Support)
*   **Acceptance Criteria:**
    1.  Key TrikeShed data structures for DGM can be serialized/deserialized efficiently.
    2.  APIs support incremental updates suitable for DGM.
    3.  A prototype or test case demonstrates DGM using TrikeShed for basic data manipulation.

**Task 26 (P1)**
*   **Title:** `trikeshed-ts` Core: Implement Core Functionality and API
*   **Goal:** Develop and unit test the fundamental data structures (`Join`, `Series`, `Tensor`, `Cursor`) and operations (zip, combine, materialize, broadcasting, slicing) for the `trikeshed-ts` (TypeScript) library. Provide TSDoc comments for all public APIs.
*   **References:** `TODO_Roadmap.md` (Section I: `trikeshed-ts`)
*   **Acceptance Criteria:**
    1.  Core data structures (`Join`, `Series`, `Tensor`, `Cursor`) are implemented in TypeScript.
    2.  Specified advanced operations are functional.
    3.  Comprehensive unit tests cover core functions with high code coverage.
    4.  All public APIs are documented with TSDoc comments.
    5.  Library is usable for basic data manipulation for the RTS game.

---
### P2-P4 Tasks Overview:

*   **P2: TrikeShed Architecture Evolution & Optimization:**
    *   Complete tensor-first columnar processing.
    *   Enhance `Series<T>` as primary collection type.
    *   Implement Context-driven development patterns (CCEK).
    *   Optimize Hot/Cold Path & Verify Zero-Cost Abstractions.
*   **P3: `trikeshed-ts` Advanced Features & Optimization:**
    *   Performance Optimization for immutable updates in `trikeshed-ts`.
    *   Enhanced Error Handling in `trikeshed-ts`.
    *   Implement `CursorWithMeta` and column operations in `trikeshed-ts`.
*   **P4: Model CRUD via Generic DSEL Tree:**
    *   Design and implement a DSEL for model CRUD operations on TrikeShed.
    *   Develop "garden collection lillpady" storage system.

---
## 2. `AI_AGENT_SELF_IMPROVEMENT_AND_ORCHESTRATION`

**Focus:** The core logic of DGM, its self-improvement loop, integration with LLMs (Langchain), and the evolution towards the Nexus intelligence layer.

---
### P1 Tasks: Core Integration & System Foundations

**Task 20 (P1)**
*   **Title:** DGM Core: Implement Langchain Orchestrator for Self-Improvement Loop
*   **Goal:** Integrate a Langchain-based orchestrator into DGM to manage the flow of its self-improvement loop, including prompt management, model interaction, and tool usage.
*   **References:** `todo/MASTER_TODO.md` (Priority 1: Grand Integration Vision)
*   **Acceptance Criteria:**
    1.  A Langchain (or similar framework) orchestrator is implemented within DGM.
    2.  Orchestrator manages the sequence of operations for a DGM self-improvement iteration.
    3.  Integration points for prompts, LLM calls, and DGM tools are defined and functional.
    4.  Basic DGM loop successfully runs end-to-end using the orchestrator.

**Task 21 (P1)**
*   **Title:** DGM Core: Design Tool Integration Layer
*   **Goal:** Design a clear and extensible tool integration layer that allows DGM (and subsequently Nexus) to interact with various components and capabilities.
*   **References:** `todo/MASTER_TODO.md` (Priority 1: Grand Integration Vision)
*   **Acceptance Criteria:**
    1.  Document outlining the architecture of the tool integration layer is produced.
    2.  Design specifies how new tools can be defined, registered, and invoked.
    3.  Design considers data exchange formats and error handling.
    4.  Design is reviewed and approved.

**Task 24 (P1)**
*   **Title:** Nexus Core: Implement Phase 1 - Core Agent Foundation
*   **Goal:** Develop the initial foundational components of the Nexus agent, including basic environment scanning, simple task execution capabilities, the groundwork for its learning system, and defining its data architecture using TrikeShed.
*   **References:** `nexus/README.md` (Implementation Plan: Phase 1)
*   **Acceptance Criteria:**
    1.  Nexus agent can perform basic environment scans.
    2.  Nexus agent can execute a predefined, simple task.
    3.  Data structures for learning are defined using TrikeShed types.
    4.  A basic learning mechanism is implemented.

---
### P2-P4 Tasks Overview:

*   **P2: DGM & Nexus Evolution:**
    *   Implement "boosting DGM" with enhanced capabilities.
    *   Nexus: Phase 2 - Intelligence Layer (Hybrid evolution engine, pattern learning, context synthesis).
*   **P3: Advanced DGM/Nexus Capabilities:**
    *   Agent assistance for DGM operations (code analysis, error detection) via Bao-Cline.
*   **P4: Visionary Nexus Features:**
    *   Nexus: Phase 4 - Advanced Features (Predictive capabilities, deep learning integration, community knowledge sharing).

---
## 3. `IDE_INTEGRATION_AND_DEVELOPER_TOOLS_FRAMEWORK`

**Focus:** Bao-Cline development, its evolution into the Nexus universal adapter layer, and general developer tool integration (VCS, build systems, IDEs). Also includes k2script as a developer scripting tool.

---
### P1 Tasks: Core Integration & System Foundations

**Task 22 (P1)**
*   **Title:** Bao-Cline/DGM: Develop Bao-Cline Interface for DGM Control and Monitoring
*   **Goal:** Create UI elements and backend logic within Bao-Cline to allow users to control DGM operations and monitor its progress and results.
*   **References:** `todo/MASTER_TODO.md`, `todo/bao_cline_todos.md`
*   **Acceptance Criteria:**
    1.  Bao-Cline webview includes new UI components for DGM interaction.
    2.  Users can initiate DGM tasks.
    3.  DGM status, logs, and key outcomes are displayed in Bao-Cline.
    4.  Communication between Bao-Cline and DGM is functional.

**Task 23 (P1)**
*   **Title:** k2script/DGM: Develop k2script Utilities for DGM Support
*   **Goal:** Create a set of k2script scripts and utilities to aid in the evaluation, testing, and operational tasks of the DGM system.
*   **References:** `k2script/TODO.adoc`, `todo/MASTER_TODO.md`
*   **Acceptance Criteria:**
    1.  Identify DGM operational tasks suitable for scripting.
    2.  Develop and test k2script scripts for these tasks.
    3.  Scripts are documented and easily executable.
    4.  Utilities are integrated into DGM development/operational workflow.

---
### P2-P4 Tasks Overview:

*   **P2: Bao-Cline Enhancements & Nexus Groundwork:**
    *   Bao-Cline UX Enhancements (Settings Modes, Breadcrumb Navigation, UI component updates).
    *   Universal Reflection Tool conceptual groundwork (interface spec, introspection design).
    *   k2script Core Improvements (execution method, argument passing, unit tests, core abstractions).
*   **P3: Nexus Universal Adapters & Advanced Tooling:**
    *   Nexus: Phase 3 - Universal Adapters (IDE adapters for VS Code, IntelliJ, Vim; tool orchestration).
    *   Implement Universal Reflection Tool (CLI, IDE adapters, VCS integration).
    *   k2script Broader Ecosystem Support (brew, scoop, Windows support, Docker/Arch deployments).
*   **P4: Mature IDE & Tooling Ecosystem:**
    *   Ongoing enhancements to Nexus adapters and reflection capabilities.

---
## 4. `APPLICATION_SPECIFIC_FEATURES_AND_LOGIC`

**Focus:** Domain-specific logic and features for applications built *on top of* the core systems. This primarily refers to the RTS Game and TA4K.

---
### P2-P4 Tasks Overview:

*   **P2: RTS Game Core Systems & `trikeshed-ts` Integration:**
    *   Entity System: Full state migration to `trikeshed-ts`. Refactor `Unit.js`, `Building.js`.
    *   Resource System: Implement income/cost using `trikeshed-ts`.
    *   Combat System: Basic combat resolution using `trikeshed-ts`.
*   **P3: RTS Game Feature Completion & Advanced AI:**
    *   Complete integration of Command Hierarchy, Formation Movement, AI Prediction Interface feature branches.
    *   Implement advanced AI behaviors, Computronium Cores model.
    *   Integrate TacticsDSL with visual programming interface.
    *   TA4K: Integrate with mature TrikeShed for trading algorithms.
*   **P4: Rich Application Ecosystem:**
    *   Further expansion of RTS game features and AI complexity.
    *   Development of sophisticated trading strategies in TA4K.

---
## 5. `VISUALIZATION_AND_USER_INTERFACE_COMPONENTS`

**Focus:** Development of user-facing visualization tools like SpaceGraph, and specific UI elements for applications.

---
### P2-P4 Tasks Overview:

*   **P2: SpaceGraph Core Features & Initial RTS Game Integration:**
    *   Implement SpaceGraph foundational features (Zooming, Scene Graph, View Modes, Event Handling, basic Widgets, HUD with REPL).
    *   Display Edge Labels & "Wattage Alpha" visualization for RTS game.
*   **P3: Advanced SpaceGraph Visualizations & Interactivity for RTS Game:**
    *   Explicit Resource Flow Visualization.
    *   "Attention and Story" Heuristics.
    *   Performance tuning for large RTS graphs.
    *   Filtering, search/focus, time-scrubbing capabilities.
*   **P4: Sophisticated and Performant Visualization Layer:**
    *   Ongoing enhancements to SpaceGraph library.
    *   Broader adoption of SpaceGraph in other components or for new visualization needs.

---
## 6. `BUILD_SYSTEM_INFRASTRUCTURE_AND_PERFORMANCE`

**Focus:** Build scripts, CI/CD, performance profiling and optimization across components, and overall development infrastructure.

---
### P0 Tasks: Foundational Compliance & Unblocking

**Task 17 (P0)**
*   **Title:** Bao-Cline Build: Implement One-Step VSIX Build Process
*   **Goal:** Consolidate the VSIX build process for the Bao-Cline VS Code extension into a single, reliable command.
*   **References:** `todo/bao_cline_todos.md`, `todo/MASTER_TODO.md`
*   **Acceptance Criteria:**
    1.  A single command (e.g., `pnpm vsix`) successfully builds the entire Bao-Cline extension.
    2.  Build process is reliable across different environments.
    3.  Turborepo configurations are optimized.
    4.  Generated VSIX file is installable and functional.

**Task 18 (P0)**
*   **Title:** Bao-Cline Build: Remove Build Exclusions & Fix Underlying Issues
*   **Goal:** Eliminate existing build exclusions in Bao-Cline by fixing the underlying code or dependency issues.
*   **References:** `todo/bao_cline_todos.md`, `todo/MASTER_TODO.md`
*   **Acceptance Criteria:**
    1.  Build configuration files are modified to remove specified exclusions (except `external: ["vscode"]`).
    2.  Underlying errors (Vite, dependency conflicts) are resolved.
    3.  VSIX builds successfully without these exclusions.
    4.  Extension functionality remains intact.

**Task 19 (P0)**
*   **Title:** TrikeShed Build: Resolve Kotlin/JS Build for `trikeshed-core`
*   **Goal:** Ensure the Gradle build for `trikeshed-core` can successfully generate JavaScript artifacts if this path is pursued.
*   **References:** `TODO_Roadmap.md` (Section IV: Build & Infrastructure)
*   **Acceptance Criteria:**
    1.  Gradle wrapper execution issues are resolved.
    2.  Build process successfully generates JS artifacts from `trikeshed-core`.
    3.  Generated artifacts are evaluated for usability.
    4.  Decision made on whether these artifacts supplement/replace `trikeshed-ts`.

---
### P2-P4 Tasks Overview:

*   **P2: Performance Optimization & Testing Setup:**
    *   General performance profiling for newly integrated systems.
    *   Automated testing setup for `trikeshed-ts` and RTS game integration tests.
*   **P3: Mature Build & Performance Monitoring:**
    *   Advanced performance optimization across all critical components.
    *   Robust CI/CD pipelines for all major projects.
*   **P4: Highly Optimized and Resilient Infrastructure:**
    *   State-of-the-art build systems.
    *   Comprehensive performance regression testing.

---
## 7. `QUALITY_ASSURANCE_DOCUMENTATION_AND_CODE_HEALTH`

**Focus:** Writing tests, creating documentation, addressing general code quality issues (TODOs/FIXMEs), and ensuring overall project maintainability.

---
### P2-P4 Tasks Overview:

*   **P2: Foundational QA and Documentation:**
    *   Address high-impact TODO/FIXME comments across codebase.
    *   Enable TypeScript Strict Mode for Bao-Cline.
    *   Update component documentation after initial P0/P1 integrations.
    *   Create comprehensive testing strategy for integrated system.
*   **P3: Comprehensive QA, Documentation, and Code Refinement:**
    *   Systematic resolution of remaining TODOs/FIXMEs.
    *   Full API documentation for all major components.
    *   User guides for complex features.
    *   Extensive integration and E2E test suites.
*   **P4: Gold Standard Quality and Maintainability:**
    *   Living documentation, continuously updated.
    *   Proactive code health monitoring and refactoring.
    *   Community contributions to documentation and testing.

### T4 Tasks: Long-term Vision

- [ ] **Task 20 (T4)**
  *   **Title:** TrikeShed Vision: Implement Advanced Tensor Operations
  *   **Goal:** Develop and integrate advanced tensor operations within TrikeShed, leveraging the `Tensor<T>` type for high-performance data processing.
  *   **References:** `todo/trikeshed_todos.md`, `CLAUDE.md`
  *   **Acceptance Criteria:**
     1.  Advanced tensor operations (e.g., convolution, pooling) are implemented.
     2.  Operations are optimized for performance.
     3.  Comprehensive unit tests are written.
     4.  Documentation is updated.
  *   **Status:** In Progress (Started: 2023-10-01)
---