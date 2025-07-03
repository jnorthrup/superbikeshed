package borg.trikeshed.lib

import kotlin.test.*

class DoubleDispatchTest {
    @Test
    fun testJsonScannerDoubleDispatch() {
        // Test specialized inline classes
        val scanner = JsonScanner("""{"key": "value"}""")
        assertNotNull(scanner)
        
        // Test quote state transitions
        val quoteState = QuoteState.IN_STRING
        assertTrue(quoteState.isInString)
        
        // Test depth changes
        val depth = Depth(1)
        assertEquals(1, depth.value)
    }
    
    @Test
    fun testPlatformCodecDoubleDispatch() {
        // Test endianness dispatch
        val littleEndian = Endianness.LITTLE
        val bigEndian = Endianness.BIG
        assertNotEquals(littleEndian, bigEndian)
        
        // Test primitive size dispatch
        val intSize = PrimitiveSize.INT
        val longSize = PrimitiveSize.LONG
        assertNotEquals(intSize, longSize)
    }
    
    @Test
    fun testKademliaNUIDDoubleDispatch() {
        // Test network type dispatch
        val ipv4 = NetworkType.IPV4
        val ipv6 = NetworkType.IPV6
        assertNotEquals(ipv4, ipv6)
        
        // Test bit capacity dispatch
        val capacity32 = BitCapacity.BITS_32
        val capacity64 = BitCapacity.BITS_64
        assertNotEquals(capacity32, capacity64)
    }
    
    @Test
    fun testWireProtoDoubleDispatch() {
        // Test serialization format dispatch
        val json = SerializationFormat.JSON
        val cbor = SerializationFormat.CBOR
        assertNotEquals(json, cbor)
        
        // Test IOMemento dispatch
        val memento = MementoType.DATA
        assertNotNull(memento)
    }
    
    @Test
    fun testHttpServerDoubleDispatch() {
        // Test connection context dispatch
        val context = ConnectionContext.SECURE
        assertTrue(context.isSecure)
        
        // Test HTTP message dispatch
        val method = MessageType.GET
        assertEquals("GET", method.name)
    }
    
    @Test
    fun testPeanoEnumDoubleDispatch() {
        // Test enum access patterns
        val sequential = EnumAccessPatterns.sequential
        val binary = EnumAccessPatterns.binary
        assertNotEquals(sequential, binary)
        
        // Test Peano number operations
        val one = Peano(1)
        val two = Peano(2)
        assertEquals(3, (one + two).value)
    }
    
    @Test
    fun testPeanoNumberOperations() {
        val zero = Peano(0)
        val one = Peano(1)
        val two = Peano(2)
        
        // Test addition
        assertEquals(1, (zero + one).value)
        assertEquals(2, (one + one).value)
        assertEquals(3, (one + two).value)
        
        // Test subtraction
        assertEquals(0, (one - one).value)
        assertEquals(1, (two - one).value)
        
        // Test multiplication
        assertEquals(0, (zero * one).value)
        assertEquals(1, (one * one).value)
        assertEquals(2, (one * two).value)
        
        // Test division
        assertEquals(0, (zero / one).value)
        assertEquals(1, (one / one).value)
        assertEquals(1, (two / two).value)
        
        // Test modulo
        assertEquals(0, (zero % one).value)
        assertEquals(0, (one % one).value)
        assertEquals(0, (two % two).value)
        
        // Test comparison
        assertTrue(zero < one)
        assertTrue(one < two)
        assertTrue(zero <= one)
        assertTrue(one <= two)
        assertTrue(one > zero)
        assertTrue(two > one)
        assertTrue(one >= zero)
        assertTrue(two >= one)
    }
    
    @Test
    fun testEnumAccessPatterns() {
        enum class TestEnum { A, B, C, D, E }
        
        // Test sequential access
        val sequential = EnumAccessPatterns.sequential
        val seqResult = sequential.get(TestEnum::class.java, EnumIndex(0))
        assertEquals(TestEnum.A, seqResult)
        
        // Test binary access
        val binary = EnumAccessPatterns.binary
        val binResult = binary.get(TestEnum::class.java, EnumIndex(1))
        assertEquals(TestEnum.B, binResult)
        
        // Test Fibonacci access
        val fibonacci = EnumAccessPatterns.fibonacci
        val fibResult = fibonacci.get(TestEnum::class.java, EnumIndex(2))
        assertEquals(TestEnum.C, fibResult)
        
        // Test power of two access
        val powerOfTwo = EnumAccessPatterns.powerOfTwo
        val powResult = powerOfTwo.get(TestEnum::class.java, EnumIndex(3))
        assertEquals(TestEnum.D, powResult)
        
        // Test custom access
        val custom = EnumAccessPatterns.custom { index -> index.value % 2 == 0 }
        val customResult = custom.get(TestEnum::class.java, EnumIndex(4))
        assertEquals(TestEnum.E, customResult)
    }
} 