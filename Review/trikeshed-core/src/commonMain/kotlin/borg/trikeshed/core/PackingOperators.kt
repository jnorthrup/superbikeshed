/**
 * This file defines user-facing infix operators for the TrikeShed Tensor Compression Module,
 * primarily the `kj` operator for packing data and the `jk` operator for type manipulation.
 * These operators leverage the core packing infrastructure defined in `PackedTypes.kt`.
 *
 * The `kj` operator is the main entry point for creating packed representations (`NJoin`, `NSeries`, etc.)
 * and handles the complexities of bit layout, data spilling across multiple segments, and error reporting
 * via the `PackingAttempt` type.
 *
 * The `jk` operator provides a way to construct standard, unpacked `Join` instances with reversed
 * type parameters, which can be useful in specific DSL or API construction scenarios.
 */
package borg.trikeshed.core

// Constants defining the spill strategy for packed data.

/**
 * Maximum number of `ActualPackedBits` (Long) segments allowed for a single packed structure.
 * This limit prevents excessive memory usage and complexity for very large individual packable items.
 * For instance, with 4 segments and 63 payload bits per segment, this allows up to 252 bits of payload.
 */
const val MAX_PACKED_SEGMENTS = 4

/**
 * Number of bits available for actual data payload within each `ActualPackedBits` segment.
 * One bit (typically the MSB) is reserved as a continuation bit to indicate if the data
 * spills into the next segment. So, for a 64-bit Long, 63 bits are available for payload.
 */
const val PAYLOAD_BITS_PER_SEGMENT = 63

/**
 * Bitmask used to set or check the continuation bit within an `ActualPackedBits` segment.
 * This is typically the Most Significant Bit (MSB) if `PAYLOAD_BITS_PER_SEGMENT` is 63.
 * If `(segment and CONTINUATION_BIT_MASK) != 0L`, it means data continues in the next segment.
 */
const val CONTINUATION_BIT_MASK = 1L shl PAYLOAD_BITS_PER_SEGMENT

/**
 * Implements the 'kj' infix operator to pack two [IsPackable] items (A and B) into an [NJoin] structure.
 * This operator is a primary entry point for data compression.
 *
 * The packing order is B first (starting at the LSB of the first segment), then A.
 * Data spilling is handled automatically: if the total payload bits for A and B exceed
 * [PAYLOAD_BITS_PER_SEGMENT], the data is distributed across multiple [ActualPackedBits]
 * segments. The [CONTINUATION_BIT_MASK] is used on each segment (except the last)
 * to indicate that data continues. The number of segments is limited by [MAX_PACKED_SEGMENTS].
 *
 * @param A The type of the second item to pack (the parameter `a`). Must be [IsPackable] and have a registered [Packer].
 * @param B The type of the first item to pack (the receiver `this`). Must be [IsPackable] and have a registered [Packer].
 * @param a The second item to pack.
 * @return [PackingAttempt<NJoin<A, B>>] which is either:
 *         - [PackingAttempt.PackingSuccess] holding the successfully packed [NJoin] instance.
 *         - [PackingAttempt.PackingFailure] with a string reason if packing fails (e.g., packer not found, data too large).
 */
