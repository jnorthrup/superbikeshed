package ta4k.cascading

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*
import kotlinx.datetime.*

/**
 * OHLCV Cascading with TrikeShed Cursors
 * 
 * Pure cursor magic for financial time series aggregation
 */

// Core OHLCV data
data class Tick(
    val symbol: String,
    val timestamp: Instant,
    val price: Double,
    val volume: Long,
    val bid: Double? = null,
    val ask: Double? = null
)

data class OHLCV(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val vwap: Double,
    val tickCount: Int,
    val timeframe: Timeframe
)

enum class Timeframe(val seconds: Int) {
    TICK(0),
    SECOND(1),
    MINUTE(60),
    MINUTE_5(300),
    MINUTE_15(900),
    HOUR(3600),
    HOUR_4(14400),
    DAY(86400),
    WEEK(604800),
    MONTH(2592000),
    YEAR(31536000)
}

/**
 * The magic: Cursor-based OHLCV aggregation
 */
object OHLCVCascade {
    
    /**
     * Convert ticks to cursor format
     */
    fun ticksToCursor(ticks: List<Tick>): Cursor {
        val rows = ticks.map { tick ->
            listOf(
                tick.symbol,
                tick.timestamp.toEpochMilliseconds(),
                tick.price,
                tick.volume,
                tick.bid ?: tick.price,
                tick.ask ?: tick.price
            )
        }
        
        return cursorOf(
            rows,
            listOf("symbol", "timestamp", "price", "volume", "bid", "ask"),
            listOf(
                IOMemento.IoString,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoDouble
            )
        )
    }
    
    /**
     * The cascading magic - aggregate ticks to any timeframe
     */
    fun Cursor.toOHLCV(
        timeframe: Timeframe,
        symbolFilter: String? = null
    ): Cursor {
        // Group by symbol and time bucket
        val grouped = this
            .filter { row ->
                symbolFilter == null || row.getString(0) == symbolFilter
            }
            .map { row ->
                val symbol = row.getString(0)!!
                val timestamp = row.getLong(1)!!
                val bucket = (timestamp / (timeframe.seconds * 1000)) * (timeframe.seconds * 1000)
                
                Triple(symbol, bucket, row)
            }
            .let { mappedCursor ->
                // Manual grouping (cursor doesn't have groupBy yet)
                val groups = mutableMapOf<Pair<String, Long>, MutableList<RowVec>>()
                
                for (i in 0 until mappedCursor.a) {
                    val (symbol, bucket, row) = mappedCursor.at(i)
                    groups.getOrPut(symbol to bucket) { mutableListOf() }.add(row)
                }
                
                groups
            }
        
        // Aggregate each group to OHLCV
        val ohlcvRows = grouped.map { (key, ticks) ->
            val (symbol, bucket) = key
            
            // Sort by timestamp for correct open/close
            val sorted = ticks.sortedBy { it.getLong(1)!! }
            
            val open = sorted.first().getDouble(2)!!
            val close = sorted.last().getDouble(2)!!
            val high = sorted.maxOf { it.getDouble(2)!! }
            val low = sorted.minOf { it.getDouble(2)!! }
            val volume = sorted.sumOf { it.getLong(3)!! }
            
            // Volume-weighted average price
            val vwapNumerator = sorted.sumOf { 
                it.getDouble(2)!! * it.getLong(3)!!
            }
            val vwap = if (volume > 0) vwapNumerator / volume else close
            
            listOf(
                symbol,
                bucket,
                open,
                high,
                low,
                close,
                volume,
                vwap,
                sorted.size,
                timeframe.name
            )
        }
        
        return cursorOf(
            ohlcvRows,
            listOf("symbol", "timestamp", "open", "high", "low", "close", "volume", "vwap", "tick_count", "timeframe"),
            listOf(
                IOMemento.IoString,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoInt,
                IOMemento.IoString
            )
        )
    }
    
