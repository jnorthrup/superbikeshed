package rtsgame.webgpu

import borg.trikeshed.lib.*
import rtsgame.core.*
import kotlin.js.*
import kotlinx.browser.*
import kotlinx.browser.wasm.*
import kotlinx.js.*
import org.w3c.dom.*

/**
 * WASM platform implementation using JS WebGPU externals
 * Follows CCEK pattern for context management
 */

// Actual implementations of WebGPU types
actual external class GPUDevice {
    actual fun createBuffer(descriptor: Any): GPUBuffer
    actual fun createRenderPipeline(descriptor: Any): GPURenderPipeline
    actual fun createCommandEncoder(): GPUCommandEncoder
    actual fun createShaderModule(descriptor: Any): GPUShaderModule
}

actual external class GPUBuffer {
    actual fun destroy()
}

actual external class GPUTexture {
    actual fun destroy()
}

actual external class GPURenderPipeline

actual external class GPUCommandEncoder {
    actual fun beginRenderPass(descriptor: Any): GPURenderPassEncoder
    actual fun finish(): GPUCommandBuffer
    actual fun copyBufferToBuffer(source: GPUBuffer, sourceOffset: Int, destination: GPUBuffer, destinationOffset: Int, size: Int)
}

actual external class GPUShaderModule

actual external class GPURenderPassEncoder

actual external class GPUCommandBuffer

// CCEK context for WebGPU WASM implementation
typealias WebGPUContext = Join<Join<GPUDevice, Indexed<GPUBuffer>>, Join<Indexed<GPURenderPipeline>, Indexed<GPUTexture>>>

/**
 * WASM-specific WebGPU implementation using browser WebGPU API
 */
