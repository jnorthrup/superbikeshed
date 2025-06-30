<<<<<<< HEAD
=======
> **Note: This document may be outdated and refer to a previous version of the rtsgame project (likely TypeScript/JavaScript-based). The current project has been significantly refactored to Kotlin Multiplatform with WebGPU. Please cross-reference with the main `rtsgame/README.md` for the latest project information.**

>>>>>>> origin/feat/core-serialization-impl
# ECS (Entity-Component-System) Integration Evaluation

This document summarizes the analysis of the current entity management system and provides a recommendation for integrating ECS principles into the RTS game project.

## 1. Potential Core Components

Based on `Unit.js` and `Building.js`, the following are examples of data components that could be derived:

**Common Components:**

*   **`IdentifierComponent`**: `{ id: string, typeName: string, team: string }`
*   **`PositionComponent`**: `{ x: number, y: number, z?: number, angle?: number }` (angle for units)
*   **`HealthComponent`**: `{ hp: number, maxHp: number }`
*   **`SelectableComponent`**: `{ isSelected: boolean }`
*   **`RenderableComponent`**: `{ modelKey: string, size: number, color?: string }` (color might be team-based or type-based)

**Unit-Specific Components:**

*   **`MovementComponent`**: `{ speed: number, currentSpeed?: number, movementType: string (land/air/sea/amphibious), velocityX: number, velocityY: number, patrolTarget?: {x,y}, path?: Point[], currentWaypointIndex?: number, pathRequestCooldown?: number }`
*   **`CombatComponent`**: `{ targetId?: string, attackRange: number, preferredRange?: number, damage: number, cooldown: number, lastFireTime?: number, aggressiveness?: number }`
*   **`ShieldComponent`**: `{ shields: number, maxShields: number, shieldRegenRate: number }`
*   **`ConstructionComponent` (for ACU/Engineer)**: `{ buildList?: string[], buildRate: number, currentConstructionTask?: { targetX, targetY, type, progress, buildingStarted } }`
*   **`AbilityComponent` (e.g., Grenade)**: `{ abilities: { grenade: { range, cooldownTime, currentCooldown } } }`
*   **`AIStateComponent`**: `{ tacticalRole: string, militaryRank: string, survivalPriority: number, commandAuthority: number, protectionNeeds?: string[], lastThreatAssessment?: number, fleeThreshold?: number, formation?: any, currentCommanderId?: string }` (Could be broken down further)
*   **`StuckDetectionComponent`**: `{ stuckFrames: number, lastPositionForStuckCheck: {x,y}, isEscaping: boolean, escapeAngle: number, escapeDuration: number }`
<<<<<<< HEAD
=======
*   **`FormationComponent`**: `{ 
        formationType: string, 
        formationOffset: { x: number, y: number }, 
        leaderId?: string, 
        leaderTargetPosition?: { x: number, y: number }, 
        leaderPredictedPosition?: { x: number, y: number }, 
        idealFormationSlotWorld?: { x: number, y: number },
        maxForce: number,
        maxTurnRate: number,
        steering: { x: number, y: number }
    }`
>>>>>>> origin/feat/core-serialization-impl

**Building-Specific Components:**

*   **`ProductionComponent` (for Factories)**: `{ produces: string[], productionQueue: string[], productionProgress: number, rallyPoint: {x,y} }`
*   **`ResourceGenerationComponent` (for Extractors)**: `{ resourceType: string, amountPerTick: number, lastTickTime?: number }`

## 2. Potential Systems

Corresponding systems could manage these components:

*   **`MovementSystem`**: Updates `PositionComponent` based on `MovementComponent` properties, pathfinding results, and terrain.
*   **`ShieldRegenSystem`**: Updates `ShieldComponent.shields`.
*   **`CooldownSystem`**: Updates `CombatComponent.cooldown`, `AbilityComponent` cooldowns.
*   **`CombatSystem`**: Handles target acquisition (modifying `CombatComponent.targetId`), attack logic (checking range, cooldown), and damage application (which might dispatch events or create "damage application" commands/components).
*   **`HealthSystem` / `DamageSystem`**: Processes damage, updates `HealthComponent.hp`, handles entity death (removing entity or its components).
*   **`ConstructionSystem`**: Manages `ConstructionComponent` progress, resource deduction, and entity creation when construction completes.
*   **`ProductionSystem`**: Manages `ProductionComponent` queues, progress, resource deduction, and unit creation.
*   **`ResourceSystem`**: Manages `ResourceGenerationComponent` and updates global player resources.
*   **`AISystem` / `StrategicAISystem` / `TacticalAISystem`**: Reads various components (`Position`, `Health`, `Combat`, `AIState`) and generates commands or updates AIState/target components.
*   **`StuckDetectionSystem`**: Updates `StuckDetectionComponent`.
*   **`FormationSystem`**: Manages formation behavior:
    *   Updates `FormationComponent` properties based on formation commands
    *   Calculates formation offsets and ideal positions
    *   Handles formation transitions and rotations
    *   Manages leader-follower relationships
    *   Applies steering behaviors for formation maintenance
