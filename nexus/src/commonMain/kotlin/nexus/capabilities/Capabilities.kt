package nexus.capabilities

import borg.trikeshed.lib.*
import kotlin.reflect.KClass

/**
 * Capability-based security and introspection system for Nexus
 * 
 * Design principles:
 * - Capabilities as first-class objects
 * - Runtime introspection and discovery
 * - Composable security policies
 * - Self-describing metadata
 */

// Core capability types
sealed interface Capability {
    val id: CapabilityId
    val metadata: CapabilityMetadata
    
    fun implies(other: Capability): Boolean
    fun combine(other: Capability): Capability
}

@kotlin.jvm.JvmInline
value class CapabilityId(val value: String) {
    companion object {
        fun generate(namespace: String, name: String) = 
            CapabilityId("$namespace:$name")
    }
}

data class CapabilityMetadata(
    val name: String,
    val description: String,
    val version: String,
    val author: String? = null,
    val tags: Indexed<String> = emptyArray<String>().toSeries(),
    val dependencies: Indexed<CapabilityId> = emptyArray<CapabilityId>().toSeries(),
    val schema: Schema? = null
)

// Capability implementations
data class SimpleCapability(
    override val id: CapabilityId,
    override val metadata: CapabilityMetadata,
    val permissions: Set<Permission>
) : Capability {
    override fun implies(other: Capability): Boolean = when (other) {
        is SimpleCapability -> permissions.containsAll(other.permissions)
        is CompositeCapability -> other.components.all { implies(it) }
        is DelegatedCapability -> implies(other.base)
    }
    
    override fun combine(other: Capability): Capability = when (other) {
        is SimpleCapability -> SimpleCapability(
            id = CapabilityId.generate("composite", "${id.value}+${other.id.value}"),
            metadata = metadata.merge(other.metadata),
            permissions = permissions + other.permissions
        )
        else -> CompositeCapability(
            id = CapabilityId.generate("composite", "${id.value}+${other.id.value}"),
            metadata = metadata.merge(other.metadata),
            components = arrayOf(this, other).toSeries()
        )
    }
}

data class CompositeCapability(
    override val id: CapabilityId,
    override val metadata: CapabilityMetadata,
    val components: Indexed<Capability>
) : Capability {
    override fun implies(other: Capability): Boolean = 
        components.any { it.implies(other) }
    
    override fun combine(other: Capability): Capability = CompositeCapability(
        id = CapabilityId.generate("composite", "${id.value}+${other.id.value}"),
        metadata = metadata.merge(other.metadata),
        components = (components.toList() + other).toTypedArray().toSeries()
    )
}

data class DelegatedCapability(
    override val id: CapabilityId,
    override val metadata: CapabilityMetadata,
    val base: Capability,
    val restrictions: Indexed<Restriction>
) : Capability {
    override fun implies(other: Capability): Boolean = 
        base.implies(other) && restrictions.all { it.allows(other) }
    
    override fun combine(other: Capability): Capability = 
        base.combine(other)
}

// Permission system
sealed interface Permission {
    val name: String
    val scope: PermissionScope
    
    data class FileSystem(
        override val name: String,
        val operations: Set<FileOperation>,
        val pathPattern: Regex? = null
    ) : Permission {
        override val scope = PermissionScope.LOCAL
    }
    
    data class Network(
        override val name: String,
        val protocols: Set<String>,
        val hostPattern: Regex? = null,
        val portRange: IntRange? = null
    ) : Permission {
        override val scope = PermissionScope.NETWORK
    }
    
    data class Execution(
        override val name: String,
        val commands: Set<String>? = null,
        val environment: Map<String, String>? = null
    ) : Permission {
        override val scope = PermissionScope.SYSTEM
    }
    
    data class Custom(
        override val name: String,
        override val scope: PermissionScope,
        val attributes: Map<String, Any?>
    ) : Permission
}

enum class PermissionScope {
    LOCAL, NETWORK, SYSTEM, GLOBAL
}

enum class FileOperation {
    READ, WRITE, DELETE, CREATE, LIST, EXECUTE
}

// Restrictions for delegated capabilities
sealed interface Restriction {
    fun allows(capability: Capability): Boolean
    
    data class TimeRestriction(
        val validFrom: Long? = null,
        val validUntil: Long? = null
    ) : Restriction {
        override fun allows(capability: Capability): Boolean {
            val now = System.currentTimeMillis()
            return (validFrom == null || now >= validFrom) &&
                   (validUntil == null || now <= validUntil)
        }
    }
    
    data class UsageRestriction(
        val maxUses: Int,
        private var currentUses: Int = 0
    ) : Restriction {
        override fun allows(capability: Capability): Boolean {
            return currentUses < maxUses
        }
        
        fun use() {
            currentUses++
        }
    }
    
