package k2script.engine

class ResourceManager(private val context: Context) {
    private val resources = mutableListOf<AutoCloseable>()
    
    fun register(resource: AutoCloseable) {
        resources.add(resource)
    }
    
    fun cleanup() {
        resources.forEach { it.close() }
        resources.clear()
    }
} 