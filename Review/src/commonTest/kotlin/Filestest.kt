package borg.trikeshed.io.testing

import borg.trikeshed.io.Files
import kotlin.test.*
import kotlin.io.IOException // Common Kotlin IO Exception
// For NoSuchFileException, ensure it's a common type or handle platform-specifically

class FilesTest {

    private val testFileName = "test_file.txt"
    private val testDirName = "test_dir"

    @BeforeTest
    fun setup() {
        // Clean up any remnants from previous tests
        if (Files.exists(testFileName)) {
            Files.delete(testFileName)
        }
        if (Files.exists(testDirName)) {
            Files.delete(testDirName)
        }
    }

    @AfterTest
    fun tearDown() {
        // Clean up after each test
        if (Files.exists(testFileName)) {
            Files.delete(testFileName)
        }
        if (Files.exists(testDirName)) {
            Files.delete(testDirName)
        }
    }

    @Test
    fun writeAndReadString() {
        val content = "Hello, Files!"
        Files.writeString(testFileName, content)
        val readContent = Files.readString(testFileName)
        assertEquals(content, readContent)
    }

    @Test
    fun writeAndReadBytes() {
        val content = "Hello, Bytes!".encodeToByteArray()
        Files.write(testFileName, content)
        val readContent = Files.readAllBytes(testFileName)
        assertTrue(content.contentEquals(readContent))
    }

    @Test
    fun readAllLines() {
        val content = "Line 1\nLine 2\nLine 3"
        Files.writeString(testFileName, content)
        val lines = Files.readAllLines(testFileName)
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), lines)
    }

    @Test
    fun exists_true() {
        Files.writeString(testFileName, "dummy")
        assertTrue(Files.exists(testFileName))
    }

    @Test
    fun exists_false() {
        assertFalse(Files.exists("non_existent_file.txt"))
    }

    @Test
    fun createDirectory() {
        Files.createDirectory(testDirName)
        assertTrue(Files.exists(testDirName))
        assertTrue(Files.isDirectory(testDirName))
    }

    @Test
    fun deleteFile() {
        Files.writeString(testFileName, "dummy")
        Files.delete(testFileName)
        assertFalse(Files.exists(testFileName))
    }

    @Test
    fun deleteDirectory() {
        Files.createDirectory(testDirName)
        Files.delete(testDirName)
        assertFalse(Files.exists(testDirName))
    }

    @Test
    fun readAllLines_throwsExceptionForNonExistentFile() {
        // If NoSuchFileException is java.nio.file.NoSuchFileException, this test might
        // be better in jvmTest or use a common/expect exception type.
        // For now, assuming a common NoSuchFileException or using a broader Exception type for commonTest.
        assertFailsWith<IOException> { // Or a more specific common exception
            Files.readAllLines("nonexistent.txt")
        }
    }

    @Test
    fun readAllBytes_throwsExceptionForNonExistentFile() {
        assertFailsWith<IOException> { // Or a more specific common exception
            Files.readAllBytes("nonexistent.txt")
        }
    }

    @Test
    fun readString_throwsExceptionForNonExistentFile() {
        assertFailsWith<IOException> { // Or a more specific common exception
            Files.readString("nonexistent.txt")
        }
    }

    @Test
    fun write_throwsExceptionForInvalidPath() {
        assertFailsWith<IOException> { // kotlin.io.IOException is fine
            Files.write("/invalid/path/testfile.txt", "content".encodeToByteArray())
        }
    }
}
