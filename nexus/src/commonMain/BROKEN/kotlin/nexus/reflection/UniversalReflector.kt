package nexus.reflection

import nexus.core.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*

/**
 * Universal Reflection Engine
 * 
 * Implements the "universal reflection on everything ever invented" vision.
 * Provides "elbow instruments" for interacting with any development environment.
 * 
 * Key capabilities:
 * - Environment scanning and capability discovery
 * - Cross-platform tool detection and integration
 * - Dynamic adaptation to any development setup
 * - Context synthesis from multiple sources
 */
class UniversalReflector(
    private val systemInspector: SystemInspector,
    private val ideDetector: IDEDetector,
    private val toolScanner: ToolScanner,
    private val projectAnalyzer: ProjectAnalyzer,
    private val capabilityMapper: CapabilityMapper
) {
    
    /**
     * Discover all available capabilities in the current environment
     * This is the core "elbow instruments" functionality
     */
    suspend fun discoverCapabilities(): Series<Capability> {
        return coroutineScope {
            // Parallel discovery of different capability types
            val systemCapabilities = async { discoverSystemCapabilities() }
            val ideCapabilities = async { discoverIDECapabilities() }
            val toolCapabilities = async { discoverToolCapabilities() }
            val languageCapabilities = async { discoverLanguageCapabilities() }
            val frameworkCapabilities = async { discoverFrameworkCapabilities() }
            val networkCapabilities = async { discoverNetworkCapabilities() }
            
            // Combine all capabilities
            systemCapabilities.await() +
            ideCapabilities.await() +
            toolCapabilities.await() +
            languageCapabilities.await() +
            frameworkCapabilities.await() +
            networkCapabilities.await()
        }
    }
    
    /**
     * Synthesize complete project context from all available sources
     */
    suspend fun synthesizeContext(): ProjectContext {
        val environment = scanEnvironment()
        val codebase = analyzeCodebase()
        val tools = analyzeMandatoryTools()
        val preferences = detectUserPreferences()
        
        return ProjectContext(environment, codebase, tools, preferences)
    }
    
    /**
     * Get current real-time context (for ongoing adaptation)
     */
    suspend fun getCurrentContext(): ProjectContext {
        // Lightweight context update - only check what might have changed
        val currentEnvironment = getCurrentEnvironment()
        val currentCodebase = getCurrentCodebaseState()
        val currentTools = getCurrentToolState()
        val currentPreferences = getCurrentPreferences()
        
        return ProjectContext(currentEnvironment, currentCodebase, currentTools, currentPreferences)
    }
    
    /**
     * Get current system state for prediction
     */
    suspend fun getCurrentState(): SystemState {
        return SystemState(
            activeWindows = getActiveWindows(),
            runningProcesses = getRunningProcesses(),
            fileSystemState = getFileSystemState(),
            networkState = getNetworkState(),
            userActivity = getUserActivity()
        )
    }
    
    /**
     * Update context based on observed changes
     */
    suspend fun updateContext(current: ProjectContext, change: Change): ProjectContext {
        return when (change.type) {
            ChangeType.FILE_SYSTEM -> current.copy(
                codebase = projectAnalyzer.updateCodebaseContext(current.codebase, change)
            )
            ChangeType.TOOL_STATE -> current.copy(
                tools = updateToolContext(current.tools, change)
            )
            ChangeType.ENVIRONMENT -> current.copy(
                environment = updateEnvironmentContext(current.environment, change)
            )
            ChangeType.USER_PREFERENCE -> current.copy(
                preferences = updatePreferences(current.preferences, change)
            )
        }
    }
    
    /**
     * Scan complete environment - the foundation of universal reflection
     */
    private suspend fun scanEnvironment(): EnvironmentContext {
        val systemInfo = systemInspector.getSystemInfo()
        val ideInfo = ideDetector.detectCurrentIDE()
        
        return EnvironmentContext(systemInfo, ideInfo)
    }
    
    /**
     * Discover system-level capabilities (OS, hardware, installed software)
     */
    private suspend fun discoverSystemCapabilities(): Series<Capability> {
        return systemInspector.inspectSystem().α { info ->
            when (info.type) {
                SystemInfoType.OS -> createOSCapabilities(info)
                SystemInfoType.HARDWARE -> createHardwareCapabilities(info)
                SystemInfoType.INSTALLED_SOFTWARE -> createSoftwareCapabilities(info)
                SystemInfoType.ENVIRONMENT_VARIABLES -> createEnvCapabilities(info)
                SystemInfoType.PATH_ENTRIES -> createPathCapabilities(info)
            }
        }.flatten()
    }
    
    /**
     * Discover IDE and editor capabilities - the "available windows"
     */
    private suspend fun discoverIDECapabilities(): Series<Capability> {
        val detectedIDEs = ideDetector.detectAllAvailableIDEs()
        
        return detectedIDEs.α { ide ->
            when (ide.type) {
                IDEType.VSCODE -> createVSCodeCapabilities(ide)
                IDEType.INTELLIJ -> createIntelliJCapabilities(ide)
                IDEType.VIM -> createVimCapabilities(ide)
                IDEType.EMACS -> createEmacsCapabilities(ide)
                IDEType.SUBLIME -> createSublimeCapabilities(ide)
                IDEType.ECLIPSE -> createEclipseCapabilities(ide)
                IDEType.UNKNOWN -> createGenericEditorCapabilities(ide)
            }
        }.flatten()
    }
    
    /**
     * Discover development tools (git, build systems, package managers, etc.)
     */
    private suspend fun discoverToolCapabilities(): Series<Capability> {
        return toolScanner.scanForTools().α { tool ->
            when (tool.category) {
                ToolCategory.VERSION_CONTROL -> createVCSCapabilities(tool)
                ToolCategory.BUILD_SYSTEM -> createBuildCapabilities(tool)
                ToolCategory.PACKAGE_MANAGER -> createPackageCapabilities(tool)
                ToolCategory.DATABASE -> createDatabaseCapabilities(tool)
                ToolCategory.CONTAINER -> createContainerCapabilities(tool)
                ToolCategory.CLOUD -> createCloudCapabilities(tool)
                ToolCategory.TESTING -> createTestingCapabilities(tool)
                ToolCategory.DEPLOYMENT -> createDeploymentCapabilities(tool)
            }
        }.flatten()
    }
    
    /**
     * Discover programming language ecosystems
     */
    private suspend fun discoverLanguageCapabilities(): Series<Capability> {
        val languages = detectInstalledLanguages()
        
        return languages.α { language ->
            createLanguageCapabilities(language)
        }.flatten()
    }
    
    /**
     * Discover framework and library capabilities
     */
    private suspend fun discoverFrameworkCapabilities(): Series<Capability> {
        val frameworks = projectAnalyzer.detectFrameworks()
        
        return frameworks.α { framework ->
            createFrameworkCapabilities(framework)
        }.flatten()
    }
    
    /**
     * Discover network and service capabilities
     */
    private suspend fun discoverNetworkCapabilities(): Series<Capability> {
        val networkServices = scanNetworkServices()
        
        return networkServices.α { service ->
            createNetworkCapabilities(service)
        }.flatten()
    }
    
    /**
     * Get "available windows" - all interactive interfaces
     */
    private suspend fun getActiveWindows(): Series<WindowInfo> {
        return systemInspector.getActiveWindows()
    }
    
    /**
     * Detect all running development-related processes
     */
    private suspend fun getRunningProcesses(): Series<ProcessInfo> {
        return systemInspector.getRunningProcesses()
            .α { process -> process.takeIf { it.isDevelopmentRelated } }
            .filterNotNull()
    }
    
    /**
     * Analyze current codebase structure and dependencies
     */
    private suspend fun analyzeCodebase(): CodebaseContext {
        val fileStructure = projectAnalyzer.analyzeFileStructure()
        val dependencyGraph = projectAnalyzer.buildDependencyGraph()
        
        return CodebaseContext(fileStructure, dependencyGraph)
    }
    
    /**
     * Create OS-specific capabilities
     */
    private fun createOSCapabilities(info: SystemInfo): Series<Capability> {
        return when (info.os) {
            "macos" -> Series.of(
                Capability.MACOS_SPOTLIGHT,
                Capability.MACOS_AUTOMATOR,
                Capability.MACOS_SHORTCUTS,
                Capability.UNIX_SHELL
            )
            "linux" -> Series.of(
                Capability.LINUX_PACKAGE_MANAGERS,
                Capability.SYSTEMD,
                Capability.UNIX_SHELL,
                Capability.CONTAINER_RUNTIME
            )
            "windows" -> Series.of(
                Capability.WINDOWS_POWERSHELL,
                Capability.WINDOWS_WSL,
                Capability.WINDOWS_REGISTRY,
                Capability.WINDOWS_SHORTCUTS
            )
            else -> Series.empty()
        }
    }
    
    /**
     * Create IDE-specific "elbow instruments"
     */
    private fun createVSCodeCapabilities(ide: IDEInfo): Series<Capability> {
        return Series.of(
            Capability.VSCODE_EXTENSIONS,
            Capability.VSCODE_TASKS,
            Capability.VSCODE_DEBUG,
            Capability.VSCODE_TERMINAL,
            Capability.VSCODE_SETTINGS_SYNC,
            Capability.VSCODE_COMMAND_PALETTE
        )
    }
    
    private fun createIntelliJCapabilities(ide: IDEInfo): Series<Capability> {
        return Series.of(
            Capability.INTELLIJ_PLUGINS,
            Capability.INTELLIJ_REFACTORING,
            Capability.INTELLIJ_DEBUGGER,
            Capability.INTELLIJ_BUILT_IN_TOOLS,
            Capability.INTELLIJ_VCS_INTEGRATION
        )
    }
    
    /**
     * Map discovered tools to actionable capabilities
     */
    private fun createVCSCapabilities(tool: ToolInfo): Series<Capability> {
        return when (tool.name) {
            "git" -> Series.of(
                Capability.GIT_OPERATIONS,
                Capability.GIT_HOOKS,
                Capability.GIT_BRANCHING,
                Capability.GIT_HISTORY_ANALYSIS
            )
            "svn" -> Series.of(
                Capability.SVN_OPERATIONS,
                Capability.SVN_BRANCHING
            )
            else -> Series.empty()
        }
    }
    
    private fun createBuildCapabilities(tool: ToolInfo): Series<Capability> {
        return when (tool.name) {
            "gradle" -> Series.of(
                Capability.GRADLE_BUILD,
                Capability.GRADLE_TASKS,
                Capability.GRADLE_DEPENDENCIES
            )
            "maven" -> Series.of(
                Capability.MAVEN_BUILD,
                Capability.MAVEN_LIFECYCLE,
                Capability.MAVEN_DEPENDENCIES
            )
            "npm" -> Series.of(
                Capability.NPM_SCRIPTS,
                Capability.NPM_PACKAGES,
                Capability.NODE_ECOSYSTEM
            )
            else -> Series.empty()
        }
    }
}

