package moneyfan.ta4k

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Bridge component connecting ta4k strategy analysis to moneyfan MDI interface
 * Handles initialization, configuration, and data flow between systems
 */
class StrategyBridge private constructor() {
    
    companion object {
        private var instance: StrategyBridge? = null
        
        fun getInstance(): StrategyBridge {
            return instance ?: synchronized(this) {
                instance ?: StrategyBridge().also { instance = it }
            }
        }
    }
    
    // Core strategy components
    private var orchestrator: StrategyOrchestrator? = null
    
    // Bridge state
    private var isInitialized = false
    private var initializationError: Exception? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Initialize real strategy components with background fetching and attention system
     */
    suspend fun initializeStrategies(): Result<StrategyOrchestrator> {
        return try {
            if (isInitialized && orchestrator != null) {
                return Result.success(orchestrator!!)
            }
            
            // Create real orchestrator with background fetching and attention system
            val strategyOrchestrator = StrategyOrchestrator()
            
            // Store components
            orchestrator = strategyOrchestrator
            isInitialized = true
            initializationError = null
            
            Result.success(strategyOrchestrator)
            
        } catch (e: Exception) {
            initializationError = e
            Result.failure(e)
        }
    }
    
    /**
     * Get strategy orchestrator if initialized
     */
    fun getOrchestrator(): StrategyOrchestrator? = orchestrator
    
    /**
     * Check if bridge is properly initialized
     */
    fun isReady(): Boolean = isInitialized && orchestrator != null
    
    /**
     * Get initialization error if any
     */
    fun getError(): Exception? = initializationError
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        orchestrator = null
        isInitialized = false
    }
}