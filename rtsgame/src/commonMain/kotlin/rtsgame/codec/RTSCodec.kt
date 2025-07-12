package rtsgame.codec

import rtsgame.core.*
import trikeshed.lib.*
import kotlinx.coroutines.withContext

/**
 * RTS codec built on TrikeShed platform
 * Uses TrikeShed's binary serialization
 */

// ============================================================================
// Codec Interface
// ============================================================================

interface Codec<T> {
    fun encode(v: T, b: BinaryBuffer)
    fun decode(b: BinaryBuffer): T
}

// ============================================================================
// Registry
// ============================================================================

object CodecReg {
    private val codecs = mutableMapOf<String, Codec<*>>()
    
    init {
        // Register core codecs
        reg("v2", V2Codec)
        reg("v3", V3Codec)
        reg("pos", PosCodec)
        reg("vel", VelCodec)
        reg("hp", HPCodec)
        reg("dmg", DmgCodec)
        reg("team", TeamCodec)
        reg("cmd", CmdCodec)
        reg("pkt", PktCodec)
    }
    
    fun reg(t: String, c: Codec<*>) {
        codecs[t] = c
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> get(t: String): Codec<T>? = codecs[t] as? Codec<T>
}

// ============================================================================
// Primitive Codecs
// ============================================================================

object V2Codec : Codec<V2> {
    override fun encode(v: V2, b: BinaryBuffer) {
        b.writeFloat(v.x)
        b.writeFloat(v.y)
    }
    
    override fun decode(b: BinaryBuffer): V2 = V2(
        b.readFloat(),
        b.readFloat()
    )
}

object V3Codec : Codec<V3> {
    override fun encode(v: V3, b: BinaryBuffer) {
        b.writeFloat(v.x)
        b.writeFloat(v.y)
        b.writeFloat(v.z)
    }
    
    override fun decode(b: BinaryBuffer): V3 = V3(
        b.readFloat(),
        b.readFloat(),
        b.readFloat()
    )
}

// ============================================================================
// Component Codecs
// ============================================================================

object PosCodec : Codec<Pos> {
    override fun encode(v: Pos, b: BinaryBuffer) {
        V3Codec.encode(v.v, b)
    }
    
    override fun decode(b: BinaryBuffer): Pos = 
        Pos(V3Codec.decode(b))
}

object VelCodec : Codec<Vel> {
    override fun encode(v: Vel, b: BinaryBuffer) {
        V3Codec.encode(v.v, b)
    }
    
    override fun decode(b: BinaryBuffer): Vel = 
        Vel(V3Codec.decode(b))
}

object HPCodec : Codec<HP> {
    override fun encode(v: HP, b: BinaryBuffer) {
        b.writeFloat(v.cur)
        b.writeFloat(v.max)
    }
    
    override fun decode(b: BinaryBuffer): HP = HP(
        b.readFloat(),
        b.readFloat()
    )
}

object DmgCodec : Codec<Dmg> {
    override fun encode(v: Dmg, b: BinaryBuffer) {
        b.writeFloat(v.v)
    }
    
    override fun decode(b: BinaryBuffer): Dmg = 
        Dmg(b.readFloat())
}

object TeamCodec : Codec<Team> {
    override fun encode(v: Team, b: BinaryBuffer) {
        b.writeVarInt(v.id)
    }
    
    override fun decode(b: BinaryBuffer): Team = 
        Team(b.readVarInt())
}

// ============================================================================
// Command Codec
// ============================================================================

object CmdCodec : Codec<Cmd> {
    override fun encode(v: Cmd, b: BinaryBuffer) {
        // Write command type
        val type = when (v) {
            is Cmd.Mv -> 0
            is Cmd.Atk -> 1
            is Cmd.Bld -> 2
            is Cmd.Hrv -> 3
            is Cmd.Stp -> 4
        }
        b.writeByte(type.toByte())
        
        // Write common fields
        b.writeVarInt(v.t.toInt())
        b.writeVarInt(v.p)
        
        // Write specific fields
        when (v) {
            is Cmd.Mv -> {
                b.writeVarInt(v.e.size)
                v.e.forEach { b.writeVarInt(it) }
                V3Codec.encode(v.tgt, b)
            }
            is Cmd.Atk -> {
                b.writeVarInt(v.component1().size)
                v.component1().forEach { b.writeVarInt(it) }
                b.writeVarInt(v.tgt)
            }
            is Cmd.Bld -> {
                b.writeVarInt(v.component2())
                b.writeString(v.ut)
                V3Codec.encode(v.pos, b)
            }
            is Cmd.Hrv -> {
                b.writeVarInt(v.h.size)
                v.h.forEach { b.writeVarInt(it) }
                b.writeVarInt(v.r)
            }
            is Cmd.Stp -> {
                b.writeVarInt(v.e.size)
                v.e.forEach { b.writeVarInt(it) }
            }
        }
    }
    
