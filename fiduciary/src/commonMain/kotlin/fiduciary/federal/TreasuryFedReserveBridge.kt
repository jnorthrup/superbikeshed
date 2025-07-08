package fiduciary.federal

import borg.trikeshed.lib.*
import fiduciary.ledger.*
import io.islandtime.*
import kotlinx.coroutines.flow.*

/**
 * US Treasury and Federal Reserve services mapping
 * Shows where internal bankers act as the bridge between these institutions
 */
class TreasuryFedReserveBridge {
    
    /**
     * Service provided by federal institution
     */
    data class FederalService(
        val provider: FederalProvider,
        val serviceName: String,
        val serviceCode: String,
        val category: ServiceCategory,
        val accessMethod: AccessMethod,
        val privateBankerRequired: Boolean,
        val description: String
    )
    
    enum class FederalProvider {
        US_TREASURY,
        FEDERAL_RESERVE,
        BOTH // Services offered by both
    }
    
    enum class ServiceCategory {
        SECURITIES,
        PAYMENTS,
        ACCOUNTS,
        LENDING,
        CUSTODY,
        INFORMATION,
        REGULATORY,
        MONETARY_POLICY
    }
    
    enum class AccessMethod {
        DIRECT_PUBLIC,         // Public can access directly
        BANK_INTERMEDIATED,    // Must go through bank
        RESTRICTED,           // Special authorization required
        WHOLESALE_ONLY        // Only banks/institutions
    }
    
    /**
     * Bridge point where Treasury and Fed services converge
     */
    data class ServiceBridge(
        val treasuryService: FederalService,
        val fedService: FederalService,
        val bridgeType: BridgeType,
        val privateBankerRole: PrivateBankerRole,
        val commonInfrastructure: String,
        val separationSteps: Int  // 1 = direct bridge via banker
    )
    
    enum class BridgeType {
        PAYMENT_SETTLEMENT,    // ACH, Wire, etc.
        SECURITIES_CUSTODY,    // Treasury securities
        ACCOUNT_SERVICES,      // TT&L, Reserve accounts
        COLLATERAL_MGMT,      // Repo, discount window
        AUCTION_PARTICIPATION, // Treasury auctions
        CASH_MANAGEMENT       // Government deposits
    }
    
    /**
     * Private banker's role in bridging services
     */
    data class PrivateBankerRole(
        val roleName: String,
        val responsibilities: Set<String>,
        val requiredCertifications: Set<String>,
        val fiduciaryDuties: Set<String>
    )
    
    /**
     * Complete service catalog
     */
    object ServiceCatalog {
        
        /**
         * US Treasury Services
         */
        val treasuryServices = listOf(
            // Direct Public Services
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "TreasuryDirect",
                serviceCode = "TD",
                category = ServiceCategory.SECURITIES,
                accessMethod = AccessMethod.DIRECT_PUBLIC,
                privateBankerRequired = false,
                description = "Direct purchase of Treasury securities by individuals"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Electronic Federal Tax Payment System (EFTPS)",
                serviceCode = "EFTPS",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.DIRECT_PUBLIC,
                privateBankerRequired = false,
                description = "Tax payment system for businesses and individuals"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Pay.gov",
                serviceCode = "PAY",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.DIRECT_PUBLIC,
                privateBankerRequired = false,
                description = "Government payment portal"
            ),
            
