package borg.trikeshed.lib

import kotlin.jvm.JvmInline
import evolution.*

// Import Series and Join directly from borg.trikeshed.core
import borg.trikeshed.core.Series // Still needed for ByteSeries.borrowFromSeries if its param remains Series<Byte>
import borg.trikeshed.core.Join
import borg.trikeshed.core.j // For the infix j function
// borg.trikeshed.core.toSeries might not be directly used here for new series creation from raw types.
// but ByteSeries and Series<Byte> parameters will now correctly refer to core types.

// Type alias for the underlying structure of Series<Byte> to improve readability locally
private typealias ByteSeriesData = Pair<Int, (Int) -> Byte>

// === BORROW CHECKER MEMORY MANAGEMENT ===
// Rust-inspired borrow checking for safe ByteArray management with TrikeShed patterns

// Memory management ontology using typealiases
typealias BorrowId = ULong                  // Unique borrow identifier
typealias LifetimeId = ULong                // Lifetime scope identifier
typealias MemoryRegionSize = ULong          // Size of borrowed memory
typealias AllocationId = ULong              // Memory allocation tracking

// Memory management compositions using Join<A,B>
typealias BorrowedRegion = Join<BorrowId, Join<MemoryRegionSize, ByteArray>>
typealias LifetimeScope = Join<LifetimeId, Join<AllocationId, BorrowedRegion>>

// Borrow-checked buffer for zero-copy operations

// Internal data class to hold BorrowedBuffer's underlying data
internal data class BorrowedBufferData(
    val bytes: ByteArray,
    val borrowId: BorrowId,
    val lifetimeId: LifetimeId
)

// @JvmInline // Temporarily remove for diagnostics
class BorrowedBuffer internal constructor( // Made internal as it was private, companion object handles creation
    private val data: BorrowedBufferData
) {
    val bytes: ByteArray get() = data.bytes
    val borrowId: BorrowId get() = data.borrowId
    val lifetime: LifetimeId get() = data.lifetimeId
    
    companion object {
        internal var nextBorrowId = 0uL
        internal var nextLifetimeId = 0uL
        internal val activeBorrows = mutableSetOf<BorrowId>()
        
        fun borrow(buffer: ByteArray): BorrowedBuffer {
            val borrowId = ++nextBorrowId
            val lifetime = ++nextLifetimeId
            activeBorrows.add(borrowId)
            
            return BorrowedBuffer(BorrowedBufferData(buffer, borrowId, lifetime))
        }
        
        fun release(borrowed: BorrowedBuffer) {
            activeBorrows.remove(borrowed.borrowId)
        }
        
        fun checkBorrow(borrowId: BorrowId): Boolean = borrowId in activeBorrows
        
        // Create borrowed buffer from ByteSeries
        fun borrowFromSeries(series: ByteSeries): BorrowedBuffer = 
            borrow(series.toArray())
            
        // Create borrowed buffer from Series<Byte> (now Pair<Int, (Int) -> Byte>)
        fun borrowFromSeries(seriesData: ByteSeriesData): BorrowedBuffer =
            borrow(ByteArray(seriesData.first, seriesData.second)) // Reconstruct ByteArray from Pair
    }
    
    // Convert to ByteSeries for TrikeShed processing
    fun toByteSeries(): ByteSeries = ByteSeries(bytes.toSeries()) // bytes.toSeries() will create Pair<Int,(Int)->Byte>
    
    // Convert to Series<Byte> (now Pair<Int, (Int) -> Byte>)
    fun toSeries(): ByteSeriesData = bytes.size j bytes::get
    
    // Create slice without copying data - returns new borrow
    fun slice(start: Int, length: Int): BorrowedBuffer? {
        if (start < 0 || length < 0 || start + length > bytes.size) return null
        
        val sliced = bytes.sliceArray(start until start + length)
        return borrow(sliced)
    }
    
    // Zero-copy view of region
    fun view(start: Int, length: Int): BorrowedView? {
        if (start < 0 || length < 0 || start + length > bytes.size) return null
        
        return BorrowedView.create(this, start.toULong(), length.toULong())
    }
}

// Zero-copy view into borrowed buffer

// Internal data class to hold BorrowedView's underlying data
internal data class BorrowedViewData(
    val buffer: BorrowedBuffer,
    val offset: ULong,
    val length: ULong
)

