# JS → KMP Rescue Plan

## Core Principle: PRESERVE THE WORKING JS GAME

The JavaScript RTS is **working and growing**. We rescue it by translating its proven patterns to KMP, not by "improving" it.

## Architecture Mapping

```
JS Structure                    →  KMP Structure
-----------                        -------------
js/core/                      →  commonMain/kotlin/rtsgame/core/
  simulation.js               →    Simulation.kt
  gameEngine.js               →    GameEngine.kt  
  unit.js                     →    Unit.kt
  building.js                 →    Building.kt
  entityFactory.js            →    EntityFactory.kt
  
js/ai/                        →  commonMain/kotlin/rtsgame/ai/
  strategicAI.js              →    StrategicAI.kt
  commandHierarchy.js         →    CommandHierarchy.kt
  autonomousBehavior.js       →    AutonomousBehavior.kt
  
js/config/                    →  commonMain/kotlin/rtsgame/config/
  unitTypes.js                →    UnitTypes.kt
  buildingTypes.js            →    BuildingTypes.kt
  gameConstants.js            →    GameConstants.kt
```

## Translation Strategy

### Phase 1: Core Game Loop (Week 1)
1. `Simulation.kt` - Direct port of simulation.js
2. `GameEngine.kt` - Direct port of gameEngine.js  
3. `GameState.kt` - Direct port of gameState.js
4. **Verify**: Basic simulation runs identically to JS

### Phase 2: Entity System (Week 2)
1. `Unit.kt` - Direct port with all behaviors
2. `Building.kt` - Direct port with production queues
3. `EntityFactory.kt` - Direct port of spawning logic
4. **Verify**: Units spawn and behave identically

### Phase 3: AI & Autonomy (Week 3)
1. `AutonomousBehavior.kt` - Unit-level decisions
2. `CommandHierarchy.kt` - Command structure
3. `StrategicAI.kt` - High-level planning
4. **Verify**: AI behavior matches JS exactly

### Phase 4: Combat & Systems (Week 4)
1. Port all systems/ modules
2. Port terrain generation
3. Port resource management
4. **Verify**: Full gameplay parity

## Key Preservation Rules

### 1. NO IMPROVEMENTS DURING PORT
```kotlin
// BAD: "Improving" during translation
class Unit {
    private val position = Vector2D() // NO! JS uses x,y
    fun move() = position.normalize() // NO! Adding "better" math
}

// GOOD: Exact translation
class Unit {
    var x: Double = 0.0  // Matches JS
    var y: Double = 0.0  // Matches JS
    fun move() {
        // Exact JS logic here
    }
}
```

### 2. PRESERVE GAME CONSTANTS
```kotlin
// Copy exact values from JS
object GameConstants {
    const val WORLD_SIZE = 5000
    const val TILE_SIZE = 10
    const val GRID_SIZE = 500
    // etc - NO CHANGES
}
```

### 3. MAINTAIN BEHAVIOR FIDELITY
- Same update rates (60 FPS)
- Same physics calculations
- Same random seed handling
- Same pathfinding logic

## Testing Strategy

### Parallel Execution Tests
1. Run JS version with seed X
2. Run KMP version with seed X
3. Compare frame-by-frame:
   - Unit positions
   - Resource counts
   - Combat outcomes
   - AI decisions

### Replay Compatibility
- KMP must play JS-recorded replays
- JS must play KMP-recorded replays
- Binary identical outcomes required

## What We DON'T Port

1. **Rendering** - KMP uses native rendering per platform
2. **Input handling** - Platform specific
3. **UI/DOM manipulation** - Platform specific
4. **File I/O** - Use KMP patterns

## Success Criteria

✓ JS and KMP produce identical simulation results  
✓ Same gameplay feel and timing  
✓ AI behaves identically  
✓ Replays are cross-compatible  
✓ Performance meets or exceeds JS  

## Anti-Patterns to Avoid

❌ "While we're at it, let's improve..."  
❌ "This would be cleaner if..."  
❌ "KMP has better patterns for..."  
❌ Adding new features during port  
❌ Changing magic numbers/constants  
❌ "Fixing" quirky JS behaviors  

## The Mantra

**"If it works in JS, it works in KMP. No improvements. Just translation."**

The goal is a 1:1 mechanical translation that preserves every quirk, every magic number, every emergent behavior that makes the JS version brilliant.