package borg.trikeshed.isam.meta;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class PlatformCodecTest {

    private static final ByteOrder DEFAULT_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    @Test
    void testReadWriteLong() {
        long value = 1234567890123456789L;
        byte[] bytes = PlatformCodec.writeLong(value);
        assertEquals(value, PlatformCodec.readLong(bytes));

        // Verify with ByteBuffer directly for sanity check
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putLong(value);
        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void testReadWriteInt() {
        int value = 1234567890;
        byte[] bytes = PlatformCodec.writeInt(value);
        assertEquals(value, PlatformCodec.readInt(bytes));

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putInt(value);
        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void testReadWriteShort() {
        short value = 12345;
        byte[] bytes = PlatformCodec.writeShort(value);
        assertEquals(value, PlatformCodec.readShort(bytes));

        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putShort(value);
        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void testReadWriteDouble() {
        double value = 12345.6789;
        byte[] bytes = PlatformCodec.writeDouble(value);
        assertEquals(value, PlatformCodec.readDouble(bytes), 0.0001);

        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putDouble(value);
        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void testReadWriteFloat() {
        float value = 123.456f;
        byte[] bytes = PlatformCodec.writeFloat(value);
        assertEquals(value, PlatformCodec.readFloat(bytes), 0.0001f);

        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putFloat(value);
        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void testReadWriteUShort() {
        int value = 0xFFFF; // Max UShort
        byte[] bytes = PlatformCodec.writeUShort(value);
        assertEquals(value, PlatformCodec.readUShort(bytes));

        int value2 = 30000;
        byte[] bytes2 = PlatformCodec.writeUShort(value2);
        assertEquals(value2, PlatformCodec.readUShort(bytes2));
    }

    @Test
    void testReadWriteUInt() {
        long value = 0xFFFFFFFFL; // Max UInt
        byte[] bytes = PlatformCodec.writeUInt(value);
        assertEquals(value, PlatformCodec.readUInt(bytes));

        long value2 = 2000000000L;
        byte[] bytes2 = PlatformCodec.writeUInt(value2);
        assertEquals(value2, PlatformCodec.readUInt(bytes2));
    }

    // ULong tests are more complex due to Java's lack of native unsigned 64-bit int
    // These tests will be basic and acknowledge the simplification in PlatformCodec
    @Test
    void testReadWriteULongSimple() {
        long value = 1234567890123456789L; // A positive long
        byte[] bytes = PlatformCodec.writeULong(value);
        assertEquals(value, PlatformCodec.readULong(bytes));
    }
}
