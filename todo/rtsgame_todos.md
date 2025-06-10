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
- [ ] **Balance testing for veterancy bonuses and authority impact**
- [ ] **AI logic to understand and use command features**
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
- [ ] **Rigorous testing and parameter tuning**
  - [ ] Test with diverse unit counts, speeds, terrain types
  - [ ] Tune steering weights (SEPARATION, TERRAIN_AVOIDANCE)
  - [ ] Optimize maxForce, maxTurnRate values
  - [ ] Adjust prediction time, arrival radius, slot distance
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
- [ ] **Additional player interactions**
  - [ ] "Dispute & Monitor" action implementation
  - [ ] "Counter-Predict: New Threat" player designation
  - [ ] Context menu for interaction type selection
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
- [ ] **Performance profiling after feature integration**
- [ ] **Cache-friendly data structure validation**
- [ ] **Memory allocation optimization in hot paths**
- [ ] **Spatial locality verification for new systems**
- [ ] **Batch operation efficiency for command/formation systems**

## UI/UX Enhancement Tasks

### TacticsDSL Integration
- [ ] **Visual programming interface integration**
- [ ] **Macro library expansion for RTS-specific commands**
- [ ] **Hotkey customization for rapid command execution**
- [ ] **Tutorial system for complex features**

### Modern UI System
- [ ] **Command hierarchy visualization in UI**
- [ ] **Formation controls and shape selection**
- [ ] **AI prediction interaction panels** 
- [ ] **Performance metrics display**
- [ ] **Replay system UI controls**

## Testing & Quality Assurance

### Test Categories
- [ ] **Unit tests for new systems**
- [ ] **Integration tests for feature combinations**
- [ ] **Performance benchmarks**
- [ ] **Gameplay balance testing**
- [ ] **Determinism verification for replay system**

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

---

*Synchronized with: rtsgame/docs/TODO_IntegrateFeatures.md, feature branch commits, and in-code TODO comments*