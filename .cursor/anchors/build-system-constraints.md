# Build System Constraints Anchor

## Gradle Permissions & Policies

### Immutable File Policy
- **Edits only with explicit permission** for specific files and purposes
- **No versions allowed** in child Gradle files
- **Run super-project Benmanes** active rewrite before each build

### Gradle Lock Enforcement
- **Highest executive permission needed** for Gradle changes
- **Gradle freedom restored** but also:
  1. Version info stripped
  2. Ben Manes runs
  3. Deviating from targets in trikeshed-lib will get stomped by trikeshed lib gradle file replacement

## Platform Targets

### Allowed Targets
- **macosArm64**: Apple Silicon with NEON/AMX SIMD
- **linuxX64**: Linux x86 with SSE/AVX/AVX2 SIMD

### Forbidden Actions
- **DO NOT** add more platform targets without justification
- **DO NOT** remove existing platform targets
- **DO NOT** modify cinterop configurations without understanding implications

## Version Management

### Ben Manes Integration
- **Runs before each build** automatically
- **Strips versions** from child projects
- **Centralizes version management** in top-level build.gradle.kts

### Child Project Constraints
```kotlin
// ✅ CORRECT - No version specified
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

// ❌ WRONG - Version specified in child project
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
```

## Build Validation
- **Architectural validation** runs on pre-commit
- **C interop validation** ensures SIMD headers exist
- **Platform target validation** ensures macosArm64/linuxX64 maintained 