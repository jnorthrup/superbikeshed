# Nexus ↔ IntelliJ Platform PSI Integration Architecture

## Executive Summary

This document maps the complete integration pathway between Nexus Universal Development Agent and IntelliJ Platform's Program Structure Interface (PSI) APIs, providing deep semantic code analysis capabilities that transcend file-level operations.

## Integration Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NEXUS UNIVERSAL AGENT                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                        TrikeShed Core (Kotlin MP)                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │ Series<T>/Join  │  │ Ontological     │  │ Tensor-First Columnar       │ │
│  │ Operations      │  │ Typealiases     │  │ Processing                  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                           ┌────────▼────────┐
                           │  PSI BRIDGE     │
                           │  ADAPTER        │
                           └────────┬────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                      INTELLIJ PLATFORM PSI LAYER                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │ Core PSI APIs  │  │ Kotlin Analysis │  │ New K2 Analysis API         │ │
│  │ • PsiElement   │  │ • BindingContext│  │ • KtAnalysisSession         │ │
│  │ • PsiFile      │  │ • DescriptorUtils│ │ • KtSymbol                  │ │
│  │ • PsiManager   │  │ • ResolutionFacade│ │ • KtType                   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                         INTEGRATION PATHWAYS                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │ Plugin SDK      │  │ Language Server │  │ Compiler Plugin             │ │
│  │ Integration     │  │ Protocol        │  │ Integration                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

## TrikeShed ↔ PSI Type System Mapping

### Core Type Transformations

```kotlin
// TrikeShed Domain Types → PSI Element Mappings
@JvmInline value class PsiElementRef(val element: PsiElement)
@JvmInline value class KtElementRef(val element: KtElement)  
@JvmInline value class SymbolRef(val symbol: KtSymbol)

typealias PsiElementSeries = Series<PsiElementRef>
typealias KtElementSeries = Series<KtElementRef>
typealias SymbolSeries = Series<SymbolRef>

// Core Bridge Architecture
typealias CodeStructure = Join<PsiElementSeries, SymbolSeries>
typealias SemanticContext = Join<BindingContext, ModuleDescriptor>  
typealias AnalysisResult = Join<CodeStructure, SemanticContext>
```

### TrikeShed Series Operations on PSI Elements

```kotlin
// Transform PSI tree into TrikeShed Series for efficient processing
fun PsiFile.toTrikeShedSeries(): PsiElementSeries = 
    PsiTreeUtil.collectElements(this) { true }
        .let { elements -> Series.from(elements.map { PsiElementRef(it) }) }

// Apply α transformations for semantic analysis
fun PsiElementSeries.analyzeSemantics(): AnalysisResult = this
    .α { ref -> ref.element.resolveSymbol() }  // Transform to symbols
    .α { symbol -> symbol.getTypeInfo() }      // Extract type information  
    .α { typeInfo -> typeInfo.analyze() }      // Perform semantic analysis
    play // Materialize results only when needed

// Join operations across multiple analysis dimensions
fun analyzeFull(file: KtFile): AnalysisResult = 
    file.toTrikeShedSeries() j 
    file.getBindingContext() j
    file.getModuleDescriptor()
    α { (elements, binding, module) ->
        FullAnalysis(elements, binding, module)
    }
```

## IntelliJ Platform API Integration Points

### 1. Core PSI APIs

```kotlin
interface NexusPsiAdapter {
    // Bridge to TrikeShed Series operations
    fun PsiElement.toSeries(): PsiElementSeries
    fun PsiFile.getAllElements(): PsiElementSeries
    fun PsiElement.getChildren(): PsiElementSeries
    fun PsiElement.findUsages(): PsiElementSeries
    
    // Semantic queries using TrikeShed patterns
    suspend fun findElementsByType(type: KClass<out PsiElement>): PsiElementSeries
    suspend fun resolveReferences(): Join<PsiElementSeries, PsiElementSeries>
    suspend fun analyzeDataFlow(): Join<PsiElementSeries, DataFlowInfo>
}

class PsiTrikeShedBridge : NexusPsiAdapter {
    override fun PsiElement.toSeries(): PsiElementSeries = 
        Series.singleton(PsiElementRef(this))
        
    override fun PsiFile.getAllElements(): PsiElementSeries =
        PsiTreeUtil.collectElements(this) { true }
            .let { Series.from(it.map(::PsiElementRef)) }
            
    override suspend fun findElementsByType(type: KClass<out PsiElement>): PsiElementSeries =
        getAllElements()
            .α { ref -> ref.element }
            .filter { it::class == type }
            .map { PsiElementRef(it) }
            .let { Series.from(it) }
}
```

