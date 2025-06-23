package moneyfan.entities

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.core.Price
import moneyfan.core.Decimal

/**
 * ## Entity Registry System
 * 
 * Central registry for all legal entities in the moneyfan fiduciary system.
 * Provides unified entity management, relationship tracking, compliance monitoring,
 * and cross-entity operations for the comprehensive business management platform.
 */

/**
 * Central entity registry
 */
@Serializable
data class EntityRegistry(
    val registryId: String,
    val entities: Indexed<LegalEntity>,
    val relationships: Indexed<EntityRelationship>,
    val compliance: Indexed<ComplianceRecord>,
    val entityIndex: Indexed<Join<EntityType, Indexed<EntityId>>>, // Index by type
    val beneficiaryIndex: Indexed<Join<BeneficiaryId, Indexed<EntityId>>>, // Cross-entity beneficiaries
    val fiduciaryIndex: Indexed<Join<StakeholderId, Indexed<EntityId>>>, // Fiduciary relationships
    val jurisdictionIndex: Indexed<Join<JurisdictionCode, Indexed<EntityId>>>, // By jurisdiction
    val registrationEvents: Indexed<RegistrationEvent>
) {
    
    /**
     * Register a new entity in the system
     */
    fun registerEntity(entity: LegalEntity): EntityRegistry {
        val newEntities = (entities.size + 1) j { i ->
            if (i < entities.size) entities[i] else entity
        }
        
        val registrationEvent = RegistrationEvent(
            eventId = "reg_${System.currentTimeMillis()}",
            entityId = entity.id,
            eventType = RegistrationEventType.ENTITY_REGISTERED,
            timestamp = kotlinx.datetime.Clock.System.now(),
            details = "Entity ${entity.name} registered as ${entity.type}",
            performedBy = StakeholderId("system")
        )
        
        val newEvents = (registrationEvents.size + 1) j { i ->
            if (i < registrationEvents.size) registrationEvents[i] else registrationEvent
        }
        
        return copy(
            entities = newEntities,
            registrationEvents = newEvents
        ).updateIndices()
    }
    
    /**
     * Find entity by ID
     */
    fun findEntity(entityId: EntityId): LegalEntity? {
        return entities.play.find { it.id == entityId }
    }
    
    /**
     * Find entities by type
     */
    fun findEntitiesByType(type: EntityType): Indexed<LegalEntity> {
        val matches = entities.play.filter { it.type == type }
        return matches.size j { i -> matches[i] }
    }
    
    /**
     * Find entities where person is a beneficiary
     */
    fun findEntitiesForBeneficiary(beneficiaryId: BeneficiaryId): Indexed<LegalEntity> {
        val matches = entities.play.filter { entity ->
            entity.getBeneficiaries().play.any { it.id == beneficiaryId }
        }
        return matches.size j { i -> matches[i] }
    }
    
    /**
     * Find entities where person has fiduciary role
     */
    fun findEntitiesForFiduciary(fiduciaryId: StakeholderId): Indexed<LegalEntity> {
        val matches = entities.play.filter { entity ->
            when (entity) {
                is LLC -> entity.members.play.any { it.id == fiduciaryId }
                is Corporation -> entity.directors.play.any { it.id == fiduciaryId } ||
                                entity.officers.play.any { it.id == fiduciaryId }
                is Trust -> entity.trustees.play.any { it.id == fiduciaryId }
                is Estate -> entity.executor.id == fiduciaryId
                is CorpSole -> entity.officeHolder.id == fiduciaryId
                else -> false
            }
        }
        return matches.size j { i -> matches[i] }
    }
    
    /**
     * Get all relationships for an entity
     */
    fun getEntityRelationships(entityId: EntityId): Indexed<EntityRelationship> {
        val matches = relationships.play.filter { 
            it.primaryEntity == entityId || it.relatedEntity == entityId 
        }
        return matches.size j { i -> matches[i] }
    }
    
    /**
     * Get compliance status for entity
     */
    fun getComplianceStatus(entityId: EntityId): ComplianceStatus {
        val complianceRecords = compliance.play.filter { it.entityId == entityId }
        val recentIssues = complianceRecords.filter { 
            it.status == ComplianceRecordStatus.NON_COMPLIANT ||
            it.status == ComplianceRecordStatus.WARNING
        }
        
        return when {
            recentIssues.isEmpty() -> ComplianceStatus.COMPLIANT
            recentIssues.any { it.severity == ComplianceSeverity.CRITICAL } -> ComplianceStatus.CRITICAL
            recentIssues.any { it.severity == ComplianceSeverity.HIGH } -> ComplianceStatus.WARNING
            else -> ComplianceStatus.MONITORING
        }
    }
    
    /**
     * Calculate aggregate net worth across all entities
     */
    fun calculateTotalNetWorth(): Price {
        val total = entities.play.sumOf { it.computeNetWorth().value }
        return Price(total)
    }
    
    /**
     * Update all indices
     */
    private fun updateIndices(): EntityRegistry {
        return copy(
            entityIndex = buildEntityIndex(),
            beneficiaryIndex = buildBeneficiaryIndex(),
            fiduciaryIndex = buildFiduciaryIndex(),
            jurisdictionIndex = buildJurisdictionIndex()
        )
    }
    
    private fun buildEntityIndex(): Indexed<Join<EntityType, Indexed<EntityId>>> {
        val typeGroups = entities.play.groupBy { it.type }
        return typeGroups.size j { i ->
            val (type, entitiesOfType) = typeGroups.entries.toList()[i]
            val entityIds = entitiesOfType.size j { j -> entitiesOfType[j].id }
            type j entityIds
        }
    }
    
    private fun buildBeneficiaryIndex(): Indexed<Join<BeneficiaryId, Indexed<EntityId>>> {
        val beneficiaryMap = mutableMapOf<BeneficiaryId, MutableList<EntityId>>()
        
        entities.play.forEach { entity ->
            entity.getBeneficiaries().play.forEach { beneficiary ->
                beneficiaryMap.getOrPut(beneficiary.id) { mutableListOf() }.add(entity.id)
            }
        }
        
        return beneficiaryMap.size j { i ->
            val (beneficiaryId, entityIds) = beneficiaryMap.entries.toList()[i]
            val indexedEntityIds = entityIds.size j { j -> entityIds[j] }
            beneficiaryId j indexedEntityIds
        }
    }
    
    private fun buildFiduciaryIndex(): Indexed<Join<StakeholderId, Indexed<EntityId>>> {
        val fiduciaryMap = mutableMapOf<StakeholderId, MutableList<EntityId>>()
        
        entities.play.forEach { entity ->
            when (entity) {
                is LLC -> entity.members.play.forEach { member ->
                    fiduciaryMap.getOrPut(member.id) { mutableListOf() }.add(entity.id)
                }
                is Corporation -> {
                    entity.directors.play.forEach { director ->
                        fiduciaryMap.getOrPut(director.id) { mutableListOf() }.add(entity.id)
                    }
                    entity.officers.play.forEach { officer ->
                        fiduciaryMap.getOrPut(officer.id) { mutableListOf() }.add(entity.id)
                    }
                }
                is Trust -> entity.trustees.play.forEach { trustee ->
                    fiduciaryMap.getOrPut(trustee.id) { mutableListOf() }.add(entity.id)
                }
                is Estate -> {
                    fiduciaryMap.getOrPut(entity.executor.id) { mutableListOf() }.add(entity.id)
                }
                is CorpSole -> {
                    fiduciaryMap.getOrPut(entity.officeHolder.id) { mutableListOf() }.add(entity.id)
                }
            }
        }
        
        return fiduciaryMap.size j { i ->
            val (fiduciaryId, entityIds) = fiduciaryMap.entries.toList()[i]
            val indexedEntityIds = entityIds.size j { j -> entityIds[j] }
            fiduciaryId j indexedEntityIds
        }
    }
    
    private fun buildJurisdictionIndex(): Indexed<Join<JurisdictionCode, Indexed<EntityId>>> {
        val jurisdictionGroups = entities.play.groupBy { it.jurisdiction }
        return jurisdictionGroups.size j { i ->
            val (jurisdiction, entitiesInJurisdiction) = jurisdictionGroups.entries.toList()[i]
            val entityIds = entitiesInJurisdiction.size j { j -> entitiesInJurisdiction[j].id }
            jurisdiction j entityIds
        }
    }
}