*   **`RenderSystem`**: Reads `PositionComponent`, `RenderableComponent`, `HealthComponent` (for health bars), `ShieldComponent` (for shield effects) to draw entities. This is already handled by `webglRenderer.js` which iterates entities and reads their properties.
*   **`SelectionSystem`**: Manages `SelectableComponent` based on user input.

## 3. Comparison of Approaches

*   **Current Class-Based Approach:**
    *   **Pros:** Familiar (OOP), data and logic are encapsulated within entity instances. Easy to understand individual entity behavior.
    *   **Cons:** Can lead to monolithic classes (`Unit.js` is large). Logic is spread out. Difficult to add new behaviors that cut across many entity types without modifying many classes or using complex inheritance/mixins. Performance can suffer with many objects and method calls (though JavaScript engines are highly optimized). State changes are often mutable and direct, harder to track for replays/debugging without strict patterns.

*   **Full ECS Approach (e.g., using a library like `bitecs`, `gecs`):**
    *   **Pros:** Excellent for performance (cache-friendly data layout). Highly decoupled – data (Components) are plain objects/structs, logic (Systems) is separate. Easy to add new types of data and behaviors. Promotes data-oriented design. Great for deterministic updates and replays if state is managed centrally.
    *   **Cons:** Steeper learning curve. Can feel "boilerplate-heavy" initially. Requires a significant upfront refactor of the existing codebase. Managing entity lifecycles and relationships can sometimes be more complex than OOP. JavaScript lacks true structs, so performance benefits might be less pronounced than in C++/Rust unless using typed arrays with libraries like `bitecs`.

*   **Adopting ECS Principles Incrementally (Recommended):**
    *   **Pros:** Allows gradual refactoring. Can start by separating data more clearly (making unit/building properties more like "components") and moving logic into "system-like" functions or modules. Can leverage existing class structure as "entity" containers for now. Focuses on data flow and pure functions for updates where possible. Can integrate with a Redux-like central state for component data, making changes trackable and replayable. Allows using Immer.js for easier immutable updates of component data.
    *   **Cons:** Might lead to a hybrid system that has complexities of both OOP and ECS if not managed carefully. Doesn't immediately yield the full performance benefits of a "pure" ECS data layout.

## 4. Recommendation

**Adopt ECS Principles Incrementally.**

This approach is the most pragmatic for the current state of the project:

1.  **Data-Oriented Refactoring:** Continue refactoring large methods within `Unit.js` and `Building.js` by extracting logic into "system-like" functions. These functions should operate on data passed to them and return new data/state changes, rather than directly mutating objects. This aligns with the "system" concept.
<<<<<<< HEAD
2.  **Component-like Data Structures:** While full component data separation might be too much now, new features or refactored logic should treat groups of related properties on Units/Buildings as "components" (e.g., `shieldData`, `movementData`).
=======
2.  **Component-like Data Structures:** While full component data separation might be too much now, new features or refactored logic should treat groups of related properties on Units/Buildings as "components" (e.g., `shieldData`, `movementData`, `formationData`).
>>>>>>> origin/feat/core-serialization-impl
3.  **Integrate with Command/Reducer Pattern:** The ongoing Redux-like refactor is highly compatible with ECS principles.
    *   `GameCommands` trigger state changes.
    *   Reducers (using Immer.js) act like systems, taking current component data and a command, and producing the next state for those components.
    *   The central state store will hold the "component data" for all entities. Entities themselves might just be IDs, which can serve as indices or coordinates within TrikeShed's `Series` or `Tensor` structures that would store the actual component data. This aligns with TrikeShed's tensor-based system, which is a specific and powerful form of data-oriented design.
4.  **Focus on Logic Separation and Data Flow:** The primary benefit initially will be cleaner, more testable code, and better state management that supports determinism (a key goal of TrikeShed) and replays via the BattleJournal logging commands. The use of Immer.js for immutable updates directly supports TrikeShed's principles of deterministic simulation and idempotent data.
5.  **Defer Full ECS Data Layout (External Libraries):** A shift to a specialized ECS library like `bitecs` (which manages raw array buffers for components) can be considered much later if performance becomes a critical bottleneck. However, TrikeShed's `Series` and `Tensor` primitives, especially if backed by JavaScript TypedArrays where appropriate, offer a native pathway to achieving similar cache-friendly, structured component data management that is inherently compatible with the existing architecture.

This incremental approach, leveraging TrikeShed's data-oriented primitives where possible for component storage and system logic, allows the project to gain benefits of ECS (decoupling, data-oriented logic, testability) without the high cost of a complete rewrite. It strongly aligns with the Redux-like state management already being introduced and the core deterministic principles of the TrikeShed architecture.

---
This document will be saved as `docs/ecs_evaluation.md`.
