package evolution

import borg.trikeshed.lib.Join
import kotlin.jvm.JvmInline

// Assuming HttpHeaderName and HttpHeaderValue are defined elsewhere, e.g., in Http.kt,
// and are already refactored to strong types if appropriate.
// For example:
// @JvmInline value class HttpHeaderName(val value: String)
// @JvmInline value class HttpHeaderValue(val value: String)
// We will import them if they are in the evolution package.
import evolution.HttpHeaderName // Placeholder import
import evolution.HttpHeaderValue // Placeholder import
import evolution.Http3MaxTableCapacity // Import from Http3.kt (where it was defined)

// --- QPACK Strong Types (Value Classes) ---
// These could be moved to a dedicated QpackSpecTypes.kt or Http3SpecTypes.kt

/**
 * Represents the current number of entries in the QPACK dynamic table.
 * Also used for Required Insert Count in decoder instructions.
 */
@JvmInline
value class QpackInsertCount(val value: ULong)

/**
 * Represents an index into a QPACK table (static or dynamic).
 */
@JvmInline
value class QpackTableIndex(val value: ULong) // Replaces QpackIndexedFieldLine

/**
 * Represents a header field line provided as a literal string.
 * This might be "name: value" or just the value if the name is indexed.
 */
@JvmInline
value class LiteralHeaderFieldString(val value: String) // Replaces QpackLiteralFieldLine


// --- QPACK Compositions (Type Aliases using new strong types) ---

/**
 * Represents the state of a QPACK decoder.
 * - Http3MaxTableCapacity: The maximum capacity of the dynamic table.
 * - QpackInsertCount: The current number of entries in the dynamic table.
 * - Map<QpackTableIndex, QpackFieldLine>: The dynamic table entries, indexed.
 */
typealias QpackDecoderState = Join<Http3MaxTableCapacity, Join<QpackInsertCount, Map<QpackTableIndex, QpackFieldLine>>>

/**
 * Represents the state of a QPACK encoder.
 * - Http3MaxTableCapacity: The maximum capacity of the dynamic table.
 * - QpackInsertCount: The current number of entries inserted.
 * - List<QpackFieldLine>: A list of field lines, possibly representing the dynamic table or a list of headers to encode.
 */
typealias QpackEncoderState = Join<Http3MaxTableCapacity, Join<QpackInsertCount, List<QpackFieldLine>>>

/**
 * Represents a QPACK field line as a join of its name and value.
 * Assumes HttpHeaderName and HttpHeaderValue are appropriately typed (e.g., value classes).
 */
typealias QpackFieldLine = Join<HttpHeaderName, HttpHeaderValue>

/**
 * Represents a QPACK header block.
 * - QpackInsertCount: The value of the Required Insert Count from the Encoder Stream.
 * - Http3MaxTableCapacity: The dynamic table capacity.
 * - ByteArray: The actual encoded header block data.
 */
typealias QpackHeaderBlock = Join<QpackInsertCount, Join<Http3MaxTableCapacity, ByteArray>>


// --- Old Type Aliases (Now Replaced or Redefined) ---
// typealias QpackIndexedFieldLine = ULong       // Now QpackTableIndex
// typealias QpackInsertCount = ULong            // Now value class QpackInsertCount
// typealias QpackLiteralFieldLine = String      // Now LiteralHeaderFieldString
// typealias QpackTableCapacity = ULong          // Now using Http3MaxTableCapacity from Http3.kt