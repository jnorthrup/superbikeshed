# nexus TODO

## Architecture Rebuild

- [ ] Delete entire `src/commonMain/BROKEN/` directory
- [ ] Remove `NexusTypes_OLD.kt` and `DefaultNexusAgent.kt`
- [ ] Remove overly-abstract agent design components

## New Architecture Implementation

- [ ] Create main entry point with argument parsing (k2script pattern)
- [ ] Add `NexusConfigBuilder` for settings management
- [ ] Add `ActionExecutor` for task handling  
- [ ] Integrate `LiteLLMClient` as core AI provider
- [ ] Implement basic environment scanning

## Core Agent Features

- [ ] Basic task execution engine
- [ ] Environment capability discovery
- [ ] Project structure analysis
- [ ] Tool orchestration framework

## IntelliJ PSI Integration

- [ ] Set up PSI analysis module
- [ ] Implement semantic code understanding
- [ ] Add type-aware refactoring capabilities
- [ ] Create LSP server for universal editor support

## Testing and Validation

- [ ] Create integration tests for new architecture
- [ ] Test LLM provider integration
- [ ] Validate environment scanning accuracy
- [ ] Performance testing for large projects