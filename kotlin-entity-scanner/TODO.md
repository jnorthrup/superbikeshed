# kotlin-entity-scanner TODO

## Core Implementation

- [ ] Complete hierarchical token classification system (5 levels)
- [ ] Implement inductive graph refinement algorithm
- [ ] Build evidence accumulation and forward chaining logic
- [ ] Add confidence scoring system for parse states

## Entity Extraction

- [ ] Class and data class extraction
- [ ] Function and property extraction  
- [ ] Import and dependency parsing
- [ ] Annotation processing (especially @file: annotations)
- [ ] Call graph generation

## K2Script Integration

- [ ] Parse @file:DependsOn annotations with context awareness
- [ ] Handle @file:Include statements
- [ ] Extract Maven coordinates and dependency information
- [ ] Replace regex-based parsing in k2script LineParser

## Performance Optimization

- [ ] Zero-cost inline class hierarchy implementation
- [ ] Lazy evaluation for Series operations
- [ ] Incremental parsing for real-time updates
- [ ] Memory-efficient graph data structures

## TrikeShed Compliance

- [ ] Convert all collections to Series<T>
- [ ] Replace Pair usage with Join<A,B>
- [ ] Implement α transforms for all operations
- [ ] Add taxonomical type aliases

## Testing

- [ ] Comprehensive Kotlin syntax test suite
- [ ] Edge case handling tests
- [ ] Performance benchmarks
- [ ] Integration tests with k2script

## Documentation

- [ ] API documentation with examples
- [ ] Architecture design documentation
- [ ] Integration guide for other projects
- [ ] Performance characteristics guide