    override fun decode(b: BinaryBuffer): Cmd {
        val type = b.readByte().toInt()
        val t = b.readVarInt().toLong()
        val p = b.readVarInt()
        
        return when (type) {
            0 -> { // Mv
                val count = b.readVarInt()
                val e = List(count) { b.readVarInt() }
                val tgt = V3Codec.decode(b)
                Cmd.Mv(t, p, e, tgt)
            }
            1 -> { // Atk
                val count = b.readVarInt()
                val a = List(count) { b.readVarInt() }
                val tgt = b.readVarInt()
                Cmd.Atk(t, p, a, tgt)
            }
            2 -> { // Bld
                val bid = b.readVarInt()
                val ut = b.readString()
                val pos = V3Codec.decode(b)
                Cmd.Bld(t, p, bid, ut, pos)
            }
            3 -> { // Hrv
                val count = b.readVarInt()
                val h = List(count) { b.readVarInt() }
                val r = b.readVarInt()
                Cmd.Hrv(t, p, h, r)
            }
            4 -> { // Stp
                val count = b.readVarInt()
                val e = List(count) { b.readVarInt() }
                Cmd.Stp(t, p, e)
            }
            else -> throw IllegalArgumentException("Unknown cmd type: $type")
        }
    }
}

// ============================================================================
// Packet Codec
// ============================================================================

object PktCodec : Codec<Pkt> {
    override fun encode(v: Pkt, b: BinaryBuffer) {
        // Write packet type
        val type = when (v) {
            is Pkt.Cmds -> 0
            is Pkt.Sync -> 1
            is Pkt.Jn -> 2
            is Pkt.Lv -> 3
        }
        b.writeByte(type.toByte())
        
        // Write common fields
        b.writeVarInt(v.seq)
        b.writeVarInt(v.ts.toInt())
        
        // Write specific fields
        when (v) {
            is Pkt.Cmds -> {
                b.writeVarInt(v.c.size)
                v.c.forEach { CmdCodec.encode(it, b) }
            }
            is Pkt.Sync -> {
                b.writeVarInt(v.t.toInt())
                b.writeVarInt(v.h)
                b.writeVarInt(v.d.size)
                v.d.forEach { (id, delta) ->
                    b.writeVarInt(id)
                    EDeltaCodec.encode(delta, b)
                }
            }
            is Pkt.Jn -> {
                b.writeVarInt(v.p)
                b.writeVarInt(v.v)
            }
            is Pkt.Lv -> {
                b.writeVarInt(v.p)
            }
        }
    }
    
    override fun decode(b: BinaryBuffer): Pkt {
        val type = b.readByte().toInt()
        val seq = b.readVarInt()
        val ts = b.readVarInt().toLong()
        
        return when (type) {
            0 -> { // Cmds
                val count = b.readVarInt()
                val c = List(count) { CmdCodec.decode(b) }
                Pkt.Cmds(seq, ts, c)
            }
            1 -> { // Sync
                val t = b.readVarInt().toLong()
                val h = b.readVarInt()
                val dCount = b.readVarInt()
                val d = buildMap {
                    repeat(dCount) {
                        val id = b.readVarInt()
                        val delta = EDeltaCodec.decode(b)
                        put(id, delta)
                    }
                }
                Pkt.Sync(seq, ts, t, h, d)
            }
            2 -> { // Jn
                val p = b.readVarInt()
                val v = b.readVarInt()
                Pkt.Jn(seq, ts, p, v)
            }
            3 -> { // Lv
                val p = b.readVarInt()
                Pkt.Lv(seq, ts, p)
            }
            else -> throw IllegalArgumentException("Unknown pkt type: $type")
        }
    }
}

object EDeltaCodec : Codec<EDelta> {
    override fun encode(v: EDelta, b: BinaryBuffer) {
        var flags = 0
        if (v.c) flags = flags or 1
        if (v.d) flags = flags or 2
        b.writeByte(flags.toByte())
        
        b.writeVarInt(v.cmp.size)
        v.cmp.forEach { (k, delta) ->
            b.writeString(k)
            CDeltaCodec.encode(delta, b)
        }
    }
    
