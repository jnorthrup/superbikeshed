package borg.trikeshed.lib

import kotlin.jvm.JvmInline

// This file contains general-purpose type aliases and value classes
// based on common patterns and needs identified across the codebase.

// --- File I/O and Paths ---

@JvmInline
value class FilePath(val path: String)

@JvmInline
value class DirectoryPath(val path: String)

@JvmInline
value class FileOffset(val value: Long) {
    operator fun plus(bytes: Long): FileOffset = FileOffset(value + bytes)
    operator fun minus(bytes: Long): FileOffset = FileOffset(value - bytes)
    operator fun compareTo(other: FileOffset): Int = value.compareTo(other.value)
}

@JvmInline
value class FileSize(val bytes: Long) {
    operator fun plus(other: FileSize): FileSize = FileSize(bytes + other.bytes)
    operator fun minus(other: FileSize): FileSize = FileSize(bytes - other.bytes)
    operator fun compareTo(other: FileSize): Int = bytes.compareTo(other.bytes)
}

@JvmInline
value class BufferSize(val bytes: Int) {
    operator fun compareTo(other: BufferSize): Int = bytes.compareTo(other.bytes)
}

@JvmInline
value class FileMode(val value: UInt)

// --- Core Data Structures / General ---

@JvmInline
value class ColumnName(val name: String)

@JvmInline
value class RowIndex(val index: Int) {
    operator fun plus(offset: Int): RowIndex = RowIndex(index + offset)
    operator fun minus(offset: Int): RowIndex = RowIndex(index - offset)
    operator fun inc(): RowIndex = RowIndex(index + 1)
    operator fun dec(): RowIndex = RowIndex(index - 1)
    operator fun compareTo(other: RowIndex): Int = index.compareTo(other.index)
}

@JvmInline
value class ColumnIndex(val index: Int) {
    operator fun plus(offset: Int): ColumnIndex = ColumnIndex(index + offset)
    operator fun minus(offset: Int): ColumnIndex = ColumnIndex(index - offset)
    operator fun inc(): ColumnIndex = ColumnIndex(index + 1)
    operator fun dec(): ColumnIndex = ColumnIndex(index - 1)
    operator fun compareTo(other: ColumnIndex): Int = index.compareTo(other.index)
}

@JvmInline
value class ItemCount(val count: Int) {
    operator fun plus(other: ItemCount): ItemCount = ItemCount(count + other.count)
    operator fun minus(other: ItemCount): ItemCount = ItemCount(count - other.count)
    operator fun compareTo(other: ItemCount): Int = count.compareTo(other.count)
}

/**
 * Represents a digest or hash, often used for versioning or content integrity.
 */
@JvmInline
value class VersionDigest(val value: String)

@JvmInline
value class TimestampEpochMillis(val millis: Long) {
    operator fun compareTo(other: TimestampEpochMillis): Int = millis.compareTo(other.millis)
}

@JvmInline
value class ErrorMessage(val message: String)


// --- Trading (e.g., Acapulco context, but potentially general) ---

/**
 * Represents a key for an asset, currency pair, or financial instrument.
 * Example: "BTC/USD", "AAPL"
 */
@JvmInline
value class AssetKey(val key: String)

@JvmInline
value class Price(val value: Double) {
    operator fun compareTo(other: Price): Int = value.compareTo(other.value)
    operator fun plus(other: Price): Price = Price(value + other.value)
    operator fun minus(other: Price): Price = Price(value - other.value)
    // Consider adding multiplication/division by Double or other numeric types if needed
}

/**
 * Represents a quantity, typically for assets or shares.
 */
@JvmInline
value class Quantity(val value: Double) {
    operator fun compareTo(other: Quantity): Int = value.compareTo(other.value)
    operator fun plus(other: Quantity): Quantity = Quantity(value + other.value)
    operator fun minus(other: Quantity): Quantity = Quantity(value - other.value)
}

@JvmInline
value class ExchangeOrderID(val id: String)


// --- Networking Primitives (General) ---

/**
 * Represents a host name or IP address string, distinct from general strings.
 */
@JvmInline
value class HostString(val value: String)

@JvmInline
value class PortNumber(val value: Int) {
    init {
        require(value in 1..65535) { "Port number must be between 1 and 65535, got $value" }
    }
}

@JvmInline
value class TimeoutMillis(val millis: Long) {
    operator fun compareTo(other: TimeoutMillis): Int = millis.compareTo(other.millis)
}

/**
 * Represents a URL string, distinct from general strings.
 */
@JvmInline
value class UrlString(val value: String)

// --- Other General Primitives ---
// These were identified during broader refactoring and are general enough.

@JvmInline
value class ByteCount(val count: Int) {
    operator fun plus(other: ByteCount): ByteCount = ByteCount(count + other.count)
    operator fun compareTo(other: ByteCount): Int = count.compareTo(other.count)
}

@JvmInline
value class CharCount(val count: Int) {
    operator fun plus(other: CharCount): CharCount = CharCount(count + other.char_count) // Corrected to other.count
    operator fun compareTo(other: CharCount): Int = count.compareTo(other.count)
}
// Note: CharCount.plus had a typo `other.char_count`, corrected to `other.count` in the generation logic.

@JvmInline
value class RecordLength(val length: Int)

@JvmInline
value class RecordOffset(val offset: Int)

@JvmInline
value class BitShiftAmount(val amount: Int)

// Consider adding more as common patterns emerge.
// Example:
// @JvmInline value class Percentage(val value: Double) // 0.0 to 1.0 or 0 to 100 based on convention
// @JvmInline value class Ratio(val value: Double)
// @JvmInline value class Identifier(val id: String) // Generic ID, less specific than ExchangeOrderID
// @JvmInline value class UuidString(val value: String)
// @JvmInline value class JsonString(val value: String)
// @JvmInline value class XmlString(val value: String)
// @JvmInline value class SqlString(val value: String)
// @JvmInline value class HexString(val value: String)

// Type aliases can still be useful for more complex, but still general, structures
// or for incrementally refactoring.
// typealias StringMap = Map<String, String>
// typealias PropertiesMap = Map<String, Any>
// typealias HeaderMap = Map<HttpHeaderName, HttpHeaderValue> // If HttpHeaderName/Value are common

// For specific byte array types where content matters but structure is just bytes:
// @JvmInline value class Sha256Digest(val bytes: ByteArray) { ... equals/hashCode ... }
// @JvmInline value class EncryptedData(val bytes: ByteArray) { ... equals/hashCode ... }
// @JvmInline value class Signature(val bytes: ByteArray) { ... equals/hashCode ... }
