# RTS Codec Manifest

## Problem: Continuous Evolution During Migration

The JS codebase is **alive and growing** - new features, systems, and behaviors are constantly added. Previous migration attempts failed because:

1. **Moving Target** - JS kept evolving during port attempts
2. **Feature Divergence** - TS and KMP versions fell behind
3. **Integration Hell** - Merging new JS features into partial ports
4. **Lost Work** - Abandoned migrations when JS surged ahead

## Solution: Synchronous Deterministic Codec

Instead of a one-time port, we need a **living codec** that can:

1. **Continuously translate** JS changes to KMP
2. **Maintain deterministic parity** between both versions
3. **Allow bidirectional sync** when needed
4. **Preserve all behavioral quirks**

## Codec Rules

### State Synchronization
- Every frame must produce identical state
- Random number streams must align perfectly  
- Collection iteration order must match
- Floating point math must be reproducible

### Override Points
- AI decisions must be interceptable
- Player inputs inject at same points
- Replay data works across both versions
- Network sync possible between JS/KMP clients

### Evolution Tracking
- New JS features get codec rules immediately
- Test harness validates behavioral parity
- Regression suite catches divergence
- Automated translation where possible

## Current State

The JS version is the **source of truth**. The KMP version must **mirror it exactly** while providing:
- Better performance on native platforms
- Type safety for large-scale development  
- Foundation for future evolution

But it must NEVER diverge from JS behavior.