/**
 * Relationship between entities
 */
@Serializable
data class EntityRelationship(
    val relationshipId: String,
    val primaryEntity: EntityId,
    val relatedEntity: EntityId,
    val relationshipType: EntityRelationshipType,
    val description: String,
    val effectiveDate: Instant,
    val expirationDate: Instant?,
    val ownershipPercentage: Decimal?,
    val votingControl: Boolean,
    val restrictions: Indexed<RelationshipRestriction>?
)

enum class EntityRelationshipType {
    PARENT_SUBSIDIARY,
    SISTER_ENTITIES,
    BENEFICIARY_RELATIONSHIP,
    TRUSTEE_RELATIONSHIP,
    MANAGEMENT_RELATIONSHIP,
    INVESTMENT_RELATIONSHIP,
    SERVICE_PROVIDER,
    CONTRACTUAL_RELATIONSHIP
}

sealed interface RelationshipRestriction {
    data class TransferRestriction(val details: String) : RelationshipRestriction
    data class VotingRestriction(val limitations: String) : RelationshipRestriction
    data class DistributionRestriction(val rules: String) : RelationshipRestriction
}

/**
 * Compliance record for entities
 */
@Serializable
data class ComplianceRecord(
    val recordId: String,
    val entityId: EntityId,
    val complianceType: ComplianceType,
    val status: ComplianceRecordStatus,
    val severity: ComplianceSeverity,
    val description: String,
    val identifiedDate: Instant,
    val dueDate: Instant?,
    val resolvedDate: Instant?,
    val responsible: StakeholderId,
    val remedialActions: Indexed<RemedialAction>?
)

