# K2Script AI-Powered Features Implementation Plan

**Agent:** k2script-agent  
**Timestamp:** 2025-06-21  
**Scope:** k2script/ directory only  

## Analysis Summary

### Current State
- k2script has solid CLI architecture with `OptionsUtils`, `ConfigBuilder`, and command parsing
- `LiteLLMClient` exists but is not integrated into the CLI
- `LineParser` uses regex-based parsing for script annotations (`@file:DependsOn`, etc.)
- TrikeShed CoreTypes system provides `Series<T>`, `Join<A,B>`, and universal composition operators
- kotlin-entity-scanner exists with `KotlinEntityScanner` and `K2ScriptIntegration` utilities

### Target Features (from CLAUDE.md)
1. **AI-powered CLI**: Add `--ai <prompt>` flag for script generation and explanation
2. **Enhanced parser**: Replace regex-based `LineParser` with `KotlinEntityScanner`  
3. **Integration**: Use `LiteLLMClient` for AI functionality

## Implementation Plan

### Phase 1: CLI Integration for AI Features
**Files to modify:**
- `/Users/jim/work/superbikeshed/k2script/src/main/kotlin/io/github/kscripting/kscript/util/OptionsUtils.kt`
- `/Users/jim/work/superbikeshed/k2script/src/main/kotlin/io/github/kscripting/kscript/Kscript.kt`
- `/Users/jim/work/superbikeshed/k2script/src/main/kotlin/io/github/kscripting/kscript/KscriptHandler.kt`

**Changes:**
1. Add `--ai` option to `OptionsUtils.createOptions()`
2. Handle AI flag in main CLI processing 
3. Create `AIHandler` class to process AI requests using `LiteLLMClient`

### Phase 2: Enhanced Annotation Parsing
**Files to modify:**
- `/Users/jim/work/superbikeshed/k2script/src/main/kotlin/io/github/kscripting/kscript/parser/LineParser.kt`
- `/Users/jim/work/superbikeshed/k2script/build.gradle.kts`

**Changes:**
1. Add kotlin-entity-scanner dependency to build.gradle.kts
2. Create `EntityBasedLineParser` using `KotlinEntityScanner.scanDependencies()`
3. Replace regex parsing with graph-based parsing for better accuracy

### Phase 3: AI Service Integration  
**Files to create:**
- `/Users/jim/work/superbikeshed/k2script/src/main/kotlin/io/github/kscripting/kscript/ai/AIHandler.kt`

**Changes:**
1. Implement script generation: `k2script --ai "create a script to find jpg files"`
2. Implement code explanation: `k2script --ai "explain this script" < script.kts`
3. Error handling for AI service unavailability

## 2-Factor Reach Analysis

### Direct Impact (files k2script will modify)
- `k2script/src/main/kotlin/io/github/kscripting/kscript/util/OptionsUtils.kt`
- `k2script/src/main/kotlin/io/github/kscripting/kscript/Kscript.kt`  
- `k2script/src/main/kotlin/io/github/kscripting/kscript/KscriptHandler.kt`
- `k2script/src/main/kotlin/io/github/kscripting/kscript/parser/LineParser.kt`
- `k2script/build.gradle.kts`

### Transitive Impact (dependencies k2script will use)
- `kotlin-entity-scanner/` - Will USE the scanner, not modify it
- `k2script/src/main/kotlin/io/github/kscripting/kscript/ai/llm/LiteLLMClient.kt` - Will USE existing client

### No Impact
- `Trikeshed/` - Using types, not modifying  
- `nexus/` - No interaction
- Other sibling projects - No interaction

## Risk Assessment
- **Low Risk**: Adding CLI options is standard practice
- **Medium Risk**: Parser replacement needs careful testing to ensure annotation compatibility
- **Low Risk**: AI integration is additive, won't break existing functionality

## Testing Strategy
1. Unit tests for new AI CLI options
2. Integration tests for kotlin-entity-scanner parsing vs regex parsing
3. End-to-end tests for AI script generation and explanation features

## Dependencies Required
```kotlin
// In k2script/build.gradle.kts commonMain dependencies
implementation(project(":kotlin-entity-scanner"))
implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
```

## Implementation Order
1. Add CLI options and basic AI handler structure (non-breaking)
2. Integrate LiteLLMClient for AI functionality  
3. Replace LineParser with EntityBasedLineParser (compatibility testing required)
4. Add comprehensive testing

## Expected Outcome
- Users can generate scripts: `k2script --ai "find all PDF files larger than 10MB"`
- Users can explain scripts: `cat script.kts | k2script --ai "explain this script"`
- More robust annotation parsing with contextual awareness
- Full backward compatibility with existing k2script functionality

## Ready to Proceed
This plan respects the scope constraints (k2script/ only), leverages existing components (LiteLLMClient, KotlinEntityScanner), and provides clear implementation phases with risk mitigation.