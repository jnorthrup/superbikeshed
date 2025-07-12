import kotlin.math.*
package rtsgame.webgpu
import kotlinx.datetime.*
import kotlin.time.*

import borg.trikeshed.lib.*

/**
 * Common WebGPU type declarations
 */

// Core WebGPU types
expect class GPUDevice {
    fun createBuffer(descriptor: Any): GPUBuffer
    fun createRenderPipeline(descriptor: Any): GPURenderPipeline
    fun createCommandEncoder(): GPUCommandEncoder
    fun createShaderModule(descriptor: Any): GPUShaderModule
}

expect class GPUBuffer {
    fun destroy()
}

expect class GPUTexture {
    fun destroy()
}

expect class GPURenderPipeline

expect class GPUCommandEncoder {
    fun beginRenderPass(descriptor: Any): GPURenderPassEncoder
    fun finish(): GPUCommandBuffer
    fun copyBufferToBuffer(source: GPUBuffer, sourceOffset: Int, destination: GPUBuffer, destinationOffset: Int, size: Int)
}

expect class GPUShaderModule

expect class GPURenderPassEncoder

expect class GPUCommandBuffer

// Resource handles
value class BufferId(val value: Int)

value class PipelineId(val value: Int)

value class TextureId(val value: Int)

// Shader resource types
typealias VertexData = Join<Vector3D, Float j Int>
typealias UniformData = Join<Matrix4, Vector3D j Float>

// Matrix type for uniforms
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