package nexus.core

import kotlinx.serialization.Serializable

/**
 * Inline value classes for type-safe identifiers and domain values.
 * These provide one-way conversion and prevent mixing up similar types.
 */

@Serializable
@JvmInline
value class NodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "NodeId cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "NodeId must contain only alphanumeric, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class NetworkId(val value: String) {
    init {
        require(value.isNotBlank()) { "NetworkId cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "NetworkId must contain only alphanumeric, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class WorkflowId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkflowId cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "WorkflowId must contain only alphanumeric, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "TaskId must contain only alphanumeric, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class PluginName(val value: String) {
    init {
        require(value.isNotBlank()) { "PluginName cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z][a-zA-Z0-9_-]*$"))) { "PluginName must start with letter and contain only alphanumeric, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class GossipTopic(val value: String) {
    init {
        require(value.isNotBlank()) { "GossipTopic cannot be blank" }
        require(value.matches(Regex("^[a-zA-Z0-9/_-]+$"))) { "GossipTopic must contain only alphanumeric, slash, underscore, or hyphen" }
    }
    
    override fun toString(): String = value
}

@Serializable
@JvmInline
value class AuthToken(val value: String) {
    init {
        require(value.isNotBlank()) { "AuthToken cannot be blank" }
        require(value.length >= 16) { "AuthToken must be at least 16 characters" }
    }
    
    override fun toString(): String = "*".repeat(value.length) // Hide token in logs
}

@Serializable
@JvmInline
value class Port(val value: Int) {
    init {
        require(value in 1..65535) { "Port must be between 1 and 65535" }
    }
    
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class TimeoutMs(val value: Long) {
    init {
        require(value >= 0) { "Timeout must be non-negative" }
    }
    
    override fun toString(): String = "${value}ms"
}

@Serializable
@JvmInline
value class MaxConnections(val value: Int) {
    init {
        require(value > 0) { "MaxConnections must be positive" }
        require(value <= 10000) { "MaxConnections cannot exceed 10000" }
    }
    
    override fun toString(): String = value.toString()
}

@Serializable
@JvmInline
value class MaxMessageSize(val value: Int) {
    init {
        require(value > 0) { "MaxMessageSize must be positive" }
        require(value <= 100 * 1024 * 1024) { "MaxMessageSize cannot exceed 100MB" }
    }
    
    override fun toString(): String = when {
        value >= 1024 * 1024 -> "${value / (1024 * 1024)}MB"
        value >= 1024 -> "${value / 1024}KB"
        else -> "${value}B"
    }
}

/**
 * Factory functions for creating inline value classes with validation
 */
object ValueFactories {
    fun nodeId(value: String): NodeId = NodeId(value)
    fun networkId(value: String): NetworkId = NetworkId(value)
    fun workflowId(value: String): WorkflowId = WorkflowId(value)
    fun taskId(value: String): TaskId = TaskId(value)
    fun pluginName(value: String): PluginName = PluginName(value)
    fun gossipTopic(value: String): GossipTopic = GossipTopic(value)
    fun authToken(value: String): AuthToken = AuthToken(value)
    fun port(value: Int): Port = Port(value)
    fun timeoutMs(value: Long): TimeoutMs = TimeoutMs(value)
    fun maxConnections(value: Int): MaxConnections = MaxConnections(value)
    fun maxMessageSize(value: Int): MaxMessageSize = MaxMessageSize(value)
} 