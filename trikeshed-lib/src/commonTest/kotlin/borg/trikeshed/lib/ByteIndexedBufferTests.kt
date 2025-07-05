package borg.trikeshed.lib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ByteIndexedBufferTests {

    @Test
    fun `test constructor with ByteArray`() {
        val bytes = byteArrayOf(1, 2, 3)
        val buffer = ByteIndexedBuffer(bytes)
        assertEquals(0, buffer.pos)
        assertEquals(3, buffer.rem)
        assertTrue(buffer.hasRemaining)
    }

    @Test
    fun `test constructor with String`() {
        val str = "abc"
        val buffer = ByteIndexedBuffer(str)
        assertEquals(0, buffer.pos)
        assertEquals(3, buffer.rem)
        assertTrue(buffer.hasRemaining)
        assertEquals('a'.code, buffer.get.toInt())
    }

    @Test
    fun `test get and hasRemaining`() {
        val buffer = ByteIndexedBuffer("abc")
        assertTrue(buffer.hasRemaining)
        assertEquals('a'.code, buffer.get.toInt())
        assertTrue(buffer.hasRemaining)
        assertEquals('b'.code, buffer.get.toInt())
        assertTrue(buffer.hasRemaining)
        assertEquals('c'.code, buffer.get.toInt())
        assertFalse(buffer.hasRemaining)
        assertEquals(0, buffer.rem)
        assertFailsWith<IndexOutOfBoundsException> { buffer.get }
    }

    @Test
    fun `test pos`() {
        val buffer = ByteIndexedBuffer("abcde")
        buffer.pos(2)
        assertEquals(2, buffer.pos)
        assertEquals(3, buffer.rem)
        assertEquals('c'.code, buffer.get.toInt())
        buffer.pos(0)
        assertEquals('a'.code, buffer.get.toInt())
    }

    @Test
    fun `test peek`() {
        val buffer = ByteIndexedBuffer("abc")
        assertEquals('a'.code, buffer.peek.toInt())
        assertEquals(0, buffer.pos)
        buffer.get
        assertEquals('b'.code, buffer.peek.toInt())
    }

    @Test
    fun `test skipWs`() {
        val buffer = ByteIndexedBuffer("""  	
abc""")
        buffer.skipWs
        assertEquals(4, buffer.pos)
        assertEquals('a'.code, buffer.get.toInt())

        val buffer2 = ByteIndexedBuffer("abc")
        buffer2.skipWs
        assertEquals(0, buffer2.pos)
        assertEquals('a'.code, buffer2.get.toInt())
    }

    
}
