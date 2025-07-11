package fiduciary.main

import fiduciary.fetch.*
import fiduciary.context.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import java.io.File

/**
 * Patrick 0720 ZIP Fetch - Rapid central directory extraction
 * Fetches ZIP #1 and #2 with minimal bandwidth usage
 */
suspend fun patrick0720ZipFetch() {
    println("🎯 Patrick 0720 ZIP Fetch - Initial & Final Range Requests")
    println("📦 Fetching ZIP #1 and #2 central directories")
    
    // Step 1: Create HTTP client
    val ioContext = IOContext.NioContext("zip-fetcher")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val fetcher = ZipRangeFetcher(httpClient)
    
    // Step 2: Fetch both ZIP central directories
    val centralDirs = fetcher.fetchZipCentralDirs()
    
    // Step 3: Save to workspace
    saveCentralDirsToWorkspace(centralDirs)
    
    // Step 4: Display results
    displayResults(centralDirs)
    
    println("✅ ZIP fetch complete - ready for content extraction")
}

/**
 * Save central directories to project workspace
 */
private fun saveCentralDirsToWorkspace(centralDirs: List<ZipCentralDir>) {
    println("💾 Saving central directories to workspace...")
    
    val outputDir = File("fiduciary/zip-central-dirs")
    outputDir.mkdirs()
    
    centralDirs.forEachIndexed { index, centralDir ->
        val archiveNum = index + 1
        
        // Save initial range
        val initialFile = File(outputDir, "zip${archiveNum}_initial_range.bin")
        initialFile.writeBytes(centralDir.initialRangeData)
        
        // Save final range  
        val finalFile = File(outputDir, "zip${archiveNum}_final_range.bin")
        finalFile.writeBytes(centralDir.finalRangeData)
        
        // Save central directory metadata
        val metadataFile = File(outputDir, "zip${archiveNum}_metadata.json")
        val metadata = buildString {
            appendLine("{")
            appendLine("  \"archiveUrl\": \"${centralDir.archiveUrl}\",")
            appendLine("  \"archiveName\": \"${centralDir.archiveName}\",")
            appendLine("  \"totalSize\": ${centralDir.totalSize},")
            appendLine("  \"centralDirOffset\": ${centralDir.centralDirOffset},")
            appendLine("  \"entryCount\": ${centralDir.entries.size},")
            appendLine("  \"totalBytes\": ${centralDir.totalBytes},")
            appendLine("  \"entries\": [")
            centralDir.entries.forEachIndexed { entryIndex, entry ->
                appendLine("    {")
                appendLine("      \"filename\": \"${entry.filename}\",")
                appendLine("      \"compressedSize\": ${entry.compressedSize},")
                appendLine("      \"uncompressedSize\": ${entry.uncompressedSize},")
                appendLine("      \"crc32\": ${entry.crc32},")
                appendLine("      \"method\": ${entry.method},")
                appendLine("      \"localHeaderOffset\": ${entry.localHeaderOffset}")
                append("    }")
                if (entryIndex < centralDir.entries.size - 1) appendLine(",")
                else appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        metadataFile.writeText(metadata)
        
        println("   📁 ZIP #$archiveNum saved:")
        println("      Initial: ${initialFile.name} (${centralDir.initialRangeData.size} bytes)")
        println("      Final: ${finalFile.name} (${centralDir.finalRangeData.size} bytes)")
        println("      Metadata: ${metadataFile.name}")
    }
}

/**
 * Display fetch results
 */
private fun displayResults(centralDirs: List<ZipCentralDir>) {
    println("\n📊 ZIP Fetch Results:")
    println("=" * 50)
    
    var totalBandwidth = 0
    var totalEntries = 0
    
    centralDirs.forEachIndexed { index, centralDir ->
        val archiveNum = index + 1
        totalBandwidth += centralDir.totalBytes
        totalEntries += centralDir.entries.size
        
        println("📦 ZIP #$archiveNum: ${centralDir.archiveName}")
        println("   Size: ${formatBytes(centralDir.totalSize)}")
        println("   Bandwidth used: ${formatBytes(centralDir.totalBytes)}")
        println("   Efficiency: ${String.format("%.3f", centralDir.totalBytes.toDouble() / centralDir.totalSize * 100)}%")
        println("   Entries: ${centralDir.entries.size}")
        println("   Central dir offset: ${centralDir.centralDirOffset}")
        
        // Show sample entries
        if (centralDir.entries.isNotEmpty()) {
            println("   Sample entries:")
            centralDir.entries.take(3).forEach { entry ->
                println("     📄 ${entry.filename} (${formatBytes(entry.uncompressedSize)})")
            }
            if (centralDir.entries.size > 3) {
                println("     ... and ${centralDir.entries.size - 3} more")
            }
        }
        println()
    }
    
    println("🎯 Total Results:")
    println("   Archives processed: ${centralDirs.size}")
    println("   Total entries: $totalEntries")
    println("   Total bandwidth: ${formatBytes(totalBandwidth)}")
    
    val totalArchiveSize = centralDirs.sumOf { it.totalSize }
    val efficiency = totalBandwidth.toDouble() / totalArchiveSize * 100
    println("   Overall efficiency: ${String.format("%.3f", efficiency)}%")
    println("   Bandwidth saved: ${formatBytes(totalArchiveSize - totalBandwidth)}")
}

/**
 * Format bytes for display
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        bytes >= 1024 * 1024 -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
        else -> "$bytes bytes"
    }
}

private fun formatBytes(bytes: Int): String = formatBytes(bytes.toLong())

private operator fun String.times(n: Int): String = this.repeat(n)

/**
 * Main entry point for ZIP fetch
 */
suspend fun main() {
    patrick0720ZipFetch()
}