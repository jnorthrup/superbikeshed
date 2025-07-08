# Feature Map: The Evolution of rtsgame

This document outlines the development history of the `rtsgame` project, structured as an "upside-down pyramid." It starts with the current, most complex implementation and traces its lineage back to the initial concepts.

## Stage 3: Stratified Kotlin Multiplatform (KMP) Game

The current incarnation of `rtsgame` is a sophisticated, multi-platform project leveraging Kotlin. This phase focuses on advanced rendering, core system integration, and modernizing the build process.

- **Interactive WebGPU Demo**: A significant leap in rendering technology, providing a modern, high-performance visual client.
  - **Commit**: `1755d2e5` - Add Interactive RTS WebGPU Demo
- **KMP Build & Configuration**: The transition to a full Kotlin Multiplatform structure, enabling code sharing across different targets.
  - **Commit**: `6af3c6c6` - Update Kotlin Multiplatform plugin version and enhance project configuration
  - **Commit**: `eda569e1` - Fix RTS game build configuration and documentation
- **Conceptual Trikeshed Core Integration**: The first step towards deep integration with the core Kotlin-based `trikeshed` library.
  - **Commit**: `ec8171e9` - Integrate rtsgame with trikeshed-core via Kotlin/JS (Conceptual)

## Stage 2: TypeScript Game with `trikeshed-ts`

The project evolved into a more robust implementation using TypeScript, centered around a dedicated TypeScript port of the Trikeshed library. This allowed for more complex game systems and features.

- **Introduce `trikeshed-ts`**: The pivotal commit that marks the migration to TypeScript.
  - **Commit**: `1ec7d9dc` - Introduce trikeshed-ts (TypeScript port) and integrate with rtsgame
- **Core Gameplay Systems (TypeScript)**:
  - **Resource System**: Built on the new TypeScript foundation.
    - **Commit**: `e334f3cc` - Implement basic resource system in rtsgame using trikeshed-ts
  - **AI Prediction Interface**: The initial proof-of-concept for the AI system.
    - **Commit**: `0894f983` - Implement initial proof-of-concept for AI Prediction Interface
  - **Command & Movement Systems**: Enhancements to unit control and behavior.
    - **Commit**: `73918143` - Implement enhanced formation movement system
    - **Commit**: `b6b967f2` - Implement enhanced command hierarchy and veterancy systems

## Stage 1: JavaScript Game & Conceptual Foundation

The origins of `rtsgame` lie in a JavaScript-based approach, primarily focused on visualization and foundational game mechanics.

- **Initial Visualization with Spacegraph.js**: The first visual element of the game.
  - **Commit**: `bf6c28b2` - Initial integration of Spacegraph.js into rtsgame for visualization
- **Project Inception**: The very first commit that set up the multi-project structure.
  - **Commit**: `02b8c00a` - Initial superbikeshed repository with multi-project Gradle setup