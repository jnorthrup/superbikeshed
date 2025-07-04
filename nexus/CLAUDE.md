# Nexus Project Instructions

## Critical Architecture Refactoring Required
 
### **CRITICAL Proposal: Complete Architectural Rebuild**

**Delete the entire `nexus/src/commonMain/BROKEN` directory** and refactor Nexus agent from ground up using proven k2script architecture:

1. **Main Entry Point**: Parse arguments (like k2script)
2. **NexusConfigBuilder**: Manage settings (like ConfigBuilder)  
3. **ActionExecutor**: Handle specific tasks (like Executor)
4. **LLM Integration**: Directly use working `k2script.ai.llm.LiteLLMClient` as provider

**Result**: Smaller, more focused, and *working* agent instead of broken abstractions.

## Proven Architecture Patterns

- Follow the successful `k2script` architectural patterns

## Agentic Intelligence Framework

This project implements agentic intelligence capabilities with TrikeShed integration.

## Architecture Patterns

- Use TrikeShed's `Indexed<T>` and `Join<A,B>` for data structures
- Prefer functional approaches over mutable state
- Follow the global SuperBikeShed patterns from borg.trikeshed.lib.CoreTypes 

## Key Components

- **Agent Interfaces**: Define agentic behavior contracts
- **Intelligence Providers**: LLM and model integrations
- **Telemetry Systems**: Cross-platform event tracking
- **Resource Management**: Efficient resource allocation

## Shunned anti-patterns

- All agent state should use TrikeShed data structures
List<T> shunned - Indexed <T> -- only allowed when not escaping, return Indexed<T> anyways
Series<T> shunned - Indexed <T>
Pair shunned - Join<A,B>  ctor is `a j b` 