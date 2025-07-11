package rtsgame.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * Dense model system for efficient 3D asset management and rendering
 * Integrates with the existing OBJ/GLB models in the models directory
 */

// Model types
typealias ModelId = String
typealias MeshData = ByteArray
typealias TextureData = ByteArray

// Model manifest matching our asset structure
object ModelManifest {
    val units = mapOf(
        "commander" to ModelAsset("commander", "models/commander.obj", scale = 1.0f),
        "tank" to ModelAsset("tank", "models/tank.obj", scale = 0.8f),
        "scout" to ModelAsset("scout", "models/scout.obj", scale = 0.6f),
        "fighter" to ModelAsset("fighter", "models/fighter.obj", scale = 0.7f),
        "artillery" to ModelAsset("artillery", "models/artillery.obj", scale = 0.9f),
        "submarine" to ModelAsset("submarine", "models/submarine.obj", scale = 0.85f)
    )
    
    val buildings = mapOf(
        "landFactory" to ModelAsset("landFactory", "models/landFactory.obj", scale = 2.0f),
        "airFactory" to ModelAsset("airFactory", "models/airFactory.obj", scale = 2.2f),
        "navalFactory" to ModelAsset("navalFactory", "models/navalFactory.obj", scale = 2.5f),
        "massExtractor" to ModelAsset("massExtractor", "models/massExtractor.obj", scale = 1.5f),
        "energyExtractor" to ModelAsset("energyExtractor", "models/energyExtractor.obj", scale = 1.5f)
    )
    
    val resources = mapOf(
        "resource" to ModelAsset("resource", "models/resource.obj", scale = 0.5f)
    )
    
    val all = units + buildings + resources
}

data class ModelAsset(
    val id: ModelId,
    val path: String,
    val scale: Float = 1.0f,
    val optimizedPath: String? = path.replace("models/", "processed_models/glb_optimized/").replace(".obj", ".glb")
)

// Vertex data structures
data class Vertex(
    val position: Vec3,
    val normal: Vec3,
    val uv: Pair<Float, Float>
)

data class Mesh(
    val vertices: List<Vertex>,
    val indices: List<Int>,
    val material: Material = Material.default
)

data class Material(
    val diffuse: Vec3 = Vec3(0.8f, 0.8f, 0.8f),
    val specular: Vec3 = Vec3(1.0f, 1.0f, 1.0f),
    val ambient: Vec3 = Vec3(0.2f, 0.2f, 0.2f),
    val shininess: Float = 32.0f
) {
    companion object {
        val default = Material()
    }
}

// Model instance for rendering
data class ModelInstance(
    val modelId: ModelId,
    val transform: Mat4,
    val color: Vec3 = Vec3(1f, 1f, 1f),
    val teamColor: Vec3? = null
)

// 4x4 Matrix for transformations
data class Mat4(val m: FloatArray = FloatArray(16)) {
    companion object {
        fun identity() = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        ))
        
        fun translation(pos: Vec3) = Mat4(floatArrayOf(
            1f, 0f, 0f, pos.first,
            0f, 1f, 0f, pos.second,
            0f, 0f, 1f, pos.third,
            0f, 0f, 0f, 1f
        ))
        
        fun scale(s: Float) = Mat4(floatArrayOf(
            s, 0f, 0f, 0f,
            0f, s, 0f, 0f,
            0f, 0f, s, 0f,
            0f, 0f, 0f, 1f
        ))
        
        fun rotation(angle: Float, axis: Vec3): Mat4 {
            val c = cos(angle)
            val s = sin(angle)
            val t = 1 - c
            val (x, y, z) = axis.normalize()
            
            return Mat4(floatArrayOf(
                t*x*x + c,    t*x*y - s*z,  t*x*z + s*y,  0f,
                t*x*y + s*z,  t*y*y + c,    t*y*z - s*x,  0f,
                t*x*z - s*y,  t*y*z + s*x,  t*z*z + c,    0f,
                0f,           0f,           0f,           1f
            ))
        }
    }
    
    operator fun times(other: Mat4): Mat4 {
        val result = FloatArray(16)
        for (i in 0..3) {
            for (j in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += m[i * 4 + k] * other.m[k * 4 + j]
                }
                result[i * 4 + j] = sum
            }
        }
        return Mat4(result)
    }
}

// Efficient model loader
object ModelLoader {
    private val cache = mutableMapOf<ModelId, Mesh>()
    
