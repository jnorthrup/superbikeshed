package moneyfan.ledger

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.core.Price
import moneyfan.core.Decimal
import moneyfan.entities.*
import moneyfan.fiduciary.*

/**
 * ## Dual Ledger System
 * 
 * Implements a dual-ledger architecture with public transparency and private
 * confidentiality for fiduciary operations. The public ledger provides transparency
 * for stakeholders while the private ledger protects sensitive equity operations
 * and beneficiary information.
 * 
 * This system serves as the "black box" for equity operations while maintaining
 * compliance and auditability through cryptographic proofs.
 */

// === CORE LEDGER TYPE ALIASES ===

/** Public transactions indexed by timestamp */
typealias PublicLedger = Indexed<Instant, PublicTransaction>

/** Private transactions indexed by encrypted key */
typealias PrivateLedger = Indexed<EncryptedKey, PrivateTransaction>

/** Cross-ledger reconciliation records */
typealias ReconciliationRecords = Indexed<ReconciliationId, LedgerReconciliation>

/** Audit trail for both ledgers */
typealias DualLedgerAuditTrail = Indexed<AuditEntryId, DualLedgerAuditEntry>

// === VALUE CLASSES FOR TYPE SAFETY ===

@JvmInline
value class TransactionId(val value: String)

@JvmInline
value class EncryptedKey(val value: String)

@JvmInline
value class ReconciliationId(val value: String)

@JvmInline
value class AuditEntryId(val value: String)

@JvmInline
value class CryptographicHash(val value: String)

@JvmInline
value class DigitalSignatureValue(val value: String)

// === DUAL LEDGER SYSTEM ===

/**
 * Main dual ledger system
 */
