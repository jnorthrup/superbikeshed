package moneyfan.entities

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.core.Price
import moneyfan.core.Decimal

/**
 * ## LLC (Limited Liability Company) Implementation
 * 
 * LLCs are hybrid entities that combine the liability protection of corporations
 * with the tax flexibility of partnerships. They can elect various tax treatments
 * and have flexible management structures.
 */

/**
 * LLC-specific data and operations
 */
@Serializable
data class LLC(
    override val id: EntityId,
    override val name: String,
    override val status: EntityStatus,
    override val jurisdiction: JurisdictionCode,
    override val taxId: TaxId?,
    override val formationDate: Instant,
    override val dissolutionDate: Instant?,
    val managementType: LLCManagementType,
    val members: Indexed<LLCMember>,
    val operatingAgreement: OperatingAgreement,
    val taxElection: LLCTaxElection,
    val registeredAgent: RegisteredAgent,
    val principalAddress: BusinessAddress,
    val bankAccounts: Indexed<BankAccount>
) : LegalEntity {
    
    override val type: EntityType = EntityType.LLC
    
    override fun computeNetWorth(): Price {
        // Sum all member capital accounts
        val totalCapital = members.play.map { it.capitalAccount.currentBalance }.sum()
        return Price(totalCapital)
    }
    
    override fun getBeneficiaries(): Indexed<Beneficiary> {
        // LLC members are the beneficiaries
        return members.size j { i ->
            val member = members[i]
            Beneficiary(
                id = BeneficiaryId(member.id.value),
                name = member.name,
                entityId = id,
                beneficialInterest = member.ownershipPercentage,
                distributionRights = member.distributionRights,
                votingRights = member.votingRights,
                restrictions = member.restrictions
            )
        }
    }
    
    override fun canPerformAction(action: EntityAction): Boolean {
        return when (action) {
            is EntityAction.Distribution -> canMakeDistribution(action)
            is EntityAction.OwnershipTransfer -> canTransferOwnership(action)
            is EntityAction.StatusChange -> canChangeStatus(action)
            is EntityAction.TaxElection -> canMakeTaxElection(action)
            else -> false
        }
    }
    
    override fun getTaxTreatment(): TaxStatus {
        return when (taxElection) {
            LLCTaxElection.DISREGARDED_ENTITY -> TaxStatus.PASS_THROUGH
            LLCTaxElection.PARTNERSHIP -> TaxStatus.PASS_THROUGH
            LLCTaxElection.C_CORP -> TaxStatus.C_CORP_TAXATION
            LLCTaxElection.S_CORP -> TaxStatus.PASS_THROUGH
        }
    }
    
    private fun canMakeDistribution(action: EntityAction.Distribution): Boolean {
        // Check if member has distribution rights
        val member = members.play.find { 
            BeneficiaryId(it.id.value) == action.beneficiaryId 
        } ?: return false
        
        // Check operating agreement restrictions
        if (!operatingAgreement.allowsDistribution(action.amount, member)) {
            return false
        }
        
        // Check if distribution would make LLC insolvent
        val netWorth = computeNetWorth()
        return action.amount <= netWorth
    }
    
    private fun canTransferOwnership(action: EntityAction.OwnershipTransfer): Boolean {
        // Check transfer restrictions in operating agreement
        return operatingAgreement.transferRestrictions.play.all { restriction ->
            when (restriction) {
                is TransferRestriction.RightOfFirstRefusal -> {
                    // Would need to check if ROFR process was followed
                    true
                }
                is TransferRestriction.ApprovalRequired -> {
                    // Check if required approvals obtained
                    restriction.approvers.play.any { it == action.entityId }
                }
                is TransferRestriction.LockupPeriod -> {
                    action.timestamp > restriction.until
                }
                else -> true
            }
        }
    }
    
    private fun canChangeStatus(action: EntityAction.StatusChange): Boolean {
        return when (action.toStatus) {
            EntityStatus.DISSOLVING -> {
                // Check if dissolution vote requirements met
                operatingAgreement.dissolutionRequirements.isMet(members)
            }
            EntityStatus.ACTIVE -> status == EntityStatus.FORMING
            else -> false
        }
    }
    
    private fun canMakeTaxElection(action: EntityAction.TaxElection): Boolean {
        // Check if all members consent to tax election change
        return operatingAgreement.majorDecisions.contains(MajorDecisionType.TAX_ELECTION)
    }
}

/**
 * LLC management structure types
 */
enum class LLCManagementType {
    MEMBER_MANAGED,      // All members participate in management
    MANAGER_MANAGED,     // Designated managers run the LLC
    BOARD_MANAGED        // Board of managers (less common)
}

/**
 * LLC tax election options
 */
