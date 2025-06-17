package gk.kademlia.id

import borg.trikeshed.lib.assert
import borg.trikeshed.num.BigInt
import gk.kademlia.bitops.BitOps
import gk.kademlia.bitops.BitOps.Companion.minOps
import gk.kademlia.id.impl.*
import gk.kademlia.net.NetMask
import gk.kademlia.security.CryptoService
import gk.kademlia.security.PublicKey
import kotlin.random.Random
// TODO: Consider if BigInteger specific import is needed if borg.trikeshed.num.BigInt is not auto-imported in all contexts
// For example: import borg.trikeshed.num.BigInt as TrikeBigInt

//import java.math.BigInteger
//import java.math.BigInteger
//import java.util.concurrent.ThreadLocalRandom
//import kotlin.random as KotlinRandom

/**
 * Network Unique ID
 *
 * network IDs within larger networks within larger networks
 *
 */

interface NUID<Primitive : Comparable<Primitive>> {
    var id: Primitive?
    val netmask: NetMask<Primitive>
    val ops: BitOps<Primitive>

    fun random(distance: Int? = null, centroid: Primitive = id!!) = ops.run {
        Random/*
        ThreadLocalRandom.current().asKotlinRandom()*/.run {
            var accum = centroid
            val uBits = netmask.bits
            (distance?.takeIf { it <= uBits } ?: nextInt(uBits)).let { distance ->
                linkedSetOf<Int>().apply {
                    while (size < distance) add(nextInt(uBits))
                }
            }.sorted().forEach {
                accum = xor(accum, shl(one, it))
            }
            accum
        }
    }

    val capacity: Primitive get() = with(ops) { xor(netmask.mask, minus(shl(one, netmask.bits), one)) }
    fun assign(it: Primitive) {
        if (id != null)
            id.run { throw RuntimeException("GUID assigned twice for $id") }
        id = it
    }

    /**
     * whatever the definition of a Riac Bitclock means, this one means
     *
     * from _a[3,4,5]
     * to   1<<3+1<<4+1<<5
     */
    fun fromBitClock(vararg clock: Int): Primitive = ops.run {
        clock.fold(xor(one, one)) { acc, i ->
            assert(netmask.bits > i)
            plus(acc, shl(one, i))
        }
    }

