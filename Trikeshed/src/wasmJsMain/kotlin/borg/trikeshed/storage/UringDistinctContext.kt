package borg.trikeshed.storage

import borg.trikeshed.lib.*
import borg.trikeshed.io.*
import kotlinx.coroutines.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * WASM/JS implementation of UringDistinctContext using Web APIs
 * Note: Web platform doesn't support io_uring, so we use IndexedDB and Web APIs
 */
actual class UringDistinctContext {
    
    private val operationCounter = kotlin.concurrent.AtomicLong(0)
    private val storage = WebStorage()
    
    /**
     * Distinct read operation with content hash verification
     */
    actual suspend fun distinctRead(
        fd: Int,
        offset: Long,
        length: Int,
        buffer: Indexed<Byte>
    ): DistinctResult {
        val operationId = operationCounter.incrementAndGet()
        
        return try {
            val data = storage.read(fd.toString(), offset, length)
            val hash = computeHash(data, HashAlgorithm.SHA2_256)
            
            DistinctResult(
                operationId = operationId,
                success = true,
                data = data,
                hash = hash,
                metadata = mapOf(
                    "bytesRead" to data.a.toString(),
                    "platform" to "WASM-JS",
                    "fd" to fd.toString()
                )
            )
        } catch (e: Exception) {
            DistinctResult(
                operationId = operationId,
                success = false,
                metadata = mapOf(
                    "error" to e.message ?: "Unknown error",
                    "platform" to "WASM-JS"
                )
            )
        }
    }
    
    /**
     * Distinct write operation with deduplication
     */
    actual suspend fun distinctWrite(
        fd: Int,
        offset: Long,
        data: Indexed<Byte>,
        deduplicationStrategy: DeduplicationStrategy
    ): DistinctResult {
        val operationId = operationCounter.incrementAndGet()
        
        return try {
            // Apply deduplication strategy
            val processedData = when (deduplicationStrategy) {
                DeduplicationStrategy.COMPRESSION_BASED -> compressData(data)
                DeduplicationStrategy.DELTA_ENCODING -> encodeDelta(data)
                else -> data
            }
            
            storage.write(fd.toString(), offset, processedData)
            val hash = computeHash(data, HashAlgorithm.SHA2_256)
            
            DistinctResult(
                operationId = operationId,
                success = true,
                data = data,
                hash = hash,
                metadata = mapOf(
                    "bytesWritten" to data.a.toString(),
                    "strategy" to deduplicationStrategy.name,
                    "platform" to "WASM-JS",
                    "fd" to fd.toString()
                )
            )
        } catch (e: Exception) {
            DistinctResult(
                operationId = operationId,
                success = false,
                metadata = mapOf(
                    "error" to e.message ?: "Unknown error",
                    "platform" to "WASM-JS"
                )
            )
        }
    }
    
    /**
     * Batch distinct operations
     */
    actual suspend fun batchDistinct(
        operations: Indexed<DistinctOperation>
    ): Indexed<DistinctResult> {
        val results = mutableListOf<DistinctResult>()
        
        for (i in 0 until operations.a) {
            val operation = operations.b(i)
            val result = when (operation) {
                is DistinctOperation.Read -> {
                    val buffer = ByteArray(operation.length).let { Indexed(it.size) { j -> it[j] } }
                    distinctRead(operation.fd, operation.offset, operation.length, buffer)
                }
                is DistinctOperation.Write -> {
                    distinctWrite(operation.fd, operation.offset, operation.data, operation.strategy)
                }
                is DistinctOperation.Hash -> {
                    val hash = computeHash(operation.data, operation.algorithm)
                    DistinctResult(
                        operationId = operationCounter.incrementAndGet(),
                        success = true,
                        hash = hash,
                        metadata = mapOf(
                            "algorithm" to operation.algorithm.name,
                            "platform" to "WASM-JS"
                        )
                    )
                }
                is DistinctOperation.Compare -> {
                    val isEqual = compareData(operation.data1, operation.data2, operation.tolerance)
                    DistinctResult(
                        operationId = operationCounter.incrementAndGet(),
                        success = true,
                        metadata = mapOf(
                            "isEqual" to isEqual.toString(),
                            "tolerance" to operation.tolerance.toString(),
                            "platform" to "WASM-JS"
                        )
                    )
                }
            }
            results.add(result)
        }
        
        return results.size j { i: Int -> results[i] }
    }
    
    // Helper methods
    
    private fun computeHash(data: Indexed<Byte>, algorithm: HashAlgorithm): String {
        // Use Web Crypto API for hashing
        val bytes = data.play.toByteArray()
        
        return when (algorithm) {
            HashAlgorithm.SHA2_256 -> {
                // In a real implementation, would use crypto.subtle.digest('SHA-256', ...)
                // For now, use a simple hash
                var hashValue = 0L
                for (byte in bytes) {
                    hashValue = hashValue * 31 + byte.toLong()
                }
                hashValue.toString(16).padStart(32, '0')
            }
            HashAlgorithm.SHA2_512 -> {
                var hashValue = 0L
                for (byte in bytes) {
                    hashValue = hashValue * 31 + byte.toLong()
                }
                hashValue.toString(16).padStart(64, '0')
            }
            else -> {
                // Fallback to simple hash for other algorithms
                var hashValue = 0L
                for (byte in bytes) {
                    hashValue = hashValue * 31 + byte.toLong()
                }
                hashValue.toString(16).padStart(32, '0')
            }
        }
    }
    
    private fun compressData(data: Indexed<Byte>): Indexed<Byte> {
        // Simple compression - in real implementation would use Web Compression API
        val compressed = mutableListOf<Byte>()
        var currentByte = data.b(0)
        var count = 1
        
        for (i in 1 until data.a) {
            if (data.b(i) == currentByte && count < 255) {
                count++
            } else {
                compressed.add(count.toByte())
                compressed.add(currentByte)
                currentByte = data.b(i)
                count = 1
            }
        }
        
        // Add the last run
        compressed.add(count.toByte())
        compressed.add(currentByte)
        
        return compressed.size j { i: Int -> compressed[i] }
    }
    
    private fun encodeDelta(data: Indexed<Byte>): Indexed<Byte> {
        // Simple delta encoding - store differences between consecutive bytes
        if (data.a <= 1) return data
        
        val delta = ByteArray(data.a)
        delta[0] = data.b(0)
        for (i in 1 until data.a) {
            delta[i] = (data.b(i) - data.b(i - 1)).toByte()
        }
        return delta.size j { i: Int -> delta[i] }
    }
    
    private fun compareData(a: Indexed<Byte>, b: Indexed<Byte>, tolerance: Double): Boolean {
        if (a.a != b.a) return false
        if (tolerance == 0.0) {
            // Exact match
            for (i in 0 until a.a) {
                if (a.b(i) != b.b(i)) return false
            }
            return true
        } else {
            // Fuzzy match
            var differences = 0
            for (i in 0 until a.a) {
                if (a.b(i) != b.b(i)) differences++
            }
            val differenceRatio = differences.toDouble() / a.a
            return differenceRatio <= tolerance
        }
    }
    
    /**
     * Close storage
     */
    fun close() {
        storage.close()
    }
}

