package fiduciary.context

import fiduciary.hexagon.*
import kotlinx.coroutines.*

/**
 * Context-based tethering - components hook up to deliberate context APIs
 * No direct coupling - everything goes through context trait interfaces
 */
suspend fun main() {
    println("🔗 Starting context-based tethering...")
    
    // 1. Create components
    val storageComponent = StorageComponent("context-db")
    val routingComponent = RoutingComponent()
    val attentionComponent = AttentionComponent()
    val ingestionComponent = IngestionComponent()
    val analyticsComponent = AnalyticsComponent()
    val crdtComponent = CRDTComponent()
    
    // 2. Register context providers first
    println("📋 Registering context providers...")
    storageComponent.registerContexts()
    routingComponent.registerContexts()
    attentionComponent.registerContexts()
    ingestionComponent.registerContexts()
    analyticsComponent.registerContexts()
    crdtComponent.registerContexts()
    
    println("✅ Registered contexts: ${ContextRegistry.getRegisteredContexts()}")
    
    // 3. Initialize context-aware components
    println("🔧 Initializing context-aware components...")
    storageComponent.initializeContexts()
    routingComponent.initializeContexts()
    attentionComponent.initializeContexts()
    ingestionComponent.initializeContexts()
    crdtComponent.initializeContexts()
    
    println("✅ All components initialized with their required contexts")
    
    // 4. Set up routing rules via context API
    val routingContext = ContextRegistry.require<RoutingContext>(ContextKeys.ROUTING)
    routingContext.addRule(RoutingRule(
        name = "High Priority Content",
        condition = { blob -> String(blob.data).contains("important") },
        decision = RoutingDecision.ATTENTION,
        priority = 10
    ))
    
    routingContext.addRule(RoutingRule(
        name = "Batch Processing",
        condition = { blob -> String(blob.data).contains("batch") },
        decision = RoutingDecision.BATCH,
        priority = 5
    ))
    
    println("✅ Routing rules configured via context API")
    
    // 5. CONTEXT TETHER DEMO: Show how components use context APIs
    println("\n🎯 CONTEXT TETHER DEMONSTRATION")
    println("=" * 50)
    
    // Example 1: Regular content processing
    println("\n📥 Example 1: Regular content processing")
    val ingestionContext = ContextRegistry.require<IngestionContext>(ContextKeys.INGESTION)
    
    val result1 = ingestionContext.ingest(IngestionData(
        content = "Regular content for processing".toByteArray(),
        contentType = "text/plain",
        metadata = mapOf("source" to "user")
    ))
    
    println("   Ingested: ${result1.id}")
    println("   -> Uses RoutingContext to route")
    println("   -> Uses StorageContext to store")
    println("   -> Uses AnalyticsContext to track")
    
    // Example 2: High priority content
    println("\n⚡ Example 2: High priority content")
    val result2 = ingestionContext.ingest(IngestionData(
        content = "This is important content that needs attention".toByteArray(),
        contentType = "text/plain",
        metadata = mapOf("priority" to "high")
    ))
    
    println("   Ingested: ${result2.id}")
    println("   -> Uses RoutingContext to route (matches attention rule)")
    println("   -> Uses AttentionContext to track attention")
    println("   -> Uses StorageContext to store")
    println("   -> Uses AnalyticsContext to track")
    
    // Example 3: Batch processing
    println("\n📦 Example 3: Batch processing")
    val result3 = ingestionContext.ingest(IngestionData(
        content = "Large batch content for distributed processing".toByteArray(),
        contentType = "text/plain",
        metadata = mapOf("size" to "large")
    ))
    
    println("   Ingested: ${result3.id}")
    println("   -> Uses RoutingContext to route (matches batch rule)")
    println("   -> Uses CRDTContext to apply distributed operations")
    println("   -> Uses StorageContext to persist CRDT state")
    println("   -> Uses AnalyticsContext to track")
    
    // 6. Query analytics to see what happened
    println("\n📊 Analytics Results")
    val analyticsContext = ContextRegistry.require<AnalyticsContext>(ContextKeys.ANALYTICS)
    
    val ingestionEvents = analyticsContext.query(AnalyticsQuery(
        type = QueryType.INGESTION_RATE,
        filters = mapOf("type" to "ingestion.ingest")
    ))
    
    val routingEvents = analyticsContext.query(AnalyticsQuery(
        type = QueryType.BLOB_COUNT,
        filters = mapOf("type" to "routing.route")
    ))
    
    val attentionEvents = analyticsContext.query(AnalyticsQuery(
        type = QueryType.ATTENTION_METRICS,
        filters = mapOf("type" to "attention.track")
    ))
    
    val storageEvents = analyticsContext.query(AnalyticsQuery(
        type = QueryType.STORAGE_USAGE,
        filters = mapOf("type" to "storage.store")
    ))
    
    println("   Ingestion events: ${ingestionEvents.totalCount}")
    println("   Routing events: ${routingEvents.totalCount}")
    println("   Attention events: ${attentionEvents.totalCount}")
    println("   Storage events: ${storageEvents.totalCount}")
    
    // 7. Show context-based queries
    println("\n🔍 Context-based Queries")
    val storageContext = ContextRegistry.require<StorageContext>(ContextKeys.STORAGE)
    
    val storedData = storageContext.query(StorageQuery(
        selector = mapOf("type" to "regular"),
        limit = 10
    ))
    
    val crdtContext = ContextRegistry.require<CRDTContext>(ContextKeys.CRDT)
    val crdtState = crdtContext.get(result3.id)
    
    val attentionContext = ContextRegistry.require<AttentionContext>(ContextKeys.ATTENTION)
    val attentionMetrics = attentionContext.getMetrics(result2.id)
    
    println("   Storage query results: ${storedData.size}")
    println("   CRDT state values: ${crdtState.values.size}")
    println("   Attention metrics: ${attentionMetrics.totalEvents} events")
    
    println("\n✅ Context-based tethering demonstration complete!")
    println("   Components communicate only through context APIs")
    println("   No direct coupling - everything is trait-based")
    println("   Each component provides and consumes specific contexts")
}

