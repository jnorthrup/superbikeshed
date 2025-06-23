package moneyfan.fiduciary

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.fiduciary.blackbox.CryptographicProof
import moneyfan.fiduciary.blackbox.ProofId

/**
 * Core Ledger Type System using Indexed metaclass
 * 
 * This module defines the dual-ledger architecture where:
 * - Public ledger contains transparent, auditable records
 * - Private ledger contains encrypted, strategic information
 * - Bridge provides one-way cryptographic proofs from private to public
 */

// Core ID types
typealias TransactionId = String
typealias EntityId = String
typealias BeneficiaryId = String
typealias ExpertId = String
typealias RoleId = String

// Public ledger - transparent, auditable, compliant
typealias PublicLedger<T> = Indexed<TransactionId, PublicRecord<T>>

// Private ledger - encrypted, strategic, proprietary  
typealias PrivateLedger<T> = Indexed<EncryptedId, EncryptedRecord<T>>

// Bridge between ledgers - one-way cryptographic proofs
typealias LedgerBridge = Indexed<ProofId, CryptographicProof>

// Encrypted ID type
typealias EncryptedId = String

/**
 * Public record - visible to regulators and auditors
 */
@Serializable
data class PublicRecord<T>(
    val id: TransactionId,
    val timestamp: Instant,
    val entityId: EntityId,
    val recordType: RecordType,
    val publicData: T,
    val complianceProof: ComplianceProof,
    val jurisdiction: Jurisdiction,
    val auditMetadata: AuditMetadata
)

/**
 * Private record - encrypted and access controlled
 */
@Serializable
data class EncryptedRecord<T>(
    val id: EncryptedId,
    val timestamp: Instant,
    val entityId: EntityId,
    val encryptedData: ByteArray,
    val accessControl: LedgerAccessControl,
    val blackBoxRef: String? = null,
    val metadata: PrivateMetadata
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedRecord<*>) return false
        return id == other.id && encryptedData.contentEquals(other.encryptedData)
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        return result
    }
}

/**
 * Types of records in the ledger system
 */
enum class RecordType {
    // Entity operations
    ENTITY_FORMATION,
    ENTITY_AMENDMENT,
    ENTITY_DISSOLUTION,
    
    // Financial operations
    CAPITAL_CONTRIBUTION,
    DISTRIBUTION,
    EXPENSE,
    REVENUE,
    
    // Compliance operations
    TAX_FILING,
    REGULATORY_FILING,
    AUDIT_EVENT,
    
    // Governance operations
    BOARD_RESOLUTION,
    MEMBER_VOTE,
    APPOINTMENT,
    
    // Beneficiary operations
    BENEFICIARY_ADDITION,
    BENEFICIARY_REMOVAL,
    BENEFIT_DISTRIBUTION
}

/**
 * Compliance proof for public records
 */
@Serializable
data class ComplianceProof(
    val proofType: ComplianceProofType,
    val regulatoryBody: String,
    val requirements: Set<String>,
    val attestation: ByteArray,
    val validUntil: Instant?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComplianceProof) return false
        return proofType == other.proofType && 
               attestation.contentEquals(other.attestation)
    }
    
    override fun hashCode(): Int {
        var result = proofType.hashCode()
        result = 31 * result + attestation.contentHashCode()
        return result
    }
}

/**
 * Types of compliance proofs
 */
enum class ComplianceProofType {
    REGULATORY_COMPLIANCE,
    TAX_COMPLIANCE,
    ACCOUNTING_STANDARDS,
    LEGAL_REQUIREMENTS,
    BENEFICIARY_PROTECTION,
    ANTI_MONEY_LAUNDERING,
    KNOW_YOUR_CUSTOMER
}

/**
 * Jurisdiction information
 */
@Serializable
data class Jurisdiction(
    val country: String,
    val state: String?,
    val locality: String?,
    val regulatoryZone: String?
)

/**
 * Audit metadata for public records
 */
@Serializable
data class AuditMetadata(
    val auditTrailId: String,
    val previousRecordId: String?,
    val recordHash: String,
    val blockNumber: Long?,
    val validators: Set<String>
)

/**
 * Access control for ledger records
 */
@Serializable
data class LedgerAccessControl(
    val owner: EntityId,
    val authorizedRoles: Set<RoleId>,
    val authorizedEntities: Set<EntityId>,
    val expirationPolicy: ExpirationPolicy?,
    val decryptionRules: Set<DecryptionRule>
)

/**
 * Expiration policy for access control
 */
@Serializable
sealed class ExpirationPolicy {
    data class TimeBasedExpiry(val expiresAt: Instant) : ExpirationPolicy()
    data class EventBasedExpiry(val triggerEvent: String) : ExpirationPolicy()
    object NeverExpires : ExpirationPolicy()
}

/**
 * Rules for decrypting private records
 */
@Serializable
sealed class DecryptionRule {
    data class MultiSignature(
        val requiredSignatures: Int,
        val authorizedSigners: Set<String>
    ) : DecryptionRule()
    
    data class TimeDelay(
        val canDecryptAfter: Instant
    ) : DecryptionRule()
    
    data class RoleRequired(
        val requiredRole: RoleId
    ) : DecryptionRule()
    
    data class ComplianceRequired(
        val complianceType: ComplianceProofType
    ) : DecryptionRule()
}

/**
 * Private metadata - minimal information about encrypted records
 */
@Serializable
data class PrivateMetadata(
    val recordCategory: String,
    val sensitivityLevel: SensitivityLevel,
    val retentionPolicy: RetentionPolicy,
    val tags: Set<String>
)

/**
 * Sensitivity levels for private data
 */
