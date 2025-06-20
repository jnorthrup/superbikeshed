#!/usr/bin/env k2script

/**
 * Acapulco Kline Ingest to ISAM DSL
 * 
 * This DSL provides an inductive walkthrough of the kline ingest pipeline
 * from CSV parsing through ISAM storage with Cursor integration and IO opt-ins.
 * 
 * The DSL follows TrikeShed patterns and integrates with Cursor telemetry.
 */

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
@file:DependsOn("com.google.trike:trikeshed-core:0.1.0")
@file:DependsOn("com.google.trike:trikeshed-isam:0.1.0")
@file:DependsOn("com.google.trike:trikeshed-cursor:0.1.0")

import kotlinx.coroutines.*
import kotlinx.datetime.*
import borg.trikeshed.cursor.*
import borg.trikeshed.isam.*
import borg.trikeshed.lib.*
import borg.trikeshed.io.*
import java.io.*
import java.nio.file.*
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

// ============================================================================
// DSL CORE TYPES AND INTERFACES
// ============================================================================

/**
 * DSL for Acapulco kline ingest pipeline
 */
class AcapulcoKlineDSL {
    
    // Pipeline configuration
    private var config = KlineIngestConfig()
    private val telemetry = CursorTelemetry()
    private val ioOptIns = mutableSetOf<IOOptIn>()
    
    // Pipeline stages
    private var csvSource: CsvSource? = null
    private var parser: KlineParser? = null
    private var transformer: KlineTransformer? = null
    private var storage: IsamStorage? = null
    private var cursor: Cursor? = null
    
    /**
     * Configure the kline ingest pipeline
     */
    fun configure(block: KlineIngestConfig.() -> Unit): AcapulcoKlineDSL {
        config.block()
        return this
    }
    
    /**
     * Enable IO opt-ins for performance optimization
     */
    fun withIOOptIns(vararg optIns: IOOptIn): AcapulcoKlineDSL {
        ioOptIns.addAll(optIns)
        return this
    }
    
    /**
     * Define CSV source
     */
    fun fromCsv(block: CsvSource.() -> Unit): AcapulcoKlineDSL {
        csvSource = CsvSource().apply(block)
        return this
    }
    
    /**
     * Define parsing strategy
     */
    fun parseWith(block: KlineParser.() -> Unit): AcapulcoKlineDSL {
        parser = KlineParser().apply(block)
        return this
    }
    
    /**
     * Define transformation pipeline
     */
    fun transformWith(block: KlineTransformer.() -> Unit): AcapulcoKlineDSL {
        transformer = KlineTransformer().apply(block)
        return this
    }
    
    /**
     * Define ISAM storage configuration
     */
    fun storeToIsam(block: IsamStorage.() -> Unit): AcapulcoKlineDSL {
        storage = IsamStorage().apply(block)
        return this
    }
    
    /**
     * Execute the pipeline
     */
    suspend fun execute(): Cursor {
        telemetry.trackPipelineStart()
        
        return try {
            // Stage 1: CSV Source
            val csvData = csvSource?.read() ?: throw IllegalStateException("CSV source not configured")
            telemetry.trackStage("csv_read", csvData.size)
            
            // Stage 2: Parsing
            val parsedKlines = parser?.parse(csvData) ?: throw IllegalStateException("Parser not configured")
            telemetry.trackStage("parse", parsedKlines.size)
            
            // Stage 3: Transformation
            val transformedData = transformer?.transform(parsedKlines) ?: parsedKlines
            telemetry.trackStage("transform", transformedData.size)
            
            // Stage 4: ISAM Storage
            val isamCursor = storage?.store(transformedData) ?: throw IllegalStateException("Storage not configured")
            telemetry.trackStage("store", isamCursor.a)
            
            cursor = isamCursor
            telemetry.trackPipelineSuccess()
            
            isamCursor
        } catch (e: Exception) {
            telemetry.trackPipelineError(e)
            throw e
        }
    }
    
    /**
     * Get the resulting cursor
     */
    fun getCursor(): Cursor? = cursor
    
    /**
     * Analyze the ingested data
     */
    fun analyze(): KlineAnalysis {
        val cursor = cursor ?: throw IllegalStateException("No cursor available - run execute() first")
        return KlineAnalysis(cursor)
    }
}

// ============================================================================
// CONFIGURATION TYPES
// ============================================================================

/**
 * Configuration for kline ingest pipeline
 */
