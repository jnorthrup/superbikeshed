> **Note: This document may be outdated and refer to a previous version of the rtsgame project (likely TypeScript/JavaScript-based). The current project has been significantly refactored to Kotlin Multiplatform with WebGPU. Please cross-reference with the main `rtsgame/README.md` for the latest project information.**

# System Implementation Map

This document maps key concepts from `the-rts-concepts.md` to their current implementation status in the codebase, identifies gaps, and outlines areas for further development.

## I. Core Gameplay & Physics Systems

### 1. Resource System (Section 4, `the-rts-concepts.md`)
    *   **Concept:** Raw Landscape Matter, Generic Mass, Specific Minerals, Energy, Computronium, Specialized Alloys, Battery Charge, Computational Cycles, Information, Population. Landscape Consumption & Mineralogy. Remnant Civilizations.
    *   **Implementation Status:**
        *   `gameConstants.js`: Defines `INITIAL_MASS`, `INITIAL_ENERGY`, `RESOURCE_TYPES` (MASS, ENERGY). `COMPUTRONIUM_CONFIG` exists.
        *   `resourceSystem.js`: Manages `MASS`, `ENERGY`, `COMPUTRONIUM`, `ALLOY`, `BATTERY`, `COMPUTATIONAL_CYCLES`, `INFORMATION`, `POPULATION`. Handles generation, consumption, efficiency, network connections, and global faction resources.
        *   `unitTypes.js`: Units have costs in `mass`, `energy`, and some Tier 3 units cost `computronium`.
    *   **Files:** `rtsgame/js/config/gameConstants.js`, `rtsgame/js/core/systems/resourceSystem.js`, `rtsgame/js/config/unitTypes.js`
    *   **Assessment:** Core resources (Mass, Energy, Computronium) are implemented. Alloy, Battery, Cycles, Info, Population are defined in `resourceSystem.js` but their gameplay mechanics (acquisition, use) might be basic or placeholders. Landscape consumption and detailed mineralogy are likely conceptual. Remnant civilizations are conceptual.
    *   **Gaps/Future Work:**
        *   Full implementation of Alloy manufacturing and varied mineral acquisition.
        *   Detailed mechanics for Battery Charge, Computational Cycles, Information, and Population resources.
        *   Implementation of landscape consumption affecting terrain mesh.
        *   Mechanics for Remnant Civilizations.

### 2. Unit Design & Capabilities (Section 6)
    *   **Concept:** Unit Archetypes, Stats, Alloys & Material Science, Computronium Cores & "Dining Philosophers".
    *   **Implementation Status:**
        *   `unitTypes.js`: Defines various units with stats (HP, speed, damage, range, cost). Some units have `hasComputroniumCore`, `coreEfficiency`.
        *   `unit.js`: `Unit` class calculates speed with a basic weight penalty. Implements Computronium core focus modes and updates modifiers. `UnitProgression` system exists.
        *   `computroniumSystem.js`: Implements "Dining Philosophers" with core focus modes, function priorities, and performance degradation.
    *   **Files:** `rtsgame/js/config/unitTypes.js`, `rtsgame/js/core/unit.js`, `rtsgame/js/core/systems/computroniumSystem.js`
    *   **Assessment:** Basic unit stats are implemented. Computronium Cores and "Dining Philosophers" are well-developed. Alloy concept (affecting stats) is missing from code.
    *   **Gaps/Future Work:**
        *   Implement Alloy system affecting unit stats.
        *   Flesh out specific abilities listed in `unitTypes.js` (e.g., `quantum_entanglement`, `phase_shift`).

### 3. Combat Systems (Section 7)
    *   **Concept:** Damage Types & Effects, Weapon Systems & Capacitance, Shield Mechanics, Armor Mechanics.
    *   **Implementation Status:**
        *   `combatSystem.js`: Basic projectile firing and direct damage application. No explicit damage types or armor interaction.
        *   `weaponSystem.js`: Manages weapon capacitors, cooldowns. Projectiles have a `damageType` field, passed to shield system.
        *   `shieldSystem.js`: Implements energy shields with `damageTypeInteractions` (shield factor, bleed-through), recharge, and tunable `harmonics`.
        *   `unit.js`: `takeDamage` method considers energy shields and their effectiveness based on attacker's weapon energy cost.
    *   **Files:** `rtsgame/js/core/systems/combatSystem.js`, `rtsgame/js/core/systems/weaponSystem.js`, `rtsgame/js/core/systems/shieldSystem.js`, `rtsgame/js/core/unit.js`
    *   **Assessment:** Shield system is advanced. Basic weapon capacitance exists. Core combat system (damage application, armor, specific damage type effects beyond shields) is simplistic.
    *   **Gaps/Future Work:**
        *   Implement armor types and values for units.
        *   Implement damage calculation formulas considering armor and specific damage type vs. armor type interactions.
        *   Implement secondary effects for damage types (e.g., EMP disabling electronics, Corrosive armor degradation).

