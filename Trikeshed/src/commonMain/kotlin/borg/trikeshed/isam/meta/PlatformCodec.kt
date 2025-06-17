package borg.trikeshed.isam.meta

import borg.trikeshed.lib.*
import kotlin.experimental.or
import kotlin.jvm.JvmStatic

// Ontological type aliases for endianness operations
@JvmInline value class EndiannessPredicate(val value: Boolean)
@JvmInline value class ByteOrderOperation(val value: String)

// Endianness dispatch tables
typealias EndiannessReadDispatch<T> = DoubleDispatchTable<EndiannessPredicate, ByteArray, T>
typealias EndiannessWriteDispatch<T> = DoubleDispatchTable<EndiannessPredicate, T, ByteArray>

interface PlatformCodec {
    val readLong: (ByteArray) -> Long
    val readInt: (ByteArray) -> Int

    val readShort: (ByteArray) -> Short
    val writeLong: (Long) -> ByteArray
    val writeInt: (Int) -> ByteArray
    val writeShort: (Short) -> ByteArray
    val writeDouble: (Double) -> ByteArray get() = { writeLong(it.toBits()) }
    val writeFloat: (Float) -> ByteArray get() = { writeInt(it.toBits()) }
    val readDouble: (ByteArray) -> Double get() = { Double.fromBits(readLong(it)) }
    val readFloat: (ByteArray) -> Float get() = { Float.fromBits(readInt(it)) }
    val readUShort: (ByteArray) -> UShort
    val readUInt: (ByteArray) -> UInt
    val readULong: (ByteArray) -> ULong
    val writeUShort: (UShort) -> ByteArray
    val writeUInt: (UInt) -> ByteArray
    val writeULong: (ULong) -> ByteArray

