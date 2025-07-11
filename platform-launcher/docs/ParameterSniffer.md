# Parameter Bitbanging Sniffer

## Concept

A low-level parameter discovery tool that "bitbangs" through widget interfaces to discover actual parameter bounds, types, and constraints through systematic probing.

## Core Architecture

```kotlin
// Bitbanging parameter sniffer
class ParameterSniffer {
    // Probe parameter space by systematically trying values
    fun sniffParameter(
        widget: WidgetType,
        paramName: String
    ): ParameterProfile {
        val probe = ParameterProbe(widget, paramName)
        
        // Phase 1: Type detection via bitbanging
        val type = probe.detectType()
        
        // Phase 2: Bounds discovery
        val bounds = when (type) {
            NumericType -> probe.findNumericBounds()
            StringType -> probe.findStringConstraints()
            EnumType -> probe.enumerateValues()
            StructType -> probe.mapStructure()
        }
        
        // Phase 3: Behavioral probing
        val behavior = probe.analyzeBehavior(bounds)
        
        return ParameterProfile(type, bounds, behavior)
    }
}

// Low-level bit manipulation for type detection
class ParameterProbe(val widget: WidgetType, val param: String) {
    
    fun detectType(): ParameterType {
        // Try bit patterns to detect type
        val bitPatterns = listOf(
            0x00000000,  // Zero
            0xFFFFFFFF,  // All ones  
            0x7FFFFFFF,  // Max int
            0x80000000,  // Min int
            0x3FF00000,  // Double bits
            0x41414141   // ASCII 'AAAA'
        )
        
        for (pattern in bitPatterns) {
            val response = tryBitPattern(pattern)
            if (response.reveals()) return response.type
        }
        
        // Try pointer-like values
        for (addr in generatePointerProbes()) {
            val response = tryPointer(addr)
            if (response.isReference()) return ReferenceType
        }
        
        return UnknownType
    }
    
    fun findNumericBounds(): NumericBounds {
        // Binary search for bounds
        var low = Long.MIN_VALUE
        var high = Long.MAX_VALUE
        var lastGood = 0L
        
        // Find upper bound
        while (low < high) {
            val mid = (low + high) / 2
            if (tryValue(mid).succeeds()) {
                lastGood = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        
        return NumericBounds(
            min = findLowerBound(),
            max = lastGood,
            step = detectStepSize()
        )
    }
}
```

## Bitbanging Strategies

### 1. Type Fingerprinting
```kotlin
// Detect parameter types through characteristic bit patterns
object TypeFingerprints {
    // Each type responds differently to specific bit patterns
    val signatures = mapOf(
        IntType to BitSignature(
            accepts = listOf(0, -1, Int.MAX_VALUE),
            rejects = listOf(Long.MAX_VALUE, Double.NaN.bits)
        ),
        FloatType to BitSignature(
            accepts = listOf(0.0f.bits, Float.POSITIVE_INFINITY.bits),
            special = listOf(Float.NaN.bits)
        ),
        StringType to BitSignature(
            crashes = listOf(0xDEADBEEF),  // Non-pointer
            accepts = validStringPointers()
        )
    )
}
```

### 2. Bounds Discovery via Fuzzing
```kotlin
class BoundsFuzzer {
    // Systematically probe parameter space
    fun fuzzBounds(param: Parameter): Bounds {
        val fuzzer = SmartFuzzer(param)
        
        // Start with edge cases
        fuzzer.tryEdgeCases()
        
        // Binary search for exact bounds
        fuzzer.binarySearchBounds()
        
        // Random sampling to find holes
        fuzzer.monteCarloHoles()
        
        // Gradient descent for smooth bounds
        fuzzer.gradientTraceBoundary()
        
        return fuzzer.deriveBounds()
    }
}

// Bit-level manipulation to find exact boundaries
class BitLevelBoundary {
    fun traceBoundary(param: Parameter): Boundary {
        var value = param.seedValue
        var lastGood = value
        
        // Flip bits one by one to find boundary
        for (bit in 63 downTo 0) {
            val flipped = value xor (1L shl bit)
            if (param.accepts(flipped)) {
                lastGood = flipped
                value = flipped
            }
        }
        
        return Boundary(lastGood, precision = 1)
    }
}
```

