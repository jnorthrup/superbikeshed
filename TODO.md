# Superbikeshed TODO

## Bitmap Acceleration
- [ ] Implement true bitmap acceleration with coordinate-based lookups
- [ ] Add coroutine element keys for parallel scanning
- [ ] Implement O(log n) bitmap-based property lookups
- [ ] Add vectorized JSON operations using bitmap

## AI Integration

- [ ] Integrate LiteLLMClient into CLI argument parsing
- [ ] Add `--ai <prompt>` flag for script generation
- [ ] Implement code explanation: `k2script --ai "explain this script" < script.kts`
- [ ] Add script generation: `k2script --ai "create a script to find jpg files and resize them"`
- [ ] Test AI features with different LLM providers

## Parser Enhancement

- [ ] Replace regex-based LineParser with KotlinEntityScanner  
- [ ] Use Inductive Graph Parsing for script annotations
- [ ] Implement contextual awareness for `@file:DependsOn` parsing
- [ ] Add support for complex dependency declarations
- [ ] Improve error reporting for malformed annotations

## Core Features

- [ ] Enhance template processing system
- [ ] Add support for Kotlin 2.0 features
- [ ] Improve Java interop handling
- [ ] Add script caching optimization

## Testing

- [ ] Add integration tests for AI features
- [ ] Test parser with edge cases
- [ ] Verify dependency resolution accuracy
- [ ] Performance testing for large scripts