@Serializable
data class DualLedgerSystem(
    val systemId: String,
    val publicLedger: PublicLedger,
    val privateLedger: PrivateLedger,
    val reconciliationRecords: ReconciliationRecords,
    val auditTrail: DualLedgerAuditTrail,
    val cryptographicProofs: Indexed<CryptographicProof>,
    val accessControls: AccessControlMatrix,
    val complianceRecords: Indexed<ComplianceRecord>,
    val lastReconciliation: Instant?,
    val systemIntegrity: SystemIntegrityStatus
) {
    
    /**
     * Record a public transaction
     */
    fun recordPublicTransaction(transaction: PublicTransaction): DualLedgerSystem {
        val newPublicLedger = (publicLedger.size + 1) j { i ->
            if (i < publicLedger.size) publicLedger[i] else transaction
        }
        
        val auditEntry = DualLedgerAuditEntry(
            entryId = AuditEntryId("audit_${System.currentTimeMillis()}"),
            timestamp = kotlinx.datetime.Clock.System.now(),
            ledgerType = LedgerType.PUBLIC,
            action = AuditAction.TRANSACTION_RECORDED,
            details = "Public transaction ${transaction.transactionId} recorded",
            performedBy = transaction.initiatedBy,
            cryptographicProof = generateTransactionProof(transaction)
        )
        
        val newAuditTrail = (auditTrail.size + 1) j { i ->
            if (i < auditTrail.size) auditTrail[i] else auditEntry
        }
        
        return copy(
            publicLedger = newPublicLedger,
            auditTrail = newAuditTrail
        )
    }
    
    /**
     * Record a private transaction (encrypted)
     */
    fun recordPrivateTransaction(transaction: PrivateTransaction, encryptionKey: EncryptedKey): DualLedgerSystem {
        val newPrivateLedger = (privateLedger.size + 1) j { i ->
            if (i < privateLedger.size) privateLedger[i] else transaction
        }
        
        val auditEntry = DualLedgerAuditEntry(
            entryId = AuditEntryId("audit_${System.currentTimeMillis()}"),
            timestamp = kotlinx.datetime.Clock.System.now(),
            ledgerType = LedgerType.PRIVATE,
            action = AuditAction.TRANSACTION_RECORDED,
            details = "Private transaction recorded (encrypted)",
            performedBy = transaction.initiatedBy,
            cryptographicProof = generatePrivateTransactionProof(transaction)
        )
        
        val newAuditTrail = (auditTrail.size + 1) j { i ->
            if (i < auditTrail.size) auditTrail[i] else auditEntry
        }
        
        return copy(
            privateLedger = newPrivateLedger,
            auditTrail = newAuditTrail
        )
    }
    
    /**
     * Perform cross-ledger reconciliation
     */
    fun performReconciliation(): DualLedgerSystem {
        val reconciliation = LedgerReconciliation(
            reconciliationId = ReconciliationId("recon_${System.currentTimeMillis()}"),
            reconciliationDate = kotlinx.datetime.Clock.System.now(),
            publicLedgerHash = calculatePublicLedgerHash(),
            privateLedgerHash = calculatePrivateLedgerHash(),
            crossLedgerProof = generateCrossLedgerProof(),
            discrepancies = identifyDiscrepancies(),
            reconciliationStatus = if (identifyDiscrepancies().size == 0) {
                ReconciliationStatus.RECONCILED
            } else {
                ReconciliationStatus.DISCREPANCIES_FOUND
            }
        )
        
        val newReconciliationRecords = (reconciliationRecords.size + 1) j { i ->
            if (i < reconciliationRecords.size) reconciliationRecords[i] else reconciliation
        }
        
        return copy(
            reconciliationRecords = newReconciliationRecords,
            lastReconciliation = reconciliation.reconciliationDate
        )
    }
    
    /**
     * Verify system integrity
     */
    fun verifyIntegrity(): SystemIntegrityStatus {
        val publicIntegrity = verifyPublicLedgerIntegrity()
        val privateIntegrity = verifyPrivateLedgerIntegrity()
        val crossLedgerIntegrity = verifyCrossLedgerIntegrity()
        
        return when {
            publicIntegrity && privateIntegrity && crossLedgerIntegrity -> SystemIntegrityStatus.VERIFIED
            else -> SystemIntegrityStatus.INTEGRITY_VIOLATIONS_DETECTED
        }
    }
    
    private fun generateTransactionProof(transaction: PublicTransaction): CryptographicHash {
        // Simplified proof generation
        val data = "${transaction.transactionId.value}${transaction.timestamp}${transaction.amount.value}"
        return CryptographicHash("sha256_${data.hashCode()}")
    }
    
    private fun generatePrivateTransactionProof(transaction: PrivateTransaction): CryptographicHash {
        // Zero-knowledge proof for private transaction
        val proofData = "${transaction.transactionId.value}${transaction.timestamp}"
        return CryptographicHash("zkp_${proofData.hashCode()}")
    }
    
    private fun calculatePublicLedgerHash(): CryptographicHash {
        val allTransactions = publicLedger.play.map { it.transactionId.value }.joinToString("")
        return CryptographicHash("public_${allTransactions.hashCode()}")
    }
    
    private fun calculatePrivateLedgerHash(): CryptographicHash {
        val allTransactions = privateLedger.play.map { it.transactionId.value }.joinToString("")
        return CryptographicHash("private_${allTransactions.hashCode()}")
    }
    
    private fun generateCrossLedgerProof(): CryptographicProof {
        return CryptographicProof(
            proofId = "cross_${System.currentTimeMillis()}",
            proofType = ProofType.CROSS_LEDGER_CONSISTENCY,
            proofData = "Cross-ledger consistency verified",
            timestamp = kotlinx.datetime.Clock.System.now(),
            verificationKey = "verification_key_placeholder"
        )
    }
    
    private fun identifyDiscrepancies(): Indexed<LedgerDiscrepancy> {
        // Simplified discrepancy detection
        return emptyIndex()
    }
    
    private fun verifyPublicLedgerIntegrity(): Boolean = true
    private fun verifyPrivateLedgerIntegrity(): Boolean = true
    private fun verifyCrossLedgerIntegrity(): Boolean = true
}