            // Bank-Intermediated Services
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Treasury Tax and Loan (TT&L)",
                serviceCode = "TTL",
                category = ServiceCategory.ACCOUNTS,
                accessMethod = AccessMethod.BANK_INTERMEDIATED,
                privateBankerRequired = true,
                description = "Bank accounts holding federal tax deposits"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Treasury Auction System",
                serviceCode = "TAAPS",
                category = ServiceCategory.SECURITIES,
                accessMethod = AccessMethod.BANK_INTERMEDIATED,
                privateBankerRequired = true,
                description = "Primary dealer participation in Treasury auctions"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Treasury Securities Services",
                serviceCode = "TSS",
                category = ServiceCategory.SECURITIES,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Wholesale Treasury securities operations"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Treasury Offset Program (TOP)",
                serviceCode = "TOP",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.BANK_INTERMEDIATED,
                privateBankerRequired = true,
                description = "Debt collection through payment intercept"
            ),
            FederalService(
                provider = FederalProvider.US_TREASURY,
                serviceName = "Foreign Bank Account Reporting",
                serviceCode = "FBAR",
                category = ServiceCategory.REGULATORY,
                accessMethod = AccessMethod.BANK_INTERMEDIATED,
                privateBankerRequired = true,
                description = "FinCEN Form 114 reporting"
            )
        )
        
        /**
         * Federal Reserve Services
         */
        val fedServices = listOf(
            // Payment Services
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Fedwire Funds Service",
                serviceCode = "FEDWIRE",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Large-value, time-critical payments"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Fedwire Securities Service",
                serviceCode = "FEDWIRE-SEC",
                category = ServiceCategory.SECURITIES,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Securities transfer and settlement"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "FedACH Services",
                serviceCode = "FEDACH",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Automated Clearing House operations"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Check Services",
                serviceCode = "CHECK21",
                category = ServiceCategory.PAYMENTS,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Check clearing and image exchange"
            ),
            
            // Account Services
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Reserve Account Management",
                serviceCode = "RAM",
                category = ServiceCategory.ACCOUNTS,
                accessMethod = AccessMethod.RESTRICTED,
                privateBankerRequired = true,
                description = "Master accounts for banks"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Term Deposit Facility",
                serviceCode = "TDF",
                category = ServiceCategory.ACCOUNTS,
                accessMethod = AccessMethod.RESTRICTED,
                privateBankerRequired = true,
                description = "Interest-bearing deposits for banks"
            ),
            
            // Lending Services
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Discount Window",
                serviceCode = "DW",
                category = ServiceCategory.LENDING,
                accessMethod = AccessMethod.RESTRICTED,
                privateBankerRequired = true,
                description = "Primary, secondary, and seasonal credit"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Repo Operations",
                serviceCode = "REPO",
                category = ServiceCategory.LENDING,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Open market operations"
            ),
            
            // Custody Services
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "Securities Safekeeping",
                serviceCode = "SAFEKEEP",
                category = ServiceCategory.CUSTODY,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Custody of securities for banks"
            ),
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "International Services",
                serviceCode = "INTL",
                category = ServiceCategory.CUSTODY,
                accessMethod = AccessMethod.RESTRICTED,
                privateBankerRequired = true,
                description = "Services for foreign central banks"
            ),
            
            // Information Services
            FederalService(
                provider = FederalProvider.FEDERAL_RESERVE,
                serviceName = "FedLine Solutions",
                serviceCode = "FEDLINE",
                category = ServiceCategory.INFORMATION,
                accessMethod = AccessMethod.WHOLESALE_ONLY,
                privateBankerRequired = true,
                description = "Electronic access to Fed services"
            )
        )
        
        /**
         * Service bridges - where Treasury and Fed converge
         */
        val serviceBridges = listOf(
            // Treasury Securities Bridge
            ServiceBridge(
                treasuryService = treasuryServices.find { it.serviceCode == "TAAPS" }!!,
                fedService = fedServices.find { it.serviceCode == "FEDWIRE-SEC" }!!,
                bridgeType = BridgeType.SECURITIES_CUSTODY,
                privateBankerRole = PrivateBankerRole(
                    roleName = "Primary Dealer",
                    responsibilities = setOf(
                        "Bid at Treasury auctions",
                        "Make markets in Treasury securities",
                        "Provide market intelligence to Fed",
                        "Participate in open market operations"
                    ),
                    requiredCertifications = setOf(
                        "FINRA Indexed 7",
                        "FINRA Indexed 63",
                        "Primary Dealer Agreement"
                    ),
                    fiduciaryDuties = setOf(
                        "Best execution",
                        "Fair pricing",
                        "Market stability"
                    )
                ),
                commonInfrastructure = "Treasury Automated Auction Processing System",
                separationSteps = 1
            ),
            
            // Payment Settlement Bridge
            ServiceBridge(
                treasuryService = treasuryServices.find { it.serviceCode == "TTL" }!!,
                fedService = fedServices.find { it.serviceCode == "FEDWIRE" }!!,
                bridgeType = BridgeType.PAYMENT_SETTLEMENT,
                privateBankerRole = PrivateBankerRole(
                    roleName = "Treasury Tax & Loan Depositary",
                    responsibilities = setOf(
                        "Accept federal tax deposits",
                        "Manage TT&L account balances",
                        "Process Treasury calls",
                        "Maintain collateral"
                    ),
                    requiredCertifications = setOf(
                        "TT&L Depositary Agreement",
                        "Collateral Pledge Agreement"
                    ),
                    fiduciaryDuties = setOf(
                        "Safeguard government funds",
                        "Maintain adequate collateral",
                        "Timely remittance"
                    )
                ),
                commonInfrastructure = "Treasury Financial Management Service",
                separationSteps = 1
            ),
            
            // Collateral Management Bridge
            ServiceBridge(
                treasuryService = treasuryServices.find { it.serviceCode == "TSS" }!!,
                fedService = fedServices.find { it.serviceCode == "DW" }!!,
                bridgeType = BridgeType.COLLATERAL_MGMT,
                privateBankerRole = PrivateBankerRole(
                    roleName = "Collateral Manager",
                    responsibilities = setOf(
                        "Value Treasury collateral",
                        "Monitor margin requirements",
                        "Execute collateral substitutions",
                        "Report to regulators"
                    ),
                    requiredCertifications = setOf(
                        "Collateral Management Certification",
                        "Risk Management Professional"
                    ),
                    fiduciaryDuties = setOf(
                        "Accurate valuation",
                        "Proper segregation",
                        "Regulatory compliance"
                    )
                ),
                commonInfrastructure = "Discount Window Application System",
                separationSteps = 1
            ),
            
            // Cash Management Bridge
            ServiceBridge(
                treasuryService = treasuryServices.find { it.serviceCode == "EFTPS" }!!,
                fedService = fedServices.find { it.serviceCode == "FEDACH" }!!,
                bridgeType = BridgeType.CASH_MANAGEMENT,
                privateBankerRole = PrivateBankerRole(
                    roleName = "Government Banking Specialist",
                    responsibilities = setOf(
                        "Process tax payments",
                        "Manage government deposits",
                        "Reconcile Treasury accounts",
                        "Provide cash position reporting"
                    ),
                    requiredCertifications = setOf(
                        "Certified Treasury Professional",
                        "Government Banking Certification"
                    ),
                    fiduciaryDuties = setOf(
                        "Accurate accounting",
                        "Timely processing",
                        "Confidentiality"
                    )
                ),
                commonInfrastructure = "ACH Network",
                separationSteps = 1
            )
        )
        
        /**
         * Find all one-step separations via internal banker
         */
        fun findOneStepSeparations(): Flow<ServiceBridge> = flow {
            serviceBridges
                .filter { it.separationSteps == 1 }
                .forEach { emit(it) }
        }
        
        /**
         * Services requiring internal banker intermediation
         */
        fun servicesRequiringBanker(): Flow<FederalService> = flow {
            (treasuryServices + fedServices)
                .filter { it.privateBankerRequired }
                .forEach { emit(it) }
        }
        
        /**
         * Direct public access services (no banker needed)
         */
        fun directPublicServices(): Flow<FederalService> = flow {
            (treasuryServices + fedServices)
                .filter { it.accessMethod == AccessMethod.DIRECT_PUBLIC }
                .forEach { emit(it) }
        }
    }
    
    /**
     * Private banker capabilities and authorizations
     */
    data class PrivateBankerAuthorization(
        val bankerId: String,
        val institutionName: String,
        val federalAuthorizations: Set<FederalAuthorization>,
        val serviceAccess: Map<String, AccessLevel>,
        val fiduciaryCapacity: FiduciaryCapacity,
        val complianceStatus: ComplianceStatus
    )
    
    data class FederalAuthorization(
        val authType: AuthorizationType,
        val grantingAgency: FederalProvider,
        val authNumber: String,
        val effectiveDate: LocalDate,
        val expiryDate: LocalDate?,
        val scope: Set<String>
    )
    
    enum class AuthorizationType {
        PRIMARY_DEALER,
        TTL_DEPOSITARY,
        FEDWIRE_PARTICIPANT,
        ACH_ORIGINATOR,
        CUSTODIAL_BANK,
        CLEARING_BANK,
        CORRESPONDENT_BANK
    }
    
    enum class AccessLevel {
        FULL, LIMITED, VIEW_ONLY, NONE
    }
    
    data class FiduciaryCapacity(
        val canActAsTrustee: Boolean,
        val canHoldCustody: Boolean,
        val canExecuteTrades: Boolean,
        val canExtendCredit: Boolean,
        val regulatoryOversight: Set<String>
    )
    
    data class ComplianceStatus(
        val amlProgram: Boolean,
        val ofacScreening: Boolean,
        val bsaReporting: Boolean,
        val lastExamDate: LocalDate,
        val rating: String?
    )
}