package fiduciary.main

import fiduciary.context.*
import fiduciary.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File

/**
 * Patrick 0720 Main - Step 1
 * Entry point that sets up context-based tethering and bootstraps from fiduciary URLs
 */
suspend fun main() {
    println("🚀 Patrick 0720 Main - Step 1: Bootstrap & Context Setup")
    println("=" * 60)
    
    // Step 1: Fetch and bootstrap from fiduciary URLs
    val bootstrapResult = bootstrapFromFiduciaryUrls()
    
    // Step 1a: Initialize all components and register their contexts
    val components = initializeComponents()
    
    // Step 1b: Register all context providers
    registerContextProviders(components)
    
    // Step 1c: Initialize all context-aware components
    initializeContextAwareComponents(components)
    
    // Step 1d: Load Patrick Divine indexes into system
    loadPatrickIndexes(components, bootstrapResult)
    
    // Step 1e: Verify all contexts are properly registered
    verifyContextRegistry()
    
    println("✅ Step 1 Complete: Bootstrap, indexes, and contexts ready")
    println("   📊 Patrick Divine archives: ${bootstrapResult.archiveCount}")
    println("   📁 Index entries loaded: ${bootstrapResult.totalEntries}")
    println("   🔗 Contexts registered: ${ContextRegistry.getRegisteredContexts().size}")
}

/**
 * Step 1: Initialize all system components
 */
private fun initializeComponents(): SystemComponents {
    println("🔧 Step 1: Initializing system components...")
    
    val storageComponent = StorageComponent("patrick0720-storage")
    val routingComponent = RoutingComponent()
    val attentionComponent = AttentionComponent()
    val ingestionComponent = IngestionComponent()
    val analyticsComponent = AnalyticsComponent()
    val crdtComponent = CRDTComponent()
    
    println("   ✅ Created 6 components")
    
    return SystemComponents(
        storage = storageComponent,
        routing = routingComponent,
        attention = attentionComponent,
        ingestion = ingestionComponent,
        analytics = analyticsComponent,
        crdt = crdtComponent
    )
}

/**
 * Step 1a: Register all context providers with the registry
 */
private fun registerContextProviders(components: SystemComponents) {
    println("📋 Step 1a: Registering context providers...")
    
    // Order matters - analytics first since others depend on it
    components.analytics.registerContexts()
    println("   ✅ AnalyticsContext registered")
    
    components.storage.registerContexts()
    println("   ✅ StorageContext registered")
    
    components.attention.registerContexts()
    println("   ✅ AttentionContext registered")
    
    components.crdt.registerContexts()
    println("   ✅ CRDTContext registered")
    
    components.routing.registerContexts()
    println("   ✅ RoutingContext registered")
    
    components.ingestion.registerContexts()
    println("   ✅ IngestionContext registered")
    
    val registeredContexts = ContextRegistry.getRegisteredContexts()
    println("   📊 Total contexts registered: ${registeredContexts.size}")
    println("   📝 Registered contexts: ${registeredContexts.joinToString(", ")}")
}

/**
 * Step 1b: Initialize all context-aware components
 */
private suspend fun initializeContextAwareComponents(components: SystemComponents) {
    println("🔗 Step 1b: Initializing context-aware components...")
    
    // Initialize components that depend on other contexts
    try {
        components.storage.initializeContexts()
        println("   ✅ StorageComponent contexts initialized")
        
        components.routing.initializeContexts()
        println("   ✅ RoutingComponent contexts initialized")
        
        components.attention.initializeContexts()
        println("   ✅ AttentionComponent contexts initialized")
        
        components.ingestion.initializeContexts()
        println("   ✅ IngestionComponent contexts initialized")
        
        components.crdt.initializeContexts()
        println("   ✅ CRDTComponent contexts initialized")
        
    } catch (e: Exception) {
        println("   ❌ Context initialization failed: ${e.message}")
        throw e
    }
}

/**
 * Step 1c: Verify all contexts are properly registered and accessible
 */
private fun verifyContextRegistry() {
    println("🔍 Step 1c: Verifying context registry...")
    
    val requiredContexts = setOf(
        ContextKeys.STORAGE,
        ContextKeys.ROUTING,
        ContextKeys.ATTENTION,
        ContextKeys.CRDT,
        ContextKeys.ANALYTICS,
        ContextKeys.INGESTION
    )
    
    val registeredContexts = ContextRegistry.getRegisteredContexts()
    
    requiredContexts.forEach { contextKey ->
        if (contextKey in registeredContexts) {
            val context = ContextRegistry.get<Any>(contextKey)
            if (context != null) {
                println("   ✅ $contextKey: Available and accessible")
            } else {
                println("   ❌ $contextKey: Registered but not accessible")
                throw IllegalStateException("Context $contextKey is not accessible")
            }
        } else {
            println("   ❌ $contextKey: Not registered")
            throw IllegalStateException("Required context $contextKey is not registered")
        }
    }
    
    println("   🎯 All ${requiredContexts.size} required contexts verified")
}

