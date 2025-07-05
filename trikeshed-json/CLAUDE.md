# TrikeShed JSON - Dual JSON Implementation

## Architecture

This provides **two TrikeShed JSON implementations** to suit different needs:

1. **Bitmap Scanner** (`TrikeShedJson.Default`) - Full-featured with parallel scanning
2. **Simple Scanner** (`TrikeShedJson.Simple`) - Lightweight without bitmap overhead

## Core Purpose

- **Primary**: High-performance JSON parsing with TrikeShed core types
- **Dual Options**: Bitmap scanning for complex needs, simple parsing for performance
- **CoreTypes**: Uses Join<A,B>, Indexed<T>, and ArrayLike patterns exclusively

## Data Structure Patterns

- Use `Indexed<T>` for JSON collections 
- Use `Join<A,B>` for structural relationships
- Use `ArrayLike` trait for clean [index] syntax
- Direct ByteArray processing - no string conversions

## Implementation Guidelines

### Bitmap Scanner (TrikeShedJson.Default)
- **Performance**: Bitmap-accelerated scanning with parallel processing
- **Features**: Fingerprinting, isomorphism detection, coordinate-based access
- **Use Case**: Complex JSON, large datasets, structural analysis

### Simple Scanner (TrikeShedJson.Simple)  
- **Performance**: Lightweight linear scanning, minimal overhead
- **Features**: Direct property extraction, simple queries
- **Use Case**: Basic JSON parsing, memory-constrained environments

### Common Patterns
- **Types**: TrikeShed core types only (Join<A,B>, Indexed<T>)
- **Access**: Clean [index] syntax via ArrayLike traits
- **No Dependencies**: Self-contained JSON implementations

## Development Patterns

- Follow global SuperBikeShed architectural guidelines
- Use ArrayLike trait for get[i] operators  
- Avoid standard library collections in favor of TrikeShed types
- Direct byte processing without intermediate conversions