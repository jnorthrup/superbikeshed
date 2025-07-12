import kotlin.math.*
package rtsgame.webgpu
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*
import rtsgame.core.*
import rtsgame.spacegraph.*
import rtsgame.compat.*
import rtsgame.game.*

/**
 * WebGPU-based SpaceGraph renderer for cross-platform RTS visualization
 * Common interface that works across JVM, Native, and WASM targets
 */

// CCEK Pattern for WebGPU Context
typealias WebGPUContext = Join<Join<Context, Configuration>, Join<Environment, Knowledge>>

data class Context(
    val device: GPUDevice,
    val buffers: Indexed<GPUBuffer>,
    val pipelines: Indexed<GPURenderPipeline>
)

data class Configuration(
    val vertexShader: String,
    val fragmentShader: String,
    val vertexFormat: VertexFormat
)

data class Environment(
    val canvas: Any, // Platform-specific canvas type
    val adapter: Any // Platform-specific GPU adapter
)

data class Knowledge(
    val capabilities: Indexed<String>,
    val limits: Map<String, Int>
)

data class VertexFormat(
    val position: Int, // Offset in bytes
    val size: Int,     // Offset in bytes
    val color: Int     // Offset in bytes
)

// Core WebGPU types (expect/actual per platform)
expect class GPUDevice {
    actual fun createBuffer(descriptor: Any): GPUBuffer
    actual fun createRenderPipeline(descriptor: Any): GPURenderPipeline
    actual fun createCommandEncoder(): GPUCommandEncoder
    actual fun createShaderModule(descriptor: Any): GPUShaderModule
}

expect class GPUBuffer {
    actual fun destroy()
}

expect class GPUTexture {
    actual fun destroy()
}

expect class GPURenderPipeline

expect class GPUCommandEncoder {
    actual fun beginRenderPass(descriptor: Any): GPURenderPassEncoder
    actual fun finish(): GPUCommandBuffer
    actual fun copyBufferToBuffer(source: GPUBuffer, sourceOffset: Int, destination: GPUBuffer, destinationOffset: Int, size: Int)
}

expect class GPUShaderModule

expect class GPURenderPassEncoder

expect class GPUCommandBuffer

// WebGPU resource handles
value class BufferId(val value: Int)

value class PipelineId(val value: Int)

value class TextureId(val value: Int)

// Shader resource types
typealias VertexData = Join<Vector3D, Float j Int> // Position + Size + Color
typealias UniformData = Join<Matrix4, Vector3D j Float> // ViewProjection + CameraPos + Time

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
    actual suspend fun initialize(): Boolean
    actual fun createVertexBuffer(data: Indexed<VertexData>): BufferId
    actual fun createUniformBuffer(data: UniformData): BufferId
    actual fun createRenderPipeline(vertexShader: String, fragmentShader: String): PipelineId
    actual fun updateBuffer(bufferId: BufferId, data: ByteArray)
    actual fun render(renderData: RenderData): RenderResult
    actual fun dispose()
}