enum class ComplianceType {
    TAX_FILING,
    ANNUAL_REPORT,
    LICENSING,
    REGULATORY_FILING,
    GOVERNANCE_REQUIREMENT,
    FIDUCIARY_DUTY,
    AUDIT_REQUIREMENT,
    DISCLOSURE_REQUIREMENT
}

enum class ComplianceRecordStatus {
    COMPLIANT,
    WARNING,
    NON_COMPLIANT,
    PENDING_REVIEW,
    REMEDIATION_IN_PROGRESS
}

enum class ComplianceSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ComplianceStatus {
    COMPLIANT,
    WARNING,
    MONITORING,
    CRITICAL
}

@Serializable
data class RemedialAction(
    val actionType: String,
    val description: String,
    val assignedTo: StakeholderId,
    val dueDate: Instant,
    val completedDate: Instant?,
    val status: String
)

/**
 * Registration events for audit trail
 */
@Serializable
data class RegistrationEvent(
    val eventId: String,
    val entityId: EntityId,
    val eventType: RegistrationEventType,
    val timestamp: Instant,
    val details: String,
    val performedBy: StakeholderId,
    val relatedEntities: Indexed<EntityId>? = null
)

enum class RegistrationEventType {
    ENTITY_REGISTERED,
    ENTITY_MODIFIED,
    ENTITY_DISSOLVED,
    RELATIONSHIP_CREATED,
    RELATIONSHIP_MODIFIED,
    RELATIONSHIP_TERMINATED,
    COMPLIANCE_ISSUE_IDENTIFIED,
    COMPLIANCE_ISSUE_RESOLVED,
    FIDUCIARY_APPOINTED,
    FIDUCIARY_REMOVED,
    BENEFICIARY_ADDED,
    BENEFICIARY_REMOVED,
    OWNERSHIP_TRANSFERRED,
    DISTRIBUTION_MADE,
    TAX_ELECTION_FILED
}

/**
 * Cross-entity analysis results
 */
