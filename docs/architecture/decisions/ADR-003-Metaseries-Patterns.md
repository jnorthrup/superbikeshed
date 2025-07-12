# ADR-003: Metaseries Patterns

## Status
Accepted

## Context
AI tends to "go to war" - fighting abstractions for each individual column/token instead of arriving at the metaseries (higher-order patterns) that unify multiple cases. This leads to micro-optimizations and bespoke solutions that don't scale.

## Decision
Design for metaseries - higher-order patterns that work across entire series of similar operations. Use composable abstractions and transformations over individual cases.

## Consequences

### Positive
- Eliminates repetitive code for individual cases
- Creates scalable patterns that work across series
- Improves maintainability through composition
- Leverages type system for series consistency
- Enables batch processing optimizations

### Negative
- Requires upfront design for series patterns
- May be overkill for truly unique cases
- Need to maintain series abstractions

## Implementation
- **Series Over Instances**: Design for entire series of operations
- **Composition Over Specialization**: Build composable transformations
- **Type Safety Over Flexibility**: Use sealed classes and enums for series types
- **Performance Over Convenience**: Use Indexed<T> patterns for series data

## Examples
- **Data Processing**: `processDataSeries<T>(data: Indexed<T>, transform: (T) -> T)`
- **Token Processing**: Sealed class hierarchies for type-safe series
- **Column Validation**: Schema-driven validation for series consistency

## Related
- Metaseries patterns anchor: `.cursor/anchors/metaseries-patterns.md`
- Type system patterns: `.cursor/anchors/type-system-patterns.md`
- String performance war: ADR-002 