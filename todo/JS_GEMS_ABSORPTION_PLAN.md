# JavaScript Gems Absorption Plan

**Status**: AUDIT COMPLETE - Ready for absorption
**Target**: Migrate valuable JS systems to KMP codec
**Priority**: HIGH - Preserve sophisticated game mechanics

## 🎯 **GEMS IDENTIFIED FOR ABSORPTION**

### 1. **Command & Control System** (116KB, 3157 lines)
**File**: `rtsgame/js/core/systems/commandAndControlSystem.js`

**Key Gems**:
- **Network Latency Simulation**: Light speed delays, fiber optic physics
- **Cache Coherence Protocol**: MESI protocol implementation with L1/L2/L3 cache modeling
- **Command Hierarchy**: Rank-based authority system (General → Colonel → Major → Captain → Lieutenant)
- **Prediction & Reconciliation**: Client-side prediction with server reconciliation
- **Tactical Decision Making**: Sophisticated tactical option scoring and selection
- **Dimensional Instability**: Physics-based network effects
- **Codec Load Balancing**: Dynamic codec node management and path optimization

**Critical Constants**:
```javascript
// Network physics
LIGHT_SPEED = 299792458; // m/s
FIBER_SPEED = 0.67 * LIGHT_SPEED; // ~67% of light speed
MAX_ACCEPTABLE_LATENCY = 500; // ms

// Cache hierarchy
CACHE_LATENCIES = { L1: 4, L2: 12, L3: 40, MAIN_MEMORY: 100, REMOTE_MEMORY: 300 }
CACHE_SIZES = { L1: 64KB, L2: 256KB, L3: 8MB }

// Command ranks with aura effects
COMMAND_RANKS = {
    GENERAL: { level: 5, auraRadius: 1000, forkBonus: 2 },
    COLONEL: { level: 4, auraRadius: 800, forkBonus: 1 },
    // ... etc
}
```

### 2. **Proof of Work System** (17KB, 459 lines)
**File**: `rtsgame/js/core/systems/proofOfWorkSystem.js`

**Key Gems**:
- **Breach Types**: Command injection, data exfiltration, network disruption, core compromise
- **Network Security Model**: Hash rate calculation, vulnerability assessment
- **Cascading Effects**: Breach propagation through network nodes
- **Defensive/Offensive PoW**: Dual-purpose proof of work system

**Critical Mechanics**:
```javascript
// Breach types with specific effects
breachTypes = {
    CMD_INJECTION: 'command_injection',
    DATA_EXFILTRATION: 'data_exfiltration', 
    NETWORK_DISRUPTION: 'network_disruption',
    CORE_COMPROMISE: 'core_compromise'
}

// Vulnerability calculation
calculateBreachSeverity(attacker, target) {
    // Sophisticated severity calculation based on:
    // - Attacker's computronium level
    // - Target's defensive capabilities
    // - Network topology
    // - Previous breach history
}
```

### 3. **Computronium System** (7.3KB, 190 lines)
**File**: `rtsgame/js/core/systems/computroniumSystem.js`

**Key Gems**:
- **Focus Modes**: Offensive (Mars), Defensive (Juno), C&C (Mercury), Utility (Vulcan), Balanced
- **Function Priority Matrix**: Sophisticated fork allocation based on focus mode
- **Performance Degradation**: Realistic performance scaling based on resource allocation

**Critical Logic**:
```javascript
// Function priorities by focus mode
functionPriorities = {
    WEAPON_SYSTEMS: { offensive: 1.0, defensive: 0.3, c_c: 0.2, utility: 0.4, balanced: 0.6 },
    SHIELD_MANAGEMENT: { offensive: 0.3, defensive: 1.0, c_c: 0.4, utility: 0.6, balanced: 0.7 },
    C_C_UPLINK: { offensive: 0.2, defensive: 0.3, c_c: 1.0, utility: 0.6, balanced: 0.5 },
    // ... comprehensive matrix
}

// Fork allocation with degradation
allocatedFunctions[function] = 'OPTIMAL' | 'DEGRADED' | 'STARVED'
```

### 4. **TrikeShed Entity Manager** (12KB, 366 lines)
**File**: `rtsgame/js/core/trikeshedEntityManager.js`

**Key Gems**:
- **Series-Based Entity Management**: TrikeShed Series<T> implementation for deterministic state
- **Deterministic ID Assignment**: Predictable entity ID generation
- **Batch Processing**: Efficient series iteration for entity updates
- **Memory Management**: Active entity counting and cleanup

