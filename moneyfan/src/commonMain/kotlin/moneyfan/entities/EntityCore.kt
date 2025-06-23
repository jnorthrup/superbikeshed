package moneyfan.entities

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.core.Price
import moneyfan.core.Decimal

/**
 * ## Legal Entity Core Types
 * 
 * Foundation for all legal entity structures in the moneyfan fiduciary system.
 * This module handles LLC, C-Corp, Sole Corp, Corp Sole, estates, and trusts
 * with a focus on beneficiary interests and fiduciary responsibilities.
 * 
 * The system operates with dual ledger capabilities (public/private) and
 * strong cryptographic guarantees for fiduciary actions.
 */

// === CORE TYPE ALIASES USING INDEXED ===

/** Entity types indexed by registration ID */
typealias EntityRegistry = Indexed<EntityId, LegalEntity>

/** Beneficiaries indexed by entity */
typealias BeneficiaryMap = Indexed<EntityId, Indexed<BeneficiaryId, Beneficiary>>

/** Ownership stakes indexed by holder */
typealias OwnershipRegistry = Indexed<EntityId, Indexed<StakeholderId, OwnershipStake>>

/** Entity relationships (parent/subsidiary) */
typealias EntityRelations = Indexed<EntityId, Indexed<RelationType, EntityId>>

// === VALUE CLASSES FOR TYPE SAFETY ===

@JvmInline
value class EntityId(val value: String)

@JvmInline
value class BeneficiaryId(val value: String)

@JvmInline
value class StakeholderId(val value: String)

@JvmInline
value class TaxId(val value: String)

@JvmInline
value class JurisdictionCode(val value: String)

// === CORE ENUMS ===

enum class EntityType {
    LLC,
    C_CORP,
    S_CORP,
    SOLE_CORP,
    CORP_SOLE,  // Religious/special purpose
    ESTATE,
    REVOCABLE_TRUST,
    IRREVOCABLE_TRUST,
    CHARITABLE_TRUST,
    PARTNERSHIP,
    SOLE_PROPRIETORSHIP
}

enum class EntityStatus {
    FORMING,          // In formation
    ACTIVE,           // Operating normally
    SUSPENDED,        // Temporarily suspended
    DISSOLVING,       // In dissolution process
    DISSOLVED,        // Fully dissolved
    MERGED,          // Merged into another entity
    BANKRUPT         // In bankruptcy
}

enum class RelationType {
    PARENT,
    SUBSIDIARY,
    AFFILIATE,
    PARTNER,
    BENEFICIARY_OF,
    TRUSTEE_OF,
    EXECUTOR_OF
}

enum class TaxStatus {
    PASS_THROUGH,     // LLC, S-Corp, Partnership
    C_CORP_TAXATION,  // C-Corp
    EXEMPT,           // Tax-exempt entities
    SPECIAL           // Special tax treatment
}

// === CORE INTERFACES ===

/**
 * Base interface for all legal entities in the system
 */
sealed interface LegalEntity {
    val id: EntityId
    val name: String
    val type: EntityType
    val status: EntityStatus
    val jurisdiction: JurisdictionCode
    val taxId: TaxId?
    val formationDate: Instant
    val dissolutionDate: Instant?
    
    /** Compute current net worth/value */
    fun computeNetWorth(): Price
    
    /** Get all beneficiaries with their interests */
    fun getBeneficiaries(): Indexed<Beneficiary>
    
    /** Check if entity can perform a specific action */
    fun canPerformAction(action: EntityAction): Boolean
    
    /** Get tax treatment for this entity */
    fun getTaxTreatment(): TaxStatus
}

/**
 * Represents a beneficiary of a legal entity
 */
@Serializable
data class Beneficiary(
    val id: BeneficiaryId,
    val name: String,
    val entityId: EntityId,
    val beneficialInterest: Decimal,  // Percentage 0-100
    val distributionRights: DistributionRights,
    val votingRights: VotingRights?,
    val restrictions: Indexed<BeneficiaryRestriction>?
)

/**
 * Distribution rights for beneficiaries
 */
@Serializable
data class DistributionRights(
    val priority: Int,  // 1 = highest priority
    val percentage: Decimal,
    val minimumDistribution: Price?,
    val maximumDistribution: Price?,
    val distributionSchedule: DistributionSchedule
)

/**
 * Voting rights (if applicable)
 */
@Serializable
data class VotingRights(
    val votingPower: Decimal,  // Percentage
    val vetoRights: Boolean,
    val specialRights: Indexed<String>?
)

/**
 * Distribution schedule
 */
sealed interface DistributionSchedule {
    data object Discretionary : DistributionSchedule
    data class Monthly(val dayOfMonth: Int) : DistributionSchedule
    data class Quarterly(val quarterEndOffset: Int) : DistributionSchedule
    data class Annual(val dayOfYear: Int) : DistributionSchedule
    data class Custom(val schedule: Indexed<Instant>) : DistributionSchedule
}

/**
 * Restrictions on beneficiary rights
 */
sealed interface BeneficiaryRestriction {
    data class AgeRestriction(val minimumAge: Int) : BeneficiaryRestriction
    data class TimeRestriction(val availableAfter: Instant) : BeneficiaryRestriction
    data class ConditionRestriction(val condition: String) : BeneficiaryRestriction
    data class SpendthriftProvision(val details: String) : BeneficiaryRestriction
}

/**
 * Ownership stake in an entity
 */
@Serializable
data class OwnershipStake(
    val holderId: StakeholderId,
    val entityId: EntityId,
    val stakeType: StakeType,
    val percentage: Decimal,
    val units: Long?,
    val acquisitionDate: Instant,
    val acquisitionPrice: Price,
    val currentValue: Price,
    val votingRights: VotingRights?,
    val transferRestrictions: Indexed<TransferRestriction>?
)

