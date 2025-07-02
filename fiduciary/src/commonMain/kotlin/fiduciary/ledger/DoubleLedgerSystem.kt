package fiduciary.ledger

import borg.trikeshed.lib.*
import borg.trikeshed.common.*
import io.islandtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Double-entry ledger system for fiduciary interests and debentures
 * Tracks both printed (physical) and digital instruments with full audit trail
 * 
 * Core principle: Every fiduciary interest exists in two states:
 * 1. Physical/Printed - The original instrument (will, trust deed, debenture certificate)
 * 2. Digital/Recorded - The digitized record with cryptographic proof
 * 
 * Both must reconcile for validity
 */
class DoubleLedgerSystem {
    
    /**
     * Fiduciary instrument with dual existence tracking
     */
    data class FiduciaryInstrument(
        val instrumentId: String,
        val type: InstrumentType,
        val printedRecord: PrintedRecord,
        val digitalRecord: DigitalRecord,
        val reconciliationStatus: ReconciliationStatus,
        val auditTrail: Indexed<AuditEntry>
    )
    
    /**
     * Types of fiduciary instruments
     */
    enum class InstrumentType {
        TRUST_DEED,
        WILL,
        DEBENTURE,
        BOND,
        PROMISSORY_NOTE,
        POWER_OF_ATTORNEY,
        BENEFICIARY_DESIGNATION,
        FIDUCIARY_APPOINTMENT,
        ESTATE_DOCUMENT,
        CUSTODIAL_AGREEMENT
    }
    
    /**
     * Physical/printed instrument record
     */
    data class PrintedRecord(
        val documentId: String,
        val creationDate: LocalDate,
        val signatories: Indexed<Signatory>,
        val witnessedBy: Indexed<Witness>,
        val physicalLocation: PhysicalLocation,
        val custodian: Custodian,
        val condition: DocumentCondition,
        val scanHash: String?,  // Hash of scanned image if digitized
        val notarization: Notarization?
    )
    
    /**
     * Digital instrument record
     */
    data class DigitalRecord(
        val digitalId: String,
        val captureDate: Instant,
        val captureMethod: CaptureMethod,
        val contentHash: String,
        val blockchainRef: String?,
        val ipfsHash: String?,
        val encryptedContent: ByteArray,
        val metadata: DocumentMetadata,
        val attestations: Indexed<DigitalAttestation>
    )
    
    /**
     * Reconciliation between physical and digital
     */
    data class ReconciliationStatus(
        val isReconciled: Boolean,
        val lastReconciled: Instant?,
        val discrepancies: Indexed<Discrepancy>,
        val verificationMethod: VerificationMethod,
        val confidence: Double  // 0.0 to 1.0
    )
    
    /**
     * Document capture methods
     */
    enum class CaptureMethod {
        HIGH_RES_SCAN,
        PHOTOGRAPH,
        OCR_TRANSCRIPTION,
        MANUAL_ENTRY,
        CERTIFIED_COPY,
        NOTARIZED_SCAN,
        BLOCKCHAIN_NOTARY,
        WITNESS_ATTESTATION
    }
    
    /**
     * Ledger entries for double-entry bookkeeping
     */
    data class LedgerEntry(
        val entryId: String,
        val timestamp: Instant,
        val instrumentId: String,
        val debitSide: LedgerSide,
        val creditSide: LedgerSide,
        val amount: MonetaryValue?,
        val description: String,
        val reversalOf: String?,  // For corrections
        val approvedBy: Approver
    )
    
    /**
     * Ledger side (debit or credit)
     */
    data class LedgerSide(
        val account: Account,
        val party: Party,
        val amount: MonetaryValue?,
        val instrumentRef: String
    )
    
    /**
     * Account types for fiduciary ledger
     */
    enum class AccountType {
        TRUST_PRINCIPAL,
        TRUST_INCOME,
        BENEFICIARY_INTEREST,
        FIDUCIARY_LIABILITY,
        CONTINGENT_INTEREST,
        REMAINDER_INTEREST,
        LIFE_ESTATE,
        DEBENTURE_OBLIGATION,
        CUSTODIAL_ASSET
    }
    
