# Omnibus Implementation Plan: Viola A/B Testing with Kotlin Playground

## Goal
A/B test the existing niloc132 Viola server against a new Kotlin playground implementation integrated with KMP and Viola, measuring performance, developer experience, and system reliability. GWT will be hosted as immutable blobs without requiring migration.

## Phase 1: Baseline Metrics (Week 1)
### niloc132 Viola Server Baseline
- [ ] Deploy monitoring on existing Viola server
- [ ] Measure: compile times, memory usage, request latency
- [ ] Document current GWT compilation pipeline performance
- [ ] Capture developer workflow metrics

### Metrics to Track
```kotlin
data class BaselineMetrics(
    val compileTimeMs: Long,
    val memoryUsageMB: Long,
    val requestLatencyP99: Long,
    val concurrentUsers: Int,
    val errorRate: Float
)
```

## Phase 2: Kotlin Playground Integration (Week 2-3)
### Core Components
1. **Trikeshed io_uring Host**
   - Single native Linux process with io_uring
   - Hosts JVM and WASM VMs with IPC
   - Zero-copy network slabs for all I/O

2. **Concentric DHT Router**
   - Kademlia with dynamic key lengths
   - N-way routing for agent distribution
   - Subnet isolation for A/B cohorts

3. **SIMD JSON/Binary Bridge**
   - Fast conversion for Viola<->Kotlin interop
   - Bitmap-indexed network slabs
   - io_uring-first design

### Implementation Checklist
```kotlin
// Minimal Viola-compatible Kotlin playground
interface KotlinPlayground {
    suspend fun compile(source: String): CompileResult
    suspend fun execute(bytecode: ByteArray): ExecuteResult
    suspend fun bridge(gwtModule: String): BridgeResult
}

// A/B test controller
interface ABTestController {
    suspend fun route(request: PlaygroundRequest): ABCohort
    suspend fun measure(cohort: ABCohort, metrics: Metrics)
    suspend fun analyze(): ABTestResult
}
```

## Phase 3: A/B Test Setup (Week 4)
### Traffic Splitting
- 50/50 split using Concentric DHT consistent hashing
- Sticky sessions per developer
- Real-time metrics collection via gossip sphere

### Test Matrix
| Feature | niloc132 Viola | Kotlin Playground |
|---------|---------------|-------------------|
| Compile Time | Baseline | Target: -30% |
| Memory Usage | Baseline | Target: -50% |
| Dev Experience | GWT | Kotlin/JS |
| Hot Reload | Limited | Full |
| REPL | No | Yes |

## Phase 4: Integration Points (Week 5)
### Shared Infrastructure
1. **CouchDB Storage**
   - Both systems use same CouchDB instance
   - Replication for consistency
   - Conflict resolution via Trikeshed consensus

2. **RequestFactory Compatibility**
   - Pure KMP RequestFactory implementation
   - Supports both GWT and Kotlin clients
   - GWT hosted as blobs (no migration needed)

3. **Live Development Sync**
   - RXF-rsync for both cohorts
   - Git integration maintained
   - File watcher compatibility

## Phase 5: Rollout Strategy (Week 6)
### Progressive Deployment
1. **Canary (5%)**
   - Internal developers only
   - Full instrumentation
   - Immediate rollback capability

2. **Beta (25%)**
   - Opt-in for external developers
   - A/B metrics dashboard
   - Feedback collection

3. **GA Decision**
   - Based on A/B test results
   - Both systems coexist permanently
   - GWT remains as blob storage

## Key Differentiators

### niloc132 Viola (Control)
- Proven GWT compilation
- Existing toolchain
- Known limitations

### Kotlin Playground (Treatment)
- Native io_uring performance
- Modern Kotlin/JS output
- Integrated REPL/hot reload
- Lower resource usage
- Coexists with GWT blobs

## Success Criteria
```kotlin
data class SuccessCriteria(
    val compileTimeImprovement: Float = 0.30f, // 30% faster
    val memoryReduction: Float = 0.50f,        // 50% less memory
    val developerSatisfaction: Float = 0.80f,  // 80% prefer new
    val errorRateThreshold: Float = 0.01f      // <1% errors
)
```

## Risk Mitigation
1. **Compatibility Issues**
   - Host GWT as immutable blobs
   - No migration required - relief!
   - Both systems run in parallel

2. **Performance Regression**
   - Real-time monitoring
   - Automatic fallback to niloc132
   - Per-request routing override

3. **Data Consistency**
   - CouchDB replication ensures consistency
   - Trikeshed consensus for conflicts
   - Event sourcing for audit trail

## Implementation Order
1. Native Linux host with io_uring
2. Concentric DHT routing
3. Kotlin playground core
4. GWT blob storage integration
5. A/B test infrastructure
6. Monitoring and analytics
7. Parallel system deployment

## Monitoring Dashboard
- Real-time A/B metrics
- Cohort performance comparison
- Developer satisfaction scores
- System health indicators
- Cost analysis (CPU/memory/network)