    data class ContextRestriction(
        val requiredContext: Map<String, Any?>
    ) : Restriction {
        override fun allows(capability: Capability): Boolean = true // Check against runtime context
    }
}

// Schema for self-description
sealed interface Schema {
    fun validate(value: Any?): ValidationResult
    fun describe(): String
    
    data class ObjectSchema(
        val properties: Map<String, Schema>,
        val required: Set<String> = emptySet(),
        val additionalProperties: Boolean = false
    ) : Schema {
        override fun validate(value: Any?): ValidationResult {
            if (value !is Map<*, *>) {
                return ValidationResult.Error("Expected object, got ${value?.let { it::class.simpleName }}")
            }
            
            val errors = mutableListOf<String>()
            
            // Check required properties
            required.forEach { prop ->
                if (prop !in value) {
                    errors.add("Missing required property: $prop")
                }
            }
            
            // Validate each property
            value.forEach { (key, propValue) ->
                if (key is String) {
                    properties[key]?.let { schema ->
                        when (val result = schema.validate(propValue)) {
                            is ValidationResult.Error -> errors.add("$key: ${result.message}")
                            is ValidationResult.Success -> Unit
                        }
                    } ?: if (!additionalProperties) {
                        errors.add("Unknown property: $key")
                    }
                }
            }
            
            return if (errors.isEmpty()) {
                ValidationResult.Success
            } else {
                ValidationResult.Error(errors.joinToString("; "))
            }
        }
        
        override fun describe(): String = buildString {
            append("{\n")
            properties.forEach { (name, schema) ->
                append("  $name${if (name in required) "" else "?"}: ${schema.describe()}\n")
            }
            append("}")
        }
    }
    
    data class ArraySchema(
        val items: Schema,
        val minItems: Int? = null,
        val maxItems: Int? = null
    ) : Schema {
        override fun validate(value: Any?): ValidationResult {
            when (value) {
                is List<*> -> {
                    if (minItems != null && value.size < minItems) {
                        return ValidationResult.Error("Array has ${value.size} items, minimum is $minItems")
                    }
                    if (maxItems != null && value.size > maxItems) {
                        return ValidationResult.Error("Array has ${value.size} items, maximum is $maxItems")
                    }
                    
                    val errors = value.mapIndexedNotNull { index, item ->
                        when (val result = items.validate(item)) {
                            is ValidationResult.Error -> "[$index]: ${result.message}"
                            is ValidationResult.Success -> null
                        }
                    }
                    
                    return if (errors.isEmpty()) {
                        ValidationResult.Success
                    } else {
                        ValidationResult.Error(errors.joinToString("; "))
                    }
                }
                is Array<*> -> validate(value.toList())
                else -> ValidationResult.Error("Expected array, got ${value?.let { it::class.simpleName }}")
            }
        }
        
        override fun describe(): String = "${items.describe()}[]"
    }
    
    data class StringSchema(
        val pattern: Regex? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null,
        val enum: Set<String>? = null
    ) : Schema {
        override fun validate(value: Any?): ValidationResult {
            if (value !is String) {
                return ValidationResult.Error("Expected string, got ${value?.let { it::class.simpleName }}")
            }
            
            if (minLength != null && value.length < minLength) {
                return ValidationResult.Error("String length ${value.length} is less than minimum $minLength")
            }
            if (maxLength != null && value.length > maxLength) {
                return ValidationResult.Error("String length ${value.length} exceeds maximum $maxLength")
            }
            if (pattern != null && !pattern.matches(value)) {
                return ValidationResult.Error("String does not match pattern: $pattern")
            }
            if (enum != null && value !in enum) {
                return ValidationResult.Error("Value must be one of: ${enum.joinToString(", ")}")
            }
            
            return ValidationResult.Success
        }
        
        override fun describe(): String = when {
            enum != null -> enum.joinToString(" | ") { "\"$it\"" }
            pattern != null -> "string (pattern: $pattern)"
            else -> "string"
        }
    }
    
    data class NumberSchema(
        val minimum: Double? = null,
        val maximum: Double? = null,
        val multipleOf: Double? = null
    ) : Schema {
        override fun validate(value: Any?): ValidationResult {
            val number = when (value) {
                is Number -> value.toDouble()
                else -> return ValidationResult.Error("Expected number, got ${value?.let { it::class.simpleName }}")
            }
            
            if (minimum != null && number < minimum) {
                return ValidationResult.Error("Value $number is less than minimum $minimum")
            }
            if (maximum != null && number > maximum) {
                return ValidationResult.Error("Value $number exceeds maximum $maximum")
            }
            if (multipleOf != null && number % multipleOf != 0.0) {
                return ValidationResult.Error("Value $number is not a multiple of $multipleOf")
            }
            
            return ValidationResult.Success
        }
        
        override fun describe(): String = "number"
    }
    