    /**
     * Main ledger operations
     */
    interface LedgerOperations {
        suspend fun recordInstrument(
            printed: PrintedRecord,
            digital: DigitalRecord
        ): FiduciaryInstrument
        
        suspend fun reconcileRecords(
            instrumentId: String,
            verificationData: VerificationData
        ): ReconciliationStatus
        
        suspend fun createLedgerEntry(
            instrument: FiduciaryInstrument,
            transaction: FiduciaryTransaction
        ): LedgerEntry
        
        suspend fun queryLedger(
            filter: LedgerFilter
        ): Flow<LedgerEntry>
        
        suspend fun generateAuditReport(
            dateRange: ClosedRange<LocalDate>,
            instrumentTypes: Set<InstrumentType>
        ): AuditReport
    }
    
    /**
     * Implementation of double-entry ledger
     */
    class DoubleLedgerImpl : LedgerOperations {
        
        private val printedLedger = mutableMapOf<String, PrintedRecord>()
        private val digitalLedger = mutableMapOf<String, DigitalRecord>()
        private val journalEntries = mutableListOf<LedgerEntry>()
        private val reconciliations = mutableMapOf<String, ReconciliationStatus>()
        
        override suspend fun recordInstrument(
            printed: PrintedRecord,
            digital: DigitalRecord
        ): FiduciaryInstrument = coroutineScope {
            val instrumentId = generateInstrumentId(printed, digital)
            
            // Verify dual existence
            val reconciliation = performReconciliation(printed, digital)
            
            // Record in both ledgers
            printedLedger[instrumentId] = printed
            digitalLedger[instrumentId] = digital
            reconciliations[instrumentId] = reconciliation
            
            // Create initial audit entry
            val auditEntry = AuditEntry(
                timestamp = Clock.System.now(),
                action = AuditAction.INSTRUMENT_CREATED,
                performedBy = "System",
                details = "Dual record created: ${printed.documentId} ↔ ${digital.digitalId}",
                hash = computeAuditHash(printed, digital)
            )
            
            FiduciaryInstrument(
                instrumentId = instrumentId,
                type = determineInstrumentType(printed, digital),
                printedRecord = printed,
                digitalRecord = digital,
                reconciliationStatus = reconciliation,
                auditTrail = idx[auditEntry]
            )
        }
        
        override suspend fun reconcileRecords(
            instrumentId: String,
            verificationData: VerificationData
        ): ReconciliationStatus = coroutineScope {
            val printed = printedLedger[instrumentId] 
                ?: throw IllegalArgumentException("No printed record for $instrumentId")
            val digital = digitalLedger[instrumentId]
                ?: throw IllegalArgumentException("No digital record for $instrumentId")
            
            val discrepancies = mutableListOf<Discrepancy>()
            
            // Verify content hash
            if (printed.scanHash != null && printed.scanHash != digital.contentHash) {
                discrepancies.add(Discrepancy(
                    type = DiscrepancyType.HASH_MISMATCH,
                    description = "Scan hash does not match digital content hash",
                    severity = Severity.HIGH
                ))
            }
            
            // Verify dates
            val printedInstant = printed.creationDate.atTime(0, 0).toInstant(TimeZone.UTC)
            if (digital.captureDate < printedInstant) {
                discrepancies.add(Discrepancy(
                    type = DiscrepancyType.TEMPORAL_ANOMALY,
                    description = "Digital capture predates printed creation",
                    severity = Severity.CRITICAL
                ))
            }
            
            // Verify signatories match digital attestations
            val signatoriesMatch = verifySignatories(printed.signatories, digital.attestations)
            if (!signatoriesMatch) {
                discrepancies.add(Discrepancy(
                    type = DiscrepancyType.SIGNATORY_MISMATCH,
                    description = "Physical signatories do not match digital attestations",
                    severity = Severity.HIGH
                ))
            }
            
            val confidence = calculateConfidence(discrepancies)
            
            ReconciliationStatus(
                isReconciled = discrepancies.isEmpty(),
                lastReconciled = Clock.System.now(),
                discrepancies = discrepancies.toIdx(),
                verificationMethod = verificationData.method,
                confidence = confidence
            )
        }
        
