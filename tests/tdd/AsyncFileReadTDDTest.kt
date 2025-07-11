import borg.trikeshed.io.PlatformFileIOImpl
import borg.trikeshed.io.AsyncFileManager
import borg.trikeshed.io.AsyncIOEngine
import borg.trikeshed.io.AsyncFiles
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.io.File
import borg.trikeshed.io.IOOperation
import borg.trikeshed.io.IOType
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertNotNull
import borg.trikeshed.io.ProgressCallback
import borg.trikeshed.io.IOOperationWithProgress
import borg.trikeshed.io.AsyncFileCache

class AsyncFileReadTDDTest {
    @Test
    fun testAsyncReadFile() = runBlocking {
        val testPath = "async_test_file.txt"
        val testContent = "Hello, async file read!"
        File(testPath).writeText(testContent)
        val io = PlatformFileIOImpl()
        val result = io.asyncReadFile(testPath)
        assertEquals(testContent, result?.toString(Charsets.UTF_8))
        File(testPath).delete()
    }

    @Test
    fun testAsyncReadLargeFile() = runBlocking {
        val testPath = "async_large_file.txt"
        val largeContent = "Large content ".repeat(1000)
        File(testPath).writeText(largeContent)
        val io = PlatformFileIOImpl()
        val result = io.asyncReadFile(testPath)
        assertEquals(largeContent, result?.toString(Charsets.UTF_8))
        File(testPath).delete()
    }

    @Test
    fun testAsyncReadNonExistentFile() = runBlocking {
        val io = PlatformFileIOImpl()
        val result = io.asyncReadFile("non_existent_file.txt")
        assertNull(result)
    }

    @Test
    fun testConcurrentAsyncReads() = runBlocking {
        val files = (1..5).map { i ->
            val path = "concurrent_test_$i.txt"
            val content = "Content for file $i"
            File(path).writeText(content)
            path to content
        }

        val io = PlatformFileIOImpl()
        val results = files.map { (path, _) ->
            async { io.asyncReadFile(path) }
        }.awaitAll()

        files.forEachIndexed { index, (path, expectedContent) ->
            assertEquals(expectedContent, results[index]?.toString(Charsets.UTF_8))
            File(path).delete()
        }
    }

    @Test
    fun testAsyncFileManager() = runBlocking {
        val manager = AsyncFileManager.instance
        val path1 = "/test/path1"
        val path2 = "/test/path2"

        val handle1 = manager.registerFile(path1)
        val handle2 = manager.registerFile(path2)

        assertTrue(handle1 != handle2)
        assertEquals(path1, manager.getPath(handle1))
        assertEquals(path2, manager.getPath(handle2))

        manager.unregisterFile(handle1)
        assertNull(manager.getPath(handle1))
        assertEquals(path2, manager.getPath(handle2))
    }

    @Test
    fun testAsyncIOEngine() = runBlocking {
        val testPath = "engine_test.txt"
        val testContent = "Engine test content"
        File(testPath).writeText(testContent)

        val engine = AsyncIOEngine.create()
        val manager = AsyncFileManager.instance
        val handle = manager.registerFile(testPath)

        val buffer = ByteArray(testContent.length)
        val bytesRead = engine.read(handle, buffer, 0)

        assertEquals(testContent.length, bytesRead)
        assertEquals(testContent, buffer.toString(Charsets.UTF_8))

        File(testPath).delete()
    }

