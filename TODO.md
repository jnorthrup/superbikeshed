# Superbikeshed TODO

## Bitmap Acceleration
- [ ] Implement true bitmap acceleration with coordinate-based lookups (2-3 days)
- [ ] Add coroutine element keys for parallel scanning (1-2 days)
- [ ] Implement O(log n) bitmap-based property lookups (3-4 days)
- [ ] Add vectorized JSON operations using bitmap (2-3 days)

## AI Integration

- [ ] Integrate LiteLLMClient into CLI argument parsing (1-2 days)
- [ ] Add `--ai <prompt>` flag for script generation (2-3 days)
- [ ] Implement code explanation: `k2script --ai "explain this script" < script.kts` (1-2 days)
- [ ] Add script generation: `k2script --ai "create a script to find jpg files and resize them"` (3-4 days)
- [ ] Test AI features with different LLM providers (2-3 days)

## Parser Enhancement

- [ ] Replace regex-based LineParser with KotlinEntityScanner (3-4 days)
- [ ] Use Inductive Graph Parsing for script annotations (4-5 days)
- [ ] Implement contextual awareness for `@file:DependsOn` parsing (2-3 days)
- [ ] Add support for complex dependency declarations (3-4 days)
- [ ] Improve error reporting for malformed annotations (1-2 days)

## Core Features

- [ ] Enhance template processing system (2-3 days)
- [ ] Add support for Kotlin 2.0 features (3-4 days)
- [ ] Improve Java interop handling (2-3 days)
- [ ] Add script caching optimization (1-2 days)

## Testing

- [ ] Add integration tests for AI features (2-3 days)
- [ ] Test parser with edge cases (1-2 days)
- [ ] Verify dependency resolution accuracy (1-2 days)
- [ ] Performance testing for large scripts (2-3 days)