        override suspend fun createLedgerEntry(
            instrument: FiduciaryInstrument,
            transaction: FiduciaryTransaction
        ): LedgerEntry = coroutineScope {
            val entry = LedgerEntry(
                entryId = generateEntryId(),
                timestamp = Clock.System.now(),
                instrumentId = instrument.instrumentId,
                debitSide = transaction.debitSide,
                creditSide = transaction.creditSide,
                amount = transaction.amount,
                description = transaction.description,
                reversalOf = null,
                approvedBy = transaction.approvedBy
            )
            
            // Ensure double-entry balance
            require(transaction.isBalanced()) { 
                "Transaction must balance: debit != credit" 
            }
            
            journalEntries.add(entry)
            
            // Update instrument audit trail
            updateAuditTrail(instrument, entry)
            
            entry
        }
        
        override suspend fun queryLedger(
            filter: LedgerFilter
        ): Flow<LedgerEntry> = flow {
            journalEntries
                .filter { entry → filter.matches(entry) }
                .forEach { emit(it) }
        }
        
        override suspend fun generateAuditReport(
            dateRange: ClosedRange<LocalDate>,
            instrumentTypes: Set<InstrumentType>
        ): AuditReport = coroutineScope {
            val entries = journalEntries.filter { entry →
                val date = entry.timestamp.toLocalDateTime(TimeZone.UTC).date
                date in dateRange
            }
            
            AuditReport(
                period = dateRange,
                totalEntries = entries.size,
                reconciledInstruments = reconciliations.count { it.value.isReconciled },
                unreconciledInstruments = reconciliations.count { !it.value.isReconciled },
                discrepancySummary = summarizeDiscrepancies(reconciliations.values),
                ledgerHash = computeLedgerHash(entries),
                generatedAt = Clock.System.now()
            )
        }
        
        private fun performReconciliation(
            printed: PrintedRecord,
            digital: DigitalRecord
        ): ReconciliationStatus {
            // Simplified reconciliation logic
            val isReconciled = printed.scanHash == digital.contentHash
            return ReconciliationStatus(
                isReconciled = isReconciled,
                lastReconciled = if (isReconciled) Clock.System.now() else null,
                discrepancies = emptyIndexed(),
                verificationMethod = VerificationMethod.HASH_COMPARISON,
                confidence = if (isReconciled) 1.0 else 0.5
            )
        }
        
        private fun calculateConfidence(discrepancies: List<Discrepancy>): Double {
            if (discrepancies.isEmpty()) return 1.0
            
            val severityScore = discrepancies.sumOf { 
                when (it.severity) {
                    Severity.LOW → 0.1
                    Severity.MEDIUM → 0.3
                    Severity.HIGH → 0.5
                    Severity.CRITICAL → 0.8
                }
            }
            
            return maxOf(0.0, 1.0 - severityScore)
        }
    }
    
    // Supporting data classes
    data class Signatory(
        val name: String,
        val role: String,
        val signatureDate: LocalDate
    )
    
    data class Witness(
        val name: String,
        val address: String,
        val attestationDate: LocalDate
    )
    
    data class PhysicalLocation(
        val facility: String,
        val room: String,
        val cabinet: String,
        val folder: String
    )
    
    data class Custodian(
        val name: String,
        val institution: String,
        val contactInfo: String
    )
    
    data class DocumentCondition(
        val status: ConditionStatus,
        val notes: String,
        val lastInspected: LocalDate
    )
    
    enum class ConditionStatus {
        EXCELLENT, GOOD, FAIR, POOR, DAMAGED, DESTROYED
    }
    
    data class Notarization(
        val notaryName: String,
        val commission: String,
        val date: LocalDate,
        val seal: String
    )
    
    data class DigitalAttestation(
        val attestorId: String,
        val publicKey: String,
        val signature: String,
        val timestamp: Instant
    )
    