// Core types for universal reflection
data class SystemState(
    val activeWindows: Series<WindowInfo>,
    val runningProcesses: Series<ProcessInfo>,
    val fileSystemState: FileSystemState,
    val networkState: NetworkState,
    val userActivity: UserActivity
)

data class WindowInfo(
    val title: String,
    val process: String,
    val isActive: Boolean,
    val bounds: WindowBounds
)

data class ProcessInfo(
    val name: String,
    val pid: Int,
    val command: String,
    val isDevelopmentRelated: Boolean
)

enum class SystemInfoType {
    OS, HARDWARE, INSTALLED_SOFTWARE, ENVIRONMENT_VARIABLES, PATH_ENTRIES
}

enum class IDEType {
    VSCODE, INTELLIJ, VIM, EMACS, SUBLIME, ECLIPSE, UNKNOWN
}

enum class ToolCategory {
    VERSION_CONTROL, BUILD_SYSTEM, PACKAGE_MANAGER, DATABASE, 
    CONTAINER, CLOUD, TESTING, DEPLOYMENT
}

enum class Capability {
    // OS Capabilities
    MACOS_SPOTLIGHT, MACOS_AUTOMATOR, MACOS_SHORTCUTS, UNIX_SHELL,
    LINUX_PACKAGE_MANAGERS, SYSTEMD, CONTAINER_RUNTIME,
    WINDOWS_POWERSHELL, WINDOWS_WSL, WINDOWS_REGISTRY, WINDOWS_SHORTCUTS,
    