### 2. Kotlin Analysis APIs

```kotlin
// Kotlin-specific semantic analysis  
interface NexusKotlinAnalyzer {
    suspend fun analyzeFile(file: KtFile): KotlinAnalysisResult
    suspend fun resolveTypes(): Join<KtElementSeries, TypeSeries>
    suspend fun findDeclarations(): Join<KtElementSeries, DeclarationSeries>
    suspend fun analyzeDependencies(): DependencyGraph
}

@JvmInline value class KotlinType(val ktType: KtType)
@JvmInline value class Declaration(val declaration: KtDeclaration)

typealias TypeSeries = Series<KotlinType>
typealias DeclarationSeries = Series<Declaration>
typealias DependencyGraph = Join<DeclarationSeries, DeclarationSeries>

class KotlinAnalysisBridge(
    private val analysisSession: KtAnalysisSession
) : NexusKotlinAnalyzer {
    
    override suspend fun analyzeFile(file: KtFile): KotlinAnalysisResult = 
        analysisSession.analyze(file) {
            val declarations = file.declarations.toSeries()
            val types = declarations.α { it.getReturnKtType() }
            val symbols = declarations.α { it.getSymbol() }
            
            KotlinAnalysisResult(
                structure = declarations j types j symbols
            )
        }
}
```

### 3. New K2 Analysis API Integration  

```kotlin
// K2 (FIR-based) Analysis Integration
interface NexusK2Analyzer {
    suspend fun createAnalysisSession(): KtAnalysisSession
    suspend fun analyzeSymbols(): SymbolAnalysisResult
    suspend fun performTypeInference(): TypeInferenceResult
    suspend fun analyzeCrossModuleDependencies(): ModuleDependencyResult
}

// K2 Symbol Bridge to TrikeShed
@JvmInline value class K2Symbol(val symbol: KtSymbol)
@JvmInline value class K2Type(val type: KtType)
@JvmInline value class K2Module(val module: KtModule)

typealias K2SymbolSeries = Series<K2Symbol>
typealias K2TypeSeries = Series<K2Type>  
typealias K2ModuleSeries = Series<K2Module>

class K2TrikeShedBridge : NexusK2Analyzer {
    
    override suspend fun analyzeSymbols(): SymbolAnalysisResult = 
        ktAnalysisSession {
            val allSymbols = getAllSymbols().toTrikeShedSeries()
            val symbolTypes = allSymbols.α { it.symbol.returnType }
            val symbolScopes = allSymbols.α { it.symbol.getContainingScope() }
            
            SymbolAnalysisResult(
                symbols = allSymbols j symbolTypes j symbolScopes
            )
        }
        
    private fun Collection<KtSymbol>.toTrikeShedSeries(): K2SymbolSeries =
        Series.from(this.map { K2Symbol(it) })
}
```

## Integration Implementation Pathways

### Pathway 1: IntelliJ Plugin SDK Integration

```kotlin
// Plugin Entry Point
class NexusIntellijPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val nexusService = project.getService(NexusService::class.java)
        val psiAdapter = PsiTrikeShedBridge()
        val kotlinAnalyzer = KotlinAnalysisBridge(project.analysisSession)
        
        nexusService.initialize(
            psiAdapter = psiAdapter,
            kotlinAnalyzer = kotlinAnalyzer,
            project = project
        )
    }
}

// Service Registration  
class NexusService(private val project: Project) {
    private lateinit var nexusAgent: NexusAgent
    
    fun initialize(
        psiAdapter: NexusPsiAdapter,
        kotlinAnalyzer: NexusKotlinAnalyzer,
        project: Project
    ) {
        nexusAgent = NexusAgent.Builder()
            .withPsiAdapter(psiAdapter)
            .withKotlinAnalyzer(kotlinAnalyzer) 
            .withProject(project)
            .build()
    }
    
    suspend fun analyzeCurrentFile(): AnalysisResult {
        val currentFile = getCurrentPsiFile()
        return nexusAgent.performFullAnalysis(currentFile)
    }
}
```

