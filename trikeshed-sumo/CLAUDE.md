# SUMO Curation Module

## Overview

This module provides SUMO (Suggested Upper Merged Ontology) curation capabilities with a KIF (Knowledge Interchange Format) scanner/parser using SIMD-style bitmap operations for high-performance ontology processing.

## Key Features

### 1. KIF Scanner/Parser with SIMD Bitmap Operations
- **SIMD-accelerated parsing**: Uses vectorized operations for parallel character classification
- **Bitmap-based structural detection**: Efficient structural element detection using bitmaps
- **Register-at-a-time scanning**: Leverages bbcursive patterns for optimal performance
- **Autovec optimization**: Automatically selects optimal scanning strategy based on data characteristics

### 2. SUMO Ontology Curation
- **Remote zip index curation**: Downloads and processes SUMO ontology files from remote sources
- **Ontology validation**: Validates SUMO structure and relationships
- **Incremental updates**: Supports incremental ontology updates and versioning
- **Cross-platform compatibility**: Works on JVM, JS, and native platforms

### 3. Performance Optimizations
- **SIMD vector operations**: 256-bit and 512-bit vector support for parallel processing
- **Bitmap-based indexing**: Fast structural element lookup using bitmap operations
- **Memory-efficient processing**: Streaming processing for large ontology files
- **Platform-specific optimizations**: Native SIMD instructions where available

## Architecture

### Core Components

1. **KIF Scanner**: SIMD-accelerated scanner for KIF syntax
2. **SUMO Curator**: Remote ontology curation and management
3. **Bitmap Engine**: SIMD bitmap operations for structural detection
4. **Ontology Validator**: SUMO structure and relationship validation

### SIMD Strategy

The module implements adaptive SIMD strategies that scale with available vector width:
- **128-bit**: ARM NEON, SSE4.2, WASM SIMD
- **256-bit**: AVX2
- **512-bit**: AVX-512
- **Scalable**: ARM SVE, RISC-V V

## Usage

### Basic SUMO Curation

```kotlin
// Create SUMO curator
val curator = SumoCurator()

// Curate from remote zip index
val ontology = curator.curateFromRemote("https://example.com/sumo.zip")

// Process with SIMD-accelerated KIF parser
val parser = KifSimdParser()
val parsedOntology = parser.parse(ontology)
```

### SIMD Bitmap Operations

```kotlin
// Create SIMD bitmap scanner
val scanner = SimdBitmapScanner(input)

// Scan for structural elements
val structuralBitmap = scanner.scanStructural()

// Extract coordinates
val coordinates = scanner.extractCoordinates()
```

## Implementation Notes

- Uses bbcursive patterns for register-at-a-time scanning
- Implements autovec optimization for automatic strategy selection
- Supports cross-platform SIMD operations
- Memory-efficient streaming for large ontologies
- Type-safe ontology representation using Kotlin data classes

## Dependencies

- `trikeshed-lib`: Core library functionality
- `trikeshed-common`: Common utilities
- `trikeshed-cursor`: Cursor-based operations
- `kotlinx-serialization`: JSON serialization
- `kotlinx-coroutines`: Asynchronous processing 