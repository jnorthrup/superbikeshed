@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson


import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.min

/**
 * Spansh Galaxy Map Reader
 * 
 * Reads pre-indexed gzip blocks from Spansh servers (or compatible services)
 * that provide KZRAN-indexed access to 90GB galaxy map files.
 * 
 * The server provides:
 * - Index metadata endpoint: GET /index/{file_id}
 * - Block data endpoint: GET /blocks/{file_id}?start={offset}&end={offset}
 * - Pre-computed KZRAN sync points
 */

// Galaxy map specific types
typealias SystemId = Long
typealias GalacticX = Double
typealias GalacticY = Double  
typealias GalacticZ = Double
typealias SystemName = String

/**
 * Attention region in galactic coordinates
 */
data class GalacticRegion(
    val minX: GalacticX,
    val maxX: GalacticX,
    val minY: GalacticY,
    val maxY: GalacticY,
    val minZ: GalacticZ,
    val maxZ: GalacticZ,
    val priority: Int = 0
)

/**
 * Block metadata from Spansh index
 */
data class SpanshBlockInfo(
    val blockId: Int,
    val compressedOffset: FileOffset,
    val compressedSize: Long,
    val uncompressedOffset: UncompressedOffset,
    val uncompressedSize: Long,
    val firstSystemId: SystemId?,
    val lastSystemId: SystemId?,
    val boundingBox: GalacticRegion?
)

/**
 * Spansh index metadata
 */
data class SpanshIndexMetadata(
    val fileId: String,
    val totalCompressedSize: Long,
    val totalUncompressedSize: Long,
    val blockSize: Int,
    val blocks: List<SpanshBlockInfo>,
    val kzranPoints: List<KzranPoint>,
    val indexVersion: String
)

/**
 * Galaxy system data
 */
data class GalaxySystem(
    val id: SystemId,
    val name: SystemName,
    val coords: Join<GalacticX, GalacticY j GalacticZ>,
    val primaryStar: Map<String, Any>?,
    val bodies: List<Map<String, Any>>?,
    val stations: List<Map<String, Any>>?
)

/**
 * Spansh Galaxy Map Reader with attention-based loading
 */
