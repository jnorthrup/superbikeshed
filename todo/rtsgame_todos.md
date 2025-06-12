# RTS Game TODO List

Consolidation of RTS game development tasks and feature integration.

## Current Status

The RTS game has multiple feature branches ready for integration:
- Command Hierarchy Enhancements
- Formation Movement ("Codec Predictor Walk")  
- AI Prediction Interface (Proof of Concept)
- Enhanced Physics and System Mapping

## Feature Integration Tasks

### Command Hierarchy Enhancements
**Status**: Implemented, needs merge and testing
**Priority**: High
**Branch**: `feature/command-hierarchy-enhancements`

- [x] Dynamic unit authority (health, veterancy, context, Computronium)
- [x] Veterancy progression system with ranks and stat boosts  
- [x] Command succession protocol implementation
- [x] Configuration via `commandConfig.js`
- [x] Debug visualizations for command links/authority
- [x] **Balance testing for veterancy bonuses and authority impact**
- [x] **AI logic to understand and use command features**
- [ ] **Advanced veterancy abilities implementation**
  - [ ] `canPromoteSubordinates` flag functionality
  - [ ] `provideMoraleBonus` aura effects
- [ ] **UI elements for rank/authority display**
- [ ] **Contextual authority modifier logic**
- [ ] **Computronium authority modifier based on core level/focus**

### Formation Movement System
**Status**: Implemented, needs merge and testing
**Priority**: High  
**Branch**: `feature/enhanced-formation-movement`

- [x] Leader-follower system with A* pathfinding for leaders
- [x] Predictive slot tracking for followers relative to leader
- [x] Steering behaviors: seek/arrive, separation, terrain avoidance
- [x] A* regrouping for separated followers
- [x] Configuration parameters in `commandConfig.js`
- [x] Debug visualizations for slots and predictions
- [x] **Rigorous testing and parameter tuning**
  - [x] Test with diverse unit counts, speeds, terrain types
  - [x] Tune steering weights (SEPARATION, TERRAIN_AVOIDANCE)
  - [x] Optimize maxForce, maxTurnRate values
  - [x] Adjust prediction time, arrival radius, slot distance
- [ ] **Advanced obstacle avoidance**
  - [ ] Multiple "feelers" for terrain avoidance
  - [ ] Choke point navigation strategies
- [ ] **Leader behavior enhancements**
  - [ ] Speed adjustment based on follower cohesion
  - [ ] A* path choice considering formation width
- [ ] **Sophisticated follower prediction along leader's A* path**
- [ ] **Dynamic formation shapes (line, column, wedge)**
- [ ] **Formation shape change commands**

### AI Prediction Interface
**Status**: Proof of concept complete, needs expansion
**Priority**: Medium
**Branch**: `feature/ai-prediction-interface-poc`

- [x] StrategicAI prediction generation
- [x] Map visualization with confidence-based colors
- [x] Player interaction via right-click acknowledgment
- [x] Event processing and prediction updates
- [x] Documentation in `docs/ai-prediction-interface.md`
- [x] **Additional player interactions**
  - [x] "Dispute & Monitor" action implementation
  - [x] "Counter-Predict: New Threat" player designation
  - [x] Context menu for interaction type selection
- [ ] **More prediction types**
  - [ ] Resource-related predictions (shortfalls, valuable nodes)
  - [ ] Defensive predictions (vulnerable structures/units)
- [ ] **Enhanced enemy attack vector prediction**
  - [ ] Enemy unit clustering analysis
  - [ ] Unit composition and defense factors
  - [ ] Path chokepoint considerations
- [ ] **Tangible AI behavior changes from interactions**
- [ ] **Multiple simultaneous predictions support**
- [ ] **Adaptive learning from player feedback**

## Code Integration Challenges

### Primary Merge Conflicts
**File**: `rtsgame/js/core/unit.js`
- **Challenge**: Major changes from both Command Hierarchy and Formation Movement
- **Areas**: Constructor properties, movement logic, tactical behavior
- **Solution**: Careful integration of all new property initializations and movement systems

**File**: `rtsgame/js/config/commandConfig.js`  
- **Challenge**: Additions from all three feature sets
- **Solution**: Maintain coherent structure while merging configurations

**File**: `rtsgame/js/app.js`
- **Challenge**: StrategicAI instantiation and update calls
- **Solution**: Ensure proper initialization order and update sequence

### Integration Strategy
1. Create integration branch from main
2. Merge Command Hierarchy Enhancements → Test
3. Merge Formation Movement → Resolve conflicts → Test  
4. Merge AI Prediction Interface → Test
5. Comprehensive integration testing

## Performance Optimization Tasks

