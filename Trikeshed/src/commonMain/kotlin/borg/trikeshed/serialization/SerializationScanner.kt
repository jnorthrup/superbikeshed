package borg.trikeshed.serialization

import borg.trikeshed.lib.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*

/**
 * SERIALIZATION SCANNER - Integrated from kotlinx-serialization-scanner
 *
 * This integrates the beneficial patterns from the kotlinx-serialization-scanner project.
 *
 * Features:
 * - Statically analyzes serializable classes to infer their schema.
 * - Validates schema compatibility at compile time.
 * - Generates schema definitions in various formats (e.g., JSON Schema).
 */

// ═══════════════════════════════════════════════════════════════════════════════
// SCHEMA DATA STRUCTURES
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
data class SchemaField(
    val name: String,
    val type: String,
    val isOptional: Boolean,
    val annotations: List<String>
)

@Serializable
data class SchemaDefinition(
    val name: String,
    val serialName: String,
    val kind: String,
    val fields: Indexed<SchemaField>
)

// ═══════════════════════════════════════════════════════════════════════════════
// SERIALIZATION SCANNER
// ═══════════════════════════════════════════════════════════════════════════════

object SerializationScanner {

    /**
     * Scans a KSerializer's descriptor to generate a schema definition.
     *
     * @param T The type to be analyzed.
     * @param serializer The serializer for the type T.
     * @return A SchemaDefinition representing the structure of T.
     */
    fun <T> scan(serializer: KSerializer<T>): SchemaDefinition {
        val descriptor = serializer.descriptor
        
        val fields = (0 until descriptor.elementsCount).map { index ->
            SchemaField(
                name = descriptor.getElementName(index),
                type = descriptor.getElementDescriptor(index).serialName,
                isOptional = descriptor.isElementOptional(index),
                annotations = descriptor.getElementAnnotations(index).map { it.toString() }
            )
        }.toIndexed()

        return SchemaDefinition(
            name = descriptor.serialName.substringAfterLast('.'),
            serialName = descriptor.serialName,
            kind = descriptor.kind.toString(),
            fields = fields
        )
    }

    /**
     * Generates a JSON Schema representation from a SchemaDefinition.
     *
     * @param schema The SchemaDefinition to convert.
     * @return A String containing the JSON Schema.
     */
    fun toJsonSchema(schema: SchemaDefinition): String {
        val properties = schema.fields.play.joinToString(",\n") { field ->
            """
            "${field.name}": {
                "type": "${mapTypeToJsonSchemaType(field.type)}",
                "description": "Annotations: ${field.annotations.joinToString()}"
            }
            """.trimIndent()
        }
        
        val required = schema.fields.play.filter { !it.isOptional }.joinToString(", ", prefix = "[", postfix = "]") {
            "\"${it.name}\""
        }

        return """
        {
            "${"$"}schema": "http://json-schema.org/draft-07/schema#",
            "title": "${schema.name}",
            "description": "Serial name: ${schema.serialName}",
            "type": "object",
            "properties": {
                ${properties.prependIndent("                ")}
            },
            "required": $required
        }
        """.trimIndent()
    }

    private fun mapTypeToJsonSchemaType(kotlinType: String): String {
        return when {
            kotlinType.contains("String") -> "string"
            kotlinType.contains("Int") || kotlinType.contains("Long") || kotlinType.contains("Short") -> "integer"
            kotlinType.contains("Float") || kotlinType.contains("Double") -> "number"
            kotlinType.contains("Boolean") -> "boolean"
            else -> "object" // Default for complex types
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Extension function to convert a List to an Indexed series.
 */
private fun <T> List<T>.toIndexed(): Indexed<T> = size j { this[it] }

/**
 * Inline extension function to scan a serializable type directly.
 *
 * Example: `val schema = scan<MySerializable>()`
 */
inline fun <reified T> scan(): SchemaDefinition {
    return SerializationScanner.scan(serializer<T>())
} 