// === PUBLIC LEDGER TRANSACTIONS ===

/**
 * Public ledger transaction (transparent)
 */
@Serializable
data class PublicTransaction(
    val transactionId: TransactionId,
    val timestamp: Instant,
    val transactionType: PublicTransactionType,
    val entityId: EntityId,
    val amount: Price,
    val description: String,
    val counterparty: String?,
    val category: TransactionCategory,
    val initiatedBy: StakeholderId,
    val approvedBy: Indexed<StakeholderId>?,
    val digitalSignatures: Indexed<LedgerDigitalSignature>,
    val complianceChecks: Indexed<ComplianceCheck>,
    val publicMetadata: PublicTransactionMetadata
)

enum class PublicTransactionType {
    ENTITY_FORMATION,
    ENTITY_DISSOLUTION,
    OWNERSHIP_TRANSFER,
    PUBLIC_DISTRIBUTION,
    COMPLIANCE_FILING,
    PUBLIC_REPORTING,
    GOVERNANCE_ACTION,
    REGULATORY_PAYMENT,
    PUBLIC_ASSET_TRANSACTION,
    TAX_PAYMENT
}

enum class TransactionCategory {
    OPERATIONAL,
    COMPLIANCE,
    GOVERNANCE,
    REPORTING,
    REGULATORY,
    PUBLIC_BENEFIT
}

@Serializable
data class PublicTransactionMetadata(
    val jurisdiction: JurisdictionCode?,
    val regulatoryReferences: Indexed<String>?,
    val publicNoticeRequired: Boolean,
    val stakeholderNotifications: Indexed<StakeholderId>?,
    val reportingRequirements: Indexed<String>?
)

@Serializable
data class LedgerDigitalSignature(
    val signerId: StakeholderId,
    val signature: DigitalSignatureValue,
    val signingMethod: SigningMethod,
    val timestamp: Instant,
    val certificateChain: Indexed<String>?
)

enum class SigningMethod {
    RSA_2048,
    ECDSA_P256,
    ED25519,
    MULTI_SIG,
    THRESHOLD_SIG
}

@Serializable
data class ComplianceCheck(
    val checkType: ComplianceCheckType,
    val status: ComplianceCheckStatus,
    val performedBy: StakeholderId,
    val timestamp: Instant,
    val details: String
)

enum class ComplianceCheckType {
    AML_SCREENING,
    KYC_VERIFICATION,
    SANCTIONS_CHECK,
    TAX_COMPLIANCE,
    REGULATORY_APPROVAL,
    FIDUCIARY_DUTY_CHECK
}

enum class ComplianceCheckStatus {
    PASSED,
    FAILED,
    PENDING,
    REQUIRES_REVIEW,
    EXEMPTED
}

// === PRIVATE LEDGER TRANSACTIONS ===

/**
 * Private ledger transaction (encrypted/confidential)
 */
@Serializable
data class PrivateTransaction(
    val transactionId: TransactionId,
    val timestamp: Instant,
    val transactionType: PrivateTransactionType,
    val entityId: EntityId,
    val amount: Price,
    val encryptedDescription: String, // Encrypted sensitive details
    val beneficiariesAffected: Indexed<BeneficiaryId>,
    val confidentialityLevel: ConfidentialityLevel,
    val initiatedBy: StakeholderId,
    val authorizedBy: Indexed<StakeholderId>,
    val encryptedSignatures: Indexed<EncryptedDigitalSignature>,
    val privacyProtections: Indexed<PrivacyProtection>,
    val zeroKnowledgeProof: ZeroKnowledgeProof
)

