# Fiduciary Modular Components

This project is composed of independently controlled, importable subprojects. Each is resource-bounded, manually configured, and can be composed as needed for a given workflow.

## Subprojects

- **tika-attention/**: Apache Tika + OCR (Tesseract or better), XML configuration, heap management, PDF/Office/image extraction.
- **stanford-nlp-blackboard/**: NLP/graph curation, subject/concept lattice, entity extraction, Kotlin entity scanner patterns.
- **audio-transcription/**: Whisper.cpp or similar, MP3→WAV→text, streaming/batch, language packs.
- **torrent-dht-attention/**: Piece-level HTTP/torrent access, catalog-at-ingest, minimal caching.
- **concept-lattice-store/**: Taxonomical, cross-document, temporal knowledge graph.
- **document-orchestrator/**: Lightweight coordinator, explicit manual control over which components are loaded and how they interact.

## Key Patterns

- **Manual import/on-demand**: Only load what's needed for a given workflow. No auto-wiring.
- **Resource isolation**: JVM heap, native memory, and CPU limits per component.
- **Explicit configuration**: XML/graphical config for Tika, scriptable for OCR/translation, etc.
- **Inline double dispatch**: All attention/predication logic uses compile-time, type-safe double dispatch, normalized to `Twin<Long>` for all range/predicate types.

See `FIDUCIARY_DOCUMENT_PROCESSING.md` for full architecture and integration details. 