enum class SensitivityLevel {
    PUBLIC,           // Can be disclosed publicly
    INTERNAL,         // Internal use only
    CONFIDENTIAL,     // Confidential business information
    HIGHLY_CONFIDENTIAL, // Highly sensitive information
    TRADE_SECRET      // Proprietary algorithms and strategies
}

/**
 * Retention policy for records
 */
@Serializable
sealed class RetentionPolicy {
    data class TimeBasedRetention(val retainUntil: Instant) : RetentionPolicy()
    data class EventBasedRetention(val retainUntilEvent: String) : RetentionPolicy()
    data class LegalHoldRetention(val holdId: String) : RetentionPolicy()
    object IndefiniteRetention : RetentionPolicy()
}

/**
 * Ledger entry for cross-references between public and private
 */
@Serializable
data class LedgerCrossReference(
    val publicRecordId: TransactionId,
    val privateRecordId: EncryptedId,
    val linkProof: CryptographicProof,
    val linkType: CrossReferenceLinkType
)

/**
 * Types of links between public and private records
 */
enum class CrossReferenceLinkType {
    COMPLIANCE_PROOF,     // Private record proves public compliance
    AGGREGATION_SOURCE,   // Private records aggregated into public
    DETAIL_EXPANSION,     // Private contains details of public
    STRATEGY_EXECUTION,   // Private strategy executed publicly
    AUDIT_SUPPORT        // Private supports public audit
}

/**
 * Factory for creating ledger instances
 */
object LedgerFactory {
    /**
     * Create a new public ledger
     */
    fun <T> createPublicLedger(capacity: Int = 10000): PublicLedger<T> {
        val records = mutableMapOf<TransactionId, PublicRecord<T>>()
        return capacity j { index ->
            val txId = "tx_$index"
            records[txId] ?: throw NoSuchElementException("Transaction $txId not found")
        }
    }
    
    /**
     * Create a new private ledger
     */
    fun <T> createPrivateLedger(capacity: Int = 10000): PrivateLedger<T> {
        val records = mutableMapOf<EncryptedId, EncryptedRecord<T>>()
        return capacity j { index ->
            val encId = "enc_$index"
            records[encId] ?: throw NoSuchElementException("Encrypted record $encId not found")
        }
    }
    
    /**
     * Create a new ledger bridge
     */
    fun createLedgerBridge(capacity: Int = 10000): LedgerBridge {
        val proofs = mutableMapOf<ProofId, CryptographicProof>()
        return capacity j { index ->
            val proofId = "proof_$index"
            proofs[proofId] ?: throw NoSuchElementException("Proof $proofId not found")
        }
    }
}

/**
 * Beneficiary tracking using Indexed
 */
typealias BeneficiaryMap = Indexed<EntityId, Indexed<BeneficiaryId, Beneficiary>>

/**
 * Beneficiary information
 */
@Serializable
data class Beneficiary(
    val id: BeneficiaryId,
    val name: String,
    val beneficiaryType: BeneficiaryType,
    val interests: Set<Interest>,
    val publicInfo: BeneficiaryPublicInfo,
    val privateInfo: EncryptedId // Reference to encrypted details
)

/**
 * Types of beneficiaries
 */
enum class BeneficiaryType {
    INDIVIDUAL,
    ENTITY,
    TRUST,
    ESTATE,
    CHARITY,
    GOVERNMENT
}

/**
 * Beneficiary interests
 */
@Serializable
data class Interest(
    val interestType: InterestType,
    val percentage: Double,
    val conditions: Set<String>,
    val priority: Int
)

/**
 * Types of interests
 */
enum class InterestType {
    EQUITY,
    INCOME,
    CAPITAL_GAINS,
    DISTRIBUTIONS,
    VOTING_RIGHTS,
    INFORMATION_RIGHTS,
    LIQUIDATION_PREFERENCE
}

/**
 * Public information about beneficiaries
 */
@Serializable
data class BeneficiaryPublicInfo(
    val disclosureRequired: Boolean,
    val publicIdentifier: String?,
    val jurisdiction: Jurisdiction?,
    val taxStatus: String?
)

/**
 * Panel member system using Indexed
 */
typealias ExpertPanel = Indexed<FiduciaryDomain, Indexed<ExpertId, PanelMember>>

/**
 * Panel member with specialized expertise
 */
@Serializable
data class PanelMember(
    val id: ExpertId,
    val expertise: Set<FiduciaryDomain>,
    val publicKey: ByteArray,
    val reputation: ReputationScore,
    val availability: AvailabilityStatus,
    val contributionHistory: EncryptedId // Reference to encrypted history
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PanelMember) return false
        return id == other.id && publicKey.contentEquals(other.publicKey)
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

/**
 * Fiduciary domains of expertise
 */
enum class FiduciaryDomain {
    TAX_PLANNING,
    ESTATE_PLANNING,
    INVESTMENT_STRATEGY,
    LEGAL_COMPLIANCE,
    ASSET_PROTECTION,
    SUCCESSION_PLANNING,
    REGULATORY_AFFAIRS,
    INTERNATIONAL_TAX,
    TRUST_ADMINISTRATION,
    CORPORATE_GOVERNANCE,
    RISK_MANAGEMENT,
    FINANCIAL_REPORTING,
    BENEFICIARY_ADVOCACY,
    ETHICAL_COMPLIANCE
}

/**
 * Reputation score for panel members
 */
@Serializable
data class ReputationScore(
    val score: Double,
    val totalContributions: Int,
    val successfulOutcomes: Int,
    val peerEndorsements: Int,
    val lastUpdated: Instant
)

/**
 * Availability status for panel members
 */
enum class AvailabilityStatus {
    AVAILABLE,
    BUSY,
    UNAVAILABLE,
    EMERGENCY_ONLY
}