    override fun decode(b: BinaryBuffer): EDelta {
        val flags = b.readByte().toInt()
        val c = (flags and 1) != 0
        val d = (flags and 2) != 0
        
        val cmpCount = b.readVarInt()
        val cmp = buildMap {
            repeat(cmpCount) {
                val k = b.readString()
                val delta = CDeltaCodec.decode(b)
                put(k, delta)
            }
        }
        
        return EDelta(c, d, cmp)
    }
}

object CDeltaCodec : Codec<CDelta> {
    override fun encode(v: CDelta, b: BinaryBuffer) {
        // Simplified - in real impl would use component registry
        b.writeByte(if (v.o != null) 1 else 0)
        b.writeByte(if (v.n != null) 1 else 0)
    }
    
    override fun decode(b: BinaryBuffer): CDelta {
        val hasOld = b.readByte() != 0.toByte()
        val hasNew = b.readByte() != 0.toByte()
        // Simplified - would decode actual components
        return CDelta(null, null)
    }
}

// ============================================================================
// Delta Compression
// ============================================================================

object DeltaComp {
    fun compress(old: W, new: W): Map<EntityId, EDelta> = buildMap {
        // Find destroyed entities
        old.forEach { (id, _) ->
            if (id !in new) {
                put(id, EDelta(d = true))
            }
        }
        
        // Find created and modified entities
        new.forEach { (id, ent) ->
            val oldEnt = old[id]
            if (oldEnt == null) {
                // Created
                put(id, EDelta(
                    c = true,
                    cmp = ent.mapValues { (_, comp) ->
                        CDelta(null, comp)
                    }
                ))
            } else {
                // Check for changes
                val cmpDeltas = buildMap<String, CDelta> {
                    // Removed components
                    oldEnt.forEach { (k, oldComp) ->
                        if (k !in ent) {
                            put(k, CDelta(oldComp, null))
                        }
                    }
                    
                    // Added or changed components
                    ent.forEach { (k, newComp) ->
                        val oldComp = oldEnt[k]
                        if (oldComp != newComp) {
                            put(k, CDelta(oldComp, newComp))
                        }
                    }
                }
                
                if (cmpDeltas.isNotEmpty()) {
                    put(id, EDelta(cmp = cmpDeltas))
                }
            }
        }
    }
    
    fun apply(w: W, deltas: Map<EntityId, EDelta>): W = buildMap {
        // Copy unchanged entities
        w.forEach { (id, ent) ->
            if (id !in deltas) {
                put(id, ent)
            }
        }
        
        // Apply deltas
        deltas.forEach { (id, delta) ->
            when {
                delta.d -> {
                    // Entity destroyed, don't add to new world
                }
                delta.c -> {
                    // Create new entity
                    val comps = delta.cmp.mapValues { (_, cDelta) ->
                        cDelta.n!!
                    }
                    put(id, comps)
                }
                else -> {
                    // Modify existing entity
                    val oldEnt = w[id] ?: return@forEach
                    val newEnt = oldEnt.toMutableMap()
                    
                    delta.cmp.forEach { (k, cDelta) ->
                        when {
                            cDelta.n == null -> newEnt.remove(k)
                            else -> newEnt[k] = cDelta.n
                        }
                    }
                    
                    put(id, newEnt)
                }
            }
        }
    }
}

// ============================================================================
// Context-Aware Encoding
// ============================================================================

suspend fun encodeCtx(pkt: Pkt): ByteArray = 
    withContext(kotlinx.coroutines.coroutineContext) {
        val b = BinaryBuffer()
        val comp = codec == 2
        
        if (comp) {
            // Add compression header
            b.writeByte(0xFF.toByte())
        }
        
        PktCodec.encode(pkt, b)
        
        b.toByteArray()
    }

suspend fun decodeCtx(data: ByteArray): Pkt =
    withContext(kotlinx.coroutines.coroutineContext) {
        val b = BinaryBuffer(data)
        
        // Check for compression
        val comp = b.data[0] == 0xFF.toByte()
        if (comp) {
            b.position = 1 // Skip compression header
        }
        
        PktCodec.decode(b)
    }

// ============================================================================
// World Hashing
// ============================================================================

fun W.hash(): Int {
    var h = 0
    forEach { (id, ent) ->
        h = h * 31 + id
        ent.forEach { (k, _) ->
            h = h * 31 + k.hashCode()
        }
    }
    return h
}