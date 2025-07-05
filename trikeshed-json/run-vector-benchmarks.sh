#!/bin/bash

echo "=== TrikeShed JSON Vector API Benchmarks ==="
echo "Demonstrating real SIMD acceleration with JVM Vector API"
echo

# Check Java version
java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt 17 ]; then
    echo "ERROR: Java 17+ required for Vector API. Current version: $java_version"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"
echo "CPU info:"
if [[ "$OSTYPE" == "darwin"* ]]; then
    sysctl -a | grep machdep.cpu | grep -E "(brand_string|features)" | head -2
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    grep -E "(model name|flags)" /proc/cpuinfo | head -2
fi
echo

# Compile with Vector API module
echo "Building with Vector API support..."
./gradlew :trikeshed-json:compileTestKotlinJvm --console=plain --no-daemon

# Run Vector API benchmarks
echo
echo "Running Vector API benchmarks..."
echo "Note: --add-modules jdk.incubator.vector enables SIMD instructions"
echo

# Create a main class that runs the benchmarks
cat > trikeshed-json/src/jvmTest/kotlin/borg/trikeshed/json/VectorBenchmarkMain.kt << 'EOF'
package borg.trikeshed.json

fun main() {
    println("Starting Vector API Benchmarks with SIMD acceleration...")
    println("JVM Args: ${System.getProperty("java.vm.version")}")
    println("Vector module available: ${isVectorModuleAvailable()}")
    println()
    
    try {
        VectorApiBenchmark().runBenchmarks()
    } catch (e: Exception) {
        println("Error running benchmarks: ${e.message}")
        e.printStackTrace()
    }
}

fun isVectorModuleAvailable(): Boolean {
    return try {
        Class.forName("jdk.incubator.vector.ByteVector")
        true
    } catch (e: ClassNotFoundException) {
        false
    }
}
EOF

# Compile the new main class
./gradlew :trikeshed-json:compileTestKotlinJvm --console=plain --no-daemon

# Run with Vector API enabled
echo "Executing benchmarks with SIMD acceleration..."
java \
    --add-modules jdk.incubator.vector \
    -cp "trikeshed-json/build/classes/kotlin/jvmTest:trikeshed-json/build/classes/kotlin/jvmMain:trikeshed-json/build/libs/*:trikeshed-lib/build/libs/*:trikeshed-common/build/libs/*:trikeshed-io/build/libs/*:$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.6.2/*/*/*.jar:$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.6.2/*/*/*.jar:$HOME/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.0/*/*/*.jar" \
    borg.trikeshed.json.VectorBenchmarkMainKt

echo
echo "=== Benchmark Complete ==="
echo "The Vector API enables true SIMD instructions on the CPU for parallel processing."
echo "Performance scales with CPU capabilities: SSE (128-bit) < AVX2 (256-bit) < AVX-512 (512-bit)"