infix fun <reified A : IsPackable, reified B : IsPackable> B.kj(a: A): PackingAttempt<NJoin<A, B>> {
    val packerB = try { getPackerFromRegistry<B>() } catch (e: Exception) {
        return PackingAttempt.PackingFailure("Packer not found for type B (${B::class.simpleName}): ${e.message}")
    }
    val packerA = try { getPackerFromRegistry<A>() } catch (e: Exception) {
        return PackingAttempt.PackingFailure("Packer not found for type A (${A::class.simpleName}): ${e.message}")
    }

    val totalPayloadBits = packerA.bitSize + packerB.bitSize
    if (totalPayloadBits < 0) { // Should not happen with valid bitSize, but good check
        return PackingAttempt.PackingFailure("Total bit size for A and B must be non-negative.")
    }
    // Allow 0-bit payload to produce an NJoin with an empty segment list if that's desired,
    // or adjust segmentsRequired for 0 bits if it should always be 1 segment.
    // Current logic: (0 + 63 - 1) / 63 = 0 segments. If 0 bits means 1 empty segment, adjust.
    // For now, assuming 0 payload bits means 0 segments, which NJoin init might reject.
    // Let's assume 0 payload means 1 segment with 0 bits of data, NJoin should handle this.
    // Or, if NJoin requires non-empty packedBitsList, then kj should ensure it for 0 payload.
    // The NJoin init requires packedBitsList.isNotEmpty().
     val segmentsRequired = if (totalPayloadBits == 0) 1 else (totalPayloadBits + PAYLOAD_BITS_PER_SEGMENT - 1) / PAYLOAD_BITS_PER_SEGMENT


    if (segmentsRequired > MAX_PACKED_SEGMENTS) {
        return PackingAttempt.PackingFailure("Data too large for packing: requires $segmentsRequired segments (payload $totalPayloadBits bits), max is $MAX_PACKED_SEGMENTS.")
    }
    if (segmentsRequired == 0 && totalPayloadBits > 0) { // Should not occur with fixed formula
         return PackingAttempt.PackingFailure("Internal error: Calculated 0 segments for $totalPayloadBits bits.")
    }


    val packedBitsList = mutableListOf<ActualPackedBits>()
    var currentSegment: ActualPackedBits = 0L
    var bitsInCurrentSegment = 0
    var bitsProcessedForCurrentPayload = 0 // Tracks bits taken from current (A or B) payload

    // --- Pack B ---
    var bitsToPackFromB = packerB.bitSize
    var packedBValue = packerB.pack(this) and packerB.bitMask

    while (bitsToPackFromB > 0) {
        val spaceInSegment = PAYLOAD_BITS_PER_SEGMENT - bitsInCurrentSegment
        val bitsToTakeNow = minOf(bitsToPackFromB, spaceInSegment)

        // Take LSBs from packedBValue
        currentSegment = currentSegment or ((packedBValue shr bitsProcessedForCurrentPayload) and ((1L shl bitsToTakeNow) - 1)) shl bitsInCurrentSegment

        bitsInCurrentSegment += bitsToTakeNow
        bitsToPackFromB -= bitsToTakeNow
        bitsProcessedForCurrentPayload += bitsToTakeNow

        if (bitsInCurrentSegment == PAYLOAD_BITS_PER_SEGMENT) {
            // Continuation bit will be set if there are more bits for B OR any bits for A OR if more segments are planned
            if (bitsToPackFromB > 0 || packerA.bitSize > 0 || segmentsRequired > packedBitsList.size + 1) {
                currentSegment = currentSegment or CONTINUATION_BIT_MASK
            }
            packedBitsList.add(currentSegment)
            currentSegment = 0L
            bitsInCurrentSegment = 0
        }
    }
    bitsProcessedForCurrentPayload = 0 // Reset for A

    // --- Pack A ---
    var bitsToPackFromA = packerA.bitSize
    var packedAValue = packerA.pack(a) and packerA.bitMask

    while (bitsToPackFromA > 0) {
        val spaceInSegment = PAYLOAD_BITS_PER_SEGMENT - bitsInCurrentSegment
        val bitsToTakeNow = minOf(bitsToPackFromA, spaceInSegment)

        // Take LSBs from packedAValue
        currentSegment = currentSegment or ((packedAValue shr bitsProcessedForCurrentPayload) and ((1L shl bitsToTakeNow) - 1)) shl bitsInCurrentSegment

        bitsInCurrentSegment += bitsToTakeNow
        bitsToPackFromA -= bitsToTakeNow
        bitsProcessedForCurrentPayload += bitsToTakeNow

        if (bitsInCurrentSegment == PAYLOAD_BITS_PER_SEGMENT) {
            // Continuation bit if more bits for A OR if more segments are planned
            if (bitsToPackFromA > 0 || segmentsRequired > packedBitsList.size + 1) {
                currentSegment = currentSegment or CONTINUATION_BIT_MASK
            }
            packedBitsList.add(currentSegment)
            currentSegment = 0L
            bitsInCurrentSegment = 0
        }
    }

    // Add the last segment if it has any bits, or if it's the very first segment (even if empty for 0 total payload)
    if (bitsInCurrentSegment > 0 || packedBitsList.isEmpty()) {
        // No continuation bit on the very last segment
        packedBitsList.add(currentSegment)
    }

    // Ensure NJoin's requirement of non-empty packedBitsList is met.
    // If totalPayloadBits is 0, segmentsRequired is 1, so packedBitsList will have one 0L element.
    if (packedBitsList.isEmpty()) {
         return PackingAttempt.PackingFailure("Internal error: No segments produced. This should not happen.")
    }

    val nJoin = NJoin(packedBitsList.toList(), packerA, packerB, totalPayloadBits)
    return PackingAttempt.PackingSuccess(nJoin)
}

