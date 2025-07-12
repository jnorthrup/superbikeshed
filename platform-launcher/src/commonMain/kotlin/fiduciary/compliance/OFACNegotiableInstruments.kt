package fiduciary.compliance

import borg.trikeshed.lib.*
import fiduciary.ledger.*
import io.islandtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * OFAC compliance system for negotiable instruments
 * Identifies reporting requirements and exemptions for financial transactions
 */
class OFACNegotiableInstruments {
    
    /**
     * Negotiable instrument with OFAC screening results
     */
    data class ScreenedInstrument(
        val instrument: NegotiableInstrument,
        val screeningResult: OFACScreeningResult,
        val reportingRequirement: ReportingRequirement,
        val exemptionStatus: ExemptionStatus?,
        val riskScore: Double,
        val screeningTimestamp: Instant
    )
    
    /**
     * Types of negotiable instruments
     */
    data class NegotiableInstrument(
        val instrumentId: String,
        val type: InstrumentType,
        val amount: MonetaryValue,
        val issuer: Party,
        val payee: Party,
        val endorsements: Indexed<Endorsement>,
        val issuanceDate: LocalDate,
        val maturityDate: LocalDate?,
        val negotiabilityStatus: NegotiabilityStatus
    )
    
    enum class InstrumentType {
        CHECK,
        PROMISSORY_NOTE,
        BILL_OF_EXCHANGE,
        DRAFT,
        MONEY_ORDER,
        TRAVELERS_CHECK,
        CERTIFICATE_OF_DEPOSIT,
        BEARER_BOND,
        WAREHOUSE_RECEIPT,
        BILL_OF_LADING
    }
    
    /**
     * OFAC screening result
     */
    data class OFACScreeningResult(
        val matchFound: Boolean,
        val matchConfidence: Double,
        val matchedEntries: Indexed<SDNEntry>,
        val screeningMethod: ScreeningMethod,
        val falsePositiveIndicators: Set<FalsePositiveIndicator>
    )
    
    /**
     * Reporting requirements based on screening
     */
    sealed class ReportingRequirement {
        data class Required(
            val reportType: ReportType,
            val deadline: Instant,
            val blockingRequired: Boolean,
            val licensePossible: Boolean
        ) : ReportingRequirement()
        
        data class Exempt(
            val exemptionReason: ExemptionReason,
            val documentationRequired: Boolean
        ) : ReportingRequirement()
        
        object NoReportingRequired : ReportingRequirement()
    }
    
    /**
     * OFAC exemption categories
     */
    data class ExemptionStatus(
        val isExempt: Boolean,
        val exemptionType: ExemptionType,
        val authorityReference: String,
        val conditions: Indexed<ExemptionCondition>,
        val expiryDate: LocalDate?
    )
    
    enum class ExemptionType {
        GENERAL_LICENSE,          // Pre-authorized by OFAC
        SPECIFIC_LICENSE,         // Individual authorization
        STATUTORY_EXEMPTION,      // Law-based exemption
        DE_MINIMIS,              // Below threshold
        HUMANITARIAN,            // Humanitarian purposes
        INFORMATIONAL_MATERIALS, // First Amendment protected
        PERSONAL_REMITTANCE,     // Family remittances
        LEGAL_SERVICES,          // Attorney fees
        AGRICULTURAL_MEDICAL,    // Food and medicine
        DIPLOMATIC_MISSION       // Diplomatic immunity
    }
    
    /**
     * ADHD-friendly workflow for complex compliance tasks
     */
    class ADHDComplianceWorkflow {
        
        /**
         * Simplified task breakdown for OFAC screening
         */
        data class SimplifiedTask(
            val taskId: String,
            val title: String,
            val urgency: UrgencyLevel,
            val complexity: ComplexityLevel,
            val estimatedMinutes: Int,
            val checklist: Indexed<ChecklistItem>,
            val visualCues: VisualCues,
            val breakReminder: BreakSchedule?
        )
        
        enum class UrgencyLevel {
            IMMEDIATE,    // Do now - blocking issue
            TODAY,        // Complete today
            THIS_WEEK,    // Can wait a few days
            ROUTINE       // Regular maintenance
        }
        
        enum class ComplexityLevel {
            SIMPLE,       // Single step
            MODERATE,     // 2-3 steps
            COMPLEX,      // Multiple steps
            OVERWHELMING  // Needs breakdown
        }
        
