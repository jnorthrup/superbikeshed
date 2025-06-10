# CCEK Context Keys and Typealias Linkage Documentation

## Core Context Key Architecture

```mermaid
graph TB
    subgraph "Foundation Types"
        Join["Join&lt;A,B&gt;<br/>Core composition operator"]
        Series["Series&lt;T&gt; = Join&lt;Int, (Int) → T&gt;<br/>Columnar data structure"]
        Tensor["Tensor&lt;T&gt; = Join&lt;IntArray, (IntArray) → T&gt;<br/>Multi-dimensional processing"]
    end
    
    subgraph "CCEK Service Keys"
        VulkanKey["VulkanService.Key<br/>GPU compute context"]
        NioKey["NioService.Key<br/>Platform I/O context"]
        AsyncIoKey["AsyncIoEngine.Key<br/>Async operations context"]
        TlsKey["TlsServiceKey<br/>TLS security context"]
        QuicKey["QuicOperationContext.Key<br/>QUIC protocol context"]
        DispatcherKey["DispatcherRegistry.Key<br/>Method dispatch context"]
        MemoryKey["MemorySlabManagerService.Key<br/>Memory management context"]
    end
    
    subgraph "CCEK Hierarchical Contexts"
        CCEKBase["CCEKContext<br/>= Join&lt;Join&lt;Context, Config&gt;, Join&lt;Env, Knowledge&gt;&gt;"]
        DevContext["DevelopmentContext<br/>= Join&lt;CCEKContext, CodebaseContext&gt;"]
        AgentContext["AgentContext<br/>= Join&lt;DevelopmentContext, LearningContext&gt;"]
        EvoContext["EvolutionContext<br/>= Join&lt;AgentContext, AdaptationContext&gt;"]
        NexusContext["CCEKNexus<br/>= Join&lt;EvolutionContext, CCEKOperations&gt;"]
    end
    
    subgraph "Application Domain Types"
        Problem["Problem = Series&lt;String&gt;"]
        Solution["Solution = Series&lt;String&gt;"]
        Action["Action = Join&lt;String, Series&lt;String&gt;&gt;"]
        Workflow["Workflow = Series&lt;Action&gt;"]
        Capability["Capability = Join&lt;String, Series&lt;String&gt;&gt;"]
        ProjectCtx["ProjectContext = Series&lt;Join&lt;String, String&gt;&gt;"]
    end
    
    subgraph "Legacy Compatibility"
        Pai2["Pai2&lt;A,B&gt; = Join&lt;A,B&gt;<br/>Legacy Pair compatibility"]
        Vect0r["Vect0r&lt;T&gt; = Series&lt;T&gt;<br/>Legacy Vector compatibility"]
        Twin["Twin&lt;T&gt; = Join&lt;T,T&gt;<br/>Paired values"]
    end
    
    %% Foundation relationships
    Join --> Series
    Join --> Tensor
    Join --> Twin
    Join --> Pai2
    Series --> Vect0r
    
    %% CCEK hierarchy relationships
    Join --> CCEKBase
    CCEKBase --> DevContext
    DevContext --> AgentContext
    AgentContext --> EvoContext
    EvoContext --> NexusContext
    
    %% Application domain relationships
    Series --> Problem
    Series --> Solution
    Join --> Action
    Series --> Workflow
    Join --> Capability
    Series --> ProjectCtx
    
    %% Context key relationships (dotted lines for service injection)
    VulkanKey -.-> VulkanService[VulkanService Implementation]
    NioKey -.-> NioService[NioService Implementation]
    AsyncIoKey -.-> AsyncIoEngine[AsyncIoEngine Implementation]
    TlsKey -.-> TlsService[TlsService Implementation]
    QuicKey -.-> QuicOperation[QuicOperationContext Implementation]
    DispatcherKey -.-> DispatcherReg[DispatcherRegistry Implementation]
    MemoryKey -.-> MemoryManager[MemorySlabManagerService Implementation]
    
    classDef foundationType fill:#e1f5fe
    classDef ccekType fill:#f3e5f5
    classDef appType fill:#e8f5e8
    classDef legacyType fill:#fff3e0
    classDef serviceType fill:#fce4ec
    
    class Join,Series,Tensor foundationType
    class VulkanKey,NioKey,AsyncIoKey,TlsKey,QuicKey,DispatcherKey,MemoryKey ccekType
    class Problem,Solution,Action,Workflow,Capability,ProjectCtx appType
    class Pai2,Vect0r,Twin legacyType
    class VulkanService,NioService,AsyncIoEngine,TlsService,QuicOperation,DispatcherReg,MemoryManager serviceType
```

## Cross-Module Dependencies

```mermaid
graph LR
    subgraph "trikeshed-core (Foundation)"
        CoreTypes["• Series&lt;T&gt;<br/>• Join&lt;A,B&gt;<br/>• j operator<br/>• α transforms<br/>• ▶ gateway"]
        CoreServices["• VulkanService.Key<br/>• NioService.Key<br/>• AsyncIoEngine.Key"]
    end
    
    subgraph "Review (Evolution)"
        EvolutionTypes["• QuicOperationContext.Key<br/>• TlsServiceKey<br/>• MemorySlabManagerService.Key<br/>• DispatcherRegistry.Key"]
        CCEK["• SpecializedQuicContextKey<br/>• Hierarchical composition<br/>• ConcurrentMethodDispatcher"]
    end
    
    subgraph "nexus (Intelligence)"
        NexusTypes["• CCEKContext<br/>• DevelopmentContext<br/>• AgentContext<br/>• EvolutionContext<br/>• Problem/Solution/Action"]
    end
    
    subgraph "k2script (Tooling)"
        ScriptTypes["• Tensor&lt;T&gt;<br/>• Pai2&lt;A,B&gt; (legacy)<br/>• Vect0r&lt;T&gt; (legacy)"]
    end
    
    subgraph "Bao-Cline (Integration)"
        TaskTypes["• TaskHistory<br/>• ProviderConfig<br/>• KotlinExtensionState"]
    end
    
    CoreTypes --> EvolutionTypes
    CoreTypes --> NexusTypes
    CoreTypes --> ScriptTypes
    CoreTypes --> TaskTypes
    CoreServices --> EvolutionTypes
    EvolutionTypes --> NexusTypes
    CCEK --> NexusTypes
```