data class KlineIngestConfig(
    var batchSize: Int = 1000,
    var enableValidation: Boolean = true,
    var enableCompression: Boolean = false,
    var enableIndexing: Boolean = true,
    var asyncIO: Boolean = true,
    var memoryMapping: Boolean = true,
    var cacheAlignment: Boolean = true,
    var telemetryEnabled: Boolean = true,
    var errorHandling: ErrorHandlingStrategy = ErrorHandlingStrategy.SKIP_INVALID,
    var performanceMode: PerformanceMode = PerformanceMode.BALANCED
)

enum class ErrorHandlingStrategy {
    SKIP_INVALID, THROW_ON_ERROR, LOG_AND_CONTINUE
}

enum class PerformanceMode {
    MEMORY_OPTIMIZED, SPEED_OPTIMIZED, BALANCED
}

/**
 * IO optimization options
 */
enum class IOOptIn {
    ASYNC_URING,      // Use io_uring for async I/O
    SCATTER_GATHER,   // Use scatter-gather I/O
    MEMORY_MAPPING,   // Use memory mapping
    CACHE_ALIGNED,    // Align data to cache lines
    UNBUFFERED_IO,    // Use unbuffered I/O
    BATCH_PROCESSING, // Process data in batches
    COMPRESSION,      // Enable compression
    INDEXING          // Enable indexing
}

// ============================================================================
// PIPELINE STAGES
// ============================================================================

/**
 * CSV Source stage
 */
class CsvSource {
    private var filePath: String? = null
    private var encoding: String = "UTF-8"
    private var hasHeader: Boolean = true
    private var delimiter: String = ","
    private var skipLines: Int = 0
    
    fun file(path: String): CsvSource {
        filePath = path
        return this
    }
    
    fun encoding(enc: String): CsvSource {
        encoding = enc
        return this
    }
    
    fun hasHeader(has: Boolean): CsvSource {
        hasHeader = has
        return this
    }
    
    fun delimiter(delim: String): CsvSource {
        delimiter = delim
        return this
    }
    
    fun skipLines(count: Int): CsvSource {
        skipLines = count
        return this
    }
    
    suspend fun read(): Series<String> {
        val path = filePath ?: throw IllegalStateException("File path not set")
        val file = Paths.get(path)
        
        if (!Files.exists(file)) {
            throw FileNotFoundException("CSV file not found: $path")
        }
        
        return withContext(Dispatchers.IO) {
            val lines = Files.readAllLines(file).drop(skipLines)
            val dataLines = if (hasHeader) lines.drop(1) else lines
            
            Series.of(dataLines.size) { i -> dataLines[i] }
        }
    }
}

/**
 * Kline Parser stage
 */
class KlineParser {
    private var strictMode: Boolean = false
    private var validateData: Boolean = true
    private var customDelimiter: String = ","
    private var timeFormat: String = "epoch_millis"
    private var decimalPrecision: Int = 8
    
    fun strictMode(strict: Boolean): KlineParser {
        strictMode = strict
        return this
    }
    
    fun validateData(validate: Boolean): KlineParser {
        validateData = validate
        return this
    }
    
    fun delimiter(delim: String): KlineParser {
        customDelimiter = delim
        return this
    }
    
    fun timeFormat(format: String): KlineParser {
        timeFormat = format
        return this
    }
    
    fun decimalPrecision(precision: Int): KlineParser {
        decimalPrecision = precision
        return this
    }
    
    suspend fun parse(csvLines: Series<String>): Series<KlineData> {
        return withContext(Dispatchers.Default) {
            val klines = mutableListOf<KlineData>()
            val errors = mutableListOf<String>()
            
            csvLines.play.forEachIndexed { index, line ->
                try {
                    val kline = parseKlineLine(line)
                    if (kline != null) {
                        klines.add(kline)
                    } else if (strictMode) {
                        throw IllegalArgumentException("Failed to parse line $index: $line")
                    }
                } catch (e: Exception) {
                    if (strictMode) {
                        throw e
                    } else {
                        errors.add("Line $index: ${e.message}")
                    }
                }
            }
            
            if (errors.isNotEmpty()) {
                println("Parsing warnings: ${errors.size} errors encountered")
                errors.take(5).forEach { println("  $it") }
            }
            
            Series.of(klines.size) { i -> klines[i] }
        }
    }
    