        /**
         * Break down complex OFAC screening into manageable steps
         */
        suspend fun createWorkflow(
            instruments: Indexed<NegotiableInstrument>
        ): Flow<SimplifiedTask> = flow {
            // Group by urgency
            val grouped = instruments.groupBy { instrument →
                when {
                    instrument.amount.amount > 10000 → UrgencyLevel.IMMEDIATE
                    instrument.amount.amount > 3000 → UrgencyLevel.TODAY
                    else → UrgencyLevel.ROUTINE
                }
            }
            
            // Create tasks for each group
            grouped.forEach { (urgency, batch) →
                when (urgency) {
                    UrgencyLevel.IMMEDIATE → {
                        // Break into individual tasks for high-value
                        batch.forEach { instrument →
                            emit(createImmediateTask(instrument))
                        }
                    }
                    UrgencyLevel.TODAY → {
                        // Batch medium-value instruments
                        emit(createBatchTask(batch, urgency))
                    }
                    else → {
                        // Single batch for routine
                        emit(createRoutineTask(batch))
                    }
                }
            }
        }
        
        internal fun createImmediateTask(
            instrument: NegotiableInstrument
        ): SimplifiedTask = SimplifiedTask(
            taskId = "OFAC-IMM-${instrument.instrumentId}",
            title = "🚨 Screen $${instrument.amount.amount} ${instrument.type}",
            urgency = UrgencyLevel.IMMEDIATE,
            complexity = ComplexityLevel.MODERATE,
            estimatedMinutes = 15,
            checklist = idx[
                ChecklistItem("Open OFAC search", false, "🔍"),
                ChecklistItem("Enter ${instrument.payee.name}", false, "✏️"),
                ChecklistItem("Check for matches", false, "👀"),
                ChecklistItem("Document result", false, "📝"),
                ChecklistItem("File if required", false, "📁")
            ],
            visualCues = VisualCues(
                color = "red",
                icon = "🚨",
                progressBar = true
            ),
            breakReminder = BreakSchedule(
                afterMinutes = 15,
                breakDuration = 5,
                message = "Great job! Take a 5-minute break 🧘"
            )
        )
        
        internal fun createBatchTask(
            batch: List<NegotiableInstrument>,
            urgency: UrgencyLevel
        ): SimplifiedTask = SimplifiedTask(
            taskId = "OFAC-BATCH-${System.currentTimeMillis()}",
            title = "📋 Screen ${batch.size} medium-value instruments",
            urgency = urgency,
            complexity = ComplexityLevel.COMPLEX,
            estimatedMinutes = batch.size * 5,
            checklist = idx[
                ChecklistItem("Prepare screening spreadsheet", false, "📊"),
                ChecklistItem("Run batch search (${batch.size} items)", false, "🔄"),
                ChecklistItem("Review matches", false, "🔎"),
                ChecklistItem("Mark false positives", false, "❌"),
                ChecklistItem("Generate report", false, "📄")
            ],
            visualCues = VisualCues(
                color = "yellow",
                icon = "📋",
                progressBar = true
            ),
            breakReminder = BreakSchedule(
                afterMinutes = 25,
                breakDuration = 10,
                message = "Time for a break! Stretch and hydrate 💧"
            )
        )
        
        data class ChecklistItem(
            val text: String,
            val completed: Boolean,
            val emoji: String
        )
        
        data class VisualCues(
            val color: String,
            val icon: String,
            val progressBar: Boolean
        )
        
        data class BreakSchedule(
            val afterMinutes: Int,
            val breakDuration: Int,
            val message: String
        )
    }
    
    /**
     * OFAC screening implementation
     */
    class OFACScreener {
        
        suspend fun screenInstrument(
            instrument: NegotiableInstrument
        ): ScreenedInstrument = coroutineScope {
            // Determine if screening is required
            val exemption = checkExemptions(instrument)
            
            if (exemption != null) {
                return@coroutineScope ScreenedInstrument(
                    instrument = instrument,
                    screeningResult = OFACScreeningResult(
                        matchFound = false,
                        matchConfidence = 0.0,
                        matchedEntries = emptyIndexed(),
                        screeningMethod = ScreeningMethod.EXEMPTION_CHECK,
                        falsePositiveIndicators = emptySet()
                    ),
                    reportingRequirement = ReportingRequirement.Exempt(
                        exemptionReason = mapExemptionReason(exemption.exemptionType),
                        documentationRequired = false
                    ),
                    exemptionStatus = exemption,
                    riskScore = 0.0,
                    screeningTimestamp = Clock.System.now()
                )
            }
            
            // Perform actual screening
            val screeningResult = performScreening(instrument)
            val reportingReq = determineReporting(instrument, screeningResult)
            val riskScore = calculateRiskScore(instrument, screeningResult)
            
            ScreenedInstrument(
                instrument = instrument,
                screeningResult = screeningResult,
                reportingRequirement = reportingReq,
                exemptionStatus = null,
                riskScore = riskScore,
                screeningTimestamp = Clock.System.now()
            )
        }
        
