# TrikeShed TODO List

Tensor-core evolution tasks and type system migration compliance with CLAUDE.md guidelines.

## Current Status

TrikeShed is undergoing major architectural evolution to implement strict type system compliance and eliminate legacy patterns. The NIO migration has been completed, but significant type system work remains.

## Priority 1: Type System Compliance (CLAUDE.md)

### Completed Tasks
- [x] **NIO Migration**: Moved borg.trikeshed.io.* to borg.trikeshed.nio.*
- [x] **Path Collections Update**: Enhanced collections handling
- [x] **Memory Slab System**: Improved logging and memory management

### Critical Type System Migration
**Status**: In progress, high priority
**Deadline**: Foundation for all other work

#### Shunned Types Elimination
- [ ] **Replace List<T> with Series<T>**
  - [ ] Audit all List<T> usage across codebase
  - [ ] Convert to Series<T> with proper α transforms
  - [ ] Update method signatures and return types
  - [ ] Verify performance implications
  
- [ ] **Replace MutableList<T> with Series<T> α transforms**
  - [ ] Identify mutable collection patterns
  - [ ] Implement immutable transform chains
  - [ ] Ensure no side-effect mutations remain

- [ ] **Replace Pair<A,B> with Join<A,B>**
  - [ ] Convert all Pair usage to Join with `j` operator
  - [ ] Update destructuring patterns
  - [ ] Verify type inference correctness

- [ ] **Eliminate raw collections**
  - [ ] Ensure all data flows through TrikeShed patterns
  - [ ] Convert direct collection usage to tensor operations

#### Mandatory Pattern Implementation
- [ ] **Join Composition Operator (`a j b`)**
  - [ ] Implement Join<A,B> as primary composition mechanism
  - [ ] Ensure `j` operator is the ONLY composition method
  - [ ] Add compile-time verification where possible

- [ ] **Alpha Transform Pattern (`series.α { transform }`)**
  - [ ] Implement α as the ONLY transformation operator
  - [ ] Convert all map/filter/reduce operations to α transforms
  - [ ] Ensure lazy evaluation and optimization

- [ ] **Play Button Materialization (`series ▶`)**
  - [ ] Implement ▶ as gateway to AbstractList/Iterable<T>
  - [ ] Use only for final materialization to legacy collections
  - [ ] Ensure no direct Series to Collection conversion

- [ ] **Inline Value Classes (`@JvmInline value class`)**
  - [ ] Convert all wrapper types to @JvmInline value class
  - [ ] Ensure zero-cost abstractions
  - [ ] Verify runtime performance characteristics

- [ ] **Type Aliases for Primitives**
  - [ ] Add descriptive typealias for ALL recurring primitives
  - [ ] Ensure permanent definitions (no removal allowed)
  - [ ] Document semantic meaning of each alias

## Priority 2: Dead Code Elimination

### Banned Practices Removal
**Status**: Ongoing cleanup required
**Compliance**: CLAUDE.md directives

- [ ] **Remove Simulated Benchmarks**
  - [ ] Audit performance measurement code
  - [ ] Keep only actual running code metrics
  - [ ] Remove any "fake" performance data

- [ ] **Eliminate Fake Demonstrations**  
  - [ ] Remove hardcoded "successful connection" simulations
  - [ ] Ensure all behavior is real implementation
  - [ ] Convert demos to actual working features or remove

- [ ] **Remove Mock Functionality**
  - [ ] Identify code that pretends to work
  - [ ] Either implement real functionality or mark as TODO
  - [ ] No placeholder success responses

- [ ] **Convert Demo-Only Code**
  - [ ] Audit for implementations that cannot perform real work
  - [ ] Convert to real implementations or remove
  - [ ] Mark remaining TODO items appropriately

## Priority 3: Architecture Evolution

### Tensor-First Processing
**Status**: Partial implementation
**Goal**: Complete tensor-first columnar processing

- [ ] **Tensor<T> Implementation**
  - [ ] Ensure Tensor<T> = Join<IntArray,(IntArray)->T>
  - [ ] Implement efficient columnar operations
  - [ ] Optimize for cache locality and SIMD