enum class StakeType {
    COMMON_STOCK,
    PREFERRED_STOCK,
    MEMBERSHIP_INTEREST,
    PARTNERSHIP_INTEREST,
    BENEFICIAL_INTEREST
}

/**
 * Transfer restrictions on ownership
 */
sealed interface TransferRestriction {
    data object RightOfFirstRefusal : TransferRestriction
    data object TagAlongRights : TransferRestriction
    data object DragAlongRights : TransferRestriction
    data class LockupPeriod(val until: Instant) : TransferRestriction
    data class ApprovalRequired(val approvers: Indexed<EntityId>) : TransferRestriction
}

/**
 * Actions that can be performed by/on entities
 */
sealed interface EntityAction {
    val entityId: EntityId
    val timestamp: Instant
    val authorizedBy: StakeholderId
    
    data class Formation(
        override val entityId: EntityId,
        override val timestamp: Instant,
        override val authorizedBy: StakeholderId,
        val founders: Indexed<StakeholderId>,
        val initialCapital: Price
    ) : EntityAction
    
    data class Distribution(
        override val entityId: EntityId,
        override val timestamp: Instant,
        override val authorizedBy: StakeholderId,
        val beneficiaryId: BeneficiaryId,
        val amount: Price,
        val reason: String
    ) : EntityAction
    
    data class OwnershipTransfer(
        override val entityId: EntityId,
        override val timestamp: Instant,
        override val authorizedBy: StakeholderId,
        val fromHolder: StakeholderId,
        val toHolder: StakeholderId,
        val stake: OwnershipStake
    ) : EntityAction
    
    data class StatusChange(
        override val entityId: EntityId,
        override val timestamp: Instant,
        override val authorizedBy: StakeholderId,
        val fromStatus: EntityStatus,
        val toStatus: EntityStatus,
        val reason: String
    ) : EntityAction
    
    data class TaxElection(
        override val entityId: EntityId,
        override val timestamp: Instant,
        override val authorizedBy: StakeholderId,
        val electionType: String,
        val effectiveDate: Instant
    ) : EntityAction
}

/**
 * Entity formation documents
 */
@Serializable
data class FormationDocuments(
    val entityId: EntityId,
    val articlesOfIncorporation: String?,
    val operatingAgreement: String?,
    val bylaws: String?,
    val trustAgreement: String?,
    val partnershipAgreement: String?,
    val einLetter: String?,
    val stateFilings: Indexed<StateFilingDoc>
)

@Serializable
data class StateFilingDoc(
    val state: JurisdictionCode,
    val documentType: String,
    val filingDate: Instant,
    val documentUrl: String,
    val status: String
)

/**
 * Financial snapshot of an entity
 */
@Serializable
data class EntityFinancials(
    val entityId: EntityId,
    val asOfDate: Instant,
    val assets: Price,
    val liabilities: Price,
    val equity: Price,
    val revenue: Price?,
    val expenses: Price?,
    val netIncome: Price?,
    val distributions: Price?
)

/**
 * Compliance requirements for an entity
 */
@Serializable
data class ComplianceRequirements(
    val entityId: EntityId,
    val annualReportDue: Instant?,
    val taxReturnDue: Instant?,
    val licensesRequired: Indexed<LicenseRequirement>,
    val filingRequirements: Indexed<FilingRequirement>
)

@Serializable
data class LicenseRequirement(
    val licenseType: String,
    val jurisdiction: JurisdictionCode,
    val renewalDate: Instant?,
    val status: String
)

@Serializable
data class FilingRequirement(
    val filingType: String,
    val dueDate: Instant,
    val frequency: String,
    val lastFiled: Instant?,
    val status: String
)

/**
 * Fiduciary duties for entity management
 */
sealed interface FiduciaryDuty {
    val entityId: EntityId
    val fiduciaryId: StakeholderId
    
    data class DutyOfCare(
        override val entityId: EntityId,
        override val fiduciaryId: StakeholderId,
        val standard: String
    ) : FiduciaryDuty
    
    data class DutyOfLoyalty(
        override val entityId: EntityId,
        override val fiduciaryId: StakeholderId,
        val conflictsDisclosed: Indexed<String>
    ) : FiduciaryDuty
    
    data class DutyOfObedience(
        override val entityId: EntityId,
        override val fiduciaryId: StakeholderId,
        val governingDocuments: Indexed<String>
    ) : FiduciaryDuty
}

/**
 * Helper functions for entity operations
 */
object EntityOperations {
    
    /**
     * Calculate total ownership percentage for validation
     */
    fun validateOwnership(stakes: Indexed<OwnershipStake>): Boolean {
        val total = stakes.play.map { it.percentage }.sum()
        return total <= 100.0
    }
    
    /**
     * Check if a beneficiary distribution is valid
     */
    fun validateDistribution(
        beneficiary: Beneficiary,
        amount: Price,
        entityFinancials: EntityFinancials
    ): Boolean {
        val availableCash = entityFinancials.assets - entityFinancials.liabilities
        val beneficiaryShare = availableCash * beneficiary.beneficialInterest
        
        return amount <= beneficiaryShare
    }
    
    /**
     * Generate unique entity ID
     */
    fun generateEntityId(type: EntityType, jurisdiction: JurisdictionCode): EntityId {
        val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return EntityId("${type.name}_${jurisdiction.value}_$timestamp")
    }
    
    /**
     * Create entity type from indexed registry
     */
    fun createEntityRegistry(entities: List<LegalEntity>): EntityRegistry {
        return entities.size j { i -> entities[i] }
    }
}