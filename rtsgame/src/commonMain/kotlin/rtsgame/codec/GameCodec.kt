package rtsgame.codec

import rtsgame.core.*
import kotlinx.coroutines.withContext

/**
 * High-performance binary codec for game state serialization
 * Uses varint encoding, delta compression, and context-aware optimization
 */

// ============================================================================
// Codec Registry
// ============================================================================

object CodecRegistry {
    private val codecs = mutableMapOf<String, Codec<*>>()
    
    init {
        // Register core codecs
        register("vec2", Vec2Codec)
        register("vec3", Vec3Codec)
        register("position", PositionCodec)
        register("velocity", VelocityCodec)
        register("health", HealthCodec)
        register("damage", DamageCodec)
        register("team", TeamCodec)
        register("command", CommandCodec)
        register("packet", PacketCodec)
    }
    
    fun register(type: String, codec: Codec<*>) {
        codecs[type] = codec
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> get(type: String): Codec<T>? = codecs[type] as? Codec<T>
}

// ============================================================================
// Primitive Codecs
// ============================================================================

object Vec2Codec : Codec<Vec2> {
    override fun encode(value: Vec2, buffer: BinaryBuffer) {
        buffer.writeFloat(value.x)
        buffer.writeFloat(value.y)
    }
    
    override fun decode(buffer: BinaryBuffer): Vec2 = Vec2(
        buffer.readFloat(),
        buffer.readFloat()
    )
}

object Vec3Codec : Codec<Vec3> {
    override fun encode(value: Vec3, buffer: BinaryBuffer) {
        buffer.writeFloat(value.x)
        buffer.writeFloat(value.y)
        buffer.writeFloat(value.z)
    }
    
    override fun decode(buffer: BinaryBuffer): Vec3 = Vec3(
        buffer.readFloat(),
        buffer.readFloat(),
        buffer.readFloat()
    )
}

// ============================================================================
// Component Codecs
// ============================================================================

object PositionCodec : Codec<Position> {
    override fun encode(value: Position, buffer: BinaryBuffer) {
        Vec3Codec.encode(value.vec, buffer)
    }
    
    override fun decode(buffer: BinaryBuffer): Position = 
        Position(Vec3Codec.decode(buffer))
}

object VelocityCodec : Codec<Velocity> {
    override fun encode(value: Velocity, buffer: BinaryBuffer) {
        Vec3Codec.encode(value.vec, buffer)
    }
    
    override fun decode(buffer: BinaryBuffer): Velocity = 
        Velocity(Vec3Codec.decode(buffer))
}

object HealthCodec : Codec<Health> {
    override fun encode(value: Health, buffer: BinaryBuffer) {
        buffer.writeFloat(value.current)
        buffer.writeFloat(value.max)
    }
    
    override fun decode(buffer: BinaryBuffer): Health = Health(
        buffer.readFloat(),
        buffer.readFloat()
    )
}

object DamageCodec : Codec<Damage> {
    override fun encode(value: Damage, buffer: BinaryBuffer) {
        buffer.writeFloat(value.value)
    }
    
    override fun decode(buffer: BinaryBuffer): Damage = 
        Damage(buffer.readFloat())
}

object TeamCodec : Codec<Team> {
    override fun encode(value: Team, buffer: BinaryBuffer) {
        buffer.writeVarInt(value.id)
    }
    
    override fun decode(buffer: BinaryBuffer): Team = 
        Team(buffer.readVarInt())
}

// ============================================================================
// Command Codecs
// ============================================================================

object CommandCodec : Codec<Command> {
    override fun encode(value: Command, buffer: BinaryBuffer) {
        // Write command type
        val type = when (value) {
            is Command.Move -> 0
            is Command.Attack -> 1
            is Command.Build -> 2
            is Command.Harvest -> 3
            is Command.Stop -> 4
        }
        buffer.writeByte(type.toByte())
        
        // Write common fields
        buffer.writeVarInt(value.tick.toInt())
        buffer.writeVarInt(value.playerId)
        
        // Write specific fields
        when (value) {
            is Command.Move -> {
                buffer.writeVarInt(value.entities.size)
                value.entities.forEach { buffer.writeVarInt(it) }
                Vec3Codec.encode(value.target, buffer)
            }
            is Command.Attack -> {
                buffer.writeVarInt(value.attackers.size)
                value.attackers.forEach { buffer.writeVarInt(it) }
                buffer.writeVarInt(value.target)
            }
            is Command.Build -> {
                buffer.writeVarInt(value.builderId)
                buffer.writeVarInt(value.unitType.length)
                buffer.data = buffer.data.plus(value.unitType.toByteArray())
                buffer.position += value.unitType.length
                Vec3Codec.encode(value.position, buffer)
            }
            is Command.Harvest -> {
                buffer.writeVarInt(value.harvesters.size)
                value.harvesters.forEach { buffer.writeVarInt(it) }
                buffer.writeVarInt(value.resourceId)
            }
            is Command.Stop -> {
                buffer.writeVarInt(value.entities.size)
                value.entities.forEach { buffer.writeVarInt(it) }
            }
        }
    }
    
