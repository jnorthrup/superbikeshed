# Revised Requirements and Gap Analysis Document

## 1. Overview & Current Architecture

### Summary of the project's aim.
The project aims to create a real-time strategy (RTS) game with a unique focus on computational warfare, resource management, and decentralized networking. The game, tentatively titled "RTSGame", involves players managing resources, building units, and engaging in combat, with a core mechanic revolving around "Computronium" as a primary resource and "Proof-of-Work" (PoW) as a key gameplay element.

### Detailed description of the Current Technical Architecture.
The current technical architecture consists of:
*   **Kotlin Multiplatform Core:** The game logic is primarily being developed in Kotlin, leveraging its multiplatform capabilities to target different platforms. This core is intended to house the simulation, game rules, and state management.
*   **WASM (WebAssembly):** The Kotlin core is compiled to WebAssembly (WASM) for web-based deployment. This allows the game logic to run efficiently in modern web browsers.
*   **JavaScript in `interactive-demo.html`:** An interactive demo (`interactive-demo.html`) utilizes JavaScript to interface with the WASM module. This serves as the current frontend for testing and showcasing basic game functionalities. It handles user input, rendering, and communication with the Kotlin/WASM backend.
*   **Build Process:** The build process involves compiling Kotlin code to WASM, likely using Gradle as the build tool for Kotlin projects. The specifics of the JavaScript build process (if any beyond simple scripting) are not detailed but are assumed to be minimal for the current demo.
*   **Legacy Code:** There are mentions of legacy code, particularly in the JavaScript frontend (`interactive-demo.html`), which may need refactoring or replacement as the project matures. Some Kotlin data structures also show signs of iterative development and potential inconsistencies that might be considered a form of internal legacy.

## 2. Key Discrepancies & Inconsistencies Found

### Documentation Mismatches.
*   **`rtsgame/README.md` vs. Actual Kotlin Structure:** The primary README file for the `rtsgame` module outlines a project structure and component interaction model that does not accurately reflect the current Kotlin codebase. For example, the README might describe modules or class interactions that have since been refactored, renamed, or are yet to be implemented. The actual Kotlin code appears to be more nascent than the README suggests, with some core systems still under initial development.

### Internal Code Inconsistencies (Kotlin).
*   **GameState Definitions:** There are multiple, potentially conflicting or redundant, definitions of `GameState` or similar concepts within the Kotlin codebase. This suggests a lack of a single source of truth for game state representation, which can lead to bugs and maintenance issues.
*   **ResourceType Enums:** The `ResourceType` enum, crucial for resource management, shows inconsistencies. Different versions or definitions might exist, or its usage across different modules (e.g., unit cost, gathering, map resources) might not be standardized.
*   **Unit Pathfinding Fields:** Fields related to unit pathfinding within the `Unit` data structures are either ill-defined, inconsistently used, or missing necessary components for a robust pathfinding system. This indicates that pathfinding logic is likely placeholder or very early in development.
*   **Unit Gathering Properties:** Properties and methods related to unit resource gathering (e.g., `canGather`, `gatherRate`, `carryingCapacity`) are not consistently defined or implemented across all relevant unit types. Some units might have these properties while others lack them, or the interpretation of these properties might vary.

## 3. Gap Analysis & Revised Requirements (Feature by Feature)

This section breaks down the Game Design Document (GDD) and compares its vision against the current state of implementation.

---

### 3.1 Resource System

*   **GDD Vision:** A multi-layered resource system with primary resources (e.g., "Computronium", "Data-Credits") gathered from map locations and potentially secondary/processed resources. Computronium is central, linked to PoW.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Partially Implemented/Different. `ResourceType` enums exist but are inconsistent. Basic resource tracking in `GameState` might be present but is not robust. Computronium concept is present but its link to PoW is not yet implemented.
    *   JS: Partially Implemented. The `interactive-demo.html` likely displays some resource counts but lacks complex gathering or processing logic.
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** The core concept of Computronium and Data-Credits.
    *   **Revise/Clarify:** Standardize `ResourceType` enums and their properties across the entire codebase. Define clear gathering mechanics, rates, and storage.
    *   **New/Emergent:** Implement the PoW mechanism for Computronium generation/acquisition. Detail the mechanics for any secondary/processed resources if they are to be kept.

---

### 3.2 Unit Design

