 
## RUNTIME MEMORY

- for loops in kotlin are the gold standard of performance intent and foreach is something else
- when running gradle "--console=plain --no-daemon "
- ordinary usecases involve doing conditional native repo determiniation in gradle and not all targets
- our gradle should always defer to superbikeshed/ gradle for versions info and not alter them.  our targets are common,conditionally-local-native,wasm,jvm 
- most of the time you just copy trikeshed gradle for a new project

## Migration Memories

- **Shunned Classes Memory**: 
  - Defer use of `List<T>`
  - Defer use of `Pair<A,B>`
  - Prefer `Series<T>`, `primitive array`, `Join<A,B>` instead

## Memory: CCEK Meaning

- CCEK stands for CoroutineContextElement.Key

## Memory: Code Cleaning Liberties

- if it wasn't mentioned before we do not tolerate "cleaning" liberties at all.  we need all our code and we paid you for all our code and do not give rights of disposal.  you may move code to a musem area and we will find a model that can do your job for you later and delete you when we have time.  that is all

## Memory: Typealias Taxonomical Ontology Design

- Before adding new design to the code, design the Typealias taxonomical ontology according to the specification language
- Leverage `Join`, and `MetaSeries` derived types like `Series` and `LongSeries`

## Memory: Project Setup

- before creating new code open trikeshed CoreTypes.kt first so its in the context

## Memory: Museum Preservation

- museums outside of compilation are the last ditch when you cannot fix something and OUR RULES PREVENT DELETING CODE AND RANDOM "CLEANING"
  
### Making Awesome More Awesome

These components are already excellent examples of design and functionality. My proposal is to elevate them further.

#### 1. The `boingDemo` Multiplatform Architecture
This is a textbook example of a clean, super-awesome Kotlin Multiplatform (KMP) project. The `expect`/`actual` pattern for `EconoCanvas` and `playSound` perfectly isolates platform-specific code, keeping the common `BoingDemo.kt` logic pure and reusable.

*   **To Make It More Awesome:** The native audio implementation is currently a stub. The next logical step is to bring it to feature parity with the other platforms.
    *   **Proposal:** Implement the `actual fun playSound` for `nativeMain` using a C-interop library like **miniaudio** or **OpenAL**. This would complete the multiplatform experience, allowing the satisfying "boing" to be heard on native desktop executables, making this awesome demo even more universally impressive.

#### 2. The `k2script` Command-Line Interface
The `k2script` entry point (`Kscript.kt`), `ConfigBuilder`, and `Executor` form a robust, professional-grade application core. The argument parsing, configuration loading (from files and environment variables), and command execution logic are solid and well-structured.

*   **To Make It More Awesome:** The recent addition of `LiteLLMClient.kt` is a game-changer. This powerful, asynchronous Large Language Model client is an "awesome" component just waiting to be integrated.
    *   **Proposal:** Add a new AI-powered feature to `k2script`. Introduce a new command-line flag, `--ai <prompt>`, that uses the `LiteLLMClient` to perform tasks like:
        1.  **Script Generation:** `kscript --ai "create a script to find all jpg files in a directory and resize them"`
        2.  **Code Explanation:** `kscript --ai "explain this script" < script.kts`
        
        This leverages the awesome CLI foundation to host an even more awesome, next-generation capability.

### From Bad to Rad: Switching to a Better Thing

These components exhibit patterns or implementations that are either incomplete, brittle, or architecturally unsound. They should be replaced by the superior solutions you've already created elsewhere.

#### 1. The `flatton` JSON Scanner
The `SimdJsonScanner.kt` inside `flatton` is a great *idea* but is explicitly a "simplified demonstration" with "crude checks." It avoids full deserialization, which is clever, but the implementation is not robust.

*   **The Better Thing You Offer:** The `kotlinx-serialization-scanner` module. This is a far superior, high-performance solution that uses bitmap scanning for lightning-fast JSON navigation and integrates properly with the standard `kotlinx.serialization` library.
    *   **Proposal:** **Deprecate and replace `flatton`'s `SimdJsonScanner` and `JsonWireProtoAdapter` entirely.** Refactor `FlattonService`'s `queryViewAsCursor` to use the `BitmapJsonDecoder` from the serialization scanner. This provides the same "cursor-like" benefit without full deserialization but with a more robust, reusable, and performant engine.

#### 2. The `k2script` Annotation Parser
The current `LineParser.kt` uses a series of regular expressions to find and parse script annotations like `@file:DependsOn`. This approach is fragile, hard to extend, and cannot understand context, leading to potential parsing errors.

*   **The Better Thing You Offer:** The `kotlin-entity-scanner` project. This is a vastly more sophisticated "awesome" tool that uses **Inductive Graph Parsing** and **Chained Rules** (`ChainedParserRules.kt`) to analyze code with grammatical and contextual awareness.
    *   **Proposal:** **Replace the regex-based parsing in `k2script.parser.LineParser` with the `KotlinEntityScanner`.** Instead of manually matching strings, the script content would be fed to the scanner, which would produce a structured series of `ScriptAnnotation` entities. This makes dependency and directive parsing more reliable, extensible, and powerful.

#### 3. The `nexus` Agent Architecture
The `nexus/src/commonMain/BROKEN` directory contains abandoned, overly-abstract agent designs (`NexusTypes_OLD.kt`) and a broken, non-functional implementation (`DefaultNexusAgent.kt`). The attempt at a "purely compositional" agent resulted in a system that is difficult to understand, maintain, or complete. This is "so bad" it requires a foundational shift.

*   **The Better Thing You Offer:** The clear, working architectural patterns from `k2script` and the powerful, standalone `LiteLLMClient`.
    *   **Proposal:** **Delete the entire `nexus/src/commonMain/BROKEN` directory.** Refactor the Nexus agent from the ground up using the same architecture as `k2script`:
        1.  A main entry point that parses arguments.
        2.  A `NexusConfigBuilder` to manage settings.
        3.  An `ActionExecutor` that handles specific tasks.
        4.  For its core logic, directly use the new, working `k2script.ai.llm.LiteLLMClient` as its provider, replacing the broken provider concepts in `nexus`. This creates a smaller, more focused, and *working* agent.

#### 4. Duplication of Core Types
There are multiple, conflicting definitions of core data structures across the codebase (e.g., `borg.trikeshed.lib.Series`, `moneyfan.trikeshed.Series`, `com.google.trike.series.Series`). This is a classic architectural code smell that leads to confusion and bugs.

*   **The Better Thing You Offer:** A single source of truth. The `Trikeshed` module is the most logical home for these foundational types.
    *   **Proposal:** **Establish `Trikeshed/src/commonMain/kotlin/borg/trikeshed/lib/CoreTypes.kt` as the canonical source for `Series`, `Join`, and other foundational types.** Replace all duplicate definitions from other modules with imports from Trikeshed, or use none if not needed. This refactoring drastically improves maintainability and consistency across the entire project.