        internal suspend fun checkExemptions(
            instrument: NegotiableInstrument
        ): ExemptionStatus? {
            // Personal remittance exemption
            if (instrument.amount.amount < 1000 &&
                instrument.type == InstrumentType.MONEY_ORDER &&
                isPersonalRemittance(instrument)) {
                return ExemptionStatus(
                    isExempt = true,
                    exemptionType = ExemptionType.PERSONAL_REMITTANCE,
                    authorityReference = "31 CFR 560.516",
                    conditions = idx[
                        ExemptionCondition(
                            "Amount under $1000",
                            met = true
                        ),
                        ExemptionCondition(
                            "Personal/family use",
                            met = true
                        )
                    ],
                    expiryDate = null
                )
            }
            
            // De minimis exemption
            if (instrument.amount.amount < 100) {
                return ExemptionStatus(
                    isExempt = true,
                    exemptionType = ExemptionType.DE_MINIMIS,
                    authorityReference = "OFAC FAQ 265",
                    conditions = idx[
                        ExemptionCondition(
                            "Amount under $100",
                            met = true
                        )
                    ],
                    expiryDate = null
                )
            }
            
            // Legal services exemption
            if (instrument.payee.partyType == PartyType.LAW_FIRM &&
                hasLegalServicesAuthorization(instrument)) {
                return ExemptionStatus(
                    isExempt = true,
                    exemptionType = ExemptionType.LEGAL_SERVICES,
                    authorityReference = "31 CFR 501.506",
                    conditions = idx[
                        ExemptionCondition(
                            "Authorized legal services",
                            met = true
                        )
                    ],
                    expiryDate = null
                )
            }
            
            return null
        }
        
        internal suspend fun performScreening(
            instrument: NegotiableInstrument
        ): OFACScreeningResult {
            val partiesToScreen = listOf(instrument.issuer, instrument.payee) + 
                                 instrument.endorsements.map { it.endorser }
            
            val matches = mutableListOf<SDNEntry>()
            
            partiesToScreen.forEach { party →
                val sdnMatches = searchSDNList(party)
                matches.addAll(sdnMatches)
            }
            
            val confidence = if (matches.isNotEmpty()) {
                calculateMatchConfidence(matches)
            } else 0.0
            
            return OFACScreeningResult(
                matchFound = matches.isNotEmpty(),
                matchConfidence = confidence,
                matchedEntries = matches.toIdx(),
                screeningMethod = ScreeningMethod.AUTOMATED_SEARCH,
                falsePositiveIndicators = identifyFalsePositives(matches, instrument)
            )
        }
        
        internal fun determineReporting(
            instrument: NegotiableInstrument,
            screening: OFACScreeningResult
        ): ReportingRequirement {
            if (!screening.matchFound) {
                return ReportingRequirement.NoReportingRequired
            }
            
            // High confidence match requires blocking
            if (screening.matchConfidence > 0.85) {
                return ReportingRequirement.Required(
                    reportType = ReportType.BLOCKING_REPORT,
                    deadline = Clock.System.now() + 10.days,
                    blockingRequired = true,
                    licensePossible = true
                )
            }
            
            // Medium confidence requires investigation
            if (screening.matchConfidence > 0.5) {
                return ReportingRequirement.Required(
                    reportType = ReportType.SUSPICIOUS_TRANSACTION,
                    deadline = Clock.System.now() + 30.days,
                    blockingRequired = false,
                    licensePossible = true
                )
            }
            
            return ReportingRequirement.NoReportingRequired
        }
        
        internal fun calculateRiskScore(
            instrument: NegotiableInstrument,
            screening: OFACScreeningResult
        ): Double {
            var score = screening.matchConfidence * 0.5
            
            // High-value transactions
            if (instrument.amount.amount > 10000) score += 0.2
            
            // Multiple endorsements
            if (instrument.endorsements.size > 2) score += 0.1
            
            // Bearer instruments
            if (instrument.negotiabilityStatus == NegotiabilityStatus.BEARER) score += 0.2
            
            return minOf(1.0, score)
        }
    }
    
