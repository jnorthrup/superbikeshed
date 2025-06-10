# Consolidated Superbikeshed TODO List

## 1. Core Type System & Data Model (P0)

### 1.1 Type System Compliance
- [ ] **List<T> to Series<T> Migration**
  - [ ] Audit all List<T> usage
  - [ ] Convert to Series<T> with α transforms
  - [ ] Update method signatures
  - [ ] Performance validation

- [ ] **MutableList<T> to Series<T> Migration**
  - [ ] Identify mutable patterns
  - [ ] Implement immutable transform chains
  - [ ] Verify no side effects

- [ ] **Pair<A,B> to Join<A,B> Migration**
  - [ ] Convert all Pair usage to Join
  - [ ] Implement `j` operator
  - [ ] Update destructuring patterns

- [ ] **Raw Collection Elimination**
  - [ ] Audit direct collection usage
  - [ ] Convert to tensor operations
  - [ ] Verify data flow patterns

### 1.2 Mandatory Pattern Implementation
- [ ] **Join Composition**
  - [ ] Implement Join<A,B> as primary composition
  - [ ] Enforce `j` operator usage
  - [ ] Add compile-time verification

- [ ] **Alpha Transform Pattern**
  - [ ] Implement `.α { transform }` operator
  - [ ] Convert all transformations
  - [ ] Optimize lazy evaluation

- [ ] **Play Button Materialization**
  - [ ] Implement `▶` operator
  - [ ] Enforce as only gateway to collections
  - [ ] Prevent direct conversions

- [ ] **Value Class Migration**
  - [ ] Convert wrappers to @JvmInline
  - [ ] Verify zero-cost abstractions
  - [ ] Performance profiling

### 1.3 Type System Cleanup
- [ ] **Primitive Type Aliases**
  - [ ] Define permanent type aliases
  - [ ] Document semantic meaning
  - [ ] Enforce usage

- [ ] **Tensor<T> Implementation**
  - [ ] Define as Join<IntArray,(IntArray)->T>
  - [ ] Implement columnar operations
  - [ ] Optimize for SIMD

- [ ] **Cursor vs Series Clarification**
  - [ ] Document legacy Cursor concept
  - [ ] Establish Series<RowVec> patterns
  - [ ] Update documentation

## 2. RTS Game Core Systems (P1)

### 2.1 Entity System
- [ ] **Entity State Migration**
  - [ ] Define entity components
  - [ ] Update EntityFactory
  - [ ] Implement state queries

- [ ] **Unit/Building Refactor**
  - [ ] Remove direct state properties
  - [ ] Implement EntityManager queries
  - [ ] Add state change requests

- [ ] **Combat System**
  - [ ] Implement damage calculation
  - [ ] Add armor/shield mechanics
  - [ ] Handle unit destruction

### 2.2 Resource System
- [ ] **Income/Cost Implementation**
  - [ ] Integrate BUILDING_YIELDS
  - [ ] Add BUILDING_COSTS
  - [ ] Implement UNIT_COSTS

- [ ] **Resource Node Management**
  - [ ] Link extractors to nodes
  - [ ] Implement depletion logic
  - [ ] Add terrain composition

### 2.3 AI System
- [ ] **State Perception**
  - [ ] Design AI state queries
  - [ ] Implement specialized views
  - [ ] Add accessor patterns

- [ ] **Action System**
  - [ ] Define command structures
  - [ ] Implement action submission
  - [ ] Add game loop processing

## 3. Visualization & UI (P2)

### 3.1 SpaceGraph Integration
- [ ] **Edge Labeling**
  - [ ] Implement label rendering
  - [ ] Add dynamic updates
  - [ ] Style configuration

- [ ] **Resource Flow Visualization**
  - [ ] Create resource edges
  - [ ] Add flow indicators
  - [ ] Implement animations

- [ ] **Performance Optimization**
  - [ ] Profile large graphs
  - [ ] Implement optimizations
  - [ ] Add level-of-detail

### 3.2 UI Enhancements
- [ ] **Command Hierarchy UI**
  - [ ] Add rank/authority display
  - [ ] Implement formation controls
  - [ ] Add prediction panels

- [ ] **TacticsDSL Integration**
  - [ ] Create visual programming interface
  - [ ] Add macro library
  - [ ] Implement hotkeys

## 4. Build & Infrastructure (P2)

### 4.1 Build System
- [ ] **Kotlin/JS Configuration**
  - [ ] Fix Gradle wrapper issues
  - [ ] Generate JS artifacts
  - [ ] Evaluate artifact usage

- [ ] **Testing Infrastructure**
  - [ ] Configure Jest for trikeshed-ts
  - [ ] Add integration tests
  - [ ] Set up CI/CD

### 4.2 Documentation
- [ ] **API Documentation**
  - [ ] Document public APIs
  - [ ] Add usage examples
  - [ ] Create migration guides

- [ ] **Architecture Documentation**
  - [ ] Document system design
  - [ ] Add component diagrams
  - [ ] Create developer guides

## 5. Integration & Testing (P3)

### 5.1 Feature Integration
- [ ] **Merge Strategy**
  - [ ] Define merge order
  - [ ] Create integration branch
  - [ ] Resolve conflicts

- [ ] **Integration Testing**
  - [ ] Test feature combinations
  - [ ] Verify performance
  - [ ] Validate determinism

### 5.2 Performance Testing
- [ ] **Benchmark Suite**
  - [ ] Create performance tests
  - [ ] Add memory profiling
  - [ ] Implement stress tests

- [ ] **Optimization**
  - [ ] Profile hot paths
  - [ ] Optimize allocations
  - [ ] Verify cache locality

## Dependencies & Blockers

### External Dependencies
- Three.js compatibility
- Webpack integration
- Model processing pipeline

### Internal Dependencies
- TrikeShed type system
- SpaceGraph visualization
- Core simulation stability

### Potential Blockers
- Memory usage in large scenarios
- Performance degradation
- Cross-system conflicts

## Timeline

### Phase 1: Core Systems (2-3 weeks)
- Type system compliance
- Entity system migration
- Basic resource system

### Phase 2: Features & Polish (3-4 weeks)
- AI system implementation
- Visualization integration
- UI enhancements

### Phase 3: Advanced Features (4-6 weeks)
- TacticsDSL integration
- Advanced AI capabilities
- Comprehensive testing 