data class RenderData(
    val nodes: Indexed<SpaceGraphNode>,
    val edges: Indexed<SpaceGraphEdge>,
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
    internal var context: WebGPUContext? = null
    internal var nodeVertexBuffer: BufferId? = null
    internal var edgeVertexBuffer: BufferId? = null
    internal var uniformBuffer: BufferId? = null
    internal var nodePipeline: PipelineId? = null
    internal var edgePipeline: PipelineId? = null
    
    internal lateinit var renderer: WebGPUSpaceGraph
    
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
    
    internal fun convertNodeToVertex(node: SpaceGraphNode): VertexData {
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
    
    internal fun convertEdgeToVertex(edge: SpaceGraphEdge, nodes: Indexed<SpaceGraphNode>): VertexData {
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
    
    internal fun updateVertexBuffers(nodeVertices: Indexed<VertexData>, edgeVertices: Indexed<VertexData>) {
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
    
    internal fun updateUniformBuffer(camera: CameraState) {
        val viewMatrix = createViewMatrix(camera)
        val projMatrix = createProjectionMatrix(camera)
        val viewProjMatrix = multiplyMatrices(projMatrix, viewMatrix)
        
        val uniformData = viewProjMatrix j (camera.position j 0f)
        
        uniformBuffer?.let { bufferId ->
            renderer.updateBuffer(bufferId, serializeUniformData(uniformData))
        }
    }
    
    internal fun serializeVertexData(vertices: Indexed<VertexData>): ByteArray {
        val data = ByteArray(vertices.play.size * 28) // 3 floats pos + 1 float size + 1 int color = 28 bytes
        var offset = 0
        
        vertices.play.forEach { vertex ->
            val pos = vertex.component1()
            val sizeColor = vertex.component2()
            
            // Position (3 floats)
            writeFloat(data, offset, pos.x.toFloat()); offset += 4
            writeFloat(data, offset, pos.y.toFloat()); offset += 4 
            writeFloat(data, offset, pos.z.toFloat()); offset += 4
            
            // Size (1 float)
            writeFloat(data, offset, sizeColor.component1()); offset += 4
            
            // Color (1 int as 4 bytes)
            writeInt(data, offset, sizeColor.component2()); offset += 4
            
            // Padding to align to 32 bytes
            offset += 8
        }
        
        return data
    }
    
    internal fun serializeUniformData(uniform: UniformData): ByteArray {
        val data = ByteArray(80) // 16 floats matrix + 3 floats pos + 1 float time = 80 bytes
        var offset = 0
        
        val matrix = uniform.component1()
        val cameraPosTime = uniform.component2()
        
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
        writeFloat(data, offset, cameraPosTime.component1().x.toFloat()); offset += 4
        writeFloat(data, offset, cameraPosTime.component1().y.toFloat()); offset += 4
        writeFloat(data, offset, cameraPosTime.component1().z.toFloat()); offset += 4
        
        // Time (1 float)
        writeFloat(data, offset, cameraPosTime.component2())
        
        return data
    }
    
    internal fun writeFloat(data: ByteArray, offset: Int, value: Float) {
        val bits = value.toBits()
        data[offset] = (bits and 0xFF).toByte()
        data[offset + 1] = ((bits shr 8) and 0xFF).toByte()
        data[offset + 2] = ((bits shr 16) and 0xFF).toByte()
        data[offset + 3] = ((bits shr 24) and 0xFF).toByte()
    }
    
    internal fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
    
    internal fun createViewMatrix(camera: CameraState): Matrix4 {
        val eye = camera.position
        val target = camera.target
        val up = Vector3D(0.0, 1.0, 0.0)
        
        val f = (target - eye).normalize()
        val s = (f cross up).normalize()
        val u = s cross f
        
        return Matrix4(
            s.x.toFloat(), s.y.toFloat(), s.z.toFloat(), 0f,
            u.x.toFloat(), u.y.toFloat(), u.z.toFloat(), 0f,
            -f.x.toFloat(), -f.y.toFloat(), -f.z.toFloat(), 0f,
            0f, 0f, 0f, 1f
        )
    }
    
    internal fun createProjectionMatrix(camera: CameraState): Matrix4 {
        val f = 1f / kotlin.math.tan(camera.fov * kotlin.math.PI.toFloat() / 360f)
        val aspect = camera.aspect
        val near = camera.near
        val far = camera.far
        
        val range = near - far
        
        return Matrix4(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (near + far) / range, -1f,
            0f, 0f, (2f * near * far) / range, 0f
        )
    }
    
    internal fun multiplyMatrices(a: Matrix4, b: Matrix4): Matrix4 {
        return Matrix4(
            a.m00 * b.m00 + a.m01 * b.m10 + a.m02 * b.m20 + a.m03 * b.m30,
            a.m00 * b.m01 + a.m01 * b.m11 + a.m02 * b.m21 + a.m03 * b.m31,
            a.m00 * b.m02 + a.m01 * b.m12 + a.m02 * b.m22 + a.m03 * b.m32,
            a.m00 * b.m03 + a.m01 * b.m13 + a.m02 * b.m23 + a.m03 * b.m33,
            
            a.m10 * b.m00 + a.m11 * b.m10 + a.m12 * b.m20 + a.m13 * b.m30,
            a.m10 * b.m01 + a.m11 * b.m11 + a.m12 * b.m21 + a.m13 * b.m31,
            a.m10 * b.m02 + a.m11 * b.m12 + a.m12 * b.m22 + a.m13 * b.m32,
            a.m10 * b.m03 + a.m11 * b.m13 + a.m12 * b.m23 + a.m13 * b.m33,
            
            a.m20 * b.m00 + a.m21 * b.m10 + a.m22 * b.m20 + a.m23 * b.m30,
            a.m20 * b.m01 + a.m21 * b.m11 + a.m22 * b.m21 + a.m23 * b.m31,
            a.m20 * b.m02 + a.m21 * b.m12 + a.m22 * b.m22 + a.m23 * b.m32,
            a.m20 * b.m03 + a.m21 * b.m13 + a.m22 * b.m23 + a.m23 * b.m33,
            
            a.m30 * b.m00 + a.m31 * b.m10 + a.m32 * b.m20 + a.m33 * b.m30,
            a.m30 * b.m01 + a.m31 * b.m11 + a.m32 * b.m21 + a.m33 * b.m31,
            a.m30 * b.m02 + a.m31 * b.m12 + a.m32 * b.m22 + a.m33 * b.m32,
            a.m30 * b.m03 + a.m31 * b.m13 + a.m32 * b.m23 + a.m33 * b.m33
        )
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