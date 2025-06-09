/**
 * This file provides the core infrastructure for the TrikeShed Tensor Compression Module's
 * bit-packing capabilities. It is inspired by modern C++ design patterns (like cppfront)
 * emphasizing type safety, zero-cost abstractions (where possible), and ergonomic syntax
 * for creating and manipulating packed data structures.
 *
 * Key components defined herein include:
 * - `ActualPackedBits`: The underlying primitive (`Long`) for storing bit sequences.
 * - `IsPackable`: A marker interface for types that can be serialized into bits.
 * - `Packer<T>`: An interface defining how a type `T` is converted to/from `ActualPackedBits`.
 * - Concrete `Packer` implementations for basic types (Int, Boolean).
 * - `N`, `NJoin`, `NSeries`: Interfaces and classes representing the actual packed data
 *   structures, potentially spanning multiple `ActualPackedBits` segments (spilling).
 * - `PackingAttempt`: An Either-like sealed interface to handle success/failure of packing ops.
 *
 * The system is designed to be extensible, allowing new packable types and their packers
 * to be registered and used. The primary user-facing packing operations are typically
 * done via `kj` operators defined in `PackingOperators.kt`.
 */
package borg.trikeshed.core

import kotlin.reflect.KClass

/**
 * Type alias for the primitive type used to store packed bits.
 * Using Long provides 64 bits of storage per segment.
 */
typealias ActualPackedBits = Long

/**
 * Marker interface for types that can be packed and unpacked.
 * Implementing this interface signals that a type is compatible with the packing system
 * and that a corresponding [Packer] implementation should exist for it.
 */
interface IsPackable

/**
 * Defines the contract for packing a type `T` into `ActualPackedBits` and unpacking it back.
 * Packer implementations are responsible for the direct conversion of a type `T`
 * to its bit representation and vice-versa.
 *
 * @param T The type of the value to be packed/unpacked. It must be marked as [IsPackable].
 */
interface Packer<T : IsPackable> {
    /**
     * Packs the given value into `ActualPackedBits`.
     * The implementation should ensure that the packed data fits within the defined [bitSize]
     * and only contains the bits relevant to the value.
     *
     * @param value The value to pack.
     * @return The packed bits as `ActualPackedBits`, typically right-aligned.
     */
    fun pack(value: T): ActualPackedBits

    /**
     * Unpacks the given `ActualPackedBits` back into a value of type `T`.
     * The input `bits` are expected to be right-aligned and contain only the data
     * relevant for type `T` as defined by [bitSize].
     *
     * @param bits The packed bits to unpack.
     * @return The unpacked value of type `T`.
     */
    fun unpack(bits: ActualPackedBits): T

    /**
     * The exact number of bits required to store the packed representation of type `T`.
     * This value is crucial for layout calculations in multi-value packing structures like [NJoin] or [NSeries].
     * Must be between 1 and 64 (inclusive, fitting within a single `ActualPackedBits` segment for the type itself).
     */
    val bitSize: Int

    /**
     * A mask that covers all bits used by this packer, based on [bitSize].
     * For example, if `bitSize` is 8, the mask is `0xFFL`. If `bitSize` is 1, the mask is `0x1L`.
     * This is useful for ensuring that only relevant bits are considered during packing and unpacking.
     */
    val bitMask: ActualPackedBits get() = (1L shl bitSize) - 1
}

/**
 * Gets a sequence of bits of a specified length starting from a given position within an `ActualPackedBits` segment.
 *
 * @param start The starting bit position (0-indexed, from the right/LSB).
 * @param length The number of bits to extract (must be > 0 and fit within the segment from the start position).
 * @return The extracted bits as a Long, right-aligned (LSB of result corresponds to bit `start`).
 * @throws IllegalArgumentException if the start or length parameters are invalid.
 */
fun ActualPackedBits.getBits(start: Int, length: Int): ActualPackedBits {
    require(start in 0..63 && length > 0 && length <= 64 - start) { "Invalid bit range: start=$start, length=$length. Must be within 0-63 and length > 0." }
    return (this shr start) and ((1L shl length) - 1)
}

/**
 * Sets a sequence of bits of a specified length starting from a given position within an `ActualPackedBits` segment.
 *
 * @param start The starting bit position (0-indexed, from the right/LSB).
 * @param length The number of bits to set (must be > 0 and fit within the segment from the start position).
 * @param bitsToSet The bits to set. Only the lower `length` bits of this value are used.
 * @return A new `ActualPackedBits` value with the specified bits set, leaving other bits untouched.
 * @throws IllegalArgumentException if the start or length parameters are invalid.
 */