    private fun parseKlineLine(line: String): KlineData? {
        val parts = line.split(customDelimiter)
        if (parts.size < 11) return null
        
        return try {
            KlineData(
                openTime = parseTime(parts[0]),
                open = BigDecimal(parts[1]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                high = BigDecimal(parts[2]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                low = BigDecimal(parts[3]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                close = BigDecimal(parts[4]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                volume = BigDecimal(parts[5]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                closeTime = parseTime(parts[6]),
                quoteAssetVolume = BigDecimal(parts[7]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                numberOfTrades = parts[8].toInt(),
                takerBuyBaseAssetVolume = BigDecimal(parts[9]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP),
                takerBuyQuoteAssetVolume = BigDecimal(parts[10]).setScale(decimalPrecision, BigDecimal.ROUND_HALF_UP)
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    private fun parseTime(timeStr: String): Long {
        return when (timeFormat) {
            "epoch_millis" -> timeStr.toLong()
            "epoch_seconds" -> timeStr.toLong() * 1000
            "iso8601" -> Instant.parse(timeStr).toEpochMilliseconds()
            else -> timeStr.toLong()
        }
    }
}

/**
 * Kline Transformer stage
 */
class KlineTransformer {
    private var enableValidation: Boolean = true
    private var enableAggregation: Boolean = false
    private var aggregationWindow: Long = 60000 // 1 minute
    private var enableCalculatedFields: Boolean = true
    private var enableFiltering: Boolean = false
    private var minVolume: BigDecimal? = null
    private var maxPrice: BigDecimal? = null
    private var minPrice: BigDecimal? = null
    
    fun enableValidation(enable: Boolean): KlineTransformer {
        enableValidation = enable
        return this
    }
    
    fun enableAggregation(enable: Boolean, windowMs: Long = 60000): KlineTransformer {
        enableAggregation = enable
        aggregationWindow = windowMs
        return this
    }
    
    fun enableCalculatedFields(enable: Boolean): KlineTransformer {
        enableCalculatedFields = enable
        return this
    }
    
    fun filterByVolume(min: BigDecimal): KlineTransformer {
        enableFiltering = true
        minVolume = min
        return this
    }
    
    fun filterByPrice(min: BigDecimal, max: BigDecimal): KlineTransformer {
        enableFiltering = true
        minPrice = min
        maxPrice = max
        return this
    }
    
    suspend fun transform(klines: Series<KlineData>): Series<KlineData> {
        return withContext(Dispatchers.Default) {
            var transformed = klines
            
            // Apply filtering
            if (enableFiltering) {
                transformed = applyFilters(transformed)
            }
            
            // Apply validation
            if (enableValidation) {
                transformed = validateData(transformed)
            }
            
            // Apply aggregation
            if (enableAggregation) {
                transformed = aggregateData(transformed)
            }
            
            // Add calculated fields
            if (enableCalculatedFields) {
                transformed = addCalculatedFields(transformed)
            }
            
            transformed
        }
    }
    
    private fun applyFilters(klines: Series<KlineData>): Series<KlineData> {
        val filtered = klines.play.filter { kline ->
            var passes = true
            
            minVolume?.let { if (kline.volume < it) passes = false }
            minPrice?.let { if (kline.low < it) passes = false }
            maxPrice?.let { if (kline.high > it) passes = false }
            
            passes
        }
        
        return Series.of(filtered.size) { i -> filtered[i] }
    }
    
    private fun validateData(klines: Series<KlineData>): Series<KlineData> {
        val valid = klines.play.filter { kline ->
            kline.open > BigDecimal.ZERO &&
            kline.high >= kline.low &&
            kline.high >= kline.open &&
            kline.high >= kline.close &&
            kline.low <= kline.open &&
            kline.low <= kline.close &&
            kline.volume >= BigDecimal.ZERO &&
            kline.openTime < kline.closeTime
        }
        
        return Series.of(valid.size) { i -> valid[i] }
    }
    
    private fun aggregateData(klines: Series<KlineData>): Series<KlineData> {
        // Group by time window and aggregate
        val grouped = klines.play.groupBy { kline ->
            kline.openTime / aggregationWindow
        }
        
        val aggregated = grouped.map { (_, groupKlines) ->
            if (groupKlines.size == 1) {
                groupKlines.first()
            } else {
                aggregateKlines(groupKlines)
            }
        }
        
        return Series.of(aggregated.size) { i -> aggregated[i] }
    }
    
    private fun aggregateKlines(klines: List<KlineData>): KlineData {
        val first = klines.first()
        val last = klines.last()
        
        return KlineData(
            openTime = first.openTime,
            open = first.open,
            high = klines.maxOf { it.high },
            low = klines.minOf { it.low },
            close = last.close,
            volume = klines.sumOf { it.volume },
            closeTime = last.closeTime,
            quoteAssetVolume = klines.sumOf { it.quoteAssetVolume },
            numberOfTrades = klines.sumOf { it.numberOfTrades },
            takerBuyBaseAssetVolume = klines.sumOf { it.takerBuyBaseAssetVolume },
            takerBuyQuoteAssetVolume = klines.sumOf { it.takerBuyQuoteAssetVolume }
        )
    }
    
    private fun addCalculatedFields(klines: Series<KlineData>): Series<KlineData> {
        // Add calculated fields like price change, volatility, etc.
        return klines // For now, return as-is - could add calculated fields here
    }
}

/**
 * ISAM Storage stage
 */
class IsamStorage {
    private var outputPath: String? = null
    private var enableCompression: Boolean = false
    private var enableIndexing: Boolean = true
    private var batchSize: Int = 1000
    private var asyncIO: Boolean = true
    private var memoryMapping: Boolean = true
    
    fun outputPath(path: String): IsamStorage {
        outputPath = path
        return this
    }
    
    fun enableCompression(enable: Boolean): IsamStorage {
        enableCompression = enable
        return this
    }
    
    fun enableIndexing(enable: Boolean): IsamStorage {
        enableIndexing = enable
        return this
    }
    
    fun batchSize(size: Int): IsamStorage {
        batchSize = size
        return this
    }
    
    fun asyncIO(enable: Boolean): IsamStorage {
        asyncIO = enable
        return this
    }
    
    fun memoryMapping(enable: Boolean): IsamStorage {
        memoryMapping = enable
        return this
    }
    
    suspend fun store(klines: Series<KlineData>): Cursor {
        val path = outputPath ?: throw IllegalStateException("Output path not set")
        
        return withContext(Dispatchers.IO) {
            // Convert klines to cursor format
            val cursor = convertKlinesToCursor(klines)
            
            // Configure ISAM options based on IO opt-ins
            val isamOptions = buildIsamOptions()
            
            // Write to ISAM
            val isamFile = IsamDataFile.write(cursor, path, isamOptions)
            
            // Return cursor for the stored data
            isamFile
        }
    }
    
    private fun convertKlinesToCursor(klines: Series<KlineData>): Cursor {
        // Convert Series<KlineData> to Cursor format
        val rowVecs = klines.play.map { kline ->
            val joins = listOf(
                kline.openTime j { ColumnMeta("open_time", IOMemento.IoLong) },
                kline.open j { ColumnMeta("open", IOMemento.IoDouble) },
                kline.high j { ColumnMeta("high", IOMemento.IoDouble) },
                kline.low j { ColumnMeta("low", IOMemento.IoDouble) },
                kline.close j { ColumnMeta("close", IOMemento.IoDouble) },
                kline.volume j { ColumnMeta("volume", IOMemento.IoDouble) },
                kline.closeTime j { ColumnMeta("close_time", IOMemento.IoLong) },
                kline.quoteAssetVolume j { ColumnMeta("quote_asset_volume", IOMemento.IoDouble) },
                kline.numberOfTrades j { ColumnMeta("number_of_trades", IOMemento.IoInt) },
                kline.takerBuyBaseAssetVolume j { ColumnMeta("taker_buy_base_asset_volume", IOMemento.IoDouble) },
                kline.takerBuyQuoteAssetVolume j { ColumnMeta("taker_buy_quote_asset_volume", IOMemento.IoDouble) }
            )
            
            MemSeries.ofJoins(joins) as RowVec
        }
        
        val schema = SeriesSchema(klinesRecordMeta)
        return MemSeries.ofRowVecs(schema, rowVecs)
    }
    
    private fun buildIsamOptions(): Map<String, Int> {
        val options = mutableMapOf<String, Int>()
        
        if (enableCompression) {
            options["compression"] = 1
        }
        
        if (enableIndexing) {
            options["indexing"] = 1
        }
        
        return options
    }
}

// ============================================================================
// DATA TYPES
// ============================================================================

/**
 * Kline data structure
 */
data class KlineData(
    val openTime: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val closeTime: Long,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: BigDecimal,
    val takerBuyQuoteAssetVolume: BigDecimal
)

/**
 * Kline analysis results
 */
class KlineAnalysis(private val cursor: Cursor) {
    
    fun getSummary(): KlineSummary {
        val rows = cursor.a
        if (rows == 0) return KlineSummary()
        
        val prices = mutableListOf<BigDecimal>()
        val volumes = mutableListOf<BigDecimal>()
        
        for (i in 0 until rows) {
            val row = cursor.b(i)
            prices.add(row[4].a as BigDecimal) // close price
            volumes.add(row[5].a as BigDecimal) // volume
        }
        
        return KlineSummary(
            totalRecords = rows,
            dateRange = getDateRange(),
            priceStats = calculatePriceStats(prices),
            volumeStats = calculateVolumeStats(volumes)
        )
    }
    
    private fun getDateRange(): Pair<Long, Long> {
        val firstRow = cursor.b(0)
        val lastRow = cursor.b(cursor.a - 1)
        
        val startTime = firstRow[0].a as Long
        val endTime = lastRow[6].a as Long // close_time
        
        return startTime to endTime
    }
    
    private fun calculatePriceStats(prices: List<BigDecimal>): PriceStats {
        val min = prices.minOrNull() ?: BigDecimal.ZERO
        val max = prices.maxOrNull() ?: BigDecimal.ZERO
        val avg = prices.reduce { acc, price -> acc + price } / BigDecimal(prices.size)
        
        return PriceStats(min, max, avg)
    }
    
    private fun calculateVolumeStats(volumes: List<BigDecimal>): VolumeStats {
        val total = volumes.reduce { acc, vol -> acc + vol }
        val avg = total / BigDecimal(volumes.size)
        val max = volumes.maxOrNull() ?: BigDecimal.ZERO
        
        return VolumeStats(total, avg, max)
    }
}

data class KlineSummary(
    val totalRecords: Int = 0,
    val dateRange: Pair<Long, Long> = 0L to 0L,
    val priceStats: PriceStats = PriceStats(),
    val volumeStats: VolumeStats = VolumeStats()
)

data class PriceStats(
    val min: BigDecimal = BigDecimal.ZERO,
    val max: BigDecimal = BigDecimal.ZERO,
    val average: BigDecimal = BigDecimal.ZERO
)

data class VolumeStats(
    val total: BigDecimal = BigDecimal.ZERO,
    val average: BigDecimal = BigDecimal.ZERO,
    val max: BigDecimal = BigDecimal.ZERO
)

// ============================================================================
// TELEMETRY INTEGRATION
// ============================================================================

/**
 * Cursor telemetry for tracking pipeline performance
 */
class CursorTelemetry {
    private val startTime = System.currentTimeMillis()
    private val stageTimes = mutableMapOf<String, Long>()
    private val stageCounts = mutableMapOf<String, Int>()
    
    fun trackPipelineStart() {
        println("🚀 Starting Acapulco Kline Ingest Pipeline")
    }
    
    fun trackStage(stageName: String, count: Int) {
        val stageTime = System.currentTimeMillis() - startTime
        stageTimes[stageName] = stageTime
        stageCounts[stageName] = count
        
        println("✅ $stageName: $count records in ${stageTime}ms")
    }
    
    fun trackPipelineSuccess() {
        val totalTime = System.currentTimeMillis() - startTime
        println("🎉 Pipeline completed successfully in ${totalTime}ms")
        
        // Print summary
        println("\n📊 Pipeline Summary:")
        stageCounts.forEach { (stage, count) ->
            val time = stageTimes[stage] ?: 0L
            val rate = if (time > 0) count * 1000 / time else 0
            println("  $stage: $count records at ${rate} records/sec")
        }
    }
    
    fun trackPipelineError(error: Exception) {
        val totalTime = System.currentTimeMillis() - startTime
        println("❌ Pipeline failed after ${totalTime}ms: ${error.message}")
    }
}

// ============================================================================
// DSL BUILDER FUNCTIONS
// ============================================================================

/**
 * Create a new Acapulco kline ingest pipeline
 */
fun acapulcoKlineIngest(block: AcapulcoKlineDSL.() -> Unit): AcapulcoKlineDSL {
    return AcapulcoKlineDSL().apply(block)
}

/**
 * DSL extension for common configurations
 */
fun AcapulcoKlineDSL.fromBinanceCsv(filePath: String): AcapulcoKlineDSL {
    return fromCsv {
        file(filePath)
        hasHeader(true)
        delimiter(",")
    }
}

fun AcapulcoKlineDSL.withStandardParsing(): AcapulcoKlineDSL {
    return parseWith {
        strictMode(false)
        validateData(true)
        timeFormat("epoch_millis")
        decimalPrecision(8)
    }
}

fun AcapulcoKlineDSL.withStandardTransformation(): AcapulcoKlineDSL {
    return transformWith {
        enableValidation(true)
        enableCalculatedFields(true)
        enableAggregation(false)
    }
}

fun AcapulcoKlineDSL.toIsamFile(filePath: String): AcapulcoKlineDSL {
    return storeToIsam {
        outputPath(filePath)
        enableIndexing(true)
        asyncIO(true)
        memoryMapping(true)
    }
}

// ============================================================================
// EXAMPLE USAGE
// ============================================================================

fun main() = runBlocking {
    println("=== Acapulco Kline Ingest DSL Demo ===")
    
    // Example 1: Basic pipeline
    val pipeline1 = acapulcoKlineIngest {
        configure {
            batchSize = 1000
            enableValidation = true
            performanceMode = PerformanceMode.BALANCED
        }
        
        withIOOptIns(
            IOOptIn.ASYNC_URING,
            IOOptIn.MEMORY_MAPPING,
            IOOptIn.CACHE_ALIGNED
        )
        
        fromBinanceCsv("data/klines.csv")
        withStandardParsing()
        withStandardTransformation()
        toIsamFile("output/klines.isam")
    }
    
    // Example 2: Advanced pipeline with filtering and aggregation
    val pipeline2 = acapulcoKlineDSL {
        configure {
            batchSize = 5000
            enableCompression = true
            performanceMode = PerformanceMode.SPEED_OPTIMIZED
        }
        
        withIOOptIns(
            IOOptIn.ASYNC_URING,
            IOOptIn.SCATTER_GATHER,
            IOOptIn.BATCH_PROCESSING,
            IOOptIn.COMPRESSION
        )
        
        fromCsv {
            file("data/large_klines.csv")
            encoding("UTF-8")
            hasHeader(true)
        }
        
        parseWith {
            strictMode(true)
            validateData(true)
            decimalPrecision(12)
        }
        
        transformWith {
            enableValidation(true)
            enableAggregation(true, 300000) // 5-minute aggregation
            filterByVolume(BigDecimal("1000"))
            filterByPrice(BigDecimal("0.001"), BigDecimal("1000000"))
        }
        
        storeToIsam {
            outputPath("output/aggregated_klines.isam")
            enableCompression(true)
            enableIndexing(true)
            batchSize(10000)
            asyncIO(true)
            memoryMapping(true)
        }
    }
    
    // Execute pipeline and analyze results
    try {
        val cursor = pipeline1.execute()
        val analysis = pipeline1.analyze()
        
        println("\n📈 Analysis Results:")
        val summary = analysis.getSummary()
        println("  Total Records: ${summary.totalRecords}")
        println("  Date Range: ${summary.dateRange.first} to ${summary.dateRange.second}")
        println("  Price Range: ${summary.priceStats.min} to ${summary.priceStats.max}")
        println("  Average Price: ${summary.priceStats.average}")
        println("  Total Volume: ${summary.volumeStats.total}")
        
    } catch (e: Exception) {
        println("❌ Pipeline execution failed: ${e.message}")
        e.printStackTrace()
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

/**
 * Create a sample CSV file for testing
 */
fun createSampleCsv(filePath: String, recordCount: Int = 1000) {
    val file = File(filePath)
    file.parentFile?.mkdirs()
    
    file.bufferedWriter().use { writer ->
        // Write header
        writer.write("Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume\n")
        
        // Write sample data
        val startTime = System.currentTimeMillis()
        for (i in 0 until recordCount) {
            val openTime = startTime + (i * 60000) // 1-minute intervals
            val closeTime = openTime + 60000
            val basePrice = 100.0 + (i * 0.1)
            val open = basePrice
            val high = basePrice + (Math.random() * 2)
            val low = basePrice - (Math.random() * 2)
            val close = basePrice + (Math.random() * 4 - 2)
            val volume = 1000.0 + (Math.random() * 5000)
            
            writer.write("$openTime,$open,$high,$low,$close,$volume,$closeTime,${volume * close},${(Math.random() * 100).toInt()},${volume * 0.6},${volume * close * 0.6}\n")
        }
    }
    
    println("✅ Created sample CSV file: $filePath with $recordCount records")
}

/**
 * DSL alias for cleaner syntax
 */
fun acapulcoKlineDSL(block: AcapulcoKlineDSL.() -> Unit): AcapulcoKlineDSL {
    return acapulcoKlineIngest(block)
} 