    suspend fun load(asset: ModelAsset): Mesh = coroutineScope {
        cache.getOrPut(asset.id) {
            // In real implementation, would load from file system
            // For now, generate procedural mesh based on model type
            when {
                asset.id.contains("commander") -> generateCommanderMesh()
                asset.id.contains("tank") -> generateTankMesh()
                asset.id.contains("factory") -> generateFactoryMesh()
                asset.id.contains("extractor") -> generateExtractorMesh()
                else -> generateDefaultMesh()
            }
        }
    }
    
    private fun generateCommanderMesh(): Mesh {
        // Simplified commander mesh - tall humanoid shape
        val vertices = mutableListOf<Vertex>()
        val indices = mutableListOf<Int>()
        
        // Create a simple box with proportions
        val positions = listOf(
            // Body
            Vec3(-0.3f, 0f, -0.3f), Vec3(0.3f, 0f, -0.3f),
            Vec3(0.3f, 1.2f, -0.3f), Vec3(-0.3f, 1.2f, -0.3f),
            Vec3(-0.3f, 0f, 0.3f), Vec3(0.3f, 0f, 0.3f),
            Vec3(0.3f, 1.2f, 0.3f), Vec3(-0.3f, 1.2f, 0.3f),
            // Head
            Vec3(-0.2f, 1.2f, -0.2f), Vec3(0.2f, 1.2f, -0.2f),
            Vec3(0.2f, 1.6f, -0.2f), Vec3(-0.2f, 1.6f, -0.2f),
            Vec3(-0.2f, 1.2f, 0.2f), Vec3(0.2f, 1.2f, 0.2f),
            Vec3(0.2f, 1.6f, 0.2f), Vec3(-0.2f, 1.6f, 0.2f)
        )
        
        positions.forEach { pos ->
            vertices.add(Vertex(pos, Vec3(0f, 1f, 0f), 0f to 0f))
        }
        
        // Box indices
        val boxIndices = listOf(
            0,1,2, 0,2,3, // front
            1,5,6, 1,6,2, // right
            5,4,7, 5,7,6, // back
            4,0,3, 4,3,7, // left
            3,2,6, 3,6,7, // top
            4,5,1, 4,1,0  // bottom
        )
        
        indices.addAll(boxIndices)
        indices.addAll(boxIndices.map { it + 8 }) // Head
        
        return Mesh(vertices, indices)
    }
    
    private fun generateTankMesh(): Mesh {
        // Simple tank - low box with turret
        val vertices = mutableListOf<Vertex>()
        val indices = mutableListOf<Int>()
        
        // Tank body
        val bodyHeight = 0.3f
        val bodyLength = 0.8f
        val bodyWidth = 0.5f
        
        val positions = listOf(
            Vec3(-bodyLength/2, 0f, -bodyWidth/2),
            Vec3(bodyLength/2, 0f, -bodyWidth/2),
            Vec3(bodyLength/2, bodyHeight, -bodyWidth/2),
            Vec3(-bodyLength/2, bodyHeight, -bodyWidth/2),
            Vec3(-bodyLength/2, 0f, bodyWidth/2),
            Vec3(bodyLength/2, 0f, bodyWidth/2),
            Vec3(bodyLength/2, bodyHeight, bodyWidth/2),
            Vec3(-bodyLength/2, bodyHeight, bodyWidth/2)
        )
        
        positions.forEach { pos ->
            vertices.add(Vertex(pos, Vec3(0f, 1f, 0f), 0f to 0f))
        }
        
        val boxIndices = listOf(
            0,1,2, 0,2,3,
            1,5,6, 1,6,2,
            5,4,7, 5,7,6,
            4,0,3, 4,3,7,
            3,2,6, 3,6,7,
            4,5,1, 4,1,0
        )
        
        indices.addAll(boxIndices)
        
        return Mesh(vertices, indices)
    }
    
    private fun generateFactoryMesh(): Mesh {
        // Large building
        val size = 1.0f
        val height = 0.8f
        
        val vertices = listOf(
            Vertex(Vec3(-size, 0f, -size), Vec3(0f, 1f, 0f), 0f to 0f),
            Vertex(Vec3(size, 0f, -size), Vec3(0f, 1f, 0f), 1f to 0f),
            Vertex(Vec3(size, height, -size), Vec3(0f, 1f, 0f), 1f to 1f),
            Vertex(Vec3(-size, height, -size), Vec3(0f, 1f, 0f), 0f to 1f),
            Vertex(Vec3(-size, 0f, size), Vec3(0f, 1f, 0f), 0f to 0f),
            Vertex(Vec3(size, 0f, size), Vec3(0f, 1f, 0f), 1f to 0f),
            Vertex(Vec3(size, height, size), Vec3(0f, 1f, 0f), 1f to 1f),
            Vertex(Vec3(-size, height, size), Vec3(0f, 1f, 0f), 0f to 1f)
        )
        
        val indices = listOf(
            0,1,2, 0,2,3,
            1,5,6, 1,6,2,
            5,4,7, 5,7,6,
            4,0,3, 4,3,7,
            3,2,6, 3,6,7,
            4,5,1, 4,1,0
        )
        
        return Mesh(vertices, indices)
    }
    
