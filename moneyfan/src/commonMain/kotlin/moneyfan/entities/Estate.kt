package moneyfan.entities

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import moneyfan.core.Price
import moneyfan.core.Decimal

/**
 * ## Estate Implementation
 * 
 * An Estate is a legal entity created upon the death of an individual (decedent)
 * to manage and distribute their assets according to their will or state law.
 * The estate exists temporarily until all assets are distributed and obligations
 * are settled.
 */

/**
 * Estate entity
 */
@Serializable
data class Estate(
    override val id: EntityId,
    override val name: String,
    override val status: EntityStatus,
    override val jurisdiction: JurisdictionCode,
    override val taxId: TaxId?,
    override val formationDate: Instant, // Date of death
    override val dissolutionDate: Instant?,
    val decedent: Decedent,
    val executor: Executor,
    val coExecutors: Indexed<Executor>?,
    val will: Will?,
    val heirs: Indexed<Heir>,
    val beneficiaries: Indexed<EstateBeneficiary>,
    val assets: Indexed<EstateAsset>,
    val liabilities: Indexed<EstateLiability>,
    val probateInfo: ProbateInfo,
    val taxInfo: EstateTaxInfo,
    val distributions: Indexed<EstateDistribution>,
    val administratorBond: AdministratorBond?,
    val fiduciaryAccountings: Indexed<FiduciaryAccounting>
) : LegalEntity {
    
    override val type: EntityType = EntityType.ESTATE
    
    override fun computeNetWorth(): Price {
        val totalAssets = assets.play.sumOf { it.currentValue.value }
        val totalLiabilities = liabilities.play.sumOf { it.amount.value }
        return Price(totalAssets - totalLiabilities)
    }
    
    override fun getBeneficiaries(): Indexed<Beneficiary> {
        return beneficiaries.size j { i ->
            val estateBeneficiary = beneficiaries[i]
            Beneficiary(
                id = estateBeneficiary.id,
                name = estateBeneficiary.name,
                entityId = id,
                beneficialInterest = estateBeneficiary.inheritancePercentage,
                distributionRights = estateBeneficiary.distributionRights,
                votingRights = null, // No voting rights in estates
                restrictions = estateBeneficiary.restrictions
            )
        }
    }
    
    override fun canPerformAction(action: EntityAction): Boolean {
        return when (action) {
            is EntityAction.Distribution -> canMakeDistribution(action)
            is EntityAction.OwnershipTransfer -> false // No ownership transfers
            is EntityAction.StatusChange -> canChangeStatus(action)
            is EntityAction.TaxElection -> false // No tax elections
            else -> false
        }
    }
    
    override fun getTaxTreatment(): TaxStatus {
        return TaxStatus.SPECIAL // Estates have special tax treatment
    }
    
    private fun canMakeDistribution(action: EntityAction.Distribution): Boolean {
        // Check if estate has sufficient assets
        val netWorth = computeNetWorth()
        if (action.amount > netWorth) return false
        
        // Check if all debts and taxes are paid or reserved for
        if (!areDebtsPaidOrReserved()) return false
        
        // Check if beneficiary is entitled to distribution
        val beneficiary = beneficiaries.play.find { it.id == action.beneficiaryId }
        return beneficiary != null && isDistributionAllowed(beneficiary, action.amount)
    }
    
    private fun canChangeStatus(action: EntityAction.StatusChange): Boolean {
        return when (action.toStatus) {
            EntityStatus.DISSOLVED -> {
                // Can close estate when all assets distributed and obligations met
                areAllAssetsDistributed() && areAllObligationsMet()
            }
            EntityStatus.ACTIVE -> status == EntityStatus.FORMING
            else -> false
        }
    }
    
    private fun areDebtsPaidOrReserved(): Boolean {
        val totalLiabilities = liabilities.play.sumOf { it.amount.value }
        val totalAssets = assets.play.sumOf { it.currentValue.value }
        return totalAssets >= totalLiabilities
    }
    
    private fun isDistributionAllowed(beneficiary: EstateBeneficiary, amount: Price): Boolean {
        return when (beneficiary.distributionType) {
            DistributionType.SPECIFIC_BEQUEST -> true
            DistributionType.RESIDUARY -> probateInfo.probatePhase == ProbatePhase.DISTRIBUTION
            DistributionType.INTESTATE_SHARE -> probateInfo.probatePhase == ProbatePhase.DISTRIBUTION
        }
    }
    
    private fun areAllAssetsDistributed(): Boolean {
        // Check if all assets have been distributed
        return assets.play.all { it.status == AssetStatus.DISTRIBUTED }
    }
    
    private fun areAllObligationsMet(): Boolean {
        // Check if all liabilities are satisfied
        return liabilities.play.all { it.status == LiabilityStatus.PAID }
    }
}

