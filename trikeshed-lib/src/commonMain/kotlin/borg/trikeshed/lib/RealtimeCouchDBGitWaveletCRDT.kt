package borg.trikeshed.lib

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*
import kotlin.random.Random

/**
 * Realtime CouchDB Git Wavelet CRDT Implementation
 * 
 * Handles:
 * - Photo import and digitization
 * - Bill and mail processing
 * - Account details and traits extraction
 * - Terms of service analysis
 * - Real-time collaborative annotations
 * - Git-based version control
 * - CouchDB persistence with realtime sync
 */

// ===== CORE CRDT WAVELET IMPLEMENTATION =====

@Serializable
data class WaveletId(val value: String) {
    companion object {
        fun generate(): WaveletId = WaveletId("wavelet-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}")
    }
}

@Serializable
data class OperationId(val value: String) {
    companion object {
        fun generate(): OperationId = OperationId("op-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt()}")
    }
}

@Serializable
data class VectorClock(
    val participantId: String,
    val sequence: Long,
    val timestamp: Instant = Clock.System.now()
) {
    fun increment(): VectorClock = copy(
        sequence = sequence + 1,
        timestamp = Clock.System.now()
    )
}

@Serializable
data class WaveletOperation(
    val id: OperationId,
    val participantId: String,
    val operationType: WaveletOperationType,
    val data: String, // JSON serialized operation data
    val vectorClock: VectorClock,
    val dependencies: List<OperationId> = emptyList(),
    val timestamp: Instant = Clock.System.now()
)

enum class WaveletOperationType {
    CREATE_BILL,
    ADD_ANNOTATION,
    UPDATE_BILL,
    EXTRACT_ACCOUNT_DETAILS,
    ANALYZE_TERMS_OF_SERVICE,
    ADD_LEGAL_FLAG
}

@Serializable
data class BillWaveletState(
    val billData: BillData? = null,
    val annotations: Indexed<BillAnnotation> = 0 j { throw IndexOutOfBoundsException() },
    val accountDetails: AccountDetails? = null,
    val legalAnalysis: TermsOfServiceAnalysis? = null,
    val version: Long = 0,
    val lastModified: Instant = Clock.System.now()
) {
    fun addAnnotation(annotation: BillAnnotation): BillWaveletState = copy(
        annotations = (annotations.a + 1) j { i: Int ->
            if (i < annotations.a) annotations.b(i) else annotation
        },
        version = version + 1,
        lastModified = Clock.System.now()
    )
    
    fun updateBillData(billData: BillData): BillWaveletState = copy(
        billData = billData,
        version = version + 1,
        lastModified = Clock.System.now()
    )
    
    fun updateAccountDetails(details: AccountDetails): BillWaveletState = copy(
        accountDetails = details,
        version = version + 1,
        lastModified = Clock.System.now()
    )
    
    fun updateLegalAnalysis(analysis: TermsOfServiceAnalysis): BillWaveletState = copy(
        legalAnalysis = analysis,
        version = version + 1,
        lastModified = Clock.System.now()
    )
}

/**
 * Bill Wavelet CRDT Implementation
 */