*   **GDD Vision:** Diverse unit types with unique abilities, costs, and roles (e.g., scouts, workers, combat units, specialized PoW units). Units have attributes like health, speed, attack, defense, and potentially unique skills.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Partially Implemented/Different. Basic `Unit` data structures exist, but attributes are inconsistently defined. Pathfinding and gathering properties are rudimentary. No clear implementation of unique abilities or diverse roles.
    *   JS: Partially Implemented. `interactive-demo.html` can likely display units and select them, but complex unit behaviors or diverse types are not evident.
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** The need for diverse unit types and core attributes (health, speed, etc.).
    *   **Revise/Clarify:** Standardize unit attribute names and types. Clearly define the properties for pathfinding, gathering, and combat for each unit.
    *   **Defer/Re-evaluate:** Complex unique abilities for many units might be deferred until core mechanics are solid.
    *   **New/Emergent:** Design and implement a flexible system for adding new unit types and their specific behaviors.

---

### 3.3 Combat Systems

*   **GDD Vision:** Real-time combat with units engaging based on stats, abilities, and potentially terrain/environmental factors. Damage calculation, unit destruction, and possibly experience/veterancy systems.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Not Implemented (Gap). Core combat logic (targeting, damage dealing, health reduction) appears to be missing or extremely placeholder.
    *   JS: Not Implemented (Gap). No visual representation or interaction for combat in the demo.
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** The core concept of real-time combat based on unit stats.
    *   **Revise/Clarify:** Define the basic combat formulas (attack vs. defense, damage calculation).
    *   **New/Emergent:** Implement foundational combat mechanics: unit targeting, attack execution, damage application, and unit death. Defer complex features like veterancy or detailed environmental effects.

---

### 3.4 Command & Control (C&C)

*   **GDD Vision:** Players issue commands (move, attack, gather, build) to units and structures. A hierarchical command system might be envisioned for more complex strategies.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Partially Implemented/Different. Basic command structures might exist (e.g., for movement in `interactive-demo.html`), but a comprehensive C&C system is not apparent. Findings from `implementation-guide.md` and `command-hierarchy-analysis.md` indicate a desire for a more structured command system, but this is not yet reflected in the core Kotlin logic.
    *   JS: Partially Implemented. `interactive-demo.html` allows basic unit selection and likely movement commands.
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** Standard RTS commands (move, attack, gather, build).
    *   **Revise/Clarify:** Integrate insights from `implementation-guide.md` and `command-hierarchy-analysis.md`. Design a clear command queue and processing system in Kotlin. Define how commands are issued, validated, and executed by units.
    *   **New/Emergent:** Implement a robust event system for command handling and feedback. Consider the implications of the "Decentralized Meta-Network" on command synchronization if applicable at this stage.
    *   **Deep Dive:**
        *   The `implementation-guide.md` likely outlines specific API endpoints or function calls expected for issuing commands. These need to be mapped to Kotlin functions.
        *   The `command-hierarchy-analysis.md` might propose a layered approach to commands (e.g., individual unit commands, squad commands, global directives). The Kotlin architecture needs to be designed to support the chosen level of hierarchy.
        *   Initial focus should be on reliable execution of basic commands for individual units.

---

### 3.5 Proof-of-Work & Computational Warfare

*   **GDD Vision:** Computronium Cores (specialized units or structures) perform PoW tasks. This PoW mechanic is integral to the economy, research, or special abilities. "Computational Warfare" implies using these mechanics offensively or defensively.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Not Implemented (Gap). The concept of Computronium Cores and the PoW mechanism itself is not implemented. The link between PoW and game resources/abilities is undefined in code.
    *   JS: Not Implemented (Gap).
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** The core idea of Computronium Cores and PoW as a central game mechanic.
    *   **Revise/Clarify:** Define precisely what the PoW tasks entail (even if simulated abstractly). How is PoW initiated, processed, and what are its outputs (Computronium, research points, etc.)?
    *   **New/Emergent:** Design and implement the `ComputroniumCore` unit/structure type. Implement the PoW simulation logic. Integrate PoW outputs into the resource system and potentially other game systems (e.g., tech tree).
    *   **Deep Dive:**
        *   What does "performing PoW" mean in terms of game logic? Is it a timed process, resource conversion, or something else?
        *   How does the player initiate and manage these PoW tasks?
        *   "Computational Warfare": Are there mechanics to disrupt enemy PoW, or use PoW for direct offensive/defensive capabilities? These are likely future features but the foundation for PoW itself is the priority.

---

### 3.6 Data Architecture & Determinism

