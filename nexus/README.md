> [!NOTE]
> Nexus is a key component of a larger, unified architecture, serving as the primary DGM orchestrator. For an overview of how Nexus fits into the broader ecosystem and implements DGM principles, please see the [Unified Architecture Documentation v3](../../docs/unified_architecture_v3.md).

# Nexus: Universal Development Agent

Agent framework for autonomous task execution and code analysis.

## Build

```bash
./gradlew build
```

## Usage

### Basic Agent Execution
```kotlin
val agent = NexusAgent()
agent.execute("analyze project structure")
```

### Configuration
```kotlin
val config = NexusConfigBuilder()
    .withProvider("openai")
    .withModel("gpt-4")
    .build()
```

## Current Status

⚠️ **Under reconstruction**: Legacy implementation in `src/commonMain/BROKEN/` is being replaced with a cleaner architecture based on k2script patterns.

## Features

- Environment scanning and capability discovery
- Task execution with LLM integration
- Project structure analysis
- Tool orchestration
- IntelliJ PSI integration for semantic analysis

## Architecture

- **NexusAgent**: Main agent execution engine
- **ActionExecutor**: Task execution handlers  
- **LiteLLMClient**: LLM provider integration
- **ConfigBuilder**: Configuration management

## Dependencies

- LiteLLMClient for AI integration
- Trikeshed core types
- Kotlin coroutines
- IntelliJ PSI APIs (optional)