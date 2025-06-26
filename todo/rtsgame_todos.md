# RTS Game - MEGA TODO & Status Consolidation

**This is the canonical, up-to-date source for all actionable status, TODOs, and integration plans for the RTS game project.**

---

## 1. Current Status & Foundation

- **Architecture:** Foundation for an RTS engine is present (entity management, AI, command hierarchy, terrain/pathfinding, codecs, battle recording).
- **State:** Untested skeleton code; systems exist in isolation and require integration.
- **Migration:** Major refactor from TypeScript/JavaScript to Kotlin Multiplatform (KMP) with WebGPU. Strict "no improvements during port" rule.
- **Build:** Compilation not guaranteed; build verification pending.
- **Testing:** No comprehensive tests or benchmarks run yet.

---

## 2. Core Systems & Implementation Gaps

### Resource System
- **Implemented:** Mass, Energy, Computronium (basic mechanics).
- **Defined but basic/placeholders:** Alloy, Battery, Cycles, Info, Population.
- **Conceptual:** Landscape consumption, mineralogy, remnant civilizations.
- **TODO:**
  - [ ] Full implementation of alloy manufacturing, mineral acquisition.
  - [ ] Detailed mechanics for battery, cycles, info, population.
  - [ ] Landscape consumption affecting terrain mesh.
  - [ ] Remnant civilization mechanics.

### Unit Design & Capabilities
- **Implemented:** Basic stats, Computronium Cores, "Dining Philosophers".
- **Missing:** Alloy system affecting stats, some special abilities.
- **TODO:**
  - [ ] Implement alloy system for units.
  - [ ] Flesh out special abilities (e.g., quantum_entanglement, phase_shift).

### Combat Systems
- **Implemented:** Basic projectile, direct damage, shield system (advanced), weapon capacitance.
- **Missing:** Armor, detailed damage type/armor interactions, secondary effects.
- **TODO:**
  - [ ] Armor types/values for units.
  - [ ] Damage formulas considering armor and type interactions.
  - [ ] Secondary effects (EMP, corrosive, etc).

### Command & Control (C&C)
- **Implemented:** Rank, authority, formation system (well-developed), latency constants.
- **Missing:** Full C&C latency effects, predictive AI, advanced squad AI, rank bonuses, formation-specific maneuvers, smooth transitions.
- **TODO:**
  - [ ] Implement C&C latency effects.
  - [ ] Develop predictive AI for C&C.
  - [ ] Advanced squad AI/contextual behaviors.
  - [ ] Rank bonuses, formation maneuvers, transitions.

### Proof-of-Work (PoW) & Computational Warfare
- **Implemented:** Defensive/offensive PoW, breach attempts, network hash rate.
- **TODO:**
  - [ ] Further balancing/integration with other systems.
  - [ ] UI feedback for PoW attacks.
  - [ ] CSA (Chronological Sync Attack) if not covered.

### Data Architecture & Determinism ("TrikeShed")
- **Implemented:** Deterministic RNG, replay system, immutable updates.
- **TODO:**
  - [ ] Continuous verification of determinism across all new logic.

### Meta-Network & Tech Tree
- **Meta-Network:** Conceptual only (P2P replay/mod sharing, PoW for meta-network integrity).
- **Tech Tree:** Not implemented; only unit tiers suggest progression.
- **TODO:**
  - [ ] Design/implement research system, tech tree UI, link unlocks to game elements.
  - [ ] Meta-network: replay export, P2P, modding support.

---

## 3. Feature Integration Tasks

### Command Hierarchy Enhancements
- [x] Dynamic unit authority (health, veterancy, context, Computronium)
- [x] Veterancy progression system
- [x] Command succession protocol
- [x] Config via `commandConfig.js`
- [x] Debug visualizations
- [ ] Advanced veterancy abilities (`canPromoteSubordinates`, `provideMoraleBonus`)
- [ ] UI for rank/authority/veterancy
- [ ] Contextual authority modifier logic
- [ ] Computronium authority modifier logic
- [ ] Review `followSuperiorOrders` vs. formation movement precedence