/**
 * Information about the deceased person
 */
@Serializable
data class Decedent(
    val id: StakeholderId,
    val fullName: String,
    val dateOfBirth: Instant,
    val dateOfDeath: Instant,
    val placeOfDeath: BusinessAddress,
    val socialSecurityNumber: String?, // Should be encrypted
    val lastKnownAddress: BusinessAddress,
    val maritalStatus: MaritalStatus,
    val children: Indexed<Child>?,
    val parents: Indexed<Parent>?,
    val citizenship: String
)

enum class MaritalStatus {
    SINGLE,
    MARRIED,
    DIVORCED,
    WIDOWED,
    SEPARATED
}

@Serializable
data class Child(
    val id: StakeholderId,
    val name: String,
    val relationship: ChildRelationship,
    val dateOfBirth: Instant,
    val isMinor: Boolean,
    val guardian: StakeholderId?
)

enum class ChildRelationship {
    BIOLOGICAL,
    ADOPTED,
    STEPCHILD
}

@Serializable
data class Parent(
    val id: StakeholderId,
    val name: String,
    val living: Boolean,
    val relationship: ParentRelationship
)

enum class ParentRelationship {
    BIOLOGICAL_FATHER,
    BIOLOGICAL_MOTHER,
    ADOPTIVE_FATHER,
    ADOPTIVE_MOTHER,
    STEPFATHER,
    STEPMOTHER
}

/**
 * Executor of the estate
 */
@Serializable
data class Executor(
    val id: StakeholderId,
    val name: String,
    val executorType: ExecutorType,
    val appointmentDate: Instant,
    val qualifications: Indexed<String>,
    val compensation: ExecutorCompensation?,
    val powers: Indexed<ExecutorPower>,
    val restrictions: Indexed<ExecutorRestriction>?,
    val bond: AdministratorBond?
)

enum class ExecutorType {
    NAMED_IN_WILL,
    COURT_APPOINTED,
    SUCCESSOR_EXECUTOR,
    TEMPORARY_ADMINISTRATOR,
    PUBLIC_ADMINISTRATOR
}

@Serializable
data class ExecutorCompensation(
    val compensationType: CompensationType,
    val amount: Price?,
    val percentage: Decimal?,
    val courtApprovalRequired: Boolean
)

enum class CompensationType {
    STATUTORY_PERCENTAGE,
    COURT_DETERMINED,
    FIXED_AMOUNT,
    WAIVED
}

enum class ExecutorPower {
    SELL_REAL_ESTATE,
    SELL_PERSONAL_PROPERTY,
    INVEST_ASSETS,
    CONTINUE_BUSINESS,
    BORROW_MONEY,
    PAY_DEBTS,
    MAKE_DISTRIBUTIONS,
    FILE_TAX_RETURNS,
    SETTLE_CLAIMS,
    REPRESENT_ESTATE_IN_COURT
}

sealed interface ExecutorRestriction {
    data class CourtApprovalRequired(val actions: Set<ExecutorPower>) : ExecutorRestriction
    data class SuretyBondRequired(val amount: Price) : ExecutorRestriction
    data class RegularAccountingRequired(val frequency: AccountingFrequency) : ExecutorRestriction
}