    companion object {
        /**
         * minimum bitops types for the intended bitcount of NUID.
         * This might be used for non-agent IDs or specific purposes.
         */
        fun minNUID(size: Int): NUID<*> =
            when (size) {
                in Int.MIN_VALUE..7 -> object : ByteNUID(minOps(size).one as Byte) {
                    override val netmask: NetMask<Byte>
                        get() = object : NetMask<Byte> { override val bits: Int get() = size }
                }
                8 -> object : UByteNUID(minOps(size).one as UByte) {
                    override val netmask: NetMask<UByte>
                        get() = object : NetMask<UByte> { override val bits: Int get() = size }
                }
                in 9..15 -> object : ShortNUID(minOps(size).one as Short) {
                    override val netmask: NetMask<Short>
                        get() = object : NetMask<Short> { override val bits: Int get() = size }
                }
                16 -> object : UShortNUID(minOps(size).one as UShort) {
                    override val netmask: NetMask<UShort>
                        get() = object : NetMask<UShort> { override val bits: Int get() = size }
                }
                in 17..31 -> object : IntNUID(minOps(size).one as Int) {
                    override val netmask: NetMask<Int>
                        get() = object : NetMask<Int> { override val bits: Int get() = size }
                }
                32 -> object : UIntNUID(minOps(size).one as UInt) {
                    override val netmask: NetMask<UInt>
                        get() = object : NetMask<UInt> { override val bits: Int get() = size }
                }
                in 33..63 -> object : LongNUID(minOps(size).one as Long) {
                    override val netmask: NetMask<Long>
                        get() = object : NetMask<Long> { override val bits: Int get() = size }
                }
                64 -> object : ULongNUID(minOps(size).one as ULong) {
                    override val netmask: NetMask<ULong>
                        get() = object : NetMask<ULong> { override val bits: Int get() = size }
                }
                else -> object : BigIntegerNUID(minOps(size).one as BigInt) {
                    override val netmask: NetMask<BigInt>
                        get() = object : NetMask<BigInt> { override val bits: Int get() = size }
                }
            }

        fun <P : Comparable<P>> createNUIDFromPublicKey(
            publicKey: PublicKey,
            cryptoService: CryptoService,
            netmask: NetMask<P>, // Pass the desired netmask
            ops: BitOps<P>       // Pass the BitOps for the primitive
        ): NUID<P> {
            val publicKeyBytes = cryptoService.encodePublicKey(publicKey)
            // Using default hash algorithm (SHA-256) from CryptoService
            val hashBytes = cryptoService.hash(publicKeyBytes)

            val nuidInstance = object : NUID<P> {
                override var id: P? = null
                override val netmask: NetMask<P> = netmask
                override val ops: BitOps<P> = ops
                // One could also store the publicKey here if needed:
                // val associatedPublicKey: PublicKey = publicKey
            }

            val idValue: P = when (ops) {
                is gk.kademlia.bitops.impl.BigIntOps -> {
                    // Ensure the BigInteger is positive and within the keyspace defined by netmask.bits
                    // borg.trikeshed.num.BigInt constructor (Int signum, ByteArray magnitude)
                    // signum: -1 for negative, 0 for zero, 1 for positive.
                    var num = borg.trikeshed.num.BigInt(1, hashBytes) // 1 for positive

                    // Ensure the ID is within the keyspace defined by netmask.bits
                    // keyspaceModulus = 2^netmask.bits
                    // For BigIntOps, shl(one, netmask.bits) correctly computes 2^netmask.bits
                    // However, if netmask.bits is very large (e.g. 160 for SHA1, 256 for SHA256)
                    // and the BigInt type itself supports numbers larger than 2^netmask.bits,
                    // then modulo is appropriate. If hashBytes directly produces an ID of the correct bit length,
                    // modulo might not be strictly needed unless the hash output is larger than the NUID keyspace.
                    // For Kademlia, the hash output length usually matches the NUID length (e.g. 160-bit SHA-1 for 160-bit IDs).
                    // If hashBytes.size * 8 > netmask.bits, then truncation or modulo is necessary.
                    // If hashBytes.size * 8 <= netmask.bits, then it can be used directly (after ensuring positivity).

                    if (netmask.bits < hashBytes.size * 8) {
                        // If the hash is longer than the NUID's bit length, we need to truncate or take modulo.
                        // Taking modulo 2^netmask.bits is a common approach.
                        val keyspaceModulus = ops.shl(ops.one, netmask.bits)
                        num = ops.rem(num, keyspaceModulus) // num % (2^netmask.bits)
                    }
                    // If hash is shorter or equal, and BigInt can hold it, it's fine.
                    // The BigInt(1, hashBytes) already implies the number of bits from hashBytes.
                    // We just need to ensure it's not negative and fits the conceptual model.

                    // Final check: if the number of bits in `num` now exceeds netmask.bits
                    // (e.g. if hash was short, and 2^netmask.bits is also small, but num became larger than it)
                    // This scenario is less likely if hash is usually long like SHA-256.
                    // The BigInteger `num` representation itself might use more bits than netmask.bits
                    // if there are leading zero bytes in the hash that were stripped.
                    // However, its numerical value should be < 2^netmask.bits after the modulo.
                    num as P
                }
                // TODO: Add cases for Long, Int, Short, Byte, ULong, UInt, UShort, UByte
                // These would involve taking a specific number of bytes from hashBytes,
                // converting to the primitive (handling endianness if necessary),
                // and ensuring it's within the netmask.bits range (e.g., by modulo if the hash portion is too large,
                // or by masking if just ensuring it fits the bit width).
                // Example for Long (assuming first 8 bytes, big-endian):
                // is gk.kademlia.bitops.impl.LongOps -> {
                //    val longBytes = hashBytes.take(8).toByteArray()
                //    var value = 0L
                //    for (byte in longBytes) {
                //        value = (value shl 8) or (byte.toLong() and 0xFF)
                //    }
                //    // Ensure value is within 0 to 2^netmask.bits - 1
                //    if (netmask.bits < 64) {
                //        val mask = (1L shl netmask.bits) - 1
                //        value = value and mask
                //    }
                //    value as P
                // }
                else -> throw NotImplementedError(
                    "NUID creation from PublicKey hash for the primitive type '${ops::class.simpleName}' is not implemented yet."
                )
            }

            nuidInstance.assign(idValue)
            return nuidInstance
        }
    }
}