# Quality of Life (QoL) Package

The QoL package sanctifies core utilities that make the codebase more pleasant and efficient to work with. These are fundamental building blocks that provide significant developer experience benefits.

## Components

### 1. ZeroScan
Zero/Non-Zero scanning utilities - fundamental boolean checks:
- `Int.z`: Extension property for zero check
- `Int.nz`: Extension property for non-zero check
- `Int?.z`: Extension property for zero check on nullable
- `Int?.nz`: Extension property for non-zero check on nullable

### 2. NullSafe
Type-safe null handling utilities:
- `or(default)`: Safely unwrap a nullable value with a default
- `orCompute(default)`: Safely unwrap a nullable value with a computed default
- `transform(transform)`: Safely transform a nullable value

### 3. CollectionUtils
Collection utilities for common operations:
- `isEmpty()`: Check if a collection is empty
- `isNotEmpty()`: Check if a collection is not empty
- `firstOrNull()`: Get first element or null
- `lastOrNull()`: Get last element or null

### 4. StringUtils
String utilities for common operations:
- `isEmpty()`: Check if string is empty or null
- `isNotEmpty()`: Check if string is not empty and not null
- `isBlank()`: Check if string is blank or null
- `isNotBlank()`: Check if string is not blank and not null

## Usage

```kotlin
import borg.trikeshed.qol.QualityOfLife.*

// Zero/Non-Zero checks
val isZero = 0.z
val isNonZero = 42.nz
val nullableIsZero = nullableInt?.z
val nullableIsNonZero = nullableInt?.nz

// Null-safe operations
val value = nullableValue.or("default")
val computed = nullableValue.orCompute { computeDefault() }
val transformed = nullableValue.transform { it.toString() }

// Collection operations
val isEmpty = myCollection?.isEmpty()
val first = myCollection?.firstOrNull()
val last = myCollection?.lastOrNull()

// String operations
val isEmpty = myString?.isEmpty()
val isNotBlank = myString?.isNotBlank()
```

## Benefits

- Reduces boilerplate code
- Provides consistent null-safety
- Makes common operations more readable
- Improves code maintainability
- Reduces the chance of null pointer exceptions

## Design Principles

1. **Simplicity**: Each utility should be simple and focused
2. **Consistency**: Similar operations should have similar APIs
3. **Null Safety**: All utilities should handle nulls gracefully
4. **Readability**: APIs should be self-documenting
5. **Composability**: Utilities should work well together

## Performance Considerations

- All utilities are designed for high-performance data processing
- SIMD acceleration is used where available
- Bitmap-based processing for efficient memory usage
- Zero-copy operations where possible

## Dependencies

- Kotlin Multiplatform
- Experimental Unsigned Types
- Platform-specific SIMD implementations 