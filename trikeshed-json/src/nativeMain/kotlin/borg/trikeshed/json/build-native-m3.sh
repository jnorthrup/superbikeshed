#!/bin/bash

echo "=== Building Native M3 Metal/MLX JSON Scanner ==="
echo "Target: Apple Silicon (M3) with Metal, NEON, and Neural Engine"
echo

# Build native executable
echo "Building Kotlin/Native executable..."
./gradlew :trikeshed-json:linkDebugExecutableNative --console=plain --no-daemon

# The executable will be in build/bin/native/debugExecutable/
EXEC_PATH="trikeshed-json/build/bin/native/debugExecutable/trikeshed-json.kexe"

if [ -f "$EXEC_PATH" ]; then
    echo "Build successful!"
    echo "Running native M3 benchmarks..."
    echo
    
    # Run the native executable
    "$EXEC_PATH"
    
    echo
    echo "=== Native Performance Summary ==="
    echo "This runs directly on Apple Silicon without JVM overhead."
    echo "Leverages:"
    echo "  - NEON SIMD (128-bit vectors)"
    echo "  - Accelerate.framework (uses AMX internally)"
    echo "  - Metal GPU compute shaders"
    echo "  - Neural Engine (via BNNS)"
else
    echo "Build failed. Executable not found at: $EXEC_PATH"
fi