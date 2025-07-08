@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.isam

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.assertEquals
import borg.trikeshed.cursor.cursorOf
import borg.trikeshed.cursor.toList

class ISAMRepeatabilityTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ISAMCursor should be repeatable`() {
        val testData = listOf(
            listOf(1, "apple", 10.0),
            listOf(2, "banana", 20.0),
            listOf(3, "cherry", 30.0)
        )
        val columnNames = listOf("id", "name", "value")
        val cursor = cursorOf(testData, columnNames)

        val isamPath = tempDir.resolve("test_repeatable.isam").toString()
        val isamMetaPath = tempDir.resolve("test_repeatable.isam.meta").toString()

        try {
            // Write ISAM file
            cursor.writeISAM(isamPath)

            // Open ISAM cursor
            val isamCursor = openISAMCursor(isamPath)

            // First read
            val firstReadData = isamCursor.toList()

            // Second read
            val secondReadData = isamCursor.toList()

            // Assert that both reads are identical
            assertEquals(testData, firstReadData, "First read should match original data")
            assertEquals(testData, secondReadData, "Second read should match original data")
            assertEquals(firstReadData, secondReadData, "First and second reads should be identical")

        } finally {
            // Clean up
            tempDir.resolve("test_repeatable.isam").deleteIfExists()
            tempDir.resolve("test_repeatable.isam.meta").deleteIfExists()
        }
    }
}