class BillWavelet(
    val id: WaveletId,
    internal var state: BillWaveletState = BillWaveletState(),
    internal val operations: MutableList<WaveletOperation> = mutableListOf(),
    internal val participants: MutableSet<String> = mutableSetOf()
) {
    
    fun getCurrentState(): BillWaveletState = state
    
    fun addAnnotation(annotation: BillAnnotation) {
        val operation = WaveletOperation(
            id = OperationId.generate(),
            participantId = annotation.participantId.value,
            operationType = WaveletOperationType.ADD_ANNOTATION,
            data = annotation.toJson(),
            vectorClock = VectorClock(annotation.participantId.value, 0)
        )
        
        applyOperation(operation)
    }
    
    fun applyAnnotation(annotation: BillAnnotation): Result<Unit> {
        return try {
            addAnnotation(annotation)
            participants.add(annotation.participantId.value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getTransformedOperations(): List<WaveletOperation> = operations.toList()
    
    fun getParticipants(): Set<String> = participants.toSet()
    
    internal fun applyOperation(operation: WaveletOperation) {
        when (operation.operationType) {
            WaveletOperationType.CREATE_BILL -> {
                val billData = operation.data.fromJson<BillData>()
                state = state.updateBillData(billData)
            }
            WaveletOperationType.ADD_ANNOTATION -> {
                val annotation = operation.data.fromJson<BillAnnotation>()
                state = state.addAnnotation(annotation)
            }
            WaveletOperationType.UPDATE_BILL -> {
                val billData = operation.data.fromJson<BillData>()
                state = state.updateBillData(billData)
            }
            WaveletOperationType.EXTRACT_ACCOUNT_DETAILS -> {
                val details = operation.data.fromJson<AccountDetails>()
                state = state.updateAccountDetails(details)
            }
            WaveletOperationType.ANALYZE_TERMS_OF_SERVICE -> {
                val analysis = operation.data.fromJson<TermsOfServiceAnalysis>()
                state = state.updateLegalAnalysis(analysis)
            }
            WaveletOperationType.ADD_LEGAL_FLAG -> {
                // Handle legal flag addition
            }
        }
        
        operations.add(operation)
        participants.add(operation.participantId)
    }
    
    companion object {
        fun create(billData: BillData): BillWavelet {
            val wavelet = BillWavelet(WaveletId.generate())
            val operation = WaveletOperation(
                id = OperationId.generate(),
                participantId = "system",
                operationType = WaveletOperationType.CREATE_BILL,
                data = billData.toJson(),
                vectorClock = VectorClock("system", 0)
            )
            wavelet.applyOperation(operation)
            return wavelet
        }
    }
}

// ===== PHOTO PROCESSING PIPELINES =====

object BillDigitizationPipeline {
    suspend fun process(photoData: PhotoData): Result<BillData> {
        return try {
            // Simulate OCR processing
            val extractedText = performOCR(photoData.imageBytes)
            
            // Extract bill information using regex patterns
            val accountNumber = extractAccountNumber(extractedText)
            val amount = extractAmount(extractedText)
            val dueDate = extractDueDate(extractedText)
            val merchant = extractMerchant(extractedText)
            val category = classifyBill(extractedText)
            
            val billData = BillData(
                id = BillId("bill-${Clock.System.now().toEpochMilliseconds()}"),
                accountNumber = accountNumber,
                amount = amount,
                dueDate = dueDate,
                merchant = merchant,
                category = category
            )
            
            Result.success(billData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    internal suspend fun performOCR(imageBytes: UByteArray): String {
        // Simulate OCR processing
        delay(100) // Simulate processing time
        return "Account: 1234567890\nAmount: \$299.99\nDue Date: 2024-02-15\nMerchant: Utility Company"
    }
    
    internal fun extractAccountNumber(text: String): String {
        val regex = Regex("Account:\\s*(\\d+)")
        return regex.find(text)?.groupValues?.get(1) ?: "Unknown"
    }
    
    internal fun extractAmount(text: String): Double {
        val regex = Regex("Amount:\\s*\\$(\\d+\\.\\d+)")
        return regex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }
    
    internal fun extractDueDate(text: String): Instant {
        val regex = Regex("Due Date:\\s*(\\d{4}-\\d{2}-\\d{2})")
        val dateStr = regex.find(text)?.groupValues?.get(1) ?: "2024-02-15"
        return Instant.parse("${dateStr}T00:00:00Z")
    }
    
    internal fun extractMerchant(text: String): String {
        val regex = Regex("Merchant:\\s*(.+)")
        return regex.find(text)?.groupValues?.get(1) ?: "Unknown Merchant"
    }
    
    internal fun classifyBill(text: String): BillCategory {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("utility") -> BillCategory.UTILITIES
            lowerText.contains("internet") -> BillCategory.INTERNET
            lowerText.contains("phone") -> BillCategory.PHONE
            lowerText.contains("streaming") -> BillCategory.ENTERTAINMENT
            lowerText.contains("insurance") -> BillCategory.INSURANCE
            lowerText.contains("gym") -> BillCategory.HEALTH
            else -> BillCategory.UTILITIES
        }
    }
}

object LegalContentExtractor {
    suspend fun extract(photoData: PhotoData): LegalContentExtraction {
        // Simulate legal content extraction
        val extractedText = performOCR(photoData.imageBytes)
        
        val terms = extractTermsOfService(extractedText)
        val flags = extractLegalFlags(extractedText)
        
        return LegalContentExtraction(terms, flags)
    }
    
    internal suspend fun performOCR(imageBytes: UByteArray): String {
        delay(150) // Simulate processing time
        return """
            Terms of Service:
            - Auto-renewal clause
            - Arbitration agreement
            - Rate changes may occur
            - Early termination fees apply
        """.trimIndent()
    }
    
    internal fun extractTermsOfService(text: String): List<String> {
        val lines = text.lines()
        return lines.filter { it.startsWith("-") || it.startsWith("•") }
            .map { it.trim().removePrefix("- ").removePrefix("• ") }
    }
    
    internal fun extractLegalFlags(text: String): List<LegalFlag> {
        val flags = mutableListOf<LegalFlag>()
        val lowerText = text.lowercase()
        
        if (lowerText.contains("auto-renewal")) {
            flags.add(LegalFlag(LegalFlagType.AUTO_RENEWAL, "HIGH", "Auto-renewal clause detected", 0))
        }
        if (lowerText.contains("arbitration")) {
            flags.add(LegalFlag(LegalFlagType.ARBITRATION_CLAUSE, "MEDIUM", "Arbitration clause detected", 1))
        }
        if (lowerText.contains("rate change")) {
            flags.add(LegalFlag(LegalFlagType.RATE_CHANGE, "MEDIUM", "Rate change clause detected", 2))
        }
        
        return flags
    }
}

object AccountDetailsExtractor {
    suspend fun extract(photoData: PhotoData): Result<AccountDetails> {
        return try {
            val extractedText = performOCR(photoData.imageBytes)
            
            val accountNumber = extractAccountNumber(extractedText)
            val accountType = extractAccountType(extractedText)
            val customerName = extractCustomerName(extractedText)
            val billingAddress = extractBillingAddress(extractedText)
            val traits = extractTraits(extractedText)
            
            val details = AccountDetails(
                accountNumber = accountNumber,
                accountType = accountType,
                customerName = customerName,
                billingAddress = billingAddress,
                traits = traits
            )
            
            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    internal suspend fun performOCR(imageBytes: UByteArray): String {
        delay(120) // Simulate processing time
        return """
            Account Number: 1234567890
            Account Type: Credit Card
            Customer: John Doe
            Address: 123 Main St, City, State 12345
            Payment History: Good
            Auto-Pay: Enabled
        """.trimIndent()
    }
    
    internal fun extractAccountNumber(text: String): String? {
        val regex = Regex("Account Number:\\s*(\\d+)")
        return regex.find(text)?.groupValues?.get(1)
    }
    
    internal fun extractAccountType(text: String): String? {
        val regex = Regex("Account Type:\\s*(.+)")
        return regex.find(text)?.groupValues?.get(1)
    }
    
    internal fun extractCustomerName(text: String): String? {
        val regex = Regex("Customer:\\s*(.+)")
        return regex.find(text)?.groupValues?.get(1)
    }
    
    internal fun extractBillingAddress(text: String): String? {
        val regex = Regex("Address:\\s*(.+)")
        return regex.find(text)?.groupValues?.get(1)
    }
    
    internal fun extractTraits(text: String): List<AccountTrait> {
        val traits = mutableListOf<AccountTrait>()
        
        if (text.contains("Payment History: Good")) {
            traits.add(AccountTrait(AccountTraitType.PAYMENT_HISTORY, "Good", 0.9))
        }
        if (text.contains("Auto-Pay: Enabled")) {
            traits.add(AccountTrait(AccountTraitType.AUTO_PAY_ENABLED, "Enabled", 0.95))
        }
        
        return traits
    }
}

object TermsOfServiceAnalyzer {
    suspend fun analyze(photoData: PhotoData): Result<TermsOfServiceAnalysis> {
        return try {
            val extractedText = performOCR(photoData.imageBytes)
            
            val terms = extractTerms(extractedText)
            val complianceFlags = extractComplianceFlags(extractedText)
            val riskLevel = assessRiskLevel(complianceFlags)
            
            val analysis = TermsOfServiceAnalysis(
                terms = terms,
                complianceFlags = complianceFlags,
                riskLevel = riskLevel
            )
            
            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    internal suspend fun performOCR(imageBytes: UByteArray): String {
        delay(180) // Simulate processing time
        return """
            Terms of Service:
            - Data may be shared with third parties
            - Disputes resolved through arbitration
            - Rates may change with 30 days notice
            - Auto-renewal unless cancelled
            - Collection practices follow industry standards
        """.trimIndent()
    }
    
    internal fun extractTerms(text: String): List<String> {
        return text.lines()
            .filter { it.startsWith("-") }
            .map { it.trim().removePrefix("- ") }
    }
    
    internal fun extractComplianceFlags(text: String): List<ComplianceFlag> {
        val flags = mutableListOf<ComplianceFlag>()
        val lowerText = text.lowercase()
        
        if (lowerText.contains("data") && lowerText.contains("shared")) {
            flags.add(ComplianceFlag(ComplianceFlagType.DATA_PRIVACY, "Data sharing clause", "MEDIUM"))
        }
        if (lowerText.contains("arbitration")) {
            flags.add(ComplianceFlag(ComplianceFlagType.ARBITRATION, "Arbitration clause", "HIGH"))
        }
        if (lowerText.contains("auto-renewal")) {
            flags.add(ComplianceFlag(ComplianceFlagType.AUTO_RENEWAL, "Auto-renewal clause", "MEDIUM"))
        }
        if (lowerText.contains("rate") && lowerText.contains("change")) {
            flags.add(ComplianceFlag(ComplianceFlagType.RATE_CHANGES, "Rate change clause", "MEDIUM"))
        }
        
        return flags
    }
    
    internal fun assessRiskLevel(flags: List<ComplianceFlag>): String {
        val highRiskFlags = flags.count { it.riskLevel == "HIGH" }
        val mediumRiskFlags = flags.count { it.riskLevel == "MEDIUM" }
        
        return when {
            highRiskFlags >= 2 -> "HIGH"
            highRiskFlags >= 1 || mediumRiskFlags >= 3 -> "MEDIUM"
            else -> "LOW"
        }
    }
}

// ===== COUCHDB SERVICE IMPLEMENTATION =====

class CouchDBWaveletService {
    internal val documents = mutableMapOf<String, CouchDocument>()
    internal val changesFlow = MutableSharedFlow<CouchChange>()
    
    suspend fun persistWavelet(wavelet: BillWavelet): Result<String> {
        return try {
            val documentId = "wavelet-${wavelet.id.value}"
            val document = CouchDocument(
                id = documentId,
                data = mapOf(
                    "waveletId" to wavelet.id.value,
                    "state" to wavelet.getCurrentState().toJson(),
                    "operations" to wavelet.getTransformedOperations().toJson(),
                    "participants" to wavelet.getParticipants().toJson(),
                    "timestamp" to Clock.System.now().toString()
                )
            )
            
            documents[documentId] = document
            
            // Emit change
            changesFlow.emit(CouchChange(
                id = documentId,
                seq = Clock.System.now().toEpochMilliseconds().toString(),
                changes = listOf(CouchChangeItem("1-${Clock.System.now().toEpochMilliseconds()}")),
                document = document
            ))
            
            Result.success(documentId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getWavelet(documentId: String): BillWavelet? {
        val document = documents[documentId] ?: return null
        
        return try {
            val waveletId = WaveletId(document.data["waveletId"] ?: "")
            val state = document.data["state"]?.fromJson<BillWaveletState>() ?: BillWaveletState()
            val operations = document.data["operations"]?.fromJson<List<WaveletOperation>>() ?: emptyList()
            val participants = document.data["participants"]?.fromJson<Set<String>>() ?: emptySet()
            
            BillWavelet(waveletId, state, operations.toMutableList(), participants.toMutableSet())
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun updateWavelet(documentId: String, wavelet: BillWavelet): Result<Unit> {
        return try {
            val document = CouchDocument(
                id = documentId,
                data = mapOf(
                    "waveletId" to wavelet.id.value,
                    "state" to wavelet.getCurrentState().toJson(),
                    "operations" to wavelet.getTransformedOperations().toJson(),
                    "participants" to wavelet.getParticipants().toJson(),
                    "timestamp" to Clock.System.now().toString()
                )
            )
            
            documents[documentId] = document
            
            // Emit change
            changesFlow.emit(CouchChange(
                id = documentId,
                seq = Clock.System.now().toEpochMilliseconds().toString(),
                changes = listOf(CouchChangeItem("2-${Clock.System.now().toEpochMilliseconds()}")),
                document = document
            ))
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getChangesFeed(database: String): Flow<CouchChange> = changesFlow.asSharedFlow()
}

// ===== GIT SERVICE IMPLEMENTATION =====

class GitWaveletService {
    internal val repositories = mutableMapOf<String, MutableList<GitCommit>>()
    
    fun createRepository(name: String): String {
        repositories[name] = mutableListOf()
        return name
    }
    
    fun createCommitsFromWavelet(wavelet: BillWavelet): List<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        
        // Create initial commit for bill creation
        if (wavelet.getCurrentState().billData != null) {
            commits.add(GitCommit(
                id = "commit-${Clock.System.now().toEpochMilliseconds()}",
                message = "Create bill: ${wavelet.getCurrentState().billData!!.merchant}",
                author = "system",
                timestamp = Clock.System.now(),
                files = listOf("bill.json", "wavelet.json")
            ))
        }
        
        // Create commits for annotations
        wavelet.getCurrentState().annotations.toList().forEach { annotation ->
            commits.add(GitCommit(
                id = "commit-${Clock.System.now().toEpochMilliseconds()}-${annotation.id.value}",
                message = "Add annotation: ${annotation.content}",
                author = annotation.participantId.value,
                timestamp = annotation.timestamp,
                files = listOf("annotations.json")
            ))
        }
        
        return commits
    }
    
    fun commitWaveletOperation(repository: String, wavelet: BillWavelet, operation: BillAnnotation): Result<Unit> {
        return try {
            val repo = repositories[repository] ?: mutableListOf()
            val commit = GitCommit(
                id = "commit-${Clock.System.now().toEpochMilliseconds()}",
                message = "Add annotation: ${operation.content}",
                author = operation.participantId.value,
                timestamp = operation.timestamp,
                files = listOf("annotations.json")
            )
            repo.add(commit)
            repositories[repository] = repo
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun replayWaveletHistory(repository: String, waveletId: BillId): BillWavelet? {
        val repo = repositories[repository] ?: return null
        
        // Recreate wavelet from git history
        val wavelet = BillWavelet(WaveletId(waveletId.value))
        
        repo.forEach { commit ->
            // Apply commit changes to wavelet
            // This is a simplified implementation
        }
        
        return wavelet
    }
}

// ===== JSON SERIALIZATION EXTENSIONS =====

internal fun <T> T.toJson(): String {
    // Simplified JSON serialization
    return when (this) {
        is BillData -> """{"id":"${id.value}","accountNumber":"$accountNumber","amount":$amount,"merchant":"$merchant","category":"$category"}"""
        is BillAnnotation -> """{"id":"${id.value}","participantId":"${participantId.value}","content":"$content","position":$position}"""
        is BillWaveletState -> """{"version":$version,"annotationsCount":${annotations.size}}"""
        is List<*> -> """[${joinToString(",") { it.toString() }}]"""
        is Set<*> -> """[${joinToString(",") { it.toString() }}]"""
        else -> toString()
    }
}

internal inline fun <reified T> String.fromJson(): T {
    // Simplified JSON deserialization
    return when (T::class) {
        BillData::class -> {
            val regex = Regex(""""id":"([^"]+)","accountNumber":"([^"]+)","amount":([^,]+),"merchant":"([^"]+)","category":"([^"]+)"""")
            val match = regex.find(this)
            if (match != null) {
                BillData(
                    id = BillId(match.groupValues[1]),
                    accountNumber = match.groupValues[2],
                    amount = match.groupValues[3].toDoubleOrNull() ?: 0.0,
                    dueDate = Clock.System.now(),
                    merchant = match.groupValues[4],
                    category = BillCategory.valueOf(match.groupValues[5])
                ) as T
            } else {
                throw IllegalArgumentException("Invalid JSON format")
            }
        }
        BillAnnotation::class -> {
            val regex = Regex(""""id":"([^"]+)","participantId":"([^"]+)","content":"([^"]+)","position":(\d+)""")
            val match = regex.find(this)
            if (match != null) {
                BillAnnotation(
                    id = AnnotationId(match.groupValues[1]),
                    participantId = ParticipantId(match.groupValues[2]),
                    content = match.groupValues[3],
                    position = match.groupValues[4].toInt(),
                    timestamp = Clock.System.now()
                ) as T
            } else {
                throw IllegalArgumentException("Invalid JSON format")
            }
        }
        BillWaveletState::class -> BillWaveletState() as T
        List::class -> emptyList<Any>() as T
        Set::class -> emptySet<String>() as T
        else -> throw IllegalArgumentException("Unsupported type")
    }
} 