## Service Context Architecture

```mermaid
graph TD
    subgraph "Platform Abstraction Layer"
        ExpectActual["expect/actual pattern"]
        JVM["JVM Platform<br/>• ActualJvmVulkan<br/>• ActualJvmAsyncIoEngine"]
        Linux["Linux Platform<br/>• ActualLinuxVulkan<br/>• ActualLinuxAsyncIoEngine (uring)"]
        Native["Native Platform<br/>• ActualNativeKqueueAsyncIoEngine"]
    end
    
    subgraph "Service Key Injection"
        ContextKey["CoroutineContext.Key&lt;T&gt;"]
        ServiceLookup["Context[ServiceKey]"]
        ServiceInstance["Service Implementation"]
    end
    
    subgraph "CCEK Service Categories"
        Compute["Compute Services<br/>• VulkanService<br/>• AsyncVulkanComputeService"]
        IO["I/O Services<br/>• NioService<br/>• AsyncIoEngine"]
        Network["Network Services<br/>• TlsService<br/>• QuicOperationContext"]
        Memory["Memory Services<br/>• MemorySlabManagerService"]
        Dispatch["Dispatch Services<br/>• DispatcherRegistry"]
    end
    
    ExpectActual --> JVM
    ExpectActual --> Linux
    ExpectActual --> Native
    
    ContextKey --> ServiceLookup
    ServiceLookup --> ServiceInstance
    
    ServiceInstance --> Compute
    ServiceInstance --> IO
    ServiceInstance --> Network
    ServiceInstance --> Memory
    ServiceInstance --> Dispatch
```

## TrikeShed Type System Flow

```mermaid
graph LR
    subgraph "Core Operators"
        JOperator["j operator<br/>a j b → Join&lt;A,B&gt;"]
        AlphaOperator["α operator<br/>series.α { transform }"]
        PlayOperator["▶ operator<br/>series ▶ (materialization)"]
    end
    
    subgraph "Data Flow Pipeline"
        Input["Raw Data"]
        Series1["Series&lt;T&gt;<br/>Columnar structure"]
        Transform["Transform via α"]
        Materialize["Materialize via ▶"]
        Output["Standard Collections"]
    end
    
    subgraph "Composition Pipeline"
        DataA["Data A"]
        DataB["Data B"]
        Compose["Compose via j"]
        JoinAB["Join&lt;A,B&gt;"]
        Extract["Extract .a, .b"]
        Components["Component A, Component B"]
    end
    
    Input --> Series1
    Series1 --> Transform
    Transform --> Materialize
    Materialize --> Output
    
    DataA --> Compose
    DataB --> Compose
    Compose --> JoinAB
    JoinAB --> Extract
    Extract --> Components
    
    JOperator -.-> Compose
    AlphaOperator -.-> Transform
    PlayOperator -.-> Materialize
```

## Key Design Principles

### 1. CCEK Context Management
- **Context**: Operational state and scope awareness
- **Configuration**: System settings and behavioral parameters  
- **Environment**: Platform capabilities and tool integrations
- **Knowledge**: Learned patterns and historical insights

### 2. Hierarchical Composition
```kotlin
CCEKContext = Join<Join<Context, Configuration>, Join<Environment, Knowledge>>
DevelopmentContext = Join<CCEKContext, CodebaseContext>
AgentContext = Join<DevelopmentContext, LearningContext>
EvolutionContext = Join<AgentContext, AdaptationContext>
```

### 3. Service Key Pattern
```kotlin
companion object Key : CoroutineContext.Key<ServiceType>
```
Enables dependency injection through coroutine context lookup.

### 4. Zero-Cost Abstractions
- `@JvmInline value class` wrappers
- Compile-time type safety with runtime erasure
- TrikeShed operators (`j`, `α`, `▶`) as inline functions

### 5. Platform Abstraction
- expect/actual multiplatform pattern
- Platform-specific CCEK service implementations
- Unified service key interface across platforms

## Usage Patterns

### Context Service Lookup
```kotlin
suspend fun useService() {
    val vulkan = coroutineContext[VulkanService.Key]
    val nio = coroutineContext[NioService.Key] 
    val asyncIo = coroutineContext[AsyncIoEngine.Key]
}
```

### CCEK Hierarchy Navigation
```kotlin
val ccek: CCEKContext = context.extractCCEK()
val development: DevelopmentContext = ccek j codebaseContext
val agent: AgentContext = development j learningContext
val evolution: EvolutionContext = agent j adaptationContext
```

### TrikeShed Data Processing
```kotlin
val problems: Problem = Series.of("issue1", "issue2", "issue3")
val solutions: Series<Solution> = problems.α { problem -> 
    generateSolution(problem) 
}
val actions: Series<Action> = solutions.α { solution ->
    solution j extractSteps(solution)
}
```