### Pathway 2: Language Server Protocol Bridge

```kotlin
// LSP Server with PSI Backend
class NexusLanguageServer : LanguageServer {
    private val psiManager = PsiManager.getInstance(project)
    private val nexusAgent = NexusAgent()
    
    override fun textDocument(): TextDocumentService = object : TextDocumentService {
        override fun hover(params: HoverParams): CompletableFuture<Hover> {
            return CompletableFuture.supplyAsync {
                val psiFile = getPsiFile(params.textDocument.uri)
                val elementAtPosition = getElementAtPosition(psiFile, params.position)
                
                val analysis = nexusAgent.analyzeElement(elementAtPosition)
                val hoverInfo = analysis.generateHoverInfo()
                
                Hover(hoverInfo)
            }
        }
        
        override fun completion(params: CompletionParams): CompletableFuture<List<CompletionItem>> {
            return CompletableFuture.supplyAsync {
                val psiFile = getPsiFile(params.textDocument.uri)
                val context = buildCompletionContext(psiFile, params.position)
                
                nexusAgent.generateCompletions(context)
                    .play // Materialize to List
                    .map { it.toCompletionItem() }
            }
        }
    }
}
```

### Pathway 3: Kotlin Compiler Plugin Integration

```kotlin
// Compiler Plugin for Deep Analysis
class NexusCompilerPlugin : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(NexusFirExtension())
    }
}

class NexusFirExtension : FirExtensionRegistrar() {
    override fun ExtensionPointName<FirExtension>.register(extension: FirExtension) {
        FirResolvePhase.ANALYSED_DEPENDENCIES.register(NexusAnalysisExtension())
    }
}

class NexusAnalysisExtension : FirExtension {
    override fun processClassFile(firClass: FirClass, session: FirSession) {
        val nexusAnalysis = NexusAgent.analyzeClass(firClass)
        
        // Store analysis results in TrikeShed format
        val classStructure = firClass.toTrikeShedRepresentation()
        val dependencies = firClass.getDependencies().toSeries()
        val analysisResult = classStructure j dependencies
        
        NexusAnalysisStorage.store(firClass.symbol, analysisResult)
    }
}
```

## TrikeShed-Enhanced Semantic Queries

### Complex Code Analysis Operations

```kotlin
// Find all usages across project using TrikeShed operations
suspend fun findAllUsages(symbol: KtSymbol): UsageAnalysisResult {
    val projectFiles = ProjectManager.getAllKtFiles().toSeries()
    
    val usages = projectFiles
        .α { file -> file.findReferences(symbol) }  // Find references in each file
        .α { refs -> refs.analyzeContext() }        // Analyze usage context
        .α { contexts -> contexts.categorize() }    // Categorize usage types
        play // Materialize results
    
    return UsageAnalysisResult(
        directUsages = usages.filter { it.isDirect },
        indirectUsages = usages.filter { it.isIndirect },
        writeUsages = usages.filter { it.isWrite },
        readUsages = usages.filter { it.isRead }
    )
}

// Dependency analysis with transitive closure
suspend fun analyzeDependencies(module: KtModule): DependencyAnalysisResult {
    val directDeps = module.getDirectDependencies().toSeries()
    
    // Use TrikeShed Join to combine multiple analysis dimensions
    val analysisResult = directDeps j 
                        computeTransitiveClosure(directDeps) j
                        analyzeCircularDependencies(directDeps) j
                        computeDependencyMetrics(directDeps)
                        
    return DependencyAnalysisResult(analysisResult)
}

// Type inference and constraint solving
suspend fun inferTypes(expression: KtExpression): TypeInferenceResult = 
    ktAnalysisSession {
        val constraints = collectConstraints(expression).toSeries()
        val solutions = constraints
            .α { constraint -> solveConstraint(constraint) }
            .α { solution -> validateSolution(solution) }
            .α { validated -> rankSolution(validated) }
            
        TypeInferenceResult(
            bestSolution = solutions.maxBy { it.confidence },
            alternativeSolutions = solutions.play.toList()
        )
    }
```