*   **GDD Vision:** A deterministic game simulation, crucial for replays, spectating, and potentially for the decentralized network aspects. Clear separation of game state and presentation.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Partially Implemented/Different. The focus on Kotlin Multiplatform for core logic is a good step towards determinism. However, inconsistencies in `GameState` definitions and lack of rigorous command processing could undermine determinism.
    *   JS: Partially Implemented. The current JS demo likely directly manipulates or mirrors game state, but its adherence to strict determinism with the Kotlin core needs verification.
*   **Revised Requirements & Recommendations:**
    *   **Keep/Verify:** The goal of a deterministic simulation.
    *   **Revise/Clarify:** Enforce a single, authoritative `GameState` structure. Ensure all game logic modifying the state is deterministic (e.g., no reliance on `Math.random()` without synced seeds).
    *   **New/Emergent:** Implement a robust command and event processing system that guarantees deterministic outcomes given the same sequence of inputs. Plan for serialization/deserialization of game state for replays/networking.

---

### 3.7 Decentralized Meta-Network

*   **GDD Vision:** Players connect in a peer-to-peer or decentralized manner, sharing game state and commands without relying on a central server. This is a highly ambitious feature.
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Not Implemented (Gap). No networking code or considerations for decentralized state synchronization are apparent.
    *   JS: Not Implemented (Gap).
*   **Revised Requirements & Recommendations:**
    *   **Defer/Re-evaluate:** This is a complex feature that should be deferred until core gameplay and single-player/local multiplayer simulation are stable and deterministic.
    *   **Revise/Clarify:** If pursued later, extensive research into P2P networking libraries and consensus algorithms will be needed. The impact on game design (e.g., handling latency, desyncs) must be carefully considered.

---

### 3.8 Technology Tree & Research

*   **GDD Vision:** Players unlock new units, abilities, and upgrades through a technology tree, likely by spending resources (including Computronium).
*   **Current Implementation Status (Kotlin & JS):**
    *   Kotlin: Not Implemented (Gap). No data structures or logic for a tech tree or research system.
    *   JS: Not Implemented (Gap).
*   **Revised Requirements & Recommendations:**
    *   **Defer/Re-evaluate:** Implement after core game loop (resource, build, command, combat) is functional.
    *   **Revise/Clarify:** Design the basic structure of the tech tree and how research items are unlocked and applied.

## 4. Path Forward & General Recommendations

### Address Inconsistencies First.
*   **Priority 1:** Resolve the internal code inconsistencies in Kotlin.
    *   Standardize `GameState` definitions.
    *   Unify `ResourceType` enums and their usage.
    *   Clearly define and implement core unit properties for pathfinding, gathering, and combat.
*   This foundational cleanup is essential before adding significant new features to prevent compounding technical debt.

### Prioritize Core Loop & Key Differentiators.
*   **Priority 2:** Focus on implementing the core gameplay loop:
    1.  **Resource Gathering:** Implement clear and functional resource gathering mechanics for basic resources.
    2.  **Unit Production:** Allow players to build a small set of initial units.
    3.  **Command & Control:** Implement basic unit commands (move, attack-move, gather) reliably. Refer to the C&C deep-dive.
    4.  **Combat:** Implement rudimentary combat mechanics.
*   **Priority 3:** Begin implementation of the **Proof-of-Work** system and `ComputroniumCore` units, as this is a key differentiator. Integrate PoW with the resource system.

### Update Documentation.
*   **Ongoing:** As inconsistencies are resolved and features are implemented, update `rtsgame/README.md` and other relevant design documents to reflect the actual state of the project. Outdated documentation is a hindrance.
*   Consider using inline code documentation (KDoc) more extensively.

### Frontend Development Strategy.
*   **Re-evaluate `interactive-demo.html`:** While useful for initial testing, the current JavaScript frontend may not be suitable for long-term development.
*   **Consider Frameworks/Libraries:** For a more complex UI and better state management, evaluate JavaScript frameworks (e.g., React, Vue, Svelte) or rendering libraries (e.g., PixiJS, Three.js if 3D is envisioned).
*   **Clear API Contract:** Define a clear API contract between the Kotlin/WASM module and the JavaScript frontend to ensure separation of concerns and easier integration.

### Handling of Retired Code.
*   As systems are refactored or replaced, establish a clear process for retiring old code. This could involve:
    *   Moving to a dedicated `legacy` or `deprecated` package/folder.
    *   Clearly commenting code as deprecated with reasons and replacement alternatives.
    *   Eventually removing unused code after a grace period and thorough testing.
*   This is particularly relevant for any parts of `interactive-demo.html` that are superseded by new frontend approaches or Kotlin/WASM functionalities.
