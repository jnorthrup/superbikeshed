# ADR-002: String Performance War

## Status
Accepted

## Context
String pointers cause innumerable stalls in common Kotlin JVM code. String allocations in speculative loops create garbage collection pressure and kill performance.

## Decision
Ban String usage in speculative loops and performance-critical paths. Use enums, constants, and structured patterns instead.

## Consequences

### Positive
- Eliminates garbage collection pressure in hot paths
- Improves performance in speculative loops
- Reduces memory allocations in critical code paths
- Forces better design patterns (enums, structured logging)

### Negative
- Requires more upfront design for identifiers
- May require refactoring existing String-heavy code
- Need to maintain alternative patterns (enums, constants)

## Implementation
- **Forbidden**: String literals in loops, String concatenation in hot paths
- **Required**: Use enums for identifiers, structured logging, primitive comparisons
- **Detection**: Automated validation scripts check for violations

## Related
- String war anchor: `.cursor/anchors/string-war-anchor.md`
- Validation script: `scripts/validate-string-war.sh`
- Type system patterns: `.cursor/anchors/type-system-patterns.md` 