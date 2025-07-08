# Keyed Services Architecture - Coroutine Context Composition

## Current State Analysis

```mermaid
graph TD
    subgraph "Current CCEK Implementations"
        CCEK["CcekContext<br/>(4 fields forced)"]
        QCCEK["QuicCCEK<br/>(4 concepts)"]
        CCCEK["CursorCCEK<br/>(partial impl)"] 
        ECCEK["CCEKECS<br/>(borrows context)"]
    end
    
    subgraph "Actual Coroutine Keys Used"
        K1["CcekContextKey"]
        K2["CursorContext.Key"]
        K3["RTSGameContext.Key"]
        K4["IOContext.Key"]
    end
    
    subgraph "Problems"
        P1["❌ Forced 4-field structure"]
        P2["❌ Inconsistent implementations"]
        P3["❌ Key confusion with fields"]
    end
```

## Proposed Architecture: Pure Keyed Services

```mermaid
graph TB
    subgraph "Coroutine Context Layer"
        CC["CoroutineContext"]
        CC --> KS["KeyedServices Registry"]
    end
    
    subgraph "Keyed Service Types"
        KS --> AUTH["AuthService<br/>Key: AuthKey"]
        KS --> METRICS["MetricsService<br/>Key: MetricsKey"]
        KS --> TRACE["TraceService<br/>Key: TraceKey"]
        KS --> IO["IOService<br/>Key: IOKey"]
        KS --> SESSION["SessionService<br/>Key: SessionKey"]
    end
    
    subgraph "Domain Compositions"
        HTTP["HTTP Handler"]
        HTTP --> AUTH
        HTTP --> METRICS
        HTTP --> SESSION
        
        QUIC["QUIC Stream"]
        QUIC --> IO
        QUIC --> TRACE
        QUIC --> SESSION
        
        CURSOR["Cursor Operations"]
        CURSOR --> IO
        CURSOR --> METRICS
    end
```

## Key-Based Trait Composition

```mermaid
graph LR
    subgraph "Trait Interfaces"
        T1["Traceable"]
        T2["Metered"]
        T3["Authenticated"]
        T4["Sessionable"]
    end
    
    subgraph "Key Implementations"
        K1["TraceKey enables Traceable"]
        K2["MetricsKey enables Metered"]
        K3["AuthKey enables Authenticated"]
        K4["SessionKey enables Sessionable"]
    end
    
    subgraph "Composed Services"
        S1["HttpService<br/>Traceable + Authenticated"]
        S2["QuicService<br/>Metered + Sessionable"]
        S3["CursorService<br/>Traceable + Metered"]
    end
    
    T1 --> K1 --> S1
    T3 --> K3 --> S1
    T2 --> K2 --> S2
    T4 --> K4 --> S2
    T1 --> K1 --> S3
    T2 --> K2 --> S3
```

## Implementation Pattern

```kotlin
// Base keyed service
interface KeyedService : CoroutineContext.Element

// Trait interfaces
interface Traceable {
    suspend fun trace(operation: String)
}

interface Metered {
    suspend fun meter(metric: String, value: Long)
}

// Concrete services with keys
data class TraceService(val tracer: Tracer) : KeyedService, Traceable {
    companion object Key : CoroutineContext.Key<TraceService>
    override val key = Key
    override suspend fun trace(operation: String) = tracer.trace(operation)
}

data class MetricsService(val collector: MetricsCollector) : KeyedService, Metered {
    companion object Key : CoroutineContext.Key<MetricsService>
    override val key = Key
    override suspend fun meter(metric: String, value: Long) = collector.record(metric, value)
}

// Domain usage - compose what you need
suspend fun handleHttpRequest(request: HttpRequest) = coroutineScope {
    val trace = coroutineContext[TraceService.Key] ?: error("No trace service")
    val auth = coroutineContext[AuthService.Key] ?: error("No auth service")
    
    trace.trace("http.request.start")
    auth.authenticate(request.headers)
    // ... handle request
}
```

## Service Discovery Graph

```mermaid
graph TD
    subgraph "CoroutineScope"
        CS["currentCoroutineContext()"]
    end
    
    subgraph "Key Lookup"
        CS --> L1["context[TraceService.Key]"]
        CS --> L2["context[MetricsService.Key]"]
        CS --> L3["context[IOService.Key]"]
    end
    
    subgraph "Service Resolution"
        L1 --> S1["TraceService?"]
        L2 --> S2["MetricsService?"]
        L3 --> S3["IOService?"]
    end
    
    subgraph "Trait Activation"
        S1 --> T1["✓ Traceable"]
        S2 --> T2["✓ Metered"]
        S3 --> T3["✓ IOCapable"]
    end
```

## Migration Path

1. **Remove 4-field requirements** - CCEK is just a key pattern
2. **Define trait interfaces** - What capabilities do services provide?
3. **Create keyed services** - One key per service, implementing relevant traits
4. **Compose at use sites** - Pull only needed services from context

## Benefits

- **No forced structure** - Services define their own shape
- **Trait-based composition** - Mix capabilities as needed
- **Type-safe lookup** - Keys guarantee correct service types
- **Clear dependencies** - Explicit service requirements at use sites