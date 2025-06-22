# Kotlinx Serialization Scanner Project Instructions

## Serialization Scanning Architecture

This project scans and analyzes kotlinx-serialization usage patterns.

## Data Structure Patterns

- Use `Indexed<T>` for serialization metadata collections
- Use `Join<A,B>` for type-to-serializer mappings
- Follow TrikeShed type system for scan result representations
- Prefer functional data structures for immutable scan results

## Scanning Guidelines

- **Type Analysis**: Indexed-based type collection and analysis
- **Serializer Mapping**: Join for type-to-serializer relationships
- **Performance**: for loops for scanning performance
- **Results**: TrikeShed data structures for output

## Development Patterns

- Follow global SuperBikeShed architectural guidelines
- Use museum preservation for scanning algorithms
- Avoid standard library collections in favor of TrikeShed types
- Defer to parent gradle configuration for dependencies