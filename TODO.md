# TrikeShed "RelaxFactory" Development Roadmap

This document outlines the next steps for realizing the TrikeShed architectural vision.

---

## Section 1: Core Metaclass & Type System

**Goal:** Solidify the compositional foundation and optimize the packing/dispatch system.

-   [ ] **Formalize PackerStrategy Registry:** Refactor `PackingStrategies.kt` to load the `PackerStrategy` objects into a central, possibly configurable, list instead of a hardcoded one.

-   [ ] **Implement `PackedView` Interface:** Define the `PackedView` interface with `getAsLong(index: Int)` and `elementCount` properties. Ensure all `PackedResult` types implement it for true zero-cost consumption by performance-critical consumers.

-   [ ] **Flesh out Packing Heuristics:** Implement the actual logic inside the `canPack` and `pack` methods for each strategy (Diagonal, Prefixed, RangeOffset, etc.).

-   [ ] **Expand `MetaSeries` Realms:** Introduce and use new `typealias` realms where appropriate, such as `TimeIndexed<T> = MetaSeries<Instant, T>` for time-series data or `Spatial<T> = MetaSeries<Coordinate, T>` for geographic data.

-   [ ] **Performance Profile Packing:** Create benchmarks to measure the real-world CPU cycle and memory allocation costs of each packing strategy to validate the `CpuBudget` model in `PackingContext.kt`.

-   [ ] **Refine `BashBracePacker`:** Refactor `BashBracePacker.kt` to use a compositional list of `CompressionStrategy` objects, similar to the `PackerStrategy` refactoring.

---

## Section 2: KSP & DSL Generation

**Goal:** Realize the "ffmpeg-like" ubiquitous configuration DSL.

-   [ ] **Implement `DefaultNexusAgent` DSL:** Use the `TrikeShedDslProcessor` to generate a full, working DSL for configuring every aspect of the `DefaultNexusAgent` (capabilities, workflows, IPFS topics, etc.).

-   [ ] **Activate Other `@Generate` Annotations:** Implement the KSP processors for the other annotations defined in `Annotations.kt`, such as `GenerateSeriesExtensions`, `GenerateEnumUtilities`, and `GenerateDataClassBuilders`.

-   [ ] **Add DSL Validation:** Enhance the KSP processors to add validation logic to the generated DSLs (e.g., a `.port()` method that throws if the number is outside the valid 1-65535 range).

-   [ ] **Generate Markdown Docs from DSL:** Create a KSP processor that analyzes the generated DSL and outputs a markdown file documenting all available configuration options, flags, and parameters, fulfilling the "self-documenting" goal.

---

## Section 3: Server & API Implementation

**Goal:** Build the actual "RelaxFactory" webserver on the strong architectural foundation.

-   [ ] **Implement QUIC Listener:** Create a KMP-compatible QUIC server listener. This will likely require using `expect`/`actual` and platform-specific networking libraries.

-   [ ] **Implement CouchDB API Layer:** Design the API endpoints that mimic the CouchDB document API (`GET /db/docid`, `PUT /db/docid`, etc.).

-   [ ] **Bridge API to IPFS Storage:** Implement the logic that translates API calls into IPFS operations.
    -   `PUT` -> Add document to IPFS, get CID.
    -   `GET` -> Resolve document ID to CID, fetch from IPFS.
    -   Use IPFS PubSub to manage document indices and updates across the network.

-   [ ] **Connect DSL to Server:** Use the generated DSL in the `main` function to configure and launch the server, demonstrating the end-to-end vision.

-   [ ] **Develop a Security Model:** Define how authentication and authorization will work over QUIC/IPFS. This may involve signed messages or decentralized identity solutions.

---

## Section 4: AI & Reasoning Feedback Loop

**Goal:** Complete the meta-feedback loop to make the system introspectable by AI.

-   [ ] **Fully Implement `ReasoningLattice`:** Take the test-driven definitions in `ReasoningLatticeTest.kt` and `SymbolEvidenceTest.kt` and create a robust implementation that can scan the entire project's source code.

-   [ ] **Create a Query API for the Semantic Map:** Expose the generated `ReasoningLattice` through an API (perhaps a GraphQL endpoint or a custom DSL) so that an external tool (or LLM) can query it. Example queries:
    -   "Find all functions with the `COROUTINE_FUNCTION` pattern."
    -   "Show the evidence chain for classifying `UserService` as `inheritance_class`."
    -   "What is the average confidence score for patterns in the `nexus.core` package?"

-   [ ] **Develop LLM Analysis Prompts:** Create a library of prompts for an LLM to perform architectural analysis using the semantic map API.
    -   "Given this semantic map, identify potential violations of the single responsibility principle."
    -   "Suggest a refactoring for the `DefaultNexusAgent` to improve its modularity, using evidence from the lattice."

-   [ ] **Integrate LLM into CI/CD:** Add a CI step that runs the semantic map generator and then uses an LLM with the analysis prompts to automatically comment on pull requests with architectural feedback.