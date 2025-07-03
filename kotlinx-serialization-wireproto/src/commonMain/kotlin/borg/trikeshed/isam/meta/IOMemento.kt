package borg.trikeshed.isam.meta

import borg.trikeshed.cursor.TypeMemento

/**
 * Input/Output memento for storing metadata about data structures.
 * Used in ISAM data files and cursor operations.
 */
class IOMemento : TypeMemento {
    var name: String? = null
    var type: String? = null  
    var width: Int? = null
    var nullable: Boolean? = null
    var encoding: String? = null
    var format: String? = null
    
    override val networkSize: Int? get() = width
    
    fun createDecoder(size: Int): (ByteArray) -> Any? {
        return when (type) {
            "IoInt" -> { bytes -> bytes.take(4).fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) } }
            "IoLong" -> { bytes -> bytes.take(8).fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) } }
            "IoDouble" -> { bytes -> bytes.take(8).let { it.toByteArray().let { ba -> Double.fromBits(ba.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }) } } }
            "IoString" -> { bytes -> bytes.decodeToString() }
            "IoBoolean" -> { bytes -> bytes.isNotEmpty() && bytes[0] != 0.toByte() }
            else -> { bytes -> bytes }
        }
    }
    
    fun createEncoder(size: Int): (Any?) -> ByteArray {
        return when (type) {
            "IoInt" -> { value -> 
                val intValue = (value as? Int) ?: 0
                ByteArray(4) { i -> ((intValue shr (24 - i * 8)) and 0xFF).toByte() }
            }
            "IoLong" -> { value -> 
                val longValue = (value as? Long) ?: 0L
                ByteArray(8) { i -> ((longValue shr (56 - i * 8)) and 0xFF).toByte() }
            }
            "IoDouble" -> { value -> 
                val doubleValue = (value as? Double) ?: 0.0
                val bits = doubleValue.toBits()
                ByteArray(8) { i -> ((bits shr (56 - i * 8)) and 0xFF).toByte() }
            }
            "IoString" -> { value -> 
                val stringValue = (value as? String) ?: ""
                stringValue.encodeToByteArray()
            }
            "IoBoolean" -> { value -> 
                val boolValue = (value as? Boolean) ?: false
                byteArrayOf(if (boolValue) 1 else 0)
            }
            else -> { value -> byteArrayOf() }
        }
    }
    
    companion object {
        fun create(name: String? = null, type: String? = null, width: Int? = null, nullable: Boolean? = null): IOMemento {
            return IOMemento().apply {
                this.name = name
                this.type = type
                this.width = width
                this.nullable = nullable
            }
        }
        
        fun valueOf(type: String): IOMemento {
            return when (type) {
                "IoInt" -> create(type = type, width = 4)
                "IoLong" -> create(type = type, width = 8)
                "IoDouble" -> create(type = type, width = 8)
                "IoString" -> create(type = type, width = null)
                "IoBoolean" -> create(type = type, width = 1)
                else -> create(type = type, width = null)
            }
        }
    }
} 