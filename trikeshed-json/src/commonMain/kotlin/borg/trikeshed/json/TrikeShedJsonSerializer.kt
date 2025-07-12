package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

/**
 * TrikeShed JSON Serializer Registry - Local kotlinx.serialization integration
 * Offers both bitmap and simple JSON scanning options
 */
object TrikeShedJson {
    
    /**
     * Default TrikeShed JSON format using bitmap scanner (with parallel scanning)
     */
    val Default = TrikeShedJsonFormat(TrikeShedJsonConfiguration())
    
    /**
     * Simple TrikeShed JSON format without bitmap overhead
     */
    val Simple = SimpleTrikeShedJson.Default
    
    /**
     * Lenient configuration for flexible parsing (bitmap)
     */
    val Lenient = TrikeShedJsonFormat(TrikeShedJsonConfiguration(
        ignoreUnknownKeys = true,
        isLenient = true
    ))
    
    /**
     * Simple lenient configuration without bitmap
     */
    val SimpleLenient = SimpleTrikeShedJson.Lenient
    
    /**
     * Create custom TrikeShed JSON format with bitmap scanning
     */
    fun create(block: TrikeShedJsonConfiguration.() -> TrikeShedJsonConfiguration): TrikeShedJsonFormat {
        val config = TrikeShedJsonConfiguration().block()
        return TrikeShedJsonFormat(config)
    }
    
    /**
     * Create custom simple TrikeShed JSON format without bitmap
     */
    fun createSimple(block: SimpleTrikeShedJsonConfiguration.() -> SimpleTrikeShedJsonConfiguration): SimpleTrikeShedJsonFormat {
        val config = SimpleTrikeShedJsonConfiguration().block()
        return SimpleTrikeShedJsonFormat(config)
    }
}

/**
 * Configuration for TrikeShed JSON serialization
 */
data class TrikeShedJsonConfiguration(
    val ignoreUnknownKeys: Boolean = false,
    val isLenient: Boolean = false,
    val allowStructuredMapKeys: Boolean = false,
    val useArrayPolymorphism: Boolean = false,
    val classDiscriminator: String = "type",
    val explicitNulls: Boolean = true,
    val useBitmapAcceleration: Boolean = true,
    val useEnhancedBitmap: Boolean = false,
    val parallelChunkSize: Int = 1024
)

/**
 * TrikeShed JSON Format - kotlinx.serialization integration with bitmap backend
 */
class TrikeShedJsonFormat(
    internal val configuration: TrikeShedJsonConfiguration
) : StringFormat {
    
    override val serializersModule: SerializersModule = EmptySerializersModule()
    
    /**
     * Decode using TrikeShed bitmap scanner
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val decoder = createBitmapDecoder(string)
        return decoder.decodeSerializableValue(deserializer)
    }
    
    /**
     * Encode using TrikeShed structures (fallback to kotlinx for now)
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        // TODO: Implement TrikeShed bitmap-based encoding
        // For now, fallback to standard kotlinx.serialization
        val json = Json {
            ignoreUnknownKeys = configuration.ignoreUnknownKeys
            isLenient = configuration.isLenient
            allowStructuredMapKeys = configuration.allowStructuredMapKeys
            useArrayPolymorphism = configuration.useArrayPolymorphism
            classDiscriminator = configuration.classDiscriminator
            explicitNulls = configuration.explicitNulls
        }
        return json.encodeToString(serializer, value)
    }
    
    internal fun createBitmapDecoder(input: String): TrikeShedJsonDecoder {
        val scanner = JsonScannerCompact(input)
        val document = scanner.scan()
        return TrikeShedJsonDecoder(serializersModule, input, scanner, document)
    }
}

/**
 * TrikeShed JSON Decoder - kotlinx.serialization Decoder using bitmap scanner
 */
class TrikeShedJsonDecoder(
    override val serializersModule: SerializersModule,
    internal val input: String,
    internal val scanner: JsonScannerCompact,
    internal val document: JsonDocument,
    internal var currentPath: String = "",
    internal var elementIndex: Int = 0
) : AbstractDecoder() {
    
    // === Core Decoding Interface ===
    
    override fun decodeBoolean(): Boolean {
        val value = scanner.query(currentPath)
        return when (val v = value?.component2()) {
            is Boolean -> v
            is String -> when (v) {
                "true" -> true
                "false" -> false
                else -> throw SerializationException("Expected boolean at path: $currentPath, got: $v")
            }
            else -> throw SerializationException("Expected boolean at path: $currentPath, got: $v")
        }
    }
    
    override fun decodeByte(): Byte = decodeNumber().toInt().toByte()
    override fun decodeShort(): Short = decodeNumber().toInt().toShort()
    override fun decodeInt(): Int = decodeNumber().toInt()
    override fun decodeLong(): Long = decodeNumber().toLong()
    override fun decodeFloat(): Float = decodeNumber().toFloat()
    override fun decodeDouble(): Double = decodeNumber()
    
    internal fun decodeNumber(): Double {
        val value = scanner.query(currentPath)
        return when (val v = value?.component2()) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: throw SerializationException("Invalid number: $v at path: $currentPath")
            else -> throw SerializationException("Expected number at path: $currentPath, got: $v")
        }
    }
    
    override fun decodeChar(): Char {
        val str = decodeString()
        if (str.length != 1) throw SerializationException("Expected single char, got: $str")
        return str[0]
    }
    
    override fun decodeString(): String {
        val value = scanner.query(currentPath)
        return when (val v = value?.component2()) {
            is String -> v
            else -> throw SerializationException("Expected string at path: $currentPath")
        }
    }
    
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeString()
        val index = enumDescriptor.getElementIndex(value)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw SerializationException("Unknown enum value: $value")
        }
        return index
    }
    
    override fun decodeNotNullMark(): Boolean {
        val value = scanner.query(currentPath)
        return value?.component2() != null
    }
    
    override fun decodeNull(): Nothing? {
        val value = scanner.query(currentPath)
        if (value?.component2() != null) throw SerializationException("Expected null at path: $currentPath")
        return null
    }
    
    // === Composite Decoding ===
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return TrikeShedJsonDecoder(serializersModule, input, scanner, document, currentPath, 0)
    }
    
    override fun endStructure(descriptor: SerialDescriptor) {
        // No-op for bitmap scanner
    }
    
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        
        val name = descriptor.getElementName(elementIndex)
        currentPath = name // For simple JSON objects, use just the field name
        
        return elementIndex++
    }
}

/**
 * Extension functions for easy usage
 */
inline fun <reified T> String.decodeTrikeShedJson(): T =
    TrikeShedJson.Default.decodeFromString<T>(this)

inline fun <reified T> T.encodeTrikeShedJson(): String =
    TrikeShedJson.Default.encodeToString(this)

/**
 * Local serializer registry for TrikeShed bitmap JSON
 */
object TrikeShedSerializers {
    
    /**
     * Create module for TrikeShed JSON integration
     */
    fun createModule(): SerializersModule = EmptySerializersModule()
}