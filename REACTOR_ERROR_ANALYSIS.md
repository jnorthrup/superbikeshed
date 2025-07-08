# Reactor Build Error Analysis

## Summary
The build failures are in trikeshed-lib, not trikeshed-reactor. Main issues:

### 1. BBCursiveNatural.kt - Inline Function Visibility (8 errors)
- **Issue**: Public inline functions calling internal functions
- **Lines**: 21-24, 59, 115, 160
- **Root cause**: `inline fun` is public but calls `internal fun` methods
- **Fix**: Already attempted to make functions internal, but suppress annotations not working

### 2. ByteBufferPyramid.kt - Generic Type Arithmetic (7 errors)  
- **Issue**: Cannot add/subtract with generic type M
- **Lines**: 57, 62, 66, 72
- **Root cause**: `position + 1` where position is type M (generic Comparable)
- **Fix**: Need abstraction for incrementing M type

### 3. ByteIndexedOptimized.kt - Same as BBCursiveNatural (4 errors)
- **Issue**: Public inline functions calling internal functions
- **Lines**: 19-22

### 4. TiledBBCursive.kt - Missing operator modifier (1 error)
- **Issue**: compareTo needs operator modifier
- **Line**: 134

## Immediate Actions

1. **For inline visibility errors**: 
   - These files already have @file:Suppress annotations
   - The suppress isn't working for metadata compilation
   - Consider making inline functions internal

2. **For ByteBufferPyramid generic arithmetic**:
   - The generic M type can't use + operator
   - Need a way to increment/decrement Comparable<M>
   - Consider using specific types (Int/Long) instead of generic M

3. **For TiledBBCursive operator**:
   - Add operator modifier to compareTo function

## Root Issue
These are experimental files with advanced type gymnastics that push Kotlin's type system. The @file:Suppress annotations indicate these issues are known but the suppression isn't working in common metadata compilation.