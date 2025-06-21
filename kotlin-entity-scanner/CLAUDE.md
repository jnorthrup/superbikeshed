# Kotlin Entity Scanner Project Instructions

## Entity Scanning Patterns

This project provides entity scanning capabilities for Kotlin multiplatform code.

## Data Structure Guidelines

- Use `Series<T>` for entity collections and scan results
- Use `Join<A,B>` for entity-to-metadata mappings
- Follow TrikeShed type system patterns for entity representations
- Prefer primitive arrays for high-performance scanning

## Scanning Architecture

- **Entity Detection**: Series-based entity collection
- **Metadata Mapping**: Join for entity attributes
- **Result Processing**: TrikeShed data structure outputs
- **Performance**: for loops for scanning algorithms

## Development Guidelines

- Follow global museum preservation rules
- Use TrikeShed patterns consistently
- Avoid List<T> and Pair<A,B> in favor of Series and Join
- Defer to superbikeshed gradle for build configuration