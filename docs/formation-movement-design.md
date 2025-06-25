> **Note: This document may be outdated and refer to a previous version of the rtsgame project (likely TypeScript/JavaScript-based). The current project has been significantly refactored to Kotlin Multiplatform with WebGPU. Please cross-reference with the main `rtsgame/README.md` for the latest project information.**

# Enhanced Formation Movement System Design ("Codec Predictor Walk")

## 1. Introduction

The goal of this system is to implement a sophisticated formation movement system that allows groups of units to move in formation smoothly, predictively, and adaptively across the game world. This system, internally codenamed "Codec Predictor Walk," aims to provide a more visually appealing and tactically effective group movement than simple "follow the leader" mechanics.

## 2. Core Principles of "Codec Predictor Walk"

The "Codec Predictor Walk" formation movement system is guided by the following core principles:

*   **Smoothness:** Units should accelerate and decelerate gracefully, and turn smoothly, avoiding jerky or abrupt movements. This is managed by `maxForce` and `maxTurnRate` properties and steering behaviors.
*   **Prediction:** Follower units should anticipate the leader's movement to maintain formation integrity. Currently, this is achieved by projecting the leader's position based on its current velocity for `COMMAND_CONFIG.FORMATION_BEHAVIOR.PREDICTION_TIME_SECONDS`.
*   **Tightness & Adaptability:** Formations should maintain their general shape (radial offsets using `formationAngle` and `MIN_FORMATION_SLOT_DISTANCE`) but be flexible enough to navigate varied terrain and minor obstacles.
*   **Obstacle Awareness (Local):** Individual followers use a single forward "feeler" to detect non-traversable terrain and apply an avoidance force.
*   **Collision Avoidance (Inter-Unit):** Followers use a separation steering behavior (`calculateSeparationForce`) to avoid clumping with nearby friendly units.
*   **Efficiency:** The system primarily relies on steering for followers, reserving A* pathfinding for leaders or followers that become significantly separated.

## 3. System Architecture

The system distinguishes between the behavior of a designated leader unit and its followers.

### 3.1. Leader's Role

*   **Pathfinding:** The leader unit determines the group's path using A* (`findPath`). Its `leaderTargetPosition` is set to its current A* waypoint or patrol target.
*   **Movement:** The leader moves along its path, primarily governed by `defaultMovementAndTargeting`. Its velocity (`vx`, `vy`) is used by followers for prediction.

### 3.2. Follower's Role (Predictive Slot Following)

Followers aim to maintain a specific slot in the formation relative to the leader using steering behaviors.

*   **Formation Slot:** Each follower uses its `formationAngle` and `COMMAND_CONFIG.FORMATION_BEHAVIOR.MIN_FORMATION_SLOT_DISTANCE` to determine a radial offset from the leader's predicted position. (A more complex `formationOffset` object is available for future enhancements).
*   **Leader Prediction:** Followers predict the leader's future position by projecting its current position forward using its current velocity (`vx`, `vy`) and `COMMAND_CONFIG.FORMATION_BEHAVIOR.PREDICTION_TIME_SECONDS`. This predicted position is stored in `leaderPredictedPosition`.
*   **Ideal Formation Slot Calculation:** The `idealFormationSlotWorld` is calculated by applying the radial offset to the `leaderPredictedPosition`.
*   **Steering Behaviors:**
    *   **Seek/Arrive Steering:** A force is calculated to move the unit towards `idealFormationSlotWorld`. This force is dampened when the unit is close to the slot (within `(this.type.size || 10) * COMMAND_CONFIG.FORMATION_BEHAVIOR.ARRIVAL_RADIUS_FACTOR`), creating an arrival effect.
    *   **Separation Steering:** The `calculateSeparationForce` method computes a force to steer away from nearby friendly units within a radius defined by `(this.type.size || 10) * COMMAND_CONFIG.NEIGHBOR_RADIUS_FACTOR`.
    *   **Terrain Avoidance Steering:** The `calculateTerrainAvoidanceForce` method projects a single forward "feeler" (length based on `this.type.size * COMMAND_CONFIG.STEERING_FEELER_LENGTH_FACTOR`). If it detects non-traversable terrain, an avoidance force is generated.
