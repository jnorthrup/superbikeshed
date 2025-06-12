package borg.ipfs

/**
 * Types and enums for working with tensor cursors and key-value series in IPFS.
 */

/**
 * Represents the type of a tensor dimension.
 */
enum class TensorDimensionType {
    SPATIAL,    // Spatial dimensions (e.g., x, y, z coordinates)
    TEMPORAL,   // Time-based dimensions
    CATEGORICAL,// Categorical dimensions (e.g., labels, classes)
    NUMERICAL,  // Numerical dimensions (e.g., measurements, metrics)
    COMPOSITE   // Composite dimensions (combinations of other types)
}

/**
 * Represents a dimension in a tensor with its type and metadata.
 *
 * @property name The name of the dimension
 * @property type The type of the dimension
 * @property size The size/cardinality of the dimension
 * @property metadata Additional metadata about the dimension
 */
data class TensorDimension(
    val name: String,
    val type: TensorDimensionType,
    val size: Long,
    val metadata: CustomMetadata? = null
)

/**
 * Represents a key-value pair in a series.
 *
 * @property key The key in the series
 * @property value The value associated with the key
 * @property timestamp Optional timestamp for when this key-value pair was created/updated
 */
data class KeyValuePair<T>(
    val key: String,
    val value: T,
    val timestamp: UnixTimestamp? = null
)

/**
 * Represents a series of key-value pairs with metadata.
 *
 * @property name The name of the series
 * @property dimension The dimension this series is associated with
 * @property values The ordered list of key-value pairs
 * @property metadata Additional metadata about the series
 */
data class KeyValueSeries<T>(
    val name: String,
    val dimension: TensorDimension,
    val values: List<KeyValuePair<T>>,
    val metadata: CustomMetadata? = null
)

/**
 * Represents a cursor position in a tensor.
 * Each dimension has an index indicating the current position.
 *
 * @property dimensionIndices Map of dimension names to their current indices
 * @property timestamp When this cursor position was created/updated
 */
data class TensorCursor(
    val dimensionIndices: Map<String, Long>,
    val timestamp: UnixTimestamp = System.currentTimeMillis()
)

/**
 * Represents a range of indices in a tensor dimension.
 *
 * @property start The starting index (inclusive)
 * @property end The ending index (exclusive)
 * @property step The step size between indices
 */
data class TensorRange(
    val start: Long,
    val end: Long,
    val step: Long = 1
)

/**
 * Represents a slice of a tensor defined by ranges for each dimension.
 *
 * @property dimensionRanges Map of dimension names to their ranges
 * @property metadata Additional metadata about the slice
 */
data class TensorSlice(
    val dimensionRanges: Map<String, TensorRange>,
    val metadata: CustomMetadata? = null
)

/**
 * Represents a query for finding key-value pairs in a tensor.
 *
 * @property dimensions The dimensions to search in
 * @property ranges The ranges to search within each dimension
 * @property filters Optional filters to apply to the results
 * @property metadata Additional metadata about the query
 */
data class TensorQuery(
    val dimensions: List<TensorDimension>,
    val ranges: Map<String, TensorRange>,
    val filters: Map<String, Any>? = null,
    val metadata: CustomMetadata? = null
) 