    override fun decode(buffer: BinaryBuffer): Command {
        val type = buffer.readByte().toInt()
        val tick = buffer.readVarInt().toLong()
        val playerId = buffer.readVarInt()
        
        return when (type) {
            0 -> { // Move
                val count = buffer.readVarInt()
                val entities = List(count) { buffer.readVarInt() }
                val target = Vec3Codec.decode(buffer)
                Command.Move(tick, playerId, entities, target)
            }
            1 -> { // Attack
                val count = buffer.readVarInt()
                val attackers = List(count) { buffer.readVarInt() }
                val target = buffer.readVarInt()
                Command.Attack(tick, playerId, attackers, target)
            }
            2 -> { // Build
                val builderId = buffer.readVarInt()
                val unitTypeLength = buffer.readVarInt()
                val unitType = String(buffer.data.sliceArray(buffer.position until buffer.position + unitTypeLength))
                buffer.position += unitTypeLength
                val position = Vec3Codec.decode(buffer)
                Command.Build(tick, playerId, builderId, unitType, position)
            }
            3 -> { // Harvest
                val count = buffer.readVarInt()
                val harvesters = List(count) { buffer.readVarInt() }
                val resourceId = buffer.readVarInt()
                Command.Harvest(tick, playerId, harvesters, resourceId)
            }
            4 -> { // Stop
                val count = buffer.readVarInt()
                val entities = List(count) { buffer.readVarInt() }
                Command.Stop(tick, playerId, entities)
            }
            else -> throw IllegalArgumentException("Unknown command type: $type")
        }
    }
}

// ============================================================================
// Packet Codecs
// ============================================================================

object PacketCodec : Codec<Packet> {
    override fun encode(value: Packet, buffer: BinaryBuffer) {
        // Write packet type
        val type = when (value) {
            is Packet.Commands -> 0
            is Packet.StateSync -> 1
            is Packet.Join -> 2
            is Packet.Leave -> 3
        }
        buffer.writeByte(type.toByte())
        
        // Write common fields
        buffer.writeVarInt(value.sequence)
        buffer.writeVarInt(value.timestamp.toInt())
        
        // Write specific fields
        when (value) {
            is Packet.Commands -> {
                buffer.writeVarInt(value.commands.size)
                value.commands.forEach { CommandCodec.encode(it, buffer) }
            }
            is Packet.StateSync -> {
                buffer.writeVarInt(value.tick.toInt())
                buffer.writeVarInt(value.worldHash)
                buffer.writeVarInt(value.entityDeltas.size)
                value.entityDeltas.forEach { (id, delta) ->
                    buffer.writeVarInt(id)
                    EntityDeltaCodec.encode(delta, buffer)
                }
            }
            is Packet.Join -> {
                buffer.writeVarInt(value.playerId)
                buffer.writeVarInt(value.protocolVersion)
            }
            is Packet.Leave -> {
                buffer.writeVarInt(value.playerId)
            }
        }
    }
    
    override fun decode(buffer: BinaryBuffer): Packet {
        val type = buffer.readByte().toInt()
        val sequence = buffer.readVarInt()
        val timestamp = buffer.readVarInt().toLong()
        
        return when (type) {
            0 -> { // Commands
                val count = buffer.readVarInt()
                val commands = List(count) { CommandCodec.decode(buffer) }
                Packet.Commands(sequence, timestamp, commands)
            }
            1 -> { // StateSync
                val tick = buffer.readVarInt().toLong()
                val worldHash = buffer.readVarInt()
                val deltaCount = buffer.readVarInt()
                val deltas = buildMap {
                    repeat(deltaCount) {
                        val id = buffer.readVarInt()
                        val delta = EntityDeltaCodec.decode(buffer)
                        put(id, delta)
                    }
                }
                Packet.StateSync(sequence, timestamp, tick, worldHash, deltas)
            }
            2 -> { // Join
                val playerId = buffer.readVarInt()
                val protocolVersion = buffer.readVarInt()
                Packet.sequence j timestamp, playerId, protocolVersion
            }
            3 -> { // Leave
                val playerId = buffer.readVarInt()
                Packet.Leave(sequence, timestamp, playerId)
            }
            else -> throw IllegalArgumentException("Unknown packet type: $type")
        }
    }
}

object EntityDeltaCodec : Codec<EntityDelta> {
    override fun encode(value: EntityDelta, buffer: BinaryBuffer) {
        var flags = 0
        if (value.created) flags = flags or 1
        if (value.destroyed) flags = flags or 2
        buffer.writeByte(flags.toByte())
        
        buffer.writeVarInt(value.components.size)
        value.components.forEach { (key, delta) ->
            buffer.writeVarInt(key.length)
            key.toByteArray().forEach { buffer.writeByte(it) }
            ComponentDeltaCodec.encode(delta, buffer)
        }
    }
    