**Critical Patterns**:
```javascript
// TrikeShed Series implementation
const j = (a, b) => ({ a, b }); // Join operator
const emptySeries = () => j(0, () => { throw new Error("Empty series"); });
const getSeriesValue = (series, i) => series.b(i);
const seriesSize = (series) => series.a;

// Deterministic entity management
updateUnitSeries(simulation, deltaTime) {
    const units = this.seriesToArray(this.unitSeries);
    const survivingUnits = [];
    // Process each unit deterministically
    // Update series with surviving units
}
```

## 🔄 **ABSORPTION STRATEGY**

### Phase 1: Core Systems Migration
1. **TrikeShed Entity Manager** → KMP ECS foundation
2. **Computronium System** → Core resource allocation
3. **Proof of Work System** → Network security model

### Phase 2: Advanced Systems Migration  
1. **Command & Control System** → Network physics and command hierarchy
2. **Cache Coherence Protocol** → Performance modeling
3. **Prediction & Reconciliation** → Deterministic networking

### Phase 3: Integration & Testing
1. **Cross-system integration** → Ensure all systems work together
2. **Deterministic validation** → Verify identical behavior across platforms
3. **Performance optimization** → Leverage KMP advantages

## 📋 **MIGRATION CHECKLIST**

### ✅ **Ready for Migration**
- [ ] **TrikeShed Series patterns** → Already in KMP, needs refinement
- [ ] **Entity management** → Basic structure exists, needs enhancement
- [ ] **Combat systems** → Basic implementation exists

### 🔄 **Needs Migration**
- [ ] **Command & Control System** → Complete rewrite needed
- [ ] **Proof of Work System** → New system to implement
- [ ] **Computronium System** → New system to implement
- [ ] **Cache coherence protocol** → New system to implement
- [ ] **Network physics** → New system to implement

### 🗑️ **To Remove (UI/Rendering)**
- [ ] **Renderer.js** → Move to SpaceGraph
- [ ] **BreachUISystem.js** → Move to SpaceGraph
- [ ] **BreachVisualizationSystem.js** → Move to SpaceGraph
- [ ] **All canvas/rendering code** → Move to SpaceGraph

## 🎯 **IMPLEMENTATION PRIORITY**

### **Priority 1: Foundation**
1. **TrikeShed Entity Manager** - Core ECS foundation
2. **Computronium System** - Resource allocation core
3. **Basic Command System** - Simplified command hierarchy

### **Priority 2: Advanced Features**
1. **Proof of Work System** - Network security
2. **Network Physics** - Latency and cache modeling
3. **Prediction System** - Client-side prediction

### **Priority 3: Optimization**
1. **Cache Coherence** - Performance modeling
2. **Advanced Tactics** - Sophisticated decision making
3. **Integration Testing** - Cross-system validation

## 🔧 **TECHNICAL REQUIREMENTS**

### **KMP Implementation**
- Use TrikeShed `Series<T>` and `Join<A,B>` patterns
- Implement deterministic RNG for all random operations
- Ensure cross-platform compatibility (JVM, Native, JS/WASM)
- Use kotlinx.serialization for state persistence

### **ECS Architecture**
- **Components**: Data-only, no behavior
- **Systems**: Pure functions that process components
- **Entities**: ID + component collections
- **Deterministic**: Same input → same output across platforms

### **Integration Points**
- **SpaceGraph**: Graphics and UI only
- **Move Clients**: Player interaction and state display
- **Network Layer**: Deterministic state synchronization

## 📊 **SUCCESS METRICS**

### **Functional**
- [ ] All JS gems successfully migrated to KMP
- [ ] Identical deterministic behavior across platforms
- [ ] No UI/rendering code in rtsgame codec
- [ ] All systems integrated and tested

### **Performance**
- [ ] KMP version faster than JS version
- [ ] Memory usage optimized for target platforms
- [ ] Deterministic performance characteristics

### **Code Quality**
- [ ] Type-safe implementation
- [ ] Comprehensive test coverage
- [ ] Clear separation of concerns
- [ ] Well-documented APIs

## 🚀 **NEXT STEPS**

1. **Create migration branch** for systematic absorption
2. **Start with TrikeShed Entity Manager** refinement
3. **Implement Computronium System** as first new system
4. **Add Proof of Work System** for network security
5. **Migrate Command & Control System** in phases
6. **Remove all UI/rendering code** to SpaceGraph
7. **Validate deterministic behavior** across platforms

---

**Note**: This absorption preserves the sophisticated game mechanics while modernizing the architecture for KMP deployment. The JS codebase contains valuable algorithmic gems that should not be lost during the migration. 