actual class WebGPUSpaceGraph actual constructor() {
    private var initialized = false
    private var context: WebGPUContext? = null
    private var nextBufferId = 0
    private var nextPipelineId = 0
    
    actual suspend fun initialize(): Boolean {
        if (initialized) return true
        
        try {
            // Get WebGPU adapter and device
            val adapter = js("await navigator.gpu.requestAdapter()").unsafeCast<Any>()
            val device = js("await adapter.requestDevice()").unsafeCast<GPUDevice>()
            
            // Create context with CCEK pattern
            val gpuContext = Context(
                device = device,
                buffers = Indexed.empty(),
                pipelines = Indexed.empty()
            )
            
            val config = Configuration(
                vertexShader = DEFAULT_VERTEX_SHADER,
                fragmentShader = DEFAULT_FRAGMENT_SHADER,
                vertexFormat = VertexFormat(
                    position = 0,
                    size = 12,
                    color = 16
                )
            )
            
            val env = Environment(
                canvas = js("document.createElement('canvas')"),
                adapter = adapter
            )
            
            val knowledge = Knowledge(
                capabilities = Indexed.of("webgpu"),
                limits = mapOf(
                    "maxBufferSize" to 1024 * 1024 * 1024,
                    "maxVertexAttributes" to 16
                )
            )
            
            context = gpuContext j config j (env j knowledge)
            
            initialized = true
            return true
        } catch (e: dynamic) {
            console.error("Failed to initialize WebGPU:", e)
            return false
        }
    }
    
    actual fun createVertexBuffer(data: Indexed<VertexData>): BufferId {
        val ctx = context ?: throw IllegalStateException("WebGPU not initialized")
        val device = ctx.a.a.device
        
        val buffer = device.createBuffer(js("""
            {
                size: ${data.play.size * 28},
                usage: 0x0008 | 0x0010, // VERTEX | COPY_DST
                mappedAtCreation: true
            }
        """))
        
        // Copy data to buffer
        val arrayBuffer = js("buffer.getMappedRange()").unsafeCast<ArrayBuffer>()
        val view = Int8Array(arrayBuffer)
        val dataBytes = serializeVertexData(data)
        view.set(dataBytes)
        js("buffer.unmap()")
        
        // Add to context
        val newBuffers = ctx.a.a.buffers.α { it } j buffer
        context = Context(
            device = device,
            buffers = newBuffers,
            pipelines = ctx.a.a.pipelines
        ) j ctx.a.b j ctx.b
        
        return BufferId(newBuffers.`play`.size - 1)
    }
    
    actual fun createUniformBuffer(data: UniformData): BufferId {
        val ctx = context ?: throw IllegalStateException("WebGPU not initialized")
        val device = ctx.a.a.device
        
        val buffer = device.createBuffer(js("""
            {
                size: 80,
                usage: 0x0008 | 0x0010, // UNIFORM | COPY_DST
                mappedAtCreation: true
            }
        """))
        
        // Copy data to buffer
        val arrayBuffer = js("buffer.getMappedRange()").unsafeCast<ArrayBuffer>()
        val view = Int8Array(arrayBuffer)
        val dataBytes = serializeUniformData(data)
        view.set(dataBytes)
        js("buffer.unmap()")
        
        // Add to context
        val newBuffers = ctx.a.a.buffers.α { it } j buffer
        context = Context(
            device = device,
            buffers = newBuffers,
            pipelines = ctx.a.a.pipelines
        ) j ctx.a.b j ctx.b
        
        return BufferId(newBuffers.`play`.size - 1)
    }
    
    actual fun createRenderPipeline(vertexShader: String, fragmentShader: String): PipelineId {
        val ctx = context ?: throw IllegalStateException("WebGPU not initialized")
        val device = ctx.a.a.device
        
        val pipeline = device.createRenderPipeline(js("""
            {
                vertex: {
                    module: ${device.createShaderModule(js("""
                        {
                            code: vertexShader
                        }
                    """))},
                    entryPoint: 'main',
                    buffers: [{
                        arrayStride: 28,
                        attributes: [{
                            format: 'float32x3',
                            offset: 0,
                            shaderLocation: 0
                        }, {
                            format: 'float32',
                            offset: 12,
                            shaderLocation: 1
                        }, {
                            format: 'uint32',
                            offset: 16,
                            shaderLocation: 2
                        }]
                    }]
                },
                fragment: {
                    module: ${device.createShaderModule(js("""
                        {
                            code: fragmentShader
                        }
                    """))},
                    entryPoint: 'main',
                    targets: [{
                        format: 'bgra8unorm'
                    }]
                },
                primitive: {
                    topology: 'triangle-list'
                }
            }
        """))
        
        // Add to context
        val newPipelines = ctx.a.a.pipelines.α { it } j pipeline
        context = Context(
            device = device,
            buffers = ctx.a.a.buffers,
            pipelines = newPipelines
        ) j ctx.a.b j ctx.b
        
        return PipelineId(newPipelines.`play`.size - 1)
    }
    
    actual fun updateBuffer(bufferId: BufferId, data: ByteArray) {
        val ctx = context ?: throw IllegalStateException("WebGPU not initialized")
        val device = ctx.a.a.device
        val buffer = ctx.a.a.buffers.`play`[bufferId.value]
        
        val stagingBuffer = device.createBuffer(js("""
            {
                size: ${data.size},
                usage: 0x0001, // COPY_SRC
                mappedAtCreation: true
            }
        """))
        
        // Copy data to staging buffer
        val arrayBuffer = js("stagingBuffer.getMappedRange()").unsafeCast<ArrayBuffer>()
        val view = Int8Array(arrayBuffer)
        view.set(data)
        js("stagingBuffer.unmap()")
        
        // Copy to destination buffer
        val encoder = device.createCommandEncoder()
        encoder.copyBufferToBuffer(stagingBuffer, 0, buffer, 0, data.size)
        val commandBuffer = encoder.finish()
        js("device.queue.submit([commandBuffer])")
        
        // Clean up staging buffer
        stagingBuffer.destroy()
    }
    
    actual fun render(renderData: RenderData): RenderResult {
        val ctx = context ?: throw IllegalStateException("WebGPU not initialized")
        val device = ctx.a.a.device
        val canvas = ctx.b.a.canvas as HTMLCanvasElement
        
        // Get current texture
        val texture = js("device.createTexture({size: [canvas.width, canvas.height], format: 'bgra8unorm', usage: 0x0002 | 0x0004})").unsafeCast<GPUTexture>()
        
        // Create render pass
        val encoder = device.createCommandEncoder()
        val renderPass = encoder.beginRenderPass(js("""
            {
                colorAttachments: [{
                    view: ${texture.createView()},
                    clearValue: { r: 0.0, g: 0.0, b: 0.0, a: 1.0 },
                    loadOp: 'clear',
                    storeOp: 'store'
                }]
            }
        """))
        
        // Set pipeline and draw
        renderPass.setPipeline(ctx.a.a.pipelines.`play`[0])
        renderPass.setVertexBuffer(0, ctx.a.a.buffers.`play`[0])
        renderPass.draw(renderData.nodes.play.size * 3, 1, 0, 0)
        
        // End render pass and submit
        renderPass.end()
        val commandBuffer = encoder.finish()
        js("device.queue.submit([commandBuffer])")
        
        // Present to canvas
        val context = canvas.getContext("webgpu")
        js("context.configure({device: device, format: 'bgra8unorm'})")
        js("context.getCurrentTexture().present()")
        
        return RenderResult(
            success = true,
            frameTime = 0f, // TODO: Implement frame timing
            trianglesRendered = renderData.nodes.play.size
        )
    }
    
    actual fun dispose() {
        val ctx = context ?: return
        
        // Destroy all buffers
        ctx.a.a.buffers.`play`.forEach { buffer ->
            buffer.destroy()
        }
        
        context = null
        initialized = false
    }
    
    companion object {
        private const val DEFAULT_VERTEX_SHADER = """
            struct VertexOutput {
                @builtin(position) position: vec4f,
                @location(0) color: vec4f
            }
            
            @vertex
            fn main(@location(0) position: vec3f,
                   @location(1) size: f32,
                   @location(2) color: u32) -> VertexOutput {
                var output: VertexOutput;
                output.position = vec4f(position, 1.0);
                output.color = vec4f(
                    f32(color & 0xFF) / 255.0,
                    f32((color >> 8) & 0xFF) / 255.0,
                    f32((color >> 16) & 0xFF) / 255.0,
                    1.0
                );
                return output;
            }
        """
        
        private const val DEFAULT_FRAGMENT_SHADER = """
            @fragment
            fn main(@location(0) color: vec4f) -> @location(0) vec4f {
                return color;
            }
        """
    }
}