### Current Architecture
- EntityManager-based simulation with async initialization
- Spatial indexing (64x64 world regions) for cache locality
- Batch processing (64-entity batches) for L1 cache optimization
- Memory stratification (hot/warm/cold data separation)
- Deterministic RNG for replay capability

### Optimization TODOs
- [x] **Performance profiling after feature integration**
- [x] **Cache-friendly data structure validation**
- [x] **Memory allocation optimization in hot paths**
- [x] **Spatial locality verification for new systems**
- [x] **Batch operation efficiency for command/formation systems**
- [x] **Implement predictive memory management system**
  - [x] Create memory usage prediction model
  - [x] Implement proactive resource allocation
  - [x] Add memory pressure monitoring
  - [x] Develop adaptive batch size adjustment
  - [x] Integrate with entity lifecycle management

## UI/UX Enhancement Tasks

### TacticsDSL Integration
- [x] **Visual programming interface integration**
- [x] **Macro library expansion for RTS-specific commands**
- [x] **Hotkey customization for rapid command execution**
- [x] **Tutorial system for complex features**
- [ ] **Implement advanced command visualization system**
  - [ ] Create hierarchical command tree visualization
  - [ ] Add real-time authority flow indicators
  - [ ] Implement formation shape preview
  - [ ] Add unit status overlays
  - [ ] Create interactive command chain editor

### Modern UI System
- [x] **Command hierarchy visualization in UI**
- [x] **Formation controls and shape selection**
- [x] **AI prediction interaction panels** 
- [x] **Performance metrics display**
- [x] **Replay system UI controls**
- [ ] **Implement advanced unit control interface**
  - [ ] Create unit group management panel
  - [ ] Add formation template library
  - [ ] Implement quick command presets
  - [ ] Add unit role assignment interface
  - [ ] Create tactical overlay system

## Testing & Quality Assurance

### Test Categories
- [x] **Unit tests for new systems**
- [x] **Integration tests for feature combinations**
- [x] **Performance benchmarks**
- [x] **Gameplay balance testing**
- [x] **Determinism verification for replay system**

### Test Scenarios
- [ ] **Large-scale battles (500+ units)**
- [ ] **Complex terrain navigation**
- [ ] **Multi-formation coordination**
- [ ] **AI prediction accuracy validation**
- [ ] **Command succession edge cases**

## Documentation Tasks

- [ ] **Update main README with new features**
- [ ] **Architecture documentation for new systems**
- [ ] **API documentation for component interfaces**
- [ ] **User guide for advanced features**
- [ ] **Developer guide for extending systems**

## Dependencies & Blockers

### External Dependencies
- Three.js renderer compatibility
- Webpack build system integration
- Model processing pipeline

### Internal Dependencies  
- TrikeShed type system migration
- SpaceGraph visualization integration
- Core simulation stability

### Potential Blockers
- Memory usage in large scenarios
- Performance degradation from complex features
- Cross-system interaction conflicts

## Timeline Estimates

### Phase 1: Feature Integration (2-3 weeks)
- Merge all feature branches
- Resolve conflicts and test basic functionality
- Initial performance validation

### Phase 2: Enhancement & Polish (3-4 weeks)  
- Implement remaining feature TODOs
- UI/UX improvements
- Performance optimization

### Phase 3: Advanced Features (4-6 weeks)
- TacticsDSL integration
- Advanced AI capabilities
- Comprehensive testing and documentation

## TrikeShed Integration Notes

### Type System Migration
- [x] Replace raw collections with `Series<T>` and `Join<A,B>`
- [x] Implement `α` transforms for data processing
- [x] Use `▶` materialization for stdlib compatibility
- [x] Convert to `@JvmInline value class` wrappers
- [x] Define domain-specific typealiases
- [ ] **Implement type-safe command hierarchy using TrikeShed types**
  - [ ] Create `CommandAuthority` type for unit authority tracking
  - [ ] Define `VeterancyRank` enum with type-safe promotions
  - [ ] Implement `FormationPosition` type for slot tracking
  - [ ] Add type-safe event system for command succession

### Performance Optimization
- [ ] Implement hot/cold path annotations
- [ ] Optimize for L1 cache locality
- [ ] Use zero-cost abstractions
- [ ] Apply memory stratification
- [ ] Implement batch processing

### Code Quality
- [ ] Remove simulated/demo code
- [ ] Implement proper error handling
- [ ] Add comprehensive tests
- [ ] Document type system usage
- [ ] Ensure deterministic behavior

---

*Synchronized with: rtsgame/docs/TODO_IntegrateFeatures.md, feature branch commits, and in-code TODO comments*