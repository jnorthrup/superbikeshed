package fiduciary.data

import kotlin.test.*
import borg.trikeshed.lib.*

class ZipIndexTest {
    @Test
    fun testZipEntryFields() {
        val entry = ZIPEntry(
            name = "test.txt",
            offset = 1234L,
            compressedSize = 567L,
            uncompressedSize = 890L,
            mimeType = "text/plain",
            metadata = mapOf("source" to "unit-test")
        )
        assertEquals("test.txt", entry.name)
        assertEquals(1234L, entry.offset)
        assertEquals(567L, entry.compressedSize)
        assertEquals(890L, entry.uncompressedSize)
        assertEquals("text/plain", entry.mimeType)
        assertEquals("unit-test", entry.metadata["source"])
    }

    @Test
    fun testZipCentralDirectory() {
        val entries = 3 j {
            when (it) {
                0 -> ZIPEntry("a.txt", 0L, 100L, 200L)
                1 -> ZIPEntry("b.txt", 100L, 150L, 300L)
                else -> ZIPEntry("c.txt", 250L, 200L, 400L)
            }
        }
        val dir = ZIPCentralDirectory(
            archiveUrl = "https://archive.org/test.zip",
            entries = entries,
            totalSize = 450L,
            metadata = mapOf("test" to "true")
        )
        assertEquals("https://archive.org/test.zip", dir.archiveUrl)
        assertEquals(3, dir.entries.component1())
        assertEquals("a.txt", dir.entries.component2()(0).name)
        assertEquals(100L, dir.entries.component2()(1).offset)
        assertEquals(200L, dir.entries.component2()(2).uncompressedSize)
        assertEquals(450L, dir.totalSize)
        assertEquals("true", dir.metadata["test"])
    }

    @Test
    fun testZipFragmentRangeTypealias() {
        val range: ZipFragmentRange = 10L j 20L
        assertEquals(10L, range.component1())
        assertEquals(20L, range.component2())
    }
} 