- [ ] **Series<T> Evolution**
  - [ ] Enhance Series<T> as primary collection type
  - [ ] Implement efficient transform chains
  - [ ] Ensure integration with tensor operations

- [ ] **CCEK Context Management**
  - [ ] Implement Context-driven development patterns
  - [ ] Use inline classes for scope management
  - [ ] Ensure dependency injection through context

### Performance Optimization
- [ ] **Hot/Cold Path Separation**
  - [ ] Identify performance-critical paths
  - [ ] Optimize hot paths for minimal allocation
  - [ ] Move complex logic to cold paths

- [ ] **Zero-Cost Abstractions**
  - [ ] Verify @JvmInline classes generate no overhead
  - [ ] Optimize transform chains for inlining
  - [ ] Profile tensor operations for efficiency

## Priority 4: Integration Support

### Component Integration
- [x] **DGM Integration Support**
  - [x] Ensure TrikeShed types work with DGM operations
  - [x] Provide efficient serialization/deserialization
  - [x] Support for incremental processing

- [ ] **RTS Game Integration**
  - [ ] Optimize tensor operations for game state
  - [ ] Efficient entity processing with Series<T>
  - [ ] Real-time performance requirements

- [ ] **TA4K Trading Integration**
  - [ ] High-frequency trading data processing
  - [ ] Efficient time-series operations
  - [ ] Low-latency transform chains

### API Stability
- [ ] **Stable Type Interface**
  - [ ] Lock down core type system APIs
  - [ ] Ensure backward compatibility where needed
  - [ ] Document breaking changes clearly

- [ ] **Platform Compatibility**
  - [ ] JVM target optimization
  - [ ] Native target support
  - [ ] JS target considerations

## Technical Debt & Quality

### Code Quality Issues
- [ ] **TODO/FIXME Resolution**
  - [ ] Address all TODO comments in TrikeShed code
  - [ ] Convert FIXME items to proper issues
  - [ ] Clean up temporary implementations

- [ ] **Testing Infrastructure**
  - [ ] Comprehensive unit tests for type system
  - [ ] Performance benchmarks for tensor operations
  - [ ] Integration tests with other components

- [ ] **Documentation**
  - [ ] Complete API documentation for all public types
  - [ ] Usage examples for common patterns
  - [ ] Migration guide from legacy patterns

### Known Issues
- [ ] **QuicInstant Prefix Problem**
  - [ ] Remove Quic prefix for general reuse (per CLAUDE.md)
  - [ ] Ensure generic temporal types
  - [ ] Update all references

- [ ] **Cursor vs Tensor Clarification**
  - [ ] Document Cursor as original TrikeShed code
  - [ ] Clarify Series<RowVec> usage patterns
  - [ ] Ensure no confusion between concepts

## Migration Guidelines

### Development Process
1. **No Destructive Changes**: Always proceed without breaking existing functionality
2. **Incremental Migration**: Convert types incrementally with proper testing
3. **Performance Validation**: Verify performance characteristics at each step
4. **Documentation**: Update docs as types evolve

### Testing Strategy
- **Type Safety**: Ensure compile-time verification of type compliance
- **Performance**: Benchmark before/after for each migration step
- **Integration**: Test with dependent components
- **Regression**: Comprehensive regression test suite

## Dependencies & Blockers

### External Dependencies
- Kotlin compiler optimization for @JvmInline
- Platform-specific performance characteristics
- Integration with legacy Java code

### Internal Dependencies
- Component integration requirements
- API stability needs for dependent projects
- Performance requirements from real-time systems

### Risk Mitigation
- Gradual migration approach
- Comprehensive testing at each step
- Rollback plans for breaking changes
- Clear communication of breaking changes

## Success Metrics

1. **Type System Compliance**: 100% CLAUDE.md compliance
2. **Performance**: No degradation from legacy patterns
3. **Code Quality**: Zero TODO/FIXME items
4. **Integration**: Seamless component integration
5. **Documentation**: Complete and accurate documentation

---

*Synchronized with: CLAUDE.md, NIO migration commits, and type system evolution plans*