enum class AccountingFrequency {
    MONTHLY,
    QUARTERLY,
    ANNUALLY,
    UPON_REQUEST
}

/**
 * Will information
 */
@Serializable
data class Will(
    val executionDate: Instant,
    val witnesses: Indexed<Witness>,
    val notarized: Boolean,
    val selfProving: Boolean,
    val codicils: Indexed<Codicil>?,
    val bequests: Indexed<Bequest>,
    val residuaryClause: ResiduaryClause,
    val contestPeriod: Instant?,
    val willContest: WillContest?
)

@Serializable
data class Witness(
    val name: String,
    val address: BusinessAddress,
    val dateWitnessed: Instant
)

@Serializable
data class Codicil(
    val executionDate: Instant,
    val changes: String,
    val witnesses: Indexed<Witness>
)

@Serializable
data class Bequest(
    val beneficiaryId: BeneficiaryId,
    val bequestType: BequestType,
    val description: String,
    val value: Price?,
    val conditions: Indexed<BequestCondition>?
)

enum class BequestType {
    SPECIFIC_PROPERTY,
    MONETARY_GIFT,
    PERCENTAGE_OF_ESTATE,
    RESIDUARY_SHARE
}

sealed interface BequestCondition {
    data class SurvivalRequirement(val days: Int) : BequestCondition
    data class AgeRequirement(val minimumAge: Int) : BequestCondition
    data class CharitableCondition(val charity: String) : BequestCondition
    data class CustomCondition(val condition: String) : BequestCondition
}

@Serializable
data class ResiduaryClause(
    val beneficiaries: Indexed<Join<BeneficiaryId, Decimal>>, // ID and percentage
    val contingentBeneficiaries: Indexed<Join<BeneficiaryId, Decimal>>?
)

@Serializable
data class WillContest(
    val contestant: StakeholderId,
    val grounds: ContestGrounds,
    val filingDate: Instant,
    val status: ContestStatus
)

enum class ContestGrounds {
    LACK_OF_TESTAMENTARY_CAPACITY,
    UNDUE_INFLUENCE,
    FRAUD,
    IMPROPER_EXECUTION,
    REVOCATION
}

enum class ContestStatus {
    PENDING,
    DISMISSED,
    SETTLED,
    TRIAL
}

/**
 * Heirs at law (for intestate succession)
 */
@Serializable
data class Heir(
    val id: BeneficiaryId,
    val name: String,
    val relationship: HeirRelationship,
    val inheritanceShare: Decimal,
    val disclaimerStatus: DisclaimerStatus?
)

enum class HeirRelationship {
    SPOUSE,
    CHILD,
    PARENT,
    SIBLING,
    GRANDCHILD,
    GRANDPARENT,
    AUNT_UNCLE,
    COUSIN,
    OTHER_RELATIVE
}

enum class DisclaimerStatus {
    DISCLAIMED,
    PARTIAL_DISCLAIMER,
    NO_DISCLAIMER
}

/**
 * Estate beneficiary
 */
@Serializable
data class EstateBeneficiary(
    val id: BeneficiaryId,
    val name: String,
    val beneficiaryType: BeneficiaryType,
    val distributionType: DistributionType,
    val inheritancePercentage: Decimal,
    val specificBequest: Price?,
    val distributionRights: DistributionRights,
    val restrictions: Indexed<BeneficiaryRestriction>?,
    val minorStatus: MinorBeneficiaryInfo?
)

enum class BeneficiaryType {
    INDIVIDUAL,
    CHARITY,
    TRUST,
    ESTATE,
    ENTITY
}

enum class DistributionType {
    SPECIFIC_BEQUEST,
    RESIDUARY,
    INTESTATE_SHARE
}

@Serializable
data class MinorBeneficiaryInfo(
    val guardian: StakeholderId,
    val trustRequired: Boolean,
    val custodialAccount: String?
)