### Advanced Code Transformation Operations

```kotlin
// Refactoring with semantic preservation
suspend fun performSemanticRefactoring(
    target: PsiElement,
    transformation: RefactoringTransformation
): RefactoringResult {
    
    // Pre-analysis using TrikeShed operations
    val preAnalysis = target.toSeries()
        .α { element -> element.analyzeSemantics() }
        .α { semantics -> semantics.extractInvariants() }
        .α { invariants -> invariants.computeConstraints() }
        
    // Apply transformation
    val transformedCode = transformation.apply(target)
    
    // Post-analysis verification  
    val postAnalysis = transformedCode.toSeries()
        .α { element -> element.analyzeSemantics() }
        .α { semantics -> semantics.extractInvariants() }
        .α { invariants -> invariants.computeConstraints() }
        
    // Verify semantic preservation using Join
    val verification = preAnalysis j postAnalysis α { (pre, post) ->
        SemanticEquivalenceChecker.verify(pre, post)
    }
    
    return RefactoringResult(
        transformedCode = transformedCode,
        semanticPreservation = verification.isEquivalent,
        confidence = verification.confidence
    )
}
```

## Integration with Nexus Universal Agent

### Nexus Agent PSI Extensions

```kotlin
// Extend Nexus Agent with PSI capabilities
class NexusAgentWithPSI(
    private val psiAdapter: NexusPsiAdapter,
    private val kotlinAnalyzer: NexusKotlinAnalyzer,
    private val k2Analyzer: NexusK2Analyzer
) : NexusAgent {
    
    // Enhanced environment scanning with PSI
    override suspend fun scanEnvironment(): EnvironmentContext {
        val psiContext = psiAdapter.scanPsiStructure()
        val kotlinContext = kotlinAnalyzer.analyzeProject()
        val symbolContext = k2Analyzer.analyzeSymbols()
        
        return EnvironmentContext(
            baseContext = super.scanEnvironment(),
            psiStructure = psiContext,
            kotlinSemantics = kotlinContext,
            symbolTable = symbolContext
        )
    }
    
    // Enhanced solution generation with semantic awareness
    override suspend fun generateSolutions(problem: Problem): Series<Solution> {
        val semanticContext = analyzeSemanticContext(problem)
        val baseSolutions = super.generateSolutions(problem)
        
        return baseSolutions
            .α { solution -> solution.enhanceWithSemantics(semanticContext) }
            .α { enhanced -> enhanced.validateSemantics() }
            .α { validated -> validated.rankBySemanticFit() }
    }
    
    // Semantic-aware solution evolution
    override suspend fun evolveSolution(
        solution: Solution, 
        feedback: Feedback
    ): Solution {
        val semanticFeedback = interpretSemanticFeedback(feedback)
        val constraints = extractSemanticConstraints(solution)
        
        return solution
            .evolveWithConstraints(constraints)
            .applySemantic­Feedback(semanticFeedback)
            .validateSemanticCoherence()
    }
}
```

### Universal Reflection with PSI Integration

```kotlin
// Enhanced reflection engine with PSI
class PSIEnhancedReflectionEngine : ReflectionEngine {
    
    override suspend fun discoverCapabilities(): Series<Capability> {
        val baseCaps = super.discoverCapabilities()
        val psiCaps = discoverPSICapabilities()
        val semanticCaps = discoverSemanticCapabilities()
        
        return baseCaps + psiCaps + semanticCaps
    }
    
    private suspend fun discoverPSICapabilities(): Series<Capability> =
        Series.from(listOf(
            Capability.PSI_TREE_ANALYSIS,
            Capability.SYMBOL_RESOLUTION,
            Capability.TYPE_INFERENCE,
            Capability.SEMANTIC_REFACTORING,
            Capability.DEPENDENCY_ANALYSIS,
            Capability.USAGE_ANALYSIS,
            Capability.CONSTRAINT_SOLVING
        ))
        
    private suspend fun discoverSemanticCapabilities(): Series<Capability> =
        ktAnalysisSession {
            getAllSymbols()
                .map { symbol -> Capability.fromSymbol(symbol) }
                .let { Series.from(it) }
        }
}
```

