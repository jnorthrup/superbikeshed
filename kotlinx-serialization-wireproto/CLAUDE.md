# Kotlinx Serialization WireProto Project Instructions

## Wire Protocol Serialization

This project implements wire protocol serialization using kotlinx-serialization.

## Protocol Data Structures

- Use `Indexed<T>` for protocol message sequences
- Use `Join<A,B>` for field-to-value mappings in messages
- Follow TrikeShed patterns for wire format representations
- Prefer byte arrays and primitive types for wire efficiency

## Wire Protocol Guidelines

- **Message Format**: TrikeShed data structures for protocol messages
- **Field Mapping**: Join-based field organization
- **Serialization**: Efficient byte-level serialization
- **Performance**: for loops for protocol processing

## Development Architecture

- Follow global museum preservation rules
- Use TrikeShed type system consistently
- Avoid List<T> and Pair<A,B> in protocol code
- Maintain compatibility with kotlinx-serialization standards
- Defer to superbikeshed gradle for build configuration