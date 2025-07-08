# Patrick Devine Agent TDD Implementation

## Overview

The Patrick Devine Agent is a comprehensive conversation processing system that:

1. **Collects conversation texts** from multiple sources (Archive.org, local files, APIs, databases)
2. **Runs Whisper microdiarization transcripts** for audio processing
3. **Maintains a CouchDB cache** for persistent storage
4. **Integrates with k2script sandboxes** for secure execution
5. **Provides automatic git integration** for version control

## Architecture

### Core Components

- **PatrickDevineAgent**: Main agent class with all functionality
- **PatrickDevineK2Script**: K2Script sandbox integration
- **PatrickDevineTypes**: Data classes and configuration types
- **TDD Tests**: Comprehensive test suite in `tests/tdd/PatrickDevineAgentTDDTest.kt`

### Data Flow

```
Conversation Sources → Collection → Preprocessing → Whisper Transcription → CouchDB Cache → Git Repository
                                    ↓
                              K2Script Sandboxes
```

## Features

### 1. Conversation Text Collection

Supports multiple source types:
- **Archive.org**: Web archive content
- **Local Files**: Text files, markdown, JSON
- **API Endpoints**: REST APIs with authentication
- **Databases**: Direct database connections

```kotlin
val sources = listOf(
    ConversationSource(
        id = "patrick_devine_archive",
        type = SourceType.ARCHIVE_ORG,
        url = "https://archive.org/details/patrick-devine-corpus"
    ),
    ConversationSource(
        id = "local_transcripts",
        type = SourceType.LOCAL_FILES,
        path = "/data/transcripts"
    )
)

val agent = PatrickDevineAgent()
val conversations = agent.collectConversationTexts(sources).getOrThrow()
```

### 2. Whisper Microdiarization

Automatic speech-to-text with speaker identification:

```kotlin
val audioFile = AudioFile(
    id = "patrick_interview_001",
    path = "/data/audio/patrick_interview_001.wav",
    format = AudioFormat.WAV,
    sampleRate = 16000,
    channels = 1,
    duration = 300.0
)

val whisperConfig = WhisperConfig(
    model = "ggml-small.en-tdrz.bin",
    language = "en",
    diarization = true,
    timestampFormat = TimestampFormat.HH_MM_SS
)

val transcript = agent.runWhisperTranscription(audioFile, whisperConfig).getOrThrow()
```

### 3. CouchDB Cache Management

Persistent storage with bulk operations:

```kotlin
val couchConfig = CouchDBConfig(
    url = "http://localhost:5984",
    database = "patrick_devine_cache",
    username = "admin",
    password = "password"
)

// Store conversation
val storedId = agent.storeConversationInCache(conversation, couchConfig).getOrThrow()

// Retrieve conversation
val retrieved = agent.retrieveConversationFromCache(storedId, couchConfig).getOrThrow()

// Bulk operations
val storedIds = agent.bulkStoreConversations(conversations, couchConfig).getOrThrow()
val retrieved = agent.bulkRetrieveConversations(storedIds, couchConfig).getOrThrow()
```

### 4. K2Script Sandbox Integration

Secure execution in isolated environments:

```kotlin
val k2scriptConfig = K2ScriptConfig(
    sandboxDir = "/tmp/patrick_agent_sandbox",
    isolationLevel = IsolationLevel.PROCESS,
    resourceLimits = ResourceLimits(
        maxMemory = "2GB",
        maxCpu = 2,
        maxDisk = "10GB"
    )
)

val operation = AgentOperation(
    id = "op_001",
    type = OperationType.TRANSCRIPTION,
    input = mapOf(
        "audio_file" to "/data/audio/patrick_001.wav",
        "model" to "ggml-small.en-tdrz.bin"
    )
)

val result = agent.executeInK2ScriptSandbox(operation, k2scriptConfig).getOrThrow()
```

### 5. Automatic Git Integration

Version control for conversation data:

```kotlin
val gitConfig = GitConfig(
    repositoryPath = "/tmp/patrick_repo",
    branch = "main",
    authorName = "Patrick Devine Agent",
    authorEmail = "agent@patrickdevine.com",
    autoCommit = true,
    commitMessageTemplate = "Add conversation batch {batch_id} from {source}",
    initializeIfNotExists = true
)

val conversationData = ConversationData(
    id = "batch_001",
    conversations = conversations,
    metadata = mapOf("source" to "archive", "timestamp" to Clock.System.now().toString())
)

val commitInfo = agent.commitConversationData(conversationData, gitConfig).getOrThrow()
```

## Complete Pipeline

Run the entire processing pipeline:

```kotlin
val pipelineConfig = PatrickDevinePipelineConfig(
    sources = sources,
    whisperConfig = whisperConfig,
    couchConfig = couchConfig,
    k2scriptConfig = k2scriptConfig,
    gitConfig = gitConfig
)

val result = agent.runCompletePipeline(pipelineConfig).getOrThrow()

println("Conversations collected: ${result.collectionResult.conversationsCollected}")
println("Transcripts generated: ${result.transcriptionResult.transcriptsGenerated}")
println("Documents stored: ${result.cacheResult.documentsStored}")
println("Git commit: ${result.gitResult.commitHash}")
```

## Text Preprocessing

The agent includes sophisticated text preprocessing for VTT files:

```kotlin
val rawText = """
    [00:00:01.000 --> 00:00:05.000] Hello, this is Patrick Devine speaking.
    [00:00:05.000 --> 00:00:08.000] Hello, this is Patrick Devine speaking about legal matters.
    [00:00:08.000 --> 00:00:12.000] Hello, this is Patrick Devine speaking about legal matters and fiduciary duty.
""".trimIndent()

val cleanedText = agent.preprocessConversationText(rawText)
// Result: "00:00:01 Hello, this is Patrick Devine speaking about legal matters and fiduciary duty."
```