/**
 * Implements the 'jk' infix operator as requested by the user.
 * This operator takes a receiver `A` (which should be [IsPackable] as per its context in this file)
 * and a parameter `b` of any type `B`. It returns an standard, unpacked [Join<B, A>] instance,
 * with the type parameters effectively reversed compared to a typical `A.j(B)` -> `Join<A,B>`.
 *
 * This operator **does not perform any bit packing**. Its primary role is for type manipulation
 * or as a syntactic element in a DSL, potentially to prepare or arrange types before a
 * packing operation like `kj` might be called elsewhere. It uses the existing `Join` interface
 * (presumably defined in `TrikeShedCore.kt` or a similar base definition file).
 *
 * @param A The type of the receiver `this`. Although constrained to [IsPackable] by its location
 *          in this file, the `Join` interface itself may not require this.
 * @param B The type of the parameter `b`.
 * @param b The item to be the first component (`a`) in the resulting `Join<B, A>`.
 * @return An instance of `Join<B, A>` where `join.a` is `b` and `join.b` is `this`.
 */
infix fun <A : IsPackable, B> A.jk(b: B): Join<B, A> {
    val valueA = this // Capture the receiver instance
    return object : Join<B, A> {
        override val a: B get() = b
        override val b: A get() = valueA
    }
}

// TODO: Implement kjSeries - fun <reified T : IsPackable> kjSeries(size: Int, accessor: (Int) -> T): PackingAttempt<NSeries<T>>
// This will involve:
// 1. Packing the series size (e.g., using NSeries.SIZE_METADATA_BITS) into the first segment.
// 2. Iterating `size` times, calling `accessor(i)` to get each element.
// 3. Packing each element using `packerT = getPackerFromRegistry<T>()`.
// 4. Handling spilling across segments for both metadata and element data.
// 5. Constructing and returning PackingSuccess(NSeries(...)) or PackingFailure.

// --- Future: Tensor Packing Operator ---

/**
 * Placeholder for the `kjTensor` operator.
 * This function would be responsible for packing a multi-dimensional tensor, defined by its shape
 * and an accessor function, into an [NTensor] structure.
 *
 * Implementation would involve:
 * 1. Retrieving the [Packer] for type T.
 * 2. Packing tensor metadata:
 *    - Rank (number of dimensions).
 *    - Shape (size of each dimension).
 * 3. Iterating through all tensor elements (e.g., using nested loops or linear indexing):
 *    - Getting each element using the `accessor` function.
 *    - Packing each element using `packerT`.
 * 4. Managing data layout and spilling across multiple [ActualPackedBits] segments,
 *    similar to `kj` for `NJoin`, but accommodating metadata first, then element data.
 * 5. Calculating total bits and constructing the [NTensor] instance.
 * 6. Returning [PackingAttempt.PackingSuccess] with the [NTensor] or [PackingAttempt.PackingFailure].
 *
 * @param T The type of elements in the tensor. Must be [IsPackable].
 * @param shape An array defining the size of each dimension of the tensor.
 * @param accessor A function that takes an `IntArray` of coordinates and returns the element at that position.
 * @return A [PackingAttempt<NTensor<T>>] indicating success or failure of the packing operation.
 */
fun <reified T : IsPackable> kjTensor(
    shape: IntArray,
    accessor: (coords: IntArray) -> T
): PackingAttempt<NTensor<T>> {
    // TODO: Implement full tensor packing logic.
    // - Get packerT.
    // - Validate shape.
    // - Calculate bits for metadata (rank, dimensions) and total element bits.
    // - Determine segments required, handle MAX_PACKED_SEGMENTS.
    // - Pack metadata into initial bit segments.
    // - Iterate, get elements via accessor, pack elements, handle spilling.
    // - Construct NTensor.
    return PackingAttempt.PackingFailure("kjTensor is not yet implemented.")
}