*   **Force Accumulation & Application:** These forces are weighted (using `COMMAND_CONFIG.STEERING_WEIGHTS`) and accumulated in `this.steering`. The total steering force is truncated by `this.maxForce`, applied to the unit's velocity, and then the velocity is truncated by `this.getCurrentSpeed()`. The unit's angle is then smoothly adjusted based on the new velocity, respecting `this.maxTurnRate`.

### 3.3. Formation Commands

The system now supports several formation-specific commands:

*   **SetFormation:** Creates a new formation with specified units and formation type
    *   Sets the first unit as leader
    *   Calculates formation offsets for followers
    *   Updates patrol targets for all units

*   **ChangeFormation:** Switches between formation types
    *   Recalculates formation offsets
    *   Maintains relative positions
    *   Updates unit properties

*   **SetLeader:** Designates a unit as formation leader
    *   Increases unit's authority
    *   Updates formation properties

*   **DisbandFormation:** Breaks up the formation
    *   Clears formation offsets
    *   Resets leader-related properties
    *   Removes patrol targets

*   **RotateFormation:** Rotates the entire formation
    *   Takes angle in degrees
    *   Rotates each unit's offset around leader
    *   Maintains relative distances

*   **ChangeFormationFacing:** Rotates formation to face target
    *   Calculates angle to target
    *   Rotates formation accordingly
    *   Updates patrol targets

*   **MoveFormation:** Moves formation to target position
    *   Option to maintain current facing
    *   Updates leader and follower positions
    *   Maintains formation structure

### 3.4. Regrouping Behavior

*   If a follower's distance to its `groupLeader` exceeds a threshold (`COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC * COMMAND_CONFIG.FORMATION_RULES.MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR`), its formation steering logic is bypassed for that tick.
*   Instead, it generates an A* path directly to the leader's current position and sets `this.patrolTarget` to the leader. This leverages the standard `defaultMovementAndTargeting` logic to help the unit regroup.
*   Once closer, the normal follower steering logic is expected to resume.

## 4. Key Data Structures & Unit Properties (Implemented)

The following properties have been added to or are utilized on `Unit` objects for formation movement:

*   `Unit.formationAngle: number` - A random angle assigned at construction, used for determining a unit's default radial slot around the leader.
*   `Unit.formationOffset: { x: number, y: number, angle: number }` - Default: `{ x: 0, y: 0, angle: 0 }`. Intended for more complex, specific slot definitions (currently, `formationAngle` is used for simpler radial slots).
*   `Unit.leaderTargetPosition: {x: number, y: number} | null` - For leaders, their current A* waypoint or patrol target. `null` for followers when actively forming up.
*   `Unit.leaderPredictedPosition: {x: number, y: number} | null` - (Follower only) The leader's predicted position, calculated based on leader's velocity and `PREDICTION_TIME_SECONDS`.
*   `Unit.idealFormationSlotWorld: {x: number, y: number} | null` - (Follower only) The follower's calculated target world position to maintain formation.
*   `Unit.maxForce: number` - Maximum magnitude of the combined steering force that can be applied in a tick. Initialized from `COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_FORCE`.
*   `Unit.maxTurnRate: number` - Maximum angle (in radians) the unit can turn per frame. Initialized from `COMMAND_CONFIG.FORMATION_BEHAVIOR.DEFAULT_MAX_TURN_RATE_RADIANS_PER_FRAME`.
*   `Unit.steering: { x: number, y: number }` - Accumulator for steering forces each tick. Reset to `{0,0}` before recalculation.
*   `Unit.debugFormation: boolean` - A flag (default `false`) to enable verbose console logging for specific units during formation movement.

## 5. Integration with `executeGroupMovement`