enum class LLCTaxElection {
    DISREGARDED_ENTITY,  // Single-member LLC default
    PARTNERSHIP,         // Multi-member LLC default  
    C_CORP,             // Elect to be taxed as C-Corp
    S_CORP              // Elect to be taxed as S-Corp
}

/**
 * LLC Member representation
 */
@Serializable
data class LLCMember(
    val id: StakeholderId,
    val name: String,
    val memberType: MemberType,
    val ownershipPercentage: Decimal,
    val capitalAccount: CapitalAccount,
    val distributionRights: DistributionRights,
    val votingRights: VotingRights,
    val managementRights: ManagementRights?,
    val restrictions: Indexed<BeneficiaryRestriction>?,
    val admissionDate: Instant,
    val withdrawalDate: Instant?
)

/**
 * Types of LLC members
 */
enum class MemberType {
    INDIVIDUAL,
    ENTITY,      // Another LLC, Corp, etc.
    TRUST,
    ESTATE,
    PARTNERSHIP
}

/**
 * Capital account tracking for LLC members
 */
@Serializable
data class CapitalAccount(
    val memberId: StakeholderId,
    val initialContribution: Price,
    val additionalContributions: Price,
    val distributions: Price,
    val allocatedProfits: Price,
    val allocatedLosses: Price,
    val currentBalance: Price
)

/**
 * Management rights for LLC members/managers
 */
@Serializable
data class ManagementRights(
    val canBindLLC: Boolean,
    val signingAuthority: SigningAuthority,
    val spendingLimit: Price?,
    val specificPowers: Indexed<String>
)

/**
 * Signing authority levels
 */
enum class SigningAuthority {
    NONE,
    LIMITED,      // Under certain threshold
    GENERAL,      // Most transactions
    UNLIMITED     // Any transaction
}

/**
 * LLC Operating Agreement
 */
@Serializable
data class OperatingAgreement(
    val effectiveDate: Instant,
    val amendmentDate: Instant?,
    val profitAllocation: AllocationMethod,
    val lossAllocation: AllocationMethod,
    val distributionTerms: DistributionTerms,
    val transferRestrictions: Indexed<TransferRestriction>,
    val dissolutionRequirements: DissolutionRequirements,
    val majorDecisions: Set<MajorDecisionType>,
    val indemnificationProvisions: IndemnificationTerms
) {
    fun allowsDistribution(amount: Price, member: LLCMember): Boolean {
        return when (distributionTerms) {
            is DistributionTerms.ProRata -> true
            is DistributionTerms.Waterfall -> {
                // Check waterfall requirements
                distributionTerms.checkTier(amount, member)
            }
            is DistributionTerms.Custom -> {
                distributionTerms.validator(amount, member)
            }
        }
    }
}

/**
 * Profit/Loss allocation methods
 */
sealed interface AllocationMethod {
    data object ProRataByOwnership : AllocationMethod
    data object ProRataByCapital : AllocationMethod
    data class Special(val allocations: Indexed<Join<StakeholderId, Decimal>>) : AllocationMethod
    data class Layered(val layers: Indexed<AllocationLayer>) : AllocationMethod
}

/**
 * Allocation layer for complex structures
 */
@Serializable
data class AllocationLayer(
    val threshold: Price,
    val allocation: Indexed<Join<StakeholderId, Decimal>>
)

/**
 * Distribution terms
 */
sealed interface DistributionTerms {
    data object ProRata : DistributionTerms
    data class Waterfall(val tiers: Indexed<WaterfallTier>) : DistributionTerms {
        fun checkTier(amount: Price, member: LLCMember): Boolean {
            // Implementation would check which tier applies
            return true
        }
    }
    data class Custom(val validator: (Price, LLCMember) -> Boolean) : DistributionTerms
}

/**
 * Waterfall distribution tier
 */
@Serializable
data class WaterfallTier(
    val priority: Int,
    val threshold: Price?,
    val participants: Indexed<StakeholderId>,
    val allocation: AllocationMethod
)

/**
 * Major decisions requiring special approval
 */
enum class MajorDecisionType {
    ADMIT_NEW_MEMBER,
    REMOVE_MEMBER,
    AMEND_OPERATING_AGREEMENT,
    SELL_MAJOR_ASSETS,
    MERGE_OR_ACQUIRE,
    DISSOLVE,
    CHANGE_BUSINESS_PURPOSE,
    TAX_ELECTION,
    TAKE_ON_DEBT,
    MAKE_CAPITAL_CALL
}

/**
 * Dissolution requirements
 */