/**
 * Estate assets
 */
@Serializable
data class EstateAsset(
    val id: String,
    val description: String,
    val assetType: EstateAssetType,
    val dateOfDeathValue: Price,
    val currentValue: Price,
    val location: String?,
    val ownershipType: OwnershipType,
    val status: AssetStatus,
    val appraisal: AssetAppraisal?,
    val saleInfo: AssetSaleInfo?
)

enum class EstateAssetType {
    REAL_ESTATE,
    BANK_ACCOUNT,
    INVESTMENT_ACCOUNT,
    LIFE_INSURANCE,
    RETIREMENT_ACCOUNT,
    PERSONAL_PROPERTY,
    BUSINESS_INTEREST,
    INTELLECTUAL_PROPERTY,
    COLLECTIBLES,
    VEHICLE,
    JEWELRY
}

enum class OwnershipType {
    SOLE_OWNERSHIP,
    JOINT_TENANCY,
    TENANCY_IN_COMMON,
    COMMUNITY_PROPERTY,
    TRUST_OWNED,
    BENEFICIARY_DESIGNATION
}

enum class AssetStatus {
    INVENTORY,
    APPRAISED,
    MARKETED,
    SOLD,
    DISTRIBUTED,
    RETAINED
}

@Serializable
data class AssetAppraisal(
    val appraiser: String,
    val appraisalDate: Instant,
    val appraisedValue: Price,
    val methodUsed: String
)

@Serializable
data class AssetSaleInfo(
    val saleDate: Instant,
    val salePrice: Price,
    val buyer: String,
    val realEstateAgent: String?
)

/**
 * Estate liabilities
 */
@Serializable
data class EstateLiability(
    val id: String,
    val creditor: String,
    val liabilityType: LiabilityType,
    val amount: Price,
    val dueDate: Instant?,
    val priority: Int, // 1 = highest priority
    val status: LiabilityStatus,
    val secured: Boolean,
    val collateral: String?
)

enum class LiabilityType {
    FUNERAL_EXPENSES,
    ADMINISTRATION_COSTS,
    TAXES,
    MORTGAGE,
    CREDIT_CARD,
    PERSONAL_LOAN,
    MEDICAL_BILLS,
    UTILITY_BILLS,
    OTHER_DEBT
}

enum class LiabilityStatus {
    PENDING,
    DISPUTED,
    APPROVED,
    PAID,
    REJECTED
}

/**
 * Probate information
 */
@Serializable
data class ProbateInfo(
    val probateCourtJurisdiction: JurisdictionCode,
    val probateCaseNumber: String,
    val probatePhase: ProbatePhase,
    val filingDate: Instant,
    val probateType: ProbateType,
    val estimatedClosingDate: Instant?,
    val inventoryFiled: Boolean,
    val inventoryDate: Instant?,
    val creditorNoticePublished: Boolean,
    val creditorClaimDeadline: Instant?
)

enum class ProbatePhase {
    PETITION_FILED,
    EXECUTOR_APPOINTED,
    INVENTORY_PHASE,
    CREDITOR_CLAIMS,
    TAX_RESOLUTION,
    DISTRIBUTION,
    CLOSED
}

enum class ProbateType {
    FORMAL_PROBATE,
    INFORMAL_PROBATE,
    SMALL_ESTATE_AFFIDAVIT,
    SUMMARY_ADMINISTRATION,
    ANCILLARY_PROBATE
}

/**
 * Estate tax information
 */
@Serializable
data class EstateTaxInfo(
    val grossEstate: Price,
    val deductions: Price,
    val taxableEstate: Price,
    val estateTaxOwed: Price?,
    val form706Required: Boolean,
    val form706Filed: Boolean,
    val form706DueDate: Instant?,
    val generationSkippingTax: Price?,
    val stateTaxOwed: Price?
)

/**
 * Estate distributions
 */