fun ActualPackedBits.setBits(start: Int, length: Int, bitsToSet: ActualPackedBits): ActualPackedBits {
    require(start in 0..63 && length > 0 && length <= 64 - start) { "Invalid bit range: start=$start, length=$length. Must be within 0-63 and length > 0." }
    val mask = ((1L shl length) - 1) shl start // Mask for the bits to be set
    val clearedValue = this and mask.inv() // Clear the bits in the target range
    val shiftedBitsToSet = (bitsToSet and ((1L shl length) - 1)) shl start // Align bitsToSet to the start position and mask
    return clearedValue or shiftedBitsToSet
}


// --- Concrete Packer Implementations for Basic Types ---

// For Int and Boolean to be used with Packer<T : IsPackable>, they don't directly implement IsPackable.
// The `getPacker<T>()` and `PackerRegistry` handle this by checking KClass for known primitive types.
// The `@IsPackableType` annotation is conceptual for these cases.

/**
 * A conceptual type annotation indicating that a primitive type is being treated as `IsPackable`
 * for the purpose of `Packer<T>` constraints. This is used because primitive types
 * cannot directly implement interfaces like [IsPackable].
 */
@Target(AnnotationTarget.TYPE)
annotation class IsPackableType // Effectively a documentation marker

/**
 * Packer for [Int] values.
 * Assumes Ints are packed into 32 bits.
 * This object serves as the canonical packer for `Int` when treated as an [IsPackableType].
 */
object IntPacker : Packer<@IsPackableType Int> {
    override fun pack(value: @IsPackableType Int): ActualPackedBits = value.toLong() and 0xFFFFFFFFL
    override fun unpack(bits: ActualPackedBits): @IsPackableType Int = bits.toInt()
    override val bitSize: Int = 32
}

/**
 * Packer for [Boolean] values.
 * Booleans are packed into a single bit (1 for true, 0 for false).
 * This object serves as the canonical packer for `Boolean` when treated as an [IsPackableType].
 */
object BooleanPacker : Packer<@IsPackableType Boolean> {
    override fun pack(value: @IsPackableType Boolean): ActualPackedBits = if (value) 1L else 0L
    override fun unpack(bits: ActualPackedBits): @IsPackableType Boolean = (bits and 1L) != 0L
    override val bitSize: Int = 1
}

// --- Packer Retrieval ---

/**
 * Retrieves a [Packer] instance for the given reified type `T`.
 * This function uses KClass comparison for known primitive-like types (`Int`, `Boolean`).
 * For user-defined types that implement [IsPackable], a more robust mechanism like
 * the [PackerRegistry] or convention-based retrieval (e.g., companion object) is recommended.
 *
 * @param T The type for which a packer is required.
 * @return A [Packer<T>] instance.
 * @throws UnsupportedOperationException if no packer is found for type `T`.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> getPacker(): Packer<T> {
    return when (T::class) {
        Int::class -> IntPacker as Packer<T>
        Boolean::class -> BooleanPacker as Packer<T>
        // Example for a user-defined IsPackable type (conceptual):
        // MyPackableType::class -> MyPackableTypePacker as Packer<T>
        else -> throw UnsupportedOperationException("Packer not found for type ${T::class.simpleName}. Consider using PackerRegistry.")
    }
}

/**
 * A registry for [Packer] instances, allowing dynamic registration and retrieval of packers.
 * This provides a more extensible way to manage packers than hardcoded checks in `getPacker<T>()`.
 */
object PackerRegistry {
    private val packers = mutableMapOf<KClass<*>, Packer<*>>()

    init {
        // Register default packers for primitive-like types
        register(Int::class, IntPacker as Packer<Any>) // Cast needed due to @IsPackableType variance
        register(Boolean::class, BooleanPacker as Packer<Any>)
    }

    /**
     * Registers a [Packer] for a specific [KClass].
     * @param type The KClass of the type this packer handles.
     * @param packer The [Packer] instance.
     */
    fun <T : Any> register(type: KClass<T>, packer: Packer<T>) {
        packers[type] = packer
    }