/**
 * Show the context dependency graph
 */
fun printContextGraph() {
    println("""
    🔗 Context-based Tethering Graph:
    
    Components and their Context APIs:
    
    IngestionComponent
    ├── Provides: IngestionContext
    └── Uses: RoutingContext, AnalyticsContext
    
    RoutingComponent  
    ├── Provides: RoutingContext
    └── Uses: StorageContext, AttentionContext, AnalyticsContext
    
    StorageComponent
    ├── Provides: StorageContext
    └── Uses: AnalyticsContext
    
    AttentionComponent
    ├── Provides: AttentionContext
    └── Uses: AnalyticsContext
    
    CRDTComponent
    ├── Provides: CRDTContext
    └── Uses: StorageContext, AnalyticsContext
    
    AnalyticsComponent
    ├── Provides: AnalyticsContext
    └── Uses: (none - leaf component)
    
    Context Flow:
    IngestionContext.ingest()
    └── RoutingContext.route()
        ├── StorageContext.store()
        │   └── AnalyticsContext.track()
        ├── AttentionContext.track()
        │   └── AnalyticsContext.track()
        └── CRDTContext.apply()
            ├── StorageContext.store()
            └── AnalyticsContext.track()
    
    Key Benefits:
    - No direct component coupling
    - Interface-based communication
    - Testable context APIs
    - Pluggable implementations
    - Clear dependency management
    """.trimIndent())
}

/**
 * Test individual context APIs
 */
suspend fun testContextAPIs() {
    println("🧪 Testing individual context APIs...")
    
    // Set up minimal context registry
    val analyticsComponent = AnalyticsComponent()
    val storageComponent = StorageComponent("test-db")
    
    analyticsComponent.registerContexts()
    storageComponent.registerContexts()
    
    // Test storage context
    val storageContext = ContextRegistry.require<StorageContext>(ContextKeys.STORAGE)
    val storeResult = storageContext.store(StorageData(
        id = "test-1",
        content = "test content".toByteArray(),
        metadata = mapOf("test" to "true")
    ))
    
    println("Storage test: ${storeResult.success}")
    
    // Test analytics context
    val analyticsContext = ContextRegistry.require<AnalyticsContext>(ContextKeys.ANALYTICS)
    val trackResult = analyticsContext.track(AnalyticsEvent(
        type = "test.event",
        entityId = "test-1",
        data = mapOf("result" to "success")
    ))
    
    println("Analytics test: ${trackResult.tracked}")
    
    // Test query
    val queryResult = analyticsContext.query(AnalyticsQuery(
        type = QueryType.BLOB_COUNT,
        filters = mapOf("type" to "test.event")
    ))
    
    println("Query test: ${queryResult.totalCount} events found")
    
    println("✅ Context API tests complete")
}

/**
 * Show how to add a new component that uses contexts
 */
class CustomProcessorComponent : ContextAware {
    override val contextKeys = setOf(
        ContextKeys.STORAGE,
        ContextKeys.ATTENTION,
        ContextKeys.ANALYTICS
    )
    
    suspend fun processCustomLogic(entityId: String) {
        initializeContexts()
        
        // Use storage context
        val storageContext = ContextRegistry.require<StorageContext>(ContextKeys.STORAGE)
        val data = storageContext.retrieve(entityId)
        
        if (data != null) {
            // Use attention context
            val attentionContext = ContextRegistry.require<AttentionContext>(ContextKeys.ATTENTION)
            attentionContext.track(AttentionData(
                entityId = entityId,
                type = AttentionType.FOCUS,
                intensity = 0.9
            ))
            
            // Use analytics context
            val analyticsContext = ContextRegistry.require<AnalyticsContext>(ContextKeys.ANALYTICS)
            analyticsContext.track(AnalyticsEvent(
                type = "custom.process",
                entityId = entityId,
                data = mapOf("processed" to "true")
            ))
        }
    }
}