@Serializable
data class CrossEntityAnalysis(
    val analysisId: String,
    val analysisDate: Instant,
    val entitiesAnalyzed: Indexed<EntityId>,
    val totalNetWorth: Price,
    val riskAssessment: RiskAssessment,
    val opportunityAnalysis: OpportunityAnalysis,
    val complianceGaps: Indexed<ComplianceGap>,
    val optimizationRecommendations: Indexed<OptimizationRecommendation>
)

@Serializable
data class RiskAssessment(
    val overallRiskLevel: RiskLevel,
    val riskFactors: Indexed<RiskFactor>,
    val mitigationStrategies: Indexed<MitigationStrategy>
)

enum class RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

@Serializable
data class RiskFactor(
    val riskType: RiskType,
    val severity: RiskSeverity,
    val description: String,
    val affectedEntities: Indexed<EntityId>,
    val likelihood: Decimal // Percentage
)

enum class RiskType {
    COMPLIANCE_RISK,
    TAX_RISK,
    FIDUCIARY_RISK,
    OPERATIONAL_RISK,
    FINANCIAL_RISK,
    LEGAL_RISK,
    SUCCESSION_RISK
}

enum class RiskSeverity {
    MINOR,
    MODERATE,
    SIGNIFICANT,
    SEVERE
}

@Serializable
data class MitigationStrategy(
    val strategyType: String,
    val description: String,
    val estimatedCost: Price?,
    val timeline: String,
    val responsibility: StakeholderId
)

@Serializable
data class OpportunityAnalysis(
    val taxOptimizationOpportunities: Indexed<TaxOptimization>,
    val restructuringOpportunities: Indexed<RestructuringOpportunity>,
    val efficiencyImprovements: Indexed<EfficiencyImprovement>
)

@Serializable
data class TaxOptimization(
    val optimizationType: String,
    val description: String,
    val estimatedSavings: Price,
    val requiredActions: Indexed<String>,
    val deadline: Instant?
)

@Serializable
data class RestructuringOpportunity(
    val opportunityType: String,
    val description: String,
    val benefitEstimate: Price?,
    val complexity: ComplexityLevel,
    val affectedEntities: Indexed<EntityId>
)

enum class ComplexityLevel {
    SIMPLE,
    MODERATE,
    COMPLEX,
    HIGHLY_COMPLEX
}

@Serializable
data class EfficiencyImprovement(
    val improvementType: String,
    val description: String,
    val estimatedBenefit: String,
    val implementation: String
)

@Serializable
data class ComplianceGap(
    val gapType: ComplianceType,
    val description: String,
    val affectedEntities: Indexed<EntityId>,
    val urgency: UrgencyLevel,
    val remediation: String
)

enum class UrgencyLevel {
    LOW,
    MEDIUM,
    HIGH,
    IMMEDIATE
}

@Serializable
data class OptimizationRecommendation(
    val recommendationType: RecommendationType,
    val title: String,
    val description: String,
    val estimatedBenefit: Price?,
    val priority: Priority,
    val implementation: ImplementationPlan
)

enum class RecommendationType {
    TAX_STRATEGY,
    ENTITY_RESTRUCTURING,
    GOVERNANCE_IMPROVEMENT,
    COMPLIANCE_ENHANCEMENT,
    COST_REDUCTION,
    RISK_MITIGATION,
    SUCCESSION_PLANNING
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

@Serializable
data class ImplementationPlan(
    val steps: Indexed<ImplementationStep>,
    val timeline: String,
    val estimatedCost: Price?,
    val requiredApprovals: Indexed<String>
)

@Serializable
data class ImplementationStep(
    val stepNumber: Int,
    val description: String,
    val responsible: StakeholderId,
    val estimatedDuration: String,
    val dependencies: Indexed<Int>? // Step numbers this depends on
)

/**
 * Entity registry operations
 */
object EntityRegistryOperations {
    