    /**
     * Retrieves a [Packer] for a specific [KClass] from the registry.
     * @param type The KClass of the type for which the packer is requested.
     * @return The registered [Packer<T>] instance, or null if not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): Packer<T>? {
        return packers[type] as Packer<T>?
    }
}

/**
 * Retrieves a [Packer] instance for the given reified type `T` using the [PackerRegistry].
 * This is generally preferred over the basic `getPacker<T>()` for better extensibility.
 *
 * @param T The type for which a packer is required. Must be `Any` to allow KClass access.
 * @return A [Packer<T>] instance from the registry.
 * @throws UnsupportedOperationException if no packer is found in the registry for type `T`.
 */
inline fun <reified T : Any> getPackerFromRegistry(): Packer<T> {
    return PackerRegistry.get(T::class)
        ?: throw UnsupportedOperationException("Packer not found in registry for type ${T::class.simpleName}")
}

// --- Packed Data Structure Definitions (`N` types) ---

/**
 * Base interface for packed data structures (`N` representing "Nexus" or "Node" of packed data).
 * It holds a list of `ActualPackedBits` segments, allowing for data to spill across multiple segments
 * if it doesn't fit within a single `ActualPackedBits` (Long). The management of continuation bits
 * and data layout across segments is handled by the packing operations (e.g., `kj` operators).
 */
interface N {
    /**
     * The list of `ActualPackedBits` segments that store the packed data.
     * If the data fits into a single segment, this list will typically contain one element.
     * If the data is spilled, it will contain multiple elements.
     * The interpretation of these bits (including continuation flags) is up to the concrete `N` type
     * and the operators that create/read it.
     */
    val packedBitsList: List<ActualPackedBits>

