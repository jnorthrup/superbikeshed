# Fiduciary Attention Architecture

## Overview

Fiduciary implements a normalized attention mechanism for document processing, integrating with Trikeshed's attention system while adding document-specific capabilities.

## Core Architecture

### Normalized Attention
All attention reduces to `Twin<Long>` (start j end), providing a unified byte range representation across all document types and access methods.

### Key Components

1. **DocumentAttention** - `Join<NormalizedAttention, String>` (range j mimeType)
2. **CorpusAttention** - `Join<Indexed<DocumentAttention>, String>` (documents j corpusId)
3. **FiduciaryContext** - `Join<IOContext, Join<Metadata, ParseContext>>`

## Key Archive.org Collections

### 1. Patrick Devine Corpus
Primary fiduciary interest - whistleblower documents and communications

**ZIP Archives:**
- Main: `https://archive.org/download/patrickdevine/patrickdevine.zip`
- Calls: `https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip`
- Files: `https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip`

**Torrent Distribution:**
- Files: `https://archive.org/download/patrickdevinefiles/patrickdevinefiles_archive.torrent`
- Calls: `https://archive.org/download/patrickdevinecalls/patrickdevinecalls_archive.torrent`

### 2. 1215.org Common Law Archive
Secondary fiduciary interest - common law documents and rights

**Access Methods:**
- Tree: `https://archive.org/download/1215-org-commonlaw/www.1215.org/` (full directory tree)
- ZIP: `https://archive.org/download/1215-org-commonlaw/1215-org-commonlaw.zip`
- Torrent: `https://archive.org/download/1215-org-commonlaw/1215-org-commonlaw_archive.torrent`

## Document Processing Pipeline

### 1. Extraction (Apache Tika)
```kotlin
suspend inline fun DocumentAttention.extract(source: TikaSource): String
```
- PDF extraction
- ZIP entry processing
- Text extraction from various formats

### 2. Analysis (Stanford NLP)
```kotlin
suspend inline fun DocumentAttention.analyze(source: NLPSource): Indexed<String>
```
- Named entity recognition
- Concept extraction
- Relationship mapping

### 3. OCR (Tesseract)
```kotlin
suspend inline fun DocumentAttention.ocr(source: OCRSource): String
```
- Image to text conversion
- Multi-language support

### 4. Transcription (Whisper)
```kotlin
suspend inline fun DocumentAttention.transcribe(source: AudioSource): String
```
- Audio to text (MP3 → WAV → text)
- Configurable sample rates

## Access Optimization

### HTTP Range Requests
- Efficient partial file access
- No full downloads required
- Supported for ZIP archives

### Torrent Distribution
- Peer-to-peer resilience
- Piece-based access
- DHT integration

### Tree Traversal
- Direct directory access for 1215.org
- Hierarchical document structure
- Path-based navigation

## Integration Points

### Trikeshed Attention
```kotlin
fun FiduciaryAttention.toContextual(): ContextualAttention
```
Seamless conversion to Trikeshed's attention system

### Nexus Compatibility
- Coroutine-based async operations
- Attention scheduling
- Priority management

### IOContext Support
- Platform-specific optimizations
- io_uring on Linux
- kqueue on macOS
- NIO fallback

## Usage Example

```kotlin
suspend fun processCorpus(ioContext: IOContext) {
    val fidContext = FiduciaryContext(/* ... */)
    
    // Patrick Devine corpus
    val patrickCorpus = fidContext.patrickDevine(
        useRangeRequests = true,
        includeTorrents = false
    )
    
    // Common Law archive
    val commonLaw = fidContext.commonLaw1215(
        useTree = true,
        useTorrent = false
    )
    
    // Process with attention
    for (doc in patrickCorpus) {
        val text = doc.extract(tikaSource)
        val concepts = doc.analyze(nlpSource)
        // Further processing...
    }
}
```

## Performance Characteristics

- **Zero-overhead inline classes** - All attention types compile to primitives
- **Compile-time double dispatch** - No virtual method calls
- **Efficient range requests** - Only fetch needed bytes
- **Torrent piece selection** - Optimize for available peers
- **Tree structure caching** - Reuse directory listings

## Future Enhancements

1. **Anna's Archive Integration** - Expand to academic papers
2. **IPFS Support** - Content-addressed storage
3. **Enhanced DHT** - Distributed metadata
4. **GPU OCR** - Accelerated text extraction
5. **Real-time transcription** - Streaming audio processing