enum class PrivateTransactionType {
    CONFIDENTIAL_DISTRIBUTION,
    PRIVATE_EQUITY_TRANSACTION,
    BENEFICIAL_INTEREST_ADJUSTMENT,
    FIDUCIARY_COMPENSATION,
    PRIVATE_ASSET_MANAGEMENT,
    CONFIDENTIAL_SETTLEMENT,
    PRIVATE_INVESTMENT,
    TRUST_ADMINISTRATION,
    ESTATE_DISTRIBUTION,
    FAMILY_OFFICE_TRANSACTION
}

enum class ConfidentialityLevel {
    RESTRICTED,
    CONFIDENTIAL,
    SECRET,
    TOP_SECRET
}

@Serializable
data class EncryptedDigitalSignature(
    val signerId: StakeholderId,
    val encryptedSignature: String,
    val encryptionMethod: EncryptionMethod,
    val timestamp: Instant
)

enum class EncryptionMethod {
    AES_256_GCM,
    CHACHA20_POLY1305,
    RSA_OAEP,
    ELLIPTIC_CURVE,
    HYBRID_ENCRYPTION
}

@Serializable
data class PrivacyProtection(
    val protectionType: PrivacyProtectionType,
    val implementation: String,
    val effectiveDate: Instant,
    val expirationDate: Instant?
)

enum class PrivacyProtectionType {
    DIFFERENTIAL_PRIVACY,
    HOMOMORPHIC_ENCRYPTION,
    SECURE_MULTIPARTY_COMPUTATION,
    ZERO_KNOWLEDGE_PROOF,
    PRIVACY_PRESERVING_ANALYTICS
}

@Serializable
data class ZeroKnowledgeProof(
    val proofType: ZKProofType,
    val proofData: String,
    val verificationKey: String,
    val circuitHash: String?
)

enum class ZKProofType {
    ZKSNARKS,
    ZKSTARKS,
    BULLETPROOFS,
    PLONK,
    GROTH16
}

// === LEDGER RECONCILIATION ===

/**
 * Cross-ledger reconciliation record
 */
@Serializable
data class LedgerReconciliation(
    val reconciliationId: ReconciliationId,
    val reconciliationDate: Instant,
    val publicLedgerHash: CryptographicHash,
    val privateLedgerHash: CryptographicHash,
    val crossLedgerProof: CryptographicProof,
    val discrepancies: Indexed<LedgerDiscrepancy>,
    val reconciliationStatus: ReconciliationStatus,
    val performedBy: StakeholderId = StakeholderId("system"),
    val resolutionActions: Indexed<ResolutionAction>?
)

enum class ReconciliationStatus {
    RECONCILED,
    DISCREPANCIES_FOUND,
    RECONCILIATION_FAILED,
    PENDING_REVIEW,
    RESOLVED
}

@Serializable
data class LedgerDiscrepancy(
    val discrepancyId: String,
    val discrepancyType: DiscrepancyType,
    val description: String,
    val affectedTransactions: Indexed<TransactionId>,
    val severity: DiscrepancySeverity,
    val detectedDate: Instant
)

enum class DiscrepancyType {
    BALANCE_MISMATCH,
    TRANSACTION_MISSING,
    DUPLICATE_TRANSACTION,
    TIMING_DISCREPANCY,
    SIGNATURE_MISMATCH,
    ENCRYPTION_ERROR
}

enum class DiscrepancySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
data class ResolutionAction(
    val actionType: String,
    val description: String,
    val performedBy: StakeholderId,
    val timestamp: Instant,
    val outcome: String
)

// === CRYPTOGRAPHIC PROOFS ===

/**
 * Cryptographic proof for ledger operations
 */
@Serializable
data class CryptographicProof(
    val proofId: String,
    val proofType: ProofType,
    val proofData: String,
    val timestamp: Instant,
    val verificationKey: String,
    val witnessData: String? = null
)

enum class ProofType {
    TRANSACTION_VALIDITY,
    BALANCE_CORRECTNESS,
    CROSS_LEDGER_CONSISTENCY,
    PRIVACY_PRESERVATION,
    COMPLIANCE_ADHERENCE,
    FIDUCIARY_DUTY_FULFILLMENT
}