### 4. Command & Control (C&C) (Section 8)
    *   **Concept:** Hierarchical Command & Rank Bonuses, Latency, Prediction, "Light Seconds", Squad AI & Contextual Behavior.
    *   **Implementation Status:**
        *   `unit.js`: Units have `militaryRank`, `commandAuthority`. `executeCommandHierarchy` is mentioned. Computronium config has `C2_LATENCY_BASE`.
        *   `ai/commandHierarchy.js`: Likely handles the structure.
        *   `core/command/authoritySystem.js`: Manages authority calculations.
        *   `command/FormationCommand.kt`: Implements formation-specific commands:
            - SetFormation: Creates new formations with specified units and types
            - ChangeFormation: Switches between formation types
            - SetLeader: Designates formation leaders
            - DisbandFormation: Breaks up formations
            - RotateFormation: Rotates entire formations
            - ChangeFormationFacing: Rotates formations to face targets
            - MoveFormation: Moves formations while maintaining structure
        *   `systems/FormationSystem.kt`: Manages formation behavior and movement
    *   **Files:** `rtsgame/js/core/unit.js`, `rtsgame/js/ai/commandHierarchy.js`, `rtsgame/js/core/command/authoritySystem.js`, `rtsgame/js/config/gameConstants.js`, `rtsgame-kmp/shared/src/commonMain/kotlin/com/rtsgame/shared/command/FormationCommand.kt`, `rtsgame-kmp/shared/src/commonMain/kotlin/com/rtsgame/shared/systems/FormationSystem.kt`
    *   **Assessment:** Basic C&C concepts (rank, authority) exist. Formation system is well-developed with multiple command types and behaviors. Latency is defined in constants. Detailed squad AI and contextual behavior are still in development.
    *   **Gaps/Future Work:**
        *   Full implementation of C&C latency effects on command execution.
        *   Development of predictive AI for C&C.
        *   Advanced squad AI and contextual behaviors.
        *   Implementation of rank bonuses.
        *   Formation-specific combat maneuvers.
        *   Smooth transitions between formation types.
        *   Formation-specific movement speeds and behaviors.

### 5. Proof-of-Work (PoW) & Computational Warfare (Section 9)
    *   **Concept:** Defensive PoW, Offensive PoW (Breach Tools, CSA), PoW Generation & Consumption.
    *   **Implementation Status:**
        *   `proofOfWorkSystem.js`: Detailed implementation of defensive/offensive PoW, network hash rate, breach attempts (CMD_INJECTION, DATA_EXFILTRATION etc.) with effects, severity, duration. Interacts with Computronium and C&C.
        *   `unitTypes.js`: Some Tier 3 units have PoW-related abilities (`pow_attack`).
    *   **Files:** `rtsgame/js/core/systems/proofOfWorkSystem.js`, `rtsgame/js/config/unitTypes.js`
    *   **Assessment:** This system is surprisingly well-developed and aligns closely with the design document.
    *   **Gaps/Future Work:**
        *   Further balancing and integration with other systems (e.g., UI feedback for PoW attacks).
        *   Implementation of CSA (Chronological Sync Attack) if not already covered by generic breach types.

### 6. Data Architecture & Determinism ("TrikeShed") (Section 10)
    *   **Concept:** Deterministic Simulation, Replay System, Idempotent Data.
    *   **Implementation Status:**
        *   `CLAUDE.md` mentions TrikeShed, deterministic RNG (`deterministicRNG.js`), immutable updates.
        *   `core/battleReplay.js`, `core/replay/`: Replay system components exist.
        *   `simulationConfig.js`: `GAME_SEED` for reproducibility.
    *   **Files:** `rtsgame/js/core/deterministicRNG.js`, `rtsgame/js/core/battleReplay.js`, `rtsgame/docs/CLAUDE.md`, `rtsgame/js/config/simulationConfig.js`
    *   **Assessment:** Foundations for determinism and replays are present. TrikeShed's full scope (tensor-based data, etc.) is a deeper architectural aspect.
    *   **Gaps/Future Work:**
        *   Continuous verification of determinism across all new physics and game logic changes.

## II. Meta-Network & "Dogfooding" (Section 11)

*   **Concept:** Kademlia/IPFS-inspired decentralized network for player-to-player sharing of replays, mods, maps, strategy guides. PoW for meta-network integrity and offense.
*   **Implementation Status:**
    *   This is a meta-game concept, primarily external to the core simulation loop.
    *   The existing replay system (`battleReplay.js`) would generate data to be shared on this network.
    *   `proofOfWorkSystem.js` could theoretically be adapted or its principles used for meta-network PoW, but it's currently focused on in-game computational warfare.
*   **Files:** (Primarily conceptual, but related to output of `rtsgame/js/core/battleReplay.js`)
*   **Assessment:** This concept is almost entirely conceptual. No direct implementation visible in the core game's JS files.
*   **Architectural Implications & Gaps/Future Work:**
    *   **Replay System:** Must produce self-contained, shareable replay files (initial state + command log).
    *   **Game Client:** Would need capabilities to act as a node in a P2P network (connect, discover peers, publish, fetch data). This is a significant software component outside the game simulation.
    *   **PoW for Meta-Network:** A separate PoW mechanism would be needed, distinct from the in-game one, for publishing content and potentially for data validation or anti-spam on the meta-network.
    *   **Content Management:** Systems for browsing, searching, and managing shared content (maps, mods, replays).
    *   **Modding Support:** If mods are to be shared, the game needs a robust modding framework.
    *   This is a major feature set requiring dedicated development, likely involving different technologies than the core game simulation (e.g., P2P libraries, potentially server infrastructure for bootstrapping).

## III. Technology Tree & Research (Section 12)

*   **Concept:** Research categories (Material Science, Armaments, Computronium, C&C, etc.) unlocking new units, abilities, alloys.
*   **Implementation Status:** Not directly visible in the provided core systems files. Unit tiers in `unitTypes.js` suggest a progression, but a formal research system is not apparent.
*   **Files:** (Likely missing or conceptual)
*   **Assessment:** Conceptual.
*   **Gaps/Future Work:** Design and implement a research system, UI for tech tree, and link tech unlocks to game elements.

---
