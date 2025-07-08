> **Note: This document may be outdated and refer to a previous version of the rtsgame project (likely TypeScript/JavaScript-based). The current project has been significantly refactored to Kotlin Multiplatform with WebGPU. Please cross-reference with the main `rtsgame/README.md` for the latest project information.**

# Feature Integration & Next Steps TODO

> **Note**: This document is synchronized with the unified todo system at `/todo/rtsgame_todos.md`

This document outlines recently implemented major features, considerations for merging their respective branches, and a TODO list for subsequent work and refinements.

## Recently Implemented Features

1.  **Command Hierarchy Enhancements:**
    *   Dynamic unit authority (health, veterancy, context, Computronium).
    *   Veterancy progression (ranks, stat boosts, ability flags).
    *   Command succession protocol.
    *   Configuration via `commandConfig.js`.
    *   Debug visualizations for command links/authority.
2.  **Formation Movement ("Codec Predictor Walk"):**
    *   Leader-follower system (leader uses A*).
    *   Followers use predictive slot tracking relative to leader.
    *   Steering behaviors for followers: seek/arrive for slot, separation from friendlies, basic single-feeler terrain avoidance.
    *   A* based regrouping for widely separated followers.
    *   Configuration for movement parameters in `commandConfig.js`.
    *   Debug visualizations for ideal slots, predicted leader positions.
3.  **AI Prediction Interface (Proof of Concept):**
    *   `StrategicAI` generates "Next Likely Enemy Ground Attack Vector/Target Area" predictions (simple heuristic).
    *   Predictions visualized on map (path as arrow/chevrons, target area as circle, confidence-based colors, special color for acknowledged).
    *   Player interaction (right-click on visualization) triggers `PlayerInteraction_AckReinforce_AttackVector` event.
    *   `StrategicAI` processes this event, marks prediction as acknowledged, may boost confidence.
    *   `StrategicAI` instance created and updated in main game loop.
    *   Design documented in `docs/ai-prediction-interface.md`.

## Merge Strategy Considerations

*   **Primary Files for Careful Merging:**
    *   `rtsgame/js/core/unit.js`: Major changes from both Command Hierarchy and Formation Movement. Constructor and movement logic (`update`, `updateTacticalBehavior`, `executeGroupMovement`, `followSuperiorOrders`) need careful integration.
    *   `rtsgame/js/config/commandConfig.js`: Additions from all three features. Structure needs to remain coherent.
    *   `rtsgame/js/app.js`: `StrategicAI` instantiation and update call from AI Prediction Interface.
    *   `rtsgame/js/rendering/webgl_2d_renderer.js`: Calls to multiple debug renderers.
*   **Suggested Approach:**
    1.  Create a new integration branch from `main`/`develop`.
    2.  Merge `feature/command-hierarchy-enhancements`. Test.
    3.  Merge `feature/enhanced-formation-movement`. Resolve `unit.js` & `commandConfig.js` conflicts. Test.
    4.  Merge `feature/ai-prediction-interface-poc`. Test.
*   **Key Conflict Points in `unit.js`:**
    *   **Constructor:** Combine all new property initializations.
    *   **Movement Control:** Ensure formation movement logic correctly takes precedence for units in formation, while allowing other command/individual movement when not in formation. Review how `patrolTarget` and `path` are managed by different systems.

## Command Hierarchy Enhancements - TODO

- [ ] **Balance Testing:** Rigorously test impact of veterancy bonuses and authority on gameplay.
- [ ] **AI Utilization:** Implement AI logic to understand and use new command features (e.g., protect high-authority units, form effective structures).
- [-] **Advanced Veterancy Abilities:** Design and implement concrete abilities for `canPromoteSubordinates` and `provideMoraleBonus` flags (e.g., auras, squad commands). [WIP: Implementing aura system for morale bonus]
- [ ] **UI Representation:** Develop UI elements to display unit rank, effective authority, and veterancy effects.
- [ ] **Contextual Authority:** Implement logic for `this.contextAuthorityModifier`.
- [ ] **Computronium Authority:** Implement logic for `this.computroniumAuthorityModifier` based on core level/focus.
- [ ] Review interaction between `followSuperiorOrders` and formation movement (`executeGroupMovement`) after merge to ensure clear command precedence.

## AI Prediction Interface - TODO

- [ ] **Implement Further Player Interactions:**
    - [ ] "Dispute & Monitor" action and `PlayerInteraction_DisputeMonitor_AttackVector` event.
        - [ ] AI reaction: Lower confidence, trigger alternative prediction search.
        - [ ] Visualization update for "disputed" predictions.
    - [ ] "Counter-Predict: New Threat" action (player designates new area) and `PlayerInteraction_NewThreatDesignation` event.
        - [ ] AI reaction: Analyze new player-designated area with high priority.
        - [ ] Visualization for player-designated threats.
- [ ] **Develop More Prediction Types:**
    - [ ] Resource-related: "Imminent resource shortfall," "Most valuable/vulnerable resource node."
    - [ ] Defensive: "Most vulnerable friendly structure/unit."
- [ ] **Refine "Enemy Attack Vector" Prediction:**
    - [ ] Implement enemy unit clustering instead of single closest unit.
    *   [ ] Factor in unit composition, player defenses, path chokepoints.
- [ ] **Refine AI Reactions to Interactions:**
    - [ ] Implement more tangible AI behavior changes beyond confidence/logging (e.g., unit reallocation, production changes).