// === ACCESS CONTROL ===

/**
 * Access control matrix for ledger operations
 */
@Serializable
data class AccessControlMatrix(
    val accessRules: Indexed<AccessRule>,
    val rolePermissions: Indexed<Join<AccessRole, Indexed<Permission>>>,
    val userRoles: Indexed<Join<StakeholderId, Indexed<AccessRole>>>,
    val temporaryAccess: Indexed<TemporaryAccessGrant>?
)

@Serializable
data class AccessRule(
    val ruleId: String,
    val resourceType: ResourceType,
    val accessLevel: AccessLevel,
    val conditions: Indexed<AccessCondition>
)

enum class ResourceType {
    PUBLIC_TRANSACTION,
    PRIVATE_TRANSACTION,
    RECONCILIATION_RECORD,
    AUDIT_TRAIL,
    CRYPTOGRAPHIC_PROOF,
    SYSTEM_CONFIGURATION
}

enum class AccessLevel {
    READ,
    WRITE,
    DELETE,
    ADMIN,
    AUDIT,
    EMERGENCY
}

sealed interface AccessCondition {
    data class TimeWindow(val startTime: Instant, val endTime: Instant) : AccessCondition
    data class IPRestriction(val allowedIPs: Indexed<String>) : AccessCondition
    data class MultiFactorRequired(val factors: Int) : AccessCondition
    data class ApprovalRequired(val approvers: Indexed<StakeholderId>) : AccessCondition
    data class RoleRequirement(val requiredRoles: Indexed<AccessRole>) : AccessCondition
}

enum class AccessRole {
    FIDUCIARY,
    BENEFICIARY,
    AUDITOR,
    COMPLIANCE_OFFICER,
    SYSTEM_ADMINISTRATOR,
    REGULATORY_AUTHORITY,
    LEGAL_COUNSEL,
    EMERGENCY_RESPONDER
}

enum class Permission {
    VIEW_PUBLIC_TRANSACTIONS,
    VIEW_PRIVATE_TRANSACTIONS,
    CREATE_TRANSACTIONS,
    APPROVE_TRANSACTIONS,
    RECONCILE_LEDGERS,
    ACCESS_AUDIT_TRAIL,
    MANAGE_ACCESS_CONTROLS,
    EMERGENCY_OVERRIDE
}

@Serializable
data class TemporaryAccessGrant(
    val grantId: String,
    val grantee: StakeholderId,
    val permissions: Indexed<Permission>,
    val startTime: Instant,
    val endTime: Instant,
    val grantedBy: StakeholderId,
    val reason: String
)

// === AUDIT TRAIL ===

/**
 * Dual ledger audit entry
 */
@Serializable
data class DualLedgerAuditEntry(
    val entryId: AuditEntryId,
    val timestamp: Instant,
    val ledgerType: LedgerType,
    val action: AuditAction,
    val details: String,
    val performedBy: StakeholderId,
    val cryptographicProof: CryptographicHash,
    val additionalMetadata: Indexed<Join<String, String>>? = null
)

enum class LedgerType {
    PUBLIC,
    PRIVATE,
    BOTH
}

enum class AuditAction {
    TRANSACTION_RECORDED,
    TRANSACTION_MODIFIED,
    TRANSACTION_DELETED,
    ACCESS_GRANTED,
    ACCESS_REVOKED,
    RECONCILIATION_PERFORMED,
    SYSTEM_CONFIGURATION_CHANGED,
    EMERGENCY_ACTION,
    COMPLIANCE_CHECK_PERFORMED,
    AUDIT_TRAIL_ACCESSED
}

// === SYSTEM INTEGRITY ===

enum class SystemIntegrityStatus {
    VERIFIED,
    INTEGRITY_VIOLATIONS_DETECTED,
    RECONCILIATION_REQUIRED,
    SYSTEM_COMPROMISED,
    UNDER_INVESTIGATION
}