    /**
     * Create a new entity registry
     */
    fun createEntityRegistry(): EntityRegistry {
        return EntityRegistry(
            registryId = "registry_${System.currentTimeMillis()}",
            entities = emptyIndex(),
            relationships = emptyIndex(),
            compliance = emptyIndex(),
            entityIndex = emptyIndex(),
            beneficiaryIndex = emptyIndex(),
            fiduciaryIndex = emptyIndex(),
            jurisdictionIndex = emptyIndex(),
            registrationEvents = emptyIndex()
        )
    }
    
    /**
     * Add entity relationship
     */
    fun addEntityRelationship(
        registry: EntityRegistry,
        primaryEntity: EntityId,
        relatedEntity: EntityId,
        relationshipType: EntityRelationshipType,
        description: String,
        ownershipPercentage: Decimal? = null
    ): EntityRegistry {
        val relationship = EntityRelationship(
            relationshipId = "rel_${System.currentTimeMillis()}",
            primaryEntity = primaryEntity,
            relatedEntity = relatedEntity,
            relationshipType = relationshipType,
            description = description,
            effectiveDate = kotlinx.datetime.Clock.System.now(),
            expirationDate = null,
            ownershipPercentage = ownershipPercentage,
            votingControl = false,
            restrictions = null
        )
        
        val newRelationships = (registry.relationships.size + 1) j { i ->
            if (i < registry.relationships.size) registry.relationships[i] else relationship
        }
        
        val registrationEvent = RegistrationEvent(
            eventId = "event_${System.currentTimeMillis()}",
            entityId = primaryEntity,
            eventType = RegistrationEventType.RELATIONSHIP_CREATED,
            timestamp = kotlinx.datetime.Clock.System.now(),
            details = "Relationship created: $description",
            performedBy = StakeholderId("system"),
            relatedEntities = 1 j { relatedEntity }
        )
        
        val newEvents = (registry.registrationEvents.size + 1) j { i ->
            if (i < registry.registrationEvents.size) registry.registrationEvents[i] else registrationEvent
        }
        
        return registry.copy(
            relationships = newRelationships,
            registrationEvents = newEvents
        )
    }
    
    /**
     * Record compliance issue
     */
    fun recordComplianceIssue(
        registry: EntityRegistry,
        entityId: EntityId,
        complianceType: ComplianceType,
        severity: ComplianceSeverity,
        description: String,
        dueDate: Instant?,
        responsible: StakeholderId
    ): EntityRegistry {
        val complianceRecord = ComplianceRecord(
            recordId = "comp_${System.currentTimeMillis()}",
            entityId = entityId,
            complianceType = complianceType,
            status = ComplianceRecordStatus.NON_COMPLIANT,
            severity = severity,
            description = description,
            identifiedDate = kotlinx.datetime.Clock.System.now(),
            dueDate = dueDate,
            resolvedDate = null,
            responsible = responsible,
            remedialActions = null
        )
        
        val newCompliance = (registry.compliance.size + 1) j { i ->
            if (i < registry.compliance.size) registry.compliance[i] else complianceRecord
        }
        
        return registry.copy(compliance = newCompliance)
    }
    
    /**
     * Perform cross-entity analysis
     */
    fun performCrossEntityAnalysis(registry: EntityRegistry): CrossEntityAnalysis {
        val entitiesAnalyzed = registry.entities.size j { i -> registry.entities[i].id }
        
        return CrossEntityAnalysis(
            analysisId = "analysis_${System.currentTimeMillis()}",
            analysisDate = kotlinx.datetime.Clock.System.now(),
            entitiesAnalyzed = entitiesAnalyzed,
            totalNetWorth = registry.calculateTotalNetWorth(),
            riskAssessment = analyzeRisks(registry),
            opportunityAnalysis = analyzeOpportunities(registry),
            complianceGaps = identifyComplianceGaps(registry),
            optimizationRecommendations = generateOptimizationRecommendations(registry)
        )
    }
    
