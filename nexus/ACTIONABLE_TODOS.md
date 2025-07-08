# Nexus Actionable TODOs

> **Note**: This list is unprioritized. Begin work on any item as resources allow.

## Architecture Rebuild

- [x] Delete entire `src/commonMain/BROKEN/` directory
- [x] Remove `NexusTypes_OLD.kt` and `DefaultNexusAgent.kt`
- [x] Remove overly-abstract agent design components

## New Architecture Implementation

- [x] Create main entry point with argument parsing (k2script pattern)
- [x] Add `NexusConfigBuilder` for settings management
- [x] Add `ActionExecutor` for task handling
- [x] Integrate `LiteLLMClient` as core AI provider
- [x] Implement basic environment scanning

## Core Agent Features

- [x] Basic task execution engine
- [x] Environment capability discovery
- [x] Project structure analysis
- [x] Tool orchestration framework

## IntelliJ PSI Integration

- [x] Set up PSI analysis module
- [x] Implement semantic code understanding
- [ ] Add type-aware refactoring capabilities
- [ ] Create LSP server for universal editor support

## Plugin Development Checklist

- [x] Plugin SDK Setup: Configure IntelliJ Platform Plugin development
- [x] PSI Bridge Implementation: Implement `NexusPsiAdapter` interface
- [x] Kotlin Analysis Integration: Set up `KotlinAnalysisBridge`
- [x] K2 API Integration: Implement `K2TrikeShedBridge`
- [x] TrikeShed Adaptation: Map PSI types to TrikeShed `Series<T>` operations
- [x] Service Registration: Register Nexus services in plugin.xml
- [ ] Performance Optimization: Implement lazy evaluation and caching
- [x] Testing Infrastructure: Create PSI-based test fixtures
- [ ] Documentation: Document API contracts and usage patterns

## Testing and Validation

- [x] Create integration tests for new architecture
- [x] Test LLM provider integration
- [x] Validate environment scanning accuracy
- [ ] Performance testing for large projects

## Telemetry System

- [ ] Add support for real-time telemetry dashboard
- [ ] Add support for telemetry data export to various formats (JSON, CSV, etc.)
- [ ] Add support for telemetry data visualization (charts, graphs)
- [ ] Add support for telemetry data analysis and insights generation
- [ ] Add support for telemetry data alerting for critical failures

---

## Quick Start Commands

```bash
# Build Nexus
./gradlew build

# Run tests
./gradlew test

# Clean and rebuild
./gradlew clean build
```

## File Locations

- **Main TODO**: `nexus/TODO.md`
- **PSI Integration**: `nexus/INTELLIJ_PSI_INTEGRATION.md`
- **Telemetry Demo**: `nexus/src/main/kotlin/nexus/telemetry/TelemetryDemo.kt`
- **Broken Code**: `nexus/src/commonMain/BROKEN/`
- **Legacy Types**: `nexus/src/commonMain/kotlin/nexus/core/NexusTypes_OLD.kt`
- **Legacy Agent**: `nexus/src/commonMain/kotlin/nexus/core/DefaultNexusAgent.kt`

---

*Last updated: $(date)* 