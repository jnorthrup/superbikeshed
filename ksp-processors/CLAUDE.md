# KSP Processors Project Instructions

## Kotlin Symbol Processing Architecture

This project implements KSP (Kotlin Symbol Processing) processors for code generation.

## Processor Data Structures

- Use `Series<T>` for symbol collections and processing queues
- Use `Join<A,B>` for symbol-to-metadata mappings
- Follow TrikeShed patterns for processor state management
- Prefer functional data structures for immutable processing

## Processing Guidelines

- **Symbol Analysis**: Series-based symbol collection and analysis
- **Code Generation**: TrikeShed data structures for generation templates
- **Metadata Mapping**: Join for symbol attributes and annotations
- **Performance**: for loops for processor algorithms

## Development Patterns

- Follow global SuperBikeShed architectural guidelines
- Use museum preservation for processor implementations
- Avoid standard collections in favor of TrikeShed types
- Generate code that follows TrikeShed patterns
- Defer to superbikeshed gradle for processor configuration