@Serializable
data class EstateDistribution(
    val distributionId: String,
    val beneficiaryId: BeneficiaryId,
    val distributionType: DistributionType,
    val assetDescription: String,
    val value: Price,
    val distributionDate: Instant,
    val receipt: DistributionReceipt?
)

@Serializable
data class DistributionReceipt(
    val receiptDate: Instant,
    val acknowledged: Boolean,
    val conditions: String?
)

/**
 * Administrator bond
 */
@Serializable
data class AdministratorBond(
    val bondCompany: String,
    val bondAmount: Price,
    val effectiveDate: Instant,
    val expirationDate: Instant,
    val sureties: Indexed<String>
)

/**
 * Fiduciary accounting
 */
@Serializable
data class FiduciaryAccounting(
    val accountingPeriodStart: Instant,
    val accountingPeriodEnd: Instant,
    val openingBalance: Price,
    val receipts: Indexed<AccountingReceipt>,
    val disbursements: Indexed<AccountingDisbursement>,
    val closingBalance: Price,
    val filedWithCourt: Boolean,
    val filingDate: Instant?
)

@Serializable
data class AccountingReceipt(
    val description: String,
    val amount: Price,
    val date: Instant,
    val source: String
)

@Serializable
data class AccountingDisbursement(
    val description: String,
    val amount: Price,
    val date: Instant,
    val payee: String,
    val purpose: String
)

/**
 * Estate operations
 */
object EstateOperations {
    
    /**
     * Create an estate upon death
     */
    fun createEstate(
        decedent: Decedent,
        executor: Executor,
        jurisdiction: JurisdictionCode,
        will: Will?
    ): Estate {
        val entityId = EntityOperations.generateEntityId(EntityType.ESTATE, jurisdiction)
        val estateName = "Estate of ${decedent.fullName}"
        
        return Estate(
            id = entityId,
            name = estateName,
            status = EntityStatus.FORMING,
            jurisdiction = jurisdiction,
            taxId = null,
            formationDate = decedent.dateOfDeath,
            dissolutionDate = null,
            decedent = decedent,
            executor = executor,
            coExecutors = null,
            will = will,
            heirs = determineHeirs(decedent, will),
            beneficiaries = determineBeneficiaries(will, decedent),
            assets = emptyIndex(),
            liabilities = emptyIndex(),
            probateInfo = createInitialProbateInfo(jurisdiction),
            taxInfo = createInitialTaxInfo(),
            distributions = emptyIndex(),
            administratorBond = null,
            fiduciaryAccountings = emptyIndex()
        )
    }
    
    /**
     * Determine heirs based on intestacy laws
     */
    private fun determineHeirs(decedent: Decedent, will: Will?): Indexed<Heir> {
        if (will != null) {
            // If there's a will, heirs may still be relevant for omitted heir statutes
            return emptyIndex()
        }
        
        // Simplified intestacy determination - would need state-specific logic
        val heirs = mutableListOf<Heir>()
        
        // Add spouse if any
        if (decedent.maritalStatus == MaritalStatus.MARRIED) {
            heirs.add(Heir(
                id = BeneficiaryId("spouse"),
                name = "Surviving Spouse",
                relationship = HeirRelationship.SPOUSE,
                inheritanceShare = if (decedent.children?.size ?: 0 > 0) 50.0 else 100.0,
                disclaimerStatus = null
            ))
        }
        
        // Add children
        decedent.children?.play?.forEach { child ->
            val sharePerChild = if (decedent.maritalStatus == MaritalStatus.MARRIED) {
                50.0 / (decedent.children?.size ?: 1)
            } else {
                100.0 / (decedent.children?.size ?: 1)
            }
            
            heirs.add(Heir(
                id = BeneficiaryId(child.id.value),
                name = child.name,
                relationship = HeirRelationship.CHILD,
                inheritanceShare = sharePerChild,
                disclaimerStatus = null
            ))
        }
        
        return heirs.size j { i -> heirs[i] }
    }
    
