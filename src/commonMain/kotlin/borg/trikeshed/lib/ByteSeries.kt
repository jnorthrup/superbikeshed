package borg.trikeshed.lib

class ByteSeries(private val bytes: ByteArray) {
    val size: Int get() = bytes.size
    
    fun toByteArray(): ByteArray = bytes.copyOf()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteSeries) return false
        return bytes.contentEquals(other.bytes)
    }
    
    override fun hashCode(): Int = bytes.contentHashCode()
    
    override fun toString(): String = bytes.joinToString("") { "%02x".format(it) }
    
    companion object {
        fun fromString(hex: String): ByteSeries {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return ByteSeries(bytes)
        }
    }
}