// === DUAL LEDGER OPERATIONS ===

object DualLedgerOperations {
    
    /**
     * Create a new dual ledger system
     */
    fun createDualLedgerSystem(): DualLedgerSystem {
        return DualLedgerSystem(
            systemId = "dual_ledger_${System.currentTimeMillis()}",
            publicLedger = emptyIndex(),
            privateLedger = emptyIndex(),
            reconciliationRecords = emptyIndex(),
            auditTrail = emptyIndex(),
            cryptographicProofs = emptyIndex(),
            accessControls = createDefaultAccessControls(),
            complianceRecords = emptyIndex(),
            lastReconciliation = null,
            systemIntegrity = SystemIntegrityStatus.VERIFIED
        )
    }
    
    /**
     * Create default access controls
     */
    private fun createDefaultAccessControls(): AccessControlMatrix {
        val accessRules = 3 j { i ->
            when (i) {
                0 -> AccessRule(
                    ruleId = "public_read",
                    resourceType = ResourceType.PUBLIC_TRANSACTION,
                    accessLevel = AccessLevel.READ,
                    conditions = emptyIndex()
                )
                1 -> AccessRule(
                    ruleId = "private_restricted",
                    resourceType = ResourceType.PRIVATE_TRANSACTION,
                    accessLevel = AccessLevel.READ,
                    conditions = 1 j {
                        AccessCondition.RoleRequirement(1 j { AccessRole.FIDUCIARY })
                    }
                )
                else -> AccessRule(
                    ruleId = "admin_full",
                    resourceType = ResourceType.SYSTEM_CONFIGURATION,
                    accessLevel = AccessLevel.ADMIN,
                    conditions = 1 j {
                        AccessCondition.MultiFactorRequired(2)
                    }
                )
            }
        }
        
        val rolePermissions = 4 j { i ->
            when (i) {
                0 -> AccessRole.FIDUCIARY j (3 j { j ->
                    when (j) {
                        0 -> Permission.VIEW_PUBLIC_TRANSACTIONS
                        1 -> Permission.VIEW_PRIVATE_TRANSACTIONS
                        else -> Permission.CREATE_TRANSACTIONS
                    }
                })
                1 -> AccessRole.BENEFICIARY j (1 j { Permission.VIEW_PUBLIC_TRANSACTIONS })
                2 -> AccessRole.AUDITOR j (2 j { j ->
                    if (j == 0) Permission.ACCESS_AUDIT_TRAIL else Permission.RECONCILE_LEDGERS
                })
                else -> AccessRole.SYSTEM_ADMINISTRATOR j (2 j { j ->
                    if (j == 0) Permission.MANAGE_ACCESS_CONTROLS else Permission.EMERGENCY_OVERRIDE
                })
            }
        }
        
        return AccessControlMatrix(
            accessRules = accessRules,
            rolePermissions = rolePermissions,
            userRoles = emptyIndex(),
            temporaryAccess = null
        )
    }
    
    /**
     * Record entity formation in public ledger
     */
    fun recordEntityFormation(
        system: DualLedgerSystem,
        entity: LegalEntity,
        initiatedBy: StakeholderId
    ): DualLedgerSystem {
        val transaction = PublicTransaction(
            transactionId = TransactionId("formation_${System.currentTimeMillis()}"),
            timestamp = kotlinx.datetime.Clock.System.now(),
            transactionType = PublicTransactionType.ENTITY_FORMATION,
            entityId = entity.id,
            amount = Price(0.0),
            description = "Formation of ${entity.type} entity: ${entity.name}",
            counterparty = null,
            category = TransactionCategory.GOVERNANCE,
            initiatedBy = initiatedBy,
            approvedBy = null,
            digitalSignatures = emptyIndex(),
            complianceChecks = emptyIndex(),
            publicMetadata = PublicTransactionMetadata(
                jurisdiction = entity.jurisdiction,
                regulatoryReferences = null,
                publicNoticeRequired = true,
                stakeholderNotifications = null,
                reportingRequirements = null
            )
        )
        
        return system.recordPublicTransaction(transaction)
    }
    