@Serializable
data class DissolutionRequirements(
    val votingThreshold: Decimal,  // e.g., 75%
    val unanimousRequired: Boolean,
    val specificTriggers: Indexed<DissolutionTrigger>
) {
    fun isMet(members: Indexed<LLCMember>): Boolean {
        val totalVotes = members.play.map { it.votingRights.votingPower }.sum()
        val requiredVotes = totalVotes * (votingThreshold / 100.0)
        // Would need actual vote tally
        return true
    }
}

/**
 * Triggers for automatic dissolution
 */
sealed interface DissolutionTrigger {
    data object BankruptcyOfMember : DissolutionTrigger
    data object DeathOfMember : DissolutionTrigger
    data object CourtOrder : DissolutionTrigger
    data class ExpirationDate(val date: Instant) : DissolutionTrigger
    data class CustomTrigger(val condition: String) : DissolutionTrigger
}

/**
 * Indemnification terms
 */
@Serializable
data class IndemnificationTerms(
    val coveredPersons: Indexed<StakeholderId>,
    val scope: IndemnificationScope,
    val exceptions: Indexed<String>,
    val advancementOfExpenses: Boolean
)

enum class IndemnificationScope {
    BROAD,      // Maximum protection
    STANDARD,   // Normal business activities
    LIMITED     // Only specific activities
}

/**
 * Registered agent information
 */
@Serializable
data class RegisteredAgent(
    val name: String,
    val address: BusinessAddress,
    val phone: String?,
    val email: String?
)

/**
 * Business address
 */
@Serializable
data class BusinessAddress(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String = "USA"
)

/**
 * Bank account information
 */
@Serializable
data class BankAccount(
    val bankName: String,
    val accountNumber: String,  // Should be encrypted in production
    val accountType: BankAccountType,
    val currentBalance: Price,
    val authorizedSigners: Indexed<StakeholderId>
)

enum class BankAccountType {
    CHECKING,
    SAVINGS,
    MONEY_MARKET,
    INVESTMENT
}

/**
 * LLC-specific helper functions
 */
object LLCOperations {
    
    /**
     * Create a single-member LLC
     */
    fun createSingleMemberLLC(
        name: String,
        jurisdiction: JurisdictionCode,
        member: LLCMember,
        registeredAgent: RegisteredAgent,
        principalAddress: BusinessAddress
    ): LLC {
        val entityId = EntityOperations.generateEntityId(EntityType.LLC, jurisdiction)
        
        return LLC(
            id = entityId,
            name = name,
            status = EntityStatus.FORMING,
            jurisdiction = jurisdiction,
            taxId = null,
            formationDate = kotlinx.datetime.Clock.System.now(),
            dissolutionDate = null,
            managementType = LLCManagementType.MEMBER_MANAGED,
            members = 1 j { member },
            operatingAgreement = createDefaultOperatingAgreement(),
            taxElection = LLCTaxElection.DISREGARDED_ENTITY,
            registeredAgent = registeredAgent,
            principalAddress = principalAddress,
            bankAccounts = emptyIndex()
        )
    }
    
    /**
     * Create default operating agreement
     */
    fun createDefaultOperatingAgreement(): OperatingAgreement {
        return OperatingAgreement(
            effectiveDate = kotlinx.datetime.Clock.System.now(),
            amendmentDate = null,
            profitAllocation = AllocationMethod.ProRataByOwnership,
            lossAllocation = AllocationMethod.ProRataByOwnership,
            distributionTerms = DistributionTerms.ProRata,
            transferRestrictions = 1 j { TransferRestriction.RightOfFirstRefusal },
            dissolutionRequirements = DissolutionRequirements(
                votingThreshold = 75.0,
                unanimousRequired = false,
                specificTriggers = emptyIndex()
            ),
            majorDecisions = setOf(
                MajorDecisionType.ADMIT_NEW_MEMBER,
                MajorDecisionType.SELL_MAJOR_ASSETS,
                MajorDecisionType.DISSOLVE
            ),
            indemnificationProvisions = IndemnificationTerms(
                coveredPersons = emptyIndex(),
                scope = IndemnificationScope.STANDARD,
                exceptions = emptyIndex(),
                advancementOfExpenses = true
            )
        )
    }
    
    /**
     * Calculate member's share of profits
     */
    fun calculateMemberProfitShare(
        member: LLCMember,
        totalProfit: Price,
        allocationMethod: AllocationMethod
    ): Price {
        return when (allocationMethod) {
            is AllocationMethod.ProRataByOwnership -> {
                Price(totalProfit.value * member.ownershipPercentage / 100.0)
            }
            is AllocationMethod.ProRataByCapital -> {
                Price(totalProfit.value * member.capitalAccount.currentBalance.value / 100.0)
            }
            else -> Price(0.0) // Would need more complex calculation
        }
    }
}