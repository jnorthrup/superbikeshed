package com.rtsgame.kt.trikeshed

// Placeholder for a more complex Tensor structure.
// For now, it might just be a wrapper around a multi-dimensional collection or a conceptual marker.
// CLAUDE.md: "Deterministic tensor-based data system for immutable state management"
data class Tensor<T>(
    val dimensions: List<Int>, // Example: (rows, cols) for a 2D tensor
    val data: List<T> // Flattened data, access would need index calculation
) {
    // Future methods: get, set, slice, map, etc.
    // Ensure immutability for state management as per CLAUDE.md

    init {
        val expectedSize = dimensions.fold(1) { acc, dim -> acc * dim }
        require(data.size == expectedSize) {
            "Data size (${data.size}) does not match expected size ($expectedSize) for dimensions $dimensions"
        }
        require(dimensions.all { it > 0 }) { "Dimensions must be positive" }
    }

    // Basic accessor example (could be more sophisticated)
    fun get(indices: List<Int>): T {
        require(indices.size == dimensions.size) { "Number of indices must match number of dimensions" }
        var index = 0
        var stride = 1
        for (i in dimensions.indices.reversed()) {
            require(indices[i] >= 0 && indices[i] < dimensions[i]) { "Index ${indices[i]} out of bounds for dimension $i (size ${dimensions[i]})" }
            index += indices[i] * stride
            stride *= dimensions[i]
        }
        return data[index]
    }
}

// CLAUDE.md: "Key Types: Join<A,B>"
// Represents a joining of two distinct data types or entities.
data class Join<A, B>(
    val first: A,
    val second: B
)

// CLAUDE.md: "Key Types: Twin<T>"
// Represents a pair of the same data type or entity, perhaps before and after state, or related instances.
data class Twin<T>(
    val first: T,
    val second: T
)

// CLAUDE.md: "Key Types: Indexed<T>"
// Represents a sequence of data, possibly time-ordered or event-ordered.
// Could be a simple wrapper around a list for now, with potential for more complex operations later.
data class Indexed<T>(
    val values: List<T>
) {
    // Future methods: add, filter, aggregate, etc.
    // Consider if this needs to be explicitly immutable in its operations.
    // The underlying List<T> is immutable by default in Kotlin if it's just a `List`
}

// Interface to mark entities that can be managed by TrikeShed
interface TrikeShedManaged {
    val id: String // Example: A common identifier
}