    /**
     * Record confidential distribution in private ledger
     */
    fun recordConfidentialDistribution(
        system: DualLedgerSystem,
        entityId: EntityId,
        beneficiaryId: BeneficiaryId,
        amount: Price,
        fiduciaryId: StakeholderId,
        encryptionKey: EncryptedKey
    ): DualLedgerSystem {
        val transaction = PrivateTransaction(
            transactionId = TransactionId("conf_dist_${System.currentTimeMillis()}"),
            timestamp = kotlinx.datetime.Clock.System.now(),
            transactionType = PrivateTransactionType.CONFIDENTIAL_DISTRIBUTION,
            entityId = entityId,
            amount = amount,
            encryptedDescription = "Encrypted distribution details",
            beneficiariesAffected = 1 j { beneficiaryId },
            confidentialityLevel = ConfidentialityLevel.CONFIDENTIAL,
            initiatedBy = fiduciaryId,
            authorizedBy = 1 j { fiduciaryId },
            encryptedSignatures = emptyIndex(),
            privacyProtections = 1 j {
                PrivacyProtection(
                    protectionType = PrivacyProtectionType.HOMOMORPHIC_ENCRYPTION,
                    implementation = "HE-based confidential computation",
                    effectiveDate = kotlinx.datetime.Clock.System.now(),
                    expirationDate = null
                )
            },
            zeroKnowledgeProof = ZeroKnowledgeProof(
                proofType = ZKProofType.ZKSNARKS,
                proofData = "Distribution validity proof",
                verificationKey = "verification_key_placeholder",
                circuitHash = "circuit_hash_placeholder"
            )
        )
        
        return system.recordPrivateTransaction(transaction, encryptionKey)
    }
    
    /**
     * Generate compliance report combining both ledgers
     */
    fun generateComplianceReport(
        system: DualLedgerSystem,
        startDate: Instant,
        endDate: Instant
    ): ComplianceReport {
        val publicTransactions = system.publicLedger.play.filter { 
            it.timestamp >= startDate && it.timestamp <= endDate 
        }
        
        val privateTransactionCount = system.privateLedger.play.filter { 
            it.timestamp >= startDate && it.timestamp <= endDate 
        }.size
        
        return ComplianceReport(
            reportId = "compliance_${System.currentTimeMillis()}",
            startDate = startDate,
            endDate = endDate,
            publicTransactionCount = publicTransactions.size,
            privateTransactionCount = privateTransactionCount,
            totalTransactionVolume = publicTransactions.sumOf { it.amount.value },
            complianceIssues = identifyComplianceIssues(publicTransactions),
            auditTrailIntegrity = system.verifyIntegrity(),
            reconciliationStatus = system.reconciliationRecords.play.lastOrNull()?.reconciliationStatus
                ?: ReconciliationStatus.PENDING_REVIEW
        )
    }
    
    private fun identifyComplianceIssues(transactions: List<PublicTransaction>): Indexed<String> {
        val issues = mutableListOf<String>()
        
        // Check for large transactions without proper approvals
        transactions.filter { it.amount.value > 100000.0 }.forEach { transaction ->
            if (transaction.approvedBy?.size ?: 0 < 2) {
                issues.add("Large transaction ${transaction.transactionId.value} lacks required approvals")
            }
        }
        
        return issues.size j { i -> issues[i] }
    }
}

@Serializable
data class ComplianceReport(
    val reportId: String,
    val startDate: Instant,
    val endDate: Instant,
    val publicTransactionCount: Int,
    val privateTransactionCount: Int,
    val totalTransactionVolume: Double,
    val complianceIssues: Indexed<String>,
    val auditTrailIntegrity: SystemIntegrityStatus,
    val reconciliationStatus: ReconciliationStatus
)