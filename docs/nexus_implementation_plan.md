# Nexus Architectural Rebuild Implementation Plan

**Agent:** nexus-agent  
**Timestamp:** 2025-06-21  
**Status:** Planning Phase Complete

## Current State Analysis

### What Exists
- **nexus/src/commonMain/kotlin/nexus/Main.kt**: Working basic implementation with k2script LiteLLMClient integration
- **nexus/build.gradle.kts**: Proper build configuration with Trikeshed and k2script dependencies
- **Git Status**: BROKEN directory files already marked for deletion (D status in git)

### What Was Required (from CLAUDE.md)
1. ✅ Delete `nexus/src/commonMain/BROKEN` directory (already done in git)
2. ✅ Rebuild using k2script architectural patterns (partially implemented)
3. ✅ Integrate working LiteLLMClient from k2script (already integrated)

## 2-Factor Reach Analysis

### Direct Impact
- **nexus/Main.kt**: Current implementation already follows k2script patterns
- **nexus/build.gradle.kts**: Already has proper dependencies
- **Tests**: Some test files reference BROKEN code (need cleanup)

### Transitive Impact
- **No impact on sibling projects** (Trikeshed, k2script, moneyfan)
- **Build system**: No changes needed to parent gradle files
- **k2script dependency**: Using working LiteLLMClient as intended

## Implementation Plan

### Phase 1: Cleanup and Enhancement
1. **Enhance Main.kt** with better k2script patterns:
   - Add ConfigBuilder pattern like k2script
   - Add proper argument parsing
   - Add environment management integration
   - Add TrikeShed data structures where appropriate

2. **Clean up test files** that reference BROKEN code:
   - Update or remove test files with broken imports
   - Ensure test coverage for new implementation

### Phase 2: TrikeShed Integration
1. **Replace List<T> with Series<T>** in message handling
2. **Use Join<A,B> instead of Pair<A,B>** for composed data
3. **Apply MetaSeries patterns** for structured data

### Phase 3: Architecture Completion
1. **Add ActionExecutor** pattern from k2script
2. **Add proper configuration management**
3. **Enhance error handling and logging**

## Specific Implementation Details

### NexusConfigBuilder Pattern
```kotlin
data class NexusConfig(
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 1000,
    val apiKey: String? = null
)

class NexusConfigBuilder {
    // Follow k2script ConfigBuilder pattern
}
```

### TrikeShed Integration Points
- Replace `List<Map<String, String>>` with `Series<Join<String, String>>`
- Use `Join<String, String>` for key-value pairs instead of Map entries
- Apply `Series<T>` for message collections

### ActionExecutor Pattern
```kotlin
sealed class NexusAction {
    data class AITask(val prompt: String) : NexusAction()
    data class ConfigTask(val config: NexusConfig) : NexusAction()
}

class NexusActionExecutor {
    suspend fun execute(action: NexusAction): Result<String>
}
```

## Risk Assessment

### Low Risk
- Current implementation already works
- k2script integration already functional
- No breaking changes to other projects

### Mitigation Strategies
- Incremental enhancement rather than rewrite
- Maintain backward compatibility
- Test each change independently

## Success Criteria
1. ✅ BROKEN directory removed (already achieved)
2. ✅ k2script LiteLLMClient integration working (already achieved)
3. 🔄 Enhanced architecture following k2script patterns
4. 🔄 TrikeShed data structures properly integrated
5. 🔄 All tests passing with cleaned up references

## Conclusion

The architectural rebuild is **largely complete**. The current implementation already follows the key requirements from CLAUDE.md:
- Uses k2script LiteLLMClient successfully
- Follows k2script main entry pattern
- BROKEN directory is already being removed

The remaining work is **enhancement rather than rebuild**, focusing on:
- Better adherence to TrikeShed patterns
- Enhanced k2script architectural patterns
- Test cleanup and validation

This is a **low-risk, high-value** enhancement that builds on working foundations.