- [ ] **UI for Interactions:** Design and implement a context menu or other UI for selecting interaction type, instead of default right-click action.
- [ ] **Visualization Polish:**
    - [ ] Smoother animations for appearing/fading predictions.
    - [ ] Clearer icons or visual language for different prediction states/types.
- [ ] **Multiple Simultaneous Predictions:** Allow AI to generate and display several predictions; allow player to interact with any.
- [ ] **"Closing the Loop":** Research how player feedback can improve future AI predictions (adaptive heuristics or basic learning).
- [ ] **Mermaid Diagram: Current Prediction-Interaction Loop**
    ```mermaid
    graph TD
        A[StrategicAI: Generates Prediction] -->|prediction data| B(Visualization: renderAIPredictionsDebug);
        B -->|visuals on map| C{Player Interaction?};
        C -- Yes (Right-Click on Visual) --> D[InputHandler: Detects Hit];
        D -->|PlayerInteraction_AckReinforce_AttackVector event| E[GameState: Event List];
        F[StrategicAI: Update Loop] -->|gets event| E;
        F -->|processes event| G[StrategicAI: handlePlayerPredictionInteraction];
        G -->|updates prediction.playerAcknowledged, .confidence| A;
        G -->|updated state| B;
    ```

## Formation Movement - TODO

- [x] **Dynamic Formation Shapes:**
    *   [x] Design system for defining formation shapes (e.g., line, column, wedge).
    *   [x] Implement logic to assign `unit.formationOffset` based on selected shape and unit's role/ID.
    *   [x] Add player commands to change formation shape.
- [ ] **Rigorous Testing & Tuning:**
    - [ ] Test with diverse unit counts, speeds, terrain.
    - [ ] Tune steering weights (`SEPARATION`, `TERRAIN_AVOIDANCE`).
    *   [ ] Tune `maxForce`, `maxTurnRate`.
    *   [ ] Tune prediction time, arrival radius, slot distance.
    *   [ ] Evaluate "feel" for smoothness, tightness, adaptiveness.
- [ ] **Advanced Obstacle Avoidance:**
    - [ ] Implement multiple "feelers" for `calculateTerrainAvoidanceForce` for better environmental awareness.
    - [ ] Explore strategies for navigating choke points as a formation (e.g., leader slows, formation compresses/elongates).
- [ ] **Leader Behavior Enhancements:**
    - [ ] Leader adjusts speed based on follower cohesion (e.g., if average follower distance > threshold).
    - [ ] (Advanced) Leader's A* path choice considers formation width.
- [ ] **More Sophisticated Follower Prediction:**
    *   [ ] Followers predict further along leader's A* path, not just current velocity projection.
- [ ] **Formation Combat Maneuvers:**
    *   [ ] Implement specialized movement patterns for combat situations
    *   [ ] Add formation-specific combat bonuses
    *   [ ] Design and implement tactical formation changes during combat
- [ ] **Formation Transitions:**
    *   [ ] Implement smooth interpolation between formation types
    *   [ ] Add transition animations and effects
    *   [ ] Handle unit position conflicts during transitions
- [ ] **Formation-specific Behaviors:**
    *   [ ] Different movement speeds based on formation type
    *   [ ] Formation-specific combat bonuses
    *   [ ] Special abilities unlocked by certain formations
- [ ] **Refine Separation Logic:** Current separation in `applyMovement` might conflict with steering-based separation. Evaluate and potentially consolidate.
- [ ] **Mermaid Diagram: Follower Movement Decision Process in `executeGroupMovement`**
    ```mermaid
    graph TD
        Start((Start Follower Logic));
        Start --> LDR{Has Group Leader?};
        LDR -- No --> END((End / Default Movement));
        LDR -- Yes --> SEP{Too Separated from Leader?};
        SEP -- Yes --> PATH_LDR[Pathfind to Leader via A*];
        PATH_LDR --> END;
        SEP -- No --> CALC_SLOT[Calculate Ideal Formation Slot (based on predicted leader pos & offset)];
        CALC_SLOT --> RST_STEER[Reset Steering Vector];
        RST_STEER --> APPLY_SEEK[Apply Seek/Arrive force towards Ideal Slot];
        APPLY_SEEK --> APPLY_SEP[Apply Separation force from nearby friendlies];
        APPLY_SEP --> APPLY_TERRAIN[Apply Terrain Avoidance force];
        APPLY_TERRAIN --> TRUNC_FORCE[Truncate Total Steering Force (maxForce)];
        TRUNC_FORCE --> APPLY_VEL[Apply Steering to Velocity];
        APPLY_VEL --> TRUNC_VEL[Truncate Velocity (maxSpeed)];
        TRUNC_VEL --> UPD_ANGLE[Update Angle Smoothly];
        UPD_ANGLE --> END;
    ```

## General / Cross-Cutting TODOs

- [ ] **Comprehensive Testing:** After all branches are merged, conduct thorough testing of combined functionality.
- [ ] **Performance Profiling:** Identify and optimize any performance bottlenecks introduced by new features.
- [ ] **Documentation Updates:** Update main READMEs or other high-level design documents to reflect the new capabilities.
- [ ] **Code Cleanup:** Refactor and clean up any temporary or debug code.
- [ ] **Address `FIXME` / `TODO` comments** introduced during feature development.
