# ANTI-REGRESSION PROTECTION

**WARNING: DO NOT DELETE OR MODIFY WITHOUT EXPLICIT PERMISSION**

This file serves as a protection mechanism against code regressions and unauthorized deletions.

## PROTECTED COMPONENTS - DO NOT DELETE

### Core Type System (CRITICAL)
- `Series2.kt` - Essential typealias and operators for Series<Join<A,B>>
- `Join.kt` - Core tensor composition operator
- `Series.kt` - Foundation of TrikeShed type system
- All `@JvmInline value class` definitions
- All `typealias` definitions

### ISAM System (CRITICAL - Old Codebase Foundation)
- `IsamDataFile.kt` - Core ISAM data file handling
- `IsamMetaFileReader.kt` - ISAM metadata parsing
- `RecordMeta.kt` - Record metadata definitions
- `WireProto.kt` - Wire protocol definitions
- All cursor implementations (`SimpleCursor.kt`, `SimpleCsvCursor.kt`, etc.)

### Essential Libraries
- `common/collections/` - All collection implementations
- `reactor/` - Async reactor pattern implementations  
- `parse/` - Parsing utilities and JSON/CSV handlers
- `io/` - File system and I/O abstractions
- `nio/` - Non-blocking I/O implementations

## RESTORE SOURCES
Primary reference: `/Users/jim/work/Trikeshed/`

## RENORMALIZATION LOG
- 2025-06-16: Restored Series2.kt with left/right accessors and operators
- 2025-06-16: Restored complete ISAM implementation
- 2025-06-16: Restored missing cursor implementations
- 2025-06-16: Restored common collections, reactor, parse, io, nio, tilting, rl, num modules

## ARCHITECTURAL PRINCIPLES (IMMUTABLE)
1. Use `Series2<T, O>` not `Map<K, V>` 
2. Use `Series<T>` not `List<T>`
3. Use `Series.α` transforms not `MutableList`
4. Use `▶` for materialization to standard collections
5. Use `@JvmInline value class` for all primitives
6. Use `typealias` for descriptive domain types

**VIOLATION OF THESE PROTECTIONS WILL BE CONSIDERED MALICIOUS**