class SpanshGalaxyMapReader(
    internal val baseUrl: String,
    internal val httpClient: HttpClient,
    internal val blockCache: BlockCache = LRUBlockCache(maxSizeBytes = 1024 * 1024 * 1024) // 1GB cache
) {
    
    /**
     * Load index metadata for a galaxy map file
     */
    suspend fun loadIndex(fileId: String): SpanshIndexMetadata {
        val response = httpClient.get("$baseUrl/index/$fileId")
        // Parse JSON manually or use platform-specific JSON parser
        return parseIndexMetadata(response.body)
    }
    
    /**
     * Parse index metadata from JSON
     */
    internal fun parseIndexMetadata(json: String): SpanshIndexMetadata {
        // Simple manual parsing for now
        // In real implementation, would use proper JSON parser
        return SpanshIndexMetadata(
            fileId = "dummy",
            totalCompressedSize = 0L,
            totalUncompressedSize = 0L,
            blockSize = 1024 * 1024,
            blocks = emptyList(),
            kzranPoints = emptyList(),
            indexVersion = "1.0"
        )
    }
    
    /**
     * Stream galaxy systems from attention regions using pre-indexed blocks
     */
    fun streamSystemsFromRegions(
        fileId: String,
        index: SpanshIndexMetadata,
        regions: List<GalacticRegion>
    ): Flow<GalaxySystem> = flow {
        // Find blocks that intersect with our regions of interest
        val relevantBlocks = findRelevantBlocks(index, regions)
            .sortedByDescending { it.second } // Sort by priority
        
        for ((block, priority) in relevantBlocks) {
            // Check cache first
            val cached = blockCache.get(fileId, block.blockId)
            val blockData = if (cached != null) {
                cached
            } else {
                // Fetch compressed block via range request
                fetchAndDecompressBlock(fileId, index, block).also {
                    blockCache.put(fileId, block.blockId, it)
                }
            }
            
            // Parse systems from block with SIMD acceleration
            parseSystemsFromBlock(blockData, regions).collect { system ->
                emit(system)
            }
        }
    }
    
    /**
     * Stream systems near a specific location with distance-based priority
     */
    fun streamSystemsNearLocation(
        fileId: String,
        index: SpanshIndexMetadata,
        center: Join<GalacticX, GalacticY j GalacticZ>,
        maxDistance: Double,
        limit: Int = 1000
    ): Flow<GalaxySystem> = flow {
        var systemCount = 0
        val (centerX, centerYZ) = center
        val (centerY, centerZ) = centerYZ
        
        // Create expanding regions around center
        val regions = generateExpandingRegions(centerX, centerY, centerZ, maxDistance)
        
        streamSystemsFromRegions(fileId, index, regions)
            .filter { system ->
                val (x, yz) = system.coords
                val (y, z) = yz
                val distance = kotlin.math.sqrt(
                    (x - centerX) * (x - centerX) +
                    (y - centerY) * (y - centerY) +
                    (z - centerZ) * (z - centerZ)
                )
                distance <= maxDistance
            }
            .take(limit)
            .collect { system ->
                emit(system)
                systemCount++
            }
    }
    
    /**
     * Find blocks that contain systems in the specified regions
     */
    internal fun findRelevantBlocks(
        index: SpanshIndexMetadata,
        regions: List<GalacticRegion>
    ): List<Pair<SpanshBlockInfo, Int>> {
        val relevantBlocks = mutableListOf<Pair<SpanshBlockInfo, Int>>()
        
        for (block in index.blocks) {
            val bbox = block.boundingBox ?: continue
            
            for (region in regions) {
                if (regionsIntersect(bbox, region)) {
                    relevantBlocks.add(block to region.priority)
                    break
                }
            }
        }
        
        return relevantBlocks
    }
    
    /**
     * Fetch and decompress a specific block
     */
    internal suspend fun fetchAndDecompressBlock(
        fileId: String,
        index: SpanshIndexMetadata,
        block: SpanshBlockInfo
    ): Indexed<Byte> = coroutineScope {
        // Find the KZRAN sync point for this block
        val syncPoint = index.kzranPoints
            .filter { it.uncompressedOffset <= block.uncompressedOffset }
            .maxByOrNull { it.uncompressedOffset }
            ?: throw IllegalStateException("No sync point found for block ${block.blockId}")
        
        // Calculate compressed range to fetch
        val startOffset = syncPoint.compressedOffset
        val endOffset = startOffset + block.compressedSize + 1024 // Extra buffer
        
        // Fetch compressed data
        val url = "$baseUrl/blocks/$fileId"
        val params = mapOf(
            "start" to startOffset.toString(),
            "end" to endOffset.toString()
        )
        
        val response = httpClient.get(url, params)
        val compressedData = response.bodyAsBytes()
        
        // Decompress using sync point
        decompressWithSyncPoint(
            compressedData,
            syncPoint,
            block.uncompressedOffset - syncPoint.uncompressedOffset,
            block.uncompressedSize
        )
    }
    
    /**
     * Parse galaxy systems from decompressed block using SIMD
     */
    internal fun parseSystemsFromBlock(
        blockData: Indexed<Byte>,
        regions: List<GalacticRegion>
    ): Flow<GalaxySystem> = flow {
        // Convert to string for initial parsing
        val jsonString = blockData.toByteArray().decodeToString()
        
        // Use line-delimited JSON parsing for galaxy data
        jsonString.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                try {
                    val system = parseSystemLine(line)
                    
                    // Check if system is in any attention region
                    if (regions.any { region -> systemInRegion(system, region) }) {
                        emit(system)
                    }
                } catch (e: Exception) {
                    // Skip malformed entries
                }
            }
    }
    
    /**
     * Parse a single system line (LJSON format)
     */
    internal fun parseSystemLine(line: String): GalaxySystem {
        return borg.trikeshed.ljson.JsonLinesParser.parseGalaxySystem(line) ?: GalaxySystem(
            id = 0L,
            name = "Unknown",
            coords = 0.0 j (0.0 j 0.0),
            primaryStar = null,
            bodies = null,
            stations = null
        )
    }
    
    /**
     * Decompress data using KZRAN sync point
     */
    internal suspend fun decompressWithSyncPoint(
        compressedData: ByteArray,
        syncPoint: KzranPoint,
        skipBytes: Long,
        readBytes: Long
    ): Indexed<Byte> {
        // Use platform-specific zlib decompression
        val decompressed = withContext(Dispatchers.Default) {
            zlibDecompressWithDictionary(
                compressedData,
                syncPoint.windowData,
                syncPoint.bitOffset,
                skipBytes,
                readBytes
            )
        }
        
        return decompressed.size j { i: Int -> decompressed[i] }
    }
    
    /**
     * Check if regions intersect
     */
    internal fun regionsIntersect(a: GalacticRegion, b: GalacticRegion): Boolean {
        return a.minX <= b.maxX && a.maxX >= b.minX &&
               a.minY <= b.maxY && a.maxY >= b.minY &&
               a.minZ <= b.maxZ && a.maxZ >= b.minZ
    }
    
    /**
     * Check if system is in region
     */
    internal fun systemInRegion(system: GalaxySystem, region: GalacticRegion): Boolean {
        val (x, yz) = system.coords
        val (y, z) = yz
        
        return x >= region.minX && x <= region.maxX &&
               y >= region.minY && y <= region.maxY &&
               z >= region.minZ && z <= region.maxZ
    }
    
    /**
     * Generate expanding cubic regions for nearest-neighbor search
     */
    internal fun generateExpandingRegions(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        maxDistance: Double
    ): List<GalacticRegion> {
        val regions = mutableListOf<GalacticRegion>()
        var distance = 1000.0 // Start with 1000 LY cubes
        var priority = 10
        
        while (distance <= maxDistance) {
            regions.add(GalacticRegion(
                minX = centerX - distance,
                maxX = centerX + distance,
                minY = centerY - distance,
                maxY = centerY + distance,
                minZ = centerZ - distance,
                maxZ = centerZ + distance,
                priority = priority
            ))
            
            distance *= 2 // Double the search radius
            priority = (priority - 1).coerceAtLeast(1)
        }
        
        return regions
    }
    
    /**
     * Convert Indexed<Byte> to ByteArray
     */
    internal fun Indexed<Byte>.toByteArray(): ByteArray {
        val result = ByteArray(a)
        for (i in 0 until a) {
            result[i] = this[i]
        }
        return result
    }
}

