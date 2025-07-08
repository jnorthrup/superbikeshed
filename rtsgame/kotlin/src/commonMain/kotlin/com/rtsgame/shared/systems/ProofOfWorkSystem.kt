package com.rtsgame.shared.systems

import com.rtsgame.shared.entity.Entity
import com.rtsgame.shared.entity.EntityManager
import kotlin.math.*

/**
 * Proof of Work System - Absorbed from JavaScript gem
 * 
 * Key Features:
 * - Breach types: Command injection, data exfiltration, network disruption, core compromise
 * - Network security model with hash rate calculation
 * - Computronium integration for security operations
 * - Deterministic breach probability calculations
 */
class ProofOfWorkSystem(
    internal val entityManager: EntityManager
) {
    
    companion object {
        // Breach Types
        enum class BreachType(val difficulty: Double, val reward: Double, val risk: Double) {
            COMMAND_INJECTION(0.8, 1.0, 0.9),
            DATA_EXFILTRATION(0.6, 0.8, 0.7),
            NETWORK_DISRUPTION(0.4, 0.6, 0.5),
            CORE_COMPROMISE(1.0, 2.0, 1.0)
        }
        
        // Security Levels
        enum class SecurityLevel(val multiplier: Double) {
            NONE(0.0), BASIC(0.25), ADVANCED(0.5), MILITARY(0.75), QUANTUM(1.0)
        }
        
        // Hash Rate Constants
        const val BASE_HASH_RATE = 1000.0 // hashes per second
        const val COMPUTRONIUM_BONUS = 2.0 // multiplier for computronium
        const val DIFFICULTY_SCALING = 1.5 // exponential difficulty increase
    }
    
    // Breach Attempt Component
    data class BreachAttempt(
        val id: Long,
        val sourceId: Long,
        val targetId: Long,
        val breachType: BreachType,
        val hashRate: Double,
        val startTime: Long,
        val progress: Double = 0.0
    )
    
    // Security Component
    data class SecurityComponent(
        val securityLevel: SecurityLevel,
        val hashRate: Double,
        val computroniumAllocation: Double,
        val activeBreaches: MutableList<Long> = mutableListOf()
    )
    
    // Network Security State
    data class NetworkSecurity(
        val nodeId: Long,
        val totalHashRate: Double,
        val activeDefenses: MutableList<Defense> = mutableListOf(),
        val threatLevel: Double = 0.0,
        val lastThreatAssessment: Long = System.currentTimeMillis()
    )
    
    // Defense System
    data class Defense(
        val id: Long,
        val defenseType: DefenseType,
        val strength: Double,
        val energyCost: Double,
        val isActive: Boolean = true
    ) {
        enum class DefenseType {
            FIREWALL, INTRUSION_DETECTION, ENCRYPTION, QUANTUM_BARRIER
        }
    }
    
    /**
     * Calculate hash rate for an entity
     */
    fun calculateHashRate(entityId: Long): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val security = entity.getComponent<SecurityComponent>() ?: return BASE_HASH_RATE
        
        var hashRate = BASE_HASH_RATE
        
        // Apply computronium bonus
        if (security.computroniumAllocation > 0) {
            hashRate *= (1.0 + (security.computroniumAllocation * COMPUTRONIUM_BONUS))
        }
        
        // Apply security level multiplier
        hashRate *= security.securityLevel.multiplier
        
        return hashRate
    }
    
    /**
     * Calculate breach difficulty for a target
     */
    fun calculateBreachDifficulty(targetId: Long, breachType: BreachType): Double {
        val target = entityManager.getEntity(targetId) ?: return Double.MAX_VALUE
        val security = target.getComponent<SecurityComponent>() ?: return breachType.difficulty
        
        var difficulty = breachType.difficulty
        
        // Apply security level multiplier
        difficulty *= (1.0 + security.securityLevel.multiplier)
        
        // Apply active defenses
        val networkSecurity = target.getComponent<NetworkSecurity>()
        if (networkSecurity != null) {
            networkSecurity.activeDefenses.forEach { defense ->
                if (defense.isActive) {
                    difficulty *= (1.0 + defense.strength)
                }
            }
        }
        
        // Apply exponential scaling
        difficulty = difficulty.pow(DIFFICULTY_SCALING)
        
        return difficulty
    }
    
    /**
     * Calculate breach probability
     */
    fun calculateBreachProbability(attackerId: Long, targetId: Long, breachType: BreachType): Double {
        val attackerHashRate = calculateHashRate(attackerId)
        val targetDifficulty = calculateBreachDifficulty(targetId, breachType)
        
        // Probability based on hash rate vs difficulty
        val baseProbability = attackerHashRate / targetDifficulty
        
        // Apply breach type risk factor
        val riskAdjustedProbability = baseProbability * (1.0 - breachType.risk)
        
        // Clamp to valid probability range
        return maxOf(0.0, minOf(1.0, riskAdjustedProbability))
    }
    
    /**
     * Initiate a breach attempt
     */
    fun initiateBreach(sourceId: Long, targetId: Long, breachType: BreachType): BreachAttempt? {
        val probability = calculateBreachProbability(sourceId, targetId, breachType)
        
        // Check if breach is possible
        if (probability <= 0.01) return null // 1% minimum threshold
        
        val hashRate = calculateHashRate(sourceId)
        
        val breachAttempt = BreachAttempt(
            id = System.nanoTime(),
            sourceId = sourceId,
            targetId = targetId,
            breachType = breachType,
            hashRate = hashRate,
            startTime = System.currentTimeMillis()
        )
        
        // Add to source entity's active breaches
        val sourceSecurity = entityManager.getEntity(sourceId)?.getComponent<SecurityComponent>()
        sourceSecurity?.activeBreaches?.add(breachAttempt.id)
        
        // Add to target entity's threat assessment
        val targetNetwork = entityManager.getEntity(targetId)?.getComponent<NetworkSecurity>()
        targetNetwork?.let { network ->
            network.threatLevel = minOf(1.0, network.threatLevel + breachType.risk * 0.1)
            network.lastThreatAssessment = System.currentTimeMillis()
        }
        
        return breachAttempt
    }
    
    /**
     * Update breach progress
     */
    fun updateBreachProgress(breachId: Long, deltaTime: Long): Boolean {
        // Find the breach attempt
        val breach = findBreachAttempt(breachId) ?: return false
        
        // Calculate progress increment
        val progressIncrement = (breach.hashRate * deltaTime) / 1000.0 // Convert to seconds
        val newProgress = breach.progress + progressIncrement
        
        // Check if breach is complete
        val difficulty = calculateBreachDifficulty(breach.targetId, breach.breachType)
        val isComplete = newProgress >= difficulty
        
        if (isComplete) {
            // Breach successful
            completeBreach(breach)
            return true
        } else {
            // Update progress
            updateBreachProgress(breachId, newProgress)
        }
        
        return false
    }
    
    /**
     * Complete a successful breach
     */
    internal fun completeBreach(breach: BreachAttempt) {
        // Apply breach effects
        when (breach.breachType) {
            BreachType.COMMAND_INJECTION -> {
                // Gain control over target entity
                applyCommandInjection(breach.sourceId, breach.targetId)
            }
            BreachType.DATA_EXFILTRATION -> {
                // Steal resources/information
                applyDataExfiltration(breach.sourceId, breach.targetId)
            }
            BreachType.NETWORK_DISRUPTION -> {
                // Disrupt target's network operations
                applyNetworkDisruption(breach.targetId)
            }
            BreachType.CORE_COMPROMISE -> {
                // Complete system takeover
                applyCoreCompromise(breach.sourceId, breach.targetId)
            }
        }
        
        // Remove from active breaches
        val sourceSecurity = entityManager.getEntity(breach.sourceId)?.getComponent<SecurityComponent>()
        sourceSecurity?.activeBreaches?.remove(breach.id)
    }
    
    /**
     * Apply command injection effects
     */
    internal fun applyCommandInjection(sourceId: Long, targetId: Long) {
        // Implementation: Grant source control over target's actions
        // This would integrate with the Command & Control System
    }
    
    /**
     * Apply data exfiltration effects
     */
    internal fun applyDataExfiltration(sourceId: Long, targetId: Long) {
        val source = entityManager.getEntity(sourceId)
        val target = entityManager.getEntity(targetId)
        
        // Transfer resources from target to source
        val targetResources = target?.getComponent<ResourceComponent>()
        val sourceResources = source?.getComponent<ResourceComponent>()
        
        if (targetResources != null && sourceResources != null) {
            val stolenAmount = (targetResources.amount * 0.3).toInt() // Steal 30%
            targetResources.amount -= stolenAmount
            sourceResources.amount += stolenAmount
        }
    }
    
    /**
     * Apply network disruption effects
     */
    internal fun applyNetworkDisruption(targetId: Long) {
        val target = entityManager.getEntity(targetId)
        val networkSecurity = target?.getComponent<NetworkSecurity>()
        
        // Reduce target's hash rate temporarily
        networkSecurity?.let { security ->
            security.totalHashRate *= 0.5 // 50% reduction
        }
    }
    
    /**
     * Apply core compromise effects
     */
    internal fun applyCoreCompromise(sourceId: Long, targetId: Long) {
        // Complete system takeover - most severe breach
        // This would transfer full control of target to source
        applyCommandInjection(sourceId, targetId)
        applyDataExfiltration(sourceId, targetId)
        applyNetworkDisruption(targetId)
    }
    
    /**
     * Find breach attempt by ID
     */
    internal fun findBreachAttempt(breachId: Long): BreachAttempt? {
        // Search through all entities for the breach attempt
        return entityManager.getAllEntities()
            .flatMap { entity ->
                entity.getComponent<SecurityComponent>()?.activeBreaches?.map { id ->
                    entity.getComponent<SecurityComponent>()?.breachHistory?.find { it.id == id }
                } ?: emptyList()
            }
            .find { it?.id == breachId }
    }
    
    /**
     * Assess network threat level
     */
    fun assessNetworkThreat(entityId: Long): Double {
        val entity = entityManager.getEntity(entityId) ?: return 0.0
        val networkSecurity = entity.getComponent<NetworkSecurity>() ?: return 0.0
        
        // Calculate threat based on active breaches and security state
        var threatLevel = networkSecurity.threatLevel
        
        // Apply time decay
        val timeSinceAssessment = System.currentTimeMillis() - networkSecurity.lastThreatAssessment
        val decayFactor = maxOf(0.0, 1.0 - (timeSinceAssessment / 60000.0)) // Decay over 1 minute
        threatLevel *= decayFactor
        
        return threatLevel
    }
    
    // Placeholder component types
    data class ResourceComponent(var amount: Int)
} 