    override fun decode(buffer: BinaryBuffer): EntityDelta {
        val flags = buffer.readByte().toInt()
        val created = (flags and 1) != 0
        val destroyed = (flags and 2) != 0
        
        val componentCount = buffer.readVarInt()
        val components = buildMap {
            repeat(componentCount) {
                val keyLength = buffer.readVarInt()
                val key = String(ByteArray(keyLength) { buffer.readByte() })
                val delta = ComponentDeltaCodec.decode(buffer)
                put(key, delta)
            }
        }
        
        return EntityDelta(created, destroyed, components)
    }
}

object ComponentDeltaCodec : Codec<ComponentDelta> {
    override fun encode(value: ComponentDelta, buffer: BinaryBuffer) {
        // Simplified - in real implementation would use component registry
        buffer.writeByte(if (value.old != null) 1 else 0)
        buffer.writeByte(if (value.new != null) 1 else 0)
    }
    
    override fun decode(buffer: BinaryBuffer): ComponentDelta {
        val hasOld = buffer.readByte() != 0.toByte()
        val hasNew = buffer.readByte() != 0.toByte()
        // Simplified - would decode actual components
        return ComponentDelta(null, null)
    }
}

// ============================================================================
// Delta Compression
// ============================================================================

object DeltaCompressor {
    fun compress(old: World, new: World): Map<EntityId, EntityDelta> = buildMap {
        // Find destroyed entities
        old.forEach { (id, _) ->
            if (id !in new) {
                put(id, EntityDelta(destroyed = true))
            }
        }
        
        // Find created and modified entities
        new.forEach { (id, entity) ->
            val oldEntity = old[id]
            if (oldEntity == null) {
                // Created
                put(id, EntityDelta(
                    created = true,
                    components = entity.mapValues { (_, component) ->
                        ComponentDelta(null, component)
                    }
                ))
            } else {
                // Check for changes
                val componentDeltas = buildMap<String, ComponentDelta> {
                    // Removed components
                    oldEntity.forEach { (key, oldComponent) ->
                        if (key !in entity) {
                            put(key, ComponentDelta(oldComponent, null))
                        }
                    }
                    
                    // Added or changed components
                    entity.forEach { (key, newComponent) ->
                        val oldComponent = oldEntity[key]
                        if (oldComponent != newComponent) {
                            put(key, ComponentDelta(oldComponent, newComponent))
                        }
                    }
                }
                
                if (componentDeltas.isNotEmpty()) {
                    put(id, EntityDelta(components = componentDeltas))
                }
            }
        }
    }
    
    fun apply(world: World, deltas: Map<EntityId, EntityDelta>): World = buildMap {
        // Copy unchanged entities
        world.forEach { (id, entity) ->
            if (id !in deltas) {
                put(id, entity)
            }
        }
        
        // Apply deltas
        deltas.forEach { (id, delta) ->
            when {
                delta.destroyed -> {
                    // Entity destroyed, don't add to new world
                }
                delta.created -> {
                    // Create new entity
                    val components = delta.components.mapValues { (_, componentDelta) ->
                        componentDelta.new!!
                    }
                    put(id, components)
                }
                else -> {
                    // Modify existing entity
                    val oldEntity = world[id] ?: return@forEach
                    val newEntity = oldEntity.toMutableMap()
                    
                    delta.components.forEach { (key, componentDelta) ->
                        when {
                            componentDelta.new == null -> newEntity.remove(key)
                            else -> newEntity[key] = componentDelta.new
                        }
                    }
                    
                    put(id, newEntity)
                }
            }
        }
    }
}

// ============================================================================
// Context-Aware Encoding
// ============================================================================

suspend fun encodeWithContext(packet: Packet): ByteArray = 
    withContext(kotlinx.coroutines.coroutineContext) {
        val buffer = BinaryBuffer()
        val compression = codecVersion == 2
        
        if (compression) {
            // Add compression header
            buffer.writeByte(0xFF.toByte())
        }
        
        PacketCodec.encode(packet, buffer)
        
        if (compression) {
            // Apply compression (simplified)
            return@withContext buffer.toByteArray()
        } else {
            return@withContext buffer.toByteArray()
        }
    }

suspend fun decodeWithContext(data: ByteArray): Packet =
    withContext(kotlinx.coroutines.coroutineContext) {
        val buffer = BinaryBuffer(data)
        
        // Check for compression
        val compressed = buffer.data[0] == 0xFF.toByte()
        if (compressed) {
            buffer.position = 1 // Skip compression header
        }
        
        PacketCodec.decode(buffer)
    }