// @JvmInline // Temporarily remove for diagnostics
class BorrowedView internal constructor( // Made internal as it was private, companion object handles creation
    private val data: BorrowedViewData
) {
    val buffer: BorrowedBuffer get() = data.buffer
    val offset: ULong get() = data.offset
    val length: ULong get() = data.length
    
    companion object {
        internal fun create(buffer: BorrowedBuffer, offset: ULong, length: ULong): BorrowedView =
            BorrowedView(BorrowedViewData(buffer, offset, length))
    }
    
    // Get byte at position within view
    fun getByte(position: ULong): Byte? {
        if (position >= length) return null
        return buffer.bytes[(offset + position).toInt()]
    }
    
    // Convert view to ByteArray (copies data)
    fun toByteArray(): ByteArray = 
        buffer.bytes.sliceArray(offset.toInt() until (offset + length).toInt())
        
    // Convert to ByteSeries
    fun toByteSeries(): ByteSeries = ByteSeries(toByteArray())
}

// Lifetime scope management for automatic cleanup
object BorrowChecker {
    private val lifetimeScopes = mutableMapOf<LifetimeId, MutableSet<BorrowId>>()
    
    fun enterScope(): LifetimeId {
        val lifetime = ++BorrowedBuffer.nextLifetimeId
        lifetimeScopes[lifetime] = mutableSetOf()
        return lifetime
    }
    
    fun exitScope(lifetime: LifetimeId) {
        lifetimeScopes[lifetime]?.forEach { borrowId ->
            BorrowedBuffer.activeBorrows.remove(borrowId)
        }
        lifetimeScopes.remove(lifetime)
    }
    
    fun trackBorrow(lifetime: LifetimeId, borrowId: BorrowId) {
        lifetimeScopes[lifetime]?.add(borrowId)
    }
    
    fun validateAccess(borrowed: BorrowedBuffer): Boolean =
        BorrowedBuffer.checkBorrow(borrowed.borrowId)
        
    // Automatic scope management with lambda
    fun <T> withScope(block: (LifetimeId) -> T): T {
        val lifetime = enterScope()
        try {
            return block(lifetime)
        } finally {
            exitScope(lifetime)
        }
    }
    
    // Get active borrow statistics
    fun getBorrowStats(): Join<ULong, ULong> {
        val activeCount = BorrowedBuffer.activeBorrows.size.toULong()
        val nextId = BorrowedBuffer.nextBorrowId
        return activeCount j nextId
    }
}

// Extension functions for Series<Byte> (now Pair<Int, (Int) -> Byte>) integration
fun ByteSeriesData.borrow(): BorrowedBuffer = BorrowedBuffer.borrowFromSeries(this)
// ByteSeries.borrow() extension can remain if ByteSeries.toArray() is efficient enough
// or if borrowFromSeries is adapted for ByteSeries directly.
// For now, assuming ByteSeries.toArray() is used by the existing ByteSeries.borrow()
fun ByteSeries.borrow(): BorrowedBuffer = BorrowedBuffer.borrowFromSeries(this.toArray().toSeries())


// Safe access patterns with automatic cleanup
fun <T> BorrowedBuffer.use(block: (BorrowedBuffer) -> T): T {
    return try {
        block(this)
    } finally {
        BorrowedBuffer.release(this)
    }
}

// Multi-buffer operations with lifetime tracking
class BorrowScope(private val lifetime: LifetimeId) {
    private val scopedBorrows = mutableSetOf<BorrowId>()
    
    fun borrow(buffer: ByteArray): BorrowedBuffer {
        val borrowed = BorrowedBuffer.borrow(buffer)
        scopedBorrows.add(borrowed.borrowId)
        BorrowChecker.trackBorrow(lifetime, borrowed.borrowId)
        return borrowed
    }
    
    fun borrowSeries(seriesData: ByteSeriesData): BorrowedBuffer { // Parameter changed
        val borrowed = BorrowedBuffer.borrowFromSeries(seriesData)
        scopedBorrows.add(borrowed.borrowId)
        BorrowChecker.trackBorrow(lifetime, borrowed.borrowId)
        return borrowed
    }
    
    fun borrowByteSeries(series: ByteSeries): BorrowedBuffer {
        val borrowed = BorrowedBuffer.borrowFromSeries(series)
        scopedBorrows.add(borrowed.borrowId)
        BorrowChecker.trackBorrow(lifetime, borrowed.borrowId)
        return borrowed
    }
    
    fun release(borrowed: BorrowedBuffer) {
        if (borrowed.borrowId in scopedBorrows) {
            BorrowedBuffer.release(borrowed)
            scopedBorrows.remove(borrowed.borrowId)
        }
    }
    
    internal fun cleanup() {
        scopedBorrows.forEach { borrowId ->
            BorrowedBuffer.activeBorrows.remove(borrowId)
        }
        scopedBorrows.clear()
    }
}

// Scoped borrow management for complex operations
fun <T> borrowScope(block: BorrowScope.() -> T): T {
    val lifetime = BorrowChecker.enterScope()
    val scope = BorrowScope(lifetime)
    return try {
        scope.block()
    } finally {
        scope.cleanup()
        BorrowChecker.exitScope(lifetime)
    }
}