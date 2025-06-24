# flatton

High-performance JSON scanning library for CouchDB and document processing.

## Build

```bash
./gradlew build
```

## Usage

### JSON Cursor Creation
```kotlin
val jsonBytes = loadJsonData()
val cursor = SimdJsonScanner.createCursor(jsonBytes)
```

### CouchDB View Processing
```kotlin
val viewResponse = fetchCouchView()
val rowCursor = SimdJsonScanner.createCouchViewCursor(viewResponse)
```

### Wire Protocol Adapter
```kotlin
val adapter = JsonWireProtoAdapter()
val cursor = adapter.toCursor(jsonBytes)
```

## Features

- Fast JSON scanning using bitmap-based parsing
- CouchDB view response handling
- Cursor-based access without full deserialization
- Integration with kotlinx-serialization-scanner
- Wire protocol adapter for streaming data

## Architecture

- **SimdJsonScanner**: Core scanning engine
- **JsonObjectCursor**: Position-aware JSON access
- **JsonWireProtoAdapter**: Protocol-specific handling

## Dependencies

- kotlinx-serialization-scanner
- Trikeshed core types