    data class DocumentMetadata(
        val title: String,
        val parties: Indexed<String>,
        val executionDate: LocalDate,
        val jurisdiction: String,
        val tags: Set<String>
    )
    
    data class Discrepancy(
        val type: DiscrepancyType,
        val description: String,
        val severity: Severity
    )
    
    enum class DiscrepancyType {
        HASH_MISMATCH,
        TEMPORAL_ANOMALY,
        SIGNATORY_MISMATCH,
        MISSING_ATTESTATION,
        CUSTODY_CHAIN_BREAK,
        VERSION_CONFLICT
    }
    
    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    data class MonetaryValue(
        val amount: Double,
        val currency: String
    )
    
    data class Account(
        val accountId: String,
        val type: AccountType,
        val name: String
    )
    
    data class Party(
        val partyId: String,
        val name: String,
        val role: String
    )
    
    data class FiduciaryTransaction(
        val debitSide: LedgerSide,
        val creditSide: LedgerSide,
        val amount: MonetaryValue?,
        val description: String,
        val approvedBy: Approver
    ) {
        fun isBalanced(): Boolean = 
            debitSide.amount?.amount == creditSide.amount?.amount
    }
    
    data class Approver(
        val name: String,
        val role: String,
        val timestamp: Instant
    )
    
    data class VerificationData(
        val method: VerificationMethod,
        val evidence: Map<String, Any>
    )
    
    enum class VerificationMethod {
        HASH_COMPARISON,
        VISUAL_INSPECTION,
        FORENSIC_ANALYSIS,
        WITNESS_CONFIRMATION,
        NOTARY_VERIFICATION
    }
    
    data class AuditEntry(
        val timestamp: Instant,
        val action: AuditAction,
        val performedBy: String,
        val details: String,
        val hash: String
    )
    
    enum class AuditAction {
        INSTRUMENT_CREATED,
        INSTRUMENT_MODIFIED,
        RECONCILIATION_PERFORMED,
        LEDGER_ENTRY_CREATED,
        DISCREPANCY_NOTED,
        VERIFICATION_COMPLETED
    }
    
    data class AuditReport(
        val period: ClosedRange<LocalDate>,
        val totalEntries: Int,
        val reconciledInstruments: Int,
        val unreconciledInstruments: Int,
        val discrepancySummary: Map<DiscrepancyType, Int>,
        val ledgerHash: String,
        val generatedAt: Instant
    )
    
    interface LedgerFilter {
        fun matches(entry: LedgerEntry): Boolean
    }
    
    // Utility functions
    private fun generateInstrumentId(printed: PrintedRecord, digital: DigitalRecord): String =
        "FID-${printed.documentId}-${digital.digitalId}".take(32)
    
    private fun generateEntryId(): String =
        "LED-${Clock.System.now().toEpochMilliseconds()}"
    
    private fun determineInstrumentType(printed: PrintedRecord, digital: DigitalRecord): InstrumentType =
        InstrumentType.TRUST_DEED // Simplified
    
    private fun computeAuditHash(printed: PrintedRecord, digital: DigitalRecord): String =
        "${printed.hashCode()}-${digital.hashCode()}" // Simplified
    
    private fun computeLedgerHash(entries: List<LedgerEntry>): String =
        entries.map { it.hashCode() }.joinToString("-")
    
    private fun verifySignatories(
        physical: Indexed<Signatory>, 
        digital: Indexed<DigitalAttestation>
    ): Boolean = physical.size == digital.size // Simplified
    
    private fun updateAuditTrail(instrument: FiduciaryInstrument, entry: LedgerEntry) {
        // Update audit trail implementation
    }
    
    private fun summarizeDiscrepancies(
        statuses: Collection<ReconciliationStatus>
    ): Map<DiscrepancyType, Int> =
        statuses.flatMap { it.discrepancies }
            .groupingBy { it.type }
            .eachCount()
    
    private fun <T> List<T>.toIdx(): Indexed<T> = b0.a
    private fun <T> emptyIndexed(): Indexed<T> = b0.a
}