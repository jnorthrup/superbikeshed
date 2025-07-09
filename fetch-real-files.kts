#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Inflater

// Load the index we already created
val indexFile = File("patrick-devine-index.json")
if (!indexFile.exists()) {
    println("❌ No index found. Run CuratePatrickDevine first.")
    System.exit(1)
}

@Serializable
data class ZipEntry(
    val filename: String,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val method: Int,
    val localHeaderOffset: Long
)

@Serializable
data class CuratedEntry(
    val archiveUrl: String,
    val archiveName: String,
    val entry: ZipEntry,
    val globalIndex: Int,
    val archiveIndex: Int
)

@Serializable
data class CuratedIndex(
    val entries: List<CuratedEntry>,
    val totalFiles: Int,
    val totalCompressedSize: Long,
    val totalUncompressedSize: Long
)

println("📂 Loading Patrick Devine index...")
val index = Json.decodeFromString<CuratedIndex>(indexFile.readText())
println("Found ${index.totalFiles} files in archives")

// Find interesting files to extract
val interestingFiles = index.entries.filter { entry ->
    entry.entry.filename.contains("0720") ||
    entry.entry.filename.contains("transcript", ignoreCase = true) ||
    entry.entry.filename.endsWith(".txt") ||
    (entry.entry.filename.endsWith(".wav") && entry.entry.uncompressedSize < 10_000_000) // Small audio files
}.take(10) // Extract first 10 matching files

println("\n🎯 Extracting ${interestingFiles.size} files:")
interestingFiles.forEach { entry ->
    println("  - ${entry.entry.filename} (${entry.entry.uncompressedSize} bytes)")
}

// Create output directory
val outputDir = File("./extracted-files")
outputDir.mkdirs()

// Extract each file using range requests
runBlocking {
    interestingFiles.forEach { curatedEntry ->
        launch {
            try {
                extractFile(curatedEntry, outputDir)
            } catch (e: Exception) {
                println("❌ Failed to extract ${curatedEntry.entry.filename}: ${e.message}")
            }
        }
    }
}

println("\n✅ Extraction complete! Files saved to: ${outputDir.absolutePath}")

// Function to extract a single file
suspend fun extractFile(curatedEntry: CuratedEntry, outputDir: File) {
    val entry = curatedEntry.entry
    println("\n📥 Extracting: ${entry.filename}")
    
    // Calculate byte range needed
    // ZIP local file header is 30 bytes + filename length + extra field length
    val headerSize = 1024 // Get extra to parse header
    val rangeStart = entry.localHeaderOffset
    val rangeEnd = rangeStart + headerSize + entry.compressedSize
    
    // Make range request
    val url = URL(curatedEntry.archiveUrl)
    val connection = url.openConnection() as HttpURLConnection
    connection.setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
    connection.setRequestProperty("User-Agent", "PatrickDevineExtractor/1.0")
    
    if (connection.responseCode != 206) {
        throw Exception("Range request failed: ${connection.responseCode}")
    }
    
    val data = connection.inputStream.readBytes()
    connection.disconnect()
    
    println("  Downloaded ${data.size} bytes")
    
    // Parse local file header
    val nameLen = (data[26].toInt() and 0xFF) or ((data[27].toInt() and 0xFF) shl 8)
    val extraLen = (data[28].toInt() and 0xFF) or ((data[29].toInt() and 0xFF) shl 8)
    val dataOffset = 30 + nameLen + extraLen
    
    // Extract compressed data
    val compressedData = data.sliceArray(dataOffset until (dataOffset + entry.compressedSize.toInt()))
    
    // Decompress based on method
    val uncompressedData = when (entry.method) {
        0 -> compressedData // Stored (no compression)
        8 -> inflateData(compressedData) // Deflated
        else -> throw Exception("Unsupported compression method: ${entry.method}")
    }
    
    // Save to file
    val outputFile = File(outputDir, entry.filename.substringAfterLast('/'))
    outputFile.parentFile?.mkdirs()
    outputFile.writeBytes(uncompressedData)
    
    println("  ✅ Saved: ${outputFile.name} (${uncompressedData.size} bytes)")
}

// Decompress deflated data
fun inflateData(compressedData: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(compressedData)
    
    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        outputStream.write(buffer, 0, count)
    }
    
    inflater.end()
    return outputStream.toByteArray()
}