    /**
     * Indicates whether the packed data is spilled across multiple `ActualPackedBits` segments.
     * This is typically true if `packedBitsList.size > 1`.
     * @return `true` if data spans multiple segments, `false` otherwise.
     */
    fun isSpilled(): Boolean = packedBitsList.size > 1
}

/**
 * Represents a packed pair of two types, A and B, conforming to the [N] interface.
 * The actual packing of `A` and `B` into `packedBitsList` and their subsequent unpacking
 * is orchestrated by `kj` operators, which also manage spilling and layout.
 * This class primarily holds the packed data and the means to interpret it (packers).
 *
 * @param A The type of the first element. Must be [IsPackable].
 * @param B The type of the second element. Must be [IsPackable].
 * @property packedBitsList The list of Long segments holding the packed data for A and B.
 * @property packerA The packer for type A, used for unpacking.
 * @property packerB The packer for type B, used for unpacking.
 * @property totalBitSize The total payload bit size for elements A and B. Does not include continuation bits.
 */
class NJoin<A : IsPackable, B : IsPackable>(
    override val packedBitsList: List<ActualPackedBits>,
    private val packerA: Packer<A>,
    private val packerB: Packer<B>,
    val totalBitSize: Int
) : N {

    init {
        require(packedBitsList.isNotEmpty()) { "Packed bits list cannot be empty for NJoin." }
        // totalBitSize validation relative to packedBitsList capacity would be complex here,
        // better handled by the packing (`kj`) function.
    }

    /**
     * Retrieves the first element (of type A) from the packed data.
     * Note: This is a simplified unpacker. The `kj` operator is responsible for the correct
     * packing strategy (layout, spilling). This method assumes `kj` has packed `B` first, then `A`.
     * Complex unpacking logic for spilled data should ideally be part of `kj`'s responsibilities
     * or a dedicated unpacking utility that understands the segment structure.
     * @return The unpacked value of type A.
     * @throws NotImplementedError if unpacking spilled data is attempted by this simplified method.
     */
    fun getA(): A {
        if (isSpilled()) {
            // Proper unpacking of spilled data requires stitching bits from packedBitsList
            // based on PAYLOAD_BITS_PER_SEGMENT and CONTINUATION_BIT_MASK.
            // This is a complex task handled by the packing/unpacking orchestrator (e.g. kj or helper).
            throw NotImplementedError("Unpacking spilled NJoin.getA() not yet fully implemented here. kj orchestrates this.")
        }
        // Simplified: assumes fits in first segment, B packed first.
        val bitsForA = packedBitsList[0].getBits(packerB.bitSize, packerA.bitSize)
        return packerA.unpack(bitsForA)
    }

    /**
     * Retrieves the second element (of type B) from the packed data.
     * Similar to `getA()`, this is a simplified unpacker.
     * @return The unpacked value of type B.
     * @throws NotImplementedError if unpacking spilled data is attempted by this simplified method.
     */
    fun getB(): B {
        if (isSpilled()) {
            throw NotImplementedError("Unpacking spilled NJoin.getB() not yet fully implemented here. kj orchestrates this.")
        }
        // Simplified: assumes fits in first segment, B packed first.
        val bitsForB = packedBitsList[0].getBits(0, packerB.bitSize)
        return packerB.unpack(bitsForB)
    }

    override fun toString(): String {
        return "NJoin(spilled=${isSpilled()}, totalPayloadBits=${totalBitSize}, segments=${packedBitsList.size})"
    }
}

/**
 * Represents a packed series of elements of type T, conforming to the [N] interface.
 * Similar to [NJoin], the packing and unpacking, including metadata for size and spilling,
 * are managed by `kj`-like operators. This class holds the packed data and means for interpretation.
 *
 * @param T The type of the elements in the series. Must be [IsPackable].
 * @property packedBitsList The list of Long segments holding the packed data for the series.
 * @property packerT The packer for type T, used for unpacking elements.
 * @property seriesSize The number of elements in the series.
 * @property totalElementBitSize The total bit size for all elements in the series, excluding series metadata.
 */
class NSeries<T : IsPackable>(
    override val packedBitsList: List<ActualPackedBits>,
    private val packerT: Packer<T>,
    val seriesSize: Int,
    val totalElementBitSize: Int // Total bits for elements only
) : N {

    init {
        require(packedBitsList.isNotEmpty()) { "Packed bits list cannot be empty for NSeries." }
        require(seriesSize >= 0) { "Series size cannot be negative." }
    }

    /**
     * Companion object for [NSeries] related constants or factory methods.
     */
    companion object {
        /**
         * Number of bits used to store the size of the series itself within the packed data.
         * For example, 8 bits can store sizes up to 255. This is a convention adopted by
         * the packing (`kjSeries`) function.
         */
        const val SIZE_METADATA_BITS = 8
    }

    /**
     * Retrieves the element at the specified index from the packed series.
     * Note: This is a simplified unpacker. The `kjSeries` operator is responsible for
     * the correct packing strategy (layout, spilling, size metadata).
     * This method assumes size metadata is packed first, then elements.
     * @param index The 0-based index of the element to retrieve.
     * @return The unpacked element of type T.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     * @throws NotImplementedError if unpacking spilled data is attempted by this simplified method.
     */
    fun get(index: Int): T {
        if (index < 0 || index >= seriesSize) {
            throw IndexOutOfBoundsException("Index $index out of bounds for series of size $seriesSize")
        }
        if (isSpilled()) {
            // Proper unpacking of spilled data requires complex logic.
            throw NotImplementedError("Unpacking spilled NSeries.get() not yet fully implemented here. kj orchestrates this.")
        }
        // Simplified: assumes all data in the first segment, size metadata first.
        val elementBitOffset = SIZE_METADATA_BITS + (index * packerT.bitSize)
        val bitsForElement = packedBitsList[0].getBits(elementBitOffset, packerT.bitSize)
        return packerT.unpack(bitsForElement)
    }

    override fun toString(): String {
        return "NSeries(size=$seriesSize, spilled=${isSpilled()}, totalElementBits=${totalElementBitSize}, segments=${packedBitsList.size})"
    }
}

// --- Packing Result Type Definition ---

/**
 * Represents the result of a packing operation (e.g., from a `kj` operator).
 * It functions as an "Either" type, holding either a successfully packed data structure
 * of type `PACKED_TYPE` or a [PackingFailure] detailing the reason for the failure.
 * This allows callers to safely handle both success and error outcomes.
 *
 * @param PACKED_TYPE The type of the successfully packed data structure, constrained to be a subtype of [N].
 */
sealed interface PackingAttempt<out PACKED_TYPE : N> {
    /**
     * Returns the successfully packed data if the operation was successful.
     * If the operation failed, this returns `null`.
     * @return The [PACKED_TYPE] instance on success, or `null` on failure.
     */
    fun getOrNull(): PACKED_TYPE?

    /**
     * Returns a string detailing the reason for failure if the operation was unsuccessful.
     * If the operation was successful, this returns `null`.
     * @return A failure reason string on error, or `null` on success.
     */
    fun getErrorOrNull(): String?