    private fun generateExtractorMesh(): Mesh {
        // Cylindrical structure
        val radius = 0.4f
        val height = 0.6f
        val segments = 8
        
        val vertices = mutableListOf<Vertex>()
        val indices = mutableListOf<Int>()
        
        // Generate cylinder vertices
        for (i in 0 until segments) {
            val angle = (i.toFloat() / segments) * PI * 2
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            
            vertices.add(Vertex(Vec3(x, 0f, z), Vec3(x, 0f, z).normalize(), i.toFloat() / segments to 0f))
            vertices.add(Vertex(Vec3(x, height, z), Vec3(x, 0f, z).normalize(), i.toFloat() / segments to 1f))
        }
        
        // Generate cylinder indices
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            indices.addAll(listOf(
                i * 2, next * 2, next * 2 + 1,
                i * 2, next * 2 + 1, i * 2 + 1
            ))
        }
        
        return Mesh(vertices, indices)
    }
    
    private fun generateDefaultMesh(): Mesh {
        // Simple cube
        val vertices = listOf(
            Vertex(Vec3(-0.5f, -0.5f, -0.5f), Vec3(0f, 0f, -1f), 0f to 0f),
            Vertex(Vec3(0.5f, -0.5f, -0.5f), Vec3(0f, 0f, -1f), 1f to 0f),
            Vertex(Vec3(0.5f, 0.5f, -0.5f), Vec3(0f, 0f, -1f), 1f to 1f),
            Vertex(Vec3(-0.5f, 0.5f, -0.5f), Vec3(0f, 0f, -1f), 0f to 1f)
        )
        
        val indices = listOf(0, 1, 2, 0, 2, 3)
        
        return Mesh(vertices, indices)
    }
}

// Dense renderer that converts world state to model instances
object DenseRenderer {
    // Team colors
    private val teamColors = listOf(
        Vec3(0.2f, 0.5f, 1.0f), // Blue
        Vec3(1.0f, 0.2f, 0.2f), // Red
        Vec3(0.2f, 1.0f, 0.2f), // Green
        Vec3(1.0f, 1.0f, 0.2f), // Yellow
        Vec3(1.0f, 0.5f, 0.2f), // Orange
        Vec3(0.8f, 0.2f, 1.0f), // Purple
        Vec3(0.2f, 1.0f, 1.0f), // Cyan
        Vec3(1.0f, 0.2f, 1.0f)  // Magenta
    )
    
    // Convert world entities to renderable instances
    fun worldToInstances(world: World): List<ModelInstance> =
        world.mapNotNull { (id, entity) ->
            val type = entity["type"] as? String ?: return@mapNotNull null
            val pos = entity.get<Pos>("pos")?.vec ?: return@mapNotNull null
            val team = entity.get<Team>("team")?.id
            
            val modelId = when (type) {
                "commander" -> "commander"
                "tank" -> "tank"
                "scout" -> "scout"
                "fighter" -> "fighter"
                "artillery" -> "artillery"
                "submarine" -> "submarine"
                "landFactory", "factory" -> "landFactory"
                "airFactory" -> "airFactory"
                "navalFactory" -> "navalFactory"
                "massExtractor" -> "massExtractor"
                "energyExtractor" -> "energyExtractor"
                "resource" -> "resource"
                else -> return@mapNotNull null
            }
            
            val asset = ModelManifest.all[modelId] ?: return@mapNotNull null
            
            // Calculate transform
            val translation = Mat4.translation(pos)
            val scale = Mat4.scale(asset.scale)
            val rotation = Mat4.rotation(
                angle = System.currentTimeMillis() * 0.001f + id * 0.5f,
                axis = Vec3(0f, 1f, 0f)
            )
            
            val transform = translation * rotation * scale
            
            // Determine color
            val teamColor = team?.let { teamColors.getOrNull(it) }
            val healthRatio = entity.get<HP>("hp")?.let { 
                it.value.first / it.value.second 
            } ?: 1f
            
            val color = Vec3(healthRatio, healthRatio, healthRatio)
            
            ModelInstance(modelId, transform, color, teamColor)
        }
    
