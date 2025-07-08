# nexus TODO

## Architecture Rebuild

- [ ] Delete entire `src/commonMain/BROKEN/` directory (0.5 days)
- [ ] Remove `NexusTypes_OLD.kt` and `DefaultNexusAgent.kt` (0.5 days)
- [ ] Remove overly-abstract agent design components (1-2 days)

## New Architecture Implementation

- [ ] Create main entry point with argument parsing (k2script pattern) (2-3 days)
- [ ] Add `NexusConfigBuilder` for settings management (2-3 days)
- [ ] Add `ActionExecutor` for task handling (3-4 days)
- [ ] Integrate `LiteLLMClient` as core AI provider (2-3 days)
- [ ] Implement basic environment scanning (3-4 days)

## Core Agent Features

- [ ] Basic task execution engine (4-5 days)
- [ ] Environment capability discovery (3-4 days)
- [ ] Project structure analysis (2-3 days)
- [ ] Tool orchestration framework (5-6 days)

## IntelliJ PSI Integration

- [ ] Set up PSI analysis module (3-4 days)
- [ ] Implement semantic code understanding (4-5 days)
- [ ] Add type-aware refactoring capabilities (5-6 days)
- [ ] Create LSP server for universal editor support (6-8 days)

## Testing and Validation

- [ ] Create integration tests for new architecture (3-4 days)
- [ ] Test LLM provider integration (2-3 days)
- [ ] Validate environment scanning accuracy (2-3 days)
- [ ] Performance testing for large projects (3-4 days)