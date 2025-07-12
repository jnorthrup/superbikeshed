# ADR-006: Unified Architectural Framework for Human Expert Alignment

## Status
Accepted

## Context
The codebase contains multiple architectural patterns from diverse sources (columnar, TrikeShed, texasEnum, relaxfactory, bbcursive) that need synthesis into a unified framework. This framework must align Cursor's planning and rewards with human architectural expertise to create a "compass" that guides AI toward architectural excellence.

## Decision
Establish a unified architectural framework that synthesizes the best patterns from all sources into a coherent system that Cursor can use for planning and rewards. This framework will serve as the architectural "compass" for AI-assisted development.

## Consequences

### Positive
- **Unified Architectural Language**: Single framework for all architectural decisions
- **Human Expert Alignment**: Rewards system that mirrors human architectural expertise
- **Cursor Planning Integration**: Clear planning phases with architectural rewards
- **Performance Optimization**: SIMD and columnar patterns for high-performance systems
- **Type Safety**: Strong type system patterns prevent architectural violations
- **Composability**: Metaseries patterns enable scalable architectural solutions

### Negative
- **Complexity**: Requires understanding of multiple architectural paradigms
- **Learning Curve**: Steep initial learning curve for the unified framework
- **Tooling**: Requires specialized tooling to enforce architectural constraints

## Unified Architectural Framework

### 1. **Core Architectural Principles**

#### 1.1 Metaseries Over Instances (from TrikeShed)
```kotlin
// ❌ WRONG - Fighting each case individually
fun processUserData(user: User) {
    val name = user.name.toUpperCase()
    val email = user.email.toLowerCase()
    // ... fighting each field
}

// ✅ RIGHT - Metaseries approach
fun processDataSeries<T>(data: Indexed<T>, transform: (T) -> T): Indexed<T> {
    return Indexed(data.a) { transform(data.b(it)) }
}
```

#### 1.2 Columnar Performance (from Columnar)
```kotlin
// Columnar data processing for performance
typealias Cursor = Indexed<RowVec>
typealias RowVec = Indexed<Any?>
typealias ColumnMeta = Join<String, KClassifier>

// Coordinate-based access for wireproto efficiency
data class CursorCoordinate(
    val row: Int,
    val col: Int,
    val offset: Long
)
```

#### 1.3 SIMD Integration (from BBCursive)
```kotlin
// Platform-specific SIMD acceleration
expect class SimdStrategy {
    fun scanStructural(data: ByteArray): IntArray
    fun scanQuotes(data: ByteArray): IntArray
    fun getCapabilities(): SimdCapabilities
}

// Apple Silicon: NEON/AMX
actual class SimdStrategy {
    actual fun scanStructural(data: ByteArray): IntArray {
        return nativeScanStructural(data) // C interop
    }
}
```

#### 1.4 Factory Patterns (from RelaxFactory)
```kotlin
// Document factory with schema evolution
class DocumentFactory(
    private val template: DocumentTemplate,
    private val api: RelaxFactoryAPI
) {
    suspend fun create(data: Map<String, JsonElement>): CouchDocument {
        // Validate required fields
        template.requiredFields.forEach { field ->
            if (!data.containsKey(field)) {
                throw IllegalArgumentException("Required field '$field' is missing")
            }
        }
        // Create with auto-generated ID
        return api.createDocument(document)
    }
}
```

#### 1.5 Enum-Driven Architecture (from texasEnum patterns)
```kotlin
// Type-safe enum hierarchies for architectural decisions
sealed class ArchitecturalDecision {
    data class PerformanceOptimization(val strategy: PerformanceStrategy) : ArchitecturalDecision()
    data class TypeSystemChoice(val pattern: TypeSystemPattern) : ArchitecturalDecision()
    data class PlatformSpecific(val target: PlatformTarget) : ArchitecturalDecision()
    data class MetaseriesDesign(val series: SeriesType) : ArchitecturalDecision()
}

enum class PerformanceStrategy {
    SIMD_ACCELERATION,
    COLUMNAR_PROCESSING,
    ZERO_COPY_OPERATIONS,
    MEMORY_MAPPING
}
```

### 2. **Architectural Planning Phases**

#### 2.1 Analysis Phase
```kotlin
data class ArchitecturalAnalysis(
    val problemSpace: ProblemSpace,
    val constraints: Indexed<ArchitecturalConstraint>,
    val opportunities: Indexed<OptimizationOpportunity>,
    val risks: Indexed<ArchitecturalRisk>
)

// Reward: +15 points for comprehensive analysis
```

#### 2.2 Design Phase
```kotlin
data class ArchitecturalDesign(
    val patterns: Indexed<ArchitecturalPattern>,
    val typeSystem: TypeSystemDesign,
    val performance: PerformanceDesign,
    val composability: ComposabilityDesign
)

// Reward: +20 points for metaseries design
// Reward: +10 points for type safety
// Reward: +8 points for performance optimization
```

#### 2.3 Implementation Phase
```kotlin
data class ArchitecturalImplementation(
    val code: Indexed<CodeComponent>,
    val tests: Indexed<TestComponent>,
    val documentation: Indexed<DocumentationComponent>,
    val validation: Indexed<ValidationComponent>
)

// Reward: +12 points for ADR compliance
// Reward: +8 points for test coverage
// Reward: +5 points for documentation
```

#### 2.4 Validation Phase
```kotlin
data class ArchitecturalValidation(
    val performance: PerformanceMetrics,
    val typeSafety: TypeSafetyReport,
    val composability: ComposabilityTest,
    val maintainability: MaintainabilityScore
)

// Reward: +15 points for performance targets met
// Reward: +10 points for type safety maintained
// Reward: +8 points for composability verified
```

