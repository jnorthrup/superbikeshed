# Fiduciary Document Processing System

## Overview

The Fiduciary system provides sophisticated document processing capabilities using attention mechanisms, HTTP range requests, and integration with Apache Tika, Stanford NLP, Tesseract OCR, and Whisper.cpp for audio transcription.

## Architecture

### Core Components

1. **Attention-based Access**: Uses HTTP range requests as "attention keyholes" to read specific portions of large archives without downloading entire files.

2. **Document Processing Pipeline**:
   - **Apache Tika**: Document parsing and metadata extraction
   - **Stanford NLP**: Natural language processing and concept extraction
   - **Tesseract OCR**: Optical character recognition for images
   - **Whisper.cpp**: Audio transcription (MP3→WAV→text)

3. **Corpus Management**: Specialized handling for fiduciary archives including Patrick Devine corpus and 1215.org Common Law documents.

## Patrick Devine Corpus

The Patrick Devine corpus consists of legal and maritime documents available on archive.org:

### Archive Files

1. **Main Archive**: `https://archive.org/download/patrickdevine/patrickdevine.zip`
2. **Calls Archive**: `https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip`
3. **Files Archive**: `https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip`

### Torrent Support

- Files Torrent: `https://archive.org/download/patrickdevinefiles/patrickdevinefiles_archive.torrent`
- Calls Torrent: `https://archive.org/download/patrickdevinecalls/patrickdevinecalls_archive.torrent`

### Processing Strategy

1. **ZIP Central Directory Reading**: Uses HTTP range requests to read only the ZIP central directory, avoiding full file downloads.

2. **Selective Entry Processing**: Filters for documents of interest (PDF, DOC, TXT, HTML, audio, images).

3. **Attention-based Extraction**: Creates DocumentAttention objects that reference byte ranges within archives.

## 1215.org Common Law Archive

The Common Law archive provides historical legal documents:

### Access Methods

1. **Tree Structure**: `https://archive.org/download/1215-org-commonlaw/www.1215.org/`
   - Direct navigation of directory structure
   - Preserves original organization

2. **ZIP Archive**: `https://archive.org/download/1215-org-commonlaw/1215-org-commonlaw.zip`
   - Complete archive download
   - Suitable for offline processing

3. **Torrent**: `https://archive.org/download/1215-org-commonlaw/1215-org-commonlaw_archive.torrent`
   - Distributed access
   - DHT-based attention mechanisms

## Document Types and Processing

### Supported MIME Types

- **Text**: text/plain, text/html
- **Documents**: application/pdf, application/msword, application/vnd.openxmlformats
- **Audio**: audio/mpeg (MP3), audio/wav
- **Images**: image/jpeg, image/png

### Processing Capabilities

1. **Text Extraction**:
   ```kotlin
   val text = document.extract(tikaSource)
   ```

2. **Concept Analysis**:
   ```kotlin
   val concepts = document.analyze(nlpSource)
   ```

3. **OCR Processing**:
   ```kotlin
   val ocrText = document.ocr(ocrSource)
   ```

4. **Audio Transcription**:
   ```kotlin
   val transcript = document.transcribe(audioSource)
   ```

## Apache Tika Configuration

### Heap Management

Tika requires significant heap space for processing large documents:

```xml
<properties>
  <tika.config>tika-config.xml</tika.config>
  <java.heap.max>4g</java.heap.max>
</properties>
```

### Parser Configuration

Custom parser configuration for specific document types:

```xml
<parsers>
  <parser class="org.apache.tika.parser.pdf.PDFParser">
    <params>
      <param name="extractInlineImages" type="bool">true</param>
      <param name="extractUniqueInlineImagesOnly" type="bool">true</param>
    </params>
  </parser>
</parsers>
```

## Stanford NLP Integration

### Blackboard Architecture

The system uses a blackboard pattern for concept extraction:

1. **Document Analysis**: Initial parsing and tokenization
2. **Entity Recognition**: Person, Organization, Location extraction
3. **Concept Mapping**: Domain-specific concept identification
4. **Relationship Extraction**: Legal and maritime relationships

### Configuration

```kotlin
val nlpConfig = mapOf(
    "annotators" to "tokenize,ssplit,pos,lemma,ner,parse",
    "ner.model" to "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz",
    "parse.model" to "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz"
)
```

## Tesseract OCR Configuration

### Language Support

- Primary: English (eng)
- Legal documents: Latin (lat)
- Historical texts: Old English (enm)

### Processing Pipeline

1. Image preprocessing (deskew, denoise)
2. Text detection and segmentation
3. Character recognition
4. Post-processing and correction

## Whisper.cpp Audio Transcription

### Audio Processing Pipeline

1. **Format Conversion**: MP3 → WAV (16kHz, mono)
2. **Segmentation**: Split into 30-second chunks
3. **Transcription**: Using Whisper model
4. **Post-processing**: Timestamp alignment and formatting

### Model Selection

- Small model for general transcription
- Medium model for legal terminology
- Large model for historical recordings

## Usage Examples

### Basic Corpus Processing

```kotlin
val processor = PatrickDevineProcessor(httpClient, ioContext)
val corpus = processor.processCorpus()
println("Processed ${corpus.a} documents")
```

### Concept Extraction

```kotlin
val concepts = docProcessor.extractConcepts(corpus)
for (i in 0 until concepts.a) {
    val concept = concepts.b(i)
    println("Document: ${concept.concept.a}")
    println("Concepts: ${concept.concept.b.toList()}")
}
```

### Range-based Access

```kotlin
// Read specific document from archive
val entry = ZipEntry(
    name = "legal/maritime-law.pdf",
    offset = 1024000L,
    compressedSize = 50000L
)

val content = httpClient.executeRange(
    archiveUrl,
    entry.offset,
    entry.offset + entry.compressedSize - 1
)
```

## Performance Considerations

1. **HTTP Range Requests**: Minimize data transfer by reading only required byte ranges
2. **Parallel Processing**: Process multiple archive entries concurrently
3. **Caching**: Cache ZIP central directories and frequently accessed entries
4. **Memory Management**: Stream large documents rather than loading entirely

## Future Enhancements

1. **Anna's Archive Integration**: Extend to process Anna's Archive collections
2. **Distributed Processing**: Leverage torrent DHT for distributed attention
3. **Semantic Search**: Build searchable index with embeddings
4. **Legal Ontology**: Develop domain-specific concept hierarchies
5. **Provenance Tracking**: Maintain chain of custody for legal documents

## Security Considerations

1. **Archive Verification**: Verify ZIP CRC32 checksums
2. **Content Validation**: Validate MIME types and file structures
3. **Access Control**: Implement role-based access for sensitive documents
4. **Audit Logging**: Track all document access and processing

## Conclusion

The Fiduciary system provides a comprehensive framework for processing large document archives using attention mechanisms and HTTP range requests. By avoiding full file downloads and focusing processing on specific byte ranges, it enables efficient analysis of massive corpora while maintaining document integrity and provenance.