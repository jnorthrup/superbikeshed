# BBCursive Optimization Analysis

## Complexity Hotspots & Inlining Opportunities

### Current State
BBCursive uses `fun interface JsonParser` with suspend/non-suspend variants across codebase.

### Branching Hotspots

1. **Character Matching** - Most frequent, prime for inlining
   ```kotlin
   private fun char(expected: Byte): JsonParser = JsonParser { buffer, pos ->
       if (pos < buffer.a && buffer.b(pos) == expected) null j (pos + 1)
       else null
   }
   ```

2. **Whitespace Skipping** - Loop unrolling candidate
   ```kotlin
   while (p < buffer.a && buffer.b(p) in " \t\n\r") p++
   ```

3. **String Escape Handling** - When-based dispatch
   - Most escapes are rare (`\b`, `\f`, unicode)
   - Common: `\"`, `\\`, `\n`
   - Optimize for fast path

### Conditional Optimizations

**Option 1: Inline Everything**
```kotlin
@kotlin.internal.InlineOnly
inline fun char(expected: Byte): JsonParser
```
- Pro: Zero overhead, perfect EA
- Con: Code bloat for complex parsers

**Option 2: Tiered Approach**
```kotlin
// Hot path inlined
inline fun matchChar(buffer: ByteIndexed, pos: Int, c: Byte): Int?
// Complex logic not inlined  
fun parseString(): JsonParser
```

**Option 3: Suspend Removal**
- Current: Mix of suspend/non-suspend
- Optimal: Pure functions only for EA
- Suspend only at top-level orchestration

### Recommendations

1. **Remove suspend from leaf parsers** - EA can't optimize across suspend boundaries
2. **Inline primitive matchers** - char, digit, whitespace
3. **Keep complex parsers non-inline** - string, array, object
4. **Unroll tight loops** - whitespace, digit sequences
5. **Specialize common patterns** - `"key":value` as single operation