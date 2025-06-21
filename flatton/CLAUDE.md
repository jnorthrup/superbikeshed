# Flatton Project Instructions

## JSON Scanner Modernization Priority

**Current Issue**: The `SimdJsonScanner.kt` is a "simplified demonstration" with "crude checks." While it avoids full deserialization (which is clever), the implementation is not robust.

### **Critical Proposal: Replace with kotlinx-serialization-scanner**
- **Deprecate and replace** `flatton`'s `SimdJsonScanner` and `JsonWireProtoAdapter` entirely
- **Refactor `FlattonService`'s `queryViewAsCursor`** to use `BitmapJsonDecoder` from serialization scanner
- **Benefits**: Same "cursor-like" benefit without full deserialization but with robust, reusable, performant engine
- **Superior Solution**: The `kotlinx-serialization-scanner` uses bitmap scanning for lightning-fast JSON navigation

## TypeScript/Kotlin Bridge Patterns

Flatton provides TypeScript interoperability with Kotlin multiplatform code.

## Cross-Platform Data Structures

- Map Kotlin `Series<T>` to TypeScript arrays with indexing semantics
- Bridge `Join<A,B>` to TypeScript Map or Record types
- Ensure type safety across language boundaries
- Follow TrikeShed patterns in TypeScript where possible

## Development Guidelines

- Maintain TrikeShed architectural patterns in TypeScript layer
- Use functional programming patterns consistent with Kotlin side
- Prefer immutable data structures in TypeScript
- Follow global museum preservation rules

## Build Integration

- Defer to superbikeshed gradle for version management
- Support WASM compilation targets
- Coordinate with Kotlin multiplatform build system
- Use consistent build flags across platforms

## Type Mapping Strategy

- Kotlin Series ↔ TypeScript ReadonlyArray with custom indexing
- Kotlin Join ↔ TypeScript Record or Map
- Kotlin primitives ↔ TypeScript primitives
- Maintain semantic equivalence across boundaries