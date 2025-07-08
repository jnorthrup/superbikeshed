package rtsgame.rendering

import rtsgame.config.UnitType
import kotlinx.serialization.Serializable

/**
 * Model loader for RTS game flow mode
 * Handles loading and caching of 3D models from GLB files
 */
class ModelLoader {
    private val modelCache = mutableMapOf<String, ModelData>()
    
    /**
     * Load a model for a unit type
     */
    suspend fun loadModel(unitType: UnitType): ModelData? {
        val modelPath = unitType.modelPath
        if (modelPath.isEmpty()) {
            println("⚠️ No model path specified for unit type: ${unitType.name}")
            return null
        }
        
        // Check cache first
        modelCache[modelPath]?.let { return it }
        
        // Load from processed_models/glb directory
        val glbPath = "processed_models/glb/$modelPath.glb"
        
        return try {
            val modelData = loadGLBModel(glbPath, unitType.scale)
            modelCache[modelPath] = modelData
            println("✅ Loaded model: $modelPath (${modelData.triangleCount} triangles)")
            modelData
        } catch (e: Exception) {
            println("❌ Failed to load model: $modelPath - ${e.message}")
            null
        }
    }
    
    /**
     * Load GLB model file
     */
    private suspend fun loadGLBModel(path: String, scale: Float): ModelData {
        // In a real implementation, this would use a GLB parser
        // For now, return mock data based on unit type
        return ModelData(
            path = path,
            scale = scale,
            triangleCount = estimateTriangleCount(path),
            vertices = emptyList(), // Would be loaded from GLB
            indices = emptyList(),  // Would be loaded from GLB
            materials = emptyList() // Would be loaded from GLB
        )
    }
    
    /**
     * Estimate triangle count based on model path
     */
    private fun estimateTriangleCount(path: String): Int {
        return when {
            path.contains("commander") -> 500
            path.contains("tank") -> 300
            path.contains("artillery") -> 400
            path.contains("scout") -> 200
            path.contains("fighter") -> 250
            path.contains("submarine") -> 350
            path.contains("resource") -> 100
            else -> 200
        }
    }
    
    /**
     * Get cached model data
     */
    fun getCachedModel(modelPath: String): ModelData? = modelCache[modelPath]
    
    /**
     * Clear model cache
     */
    fun clearCache() {
        modelCache.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            cachedModels = modelCache.size,
            totalTriangles = modelCache.values.sumOf { it.triangleCount }
        )
    }
}

/**
 * Model data structure
 */
@Serializable
data class ModelData(
    val path: String,
    val scale: Float,
    val triangleCount: Int,
    val vertices: List<Float>,
    val indices: List<Int>,
    val materials: List<MaterialData>
)

/**
 * Material data structure
 */
@Serializable
data class MaterialData(
    val name: String,
    val diffuseColor: FloatArray,
    val specularColor: FloatArray,
    val shininess: Float
)

/**
 * Cache statistics
 */
data class CacheStats(
    val cachedModels: Int,
    val totalTriangles: Int
) 