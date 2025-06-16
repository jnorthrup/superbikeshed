package rtsgame.webgpu

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.compat.*

/**
 * WebGPU-based SpaceGraph renderer for cross-platform RTS visualization
 * Common interface that works across JVM, Native, and WASM targets
 */

// Core WebGPU types (expect/actual per platform)
expect class GPUDevice
expect class GPUBuffer  
expect class GPUTexture
expect class GPURenderPipeline
expect class GPUCommandEncoder
expect class GPUShaderModule

// WebGPU resource handles
@PlatformInline
value class BufferId(val value: Int)

@PlatformInline
value class PipelineId(val value: Int)

@PlatformInline
value class TextureId(val value: Int)

// Shader resource types
typealias VertexData = Join<Vector3D, Join<Float, Int>> // Position + Size + Color
typealias UniformData = Join<Matrix4, Join<Vector3D, Float>> // ViewProjection + CameraPos + Time

data class Matrix4(
    val m00: Float, val m01: Float, val m02: Float, val m03: Float,
    val m10: Float, val m11: Float, val m12: Float, val m13: Float,
    val m20: Float, val m21: Float, val m22: Float, val m23: Float,
    val m30: Float, val m31: Float, val m32: Float, val m33: Float
) {
    companion object {
        fun identity() = Matrix4(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}

/**
 * Cross-platform WebGPU renderer for SpaceGraph visualization
 */
expect class WebGPUSpaceGraph() {
    suspend fun initialize(): Boolean
    fun createVertexBuffer(data: Series<VertexData>): BufferId
    fun createUniformBuffer(data: UniformData): BufferId
    fun createRenderPipeline(vertexShader: String, fragmentShader: String): PipelineId
    fun updateBuffer(bufferId: BufferId, data: ByteArray)
    fun render(renderData: RenderData): RenderResult
    fun dispose()
}

data class RenderData(
    val nodes: Series<SpaceGraphNode>,
    val edges: Series<SpaceGraphEdge>,
    val camera: CameraState,
    val time: Float
)

data class RenderResult(
    val success: Boolean,
    val frameTime: Float,
    val trianglesRendered: Int
)

data class CameraState(
    val position: Vector3D,
    val target: Vector3D,
    val fov: Float = 70f,
    val aspect: Float = 16f/9f,
    val near: Float = 0.1f,
    val far: Float = 1000f
)

/**
 * Common WebGPU SpaceGraph implementation
 */
class CommonWebGPUSpaceGraph {
    private var device: GPUDevice? = null
    private var nodeVertexBuffer: BufferId? = null
    private var edgeVertexBuffer: BufferId? = null
    private var uniformBuffer: BufferId? = null
    private var nodePipeline: PipelineId? = null
    private var edgePipeline: PipelineId? = null
    
    private lateinit var renderer: WebGPUSpaceGraph
    
    suspend fun initialize(): Boolean {
        renderer = WebGPUSpaceGraph()
        return renderer.initialize()
    }
    
    fun renderGameState(gameState: GameState, camera: CameraState): RenderResult {
        val spaceGraphRenderer = RTSSpaceGraphRenderer()
        val spaceGraphData = spaceGraphRenderer.renderGameState(gameState)
        
        // Convert to WebGPU vertex data
        val nodeVertices = spaceGraphData.nodes.α { node ->
            convertNodeToVertex(node)
        }
        
        val edgeVertices = spaceGraphData.edges.α { edge ->
            convertEdgeToVertex(edge, spaceGraphData.nodes)
        }
        
        // Update GPU buffers
        updateVertexBuffers(nodeVertices, edgeVertices)
        updateUniformBuffer(camera)
        
        // Render
        val renderData = RenderData(
            nodes = spaceGraphData.nodes,
            edges = spaceGraphData.edges,
            camera = camera,
            time = gameState.tick.value.toFloat()
        )
        
        return renderer.render(renderData)
    }
    
    private fun convertNodeToVertex(node: SpaceGraphNode): VertexData {
        val color = when (node.data.entityType) {
            EntityType.COMMANDER -> 0xFF0000 // Red
            EntityType.UNIT -> 0x00FF00       // Green  
            EntityType.SCOUT -> 0x0000FF      // Blue
            EntityType.BUILDING -> 0xFFFF00   // Yellow
        }
        
        val size = when (node.data.entityType) {
            EntityType.COMMANDER -> 8f
            EntityType.UNIT -> 5f
            EntityType.SCOUT -> 3f
            EntityType.BUILDING -> 12f
        }
        
        return node.position j (size j color)
    }
    
    private fun convertEdgeToVertex(edge: SpaceGraphEdge, nodes: Series<SpaceGraphNode>): VertexData {
        // Find source and target positions
        val sourceNode = nodes.play.find { it.id == edge.source }
        val targetNode = nodes.play.find { it.id == edge.target }
        
        if (sourceNode == null || targetNode == null) {
            return Vector3D(0.0, 0.0, 0.0) j (1f j 0x808080)
        }
        
        // Use midpoint for edge visualization
        val midpoint = Vector3D(
            (sourceNode.position.x + targetNode.position.x) / 2.0,
            (sourceNode.position.y + targetNode.position.y) / 2.0,
            (sourceNode.position.z + targetNode.position.z) / 2.0
        )
        
        val color = when (edge.connectionType) {
            ConnectionType.ALLY -> 0x00FF00
            ConnectionType.ENEMY -> 0xFF0000
            ConnectionType.NEUTRAL -> 0x808080
            ConnectionType.COMMAND -> 0xFFFF00
        }
        
        return midpoint j (2f j color)
    }
    
    private fun updateVertexBuffers(nodeVertices: Series<VertexData>, edgeVertices: Series<VertexData>) {
        // Convert to byte arrays for GPU upload
        val nodeData = serializeVertexData(nodeVertices)
        val edgeData = serializeVertexData(edgeVertices)
        
        nodeVertexBuffer?.let { bufferId ->
            renderer.updateBuffer(bufferId, nodeData)
        }
        
        edgeVertexBuffer?.let { bufferId ->
            renderer.updateBuffer(bufferId, edgeData)
        }
    }
    
    private fun updateUniformBuffer(camera: CameraState) {
        val viewMatrix = createViewMatrix(camera)
        val projMatrix = createProjectionMatrix(camera)
        val viewProjMatrix = multiplyMatrices(projMatrix, viewMatrix)
        
        val uniformData = viewProjMatrix j (camera.position j 0f)
        
        uniformBuffer?.let { bufferId ->
            renderer.updateBuffer(bufferId, serializeUniformData(uniformData))
        }
    }
    
    private fun serializeVertexData(vertices: Series<VertexData>): ByteArray {
        val data = ByteArray(vertices.play.size * 28) // 3 floats pos + 1 float size + 1 int color = 28 bytes
        var offset = 0
        
        vertices.play.forEach { vertex ->
            val pos = vertex.a
            val sizeColor = vertex.b
            
            // Position (3 floats)
            writeFloat(data, offset, pos.x.toFloat()); offset += 4
            writeFloat(data, offset, pos.y.toFloat()); offset += 4 
            writeFloat(data, offset, pos.z.toFloat()); offset += 4
            
            // Size (1 float)
            writeFloat(data, offset, sizeColor.a); offset += 4
            
            // Color (1 int as 4 bytes)
            writeInt(data, offset, sizeColor.b); offset += 4
            
            // Padding to align to 32 bytes
            offset += 8
        }
        
        return data
    }
    
    private fun serializeUniformData(uniform: UniformData): ByteArray {
        val data = ByteArray(80) // 16 floats matrix + 3 floats pos + 1 float time = 80 bytes
        var offset = 0
        
        val matrix = uniform.a
        val cameraPosTime = uniform.b
        
        // Matrix (16 floats)
        writeFloat(data, offset, matrix.m00); offset += 4
        writeFloat(data, offset, matrix.m01); offset += 4
        writeFloat(data, offset, matrix.m02); offset += 4
        writeFloat(data, offset, matrix.m03); offset += 4
        writeFloat(data, offset, matrix.m10); offset += 4
        writeFloat(data, offset, matrix.m11); offset += 4
        writeFloat(data, offset, matrix.m12); offset += 4
        writeFloat(data, offset, matrix.m13); offset += 4
        writeFloat(data, offset, matrix.m20); offset += 4
        writeFloat(data, offset, matrix.m21); offset += 4
        writeFloat(data, offset, matrix.m22); offset += 4
        writeFloat(data, offset, matrix.m23); offset += 4
        writeFloat(data, offset, matrix.m30); offset += 4
        writeFloat(data, offset, matrix.m31); offset += 4
        writeFloat(data, offset, matrix.m32); offset += 4
        writeFloat(data, offset, matrix.m33); offset += 4
        
        // Camera position (3 floats)
        val cameraPos = cameraPosTime.a
        writeFloat(data, offset, cameraPos.x.toFloat()); offset += 4
        writeFloat(data, offset, cameraPos.y.toFloat()); offset += 4
        writeFloat(data, offset, cameraPos.z.toFloat()); offset += 4
        
        // Time (1 float)
        writeFloat(data, offset, cameraPosTime.b)
        
        return data
    }
    
    private fun writeFloat(data: ByteArray, offset: Int, value: Float) {
        val bits = value.toBits()
        data[offset] = (bits and 0xFF).toByte()
        data[offset + 1] = ((bits shr 8) and 0xFF).toByte()
        data[offset + 2] = ((bits shr 16) and 0xFF).toByte()
        data[offset + 3] = ((bits shr 24) and 0xFF).toByte()
    }
    
    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    
    private fun createViewMatrix(camera: CameraState): Matrix4 {
        // Simplified view matrix calculation
        return Matrix4.identity() // TODO: Implement proper view matrix
    }
    
    private fun createProjectionMatrix(camera: CameraState): Matrix4 {
        // Simplified projection matrix calculation
        return Matrix4.identity() // TODO: Implement proper projection matrix
    }
    
    private fun multiplyMatrices(a: Matrix4, b: Matrix4): Matrix4 {
        // Simplified matrix multiplication
        return Matrix4.identity() // TODO: Implement proper matrix multiplication
    }
    
    fun dispose() {
        renderer.dispose()
    }
}

// Shared shader code (WGSL)
object WebGPUShaders {
    const val VERTEX_SHADER = """
        struct VertexInput {
            @location(0) position: vec3<f32>,
            @location(1) size: f32,
            @location(2) color: u32,
        }
        
        struct VertexOutput {
            @builtin(position) clip_position: vec4<f32>,
            @location(0) color: vec3<f32>,
            @location(1) size: f32,
        }
        
        struct Uniforms {
            view_proj: mat4x4<f32>,
            camera_pos: vec3<f32>,
            time: f32,
        }
        
        @group(0) @binding(0) var<uniform> uniforms: Uniforms;
        
        @vertex
        fn vs_main(vertex: VertexInput) -> VertexOutput {
            var out: VertexOutput;
            out.clip_position = uniforms.view_proj * vec4<f32>(vertex.position, 1.0);
            
            // Unpack color from u32
            let r = f32((vertex.color >> 16u) & 0xFFu) / 255.0;
            let g = f32((vertex.color >> 8u) & 0xFFu) / 255.0;
            let b = f32(vertex.color & 0xFFu) / 255.0;
            out.color = vec3<f32>(r, g, b);
            
            out.size = vertex.size;
            return out;
        }
    """
    
    const val FRAGMENT_SHADER = """
        struct FragmentInput {
            @location(0) color: vec3<f32>,
            @location(1) size: f32,
        }
        
        @fragment
        fn fs_main(input: FragmentInput) -> @location(0) vec4<f32> {
            return vec4<f32>(input.color, 1.0);
        }
    """
}