## Performance Considerations

### TrikeShed Performance Optimizations for PSI

```kotlin
// Lazy evaluation with Series operations
class LazyPSIAnalysis {
    private val elementCache: MutableMap<PsiElement, AnalysisResult> = mutableMapOf()
    
    fun analyzeElementLazily(element: PsiElement): Lazy<AnalysisResult> = lazy {
        elementCache.getOrPut(element) {
            element.toSeries()
                .α { it.performAnalysis() }    // Only computed when accessed
                .α { it.memoizeResults() }     // Cache for reuse
                .single()                      // Extract single result
        }
    }
    
    // Batch processing for performance
    suspend fun analyzeBatch(elements: Collection<PsiElement>): Series<AnalysisResult> =
        elements.toSeries()
            .chunked(BATCH_SIZE)           // Process in batches
            .α { batch -> processBatch(batch) }  // Parallel processing
            .flatten()                     // Flatten results
}

// Memory-efficient streaming analysis
suspend fun streamAnalyzeProject(project: Project): Flow<AnalysisResult> = flow {
    val allFiles = project.getAllKtFiles().toSeries()
    
    allFiles.play.asFlow()                   // Convert to Flow for streaming
        .map { file -> analyzeFile(file) }  // Transform each file
        .collect { result -> emit(result) } // Emit results as available
}
```

## API Surface Summary

### Core Integration Points

| Component | API Surface | Integration Method |
|-----------|-------------|-------------------|
| **PSI Core** | `PsiElement`, `PsiFile`, `PsiManager` | Direct Plugin SDK |
| **Kotlin Analysis** | `BindingContext`, `DescriptorUtils` | Analysis API |
| **K2 Analysis** | `KtAnalysisSession`, `KtSymbol` | New Analysis API |
| **TrikeShed Bridge** | `Series<T>`, `Join<A,B>`, `α` transforms | Custom Adapters |
| **Nexus Agent** | `NexusAgent`, `EnvironmentContext` | Service Integration |

### Plugin Development Checklist

- [ ] **Plugin SDK Setup**: Configure IntelliJ Platform Plugin development
- [ ] **PSI Bridge Implementation**: Implement `NexusPsiAdapter` interface  
- [ ] **Kotlin Analysis Integration**: Set up `KotlinAnalysisBridge`
- [ ] **K2 API Integration**: Implement `K2TrikeShedBridge`
- [ ] **TrikeShed Adaptation**: Map PSI types to TrikeShed `Series<T>` operations
- [ ] **Service Registration**: Register Nexus services in plugin.xml
- [ ] **Performance Optimization**: Implement lazy evaluation and caching
- [ ] **Testing Infrastructure**: Create PSI-based test fixtures
- [ ] **Documentation**: Document API contracts and usage patterns

### Development Workflow Integration

```kotlin
// Example: Integrated development workflow
suspend fun integratedWorkflow() {
    val nexus = NexusAgentWithPSI(psiAdapter, kotlinAnalyzer, k2Analyzer)
    
    // 1. Environment discovery
    val environment = nexus.scanEnvironment()
    
    // 2. Semantic problem analysis  
    val problem = Problem("Optimize this method for performance")
    val semanticContext = nexus.analyzeSemanticContext(problem)
    
    // 3. Solution generation with PSI awareness
    val solutions = nexus.generateSolutions(problem)
        .α { solution -> solution.validateWithPSI() }
        .α { validated -> validated.rankBySemanticFit() }
    
    // 4. Interactive evolution with human feedback
    val feedback = HumanInterface.getFeedback(solutions)
    val evolvedSolution = nexus.evolveSolution(solutions.best(), feedback)
    
    // 5. Semantic validation and application
    val finalSolution = evolvedSolution.validateSemantics()
    nexus.applySolution(finalSolution)
}
```

---

This integration architecture provides Nexus with the deep semantic understanding required for advanced code analysis, transformation, and generation capabilities while maintaining the performance and elegance of TrikeShed's type system and operations.