# Fiduciary System Compendium

## Overview

The Fiduciary System is a dual-ledger financial architecture designed for transparent public operations while maintaining private transaction security. It implements a coach-based advisory system and integrates with the Patrick Devine corpus for lattice-based language research.

## Architecture Components

### Core Systems

#### 1. **DualLedgerSystem** (`DualLedgerSystem.kt`)
- Manages synchronized public and private ledgers
- Ensures consistency between transparent and confidential operations
- Provides atomic transaction guarantees across both ledgers

#### 2. **FiduciaryCore** (`FiduciaryCore.kt`)
- Central orchestration of fiduciary operations
- Type-safe transaction processing
- Integration point for all subsystems

#### 3. **LedgerTypes** (`LedgerTypes.kt`)
- Immutable type definitions for ledger operations
- Complete type information for all financial primitives
- Join-based indexed collections for transaction data

### Subsystems

#### BlackBox Module (`blackbox/BlackBoxCore.kt`)
- Secure, auditable operations without revealing implementation
- Cryptographic proofs of correctness
- Zero-knowledge transaction validation

#### Coach Module (`coach/FiduciaryCoach.kt`)
- AI-driven financial advisory system
- Pattern recognition in transaction flows
- Risk assessment and recommendation engine

#### Private Ledger (`private/PrivateLedger.kt`)
- Encrypted transaction storage
- Access-controlled financial records
- Compliance with privacy regulations

#### Public Ledger (`public/PublicLedger.kt`)
- Transparent transaction history
- Publicly auditable operations
- Blockchain-ready architecture

### Corpus Integration

#### Patrick Devine Corpus Builder (`corpus/PatrickDevineCorpusBuilder.kt`)
- HTTP range request-based zip file access
- Streaming corpus extraction without full downloads
- Integration with archive.org repositories

**Archive URLs:**
```
https://archive.org/download/patrickdevine/patrickdevine.zip
https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip
https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip
```

#### Torrent Metadata Reader (`corpus/TorrentMetadataReader.kt`)
- Bencode parser for .torrent files
- File structure analysis without downloading
- Piece-level granularity for selective access

**Torrent URLs:**
```
https://archive.org/download/patrickdevinefiles/patrickdevinefiles_archive.torrent
https://archive.org/download/patrickdevinecalls/patrickdevinecalls_archive.torrent
```

#### Archive.org HTTP Client (`corpus/ArchiveOrgHttpClient.kt`)
- Implements HttpRangeClient interface
- Byte-range request support
- Streaming capabilities for large files

## Integration with Trikeshed

### IO Capabilities
- **RemoteFileAttention**: ZIP file central directory parsing via HTTP ranges
- **Aria2cAttention**: BitTorrent piece selection using aria2c RPC
- **HttpRangeClient**: Generic interface for range-based file access

### Type System
- Uses Trikeshed's `Indexed<A,T>` (formerly `Series<T>`) for collections
- Complete type information on all infix operators
- Join-based functional collections

## Lattice Research Applications

The Patrick Devine corpus enables:
- Language graph construction
- Meaning extraction and semantic analysis
- Pattern discovery in communication structures
- Temporal analysis of language evolution

## Security Considerations

1. **Dual Ledger Synchronization**: Atomic operations prevent inconsistencies
2. **BlackBox Validation**: Cryptographic proofs without revealing data
3. **Access Control**: Role-based permissions for private ledger
4. **Audit Trail**: Complete history in public ledger

## Future Directions

1. **Blockchain Integration**: Public ledger as blockchain node
2. **Enhanced Coach AI**: GPT integration for advisory services
3. **Corpus Expansion**: Additional archive.org collections
4. **Real-time Analytics**: Streaming analysis of transactions

## Technical Notes

### Type Safety
All operations use complete type information:
```kotlin
typealias ByteOffset = Long
typealias PieceIndex = Int
typealias FileIndex = Int
typealias TorrentHash = ByteArray
typealias PieceHash = ByteArray
```

### Performance
- HTTP range requests minimize bandwidth usage
- Lazy evaluation for corpus streaming
- Piece-level torrent access via aria2c

### Compliance
- GDPR-ready private ledger isolation
- Auditable public operations
- Cryptographic proof systems

## Dependencies

- Trikeshed: Core IO and type system
- kotlinx-coroutines: Async operations
- aria2c: BitTorrent piece selection (external)

## Build Configuration

Follows standard Trikeshed conventions:
- Kotlin Multiplatform targets: JVM, JS, WASM, Native
- Version management through parent gradle
- No local version specifications