    /**
     * A convenience property to check if the packing operation was successful.
     * @return `true` if successful (i.e., this is an instance of [PackingSuccess]), `false` otherwise.
     */
    val isSuccess: Boolean get() = this is PackingSuccess // More idiomatic check

    /**
     * Represents a successful packing operation.
     * @property result The successfully packed data structure.
     */
    data class PackingSuccess<out PACKED_TYPE : N>(val result: PACKED_TYPE) : PackingAttempt<PACKED_TYPE> {
        override fun getOrNull(): PACKED_TYPE = result // Non-nullable here as it's a success
        override fun getErrorOrNull(): String? = null
    }

    /**
     * Represents a failed packing operation.
     * @property reason A message describing the reason for the failure.
     */
    data class PackingFailure<out PACKED_TYPE : N>(val reason: String) : PackingAttempt<PACKED_TYPE> {
        override fun getOrNull(): PACKED_TYPE? = null
        override fun getErrorOrNull(): String = reason // Non-nullable here as it's a failure
    }
}

// --- Future: Packed Tensor Data Structure ---

/**
 * Represents a packed multi-dimensional Tensor of elements of type T.
 * This is a placeholder for future implementation. The actual packing and unpacking
 * would be managed by a dedicated `kjTensor` operator, handling metadata (shape)
 * and element data, including spilling.
 *
 * @param T The type of the elements in the tensor. Must be [IsPackable].
 * @property packedBitsList The list of Long segments holding the packed data.
 * @property packerT The packer for type T, used for unpacking elements.
 * @property shape The dimensions of the tensor.
 * @property totalElementBitSize The total bit size for all elements in the tensor.
 */
class NTensor<T : IsPackable>(
    override val packedBitsList: List<ActualPackedBits>,
    private val packerT: Packer<T>, // Made private for now, access via methods if needed
    val shape: IntArray,
    val totalElementBitSize: Int // Total bits for elements only
) : N {

    init {
        require(packedBitsList.isNotEmpty()) { "Packed bits list cannot be empty for NTensor." }
        require(shape.isNotEmpty()) { "Tensor shape cannot be empty." }
        shape.forEach { require(it > 0) { "Tensor dimensions must be positive." } }
    }

    /**
     * The rank (number of dimensions) of the tensor.
     */
    val rank: Int get() = shape.size

    /**
     * The total number of elements in the tensor.
     */
    val elementCount: Long get() = shape.fold(1L) { acc, dimSize -> acc * dimSize }

    /**
     * Companion object for [NTensor] related constants or factory methods.
     */
    companion object {
        // Example: Max bits for rank, bits per dimension in metadata.
        // These would be conventions for `kjTensor`.
        const val RANK_METADATA_BITS = 8
        const val BITS_PER_DIMENSION_METADATA = 16
    }

    /**
     * Retrieves the element at the specified coordinates from the packed tensor.
     * Note: This is a highly simplified placeholder. Full implementation requires:
     * 1. Unpacking tensor metadata (rank, shape dimensions) from `packedBitsList`.
     * 2. Calculating the linear index for the given coordinates.
     * 3. Determining the bit offset for the element, accounting for metadata and prior elements.
     * 4. Handling data spilling across multiple segments.
     * This complex logic would be orchestrated by the `kjTensor` operator or a dedicated unpacker.
     *
     * @param coords The coordinates of the element to retrieve.
     * @return The unpacked element of type T.
     * @throws IndexOutOfBoundsException if coordinates are invalid.
     * @throws NotImplementedError as this is a placeholder.
     */
    fun get(vararg coords: Int): T {
        require(coords.size == rank) { "Coordinate rank ${coords.size} does not match tensor rank $rank." }
        // Simplified coordinate validation (bounds checking needed per dimension)
        coords.forEachIndexed { index, coordVal ->
            require(coordVal >= 0 && coordVal < shape[index]) {
                "Coordinate at dim $index ($coordVal) is out of bounds (0-${shape[index]-1})."
            }
        }

        // Actual unpacking logic is highly complex and not implemented here.
        throw NotImplementedError("NTensor.get() is not yet fully implemented. Requires complex unpacking logic for metadata and spilled data.")
    }

    override fun toString(): String {
        return "NTensor(shape=${shape.contentToString()}, spilled=${isSpilled()}, totalElementBits=${totalElementBitSize}, segments=${packedBitsList.size})"
    }
}