The `executeGroupMovement(gameContext)` method in `Unit.js` now contains the core logic:
*   **Leader Identification:** Identifies the `groupLeader` based on authority and fitness within strategic range. A unit can act as its own leader if none better is found and it's fit.
*   **Leader Behavior:** If a unit is a leader, it updates its `leaderTargetPosition` and clears follower-specific properties. Its movement is primarily driven by `defaultMovementAndTargeting`.
*   **Follower Behavior:**
    1.  Checks for excessive separation from the leader. If too far, initiates **Regrouping Behavior** (A* path to leader) and skips other follower logic for the tick.
    2.  Predicts the leader's future position (`leaderPredictedPosition`).
    3.  Calculates its `idealFormationSlotWorld` using `formationAngle` and `MIN_FORMATION_SLOT_DISTANCE` from the leader's predicted position.
    4.  Resets `this.steering`.
    5.  Calculates and accumulates steering forces:
        *   Seek/Arrive force towards `idealFormationSlotWorld` (dampened on approach).
        *   Separation force from nearby units (`calculateSeparationForce`).
        *   Terrain avoidance force from a forward feeler (`calculateTerrainAvoidanceForce`).
    6.  The combined `this.steering` force is truncated by `this.maxForce`.
    7.  The steering force is applied to update `this.vx` and `this.vy`.
    8.  The resulting velocity is truncated by `this.getCurrentSpeed()`.
    9.  `this.angle` is smoothly adjusted towards the new velocity direction, respecting `this.maxTurnRate`.
    10. Follower's `patrolTarget` and `path` are cleared to ensure formation logic takes precedence.

## 6. Obstacle Avoidance Strategy

*   **Leader:** Relies on its global A* pathfinding.
*   **Followers:**
    *   **Inter-Unit:** `calculateSeparationForce` pushes units away from each other within a configured radius (`NEIGHBOR_RADIUS_FACTOR`).
    *   **Terrain:** `calculateTerrainAvoidanceForce` uses a single forward "feeler" (length based on `STEERING_FEELER_LENGTH_FACTOR`). If the feeler's tip is on a non-traversable tile, a repulsion force is generated. More complex feeler arrangements are a future consideration.
    *   Units attempt to steer back to their ideal slot after minor deviations.

## 7. Key Challenges

*   **Tuning Steering Behaviors:** Balancing weights (`COMMAND_CONFIG.STEERING_WEIGHTS`) and parameters (`maxForce`, `maxTurnRate`, radii, feeler length) is crucial.
*   **Performance:** Especially `calculateSeparationForce` (O(N^2) if checking all pairs, though currently checks against all units for each unit) and multiple `isTraversable` calls if many feelers were used.
*   **Choke Points:** Current system relies on leader pathing and follower local adaptation (separation, single feeler avoidance). May result in formation compression or temporary disorder.
*   **Complex Obstacle Shapes:** Single forward feeler for terrain avoidance is basic and may not handle all concave shapes or complex obstacle clusters well.

## 8. Future Considerations

*   **Dynamic Formation Shapes:** More sophisticated `formationOffset` usage.
*   **Advanced Flocking:** Cohesion and alignment steering behaviors.
*   **Multiple Feeler Arrangements:** For better terrain/obstacle avoidance by followers.
*   **Path Smoothing for Leader:** To make follower prediction easier.
*   **Follower Pathing to Slot:** Instead of pure steering, short A* paths to `idealFormationSlotWorld` if heavily obstructed but not fully separated.
*   **Formation Combat Maneuvers:** Specialized movement patterns for combat situations.
*   **Formation Transitions:** Smooth interpolation between different formation types.
*   **Formation-specific Behaviors:** Different movement speeds or behaviors based on formation type.

## 9. Configuration Parameters

The following parameters in `rtsgame/js/config/commandConfig.js` control the formation and steering behaviors:

*   **`COMMAND_CONFIG.FORMATION_BEHAVIOR`**:
    *   `PREDICTION_TIME_SECONDS`: How far ahead (in seconds) followers predict the leader's position based on leader's current velocity.
    *   `ARRIVAL_RADIUS_FACTOR`: Multiplied by `unit.type.size`. When a follower is within this distance of its target slot, the "seek" force is nullified (basic arrival).
    *   `DEFAULT_MAX_FORCE`: The default maximum magnitude of combined steering forces applied to a unit per frame.
    *   `DEFAULT_MAX_TURN_RATE_RADIANS_PER_FRAME`: The default maximum rate (radians per frame) at which a unit can change its orientation.
    *   `MIN_FORMATION_SLOT_DISTANCE`: The base radial distance followers try to maintain from the (predicted) leader position, using their `formationAngle`.
*   **`COMMAND_CONFIG.FORMATION_RULES`**:
    *   `MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR`: Multiplied by `COMMAND_CONFIG.COMMAND_RANGES.STRATEGIC`. If a follower is further than this distance from its leader, it triggers A* regrouping behavior.
