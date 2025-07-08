package borg.trikeshed.lib

import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.ExperimentalUnsignedTypes

// ===== BILL AND MAIL DATA TYPES =====

@Serializable
data class PhotoId(val value: String)

@Serializable
data class PhotoData(
    val id: PhotoId,
    val imageBytes: UByteArray,
    val metadata: PhotoMetadata
)

@Serializable
data class PhotoMetadata(
    val timestamp: Instant,
    val deviceId: String,
    val location: String,
    val resolution: String
)

@Serializable
data class BillId(val value: String)

@Serializable
data class BillData(
    val id: BillId,
    val accountNumber: String,
    val amount: Double,
    val dueDate: Instant,
    val merchant: String,
    val category: BillCategory
)

enum class BillCategory {
    UTILITIES, INTERNET, PHONE, ENTERTAINMENT, INSURANCE, HEALTH, CREDIT_CARD, LOAN
}

@Serializable
data class AnnotationId(val value: String)

@Serializable
data class ParticipantId(val value: String)

@Serializable
data class BillAnnotation(
    val id: AnnotationId,
    val participantId: ParticipantId,
    val content: String,
    val position: Int,
    val timestamp: Instant
)

@Serializable
sealed class LegalFlagType {
    @Serializable
    object ARBITRATION_CLAUSE : LegalFlagType()
    @Serializable
    object AUTO_RENEWAL : LegalFlagType()
    @Serializable
    object RATE_CHANGE : LegalFlagType()
    @Serializable
    object EARLY_TERMINATION_FEE : LegalFlagType()
    @Serializable
    object DATA_SHARING : LegalFlagType()
    @Serializable
    object CREDIT_CHECK : LegalFlagType()
    @Serializable
    object COLLECTION_PRACTICES : LegalFlagType()
}

@Serializable
data class LegalFlag(
    val type: LegalFlagType,
    val severity: String,
    val description: String,
    val position: Int
)

@Serializable
data class LegalContentExtraction(
    val termsOfService: List<String>,
    val legalFlags: List<LegalFlag>
)

@Serializable
sealed class AccountTraitType {
    @Serializable
    object PAYMENT_HISTORY : AccountTraitType()
    @Serializable
    object CREDIT_SCORE_IMPACT : AccountTraitType()
    @Serializable
    object AUTO_PAY_ENABLED : AccountTraitType()
    @Serializable
    object LATE_PAYMENT_HISTORY : AccountTraitType()
    @Serializable
    object ACCOUNT_AGE : AccountTraitType()
}

@Serializable
data class AccountTrait(
    val type: AccountTraitType,
    val value: String,
    val confidence: Double
)

@Serializable
data class AccountDetails(
    val accountNumber: String?,
    val accountType: String?,
    val customerName: String?,
    val billingAddress: String?,
    val traits: List<AccountTrait>
)

enum class ComplianceFlagType {
    DATA_PRIVACY, ARBITRATION, AUTO_RENEWAL, RATE_CHANGES, 
    CANCELLATION_POLICY, DISPUTE_RESOLUTION, COLLECTION_PRACTICES
}

@Serializable
data class ComplianceFlag(
    val type: ComplianceFlagType,
    val description: String,
    val riskLevel: String
)

@Serializable
data class TermsOfServiceAnalysis(
    val terms: List<String>,
    val complianceFlags: List<ComplianceFlag>,
    val riskLevel: String
)

// ===== COUCHDB TYPES =====

@Serializable
data class CouchChange(
    val id: String,
    val seq: String,
    val changes: List<CouchChangeItem>,
    val deleted: Boolean = false,
    val document: CouchDocument? = null
)

@Serializable
data class CouchChangeItem(val rev: String)

@Serializable
data class CouchDocument(
    val id: String? = null,
    val rev: String? = null,
    val deleted: Boolean? = null,
    val data: Map<String, String> = emptyMap()
)

// ===== GIT TYPES =====

@Serializable
data class GitCommit(
    val id: String,
    val message: String,
    val author: String?,
    val timestamp: Instant,
    val files: List<String>
) 