    // IDE Capabilities  
    VSCODE_EXTENSIONS, VSCODE_TASKS, VSCODE_DEBUG, VSCODE_TERMINAL, 
    VSCODE_SETTINGS_SYNC, VSCODE_COMMAND_PALETTE,
    INTELLIJ_PLUGINS, INTELLIJ_REFACTORING, INTELLIJ_DEBUGGER, 
    INTELLIJ_BUILT_IN_TOOLS, INTELLIJ_VCS_INTEGRATION,
    
    // Tool Capabilities
    GIT_OPERATIONS, GIT_HOOKS, GIT_BRANCHING, GIT_HISTORY_ANALYSIS,
    SVN_OPERATIONS, SVN_BRANCHING,
    GRADLE_BUILD, GRADLE_TASKS, GRADLE_DEPENDENCIES,
    MAVEN_BUILD, MAVEN_LIFECYCLE, MAVEN_DEPENDENCIES,
    NPM_SCRIPTS, NPM_PACKAGES, NODE_ECOSYSTEM
}

// Interfaces for pluggable inspection components
interface SystemInspector {
    suspend fun getSystemInfo(): SystemInfo
    suspend fun inspectSystem(): Series<SystemInfo>
    suspend fun getActiveWindows(): Series<WindowInfo>
    suspend fun getRunningProcesses(): Series<ProcessInfo>
}