    // Supporting data classes
    data class Party(
        val partyId: String,
        val name: String,
        val partyType: PartyType,
        val identifiers: Indexed<PartyIdentifier>,
        val addresses: Indexed<Address>
    )
    
    enum class PartyType {
        INDIVIDUAL, CORPORATION, LAW_FIRM, FINANCIAL_INSTITUTION, GOVERNMENT
    }
    
    data class PartyIdentifier(
        val type: IdentifierType,
        val value: String,
        val issuingCountry: String?
    )
    
    enum class IdentifierType {
        SSN, EIN, PASSPORT, DRIVERS_LICENSE, NATIONAL_ID, SWIFT_CODE
    }
    
    data class Endorsement(
        val endorser: Party,
        val endorsementDate: LocalDate,
        val endorsementType: EndorsementType,
        val restrictive: Boolean
    )
    
    enum class EndorsementType {
        BLANK, SPECIAL, RESTRICTIVE, QUALIFIED, CONDITIONAL
    }
    
    enum class NegotiabilityStatus {
        ORDER, BEARER, NON_NEGOTIABLE, RESTRICTED
    }
    
    data class SDNEntry(
        val entryId: String,
        val name: String,
        val aliases: Indexed<String>,
        val programs: Set<String>,
        val remarks: String?,
        val score: Double
    )
    
    enum class ScreeningMethod {
        AUTOMATED_SEARCH, MANUAL_REVIEW, EXEMPTION_CHECK, HYBRID
    }
    
    enum class FalsePositiveIndicator {
        COMMON_NAME,
        PARTIAL_MATCH_ONLY,
        DIFFERENT_COUNTRY,
        DIFFERENT_DOB,
        BUSINESS_VS_INDIVIDUAL
    }
    
    enum class ReportType {
        BLOCKING_REPORT, SUSPICIOUS_TRANSACTION, VOLUNTARY_DISCLOSURE, REJECTED_TRANSACTION
    }
    
    enum class ExemptionReason {
        GENERAL_LICENSE,
        BELOW_THRESHOLD,
        PERSONAL_USE,
        HUMANITARIAN,
        PRE_EXISTING,
        INFORMATIONAL
    }
    
    data class ExemptionCondition(
        val condition: String,
        val met: Boolean
    )
    
    data class Address(
        val street: String?,
        val city: String?,
        val state: String?,
        val postalCode: String?,
        val country: String
    )
    
    // Utility functions
    internal fun isPersonalRemittance(instrument: NegotiableInstrument): Boolean {
        // Check memo line or purpose
        return true // Simplified
    }
    
    internal fun hasLegalServicesAuthorization(instrument: NegotiableInstrument): Boolean {
        // Check for legal services authorization
        return instrument.payee.partyType == PartyType.LAW_FIRM
    }
    
    internal suspend fun searchSDNList(party: Party): List<SDNEntry> {
        // Would connect to OFAC SDN API or database
        return emptyList()
    }
    
    internal fun calculateMatchConfidence(matches: List<SDNEntry>): Double {
        return matches.maxOfOrNull { it.score } ?: 0.0
    }
    
    internal fun identifyFalsePositives(
        matches: List<SDNEntry>,
        instrument: NegotiableInstrument
    ): Set<FalsePositiveIndicator> {
        val indicators = mutableSetOf<FalsePositiveIndicator>()
        
        matches.forEach { match →
            if (match.score < 0.8) {
                indicators.add(FalsePositiveIndicator.PARTIAL_MATCH_ONLY)
            }
        }
        
        return indicators
    }
    
    internal fun mapExemptionReason(type: ExemptionType): ExemptionReason =
        when (type) {
            ExemptionType.GENERAL_LICENSE → ExemptionReason.GENERAL_LICENSE
            ExemptionType.DE_MINIMIS → ExemptionReason.BELOW_THRESHOLD
            ExemptionType.PERSONAL_REMITTANCE → ExemptionReason.PERSONAL_USE
            ExemptionType.HUMANITARIAN → ExemptionReason.HUMANITARIAN
            else → ExemptionReason.GENERAL_LICENSE
        }
    
    internal fun <T> List<T>.toIdx(): Indexed<T> = b0.component1()
    internal fun <T> emptyIndexed(): Indexed<T> = b0.component1()
    internal fun <T> idx(vararg items: T): Indexed<T> = b0.component1()
}