    /**
     * Cascade OHLCV to higher timeframes
     */
    fun Cursor.cascadeOHLCV(
        fromTimeframe: Timeframe,
        toTimeframe: Timeframe
    ): Cursor {
        require(toTimeframe.seconds > fromTimeframe.seconds) {
            "Can only cascade to higher timeframes"
        }
        
        // Group by new time buckets
        val ratio = toTimeframe.seconds / fromTimeframe.seconds
        
        val grouped = this
            .map { row ->
                val symbol = row.getString(0)!!
                val timestamp = row.getLong(1)!!
                val newBucket = (timestamp / (toTimeframe.seconds * 1000)) * (toTimeframe.seconds * 1000)
                
                Triple(symbol, newBucket, row)
            }
            .let { mapped ->
                val groups = mutableMapOf<Pair<String, Long>, MutableList<RowVec>>()
                
                for (i in 0 until mapped.a) {
                    val (symbol, bucket, row) = mapped.at(i)
                    groups.getOrPut(symbol to bucket) { mutableListOf() }.add(row)
                }
                
                groups
            }
        
        // Re-reduce OHLCV (this is where the magic happens!)
        val cascadedRows = grouped.map { (key, ohlcvBars) ->
            val (symbol, bucket) = key
            
            // Sort by timestamp for correct open/close
            val sorted = ohlcvBars.sortedBy { it.getLong(1)!! }
            
            // OHLCV cascade logic - preserves integrity!
            val open = sorted.first().getDouble(2)!!  // First open
            val close = sorted.last().getDouble(5)!!  // Last close
            val high = sorted.maxOf { it.getDouble(3)!! }  // Max of highs
            val low = sorted.minOf { it.getDouble(4)!! }   // Min of lows
            val volume = sorted.sumOf { it.getLong(6)!! }  // Sum volumes
            
            // Weighted VWAP
            val vwapNumerator = sorted.sumOf {
                it.getDouble(7)!! * it.getLong(6)!!  // vwap * volume
            }
            val vwap = if (volume > 0) vwapNumerator / volume else close
            
            val tickCount = sorted.sumOf { it.getInt(8)!! }
            
            listOf(
                symbol,
                bucket,
                open,
                high,
                low,
                close,
                volume,
                vwap,
                tickCount,
                toTimeframe.name
            )
        }
        
        return cursorOf(
            cascadedRows,
            columnNames.toList(),
            listOf(
                IOMemento.IoString,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoDouble,
                IOMemento.IoLong,
                IOMemento.IoDouble,
                IOMemento.IoInt,
                IOMemento.IoString
            )
        )
    }
    
    /**
     * Technical indicator calculations on OHLCV cursor
     */
    fun Cursor.withIndicators(): Cursor {
        // Add SMA, RSI, MACD etc as new columns
        val sma20 = this.sma(20, "close")
        val sma50 = this.sma(50, "close")
        val rsi14 = this.rsi(14)
        
        // Combine into extended cursor
        return this.addColumns(
            "sma20" to sma20,
            "sma50" to sma50,
            "rsi14" to rsi14
        )
    }
    
    // Technical indicator helpers
    
    internal fun Cursor.sma(period: Int, column: String): Indexed<Double?> {
        val colIndex = columnNames.toList().indexOf(column)
        require(colIndex >= 0) { "Column $column not found" }
        
        return a j { rowIndex ->
            if (rowIndex < period - 1) {
                null
            } else {
                val sum = (0 until period).sumOf { i ->
                    at(rowIndex - i).getDouble(colIndex) ?: 0.0
                }
                sum / period
            }
        }
    }
    
    internal fun Cursor.rsi(period: Int): Indexed<Double?> {
        val closeIndex = columnNames.toList().indexOf("close")
        require(closeIndex >= 0) { "Close column not found" }
        
        return a j { rowIndex ->
            if (rowIndex < period) {
                null
            } else {
                var gains = 0.0
                var losses = 0.0
                
                for (i in 1..period) {
                    val prev = at(rowIndex - i).getDouble(closeIndex) ?: 0.0
                    val curr = at(rowIndex - i + 1).getDouble(closeIndex) ?: 0.0
                    val change = curr - prev
                    
                    if (change > 0) gains += change
                    else losses += -change
                }
                
                val avgGain = gains / period
                val avgLoss = losses / period
                
                if (avgLoss == 0.0) 100.0
                else {
                    val rs = avgGain / avgLoss
                    100.0 - (100.0 / (1.0 + rs))
                }
            }
        }
    }
    
    internal fun Cursor.addColumns(vararg columns: Pair<String, Indexed<*>>): Cursor {
        // Implementation to add new columns to cursor
        // This is a simplified version - real implementation would be more sophisticated
        val newRows = (0 until a).map { rowIndex ->
            val originalRow = at(rowIndex).let { row ->
                (0 until row.a).map { i -> row.b(i).a }
            }
            
            val newValues = columns.map { (_, indexed) ->
                indexed.b(rowIndex)
            }
            
            originalRow + newValues
        }
        
        val newColumnNames = columnNames.toList() + columns.map { it.first }
        val newColumnTypes = (0 until columnNames.a).map { IOMemento.IoDouble } +
                            columns.map { IOMemento.IoDouble }
        
        return cursorOf(newRows, newColumnNames, newColumnTypes)
    }
}