## Testing

### Running TDD Tests

The comprehensive test suite is located in `tests/tdd/PatrickDevineAgentTDDTest.kt`:

```bash
# Run all Patrick Devine agent tests
./gradlew test --tests "tests.tdd.PatrickDevineAgentTDDTest"

# Run specific test
./gradlew test --tests "tests.tdd.PatrickDevineAgentTDDTest.should collect conversation texts from multiple sources"
```

### Test Categories

1. **Conversation Collection Tests**: Verify multi-source collection
2. **Whisper Transcription Tests**: Test audio processing and diarization
3. **CouchDB Cache Tests**: Verify storage and retrieval operations
4. **K2Script Sandbox Tests**: Test secure execution environments
5. **Git Integration Tests**: Verify version control functionality
6. **Integration Tests**: End-to-end pipeline validation

### Quick Test Runner

Use the standalone test runner for quick verification:

```bash
cd fiduciary
kotlin test-patrick-devine-agent.kts
```

## Configuration

### Environment Variables

```bash
export COUCHDB_URL="http://localhost:5984"
export COUCHDB_USERNAME="admin"
export COUCHDB_PASSWORD="password"
export WHISPER_MODEL_PATH="/models/ggml-small.en-tdrz.bin"
export K2SCRIPT_SANDBOX_DIR="/tmp/patrick_sandboxes"
export GIT_REPO_PATH="/data/patrick_conversations"
```

### Configuration Files

Create configuration files for different environments:

```json
{
  "sources": [
    {
      "id": "patrick_archive",
      "type": "ARCHIVE_ORG",
      "url": "https://archive.org/details/patrick-devine-corpus"
    }
  ],
  "whisper": {
    "model": "ggml-small.en-tdrz.bin",
    "language": "en",
    "diarization": true
  },
  "couchdb": {
    "url": "http://localhost:5984",
    "database": "patrick_devine_cache"
  },
  "k2script": {
    "sandboxDir": "/tmp/patrick_sandboxes",
    "isolationLevel": "PROCESS"
  },
  "git": {
    "repositoryPath": "/data/patrick_repo",
    "branch": "main",
    "autoCommit": true
  }
}
```

## Deployment

### Prerequisites

1. **CouchDB**: Running instance for cache storage
2. **Whisper Models**: Downloaded and available
3. **K2Script**: Installed and configured
4. **Git**: Available for version control
5. **Kotlin**: Runtime environment

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd v2superbikeshed

# Build the project
./gradlew build

# Run tests
./gradlew test --tests "tests.tdd.PatrickDevineAgentTDDTest"
```

### Docker Deployment

```dockerfile
FROM openjdk:17-jdk-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy application
COPY fiduciary/build/libs/fiduciary-*.jar /app/fiduciary.jar

# Run the agent
CMD ["java", "-jar", "/app/fiduciary.jar"]
```

## Monitoring and Logging

### Metrics

The agent provides metrics for:
- Conversations collected per source
- Transcription processing time
- Cache hit/miss rates
- Sandbox execution statistics
- Git commit frequency

### Logging

Configure logging levels:

```kotlin
// Enable debug logging
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")

// Enable specific component logging
System.setProperty("fiduciary.agents.logLevel", "INFO")
```

## Security Considerations

### Sandbox Isolation

- Process-level isolation for basic security
- Container isolation for enhanced security
- VM isolation for maximum security

### Data Privacy

- All conversation data is stored locally by default
- CouchDB can be configured with authentication
- Git repositories can be private

### Resource Limits

- Memory limits prevent resource exhaustion
- CPU limits ensure fair sharing
- Disk limits prevent storage abuse

## Troubleshooting

### Common Issues

1. **CouchDB Connection Failed**
   - Verify CouchDB is running
   - Check credentials and permissions
   - Ensure database exists

2. **Whisper Model Not Found**
   - Download the required model
   - Verify model path configuration
   - Check file permissions

3. **K2Script Sandbox Failure**
   - Verify k2script installation
   - Check sandbox directory permissions
   - Review resource limits

4. **Git Operations Failed**
   - Verify git installation
   - Check repository permissions
   - Ensure git configuration is correct

### Debug Mode

Enable debug mode for detailed logging:

```kotlin
val agent = PatrickDevineAgent()
agent.enableDebugMode()

// Run operations with detailed logging
val result = agent.runCompletePipeline(config)
```

## Future Enhancements

### Planned Features

1. **Real-time Processing**: Stream processing capabilities
2. **Advanced Diarization**: Multi-speaker identification
3. **Content Analysis**: Sentiment and topic analysis
4. **Distributed Processing**: Multi-node execution
5. **API Endpoints**: REST API for external integration

### Extensibility

The agent is designed for easy extension:

```kotlin
// Add custom source type
class CustomSource : ConversationSource {
    override suspend fun collect(): List<ConversationText> {
        // Custom collection logic
    }
}

// Add custom processing step
class CustomProcessor : ProcessingStep {
    override suspend fun process(conversation: ConversationText): ConversationText {
        // Custom processing logic
    }
}
```

## Contributing

### Development Workflow

1. Write failing tests (TDD approach)
2. Implement minimal functionality to pass tests
3. Refactor and improve implementation
4. Add integration tests
5. Update documentation

### Code Standards

- Follow Kotlin coding conventions
- Use TDD for all new features
- Maintain comprehensive test coverage
- Document public APIs
- Use meaningful commit messages

## License

This project is licensed under the Apache License 2.0. See LICENSE file for details. 