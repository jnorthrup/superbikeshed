package borg.trikeshed.isam.meta;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PlatformCodec {

    // Default to Big Endian as it's common for network protocols and Java DataStreams
    private static final ByteOrder DEFAULT_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    public static long readLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return buffer.getLong();
    }

    public static int readInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return buffer.getInt();
    }

    public static short readShort(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return buffer.getShort();
    }

    public static byte[] writeLong(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putLong(value);
        return buffer.array();
    }

    public static byte[] writeInt(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putInt(value);
        return buffer.array();
    }

    public static byte[] writeShort(short value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putShort(value);
        return buffer.array();
    }

    public static double readDouble(byte[] bytes) {
        return Double.longBitsToDouble(readLong(bytes));
    }

    public static float readFloat(byte[] bytes) {
        return Float.intBitsToFloat(readInt(bytes));
    }

    public static byte[] writeDouble(double value) {
        return writeLong(Double.doubleToLongBits(value));
    }

    public static byte[] writeFloat(float value) {
        return writeInt(Float.floatToIntBits(value));
    }

    // Placeholder for UShort read
    public static int readUShort(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return Short.toUnsignedInt(buffer.getShort());
    }

    // Placeholder for UInt read
    public static long readUInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return Integer.toUnsignedLong(buffer.getInt());
    }

    // Placeholder for ULong read - Requires BigInteger or careful handling
    // For simplicity, this might return incorrect values for very large ULongs
    public static long readULong(byte[] bytes) {
         // This is a simplification and may not correctly handle all ULong values
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(DEFAULT_BYTE_ORDER);
        return buffer.getLong();
    }

    // Placeholder for UShort write
    public static byte[] writeUShort(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putShort((short)value);
        return buffer.array();
    }

    // Placeholder for UInt write
    public static byte[] writeUInt(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putInt((int)value);
        return buffer.array();
    }

    // Placeholder for ULong write - Requires BigInteger or careful handling
    public static byte[] writeULong(long value) {
         // This is a simplification and may not correctly handle all ULong values
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.order(DEFAULT_BYTE_ORDER);
        buffer.putLong(value);
        return buffer.array();
    }
}