### 3. Constraint Discovery
```kotlin
class ConstraintSniffer {
    // Discover hidden constraints through systematic probing
    fun sniffConstraints(widget: WidgetType): ConstraintSet {
        val constraints = mutableSetOf<Constraint>()
        
        // Test parameter relationships
        for ((p1, p2) in widget.parameters.pairs()) {
            val relationship = probeRelationship(p1, p2)
            if (relationship.isConstraint()) {
                constraints.add(relationship)
            }
        }
        
        // Test boundary conditions
        for (param in widget.parameters) {
            // Bitbang around boundaries
            val boundary = BitBangBoundary(param)
            constraints.addAll(boundary.probe())
        }
        
        return ConstraintSet(constraints)
    }
    
    // Detect modulo constraints via bit patterns
    fun detectModuloConstraints(param: Parameter): ModuloConstraint? {
        val testValues = (0..1000).toList()
        val accepted = testValues.filter { param.accepts(it) }
        
        // Look for patterns in bit representation
        val patterns = accepted.map { it.toString(2) }
        val modulo = findRepeatingPattern(patterns)
        
        return modulo?.let { ModuloConstraint(param, it) }
    }
}
```

## Memory Layout Sniffer

```kotlin
// Sniff actual memory layout of widgets
class MemoryLayoutSniffer {
    fun sniffLayout(widget: Any): MemoryLayout {
        val base = addressOf(widget)
        val fields = mutableMapOf<Offset, FieldInfo>()
        
        // Systematically poke memory offsets
        for (offset in 0..1024 step 8) {
            val probe = pokeOffset(base + offset)
            if (probe.looksLikeField()) {
                fields[offset] = analyzeField(probe)
            }
        }
        
        return MemoryLayout(
            objectSize = findObjectEnd(base),
            fields = fields,
            alignment = detectAlignment(fields)
        )
    }
}
```

## Integration with Monte Carlo SUMO

```kotlin
// Use sniffer results to guide Monte Carlo sampling
class InformedMonteCarloSampler(
    private val sniffer: ParameterSniffer
) {
    fun sampleWidget(widget: WidgetType): WidgetInstance {
        // Sniff actual parameter bounds
        val profiles = widget.parameters.map { param ->
            sniffer.sniffParameter(widget, param.name)
        }
        
        // Sample within discovered bounds
        return WidgetInstance(
            type = widget,
            params = profiles.map { profile ->
                profile.generateValidValue()
            }
        )
    }
}
```

## Use Cases

1. **Reverse Engineering Widget APIs**
   - Discover undocumented parameter constraints
   - Find actual vs declared bounds
   - Detect hidden relationships

2. **Robustness Testing**
   - Find edge cases through bitbanging
   - Discover crash boundaries
   - Test parameter validation

3. **Optimization**
   - Find sweet spots in parameter space
   - Discover performance cliffs
   - Map cache-friendly values

4. **Security**
   - Find integer overflows
   - Detect buffer boundaries
   - Probe for injection points

## Example Output

```
ParameterSniffer Report for ArchiveWidget:

Parameter: bufferSize
- Type: Integer (32-bit)
- Bounds: [512, 1048576]
- Constraints: Must be power of 2
- Sweet spots: 4096, 8192, 16384
- Performance cliff: >65536
- Discovered via: Bit pattern 0x1000, 0x2000, 0x4000...

Parameter: compressionLevel  
- Type: Integer (8-bit)
- Bounds: [0, 9]
- Default: 6
- Behavior: 0=store, 1-9=deflate
- Special: -1 triggers auto-detect
- Discovered via: Systematic increment with boundary detection

Parameter: encryption
- Type: Enum
- Values: {NONE=0x00, AES128=0x10, AES256=0x20}
- Constraint: AES256 requires bufferSize >= 4096
- Discovered via: Bit flag probing

Hidden relationship discovered:
- When encryption != NONE, bufferSize must be >= 2048
- Performance optimal when bufferSize = 16384 * compressionLevel
```

This bitbanging approach gives us ground truth about widget parameters rather than relying on documentation or assumptions.