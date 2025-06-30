# BoingDemo Project Instructions

## Multiplatform Architecture Excellence

BoingDemo is a textbook example of clean Kotlin Multiplatform (KMP) design. The `expect`/`actual` pattern for `EconoCanvas` and `playSound` perfectly isolates platform-specific code, keeping the common `BoingDemo.kt` logic pure and reusable.

## Architecture Patterns

- **expect/actual Pattern**: Use for platform-specific implementations
- **Pure Common Logic**: Keep core demo logic in commonMain
- **Platform Isolation**: Separate platform concerns cleanly
- **Feature Parity Goal**: All platforms should have equivalent capabilities

## Audio Implementation Priority

The native audio implementation is currently a stub and needs completion:

### **Proposal: Complete Native Audio**
- Implement `actual fun playSound` for `nativeMain` using C-interop
- Consider libraries: **miniaudio** or **OpenAL** 
- Goal: Complete multiplatform experience with satisfying "boing" sound
- This makes the awesome demo even more universally impressive

## Development Guidelines

- Use TrikeShed data structures for demo state management
- Prefer `Series<T>` for animation sequences and state collections
- Use `Join<A,B>` for configuration mappings
- Follow global SuperBikeShed architectural patterns

## Demo Architecture

- Keep demo code simple but architecturally sound
- Demonstrate TrikeShed performance benefits
- Show multiplatform compilation capabilities
- Use museum preservation for demo algorithms

## Build Patterns

- Defer to superbikeshed gradle for dependencies
- Support common,native,wasm,jvm targets
- Follow gradle console settings from parent project