# Nexus System Interaction Diagrams

## 1. Subsumption Hierarchy Flow

```mermaid
graph TD
    EVO[Evolution Level<br/>DGM-style self-improvement] --> AG[Agent Level<br/>HybridIntelligence]
    AG --> DEV[Development Level<br/>Code generation & execution]
    DEV --> CCEK[CCEK Level<br/>Context management]
    
    EVO -.->|Fitness feedback| TENSOR[NexusTensorCore<br/>4D tensor space]
    AG -.->|Learning patterns| TENSOR
    DEV -.->|Action outcomes| TENSOR
    CCEK -.->|Environment state| TENSOR
    
    TENSOR -->|Vectorized ops| EVO
    TENSOR -->|Pattern correlation| AG
    TENSOR -->|Performance metrics| DEV
    TENSOR -->|Context synthesis| CCEK
```

## 2. Request Processing Pipeline

```mermaid
sequenceDiagram
    participant Human
    participant HI as HybridIntelligence
    participant UR as UniversalReflector
    participant NP as NexusProviders
    participant EA as EnvironmentAdapter
    participant TC as TensorCore
    
    Human->>HI: Submit request
    HI->>UR: Scan environment
    UR->>TC: Store observations as tensors
    TC-->>UR: Return context patterns
    UR-->>HI: Environment capabilities
    
    HI->>NP: Generate solutions (multiple providers)
    NP->>NP: Anthropic/OpenAI/Local processing
    NP-->>HI: Solution candidates
    
    HI->>TC: Evaluate via tensor operations
    TC-->>HI: Fitness scores & rankings
    
    HI->>EA: Execute best solution
    EA->>EA: Route to IDE adapter
    EA-->>HI: Execution results
    
    HI->>TC: Store outcomes for learning
    TC->>TC: Update pattern tensors
```

## 3. Multi-Provider Architecture

```mermaid
graph LR
    subgraph "Request Classification"
        REQ[Human Request] --> CLASS[HybridIntelligence<br/>Classifier]
        CLASS --> CODE[CODE_GENERATION]
        CLASS --> PROB[PROBLEM_SOLVING]  
        CLASS --> REF[REFACTORING]
    end
    
    subgraph "Provider Selection"
        CODE --> ANTHRO[Anthropic Claude<br/>Structured reasoning]
        PROB --> OPENAI[OpenAI GPT<br/>Context awareness]
        REF --> LOCAL[Ollama Local<br/>Privacy + Speed]
        CODE --> MOCK[Mock Provider<br/>Testing]
    end
    
    subgraph "Response Processing"
        ANTHRO --> MERGE[Solution Merger]
        OPENAI --> MERGE
        LOCAL --> MERGE
        MOCK --> MERGE
        MERGE --> EVAL[Tensor Evaluation]
    end
```

## 4. Environment Integration Flow

```mermaid
graph TB
    subgraph "IDE Adapters"
        VS[VS Code Adapter<br/>Extension API simulation]
        IJ[IntelliJ Adapter<br/>Build/test integration]
        NEO[Neovim Adapter<br/>RPC communication]
    end
    
    subgraph "EnvironmentAdapter"
        EA[EnvironmentAdapter<br/>Action routing]
        OBS[Change Observation<br/>Flow-based]
        CAP[Capability Aggregation]
    end
    
    subgraph "Core Processing"
        TC[TensorCore<br/>Pattern storage]
        UR[UniversalReflector<br/>Environment scanning]
        HI[HybridIntelligence<br/>Decision making]
    end
    
    VS -->|Actions| EA
    IJ -->|Actions| EA
    NEO -->|Actions| EA
    
    EA -->|Observations| OBS
    OBS -->|Patterns| TC
    TC -->|Context| UR
    UR -->|Capabilities| CAP
    CAP -->|Aggregated| HI
    
    HI -->|Commands| EA
    EA -->|Route by type| VS
    EA -->|Route by type| IJ
    EA -->|Route by type| NEO
```

## 5. Learning & Evolution Cycle

```mermaid
graph TD
    subgraph "Observation Phase"
        ENV[Environment<br/>Changes] --> SCAN[UniversalReflector<br/>Scanning]
        SCAN --> PAT[Pattern<br/>Extraction]
    end
    
    subgraph "Learning Phase"
        PAT --> CORR[Correlation<br/>Analysis]
        CORR --> PRED[Prediction<br/>Generation]
        PRED --> ACT[Action<br/>Planning]
    end
    
    subgraph "Evolution Phase"
        ACT --> GEN[Solution<br/>Generation]
        GEN --> FIT[Fitness<br/>Evaluation]
        FIT --> SEL[Selection &<br/>Mutation]
        SEL --> GEN
    end
    
    subgraph "Execution Phase"
        SEL --> EXEC[Environment<br/>Execution]
        EXEC --> FEED[Feedback<br/>Collection]
        FEED --> ENV
    end
    
    subgraph "Tensor Operations"
        TC[TensorCore<br/>4D Operations]
        PAT -.->|Store| TC
        CORR -.->|Compute| TC
        FIT -.->|Calculate| TC
        FEED -.->|Update| TC
    end
```

