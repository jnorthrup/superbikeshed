# NVIDIA Tasker Consolidation

## Overview

The NVIDIA tasker functionality has been consolidated from multiple sources into a unified implementation in `NvidiaTasker`.

## Consolidated Components

### 1. NvidiaAgent
- Original location: `nexus/src/commonMain/kotlin/nexus/agent/NvidiaAgent.kt`
- Features integrated:
  - FSM-based agent states
  - Tool system (file operations, time)
  - Coordinate-based file editing
  - File interaction analysis
  - API key rotation

### 2. NvidiaClient
- Original location: `nexus/src/jvmMain/kotlin/nexus/ai/NvidiaClient.kt`
- Features integrated:
  - HTTP client for NVIDIA API
  - Chat completion endpoints
  - Simple chat interface

### 3. NemotronProvider
- Original location: `nexus/src/jvmMain/kotlin/nexus/ai/providers/NemotronProvider.kt`
- Features integrated:
  - Multiple API support (NVIDIA, Hugging Face)
  - Environment-based configuration
  - LLM provider interface compatibility

## New Unified Architecture

### NvidiaTasker
Located at: `nexus/src/commonMain/kotlin/nexus/agent/NvidiaTasker.kt`

Key improvements:
1. **Unified Configuration**: Single `TaskerConfig` class manages all settings
2. **Task-based API**: Clear task types for different operations
3. **Batch Processing**: Support for executing multiple tasks
4. **Platform Abstraction**: Common interface with platform-specific implementations
5. **Better Error Handling**: Consistent state management and error reporting

### Task Types
- `Chat`: AI conversations with optional system prompts
- `FileEdit`: Coordinate-based file editing operations
- `ToolExecution`: Execute predefined tools
- `Analysis`: Analyze file interactions and dependencies
- `Batch`: Execute multiple tasks in sequence

### Usage Example

```kotlin
// Create tasker with default config
val tasker = NvidiaTasker()

// Simple chat
val chatTask = NvidiaTasker.chat("What is the weather like?")
val result = tasker.execute(chatTask)

// File editing
val editTask = NvidiaTasker.edit(
    NvidiaTasker.EditInstruction.Insert(
        filePath = "example.kt",
        afterLine = 10,
        content = "// New comment"
    )
)
val editResult = tasker.execute(editTask)

// Batch operations
val batchTask = NvidiaTasker.batch(
    NvidiaTasker.chat("Analyze this code"),
    NvidiaTasker.analyze("src/main.kt"),
    NvidiaTasker.edit(instruction)
)
val batchResult = tasker.execute(batchTask)
```

## API Key Management

The tasker supports multiple API keys with automatic rotation:
1. Environment variables: `NVIDIA_API_KEY`, `NVIDIA_API_KEY_2`, `NVIDIA_API_KEY_3`
2. Hugging Face token: `HF_TOKEN` (enables HF endpoint)
3. Default keys included for testing

## Platform Support

- **Common**: Core logic in `NvidiaTasker.kt`
- **JVM**: HTTP client and file operations in `NvidiaTaskerJvm.kt`
- **Other platforms**: Can add platform-specific implementations as needed

## Migration Guide

If you were using the old components:

1. **NvidiaAgent** → Use `NvidiaTasker` with appropriate tasks
2. **NvidiaClient.chatCompletion()** → Use `tasker.execute(NvidiaTasker.chat(...))`
3. **NemotronProvider** → Configure `TaskerConfig` with `useHuggingFace = true`

## Benefits of Consolidation

1. **Single Point of Entry**: One class to manage all NVIDIA AI operations
2. **Consistent API**: Task-based interface for all operations
3. **Better Testing**: Easier to mock and test with unified architecture
4. **Reduced Duplication**: Shared code for API calls, retry logic, etc.
5. **Platform Flexibility**: Easy to add new platform implementations