package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

/**
 * Simple TrikeShed JSON Serializer - No bitmap overhead
 * Direct string parsing with kotlinx.serialization integration
 */
object SimpleTrikeShedJson {
    
    /**
     * Default simple JSON format
     */
    val Default = SimpleTrikeShedJsonFormat(SimpleTrikeShedJsonConfiguration())
    
    /**
     * Lenient configuration for flexible parsing
     */
    val Lenient = SimpleTrikeShedJsonFormat(SimpleTrikeShedJsonConfiguration(
        ignoreUnknownKeys = true,
        isLenient = true
    ))
    
    /**
     * Create custom simple JSON format
     */
    fun create(block: SimpleTrikeShedJsonConfiguration.() -> SimpleTrikeShedJsonConfiguration): SimpleTrikeShedJsonFormat {
        val config = SimpleTrikeShedJsonConfiguration().block()
        return SimpleTrikeShedJsonFormat(config)
    }
}

/**
 * Configuration for simple TrikeShed JSON serialization
 */
data class SimpleTrikeShedJsonConfiguration(
    val ignoreUnknownKeys: Boolean = false,
    val isLenient: Boolean = false,
    val allowStructuredMapKeys: Boolean = false,
    val useArrayPolymorphism: Boolean = false,
    val classDiscriminator: String = "type",
    val explicitNulls: Boolean = true
)

/**
 * Simple TrikeShed JSON Format - kotlinx.serialization integration without bitmap
 */
class SimpleTrikeShedJsonFormat(
    internal val configuration: SimpleTrikeShedJsonConfiguration
) : StringFormat {
    
    override val serializersModule: SerializersModule = EmptySerializersModule()
    
    /**
     * Decode using simple TrikeShed scanner
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val decoder = createSimpleDecoder(string)
        return decoder.decodeSerializableValue(deserializer)
    }
    
    /**
     * Encode using TrikeShed structures (fallback to kotlinx for now)
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        // Use standard kotlinx.serialization for encoding
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
    
    internal fun createSimpleDecoder(input: String): SimpleTrikeShedJsonDecoder {
        val scanner = SimpleJsonScanner(input)
        val document = scanner.scan()
        return SimpleTrikeShedJsonDecoder(serializersModule, input, scanner, document)
    }
}

/**
 * Simple TrikeShed JSON Decoder - kotlinx.serialization Decoder using simple scanner
 */
class SimpleTrikeShedJsonDecoder(
    override val serializersModule: SerializersModule,
    internal val input: String,
    internal val scanner: SimpleJsonScanner,
    internal val document: SimpleJsonDocument,
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
        return value?.component2() != null && value.component2() != "null"
    }
    
    override fun decodeNull(): Nothing? {
        val value = scanner.query(currentPath)
        if (value?.component2() != null && value.component2() != "null") {
            throw SerializationException("Expected null at path: $currentPath")
        }
        return null
    }
    
    // === Composite Decoding ===
    
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return SimpleTrikeShedJsonDecoder(serializersModule, input, scanner, document, currentPath, 0)
    }
    
    override fun endStructure(descriptor: SerialDescriptor) {
        // No-op for simple scanner
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
inline fun <reified T> String.decodeSimpleTrikeShedJson(): T =
    SimpleTrikeShedJson.Default.decodeFromString<T>(this)

inline fun <reified T> T.encodeSimpleTrikeShedJson(): String =
    SimpleTrikeShedJson.Default.encodeToString(this)

/**
 * Simple serializer registry for TrikeShed JSON without bitmap
 */
object SimpleTrikeShedSerializers {
    
    /**
     * Create module for simple TrikeShed JSON integration
     */
    fun createModule(): SerializersModule = EmptySerializersModule()
}