    /**
     * Determine beneficiaries from will
     */
    private fun determineBeneficiaries(will: Will?, decedent: Decedent): Indexed<EstateBeneficiary> {
        val beneficiaries = mutableListOf<EstateBeneficiary>()
        
        if (will != null) {
            // Process will bequests
            will.bequests.play.forEach { bequest ->
                beneficiaries.add(EstateBeneficiary(
                    id = bequest.beneficiaryId,
                    name = "Will Beneficiary", // Would lookup actual name
                    beneficiaryType = BeneficiaryType.INDIVIDUAL,
                    distributionType = when (bequest.bequestType) {
                        BequestType.SPECIFIC_PROPERTY, BequestType.MONETARY_GIFT -> DistributionType.SPECIFIC_BEQUEST
                        else -> DistributionType.RESIDUARY
                    },
                    inheritancePercentage = 0.0, // Would calculate based on bequest
                    specificBequest = bequest.value,
                    distributionRights = DistributionRights(
                        priority = 1,
                        percentage = 0.0,
                        minimumDistribution = null,
                        maximumDistribution = null,
                        distributionSchedule = DistributionSchedule.Discretionary
                    ),
                    restrictions = null,
                    minorStatus = null
                ))
            }
        } else {
            // Process intestate heirs as beneficiaries
            determineHeirs(decedent, null).play.forEach { heir ->
                beneficiaries.add(EstateBeneficiary(
                    id = heir.id,
                    name = heir.name,
                    beneficiaryType = BeneficiaryType.INDIVIDUAL,
                    distributionType = DistributionType.INTESTATE_SHARE,
                    inheritancePercentage = heir.inheritanceShare,
                    specificBequest = null,
                    distributionRights = DistributionRights(
                        priority = 1,
                        percentage = heir.inheritanceShare,
                        minimumDistribution = null,
                        maximumDistribution = null,
                        distributionSchedule = DistributionSchedule.Discretionary
                    ),
                    restrictions = null,
                    minorStatus = null
                ))
            }
        }
        
        return beneficiaries.size j { i -> beneficiaries[i] }
    }
    
    /**
     * Create initial probate information
     */
    private fun createInitialProbateInfo(jurisdiction: JurisdictionCode): ProbateInfo {
        return ProbateInfo(
            probateCourtJurisdiction = jurisdiction,
            probateCaseNumber = "", // To be assigned by court
            probatePhase = ProbatePhase.PETITION_FILED,
            filingDate = kotlinx.datetime.Clock.System.now(),
            probateType = ProbateType.FORMAL_PROBATE,
            estimatedClosingDate = null,
            inventoryFiled = false,
            inventoryDate = null,
            creditorNoticePublished = false,
            creditorClaimDeadline = null
        )
    }
    
    /**
     * Create initial tax information
     */
    private fun createInitialTaxInfo(): EstateTaxInfo {
        return EstateTaxInfo(
            grossEstate = Price(0.0),
            deductions = Price(0.0),
            taxableEstate = Price(0.0),
            estateTaxOwed = null,
            form706Required = false,
            form706Filed = false,
            form706DueDate = null,
            generationSkippingTax = null,
            stateTaxOwed = null
        )
    }
    
    /**
     * Calculate estate tax liability
     */
    fun calculateEstateTax(estate: Estate): Price {
        val federalExemption = Price(12920000.0) // 2023 federal exemption
        val taxableEstate = maxOf(0.0, estate.taxInfo.grossEstate.value - federalExemption.value)
        
        // Simplified calculation - actual rates are progressive
        val taxRate = 0.40 // 40% top rate
        return Price(taxableEstate * taxRate)
    }
    
    /**
     * Check if Form 706 is required
     */
    fun isForm706Required(grossEstate: Price): Boolean {
        val filingThreshold = Price(12920000.0) // 2023 threshold
        return grossEstate.value > filingThreshold.value
    }
}