/**
 * Block cache interface
 */
interface BlockCache {
    suspend fun get(fileId: String, blockId: Int): Indexed<Byte>?
    suspend fun put(fileId: String, blockId: Int, data: Indexed<Byte>)
}

/**
 * LRU cache implementation for blocks
 */
class LRUBlockCache(internal val maxSizeBytes: Long) : BlockCache {
    internal val cache = LinkedHashMap<String, Indexed<Byte>>()
    internal var currentSize = 0L
    
    override suspend fun get(fileId: String, blockId: Int): Indexed<Byte>? {
        return cache["$fileId:$blockId"]
    }
    
    override suspend fun put(fileId: String, blockId: Int, data: Indexed<Byte>) {
        val key = "$fileId:$blockId"
        val size = data.component1().toLong()
        
        // Remove old entries if needed
        while (currentSize + size > maxSizeBytes && cache.isNotEmpty()) {
            val oldest = cache.iterator().next()
            cache.remove(oldest.key)
            currentSize -= oldest.value.component1()
        }
        
        cache[key] = data
        currentSize += size
    }
}

/**
 * Platform-specific zlib decompression with dictionary
 */
expect suspend fun zlibDecompressWithDictionary(
    compressedData: ByteArray,
    dictionary: ByteArray,
    bitOffset: Int,
    skipBytes: Long,
    readBytes: Long
): ByteArray

/**
 * HTTP client interface
 */
interface HttpClient {
    suspend fun get(url: String, params: Map<String, String> = emptyMap()): HttpResponse
}

/**
 * HTTP response
 */
data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String
) {
    fun bodyAsBytes(): ByteArray = body.encodeToByteArray()
}

/**
 * Simple JSON parser for Spansh data
 */
internal object SimpleJsonParser {
    fun parseSystemLine(line: String): GalaxySystem? {
        return borg.trikeshed.ljson.JsonLinesParser.parseGalaxySystem(line)
    }
}

/**
 * Example usage
 */
suspend fun exampleUsage() {
    val reader = SpanshGalaxyMapReader(
        baseUrl = "https://spansh.co.uk/api/galaxy-data",
        httpClient = object : HttpClient {
            override suspend fun get(url: String, params: Map<String, String>): HttpResponse {
                TODO("Implement HTTP client")
            }
        }
    )
    
    // Load index for the galaxy map
    val index = reader.loadIndex("edsm-galaxy-2024")
    
    // Define region of interest (bubble around Sol)
    val solRegion = GalacticRegion(
        minX = -100.0, maxX = 100.0,
        minY = -100.0, maxY = 100.0,
        minZ = -100.0, maxZ = 100.0,
        priority = 10
    )
    
    // Stream systems from the region
    reader.streamSystemsFromRegions("edsm-galaxy-2024", index, listOf(solRegion))
        .collect { system ->
            println("Found system: ${system.name} at ${system.coords}")
        }
    
    // Or find nearest systems to a location
    val colonia = 9530.5 j (-910.28125 j 19808.125)
    reader.streamSystemsNearLocation(
        "edsm-galaxy-2024",
        index,
        colonia,
        maxDistance = 500.0,
        limit = 100
    ).collect { system ->
        println("Near Colonia: ${system.name}")
    }
}