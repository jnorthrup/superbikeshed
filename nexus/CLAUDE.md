# Nexus Project Instructions

## Critical Architecture Refactoring Required

**Current Issue**: The `nexus/src/commonMain/BROKEN` directory contains abandoned, overly-abstract agent designs (`NexusTypes_OLD.kt`) and broken, non-functional implementation (`DefaultNexusAgent.kt`). The attempt at "purely compositional" agent resulted in a system that is difficult to understand, maintain, or complete.

### **CRITICAL Proposal: Complete Architectural Rebuild**

**Delete the entire `nexus/src/commonMain/BROKEN` directory** and refactor Nexus agent from ground up using proven k2script architecture:

1. **Main Entry Point**: Parse arguments (like k2script)
2. **NexusConfigBuilder**: Manage settings (like ConfigBuilder)  
3. **ActionExecutor**: Handle specific tasks (like Executor)
4. **LLM Integration**: Directly use working `k2script.ai.llm.LiteLLMClient` as provider

**Result**: Smaller, more focused, and *working* agent instead of broken abstractions.

## Proven Architecture Patterns

- Follow the successful `k2script` architectural patterns
- Use working `LiteLLMClient` instead of broken provider concepts
- Replace overly-abstract designs with concrete, working implementations
- Build incrementally on proven foundations

## Agentic Intelligence Framework

This project implements agentic intelligence capabilities with TrikeShed integration.

## Architecture Patterns

- Use TrikeShed's `Series<T>` and `Join<A,B>` for data structures
- Prefer functional approaches over mutable state
- Follow the global SuperBikeShed patterns from main CLAUDE.md

## Key Components

- **Agent Interfaces**: Define agentic behavior contracts
- **Intelligence Providers**: LLM and model integrations
- **Telemetry Systems**: Cross-platform event tracking
- **Resource Management**: Efficient resource allocation

## Development Guidelines

- All agent state should use TrikeShed data structures
- Prefer `Series<T>` over `List<T>` for collections
- Use `Join<A,B>` for key-value associations
- Follow museum preservation rules - no arbitrary code deletion