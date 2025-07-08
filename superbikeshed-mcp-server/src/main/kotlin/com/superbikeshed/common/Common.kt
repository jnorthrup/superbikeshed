package com.superbikeshed.common

// Common interfaces and utilities for trikeshed components
interface TrikeshedComponent {
    fun getName(): String
    fun getVersion(): String
}

interface Configurable {
    fun configure(config: Map<String, Any>)
}

interface Lifecycle {
    fun start()
    fun stop()
    fun isRunning(): Boolean
}

data class TrikeshedConfig(
    val name: String,
    val version: String,
    val properties: Map<String, Any> = emptyMap()
) 