### 3. **Rewards Model Framework**

#### 3.1 Architectural Compliance Rewards
```kotlin
data class ArchitecturalRewards(
    val adrAdherence: Int = 0,           // +10 points
    val typeSystemCompliance: Int = 0,   // +8 points
    val performancePatterns: Int = 0,    // +7 points
    val metaseriesDesign: Int = 0,       // +9 points
    val platformOptimization: Int = 0    // +6 points
)
```

#### 3.2 Team Coordination Rewards
```kotlin
data class TeamRewards(
    val architecturalConsensus: Int = 0,  // +12 points
    val knowledgePreservation: Int = 0,   // +8 points
    val patternReuse: Int = 0,           // +6 points
    val architecturalMentoring: Int = 0   // +10 points
)
```

#### 3.3 Planning Acknowledgment Rewards
```kotlin
data class PlanningRewards(
    val architecturalForesight: Int = 0,  // +15 points
    val debtManagement: Int = 0,          // +8 points
    val evolutionPlanning: Int = 0,       // +12 points
    val constraintAnticipation: Int = 0   // +10 points
)
```

### 4. **Cursor Integration Points**

#### 4.1 Architectural Context Recognition
```kotlin
// Cursor reads architectural context
data class ArchitecturalContext(
    val adrs: Indexed<ArchitecturalDecisionRecord>,
    val patterns: Indexed<ArchitecturalPattern>,
    val constraints: Indexed<ArchitecturalConstraint>,
    val rewards: ArchitecturalRewards
)
```

#### 4.2 Planning Phase Integration
```kotlin
// Cursor acknowledges planning phases
enum class PlanningPhase {
    ANALYSIS,      // Understand problem space
    DESIGN,        // Create architectural solution
    IMPLEMENTATION, // Build with architectural awareness
    VALIDATION     // Verify architectural compliance
}

// Reward for each phase completion
fun calculatePhaseReward(phase: PlanningPhase): Int = when (phase) {
    PlanningPhase.ANALYSIS -> 15
    PlanningPhase.DESIGN -> 20
    PlanningPhase.IMPLEMENTATION -> 12
    PlanningPhase.VALIDATION -> 15
}
```

#### 4.3 Team Coordination Integration
```kotlin
// Cursor recognizes team-level architectural decisions
data class TeamArchitecturalDecision(
    val decision: ArchitecturalDecision,
    val participants: Indexed<TeamMember>,
    val consensus: ConsensusLevel,
    val impact: ArchitecturalImpact
)

// Reward for team coordination
fun calculateTeamReward(decision: TeamArchitecturalDecision): Int {
    return when (decision.consensus) {
        ConsensusLevel.UNANIMOUS -> 12
        ConsensusLevel.SUPERMAJORITY -> 10
        ConsensusLevel.SIMPLE_MAJORITY -> 8
        ConsensusLevel.PLURALITY -> 6
    }
}
```

### 5. **Implementation Strategy**

#### 5.1 Enhanced .cursorrules
```markdown
## Architectural Rewards Framework
- ADR adherence: +10 points
- Type system compliance: +8 points
- Metaseries design: +9 points
- Performance optimization: +7 points
- Team coordination: +12 points
- Planning acknowledgment: +15 points
```

#### 5.2 Architectural Score Tracking
```kotlin
data class ArchitecturalScore(
    val compliance: ArchitecturalRewards,
    val team: TeamRewards,
    val planning: PlanningRewards,
    val total: Int = compliance.adrAdherence + 
                    compliance.typeSystemCompliance +
                    compliance.performancePatterns +
                    compliance.metaseriesDesign +
                    compliance.platformOptimization +
                    team.architecturalConsensus +
                    team.knowledgePreservation +
                    team.patternReuse +
                    team.architecturalMentoring +
                    planning.architecturalForesight +
                    planning.debtManagement +
                    planning.evolutionPlanning +
                    planning.constraintAnticipation
)
```

#### 5.3 Planning Acknowledgment System
```kotlin
// Explicit planning phases with rewards
enum class PlanningPhase {
    ANALYSIS,      // +15 points
    DESIGN,        // +20 points
    IMPLEMENTATION, // +12 points
    VALIDATION     // +15 points
}

// Cursor acknowledges each phase
fun acknowledgePlanningPhase(phase: PlanningPhase): Int {
    return when (phase) {
        PlanningPhase.ANALYSIS -> 15
        PlanningPhase.DESIGN -> 20
        PlanningPhase.IMPLEMENTATION -> 12
        PlanningPhase.VALIDATION -> 15
    }
}
```

## Benefits of This Framework

1. **Compass Alignment**: Clear rewards guide Cursor toward architectural excellence
2. **Human Expert Reinforcement**: Rewards align with human architectural expertise
3. **Team Coordination**: Encourages architectural consensus and knowledge sharing
4. **Long-term Planning**: Rewards architectural foresight and debt management
5. **Performance Focus**: SIMD and columnar patterns ensure high-performance systems
6. **Type Safety**: Strong type system prevents architectural violations
7. **Composability**: Metaseries patterns enable scalable solutions

## Related
- Metaseries patterns: ADR-003
- SIMD strategy: ADR-001
- Cursor coordinate metaseries: ADR-004
- Single-line Kotlin architecture: ADR-005
- Cursor metaseries patterns: `.cursor/anchors/cursor-metaseries-patterns.md`
- Type system patterns: `.cursor/anchors/type-system-patterns.md` 