interface IDEDetector {
    suspend fun detectCurrentIDE(): IDEInfo
    suspend fun detectAllAvailableIDEs(): Series<IDEInfo>
}

interface ToolScanner {
    suspend fun scanForTools(): Series<ToolInfo>
}

interface ProjectAnalyzer {
    suspend fun analyzeFileStructure(): FileStructure
    suspend fun buildDependencyGraph(): DependencyGraph
    suspend fun detectFrameworks(): Series<FrameworkInfo>
    suspend fun updateCodebaseContext(current: CodebaseContext, change: Change): CodebaseContext
}

// ═══════════════════════════════════════════════════════════════════════════════
// LEARNING AND PATTERN EXTRACTION ENGINE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Pattern Learning System - Extracts patterns from environment observations
 */
class PatternLearner(
    private val observationHistory: MutableSeries<Observation> = mutableSeriesOf(),
    private val patterns: MutableSeries<LearnedPattern> = mutableSeriesOf(),
    private val contextCorrelations: MutableSeries<ContextCorrelation> = mutableSeriesOf()
) {
    
    /**
     * Learn patterns from user behavior and environment changes
     */
    suspend fun learnFromObservation(observation: Observation) {
        observationHistory.add(observation)
        
        // Extract patterns from recent observations
        val recentPatterns = extractPatternsFromHistory()
        patterns.addAll(recentPatterns)
        
        // Learn context correlations
        val correlations = extractContextCorrelations(observation)
        contextCorrelations.addAll(correlations)
        
        // Update existing patterns with new evidence
        updateExistingPatterns(observation)
    }
    
    /**
     * Extract behavioral patterns from observation history
     */
    private fun extractPatternsFromHistory(): Series<LearnedPattern> {
        val recentObservations = observationHistory.takeLast(20)
        val sequencePatterns = extractSequencePatterns(recentObservations)
        val temporalPatterns = extractTemporalPatterns(recentObservations)
        val contextualPatterns = extractContextualPatterns(recentObservations)
        
        return sequencePatterns + temporalPatterns + contextualPatterns
    }
    
    /**
     * Extract action sequence patterns (what happens after what)
     */
    private fun extractSequencePatterns(observations: Series<Observation>): Series<LearnedPattern> {
        val sequences = observations.`play`.windowed(3, 1) { window ->
            when {
                window.size >= 3 -> {
                    val trigger = window[0].action
                    val context = window[1].context
                    val outcome = window[2].outcome
                    
                    if (trigger != null && context != null && outcome != null) {
                        LearnedPattern(
                            name = "sequence_${trigger.hashCode()}",
                            triggers = Series.of(trigger),
                            actions = Series.of(context.suggestAction()),
                            confidence = 0.7,
                            category = PatternCategory.SEQUENCE
                        )
                    } else null
                }
                else -> null
            }
        }.filterNotNull()
        
        return Series.of(*sequences.toTypedArray())
    }
    
    /**
     * Extract time-based patterns (when things typically happen)
     */
    private fun extractTemporalPatterns(observations: Series<Observation>): Series<LearnedPattern> {
        val timeGroups = observations.`play`.groupBy { obs ->
            obs.timestamp / (60 * 60 * 1000) // Group by hour
        }
        
        val patterns = timeGroups.mapNotNull { (hour, obsInHour) ->
            if (obsInHour.size >= 3) {
                val commonActions = obsInHour.mapNotNull { it.action }.groupingBy { it }.eachCount()
                val mostCommon = commonActions.maxByOrNull { it.value }?.key
                
                if (mostCommon != null) {
                    LearnedPattern(
                        name = "temporal_hour_$hour",
                        triggers = Series.of("time_$hour"),
                        actions = Series.of(mostCommon),
                        confidence = 0.6,
                        category = PatternCategory.TEMPORAL
                    )
                } else null
            } else null
        }
        
        return Series.of(*patterns.toTypedArray())
    }
    
    /**
     * Extract context-dependent patterns (what works in which situations)
     */
    private fun extractContextualPatterns(observations: Series<Observation>): Series<LearnedPattern> {
        val contextGroups = observations.`play`.groupBy { obs ->
            obs.context?.extractCurrentScope() ?: "unknown"
        }
        
        val patterns = contextGroups.mapNotNull { (context, obsInContext) ->
            if (obsInContext.size >= 2) {
                val successfulActions = obsInContext.filter { it.outcome?.success == true }
                    .mapNotNull { it.action }
                
                if (successfulActions.isNotEmpty()) {
                    LearnedPattern(
                        name = "contextual_$context",
                        triggers = Series.of(context),
                        actions = Series.of(*successfulActions.toTypedArray()),
                        confidence = successfulActions.size.toDouble() / obsInContext.size,
                        category = PatternCategory.CONTEXTUAL
                    )
                } else null
            } else null
        }
        
        return Series.of(*patterns.toTypedArray())
    }
    
    /**
     * Extract correlations between context changes and outcomes
     */
    private fun extractContextCorrelations(observation: Observation): Series<ContextCorrelation> {
        val recentObservations = observationHistory.takeLast(10)
        
        return recentObservations.`play`.mapNotNull { prevObs ->
            if (prevObs.context != null && observation.context != null) {
                val contextSimilarity = calculateContextSimilarity(prevObs.context!!, observation.context!!)
                val outcomeSimilarity = calculateOutcomeSimilarity(prevObs.outcome, observation.outcome)
                
                if (contextSimilarity > 0.7 && outcomeSimilarity > 0.7) {
                    ContextCorrelation(
                        contextPattern = prevObs.context!!.extractPattern(),
                        outcomePattern = observation.outcome?.extractPattern() ?: "unknown",
                        strength = (contextSimilarity + outcomeSimilarity) / 2,
                        frequency = 1
                    )
                } else null
            } else null
        }.let { Series.of(*it.toTypedArray()) }
    }
    
    /**
     * Update existing patterns with new evidence
     */
    private fun updateExistingPatterns(observation: Observation) {
        patterns.`play`.forEach { pattern ->
            val isTriggered = observation.matchesPattern(pattern)
            if (isTriggered) {
                val wasSuccessful = observation.outcome?.success == true
                pattern.updateConfidence(wasSuccessful)
            }
        }
    }
    
    /**
     * Get patterns that match current context
     */
    fun getRelevantPatterns(context: CCEKContext): Series<LearnedPattern> {
        return patterns.`play`.filter { pattern ->
            pattern.isRelevantTo(context)
        }.sortedByDescending { it.confidence }
        .let { Series.of(*it.toTypedArray()) }
    }
    
    /**
     * Predict next likely action based on learned patterns
     */
    fun predictNextAction(context: CCEKContext): Action? {
        val relevantPatterns = getRelevantPatterns(context)
        val bestPattern = relevantPatterns.`play`.firstOrNull()
        
        return bestPattern?.suggestAction(context)
    }
    
    /**
     * Get insights about environment usage patterns
     */
    fun getUsageInsights(): UsageInsights {
        val totalObservations = observationHistory.size
        val successRate = observationHistory.`play`.count { it.outcome?.success == true }.toDouble() / totalObservations
        val mostUsedTools = observationHistory.`play`.mapNotNull { it.tool }.groupingBy { it }.eachCount()
        val mostActiveContexts = observationHistory.`play`.mapNotNull { it.context?.extractCurrentScope() }.groupingBy { it }.eachCount()
        
        return UsageInsights(
            totalInteractions = totalObservations,
            successRate = successRate,
            mostUsedTools = mostUsedTools.toList().sortedByDescending { it.second }.take(5),
            mostActiveContexts = mostActiveContexts.toList().sortedByDescending { it.second }.take(5),
            learnedPatterns = patterns.size,
            confidenceDistribution = patterns.`play`.map { it.confidence }.groupingBy {
                when {
                    it >= 0.8 -> "High"
                    it >= 0.6 -> "Medium" 
                    else -> "Low"
                }
            }.eachCount()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OBSERVATION AND PATTERN TYPES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Environment observation - captures what happened in the environment
 */
data class Observation(
    val timestamp: Long = System.currentTimeMillis(),
    val context: CCEKContext? = null,
    val action: String? = null,
    val outcome: Outcome? = null,
    val tool: String? = null,
    val user: String? = null,
    val environment: String? = null
) {
    fun matchesPattern(pattern: LearnedPattern): Boolean =
        pattern.triggers.`play`.any { trigger ->
            action?.contains(trigger, ignoreCase = true) == true ||
            context?.extractCurrentScope()?.contains(trigger, ignoreCase = true) == true
        }
}

/**
 * Learned behavioral pattern
 */
data class LearnedPattern(
    val name: String,
    val triggers: Series<String>,
    val actions: Series<String>,
    var confidence: Double,
    val category: PatternCategory,
    var occurrences: Int = 1,
    var successes: Int = 1
) {
    fun updateConfidence(wasSuccessful: Boolean) {
        occurrences++
        if (wasSuccessful) successes++
        confidence = successes.toDouble() / occurrences
    }
    
    fun isRelevantTo(context: CCEKContext): Boolean {
        val contextScope = context.extractCurrentScope()
        return triggers.`play`.any { trigger ->
            contextScope.contains(trigger, ignoreCase = true)
        }
    }
    
    fun suggestAction(context: CCEKContext): Action? {
        val relevantActions = actions.`play`.filter { action ->
            // Filter actions that make sense in current context
            context.extractCurrentCapabilities().any { cap ->
                action.contains(cap, ignoreCase = true)
            }
        }
        
        return relevantActions.firstOrNull()?.let { Action(it) }
    }
}

/**
 * Context correlation pattern
 */
data class ContextCorrelation(
    val contextPattern: String,
    val outcomePattern: String,
    val strength: Double,
    var frequency: Int
)

/**
 * Usage insights from pattern analysis
 */
data class UsageInsights(
    val totalInteractions: Int,
    val successRate: Double,
    val mostUsedTools: List<Pair<String, Int>>,
    val mostActiveContexts: List<Pair<String, Int>>,
    val learnedPatterns: Int,
    val confidenceDistribution: Map<String, Int>
)

enum class PatternCategory {
    SEQUENCE, TEMPORAL, CONTEXTUAL, OUTCOME_BASED, TOOL_USAGE
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

fun CCEKContext.extractPattern(): String =
    "${extractCurrentScope()}:${extractCurrentCapabilities().take(3).joinToString(",")}"

fun Outcome.extractPattern(): String =
    "${if (success) "success" else "failure"}:${data.take(50)}"

fun CCEKContext.suggestAction(): String =
    "suggested_action_for_${extractCurrentScope()}"

fun calculateContextSimilarity(ctx1: CCEKContext, ctx2: CCEKContext): Double {
    val scope1 = ctx1.extractCurrentScope()
    val scope2 = ctx2.extractCurrentScope()
    val caps1 = ctx1.extractCurrentCapabilities().toSet()
    val caps2 = ctx2.extractCurrentCapabilities().toSet()
    
    val scopeMatch = if (scope1 == scope2) 1.0 else 0.0
    val capMatch = caps1.intersect(caps2).size.toDouble() / (caps1.union(caps2).size)
    
    return (scopeMatch + capMatch) / 2
}

fun calculateOutcomeSimilarity(out1: Outcome?, out2: Outcome?): Double {
    if (out1 == null || out2 == null) return 0.0
    if (out1.success == out2.success) return 0.8
    return 0.2
}

// Series helpers
fun <T> mutableSeriesOf(): MutableSeries<T> = MutableSeriesImpl()
fun <T> MutableSeries<T>.add(item: T) = (this as MutableList<T>).add(item)
fun <T> MutableSeries<T>.addAll(items: Series<T>) = (this as MutableList<T>).addAll(items.`play`)
fun <T> Series<T>.takeLast(n: Int) = this.`play`.takeLast(n).let { Series.of(*it.toTypedArray()) }

typealias MutableSeries<T> = MutableList<T>
class MutableSeriesImpl<T> : ArrayList<T>(), MutableSeries<T>

// ═══════════════════════════════════════════════════════════════════════════════
// ENHANCED UNIVERSAL REFLECTOR WITH LEARNING
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Enhanced Universal Reflector with integrated learning and pattern extraction
 */
class LearningUniversalReflector(
    private val baseReflector: UniversalReflector,
    private val patternLearner: PatternLearner = PatternLearner()
) {
    
    /**
     * Observe and learn from environment interactions
     */
    suspend fun observeAndLearn(
        context: CCEKContext,
        action: String?,
        outcome: Outcome?,
        tool: String? = null
    ) {
        val observation = Observation(
            context = context,
            action = action,
            outcome = outcome,
            tool = tool
        )
        
        patternLearner.learnFromObservation(observation)
    }
    
    /**
     * Enhanced capability discovery with learned patterns
     */
    suspend fun discoverCapabilitiesWithLearning(): Series<Capability> {
        val baseCapabilities = baseReflector.discoverCapabilities()
        val learnedCapabilities = extractLearnedCapabilities()
        
        return baseCapabilities + learnedCapabilities
    }
    
    /**
     * Context synthesis enhanced with learned patterns
     */
    suspend fun synthesizeContextWithLearning(): ProjectContext {
        val baseContext = baseReflector.synthesizeContext()
        val insights = patternLearner.getUsageInsights()
        
        return enhanceContextWithInsights(baseContext, insights)
    }
    
    /**
     * Predict next optimal action based on patterns
     */
    suspend fun predictOptimalAction(context: CCEKContext): Action? {
        return patternLearner.predictNextAction(context)
    }
    
    /**
     * Get recommendations based on learned patterns
     */
    fun getRecommendations(context: CCEKContext): Series<Recommendation> {
        val relevantPatterns = patternLearner.getRelevantPatterns(context)
        
        return relevantPatterns.`play`.mapNotNull { pattern ->
            val action = pattern.suggestAction(context)
            if (action != null) {
                Recommendation(
                    action = action,
                    reason = "Based on pattern '${pattern.name}' with confidence ${(pattern.confidence * 100).toInt()}%",
                    confidence = pattern.confidence,
                    category = pattern.category.name
                )
            } else null
        }.let { Series.of(*it.toTypedArray()) }
    }
    
    private fun extractLearnedCapabilities(): Series<Capability> {
        val insights = patternLearner.getUsageInsights()
        
        return insights.mostUsedTools.map { (tool, _) ->
            when (tool.lowercase()) {
                "git" -> Capability.GIT_OPERATIONS
                "gradle" -> Capability.GRADLE_BUILD
                "npm" -> Capability.NPM_SCRIPTS
                "vscode" -> Capability.VSCODE_EXTENSIONS
                else -> null
            }
        }.filterNotNull().let { Series.of(*it.toTypedArray()) }
    }
    
    private fun enhanceContextWithInsights(
        context: ProjectContext, 
        insights: UsageInsights
    ): ProjectContext {
        // Add learned preferences and patterns to context
        return context // For now, return unchanged
    }
}

/**
 * Action recommendation based on learned patterns
 */
data class Recommendation(
    val action: Action,
    val reason: String,
    val confidence: Double,
    val category: String
)