    // Batch instances by model for efficient rendering
    fun batchInstances(instances: List<ModelInstance>): Map<ModelId, List<ModelInstance>> =
        instances.groupBy { it.modelId }
    
    // Generate instance buffer for GPU instancing
    fun generateInstanceBuffer(instances: List<ModelInstance>): FloatArray {
        val floatsPerInstance = 16 + 3 + 3 // transform + color + teamColor
        val buffer = FloatArray(instances.size * floatsPerInstance)
        
        instances.forEachIndexed { idx, instance ->
            val offset = idx * floatsPerInstance
            
            // Copy transform matrix
            instance.transform.m.copyInto(buffer, offset)
            
            // Copy color
            buffer[offset + 16] = instance.color.first
            buffer[offset + 17] = instance.color.second
            buffer[offset + 18] = instance.color.third
            
            // Copy team color
            instance.teamColor?.let {
                buffer[offset + 19] = it.first
                buffer[offset + 20] = it.second
                buffer[offset + 21] = it.third
            } ?: run {
                buffer[offset + 19] = instance.color.first
                buffer[offset + 20] = instance.color.second
                buffer[offset + 21] = instance.color.third
            }
        }
        
        return buffer
    }
}

// Level of detail system
object LODSystem {
    enum class LODLevel(val distance: Float, val reduction: Float) {
        HIGH(0f, 1.0f),
        MEDIUM(50f, 0.5f),
        LOW(100f, 0.25f),
        BILLBOARD(200f, 0.0f)
    }
    
    fun selectLOD(distance: Float): LODLevel =
        LODLevel.values().lastOrNull { distance >= it.distance } ?: LODLevel.HIGH
    
    fun reduceMesh(mesh: Mesh, level: LODLevel): Mesh =
        when (level) {
            LODLevel.HIGH -> mesh
            LODLevel.MEDIUM -> reduceMeshByHalf(mesh)
            LODLevel.LOW -> reduceMeshByQuarter(mesh)
            LODLevel.BILLBOARD -> generateBillboard(mesh)
        }
    
    private fun reduceMeshByHalf(mesh: Mesh): Mesh {
        // Simple reduction - take every other vertex
        val vertices = mesh.vertices.filterIndexed { idx, _ -> idx % 2 == 0 }
        val indices = mesh.indices.mapNotNull { idx ->
            val newIdx = idx / 2
            if (newIdx < vertices.size) newIdx else null
        }
        return Mesh(vertices, indices, mesh.material)
    }
    
    private fun reduceMeshByQuarter(mesh: Mesh): Mesh {
        val vertices = mesh.vertices.filterIndexed { idx, _ -> idx % 4 == 0 }
        val indices = mesh.indices.mapNotNull { idx ->
            val newIdx = idx / 4
            if (newIdx < vertices.size) newIdx else null
        }
        return Mesh(vertices, indices, mesh.material)
    }
    
    private fun generateBillboard(mesh: Mesh): Mesh {
        // Single quad facing camera
        val vertices = listOf(
            Vertex(Vec3(-0.5f, 0f, 0f), Vec3(0f, 0f, 1f), 0f to 0f),
            Vertex(Vec3(0.5f, 0f, 0f), Vec3(0f, 0f, 1f), 1f to 0f),
            Vertex(Vec3(0.5f, 1f, 0f), Vec3(0f, 0f, 1f), 1f to 1f),
            Vertex(Vec3(-0.5f, 1f, 0f), Vec3(0f, 0f, 1f), 0f to 1f)
        )
        val indices = listOf(0, 1, 2, 0, 2, 3)
        return Mesh(vertices, indices, mesh.material)
    }
}

// Integration with game loop
suspend fun DenseRTSGame.renderModels(cameraPos: Vec3) {
    world.collect { w ->
        // Convert entities to model instances
        val instances = DenseRenderer.worldToInstances(w)
        
        // Apply LOD based on camera distance
        val lodInstances = instances.map { instance ->
            val distance = instance.transform.let { transform ->
                val pos = Vec3(transform.m[3], transform.m[7], transform.m[11])
                cameraPos.dist(pos)
            }
            val lod = LODSystem.selectLOD(distance)
            instance to lod
        }
        
        // Batch by model and LOD
        val batches = lodInstances.groupBy { (instance, lod) ->
            "${instance.modelId}_${lod.name}"
        }
        
        // Generate instance buffers for GPU
        batches.forEach { (key, instances) ->
            val buffer = DenseRenderer.generateInstanceBuffer(instances.map { it.first })
            // In real implementation, upload to GPU
            println("Batch $key: ${instances.size} instances")
        }
    }
}