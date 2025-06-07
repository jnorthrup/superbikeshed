# PRELOAD.md

Quick-start context for Claude Code to get productive immediately with TrikeShed tensor-first design.

## Core Patterns (Copy-Paste Ready)

```kotlin
// Foundation: Everything is Join<A,B>
interface Join<A, B> {
    val a: A
    val b: B
    operator fun component1(): A = a
    operator fun component2(): B = b
}

inline infix fun <A, B> A.j(b: B): Join<A, B> = object : Join<A, B> {
    override val a: A = this@j
    override val b: B = b
}

// CRITICAL: j is for Joins, to is for Pairs - NEVER mix them!
// ✅ CORRECT: Use j for Join creation
val data = "key" j { i -> values[i] }
// ❌ WRONG: Using to creates Pair<String, Function>, not Join
val wrong = "key" to { i -> values[i] }

// Tensor-first: Unified dimensional structure
typealias Tensor<T> = Join<IntArray, (IntArray) -> T>
typealias Series<T> = Join<Int, (Int) -> T>

// TrikeShed Cursor (The Pandas Killer)
typealias RowVec = Series2<Any?, () -> ColumnMeta>
typealias Cursor = Series<RowVec>

// Cursor Factory - Pure Join Elegance
@JvmInline
value class CursorBuilder(val columnSpecs: Series<ColumnSpec>) {
    fun <T> column(name: String, accessor: (Int) -> T): CursorBuilder =
        CursorBuilder(columnSpecs.size + 1 j { i ->
            if (i < columnSpecs.size) columnSpecs[i]
            else ColumnSpec(name, Any::class, accessor as (Int) -> Any?)
        })
    
    fun build(rowCount: Int): Cursor = rowCount j { rowIndex: Int ->
        columnSpecs.size j { colIndex: Int ->
            val spec = columnSpecs[colIndex]
            spec.accessor(rowIndex) j { spec.meta }
        }
    }
}

// Cursor creation - The strongest pandas killer syntax
fun C_(): CursorBuilder = CursorBuilder(emptySeries())
fun C_(vararg columns: Join<String, (Int) -> Any?>): CursorBuilder =
    columns.fold(C_()) { builder, (name, accessor) ->
        builder.column(name, accessor)
    }

// USAGE: Pure Join elegance for data analysis
val trades = C_(
    "symbol" j { i -> symbols[i] },        // ✅ Join supremacy
    "price" j { i -> prices[i] },          // ✅ Mathematical elegance  
    "volume" j { i -> volumes[i] }         // ✅ TrikeShed purity
).build(1000)

// α-conversion: Fundamental transformation
inline infix fun <X, C> Tensor<X>.α(crossinline xform: (X) -> C): Tensor<C> = 
    a j { coords -> xform(this.b(coords)) }

inline infix fun <X, C> Series<X>.α(crossinline xform: (X) -> C): Series<C> = 
    a j { i -> xform(this.b(i)) }

// Vararg construction for ergonomics
object T_ {
    operator fun <T> invoke(vararg data: T): Tensor<T> = 
        intArrayOf(data.size) j { coords -> data[coords[0]] }
}

// Hot path materialization
fun <T> Tensor<T>.materializeHot(coords: IntArray, size: Int, block: (AlignedArray<T>) -> Unit) {
    // Extract dense chunk for SIMD operations
}

// Pandas-killer operations
fun Cursor.head(rows: Int = 5): Unit = show(0 until min(rows, size))
fun Cursor.show(range: IntRange = 0 until size) {
    println("rows:$size" to meta.names.toList())
    // Show data rows
}
val Cursor.meta: Series<ColumnMeta> get() = row(0).α { it.second() }
```

## Recent Kotlin Features to Use

### Kotlin 2.0.20+ Context Receivers (Experimental)
```kotlin
context(MemoryScope)
fun <T> Tensor<T>.materialize(): NativeArray<T> {
    // Use context receiver for memory management
}
```

### Kotlin Multiplatform Hierarchy Template
```kotlin
// build.gradle.kts - Latest Gradle 8.5+
kotlin {
    wasmJs()      // New WASM target
    js(IR)        // IR only, legacy deprecated
    jvm()
    
    nativeTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops {
                val simd by creating {
                    defFile(project.file("src/nativeInterop/cinterop/simd.def"))
                    compilerOpts("-mavx2", "-mfma")
                }
            }
        }
    }
}
```

