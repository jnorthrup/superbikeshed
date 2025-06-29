# K2Script - Consolidated Documentation

## Project Overview
**Status**: Active development  
**Type**: Kotlin script execution engine  
**Purpose**: Script execution with dependency management and AI integration

## Core Features
- **Kotlin Script Execution** - Run .kts files directly
- **Dependency Management** - Automatic Maven/Gradle dependency resolution
- **AI Integration** - LLM-powered code generation and explanation
- **Template Processing** - Script template system
- **Java Interoperability** - Seamless Java library integration

## Installation & Usage
```bash
# Install
./install.sh

# Basic execution
k2script script.kts

# With dependencies
k2script -d "com.example:library:1.0" script.kts

# AI features
k2script --ai "create a script to find large files"
k2script --ai "explain this code" < script.kts
```

## Architecture Components
- **K2script.kt** - Main entry point and CLI
- **parser/** - Script annotation parsing
- **ai/llm/** - LLM client integration
- **executor/** - Script execution engine

## Configuration
- `~/.kscript/kscript.properties` - Global configuration
- Project-specific `.kscript/kscript.properties`

## Implementation Status

### Completed ✅
- Documentation consolidation (June 24, 2025)
- Basic script execution engine
- Dependency resolution system
- CLI framework

### In Progress 🔄
- AI integration with LiteLLMClient
- Parser enhancement with KotlinEntityScanner
- Template processing system

### Pending ⏳
- `--ai <prompt>` flag for script generation
- Code explanation: `k2script --ai "explain this script" < script.kts`
- Replace regex-based LineParser with KotlinEntityScanner
- Inductive Graph Parsing for script annotations
- Contextual awareness for `@file:DependsOn` parsing
- Kotlin 2.0 features support
- Script caching optimization

## AI Integration Features
- **Script Generation** - Create scripts from natural language prompts
- **Code Explanation** - Explain existing scripts using LLM
- **LiteLLMClient Integration** - Support for multiple LLM providers
- **Contextual Parsing** - AI-aware dependency resolution

## Parser Enhancement Plan
- Replace regex-based parsing with KotlinEntityScanner
- Implement inductive graph parsing for annotations
- Add contextual awareness for dependency declarations
- Improve error reporting for malformed annotations
- Support complex dependency declarations

## Testing Requirements
- Integration tests for AI features
- Parser edge case testing
- Dependency resolution accuracy verification
- Performance testing for large scripts

## Documentation Status
- **Total Files**: 9 (5 root level, 4 in docs/)
- **Large Files**: 3 files >7KB (user guide, implementation details)
- **Consolidation**: Completed June 24, 2025
- **Status**: All phases completed successfully

## Key Integrations
- **KotlinEntityScanner** - For enhanced parsing
- **LiteLLMClient** - For AI features
- **Maven/Gradle** - For dependency management
- **Java Libraries** - For interoperability

---
*Last Updated: June 24, 2025*  
*Documentation Status: Consolidated* 