/**
 * System components container
 */
data class SystemComponents(
    val storage: StorageComponent,
    val routing: RoutingComponent,
    val attention: AttentionComponent,
    val ingestion: IngestionComponent,
    val analytics: AnalyticsComponent,
    val crdt: CRDTComponent
)

/**
 * Step 1: Bootstrap from fiduciary URLs - fetch Patrick Divine indexes
 */
private suspend fun bootstrapFromFiduciaryUrls(): BootstrapResult {
    println("🌐 Step 1: Bootstrapping from fiduciary URLs...")
    
    // URLs from fiduciary codebase
    val patrickDevineUrls = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )
    
    println("   📡 Found ${patrickDevineUrls.size} Patrick Divine archive URLs")
    
    // Create HTTP client
    val ioContext = IOContext.NioContext("patrick-bootstrap")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    // Create divine index fetcher
    val fetcher = DivineIndexFetcher(
        httpClient = httpClient,
        outputDir = "fiduciary/patrick-indexes"
    )
    
    println("   🔄 Fetching archive indexes...")
    
    // Fetch all indexes
    val indexes = mutableListOf<DivineIndex>()
    var totalEntries = 0
    
    for (url in patrickDevineUrls) {
        try {
            println("     📥 Fetching: ${extractArchiveName(url)}")
            val index = fetcher.fetchArchiveIndex(url)
            indexes.add(index)
            totalEntries += index.entries.size
            println("     ✅ Found ${index.entries.size} entries")
        } catch (e: Exception) {
            println("     ❌ Failed to fetch ${extractArchiveName(url)}: ${e.message}")
        }
    }
    
    // Save indexes to workspace
    val indexDir = File("fiduciary/patrick-indexes")
    indexDir.mkdirs()
    
    indexes.forEach { index ->
        val indexFile = File(indexDir, "${extractArchiveName(index.archiveUrl)}_index.json")
        val json = Json { 
            prettyPrint = true 
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(DivineIndex.serializer(), index)
        indexFile.writeText(jsonString)
        println("   💾 Saved index: ${indexFile.name}")
    }
    
    println("   ✅ Bootstrap complete: ${indexes.size} archives, $totalEntries total entries")
    
    return BootstrapResult(
        archiveCount = indexes.size,
        totalEntries = totalEntries,
        indexes = indexes,
        indexDirectory = indexDir.absolutePath
    )
}

/**
 * Step 1d: Load Patrick indexes into system contexts
 */
private suspend fun loadPatrickIndexes(components: SystemComponents, bootstrap: BootstrapResult) {
    println("📂 Step 1d: Loading Patrick indexes into system...")
    
    val storageContext = ContextRegistry.require<StorageContext>(ContextKeys.STORAGE)
    val analyticsContext = ContextRegistry.require<AnalyticsContext>(ContextKeys.ANALYTICS)
    
    var loadedEntries = 0
    
    for (index in bootstrap.indexes) {
        // Load index metadata into storage
        val indexMetadata = StorageData(
            id = "index-${extractArchiveName(index.archiveUrl)}",
            content = Json.encodeToString(DivineIndex.serializer(), index).toByteArray(),
            metadata = mapOf(
                "type" to "patrick-divine-index",
                "archive" to index.archiveName,
                "entries" to index.entries.size.toString(),
                "url" to index.archiveUrl
            )
        )
        
        val storeResult = storageContext.store(indexMetadata)
        if (storeResult.success) {
            println("   ✅ Loaded index: ${index.archiveName}")
            loadedEntries += index.entries.size
            
            // Track in analytics
            analyticsContext.track(AnalyticsEvent(
                type = "bootstrap.index_loaded",
                entityId = index.archiveName,
                data = mapOf(
                    "entries" to index.entries.size.toString(),
                    "archive_size" to index.totalSize.toString()
                )
            ))
        } else {
            println("   ❌ Failed to load index: ${index.archiveName}")
        }
    }
    
    println("   📊 Loaded $loadedEntries Patrick Divine entries into storage context")
}

/**
 * Helper function to extract archive name from URL
 */
private fun extractArchiveName(url: String): String {
    val fileName = url.substringAfterLast("/")
    return fileName.substringBefore("?").removeSuffix(".zip")
}

/**
 * Bootstrap result data
 */
@Serializable
data class BootstrapResult(
    val archiveCount: Int,
    val totalEntries: Int,
    val indexes: List<DivineIndex>,
    val indexDirectory: String
)

private operator fun String.times(n: Int): String = this.repeat(n)