    private fun analyzeRisks(registry: EntityRegistry): RiskAssessment {
        val riskFactors = mutableListOf<RiskFactor>()
        
        // Analyze compliance risks
        val complianceIssues = registry.compliance.play.filter { 
            it.status == ComplianceRecordStatus.NON_COMPLIANT 
        }
        if (complianceIssues.isNotEmpty()) {
            riskFactors.add(RiskFactor(
                riskType = RiskType.COMPLIANCE_RISK,
                severity = RiskSeverity.SIGNIFICANT,
                description = "${complianceIssues.size} compliance issues identified",
                affectedEntities = complianceIssues.size j { i -> complianceIssues[i].entityId },
                likelihood = 90.0
            ))
        }
        
        return RiskAssessment(
            overallRiskLevel = if (riskFactors.isEmpty()) RiskLevel.LOW else RiskLevel.MODERATE,
            riskFactors = riskFactors.size j { i -> riskFactors[i] },
            mitigationStrategies = emptyIndex()
        )
    }
    
    private fun analyzeOpportunities(registry: EntityRegistry): OpportunityAnalysis {
        return OpportunityAnalysis(
            taxOptimizationOpportunities = emptyIndex(),
            restructuringOpportunities = emptyIndex(),
            efficiencyImprovements = emptyIndex()
        )
    }
    
    private fun identifyComplianceGaps(registry: EntityRegistry): Indexed<ComplianceGap> {
        val gaps = mutableListOf<ComplianceGap>()
        
        // Check for entities without recent filings
        registry.entities.play.forEach { entity ->
            val recentFilings = registry.compliance.play.filter { 
                it.entityId == entity.id && 
                it.complianceType == ComplianceType.TAX_FILING 
            }
            
            if (recentFilings.isEmpty()) {
                gaps.add(ComplianceGap(
                    gapType = ComplianceType.TAX_FILING,
                    description = "No recent tax filings found for ${entity.name}",
                    affectedEntities = 1 j { entity.id },
                    urgency = UrgencyLevel.HIGH,
                    remediation = "File required tax returns"
                ))
            }
        }
        
        return gaps.size j { i -> gaps[i] }
    }
    
    private fun generateOptimizationRecommendations(registry: EntityRegistry): Indexed<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()
        
        // Check for tax optimization opportunities
        if (registry.entities.size > 1) {
            recommendations.add(OptimizationRecommendation(
                recommendationType = RecommendationType.TAX_STRATEGY,
                title = "Multi-Entity Tax Optimization",
                description = "Review tax structures across ${registry.entities.size} entities for optimization opportunities",
                estimatedBenefit = Price(50000.0),
                priority = Priority.MEDIUM,
                implementation = ImplementationPlan(
                    steps = 3 j { i ->
                        when (i) {
                            0 -> ImplementationStep(1, "Analyze current tax positions", StakeholderId("tax_advisor"), "2 weeks", null)
                            1 -> ImplementationStep(2, "Identify optimization strategies", StakeholderId("tax_advisor"), "1 week", 1 j { 1 })
                            else -> ImplementationStep(3, "Implement recommended changes", StakeholderId("attorney"), "4 weeks", 1 j { 2 })
                        }
                    },
                    timeline = "7 weeks",
                    estimatedCost = Price(15000.0),
                    requiredApprovals = 1 j { "Board of Directors" }
                )
            ))
        }
        
        return recommendations.size j { i -> recommendations[i] }
    }
    
    /**
     * Generate entity relationship map
     */
    fun generateRelationshipMap(registry: EntityRegistry): Indexed<Join<EntityId, Indexed<EntityRelationship>>> {
        val relationshipMap = mutableMapOf<EntityId, MutableList<EntityRelationship>>()
        
        registry.relationships.play.forEach { relationship ->
            relationshipMap.getOrPut(relationship.primaryEntity) { mutableListOf() }.add(relationship)
            relationshipMap.getOrPut(relationship.relatedEntity) { mutableListOf() }.add(relationship)
        }
        
        return relationshipMap.size j { i ->
            val (entityId, relationships) = relationshipMap.entries.toList()[i]
            val indexedRelationships = relationships.size j { j -> relationships[j] }
            entityId j indexedRelationships
        }
    }
}