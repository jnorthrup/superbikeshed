package fiduciary.tether

import fiduciary.hexagon.*
import kotlinx.coroutines.*

/**
 * Real tether initialization - shows actual call chain from main to next to next
 */
suspend fun main() {
    // Create components
    val storage = CouchDBStorageHexagon("tether-db")
    val crdt = CRDTHexagon()
    val crdtStream = CRDTStreamHexagon()
    val dashboard = DashboardAnalyticsHexagon()
    val attention = AttentionHexagon()
    val router = RouterHexagon()
    val ingester = IngesterHexagon()
    
    // REAL TETHER: Wire components together with actual dependencies
    // Each component holds reference to next component and calls it directly
    
    // Storage connects to CRDT and Dashboard
    storage.connectCRDT(crdt)
    storage.connectDashboard(dashboard)
    
    // CRDT connects back to Storage (bidirectional)
    crdt.connectToStorage(storage)
    
    // CRDT Stream connects to CRDT, Storage, and Dashboard
    crdtStream.connectCRDT(crdt)
    crdtStream.connectStorage(storage)
    crdtStream.connectDashboard(dashboard)
    
    // Attention connects to Dashboard
    attention.connectDashboard(dashboard)
    
    // Router connects to Storage, Attention, and CRDT Stream
    router.connectStorage(storage)
    router.connectAttention(attention)
    router.connectCRDTStream(crdtStream)
    
    // Ingester connects to Router
    ingester.connectRouter(router)
    
    // Add routing rule for demonstration
    router.addRoutingRule(RoutingRule(
        name = "High Priority",
        condition = { blob -> String(blob.data).contains("important") },
        decision = RoutingDecision.ATTENTION,
        priority = 10
    ))
    
    // REAL CALL CHAIN: One call triggers cascade through all connected components
    println("🔗 Starting real tether call chain...")
    
    // 1. Main calls Ingester.ingest()
    val blob = ingester.ingest("This is important content".toByteArray(), "text/plain")
    
    // This automatically triggers the entire chain:
    // Ingester.ingest() -> calls Router.route()
    // Router.route() -> calls Attention.processAttention() AND Storage.store()
    // Attention.processAttention() -> calls Dashboard.notifyAttentionEvent()
    // Storage.store() -> calls CRDT.applyCRDTOperation() AND Dashboard.notifyStorageChange()
    // CRDT.applyCRDTOperation() -> calls Storage.store() (sync back)
    
    println("✅ Call chain completed - blob ${blob.id} processed through entire system")
    
    // 2. Another call chain example with batch processing
    val batchBlob = ingester.ingest("Large batch content".toByteArray(), "text/plain")
    
    // This would trigger:
    // Ingester.ingest() -> Router.route() -> CRDTStream.publishUpdate()
    // CRDTStream.publishUpdate() -> CRDT.applyCRDTOperation() AND Storage.store() AND Dashboard.notifyStorageChange()
    
    println("✅ Batch processing chain completed - blob ${batchBlob.id}")
    
    // 3. Direct CRDT stream example
    crdtStream.publishUpdate(CRDTUpdate(
        entityId = "manual-update",
        operation = CRDTOperation.ADD,
        value = "direct-crdt-value",
        timestamp = System.currentTimeMillis()
    ))
    
    // This triggers:
    // CRDTStream.publishUpdate() -> CRDT.applyCRDTOperation() AND Storage.store() AND Dashboard.notifyStorageChange()
    
    println("✅ Direct CRDT stream chain completed")
    
    println("\n🎯 Real tethering demonstration complete!")
    println("   Each component directly calls the next component in the chain")
    println("   No event buses, no loose coupling - actual method calls")
    println("   Dependencies are injected and used immediately")
}

/**
 * Show the actual dependency graph
 */
fun printDependencyGraph() {
    println("""
    🔗 Real Tether Dependency Graph:
    
    Ingester ──→ Router ──┬──→ Storage ──┬──→ CRDT ──→ Storage (sync)
                          │              └──→ Dashboard
                          ├──→ Attention ──→ Dashboard  
                          └──→ CRDTStream ──┬──→ CRDT
                                            ├──→ Storage
                                            └──→ Dashboard
    
    Real method calls (not events):
    - ingester.ingest() calls router.route()
    - router.route() calls storage.store() OR attention.processAttention() OR crdtStream.publishUpdate()
    - storage.store() calls crdt.applyCRDTOperation() AND dashboard.notifyStorageChange()
    - attention.processAttention() calls dashboard.notifyAttentionEvent()
    - crdtStream.publishUpdate() calls crdt.applyCRDTOperation() AND storage.store() AND dashboard.notifyStorageChange()
    """.trimIndent())
}

/**
 * Test the tether chain with specific routing
 */
suspend fun testTetherChain() {
    // Set up minimal tether
    val storage = CouchDBStorageHexagon("test-db")
    val dashboard = DashboardAnalyticsHexagon()
    val router = RouterHexagon()
    val ingester = IngesterHexagon()
    
    // Wire up
    storage.connectDashboard(dashboard)
    router.connectStorage(storage)
    ingester.connectRouter(router)
    
    // Test the chain
    println("Testing tether chain:")
    
    // This single call will:
    // 1. ingester.ingest() -> 2. router.route() -> 3. storage.store() -> 4. dashboard.notifyStorageChange()
    val result = ingester.ingest("test content".toByteArray(), "text/plain")
    
    println("Chain executed: Ingester -> Router -> Storage -> Dashboard")
    println("Result: ${result.id}")
}