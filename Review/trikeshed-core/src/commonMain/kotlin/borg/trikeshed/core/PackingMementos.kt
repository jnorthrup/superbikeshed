package borg.trikeshed.core

/**
 * This file defines "memento" data classes used for external introspection or
 * serialization of packed data structure metadata. These mementos provide a
 * snapshot of the structural characteristics of `NJoin` and `NSeries` instances.
 */

/**
 * A memento object that describes the structural characteristics of an [NJoin] instance.
 * This can be used for debugging, schema representation, or external introspection.
 *
 * @property typeA The simple name of type A in the NJoin<A, B>.
 * @property bitSizeA The bit size allocated for type A.
 * @property typeB The simple name of type B in the NJoin<A, B>.
 * @property bitSizeB The bit size allocated for type B.
 * @property packingOrder A description of the packing order (e.g., "B then A").
 * @property totalPayloadBits The total number of bits used for the payload (A + B).
 * @property isSpilled Indicates if the packed data spans multiple segments.
 * @property segmentCount The number of `ActualPackedBits` segments used.
 * @property bitsPerSegmentPayload The number of payload bits available per segment (e.g., 63).
 * @property continuationBit Indicates if a continuation bit mechanism is used.
 */
data class NJoinMemento(
    val typeA: String,
    val bitSizeA: Int,
    val typeB: String,
    val bitSizeB: Int,
    val packingOrder: String, // e.g., "B then A starting at LSB"
    val totalPayloadBits: Int,
    val isSpilled: Boolean,
    val segmentCount: Int,
    val bitsPerSegmentPayload: Int = PAYLOAD_BITS_PER_SEGMENT, // From PackingOperators.kt
    val continuationBit: Boolean = true // Assuming continuation mechanism is active
)

/**
 * A memento object that describes the structural characteristics of an [NSeries] instance.
 * This can be used for debugging, schema representation, or external introspection.
 *
 * @property typeT The simple name of the element type T in the NSeries<T>.
 * @property bitSizeT The bit size allocated for each element of type T.
 * @property seriesLength The number of elements in the series.
 * @property metadataBitSize The number of bits used for series metadata (e.g., storing the seriesLength).
 * @property totalElementPayloadBits The total number of bits used for all elements in the series (seriesLength * bitSizeT).
 * @property totalBitsWithMetadata The total number of bits including element data and series metadata.
 * @property isSpilled Indicates if the packed data spans multiple segments.
 * @property segmentCount The number of `ActualPackedBits` segments used.
 * @property bitsPerSegmentPayload The number of payload bits available per segment.
 * @property continuationBit Indicates if a continuation bit mechanism is used.
 */
data class NSeriesMemento(
    val typeT: String,
    val bitSizeT: Int,
    val seriesLength: Int,
    val metadataBitSize: Int, // e.g., NSeries.SIZE_METADATA_BITS
    val totalElementPayloadBits: Int,
    val totalBitsWithMetadata: Int,
    val isSpilled: Boolean,
    val segmentCount: Int,
    val bitsPerSegmentPayload: Int = PAYLOAD_BITS_PER_SEGMENT, // From PackingOperators.kt
    val continuationBit: Boolean = true // Assuming continuation mechanism is active
)

// Future: Could add functions here or as extensions on NJoin/NSeries to create these mementos.
// e.g.:
// fun <A: IsPackable, B: IsPackable> NJoin<A,B>.toMemento(typeAString: String, typeBString: String): NJoinMemento {
//     return NJoinMemento(
//         typeA = typeAString,
//         bitSizeA = this.packerA.bitSize, // Requires packerA to be accessible
//         typeB = typeBString,
//         bitSizeB = this.packerB.bitSize, // Requires packerB to be accessible
//         packingOrder = "B then A starting at LSB", // Convention from kj
//         totalPayloadBits = this.totalBitSize,
//         isSpilled = this.isSpilled(),
//         segmentCount = this.packedBitsList.size
//     )
// }
// To make packers accessible, NJoin/NSeries would need to expose them or have methods that use them.