### Formation Movement System
- [x] Leader-follower system (A* for leaders)
- [x] Predictive slot tracking for followers
- [x] Steering behaviors: seek/arrive, separation, terrain avoidance
- [x] A* regrouping for separated followers
- [x] Configurable parameters
- [x] Debug visualizations
- [ ] Advanced obstacle avoidance (multi-feeler, choke points)
- [ ] Leader speed adjustment, path choice by formation width
- [ ] Sophisticated follower prediction
- [ ] Dynamic formation shapes, shape change commands
- [ ] Formation combat maneuvers, transitions, formation-specific bonuses/abilities

### AI Prediction Interface
- [x] StrategicAI prediction generation
- [x] Map visualization, player interaction, event processing
- [ ] More player interactions ("Dispute & Monitor", "Counter-Predict: New Threat")
- [ ] More prediction types (resource, defensive)
- [ ] Enhanced enemy attack vector prediction (clustering, composition, chokepoints)
- [ ] Tangible AI behavior changes from interactions
- [ ] Multiple simultaneous predictions
- [ ] Adaptive learning from player feedback

### General/Cross-Cutting
- [ ] Comprehensive testing after all merges
- [ ] Performance profiling/optimization
- [ ] Documentation updates
- [ ] Code cleanup (remove temp/debug code, address TODO/FIXME)

---

## 4. Performance & Optimization
- [x] EntityManager-based simulation, async init
- [x] Spatial indexing, batch processing, memory stratification
- [x] Deterministic RNG for replay
- [ ] Validate cache-friendly data structures
- [ ] Optimize memory allocation in hot paths
- [ ] Batch operation efficiency for new systems
- [ ] Predictive memory management system
- [ ] Hot/cold path annotations, L1 cache locality, zero-cost abstractions

---

## 5. UI/UX Enhancement Tasks
- [x] Command hierarchy visualization
- [x] Formation controls/shape selection
- [x] AI prediction interaction panels
- [x] Performance metrics display
- [x] Replay system UI controls
- [ ] Advanced unit control interface (group management, formation templates, quick presets, tactical overlays)
- [ ] Command tree visualization, authority flow indicators, formation preview, unit status overlays, interactive command chain editor

---

## 6. Testing & Quality Assurance
- [x] Unit/integration/performance tests for new systems
- [x] Gameplay balance testing
- [x] Determinism verification for replay
- [ ] Large-scale battles (500+ units)
- [ ] Complex terrain navigation
- [ ] Multi-formation coordination
- [ ] AI prediction accuracy validation
- [ ] Command succession edge cases

---

## 7. Documentation Tasks
- [ ] Update main README with new features
- [ ] Architecture docs for new systems
- [ ] API docs for component interfaces
- [ ] User guide for advanced features
- [ ] Developer guide for extending systems

---

## 8. Dependencies & Blockers
- **External:** Three.js renderer, Webpack, model processing pipeline
- **Internal:** TrikeShed migration, SpaceGraph integration, core simulation stability
- **Blockers:** Memory usage in large scenarios, performance degradation, cross-system conflicts

---

## 9. Timeline Estimates
- **Phase 1:** Feature Integration (2-3 weeks)
- **Phase 2:** Enhancement & Polish (3-4 weeks)
- **Phase 3:** Advanced Features (4-6 weeks)

---

## 10. TrikeShed Integration Notes
- [x] Replace raw collections with `Series<T>`, `Join<A,B>`
- [x] Implement `α` transforms, `play` materialization, `@JvmInline value class` wrappers
- [x] Domain-specific typealiases
- [ ] Type-safe command hierarchy, `CommandAuthority`, `VeterancyRank`, `FormationPosition`, type-safe event system
- [ ] Hot/cold path annotations, L1 cache, zero-cost abstractions, batch processing
- [ ] Remove demo code, add error handling, comprehensive tests, document type system, ensure determinism

---

**This document supersedes all previous status, TODO, and planning docs for the RTS game project.**