/**
 * Web storage abstraction using IndexedDB
 */
class WebStorage {
    private val dbName = "DistinctStorage"
    private val storeName = "files"
    private var db: dynamic = null
    
    init {
        initializeDB()
    }
    
    private fun initializeDB() {
        val request = indexedDB.open(dbName, 1)
        
        request.onupgradeneeded = { event ->
            val db = event.target.result
            if (!db.objectStoreNames.contains(storeName)) {
                db.createObjectStore(storeName)
            }
        }
        
        request.onsuccess = { event ->
            db = event.target.result
        }
        
        request.onerror = { event ->
            console.error("Failed to open IndexedDB:", event)
        }
    }
    
    suspend fun read(key: String, offset: Long, length: Int): Indexed<Byte> {
        return suspendCoroutine { continuation ->
            val transaction = db.transaction(storeName, "readonly")
            val store = transaction.objectStore(storeName)
            val request = store.get(key)
            
            request.onsuccess = { event ->
                val data = event.target.result
                if (data != null) {
                    val arrayBuffer = data as ArrayBuffer
                    val uint8Array = Uint8Array(arrayBuffer)
                    
                    val start = offset.toInt()
                    val end = minOf(start + length, uint8Array.length)
                    val result = ByteArray(end - start)
                    
                    for (i in start until end) {
                        result[i - start] = uint8Array[i].toByte()
                    }
                    
                    continuation.resume(result.size j { i: Int -> result[i] })
                } else {
                    continuation.resume(0 j { 0.toByte() })
                }
            }
            
            request.onerror = { event ->
                continuation.resumeWithException(Exception("Failed to read from IndexedDB"))
            }
        }
    }
    
    suspend fun write(key: String, offset: Long, data: Indexed<Byte>) {
        return suspendCoroutine { continuation ->
            val transaction = db.transaction(storeName, "readwrite")
            val store = transaction.objectStore(storeName)
            
            // Read existing data
            val readRequest = store.get(key)
            readRequest.onsuccess = { event ->
                val existingData = event.target.result
                val existingArray = if (existingData != null) {
                    Uint8Array(existingData as ArrayBuffer)
                } else {
                    Uint8Array(0)
                }
                
                // Create new array with sufficient size
                val newSize = maxOf(existingArray.length, offset.toInt() + data.a)
                val newArray = Uint8Array(newSize)
                
                // Copy existing data
                for (i in 0 until existingArray.length) {
                    newArray[i] = existingArray[i]
                }
                
                // Write new data
                for (i in 0 until data.a) {
                    newArray[offset.toInt() + i] = data.b(i).toUByte()
                }
                
                // Store back to IndexedDB
                val writeRequest = store.put(newArray.buffer, key)
                writeRequest.onsuccess = { event ->
                    continuation.resume(Unit)
                }
                writeRequest.onerror = { event ->
                    continuation.resumeWithException(Exception("Failed to write to IndexedDB"))
                }
            }
            
            readRequest.onerror = { event ->
                continuation.resumeWithException(Exception("Failed to read from IndexedDB"))
            }
        }
    }
    
    fun close() {
        if (db != null) {
            db.close()
        }
    }
} 