## 6. Data Flow Architecture

```mermaid
flowchart LR
    subgraph "Input Layer"
        HUM[Human Requests]
        IDE[IDE Events]
        SYS[System State]
    end
    
    subgraph "Processing Layer"
        CCEK[CCEK Context<br/>Series&lt;Join&lt;String,String&gt;&gt;]
        CAP[Capabilities<br/>Join&lt;String,Series&lt;String&gt;&gt;]
        SOL[Solutions<br/>Series&lt;String&gt;]
    end
    
    subgraph "Tensor Layer"
        T4D[4D Tensor Space<br/>[context,capabilities,patterns,outcomes]]
        ALPHA[α Transforms<br/>Data processing]
        PLAY[▶ Operator<br/>Hot/cold materialization]
    end
    
    subgraph "Output Layer"
        ACT[Actions<br/>Join&lt;String,Series&lt;String&gt;&gt;]
        OUT[Outcomes<br/>Series&lt;String&gt;]
        LEARN[Learning Updates<br/>Join&lt;String,String&gt;]
    end
    
    HUM --> CCEK
    IDE --> CAP
    SYS --> SOL
    
    CCEK -->|j operator| T4D
    CAP -->|j operator| T4D
    SOL -->|j operator| T4D
    
    T4D --> ALPHA
    ALPHA --> PLAY
    PLAY --> ACT
    
    ACT --> OUT
    OUT --> LEARN
    LEARN -.->|Feedback| T4D
```

## 7. TrikeShed Type System Flow

```mermaid
graph TB
    subgraph "Core Types"
        JOIN["Join&lt;A,B&gt;<br/>a j b"]
        SERIES["Series&lt;T&gt;<br/>Join&lt;Int,(Int)-&gt;T&gt;"]
        TENSOR["Tensor&lt;T&gt;<br/>Join&lt;IntArray,(IntArray)-&gt;T&gt;"]
    end
    
    subgraph "Operations"
        ALPHA["α Transform<br/>series.α { transform }"]
        PLAY["▶ Operator<br/>series ▶ (materialization)"]
        INLINE["@JvmInline value class<br/>Zero-cost abstractions"]
    end
    
    subgraph "Nexus Types"
        ENV["EnvironmentContext<br/>Series&lt;Join&lt;String,String&gt;&gt;"]
        CAP["Capability<br/>Join&lt;String,Series&lt;String&gt;&gt;"]
        PROB["Problem<br/>Series&lt;String&gt;"]
        SOL["Solution<br/>Series&lt;String&gt;"]
        ACT["Action<br/>Join&lt;String,Series&lt;String&gt;&gt;"]
    end
    
    JOIN --> SERIES
    SERIES --> TENSOR
    
    SERIES --> ALPHA
    SERIES --> PLAY
    
    JOIN --> ENV
    JOIN --> CAP
    SERIES --> PROB
    SERIES --> SOL
    JOIN --> ACT
    
    ALPHA --> ENV
    ALPHA --> CAP
    ALPHA --> PROB
    ALPHA --> SOL
    ALPHA --> ACT
```

## Key Interaction Patterns

### 1. Subsumption Control Flow
- **Higher levels** set goals and strategies
- **Lower levels** handle execution details
- **Tensor operations** provide cross-level communication
- **Feedback loops** enable learning at all levels

### 2. Provider Orchestration
- **Request classification** determines provider selection
- **Multiple providers** generate diverse solutions in parallel
- **Tensor evaluation** ranks solutions objectively
- **Best solution** selected for execution

### 3. Environment Integration
- **Universal adapters** provide consistent interface
- **Flow-based observation** captures real-time changes
- **Capability aggregation** synthesizes environment state
- **Action routing** distributes commands to appropriate tools

### 4. Learning Pipeline
- **Pattern extraction** from environment observations
- **Correlation analysis** via tensor operations
- **Prediction generation** based on learned patterns
- **Action planning** using predicted outcomes

### 5. Evolution Mechanism
- **Solution generation** creates candidate pool
- **Fitness evaluation** via tensor calculations
- **Selection and mutation** based on performance
- **Feedback integration** improves future generations