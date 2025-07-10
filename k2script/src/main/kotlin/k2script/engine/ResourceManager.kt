@file:OptIn(kotlin.kotlin.ExperimentalStdlibApi::class)
package k2script.engine

class ResourceManager(internal val context: Context) {
    internal val resources = mutableListOf<AutoCloseable>()
    
    fun register(resource: AutoCloseable) {
        resources.add(resource)
    }
    
    fun cleanup() {
        resources.forEach { it.close() }
        resources.clear()
    }
} 