    data class BooleanSchema(
        val default: Boolean? = null
    ) : Schema {
        override fun validate(value: Any?): ValidationResult = when (value) {
            is Boolean -> ValidationResult.Success
            else -> ValidationResult.Error("Expected boolean, got ${value?.let { it::class.simpleName }}")
        }
        
        override fun describe(): String = "boolean"
    }
    
    data class UnionSchema(
        val schemas: Indexed<Schema>
    ) : Schema {
        override fun validate(value: Any?): ValidationResult {
            val results = schemas.map { it.validate(value) }
            return if (results.any { it is ValidationResult.Success }) {
                ValidationResult.Success
            } else {
                ValidationResult.Error("Value does not match any of the union schemas")
            }
        }
        
        override fun describe(): String = 
            schemas.joinToString(" | ") { it.describe() }
    }
}

sealed interface ValidationResult {
    data object Success : ValidationResult
    data class Error(val message: String) : ValidationResult
}

// Capability registry with introspection
class CapabilityRegistry {
    private val capabilities = mutableMapOf<CapabilityId, Capability>()
    private val aliases = mutableMapOf<String, CapabilityId>()
    
    fun register(capability: Capability, aliases: Indexed<String> = emptyArray<String>().toSeries()) {
        capabilities[capability.id] = capability
        aliases.forEach { alias ->
            this.aliases[alias] = capability.id
        }
    }
    
    fun get(id: CapabilityId): Capability? = capabilities[id]
    
    fun getByAlias(alias: String): Capability? = aliases[alias]?.let { capabilities[it] }
    
    fun search(query: CapabilityQuery): Indexed<Capability> {
        return capabilities.values
            .filter { query.matches(it) }
            .toTypedArray()
            .toSeries()
    }
    
    fun introspect(id: CapabilityId): CapabilityIntrospection? {
        val capability = capabilities[id] ?: return null
        return CapabilityIntrospection(
            capability = capability,
            dependencies = resolveDependencies(capability),
            dependents = findDependents(capability),
            impliedBy = findImpliers(capability),
            implies = findImplied(capability)
        )
    }
    
    private fun resolveDependencies(capability: Capability): Indexed<Capability> {
        return capability.metadata.dependencies
            .mapNotNull { capabilities[it] }
            .toTypedArray()
            .toSeries()
    }
    
    private fun findDependents(capability: Capability): Indexed<Capability> {
        return capabilities.values
            .filter { it.metadata.dependencies.any { dep -> dep == capability.id } }
            .toTypedArray()
            .toSeries()
    }
    
    private fun findImpliers(capability: Capability): Indexed<Capability> {
        return capabilities.values
            .filter { it.implies(capability) && it.id != capability.id }
            .toTypedArray()
            .toSeries()
    }
    
    private fun findImplied(capability: Capability): Indexed<Capability> {
        return capabilities.values
            .filter { capability.implies(it) && it.id != capability.id }
            .toTypedArray()
            .toSeries()
    }
}

data class CapabilityQuery(
    val tags: Set<String>? = null,
    val author: String? = null,
    val namePattern: Regex? = null,
    val hasSchema: Boolean? = null,
    val permissions: Set<Permission>? = null
) {
    fun matches(capability: Capability): Boolean {
        if (tags != null && !capability.metadata.tags.any { it in tags }) return false
        if (author != null && capability.metadata.author != author) return false
        if (namePattern != null && !namePattern.matches(capability.metadata.name)) return false
        if (hasSchema == true && capability.metadata.schema == null) return false
        if (permissions != null && capability is SimpleCapability) {
            if (!permissions.all { perm -> capability.permissions.contains(perm) }) return false
        }
        return true
    }
}

data class CapabilityIntrospection(
    val capability: Capability,
    val dependencies: Indexed<Capability>,
    val dependents: Indexed<Capability>,
    val impliedBy: Indexed<Capability>,
    val implies: Indexed<Capability>
)

// Extension functions for metadata
fun CapabilityMetadata.merge(other: CapabilityMetadata): CapabilityMetadata = CapabilityMetadata(
    name = "$name + ${other.name}",
    description = "$description; ${other.description}",
    version = "composite",
    author = listOfNotNull(author, other.author).distinct().joinToString(", ").takeIf { it.isNotEmpty() },
    tags = (tags.toList() + other.tags.toList()).distinct().toTypedArray().toSeries(),
    dependencies = (dependencies.toList() + other.dependencies.toList()).distinct().toTypedArray().toSeries(),
    schema = null // Composite schemas are complex
)