*   **`COMMAND_CONFIG.STEERING_WEIGHTS`**:
    *   `SEPARATION`: Multiplier for the separation force.
    *   `TERRAIN_AVOIDANCE`: Multiplier for the terrain avoidance force.
*   **`COMMAND_CONFIG.STEERING_FEELER_LENGTH_FACTOR`**: Multiplied by `unit.type.size` to determine the length of the terrain avoidance feeler.
*   **`COMMAND_CONFIG.NEIGHBOR_RADIUS_FACTOR`**: Multiplied by `unit.type.size` to determine the radius within which separation forces are checked against other units.
*   **`COMMAND_CONFIG.COMMAND_RANGES`**:
    *   `STRATEGIC`: Used as the base for `MAX_FOLLOWER_SEPARATION_DISTANCE_FACTOR`. Also used by `executeGroupMovement` for the initial leader search radius.
    *   `FORMATION_MIN_DISTANCE` (value 80): Used in `followSuperiorOrders` for basic following distance.
    *   `FORMATION_MAX_DISTANCE` (value 150): Used in `followSuperiorOrders` for basic following distance.

This document should be updated as the system evolves.

## 10. System Interactions

### 10.1. Command System Integration
- Formation commands processed by CommandSystem
- Commands trigger state changes in FormationComponent
- Command execution affects multiple units simultaneously
- Command history maintained for replay/debugging

### 10.2. Movement System Integration
- FormationSystem updates ideal positions
- MovementSystem applies steering behaviors
- Pathfinding considers formation shape
- Collision avoidance respects formation structure

### 10.3. AI System Integration
- AI uses formations for tactical positioning
- Formation commands integrated into AI decision making
- AI can predict and counter enemy formations
- Formation-aware combat behaviors

### 10.4. Combat System Integration
- Formations affect combat effectiveness
- Formation-specific combat bonuses
- Combat can trigger formation changes
- Damage affects formation maintenance

### 10.5. Physics System Integration
- Formation-aware collision detection
- Terrain adaptation for formations
- Physics constraints for formation maintenance
- Formation-specific movement rules

### 10.6. Rendering System Integration
- Formation visualization
- Formation transition animations
- Leader/follower highlighting
- Formation-specific effects

## 11. Key Data Structures

### 11.1. FormationComponent
```typescript
{
    formationType: string,
    formationOffset: { x: number, y: number },
    leaderId?: string,
    leaderTargetPosition?: { x: number, y: number },
    leaderPredictedPosition?: { x: number, y: number },
    idealFormationSlotWorld?: { x: number, y: number },
    maxForce: number,
    maxTurnRate: number,
    steering: { x: number, y: number }
}
```

### 11.2. FormationCommand
```typescript
interface FormationCommand {
    execute(gameState: GameState, gameMap: GameMap): void;
}

class SetFormation implements FormationCommand {
    constructor(
        private unitIds: string[],
        private formationType: string
    ) {}
    // Implementation
}

// Other command implementations...
```

## 12. Configuration Parameters

### 12.1. Formation Parameters
- Spacing between units
- Formation transition speed
- Leader prediction distance
- Formation maintenance thresholds

### 12.2. Steering Parameters
- Separation force
- Alignment force
- Cohesion force
- Path following weight

## 13. Future Considerations

### 13.1. Formation Combat Maneuvers
- Flanking formations
- Defensive formations
- Attack formations
- Specialized combat formations

### 13.2. Formation Transitions
- Smooth transitions between formations
- Formation-specific transition rules
- Transition timing and coordination
- Transition visualization

### 13.3. Formation-specific Behaviors
- Formation-based combat bonuses
- Formation-specific movement rules
- Formation-based ability effects
- Formation-specific AI behaviors

### 13.4. Advanced Obstacle Avoidance
- Formation-aware pathfinding
- Dynamic formation reshaping
- Gap management
- Terrain adaptation

### 13.5. Leader Behavior Enhancement
- Advanced leader selection
- Dynamic leader switching
- Leader-specific formation rules
- Leader command authority

### 13.6. Formation Refinement
- Improved separation logic
- Better formation maintenance
- Enhanced transition smoothness
- More sophisticated steering

---
This document will be saved as `docs/formation-movement-design.md`.
