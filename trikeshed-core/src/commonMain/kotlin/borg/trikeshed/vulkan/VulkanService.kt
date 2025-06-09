package borg.trikeshed.vulkan

import kotlin.coroutines.CoroutineContext

/** Opaque handle to a Vulkan instance (`VkInstance`). */
expect class VulkanInstanceHandle

/** Opaque handle to a Vulkan physical device (`VkPhysicalDevice`). */
expect class PhysicalDeviceHandle

/** Opaque handle to a Vulkan logical device (`VkDevice`). */
expect class LogicalDeviceHandle

/** Opaque handle to a Vulkan queue (`VkQueue`). */
expect class QueueHandle

// --- Informational Data Classes (Common) ---

/** Basic information about a physical device. */
data class PhysicalDeviceInfo(
    val deviceName: String,
    val deviceID: UInt,
    val vendorID: UInt,
    val deviceType: String, // e.g., "INTEGRATED_GPU", "DISCRETE_GPU", "CPU", "OTHER"
    val apiVersionMajor: UInt,
    val apiVersionMinor: UInt,
    val apiVersionPatch: UInt
) {
    val apiVersionString: String get() = "$apiVersionMajor.$apiVersionMinor.$apiVersionPatch"
}

/** Basic information about a queue family on a physical device. */
data class QueueFamilyInfo(
    val index: UInt,
    val queueCount: UInt,
    val supportsGraphics: Boolean,
    val supportsCompute: Boolean,
    val supportsTransfer: Boolean,
    val supportsSparseBinding: Boolean
    // timestampValidBits could be added if needed
)

// --- Vulkan Service CCEK ---

/**
 * Defines the contract for a platform-agnostic Vulkan service.
 * This provides a very high-level interface to Vulkan initialization,
 * device enumeration, and logical device creation. It does not cover
 * rendering, compute, memory management, or synchronization primitives of Vulkan,
 * which are extensive.
 */
expect class VulkanService() : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<VulkanService>
    override val key: CoroutineContext.Key<*>

    /**
     * Checks if the Vulkan loader and a compatible runtime are available on the system.
     * This might involve trying to load the Vulkan library or checking for specific system properties.
     * @return True if Vulkan appears to be available, false otherwise.
     */
    fun isVulkanAvailable(): Boolean

    /**
     * Creates a Vulkan instance.
     *
     * @param applicationName The name of the application.
     * @param applicationVersion The version of the application (encoded as per Vulkan spec, e.g., `VK_MAKE_VERSION(1,0,0)`).
     * @param engineName Optional name of the engine.
     * @param engineVersion Optional version of the engine.
     * @param requiredInstanceExtensions A list of required instance-level extension names (e.g., "VK_KHR_surface").
     * @param enableValidationLayers If true, attempts to enable standard Vulkan validation layers (primarily for debugging).
     * @return A [Result] containing the [VulkanInstanceHandle] on success, or an [Exception] (e.g., VulkanException) on failure.
     */
    suspend fun createInstance(
        applicationName: String,
        applicationVersion: UInt = 0u, // VK_MAKE_VERSION(1,0,0)
        engineName: String? = "TrikeShedEngine",
        engineVersion: UInt = 0u,
        requiredInstanceExtensions: List<String> = emptyList(),
        enableValidationLayers: Boolean = false
    ): Result<VulkanInstanceHandle>

    /**
     * Destroys a Vulkan instance and releases its resources.
     * @param instanceHandle The handle of the instance to destroy.
     */
    fun destroyInstance(instanceHandle: VulkanInstanceHandle)

    /**
     * Enumerates physical devices (GPUs, CPUs with Vulkan support) available to a Vulkan instance.
     *
     * @param instanceHandle The handle of the Vulkan instance.
     * @return A list of pairs, where each pair contains a [PhysicalDeviceHandle] and its corresponding [PhysicalDeviceInfo].
     * @throws VulkanException if the instance is invalid or enumeration fails.
     */
    suspend fun enumeratePhysicalDevices(instanceHandle: VulkanInstanceHandle): List<Pair<PhysicalDeviceHandle, PhysicalDeviceInfo>>

    /**
     * Gets detailed queue family properties for a given physical device.
     *
     * @param physicalDeviceHandle The handle of the physical device.
     * @return A list of [QueueFamilyInfo] describing each queue family.
     * @throws VulkanException if the handle is invalid or the query fails.
     */
    suspend fun getQueueFamilyProperties(physicalDeviceHandle: PhysicalDeviceHandle): List<QueueFamilyInfo>

    /**
     * Creates a logical device and one or more queues from a physical device.
     * This is a simplified interface; real device creation is complex involving features, multiple queue families, etc.
     *
     * @param physicalDeviceHandle The handle of the physical device.
     * @param queueFamilyIndex The index of the queue family from which to create queues. This must be a valid index
     *                         obtained from [getQueueFamilyProperties].
     * @param queuePriorities A list of priorities (0.0f to 1.0f) for the queues to be created from the specified family.
     *                        The number of priorities determines the number of queues created from this family.
     *                        The count must not exceed the `queueCount` of the chosen family.
     * @param requiredDeviceExtensions A list of required device-level extension names (e.g., "VK_KHR_swapchain").
     * @param enabledFeatures A data structure (platform-specific or a common one if designed) representing
     *                        `VkPhysicalDeviceFeatures` to enable. For this sketch, we omit it for simplicity,
     *                        implying default features or that the platform actual handles common features.
     * @return A [Result] containing a Pair of the [LogicalDeviceHandle] and a list of [QueueHandle]s on success,
     *         or an [Exception] (e.g., VulkanException) on failure.
     */
    suspend fun createLogicalDevice(
        physicalDeviceHandle: PhysicalDeviceHandle,
        queueFamilyIndex: UInt,
        queuePriorities: List<Float> = listOf(1.0f),
        requiredDeviceExtensions: List<String> = emptyList()
        // enabledFeatures: Any? = null // Placeholder for VkPhysicalDeviceFeatures
    ): Result<Pair<LogicalDeviceHandle, List<QueueHandle>>>

    /**
     * Destroys a logical Vulkan device.
     * This will also implicitly destroy queues obtained from it.
     * @param deviceHandle The handle of the logical device to destroy.
     */
    fun destroyLogicalDevice(deviceHandle: LogicalDeviceHandle)

    /**
     * Optional: Shuts down the VulkanService, cleaning up any global resources it might hold (e.g. loaded loader).
     * This might be relevant on some platforms or if a global context is initialized.
     */
    fun shutdownService()
}

/** Custom exception type for Vulkan related errors. */
class VulkanException(message: String, val errorCode: Int? = null, cause: Throwable? = null) : RuntimeException(message, cause)