    companion object {
        @JvmStatic
        val isNetworkEndian: Boolean by lazy {
            val i = 0x01020304
            val b = i.toByte()
            b == 0x01.toByte()

        }

        @JvmStatic
        val isLittleEndian: Boolean get() = !isNetworkEndian

        // Double dispatch tables for endianness-specific operations
        private val shortReadDispatch: EndiannessReadDispatch<Short> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray -> 
                ((it[1].toInt() and 0xFF) shl 8).toShort() or (it[0].toInt() and 0xFF).toShort() }),
            ((EndiannessPredicate(false) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray -> 
                (it[0].toInt() and 0xFF shl 8 or (it[1].toInt() and 0xFF)).toShort() })
        )

        private val intReadDispatch: EndiannessReadDispatch<Int> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray ->
                (((it[3].toUByte()).toUInt() shl 24) or
                 ((it[2].toUByte()).toUInt() shl 16) or
                 ((it[1].toUByte()).toUInt() shl 8) or
                 (it[0].toUByte()).toUInt()).toInt() }),
            ((EndiannessPredicate(false) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray ->
                (((it[0].toUByte()).toUInt() shl 24) or
                 ((it[1].toUByte()).toUInt() shl 16) or
                 ((it[2].toUByte()).toUInt() shl 8) or
                 (it[3].toUByte()).toUInt()).toInt() })
        )

        private val longReadDispatch: EndiannessReadDispatch<Long> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray ->
                (((it[7].toUByte()).toULong() shl 56) or
                 ((it[6].toUByte()).toULong() shl 48) or
                 ((it[5].toUByte()).toULong() shl 40) or
                 ((it[4].toUByte()).toULong() shl 32) or
                 ((((it[3].toUByte()).toUInt() shl 24) or
                   ((it[2].toUByte()).toUInt() shl 16) or
                   ((it[1].toUByte()).toUInt() shl 8) or
                   (it[0].toUByte()).toUInt()).toULong())).toLong() }),
            ((EndiannessPredicate(false) j wildcard<ByteArray>()) j { _: EndiannessPredicate, it: ByteArray ->
                (((it[0].toUByte()).toULong() shl 56) or
                 ((it[1].toUByte()).toULong() shl 48) or
                 ((it[2].toUByte()).toULong() shl 40) or
                 ((it[3].toUByte()).toULong() shl 32) or
                 ((((it[4].toUByte()).toUInt() shl 24) or
                   ((it[5].toUByte()).toUInt() shl 16) or
                   ((it[6].toUByte()).toUInt() shl 8) or
                   (it[7].toUByte()).toUInt()).toULong())).toLong() })
        )

        private val shortWriteDispatch: EndiannessWriteDispatch<Short> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<Short>()) j { _: EndiannessPredicate, it: Short ->
                byteArrayOf((it.toUByte()).toByte(), ((it.toUInt() shr 8).toUByte()).toByte()) }),
            ((EndiannessPredicate(false) j wildcard<Short>()) j { _: EndiannessPredicate, it: Short ->
                byteArrayOf(((it.toUInt() shr 8).toUByte()).toByte(), (it.toUByte()).toByte()) })
        )

        private val intWriteDispatch: EndiannessWriteDispatch<Int> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<Int>()) j { _: EndiannessPredicate, it: Int ->
                byteArrayOf((it.toUByte()).toByte(), ((it shr 8).toUByte()).toByte(),
                           ((it shr 16).toUByte()).toByte(), ((it shr 24).toUByte()).toByte()) }),
            ((EndiannessPredicate(false) j wildcard<Int>()) j { _: EndiannessPredicate, it: Int ->
                byteArrayOf(((it shr 24).toUByte()).toByte(), ((it shr 16).toUByte()).toByte(),
                           ((it shr 8).toUByte()).toByte(), (it.toUByte()).toByte()) })
        )

        private val longWriteDispatch: EndiannessWriteDispatch<Long> = seriesOf(
            ((EndiannessPredicate(true) j wildcard<Long>()) j { _: EndiannessPredicate, it: Long ->
                byteArrayOf((it.toUByte()).toByte(), ((it shr 8).toUByte()).toByte(),
                           ((it shr 16).toUByte()).toByte(), ((it shr 24).toUByte()).toByte(),
                           ((it shr 32).toUByte()).toByte(), ((it shr 40).toUByte()).toByte(),
                           ((it shr 48).toUByte()).toByte(), ((it shr 56).toUByte()).toByte()) }),
            ((EndiannessPredicate(false) j wildcard<Long>()) j { _: EndiannessPredicate, it: Long ->
                byteArrayOf(((it shr 56).toUByte()).toByte(), ((it shr 48).toUByte()).toByte(),
                           ((it shr 40).toUByte()).toByte(), ((it shr 32).toUByte()).toByte(),
                           ((it shr 24).toUByte()).toByte(), ((it shr 16).toUByte()).toByte(),
                           ((it shr 8).toUByte()).toByte(), (it.toUByte()).toByte()) })
        )

        object currentPlatformCodec : PlatformCodec {
            override val readShort: (ByteArray) -> Short = { bytes ->
                shortReadDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), bytes)
            }
            override val readInt: (ByteArray) -> Int = { bytes ->
                intReadDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), bytes)
            }
            override val readLong: (ByteArray) -> Long = { bytes ->
                longReadDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), bytes)
            }
            override val writeShort: (Short) -> ByteArray = { value ->
                shortWriteDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), value)
            }
            override val writeInt: (Int) -> ByteArray = { value ->
                intWriteDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), value)
            }
            override val writeLong: (Long) -> ByteArray = { value ->
                longWriteDispatch.exactDispatch(EndiannessPredicate(isLittleEndian), value)
            }
            //6 kotlin unsigned adapters below for the above 6
            override val readUShort: (ByteArray) -> UShort ={it->readShort(it).toUShort()}
            override val readUInt: (ByteArray) -> UInt ={it->readInt(it).toUInt()}
            override val readULong: (ByteArray) -> ULong ={it->readLong(it).toULong()}
            override val writeUShort: (UShort) -> ByteArray ={it->writeShort(it.toShort())}
            override val writeUInt: (UInt) -> ByteArray ={it->writeInt(it.toInt())}
            override val writeULong: (ULong) -> ByteArray ={it->writeLong(it.toLong())}

        }
    }

}