### Value Classes for Zero-Cost Abstractions
```kotlin
@JvmInline
value class TensorShape(val dims: IntArray) {
    val rank: Int get() = dims.size
    operator fun get(index: Int): Int = dims[index]
}

@JvmInline  
value class LinearIndex(val value: Int)
```

## Common Error Patterns & Solutions

### Gradle/Kotlin Version Issues
```gradle
// build.gradle.kts - Always use latest stable
kotlin.version = "2.0.20"
gradle.version = "8.5"

// Common fix for MPP issues
kotlin {
    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}
```

### Native Memory Management
```kotlin
// ERROR: Memory leak in native code
// SOLUTION: Always use memScoped or AutoCloseable
memScoped {
    val buffer = allocArray<DoubleVar>(size)
    // Automatically freed at scope exit
}

// OR
class AlignedTensor<T> : AutoCloseable {
    private val memory = nativeHeap.allocArray<DoubleVar>(size)
    override fun close() = nativeHeap.free(memory)
}
```

### WASM Compatibility Issues
```kotlin
// ERROR: Unsupported operations in WASM
// SOLUTION: Platform-specific implementations
expect fun simdOperation(data: FloatArray): FloatArray

actual fun simdOperation(data: FloatArray): FloatArray {
    // JVM: Use Panama Vector API or JNI
    // Native: Direct SIMD intrinsics  
    // WASM: Fallback to scalar operations
}
```

### Type Inference Problems
```kotlin
// ERROR: Cannot infer type parameter
val tensor = data.α { transform(it) }  // Type unclear

// SOLUTION: Explicit type annotation
val tensor: Tensor<Double> = data.α { transform(it) }

// OR use type hint
val tensor = data.α<Double> { transform(it) }
```

## Performance Patterns (Latest Kotlin)

### Context Receivers for Performance Scopes
```kotlin
context(SIMDContext)
fun <T> Tensor<T>.accelerate(): Tensor<T> {
    // SIMD operations available in context
}

// Usage
with(AVX2Context) {
    val result = tensor.accelerate()
}
```

### Contracts for Compiler Optimization
```kotlin
@OptIn(ExperimentalContracts::class)
inline fun <T> Tensor<T>.forEach(action: (T) -> Unit) {
    contract {
        callsInPlace(action, InvocationKind.UNKNOWN)
    }
    // Enables compiler optimizations
}
```

### Expected/Actual for Platform Optimization
```kotlin
// Common interface
expect class PlatformTensor<T> {
    fun multiply(other: PlatformTensor<T>): PlatformTensor<T>
}

// JVM: Use BLAS libraries
actual class PlatformTensor<T> actual constructor() {
    actual fun multiply(other: PlatformTensor<T>): PlatformTensor<T> {
        // Intel MKL or OpenBLAS
    }
}

// Native: Direct SIMD
actual class PlatformTensor<T> actual constructor() {
    actual fun multiply(other: PlatformTensor<T>): PlatformTensor<T> {
        // AVX2/AVX512 implementation
    }
}
```

## Debugging Patterns

### Tensor Pipeline Inspection
```kotlin
// Debug lazy pipelines without breaking laziness
fun <T> Tensor<T>.debug(tag: String): Tensor<T> = 
    a j { coords -> 
        val value = this.b(coords)
        println("$tag[${coords.contentToString()}] = $value")
        value
    }

// Usage
val result = tensor
    .α { normalize(it) }
    .debug("after_normalize")
    .α { transform(it) }
    .debug("after_transform")
```

### Memory Usage Tracking
```kotlin
// Track allocations in hot paths
inline fun <T> measureMemory(block: () -> T): Pair<T, Long> {
    val before = Runtime.getRuntime().freeMemory()
    val result = block()
    val after = Runtime.getRuntime().freeMemory() 
    return result to (before - after)
}
```

## IDE Setup for Latest Kotlin

### IntelliJ Settings
- Enable "New Kotlin/MPP project model" 
- Set Kotlin plugin to "EAP" channel for latest features
- Enable "Context receivers" in experimental features

### Common IDE Issues
```kotlin
// Red underlines in MPP projects
// SOLUTION: Invalidate caches and restart IDE
// File → Invalidate Caches and Restart

// Native debugging not working  
// SOLUTION: Enable native debugging in run configuration
// Run → Edit Configurations → Enable native debugging
```

This gets you productive immediately while leveraging latest Kotlin capabilities for tensor-first design.