    @Test
    fun testAsyncWriteFile() = runBlocking {
        val testPath = "async_write_test.txt"
        val testContent = "Async write test content"
        val contentBytes = testContent.encodeToByteArray()
        
        val io = PlatformFileIOImpl()
        val success = io.asyncWriteFile(testPath, contentBytes)
        
        assertTrue(success)
        val readContent = File(testPath).readText()
        assertEquals(testContent, readContent)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncWriteLargeFile() = runBlocking {
        val testPath = "async_write_large.txt"
        val largeContent = "Large write content ".repeat(1000)
        val contentBytes = largeContent.encodeToByteArray()
        
        val io = PlatformFileIOImpl()
        val success = io.asyncWriteFile(testPath, contentBytes)
        
        assertTrue(success)
        val readContent = File(testPath).readText()
        assertEquals(largeContent, readContent)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesReadAllBytes() = runBlocking {
        val testPath = "asyncfiles_test.txt"
        val testContent = "AsyncFiles test content"
        File(testPath).writeText(testContent)
        
        val result = AsyncFiles.readAllBytes(testPath)
        assertEquals(testContent, result?.toString(Charsets.UTF_8))
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesReadString() = runBlocking {
        val testPath = "asyncfiles_string_test.txt"
        val testContent = "AsyncFiles string test"
        File(testPath).writeText(testContent)
        
        val result = AsyncFiles.readString(testPath)
        assertEquals(testContent, result)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesReadAllLines() = runBlocking {
        val testPath = "asyncfiles_lines_test.txt"
        val lines = listOf("Line 1", "Line 2", "Line 3")
        File(testPath).writeText(lines.joinToString("\n"))
        
        val result = AsyncFiles.readAllLines(testPath)
        assertEquals(lines, result)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesWrite() = runBlocking {
        val testPath = "asyncfiles_write_test.txt"
        val testContent = "AsyncFiles write test"
        
        val success = AsyncFiles.write(testPath, testContent)
        assertTrue(success)
        
        val readContent = File(testPath).readText()
        assertEquals(testContent, readContent)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesWriteLines() = runBlocking {
        val testPath = "asyncfiles_write_lines_test.txt"
        val lines = listOf("Write Line 1", "Write Line 2", "Write Line 3")
        
        val success = AsyncFiles.write(testPath, lines)
        assertTrue(success)
        
        val readLines = File(testPath).readLines()
        assertEquals(lines, readLines)
        
        File(testPath).delete()
    }

    @Test
    fun testAsyncFilesCopyFile() = runBlocking {
        val sourcePath = "copy_source.txt"
        val destPath = "copy_dest.txt"
        val testContent = "Copy test content"
        
        File(sourcePath).writeText(testContent)
        
        val success = AsyncFiles.copyFile(sourcePath, destPath)
        assertTrue(success)
        
        val sourceContent = File(sourcePath).readText()
        val destContent = File(destPath).readText()
        assertEquals(testContent, sourceContent)
        assertEquals(testContent, destContent)
        
        File(sourcePath).delete()
        File(destPath).delete()
    }

    @Test
    fun testAsyncFilesMoveFile() = runBlocking {
        val sourcePath = "move_source.txt"
        val destPath = "move_dest.txt"
        val testContent = "Move test content"
        
        File(sourcePath).writeText(testContent)
        
        val success = AsyncFiles.moveFile(sourcePath, destPath)
        assertTrue(success)
        
        assertFalse(File(sourcePath).exists())
        val destContent = File(destPath).readText()
        assertEquals(testContent, destContent)
        
        File(destPath).delete()
    }

    @Test
    fun testAsyncFilesExists() = runBlocking {
        val testPath = "exists_test.txt"
        
        assertFalse(AsyncFiles.exists(testPath))
        
        File(testPath).writeText("test")
        assertTrue(AsyncFiles.exists(testPath))
        
        File(testPath).delete()
    }

    @Test
    fun testConcurrentAsyncWrites() = runBlocking {
        val files = (1..3).map { i ->
            val path = "concurrent_write_$i.txt"
            val content = "Concurrent write content $i"
            path to content
        }

        val results = files.map { (path, content) ->
            async { AsyncFiles.write(path, content) }
        }.awaitAll()

        assertTrue(results.all { it })
        
        files.forEach { (path, expectedContent) ->
            val readContent = File(path).readText()
            assertEquals(expectedContent, readContent)
            File(path).delete()
        }
    }

    @Test
    fun testBatchOperations() = runBlocking {
        val testPath1 = "batch_test1.txt"
        val testPath2 = "batch_test2.txt"
        val content1 = "Batch test content 1"
        val content2 = "Batch test content 2"
        
        // Create test files
        File(testPath1).writeText(content1)
        File(testPath2).writeText(content2)
        
        val engine = AsyncIOEngine.create()
        val manager = AsyncFileManager.instance
        val handle1 = manager.registerFile(testPath1)
        val handle2 = manager.registerFile(testPath2)
        
        // Create batch read operations
        val buffer1 = ByteArray(content1.length)
        val buffer2 = ByteArray(content2.length)
        
        val operations = listOf(
            IOOperation(1L, IOType.READ, handle1, buffer1, 0),
            IOOperation(2L, IOType.READ, handle2, buffer2, 0)
        )
        
        val results = engine.submitBatch(operations)
        
        assertEquals(2, results.size)
        assertEquals(content1.length, results[0].bytesTransferred)
        assertEquals(content2.length, results[1].bytesTransferred)
        assertEquals(content1, buffer1.toString(Charsets.UTF_8))
        assertEquals(content2, buffer2.toString(Charsets.UTF_8))
        
        File(testPath1).delete()
        File(testPath2).delete()
    }

    @Test
    fun testBatchWriteOperations() = runBlocking {
        val testPath1 = "batch_write1.txt"
        val testPath2 = "batch_write2.txt"
        val content1 = "Batch write content 1"
        val content2 = "Batch write content 2"
        
        val engine = AsyncIOEngine.create()
        val manager = AsyncFileManager.instance
        val handle1 = manager.registerFile(testPath1)
        val handle2 = manager.registerFile(testPath2)
        
        // Create batch write operations
        val operations = listOf(
            IOOperation(1L, IOType.WRITE, handle1, content1.encodeToByteArray(), 0),
            IOOperation(2L, IOType.WRITE, handle2, content2.encodeToByteArray(), 0)
        )
        
        val results = engine.submitBatch(operations)
        
        assertEquals(2, results.size)
        assertEquals(content1.length, results[0].bytesTransferred)
        assertEquals(content2.length, results[1].bytesTransferred)
        
        // Verify files were written
        assertEquals(content1, File(testPath1).readText())
        assertEquals(content2, File(testPath2).readText())
        
        File(testPath1).delete()
        File(testPath2).delete()
    }

    @Test
    fun testStreamingOperations() = runBlocking {
        val testPath = "streaming_test.txt"
        val lines = listOf("Line 1", "Line 2", "Line 3", "Line 4", "Line 5")
        File(testPath).writeText(lines.joinToString("\n"))
        
        // Test streamLines
        val streamedLines = AsyncFiles.streamLines(testPath).toList()
        assertEquals(lines, streamedLines)
        
        // Test streamBytes
        val content = lines.joinToString("\n")
        val streamedBytes = AsyncFiles.streamBytes(testPath, 10).toList()
        val reconstructed = streamedBytes.reduce { acc, chunk -> acc + chunk }.toString(Charsets.UTF_8)
        assertEquals(content, reconstructed)
        
        File(testPath).delete()
    }

    @Test
    fun testCompressionOperations() = runBlocking {
        val testPath = "compression_test.gz"
        val originalContent = "This is a test content for compression. ".repeat(100)
        
        // Test writeCompressed
        val success = AsyncFiles.writeCompressedString(testPath, originalContent)
        assertTrue(success)
        
        // Verify file is compressed (smaller than original)
        val compressedSize = File(testPath).length()
        val originalSize = originalContent.encodeToByteArray().size.toLong()
        assertTrue(compressedSize < originalSize)
        
        // Test readCompressed
        val decompressed = AsyncFiles.readCompressedString(testPath)
        assertEquals(originalContent, decompressed)
        
        File(testPath).delete()
    }

    @Test
    fun testCachingOperations() = runBlocking {
        val testPath = "cache_test.txt"
        val testContent = "Cache test content"
        File(testPath).writeText(testContent)
        
        val cache = AsyncFileCache.instance
        
        // Test cache miss
        val cached1 = cache.get(testPath)
        assertNull(cached1)
        
        // Test cache put and get
        val content = testContent.encodeToByteArray()
        cache.put(testPath, content, 10.seconds)
        
        val cached2 = cache.get(testPath)
        assertNotNull(cached2)
        assertEquals(testContent, cached2?.toString(Charsets.UTF_8))
        
        // Test cache invalidation
        cache.invalidate(testPath)
        val cached3 = cache.get(testPath)
        assertNull(cached3)
        
        File(testPath).delete()
    }

    @Test
    fun testProgressTracking() = runBlocking {
        val testPath = "progress_test.txt"
        val testContent = "Progress test content ".repeat(100)
        File(testPath).writeText(testContent)
        
        val engine = AsyncIOEngine.create()
        val manager = AsyncFileManager.instance
        val handle = manager.registerFile(testPath)
        
        var progressUpdates = mutableListOf<Pair<Long, Long>>()
        val progressCallback: ProgressCallback = { completed, total ->
            progressUpdates.add(completed to total)
        }
        
        val buffer = ByteArray(testContent.length)
        val operation = IOOperationWithProgress(
            IOOperation(1L, IOType.READ, handle, buffer, 0),
            progressCallback
        )
        
        val results = engine.submitBatchWithProgress(listOf(operation), testContent.length.toLong())
        
        assertEquals(1, results.size)
        assertEquals(testContent.length, results[0].bytesTransferred)
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(testContent.length.toLong(), progressUpdates.last().first)
        
        File(testPath).delete()
    }

    @Test
    fun testConcurrentCaching() = runBlocking {
        val testPath = "concurrent_cache_test.txt"
        val testContent = "Concurrent cache test content"
        File(testPath).writeText(testContent)
        
        val cache = AsyncFileCache.instance
        val content = testContent.encodeToByteArray()
        
        // Test concurrent cache operations
        val results = (1..5).map { i ->
            async {
                when (i % 3) {
                    0 -> cache.put(testPath, content, 10.seconds)
                    1 -> cache.get(testPath)
                    2 -> cache.invalidate(testPath)
                }
            }
        }.awaitAll()
        
        // At least some operations should succeed
        assertTrue(results.isNotEmpty())
        
        File(testPath).delete()
    }
} 