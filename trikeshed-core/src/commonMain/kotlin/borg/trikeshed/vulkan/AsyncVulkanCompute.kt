package borg.trikeshed.vulkan

import kotlin.coroutines.CoroutineContext
// Potentially your common ByteBuffer if used for shader code or data transfer
import borg.trikeshed.nio.ByteBuffer

// Opaque handles for Vulkan objects (platform-specific representations)
expect class VulkanDeviceHandle
expect class VulkanQueueHandle
expect class VulkanShaderModuleHandle
expect class VulkanBufferHandle
expect class VulkanComputeCommandHandle // Represents a submitted compute operation
expect class VulkanFenceHandle          // For CPU-GPU synchronization

/**
 * Information to create a Vulkan buffer for compute.
 */
data class VulkanComputeBufferInfo(
    val size: Long, // in bytes
    val isInput: Boolean,
    val isOutput: Boolean
    // Add other relevant flags like memory properties if needed
)

/**
 * Information to describe a compute shader.
 */
data class VulkanComputeShaderInfo(
    val spirvCode: ByteArray,
    val entryPointName: String = "main"
)

/**
 * Describes the dispatch dimensions for a compute operation.
 */
data class VulkanComputeDispatchArgs(
    val groupCountX: Int,
    val groupCountY: Int,
    val groupCountZ: Int
)

/**
 * Result of a compute operation, potentially including profiling information.
 */
data class VulkanComputeResult(
    val success: Boolean,
    val executionTimeNanos: Long? = null, // Optional
    val errorMessage: String? = null
)

/**
 * Defines an asynchronous Vulkan compute service.
 */
expect class AsyncVulkanComputeService() : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<AsyncVulkanComputeService>
    override val key: CoroutineContext.Key<*>

    /**
     * Initializes the Vulkan environment (instance, physical device, logical device, compute queue).
     *
     * @param preferHighPerformanceGpu Whether to prefer a discrete/high-performance GPU if multiple are available.
     * @return True if initialization was successful, false otherwise.
     */
    suspend fun initialize(preferHighPerformanceGpu: Boolean = true): Boolean

    /**
     * Checks if the service has been successfully initialized.
     */
    fun isInitialized(): Boolean

    /**
     * Creates a Vulkan buffer suitable for compute shader input/output.
     *
     * @param info Description of the buffer to create.
     * @return A handle to the created buffer, or null on failure.
     */
    suspend fun createComputeBuffer(info: VulkanComputeBufferInfo): VulkanBufferHandle?

    /**
     * Writes data from a host ByteBuffer to a Vulkan device buffer.
     * This operation itself might be asynchronous, with completion signaled separately or waited upon.
     *
     * @param deviceBuffer The target Vulkan buffer handle.
     * @param data The source ByteBuffer (data to write).
     * @param deviceOffset The offset in the device buffer to start writing.
     * @param fenceToSignal An optional fence to signal when this transfer completes.
     * @return True if the write operation was successfully submitted.
     */
    suspend fun writeToBuffer(
        deviceBuffer: VulkanBufferHandle,
        data: ByteBuffer, // Using your common ByteBuffer
        deviceOffset: Long = 0L,
        fenceToSignal: VulkanFenceHandle? = null
    ): Boolean

    /**
     * Reads data from a Vulkan device buffer into a host ByteBuffer.
     * This operation might be asynchronous.
     *
     * @param deviceBuffer The source Vulkan buffer handle.
     * @param destinationBuffer The host ByteBuffer to read data into.
     * @param deviceOffset The offset in the device buffer to start reading.
     * @param fenceToSignal An optional fence to signal when this transfer completes.
     * @return True if the read operation was successfully submitted.
     */
    suspend fun readFromBuffer(
        deviceBuffer: VulkanBufferHandle,
        destinationBuffer: ByteBuffer,
        deviceOffset: Long = 0L,
        fenceToSignal: VulkanFenceHandle? = null
    ): Boolean

    /**
     * Destroys a Vulkan buffer and frees its memory.
     * @param bufferHandle The handle of the buffer to destroy.
     */
    suspend fun destroyComputeBuffer(bufferHandle: VulkanBufferHandle)

    /**
     * Creates a Vulkan shader module from SPIR-V bytecode.
     * @param shaderInfo Information about the shader.
     * @return A handle to the created shader module, or null on failure.
     */
    suspend fun createComputeShaderModule(shaderInfo: VulkanComputeShaderInfo): VulkanShaderModuleHandle?

    /**
     * Destroys a Vulkan shader module.
     * @param shaderModuleHandle The handle of the shader module to destroy.
     */
    suspend fun destroyComputeShaderModule(shaderModuleHandle: VulkanShaderModuleHandle)

    /**
     * Submits an asynchronous compute operation to a GPU queue.
     * This is a simplified representation. A real version would involve creating compute pipelines,
     * descriptor sets for binding buffers, command buffer recording, etc.
     *
     * @param shaderModule The compiled compute shader to execute.
     * @param inputBuffers A list of input buffer handles and their binding information (simplified).
     * @param outputBuffers A list of output buffer handles and their binding information (simplified).
     * @param dispatchArgs The dimensions for the compute grid.
     * @param fenceToSignal An optional fence to signal upon completion of this compute dispatch.
     * @return A [VulkanComputeCommandHandle] representing the submitted work, or null on failure.
     */
    suspend fun submitAsyncCompute(
        shaderModule: VulkanShaderModuleHandle,
        inputBuffers: List<VulkanBufferHandle>,  // Simplified binding
        outputBuffers: List<VulkanBufferHandle>, // Simplified binding
        dispatchArgs: VulkanComputeDispatchArgs,
        fenceToSignal: VulkanFenceHandle? = null
    ): VulkanComputeCommandHandle?

    /**
     * Creates a Vulkan fence that can be used for synchronization.
     * @param signaled If true, the fence is created in a signaled state.
     * @return A handle to the created fence, or null on failure.
     */
    suspend fun createFence(signaled: Boolean = false): VulkanFenceHandle?

    /**
     * Waits for one or more fences to be signaled.
     * @param fences A list of fence handles to wait for.
     * @param waitAll If true, waits for all fences. If false, waits for any one fence.
     * @param timeoutMillis Maximum time to wait in milliseconds. A negative value means infinite wait.
     * @return True if the condition (all/any) was met, false on timeout or error.
     */
    suspend fun waitForFences(fences: List<VulkanFenceHandle>, waitAll: Boolean = true, timeoutMillis: Long): Boolean

    /**
     * Resets one or more fences to the unsignaled state.
     * @param fences The list of fence handles to reset.
     * @return True if reset was successful for all specified fences.
     */
    suspend fun resetFences(fences: List<VulkanFenceHandle>): Boolean

    /**
     * Destroys a Vulkan fence.
     * @param fenceHandle The handle of the fence to destroy.
     */
    suspend fun destroyFence(fenceHandle: VulkanFenceHandle)

    /**
     * Shuts down the Vulkan compute service, releasing all associated resources (device, instance, etc.).
     */
    fun shutdown()
}
