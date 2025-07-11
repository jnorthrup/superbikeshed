package fiduciary.federal

import borg.trikeshed.lib.*
import fiduciary.ledger.*
import io.islandtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Federal Reserve and Treasury API integration system
 * Integrates FRED, NY Fed Markets, Treasury Fiscal Data, and FRB Services APIs
 */
class FederalDataAPIs {
    
    /**
     * API client configurations
     */
    data class APIConfig(
        val fredApiKey: String,
        val nyFedApiKey: String?,
        val treasuryApiKey: String?,
        val frbServicesCredentials: FRBCredentials?
    )
    
    data class FRBCredentials(
        val clientId: String,
        val clientSecret: String,
        val certificatePath: String
    )
    
    /**
     * FRED API Client - Federal Reserve Economic Data
     * https://fred.stlouisfed.org/docs/api/fred/
     */
    class FREDClient(internal val apiKey: String) {
        
        internal val baseUrl = "https://api.stlouisfed.org/fred"
        
        /**
         * Economic data series for fiduciary analysis
         */
        suspend fun getEconomicSeries(
            seriesId: String,
            startDate: LocalDate? = null,
            endDate: LocalDate? = null
        ): Flow<EconomicDataPoint> = flow {
            val params = buildMap {
                put("series_id", seriesId)
                put("api_key", apiKey)
                put("file_type", "json")
                startDate?.let { put("observation_start", it.toString()) }
                endDate?.let { put("observation_end", it.toString()) }
            }
            
            val response = httpGet("$baseUrl/series/observations", params)
            val data = Json.decodeFromString<FREDResponse>(response)
            
            data.observations.forEach { obs →
                if (obs.value != ".") {  // FRED uses "." for missing values
                    emit(EconomicDataPoint(
                        seriesId = seriesId,
                        date = LocalDate.parse(obs.date),
                        value = obs.value.toDouble(),
                        timestamp = Clock.System.now()
                    ))
                }
            }
        }
        
        /**
         * Key fiduciary-relevant economic indicators
         */
        suspend fun getFiduciaryIndicators(): Flow<FiduciaryIndicator> = flow {
            val indicators = mapOf(
                "FEDFUNDS" to "Federal Funds Rate",
                "DFF" to "Daily Federal Funds Rate",
                "TB3MS" to "3-Month Treasury Rate",
                "TB6MS" to "6-Month Treasury Rate", 
                "GS1" to "1-Year Treasury Rate",
                "GS2" to "2-Year Treasury Rate",
                "GS5" to "5-Year Treasury Rate",
                "GS10" to "10-Year Treasury Rate",
                "GS30" to "30-Year Treasury Rate",
                "MORTGAGE30US" to "30-Year Mortgage Rate",
                "UNRATE" to "Unemployment Rate",
                "CPIAUCSL" to "Consumer Price Index",
                "GDP" to "Gross Domestic Product",
                "M2SL" to "M2 Money Supply",
                "DEXUSEU" to "US/Euro Exchange Rate",
                "GOLDAMGBD228NLBM" to "Gold Price"
            )
            
            indicators.forEach { (seriesId, description) →
                getEconomicSeries(seriesId).collect { dataPoint →
                    emit(FiduciaryIndicator(
                        indicator = description,
                        seriesId = seriesId,
                        currentValue = dataPoint.value,
                        date = dataPoint.date,
                        category = categorizeIndicator(seriesId),
                        fiduciaryRelevance = assessFiduciaryRelevance(seriesId)
                    ))
                }
            }
        }
        
        /**
         * Search for economic series
         */
        suspend fun searchSeries(
            searchText: String,
            tags: Set<String> = emptySet()
        ): Flow<SeriesInfo> = flow {
            val params = buildMap {
                put("search_text", searchText)
                put("api_key", apiKey)
                put("file_type", "json")
                if (tags.isNotEmpty()) {
                    put("tag_names", tags.joinToString(";"))
                }
            }
            
            val response = httpGet("$baseUrl/series/search", params)
            val data = Json.decodeFromString<FREDSeriesResponse>(response)
            
            data.series.forEach { series →
                emit(SeriesInfo(
                    id = series.id,
                    title = series.title,
                    units = series.units,
                    frequency = series.frequency,
                    lastUpdated = LocalDate.parse(series.lastUpdated)
                ))
            }
        }
    }
    
    /**
     * NY Fed Markets API Client
     * https://markets.newyorkfed.org/static/docs/markets-api.html
     */
    class NYFedMarketsClient(internal val apiKey: String?) {
        
        internal val baseUrl = "https://markets.newyorkfed.org/api"
        
        /**
         * Get repo operations data
         */
        suspend fun getRepoOperations(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<RepoOperation> = flow {
            val params = buildMap {
                put("startDate", startDate.toString())
                put("endDate", endDate.toString())
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/repo/all/search.json", params)
            val data = Json.decodeFromString<NYFedRepoResponse>(response)
            
            data.repo.forEach { op →
                emit(RepoOperation(
                    operationDate = LocalDate.parse(op.operationDate),
                    operationType = op.operationType,
                    maturityDate = LocalDate.parse(op.maturityDate),
                    amountAccepted = op.amountAccepted,
                    rate = op.rate,
                    proposedAmount = op.proposedAmount
                ))
            }
        }
        
        /**
         * Get SOMA holdings (System Open Market Account)
         */
        suspend fun getSOMAHoldings(
            date: LocalDate? = null
        ): Flow<SOMAHolding> = flow {
            val params = buildMap {
                date?.let { put("date", it.toString()) }
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/soma/summary.json", params)
            val data = Json.decodeFromString<NYFedSomaResponse>(response)
            
            data.soma.holdings.forEach { holding →
                emit(SOMAHolding(
                    asOfDate = LocalDate.parse(holding.asOfDate),
                    securityType = holding.securityType,
                    parValue = holding.parValue,
                    percentOfPortfolio = holding.percentOfPortfolio
                ))
            }
        }
        
        /**
         * Get primary dealer statistics
         */
        suspend fun getPrimaryDealerStats(): Flow<PrimaryDealerStat> = flow {
            val params = buildMap {
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/pd/search.json", params)
            val data = Json.decodeFromString<NYFedPDResponse>(response)
            
            data.pd.forEach { stat →
                emit(PrimaryDealerStat(
                    reportDate = LocalDate.parse(stat.reportDate),
                    dealerName = stat.dealerName,
                    netPosition = stat.netPosition,
                    grossPosition = stat.grossPosition,
                    securityType = stat.securityType
                ))
            }
        }
    }
    
    /**
     * Treasury Fiscal Data API Client
     * https://fiscaldata.treasury.gov/api-documentation/
     */
    class TreasuryFiscalClient(internal val apiKey: String?) {
        
        internal val baseUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service"
        
        /**
         * Get daily treasury statement
         */
        suspend fun getDailyTreasuryStatement(
            date: LocalDate? = null
        ): Flow<TreasuryStatement> = flow {
            val params = buildMap {
                put("format", "json")
                date?.let { put("filter", "record_date:eq:${it}") }
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/dts/dts_table_1", params)
            val data = Json.decodeFromString<TreasuryDTSResponse>(response)
            
            data.data.forEach { statement →
                emit(TreasuryStatement(
                    recordDate = LocalDate.parse(statement.recordDate),
                    openingBalance = statement.openingBalance?.toDoubleOrNull(),
                    receipts = statement.receipts?.toDoubleOrNull(),
                    outlays = statement.outlays?.toDoubleOrNull(),
                    closingBalance = statement.closingBalance?.toDoubleOrNull(),
                    accountType = statement.accountType
                ))
            }
        }
        
        /**
         * Get debt to the penny data
         */
        suspend fun getDebtData(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<DebtRecord> = flow {
            val params = buildMap {
                put("format", "json")
                put("filter", "record_date:gte:$startDate,record_date:lte:$endDate")
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/debt_to_penny", params)
            val data = Json.decodeFromString<TreasuryDebtResponse>(response)
            
            data.data.forEach { debt →
                emit(DebtRecord(
                    recordDate = LocalDate.parse(debt.recordDate),
                    totalDebt = debt.totalDebt.toDouble(),
                    intragovernmentalHoldings = debt.intragovernmentalHoldings.toDouble(),
                    debtHeldByPublic = debt.debtHeldByPublic.toDouble()
                ))
            }
        }
        
        /**
         * Get interest rates on treasury securities
         */
        suspend fun getTreasuryRates(): Flow<TreasuryRate> = flow {
            val params = buildMap {
                put("format", "json")
                put("sort", "-record_date")
                put("page[size]", "100")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/avg_interest_rates", params)
            val data = Json.decodeFromString<TreasuryRatesResponse>(response)
            
            data.data.forEach { rate →
                emit(TreasuryRate(
                    recordDate = LocalDate.parse(rate.recordDate),
                    securityType = rate.securityType,
                    avgInterestRate = rate.avgInterestRate?.toDoubleOrNull(),
                    securityDesc = rate.securityDesc
                ))
            }
        }
        
        /**
         * Get Monthly Treasury Statement (MTS)
         */
        suspend fun getMonthlyTreasuryStatement(
            year: Int,
            month: Int? = null
        ): Flow<MonthlyTreasuryStatement> = flow {
            val params = buildMap {
                put("format", "json")
                put("filter", "record_fiscal_year:eq:$year")
                month?.let { put("filter", "record_calendar_month:eq:$it") }
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/mts/mts_table_1", params)
            val data = Json.decodeFromString<TreasuryMTSResponse>(response)
            
            data.data.forEach { statement →
                emit(MonthlyTreasuryStatement(
                    recordDate = LocalDate.parse(statement.recordDate),
                    fiscalYear = statement.recordFiscalYear.toInt(),
                    calendarMonth = statement.recordCalendarMonth?.toIntOrNull(),
                    receipts = statement.currentMonthReceipts?.toDoubleOrNull(),
                    outlays = statement.currentMonthOutlays?.toDoubleOrNull(),
                    surplus = statement.currentMonthSurplus?.toDoubleOrNull(),
                    ytdReceipts = statement.fiscalYearToDateReceipts?.toDoubleOrNull(),
                    ytdOutlays = statement.fiscalYearToDateOutlays?.toDoubleOrNull(),
                    ytdSurplus = statement.fiscalYearToDateSurplus?.toDoubleOrNull()
                ))
            }
        }
        
        /**
         * Get Treasury auction data
         */
        suspend fun getTreasuryAuctions(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<TreasuryAuction> = flow {
            val params = buildMap {
                put("format", "json")
                put("filter", "auction_date:gte:$startDate,auction_date:lte:$endDate")
                put("sort", "-auction_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/auctions_query", params)
            val data = Json.decodeFromString<TreasuryAuctionResponse>(response)
            
            data.data.forEach { auction →
                emit(TreasuryAuction(
                    auctionDate = LocalDate.parse(auction.auctionDate),
                    securityType = auction.securityType,
                    securityTerm = auction.securityTerm,
                    maturityDate = LocalDate.parse(auction.maturityDate),
                    interestRate = auction.interestRate?.toDoubleOrNull(),
                    highYield = auction.highYield?.toDoubleOrNull(),
                    allotmentRatio = auction.allotmentRatio?.toDoubleOrNull(),
                    totalAccepted = auction.totalAccepted?.toDoubleOrNull(),
                    competitiveTendered = auction.competitiveTendered?.toDoubleOrNull(),
                    noncompetitiveTendered = auction.noncompetitiveTendered?.toDoubleOrNull()
                ))
            }
        }
        
        /**
         * Get Treasury securities outstanding
         */
        suspend fun getTreasurySecuritiesOutstanding(
            date: LocalDate? = null
        ): Flow<TreasurySecurity> = flow {
            val params = buildMap {
                put("format", "json")
                date?.let { put("filter", "record_date:eq:$it") }
                put("sort", "-record_date")
                put("page[size]", "1000")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/securities_outstanding", params)
            val data = Json.decodeFromString<TreasurySecuritiesResponse>(response)
            
            data.data.forEach { security →
                emit(TreasurySecurity(
                    recordDate = LocalDate.parse(security.recordDate),
                    cusip = security.cusip,
                    securityType = security.securityType,
                    securityClass = security.securityClass,
                    interestRate = security.interestRate?.toDoubleOrNull(),
                    maturityDate = security.maturityDate?.let { LocalDate.parse(it) },
                    outstandingAmount = security.outstandingAmount?.toDoubleOrNull(),
                    originalIssueDate = security.originalIssueDate?.let { LocalDate.parse(it) }
                ))
            }
        }
        
        /**
         * Get gift contributions to reduce debt
         */
        suspend fun getGiftContributions(
            year: Int? = null
        ): Flow<GiftContribution> = flow {
            val params = buildMap {
                put("format", "json")
                year?.let { put("filter", "record_fiscal_year:eq:$it") }
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/gift_contributions", params)
            val data = Json.decodeFromString<TreasuryGiftResponse>(response)
            
            data.data.forEach { gift →
                emit(GiftContribution(
                    recordDate = LocalDate.parse(gift.recordDate),
                    fiscalYear = gift.recordFiscalYear?.toIntOrNull(),
                    contributionAmount = gift.contributionAmount?.toDoubleOrNull(),
                    contributorName = gift.contributorName,
                    contributorState = gift.contributorState,
                    purpose = gift.purpose
                ))
            }
        }
        
        /**
         * Get Treasury operating cash balance
         */
        suspend fun getOperatingCashBalance(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<OperatingCashBalance> = flow {
            val params = buildMap {
                put("format", "json")
                put("filter", "record_date:gte:$startDate,record_date:lte:$endDate")
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/cash_balance", params)
            val data = Json.decodeFromString<TreasuryCashResponse>(response)
            
            data.data.forEach { balance →
                emit(OperatingCashBalance(
                    recordDate = LocalDate.parse(balance.recordDate),
                    openingBalance = balance.openingBalance?.toDoubleOrNull(),
                    totalReceipts = balance.totalReceipts?.toDoubleOrNull(),
                    totalWithdrawals = balance.totalWithdrawals?.toDoubleOrNull(),
                    closingBalance = balance.closingBalance?.toDoubleOrNull(),
                    accountType = balance.accountType
                ))
            }
        }
        
        /**
         * Get Treasury International Capital (TIC) data
         */
        suspend fun getTICData(
            country: String? = null,
            year: Int? = null
        ): Flow<TICData> = flow {
            val params = buildMap {
                put("format", "json")
                country?.let { put("filter", "country_desc:eq:$it") }
                year?.let { put("filter", "record_year:eq:$it") }
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/tic", params)
            val data = Json.decodeFromString<TreasuryTICResponse>(response)
            
            data.data.forEach { tic →
                emit(TICData(
                    recordDate = LocalDate.parse(tic.recordDate),
                    country = tic.countryDesc,
                    countryCode = tic.countryCode,
                    holdingsAmount = tic.holdingsAmount?.toDoubleOrNull(),
                    securityType = tic.securityType,
                    monthlyChange = tic.monthlyChange?.toDoubleOrNull(),
                    yearOverYearChange = tic.yearOverYearChange?.toDoubleOrNull()
                ))
            }
        }
        
        /**
         * Get Federal Investment Program (FIP) data
         */
        suspend fun getFIPData(
            agency: String? = null
        ): Flow<FIPData> = flow {
            val params = buildMap {
                put("format", "json")
                agency?.let { put("filter", "agency_nm:eq:$it") }
                put("sort", "-record_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/fip", params)
            val data = Json.decodeFromString<TreasuryFIPResponse>(response)
            
            data.data.forEach { fip →
                emit(FIPData(
                    recordDate = LocalDate.parse(fip.recordDate),
                    agencyName = fip.agencyName,
                    accountName = fip.accountName,
                    investmentBalance = fip.investmentBalance?.toDoubleOrNull(),
                    interestEarned = fip.interestEarned?.toDoubleOrNull(),
                    effectiveRate = fip.effectiveRate?.toDoubleOrNull()
                ))
            }
        }
        
        /**
         * Get Record Setting Auction Data
         */
        suspend fun getRecordSettingAuctions(): Flow<RecordSettingAuction> = flow {
            val params = buildMap {
                put("format", "json")
                put("sort", "-auction_date")
                apiKey?.let { put("api_key", it) }
            }
            
            val response = httpGet("$baseUrl/v1/accounting/od/record_setting_auction", params)
            val data = Json.decodeFromString<TreasuryRecordAuctionResponse>(response)
            
            data.data.forEach { auction →
                emit(RecordSettingAuction(
                    auctionDate = LocalDate.parse(auction.auctionDate),
                    securityType = auction.securityType,
                    recordType = auction.recordType,
                    recordValue = auction.recordValue?.toDoubleOrNull(),
                    previousRecord = auction.previousRecord?.toDoubleOrNull(),
                    recordDescription = auction.recordDescription
                ))
            }
        }
    }

    /**
     * IRS API Client - Internal Revenue Service
     * https://www.irs.gov/tax-professionals/get-an-api-client-id
     */
    class IRSClient(internal val clientId: String, internal val clientSecret: String) {
        
        internal val baseUrl = "https://api.irs.gov"
        internal val authUrl = "https://oauth.irs.gov"
        
        /**
         * Get tax return transcript
         */
        suspend fun getTaxReturnTranscript(
            taxYear: Int,
            taxPeriod: String,
            tin: String,
            transcriptType: TranscriptType
        ): TaxReturnTranscript = coroutineScope {
            val token = authenticateWithIRS()
            
            val params = mapOf(
                "taxYear" to taxYear.toString(),
                "taxPeriod" to taxPeriod,
                "tin" to tin,
                "transcriptType" to transcriptType.code
            )
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/transcript", params, token)
            val data = Json.decodeFromString<IRSTranscriptResponse>(response)
            
            TaxReturnTranscript(
                taxYear = taxYear,
                tin = tin,
                transcriptType = transcriptType,
                filingStatus = data.filingStatus,
                adjustedGrossIncome = data.adjustedGrossIncome?.toDoubleOrNull(),
                taxableIncome = data.taxableIncome?.toDoubleOrNull(),
                totalTax = data.totalTax?.toDoubleOrNull(),
                federalTaxWithheld = data.federalTaxWithheld?.toDoubleOrNull(),
                refundAmount = data.refundAmount?.toDoubleOrNull(),
                balanceDue = data.balanceDue?.toDoubleOrNull(),
                lineItems = data.lineItems.map { item →
                    TranscriptLineItem(
                        lineNumber = item.lineNumber,
                        description = item.description,
                        amount = item.amount?.toDoubleOrNull()
                    )
                }
            )
        }
        
        /**
         * Validate TIN (Tax Identification Number)
         */
        suspend fun validateTIN(tin: String, name: String): TINValidationResult = coroutineScope {
            val token = authenticateWithIRS()
            
            val payload = mapOf(
                "tin" to tin,
                "name" to name
            )
            
            val response = httpPostSecure("$baseUrl/taxpayer/v1/tin-validation", payload, token)
            val data = Json.decodeFromString<IRSTINResponse>(response)
            
            TINValidationResult(
                tin = tin,
                isValid = data.isValid,
                nameMatch = data.nameMatch,
                tinType = data.tinType,
                businessName = data.businessName,
                errorCode = data.errorCode,
                errorMessage = data.errorMessage
            )
        }
        
        /**
         * Get Form 1099 information
         */
        suspend fun get1099Information(
            taxYear: Int,
            tin: String,
            form1099Type: Form1099Type
        ): Flow<Form1099Info> = flow {
            val token = authenticateWithIRS()
            
            val params = mapOf(
                "taxYear" to taxYear.toString(),
                "tin" to tin,
                "formType" to form1099Type.code
            )
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/form1099", params, token)
            val data = Json.decodeFromString<IRS1099Response>(response)
            
            data.forms.forEach { form →
                emit(Form1099Info(
                    taxYear = taxYear,
                    formType = form1099Type,
                    payerTIN = form.payerTIN,
                    payerName = form.payerName,
                    recipientTIN = form.recipientTIN,
                    recipientName = form.recipientName,
                    federalIncomeTaxWithheld = form.federalIncomeTaxWithheld?.toDoubleOrNull(),
                    totalAmount = form.totalAmount?.toDoubleOrNull(),
                    boxes = form.boxes.associate { box →
                        box.boxNumber to (box.amount?.toDoubleOrNull() ?: 0.0)
                    }
                ))
            }
        }
        
        /**
         * Check refund status
         */
        suspend fun getRefundStatus(
            taxYear: Int,
            tin: String,
            refundAmount: Double
        ): RefundStatus = coroutineScope {
            val token = authenticateWithIRS()
            
            val params = mapOf(
                "taxYear" to taxYear.toString(),
                "tin" to tin,
                "refundAmount" to refundAmount.toString()
            )
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/refund-status", params, token)
            val data = Json.decodeFromString<IRSRefundResponse>(response)
            
            RefundStatus(
                taxYear = taxYear,
                tin = tin,
                status = RefundStatusType.valueOf(data.status),
                refundAmount = data.refundAmount?.toDoubleOrNull(),
                refundDate = data.refundDate?.let { LocalDate.parse(it) },
                directDepositDate = data.directDepositDate?.let { LocalDate.parse(it) },
                trackingNumber = data.trackingNumber
            )
        }
        
        /**
         * Get Employer Identification Number (EIN) status
         */
        suspend fun getEINStatus(ein: String): EINStatus = coroutineScope {
            val token = authenticateWithIRS()
            
            val params = mapOf("ein" to ein)
            
            val response = httpGetSecure("$baseUrl/business/v1/ein-status", params, token)
            val data = Json.decodeFromString<IRSEINResponse>(response)
            
            EINStatus(
                ein = ein,
                isActive = data.isActive,
                businessName = data.businessName,
                businessType = data.businessType,
                incorporationDate = data.incorporationDate?.let { LocalDate.parse(it) },
                einAssignedDate = data.einAssignedDate?.let { LocalDate.parse(it) },
                address = data.address?.let { addr →
                    BusinessAddress(
                        street = addr.street,
                        city = addr.city,
                        state = addr.state,
                        zipCode = addr.zipCode
                    )
                }
            )
        }
        
        /**
         * Get installment agreement information
         */
        suspend fun getInstallmentAgreements(tin: String): Flow<InstallmentAgreement> = flow {
            val token = authenticateWithIRS()
            
            val params = mapOf("tin" to tin)
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/installment-agreements", params, token)
            val data = Json.decodeFromString<IRSInstallmentResponse>(response)
            
            data.agreements.forEach { agreement →
                emit(InstallmentAgreement(
                    agreementNumber = agreement.agreementNumber,
                    tin = tin,
                    agreementType = agreement.agreementType,
                    establishedDate = LocalDate.parse(agreement.establishedDate),
                    originalBalance = agreement.originalBalance?.toDoubleOrNull(),
                    currentBalance = agreement.currentBalance?.toDoubleOrNull(),
                    monthlyPayment = agreement.monthlyPayment?.toDoubleOrNull(),
                    nextPaymentDate = agreement.nextPaymentDate?.let { LocalDate.parse(it) },
                    status = agreement.status,
                    defaultDate = agreement.defaultDate?.let { LocalDate.parse(it) }
                ))
            }
        }
        
        /**
         * Get levy and lien information
         */
        suspend fun getLevyLienInfo(tin: String): Flow<LevyLienInfo> = flow {
            val token = authenticateWithIRS()
            
            val params = mapOf("tin" to tin)
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/levy-lien", params, token)
            val data = Json.decodeFromString<IRSLevyLienResponse>(response)
            
            data.items.forEach { item →
                emit(LevyLienInfo(
                    tin = tin,
                    type = LevyLienType.valueOf(item.type),
                    amount = item.amount?.toDoubleOrNull(),
                    filedDate = LocalDate.parse(item.filedDate),
                    releaseDate = item.releaseDate?.let { LocalDate.parse(it) },
                    status = item.status,
                    jurisdiction = item.jurisdiction,
                    recordingOffice = item.recordingOffice
                ))
            }
        }
        
        /**
         * Get Offer in Compromise (OIC) status
         */
        suspend fun getOfferInCompromiseStatus(
            tin: String,
            applicationNumber: String
        ): OfferInCompromiseStatus = coroutineScope {
            val token = authenticateWithIRS()
            
            val params = mapOf(
                "tin" to tin,
                "applicationNumber" to applicationNumber
            )
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/offer-in-compromise", params, token)
            val data = Json.decodeFromString<IRSOICResponse>(response)
            
            OfferInCompromiseStatus(
                applicationNumber = applicationNumber,
                tin = tin,
                submissionDate = LocalDate.parse(data.submissionDate),
                status = data.status,
                offerAmount = data.offerAmount?.toDoubleOrNull(),
                acceptedAmount = data.acceptedAmount?.toDoubleOrNull(),
                reasonCode = data.reasonCode,
                lastAction = data.lastAction,
                lastActionDate = data.lastActionDate?.let { LocalDate.parse(it) }
            )
        }
        
        /**
         * Get prior year AGI for identity verification
         */
        suspend fun getPriorYearAGI(
            tin: String,
            taxYear: Int,
            spouseTIN: String? = null
        ): PriorYearAGI = coroutineScope {
            val token = authenticateWithIRS()
            
            val params = buildMap {
                put("tin", tin)
                put("taxYear", taxYear.toString())
                spouseTIN?.let { put("spouseTIN", it) }
            }
            
            val response = httpGetSecure("$baseUrl/taxpayer/v1/prior-year-agi", params, token)
            val data = Json.decodeFromString<IRSPriorYearAGIResponse>(response)
            
            PriorYearAGI(
                tin = tin,
                taxYear = taxYear,
                adjustedGrossIncome = data.adjustedGrossIncome?.toDoubleOrNull(),
                spouseAGI = data.spouseAGI?.toDoubleOrNull(),
                jointAGI = data.jointAGI?.toDoubleOrNull(),
                filingStatus = data.filingStatus
            )
        }
        
        /**
         * OAuth 2.0 authentication with IRS
         */
        internal suspend fun authenticateWithIRS(): String = coroutineScope {
            val credentials = Base64.encode("$clientId:$clientSecret".toByteArray())
            
            val params = mapOf(
                "grant_type" to "client_credentials",
                "scope" to "read write"
            )
            
            val headers = mapOf(
                "Authorization" to "Basic $credentials",
                "Content-Type" to "application/x-www-form-urlencoded"
            )
            
            val response = httpPostWithHeaders("$authUrl/oauth/token", params, headers)
            val data = Json.decodeFromString<IRSTokenResponse>(response)
            
            data.accessToken
        }
    }
    
    /**
     * USCIS API Client - US Citizenship and Immigration Services
     * https://developer.uscis.gov/apis (2025)
     */
    class USCISClient(internal val apiKey: String) {
        
        internal val baseUrl = "https://api.uscis.gov/v1"
        
        /**
         * Check case status for immigration cases
         */
        suspend fun getCaseStatus(receiptNumber: String): CaseStatus = coroutineScope {
            val params = mapOf(
                "receipt_number" to receiptNumber,
                "api_key" to apiKey
            )
            
            val response = httpGet("$baseUrl/cases/status", params)
            val data = Json.decodeFromString<USCISCaseResponse>(response)
            
            CaseStatus(
                receiptNumber = receiptNumber,
                caseType = data.caseType,
                currentStatus = data.currentStatus,
                lastUpdate = LocalDate.parse(data.lastUpdate),
                nextAction = data.nextAction,
                estimatedCompletion = data.estimatedCompletion?.let { LocalDate.parse(it) },
                priority = data.priority
            )
        }
        
        /**
         * Get processing times for different forms
         */
        suspend fun getProcessingTimes(
            formType: String,
            serviceCenter: String? = null
        ): Flow<ProcessingTime> = flow {
            val params = buildMap {
                put("form_type", formType)
                put("api_key", apiKey)
                serviceCenter?.let { put("service_center", it) }
            }
            
            val response = httpGet("$baseUrl/processing-times", params)
            val data = Json.decodeFromString<USCISProcessingResponse>(response)
            
            data.processingTimes.forEach { time ->
                emit(ProcessingTime(
                    formType = time.formType,
                    serviceCenter = time.serviceCenter,
                    rangeFrom = time.rangeFrom,
                    rangeTo = time.rangeTo,
                    unit = time.unit,
                    lastUpdated = LocalDate.parse(time.lastUpdated),
                    volume = time.volume
                ))
            }
        }
        
        /**
         * Get naturalization test locations
         */
        suspend fun getNaturalizationTestLocations(
            zipCode: String,
            radius: Int = 50
        ): Flow<TestLocation> = flow {
            val params = mapOf(
                "zip_code" to zipCode,
                "radius" to radius.toString(),
                "api_key" to apiKey
            )
            
            val response = httpGet("$baseUrl/naturalization/test-locations", params)
            val data = Json.decodeFromString<USCISLocationResponse>(response)
            
            data.locations.forEach { location ->
                emit(TestLocation(
                    facilityName = location.facilityName,
                    address = Address(
                        street = location.address.street,
                        city = location.address.city,
                        state = location.address.state,
                        zipCode = location.address.zipCode
                    ),
                    distance = location.distance,
                    availableSlots = location.availableSlots,
                    nextAvailableDate = location.nextAvailableDate?.let { LocalDate.parse(it) }
                ))
            }
        }
        
        /**
         * Get premium processing availability
         */
        suspend fun getPremiumProcessingAvailability(
            formType: String
        ): PremiumProcessingInfo = coroutineScope {
            val params = mapOf(
                "form_type" to formType,
                "api_key" to apiKey
            )
            
            val response = httpGet("$baseUrl/premium-processing", params)
            val data = Json.decodeFromString<USCISPremiumResponse>(response)
            
            PremiumProcessingInfo(
                formType = formType,
                isAvailable = data.isAvailable,
                fee = data.fee,
                processingDays = data.processingDays,
                eligibilityCriteria = data.eligibilityCriteria,
                lastUpdated = LocalDate.parse(data.lastUpdated)
            )
        }
        
        /**
         * Get civics test questions for naturalization preparation
         */
        suspend fun getCivicsTestQuestions(
            category: String? = null
        ): Flow<CivicsQuestion> = flow {
            val params = buildMap {
                put("api_key", apiKey)
                category?.let { put("category", it) }
            }
            
            val response = httpGet("$baseUrl/naturalization/civics-test", params)
            val data = Json.decodeFromString<USCISCivicsResponse>(response)
            
            data.questions.forEach { question ->
                emit(CivicsQuestion(
                    questionId = question.questionId,
                    category = question.category,
                    question = question.question,
                    acceptableAnswers = question.acceptableAnswers,
                    difficulty = question.difficulty,
                    historicalContext = question.historicalContext
                ))
            }
        }
        
        /**
         * Submit I-90 (Green Card Renewal) application
         */
        suspend fun submitI90Application(
            application: I90Application
        ): ApplicationSubmissionResult = coroutineScope {
            val payload = Json.encodeToString(application)
            
            val response = httpPost("$baseUrl/forms/i-90/submit", payload, apiKey)
            val data = Json.decodeFromString<USCISSubmissionResponse>(response)
            
            ApplicationSubmissionResult(
                receiptNumber = data.receiptNumber,
                submissionDate = LocalDate.parse(data.submissionDate),
                estimatedProcessingTime = data.estimatedProcessingTime,
                requiredDocuments = data.requiredDocuments,
                nextSteps = data.nextSteps,
                biometricsRequired = data.biometricsRequired
            )
        }
        
        /**
         * Get fee calculator for different forms
         */
        suspend fun calculateFees(
            formType: String,
            applicantAge: Int? = null,
            expediteRequested: Boolean = false
        ): FeeCalculation = coroutineScope {
            val params = buildMap {
                put("form_type", formType)
                put("api_key", apiKey)
                applicantAge?.let { put("applicant_age", it.toString()) }
                put("expedite_requested", expediteRequested.toString())
            }
            
            val response = httpGet("$baseUrl/fees/calculate", params)
            val data = Json.decodeFromString<USCISFeeResponse>(response)
            
            FeeCalculation(
                formType = formType,
                baseFee = data.baseFee,
                biometricsFee = data.biometricsFee,
                expediteFee = data.expediteFee,
                totalFee = data.totalFee,
                paymentMethods = data.paymentMethods,
                feeWaiverEligible = data.feeWaiverEligible
            )
        }
    }

    /**
     * Library of Congress API Client - Congress.gov API
     * https://github.com/LibraryOfCongress/api.congress.gov
     * https://www.congress.gov/help/using-data-offsite
     */
    class CongressAPIClient(internal val apiKey: String) {
        
        internal val baseUrl = "https://api.congress.gov/v3"
        
        /**
         * Get bills by congress and type
         */
        suspend fun getBills(
            congress: Int,
            billType: String? = null,
            limit: Int = 20,
            offset: Int = 0
        ): Flow<Bill> = flow {
            val params = buildMap {
                put("api_key", apiKey)
                put("format", "json")
                put("limit", limit.toString())
                put("offset", offset.toString())
                billType?.let { put("type", it) }
            }
            
            val response = httpGet("$baseUrl/bill/$congress", params)
            val data = Json.decodeFromString<CongressBillResponse>(response)
            
            data.bills.forEach { bill →
                emit(Bill(
                    billId = bill.number,
                    congress = congress,
                    billType = bill.type,
                    title = bill.title,
                    introducedDate = bill.introducedDate?.let { LocalDate.parse(it) },
                    latestAction = bill.latestAction?.actionText,
                    latestActionDate = bill.latestAction?.actionDate?.let { LocalDate.parse(it) },
                    sponsor = bill.sponsors.firstOrNull()?.let { 
                        CongressMember(
                            bioguideId = it.bioguideId,
                            firstName = it.firstName,
                            lastName = it.lastName,
                            party = it.party,
                            state = it.state
                        )
                    },
                    subjects = bill.subjects.map { it.name },
                    committeesReferred = bill.committees.map { it.name }
                ))
            }
        }
        
        /**
         * Get bill text and versions
         */
        suspend fun getBillText(
            congress: Int,
            billType: String,
            billNumber: String
        ): Flow<BillText> = flow {
            val params = mapOf(
                "api_key" to apiKey,
                "format" to "json"
            )
            
            val response = httpGet("$baseUrl/bill/$congress/$billType/$billNumber/text", params)
            val data = Json.decodeFromString<CongressBillTextResponse>(response)
            
            data.textVersions.forEach { version →
                emit(BillText(
                    billId = "$billType$billNumber",
                    congress = congress,
                    version = version.type,
                    date = LocalDate.parse(version.date),
                    textUrl = version.formats.firstOrNull { it.type == "pdf" }?.url,
                    xmlUrl = version.formats.firstOrNull { it.type == "xml" }?.url
                ))
            }
        }
        
        /**
         * Get members of Congress
         */
        suspend fun getMembers(
            congress: Int,
            chamber: String? = null // "house", "senate"
        ): Flow<CongressMember> = flow {
            val params = buildMap {
                put("api_key", apiKey)
                put("format", "json")
                chamber?.let { put("chamber", it) }
            }
            
            val response = httpGet("$baseUrl/member/$congress", params)
            val data = Json.decodeFromString<CongressMemberResponse>(response)
            
            data.members.forEach { member →
                emit(CongressMember(
                    bioguideId = member.bioguideId,
                    firstName = member.name.first,
                    lastName = member.name.last,
                    party = member.partyName,
                    state = member.state,
                    district = member.district,
                    chamber = member.chamber,
                    termStart = member.terms.lastOrNull()?.startYear,
                    termEnd = member.terms.lastOrNull()?.endYear
                ))
            }
        }
        
        /**
         * Get committee information
         */
        suspend fun getCommittees(
            congress: Int,
            chamber: String? = null
        ): Flow<Committee> = flow {
            val params = buildMap {
                put("api_key", apiKey)
                put("format", "json")
                chamber?.let { put("chamber", it) }
            }
            
            val response = httpGet("$baseUrl/committee/$congress", params)
            val data = Json.decodeFromString<CongressCommitteeResponse>(response)
            
            data.committees.forEach { committee →
                emit(Committee(
                    systemCode = committee.systemCode,
                    name = committee.name,
                    chamber = committee.chamber,
                    parent = committee.parent?.systemCode,
                    subcommittees = committee.subcommittees.map { 
                        Subcommittee(
                            systemCode = it.systemCode,
                            name = it.name
                        )
                    }
                ))
            }
        }
        
        /**
         * Get votes and roll calls
         */
        suspend fun getVotes(
            congress: Int,
            chamber: String,
            year: Int? = null
        ): Flow<Vote> = flow {
            val params = buildMap {
                put("api_key", apiKey)
                put("format", "json")
                year?.let { put("year", it.toString()) }
            }
            
            val response = httpGet("$baseUrl/vote/$congress/$chamber", params)
            val data = Json.decodeFromString<CongressVoteResponse>(response)
            
            data.votes.forEach { vote →
                emit(Vote(
                    congress = congress,
                    chamber = chamber,
                    rollCall = vote.rollCall,
                    session = vote.session,
                    date = LocalDate.parse(vote.date),
                    question = vote.question,
                    result = vote.result,
                    bill = vote.bill?.let { 
                        BillReference(
                            congress = it.congress,
                            type = it.type,
                            number = it.number
                        )
                    },
                    yesVotes = vote.yesCount,
                    noVotes = vote.noCount,
                    presentVotes = vote.presentCount,
                    notVotingCount = vote.notVotingCount
                ))
            }
        }
        
        /**
         * Get treaty information
         */
        suspend fun getTreaties(
            congress: Int,
            limit: Int = 20
        ): Flow<Treaty> = flow {
            val params = mapOf(
                "api_key" to apiKey,
                "format" to "json",
                "limit" to limit.toString()
            )
            
            val response = httpGet("$baseUrl/treaty/$congress", params)
            val data = Json.decodeFromString<CongressTreatyResponse>(response)
            
            data.treaties.forEach { treaty →
                emit(Treaty(
                    treatyId = treaty.number,
                    congress = congress,
                    suffix = treaty.suffix,
                    transmittedDate = treaty.transmittedDate?.let { LocalDate.parse(it) },
                    title = treaty.title,
                    parts = treaty.parts,
                    topics = treaty.topics.map { it.name },
                    countries = treaty.countries.map { it.name }
                ))
            }
        }
        
        /**
         * Get nomination information
         */
        suspend fun getNominations(
            congress: Int,
            limit: Int = 20
        ): Flow<Nomination> = flow {
            val params = mapOf(
                "api_key" to apiKey,
                "format" to "json", 
                "limit" to limit.toString()
            )
            
            val response = httpGet("$baseUrl/nomination/$congress", params)
            val data = Json.decodeFromString<CongressNominationResponse>(response)
            
            data.nominations.forEach { nomination →
                emit(Nomination(
                    nominationId = nomination.number,
                    congress = congress,
                    receivedDate = nomination.receivedDate?.let { LocalDate.parse(it) },
                    organization = nomination.organization,
                    nominees = nomination.nominees.map { nominee →
                        Nominee(
                            firstName = nominee.firstName,
                            lastName = nominee.lastName,
                            position = nominee.position,
                            state = nominee.state
                        )
                    },
                    latestAction = nomination.latestAction?.text
                ))
            }
        }
        
        /**
         * Search bills by keyword and criteria
         */
        suspend fun searchBills(
            query: String,
            congress: Int? = null,
            billType: String? = null,
            limit: Int = 20
        ): Flow<Bill> = flow {
            val params = buildMap {
                put("q", query)
                put("api_key", apiKey)
                put("format", "json")
                put("limit", limit.toString())
                congress?.let { put("congress", it.toString()) }
                billType?.let { put("type", it) }
            }
            
            val response = httpGet("$baseUrl/bill", params)
            val data = Json.decodeFromString<CongressBillResponse>(response)
            
            data.bills.forEach { bill →
                emit(Bill(
                    billId = bill.number,
                    congress = bill.congress,
                    billType = bill.type,
                    title = bill.title,
                    introducedDate = bill.introducedDate?.let { LocalDate.parse(it) },
                    latestAction = bill.latestAction?.actionText,
                    latestActionDate = bill.latestAction?.actionDate?.let { LocalDate.parse(it) },
                    sponsor = bill.sponsors.firstOrNull()?.let { 
                        CongressMember(
                            bioguideId = it.bioguideId,
                            firstName = it.firstName,
                            lastName = it.lastName,
                            party = it.party,
                            state = it.state
                        )
                    },
                    subjects = bill.subjects.map { it.name },
                    committeesReferred = bill.committees.map { it.name }
                ))
            }
        }
    }

    /**
     * USPS Web Tools API Client
     * https://www.usps.com/business/web-tools-apis/
     */
    class USPSClient(internal val userId: String, internal val password: String) {
        
        internal val baseUrl = "https://secure.shippingapis.com/ShippingAPI.dll"
        
        /**
         * Address validation and standardization
         */
        suspend fun validateAddress(address: USPSAddress): AddressValidationResult = coroutineScope {
            val xmlRequest = buildAddressValidationXML(address)
            val params = mapOf(
                "API" to "Verify",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseAddressValidationResponse(response)
        }
        
        /**
         * ZIP Code lookup by address
         */
        suspend fun lookupZipCode(address: USPSAddress): ZipCodeLookupResult = coroutineScope {
            val xmlRequest = buildZipCodeLookupXML(address)
            val params = mapOf(
                "API" to "ZipCodeLookup",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseZipCodeLookupResponse(response)
        }
        
        /**
         * City and state lookup by ZIP code
         */
        suspend fun lookupCityState(zipCode: String): CityStateLookupResult = coroutineScope {
            val xmlRequest = buildCityStateLookupXML(zipCode)
            val params = mapOf(
                "API" to "CityStateLookup",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseCityStateLookupResponse(response)
        }
        
        /**
         * Calculate postage rates
         */
        suspend fun calculatePostage(
            service: USPSService,
            zipOrigin: String,
            zipDestination: String,
            pounds: Int,
            ounces: Int,
            container: String = "",
            size: String = "REGULAR",
            machinable: Boolean = true
        ): PostageCalculationResult = coroutineScope {
            val xmlRequest = buildPostageCalculationXML(
                service, zipOrigin, zipDestination, pounds, ounces, container, size, machinable
            )
            val params = mapOf(
                "API" to "RateV4",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parsePostageCalculationResponse(response)
        }
        
        /**
         * Track package by tracking number
         */
        suspend fun trackPackage(trackingNumber: String): TrackingResult = coroutineScope {
            val xmlRequest = buildTrackingXML(trackingNumber)
            val params = mapOf(
                "API" to "TrackV2",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseTrackingResponse(response)
        }
        
        /**
         * Get Priority Mail Express label
         */
        suspend fun createExpressLabel(
            labelRequest: ExpressLabelRequest
        ): ExpressLabelResult = coroutineScope {
            val xmlRequest = buildExpressLabelXML(labelRequest)
            val params = mapOf(
                "API" to "ExpressMailLabel",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseExpressLabelResponse(response)
        }
        
        /**
         * Get service standards (delivery times)
         */
        suspend fun getServiceStandards(
            zipOrigin: String,
            zipDestination: String
        ): ServiceStandardsResult = coroutineScope {
            val xmlRequest = buildServiceStandardsXML(zipOrigin, zipDestination)
            val params = mapOf(
                "API" to "ServiceStandards",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseServiceStandardsResponse(response)
        }
        
        /**
         * Confirm priority mail service availability
         */
        suspend fun confirmPriorityMail(
            zipOrigin: String,
            zipDestination: String
        ): PriorityMailConfirmationResult = coroutineScope {
            val xmlRequest = buildPriorityMailConfirmationXML(zipOrigin, zipDestination)
            val params = mapOf(
                "API" to "PriorityMail",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parsePriorityMailConfirmationResponse(response)
        }
        
        /**
         * Get carrier route information
         */
        suspend fun getCarrierRoute(
            zipCode: String,
            address: String
        ): CarrierRouteResult = coroutineScope {
            val xmlRequest = buildCarrierRouteXML(zipCode, address)
            val params = mapOf(
                "API" to "CarrierRoute",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseCarrierRouteResponse(response)
        }
        
        /**
         * International rate calculator
         */
        suspend fun calculateInternationalPostage(
            country: String,
            pounds: Int,
            ounces: Int,
            mailType: String,
            valueOfContents: Double = 0.0
        ): InternationalPostageResult = coroutineScope {
            val xmlRequest = buildInternationalRateXML(
                country, pounds, ounces, mailType, valueOfContents
            )
            val params = mapOf(
                "API" to "IntlRateV2",
                "XML" to xmlRequest
            )
            
            val response = httpGet(baseUrl, params)
            parseInternationalPostageResponse(response)
        }
        
        // XML builders and parsers (simplified implementations)
        internal fun buildAddressValidationXML(address: USPSAddress): String = """
            <AddressValidateRequest USERID="$userId">
                <Revision>1</Revision>
                <Address ID="0">
                    <Address1>${address.address1 ?: ""}</Address1>
                    <Address2>${address.address2}</Address2>
                    <City>${address.city}</City>
                    <State>${address.state}</State>
                    <Zip5>${address.zip5}</Zip5>
                    <Zip4>${address.zip4 ?: ""}</Zip4>
                </Address>
            </AddressValidateRequest>
        """.trimIndent()
        
        internal fun buildZipCodeLookupXML(address: USPSAddress): String = """
            <ZipCodeLookupRequest USERID="$userId">
                <Address ID="0">
                    <Address1>${address.address1 ?: ""}</Address1>
                    <Address2>${address.address2}</Address2>
                    <City>${address.city}</City>
                    <State>${address.state}</State>
                </Address>
            </ZipCodeLookupRequest>
        """.trimIndent()
        
        internal fun buildCityStateLookupXML(zipCode: String): String = """
            <CityStateLookupRequest USERID="$userId">
                <ZipCode ID="0">
                    <Zip5>$zipCode</Zip5>
                </ZipCode>
            </CityStateLookupRequest>
        """.trimIndent()
        
        internal fun buildPostageCalculationXML(
            service: USPSService,
            zipOrigin: String,
            zipDestination: String,
            pounds: Int,
            ounces: Int,
            container: String,
            size: String,
            machinable: Boolean
        ): String = """
            <RateV4Request USERID="$userId">
                <Revision>2</Revision>
                <Package ID="0">
                    <Service>${service.code}</Service>
                    <ZipOrigination>$zipOrigin</ZipOrigination>
                    <ZipDestination>$zipDestination</ZipDestination>
                    <Pounds>$pounds</Pounds>
                    <Ounces>$ounces</Ounces>
                    <Container>$container</Container>
                    <Size>$size</Size>
                    <Machinable>${if (machinable) "TRUE" else "FALSE"}</Machinable>
                </Package>
            </RateV4Request>
        """.trimIndent()
        
        internal fun buildTrackingXML(trackingNumber: String): String = """
            <TrackRequest USERID="$userId">
                <TrackID ID="$trackingNumber"></TrackID>
            </TrackRequest>
        """.trimIndent()
        
        internal fun buildExpressLabelXML(request: ExpressLabelRequest): String = """
            <ExpressMailLabelRequest USERID="$userId">
                <Option>${request.option}</Option>
                <Revision>2</Revision>
                <EMCAAccount>${request.emcaAccount}</EMCAAccount>
                <EMCAPassword>${request.emcaPassword}</EMCAPassword>
                <ImageParameters>
                    <ImageParameter>4X6LABEL</ImageParameter>
                </ImageParameters>
                <FromName>${request.fromName}</FromName>
                <FromFirm>${request.fromFirm}</FromFirm>
                <FromAddress1>${request.fromAddress1}</FromAddress1>
                <FromCity>${request.fromCity}</FromCity>
                <FromState>${request.fromState}</FromState>
                <FromZip5>${request.fromZip5}</FromZip5>
                <ToName>${request.toName}</ToName>
                <ToFirm>${request.toFirm}</ToFirm>
                <ToAddress1>${request.toAddress1}</ToAddress1>
                <ToCity>${request.toCity}</ToCity>
                <ToState>${request.toState}</ToState>
                <ToZip5>${request.toZip5}</ToZip5>
                <WeightInOunces>${request.weightInOunces}</WeightInOunces>
                <FlatRate>${if (request.flatRate) "TRUE" else "FALSE"}</FlatRate>
                <SundayHolidayDelivery>${if (request.sundayHolidayDelivery) "TRUE" else "FALSE"}</SundayHolidayDelivery>
                <StandardizeAddress>${if (request.standardizeAddress) "TRUE" else "FALSE"}</StandardizeAddress>
                <WaiverOfSignature>${if (request.waiverOfSignature) "TRUE" else "FALSE"}</WaiverOfSignature>
                <NoWeekend>${if (request.noWeekend) "TRUE" else "FALSE"}</NoWeekend>
                <SeparateReceiptPage>${if (request.separateReceiptPage) "TRUE" else "FALSE"}</SeparateReceiptPage>
                <POZipCode>${request.poZipCode}</POZipCode>
                <FacilityType>DDU</FacilityType>
                <ImageType>PDF</ImageType>
                <CustomerRefNo>${request.customerRefNo}</CustomerRefNo>
                <SenderName>${request.senderName}</SenderName>
                <SenderEMail>${request.senderEmail}</SenderEMail>
                <RecipientName>${request.recipientName}</RecipientName>
                <RecipientEMail>${request.recipientEmail}</RecipientEMail>
            </ExpressMailLabelRequest>
        """.trimIndent()
        
        // Simplified response parsers (would use XML parsing in real implementation)
        internal fun parseAddressValidationResponse(response: String): AddressValidationResult {
            return AddressValidationResult(
                isValid = !response.contains("Error"),
                standardizedAddress = null, // Would parse from XML
                errorMessage = if (response.contains("Error")) "Address validation failed" else null
            )
        }
        
        internal fun parseZipCodeLookupResponse(response: String): ZipCodeLookupResult {
            return ZipCodeLookupResult(
                zipCode = "", // Would parse from XML
                zip4 = null,
                errorMessage = if (response.contains("Error")) "ZIP lookup failed" else null
            )
        }
        
        internal fun parseCityStateLookupResponse(response: String): CityStateLookupResult {
            return CityStateLookupResult(
                city = "", // Would parse from XML
                state = "",
                errorMessage = if (response.contains("Error")) "City/State lookup failed" else null
            )
        }
        
        internal fun parsePostageCalculationResponse(response: String): PostageCalculationResult {
            return PostageCalculationResult(
                rate = 0.0, // Would parse from XML
                zone = null,
                errorMessage = if (response.contains("Error")) "Rate calculation failed" else null
            )
        }
        
        internal fun parseTrackingResponse(response: String): TrackingResult {
            return TrackingResult(
                trackingNumber = "",
                status = "",
                events = emptyList(),
                errorMessage = if (response.contains("Error")) "Tracking failed" else null
            )
        }
        
        internal fun parseExpressLabelResponse(response: String): ExpressLabelResult {
            return ExpressLabelResult(
                labelUrl = "", // Would parse from XML
                trackingNumber = "",
                postage = 0.0,
                errorMessage = if (response.contains("Error")) "Label creation failed" else null
            )
        }
        
        internal fun parseServiceStandardsResponse(response: String): ServiceStandardsResult {
            return ServiceStandardsResult(
                days = 0, // Would parse from XML
                effectiveAcceptanceDate = "",
                cutOffTime = "",
                errorMessage = if (response.contains("Error")) "Service standards lookup failed" else null
            )
        }
        
        internal fun parsePriorityMailConfirmationResponse(response: String): PriorityMailConfirmationResult {
            return PriorityMailConfirmationResult(
                isAvailable = !response.contains("Error"),
                days = 0,
                errorMessage = if (response.contains("Error")) "Priority Mail confirmation failed" else null
            )
        }
        
        internal fun parseCarrierRouteResponse(response: String): CarrierRouteResult {
            return CarrierRouteResult(
                carrierRoute = "", // Would parse from XML
                zipCode = "",
                errorMessage = if (response.contains("Error")) "Carrier route lookup failed" else null
            )
        }
        
        internal fun parseInternationalPostageResponse(response: String): InternationalPostageResult {
            return InternationalPostageResult(
                rates = emptyList(), // Would parse from XML
                errorMessage = if (response.contains("Error")) "International rate calculation failed" else null
            )
        }
        
        // Helper methods for other XML builders
        internal fun buildServiceStandardsXML(zipOrigin: String, zipDestination: String): String = ""
        internal fun buildPriorityMailConfirmationXML(zipOrigin: String, zipDestination: String): String = ""
        internal fun buildCarrierRouteXML(zipCode: String, address: String): String = ""
        internal fun buildInternationalRateXML(
            country: String,
            pounds: Int,
            ounces: Int,
            mailType: String,
            valueOfContents: Double
        ): String = ""
    }

    /**
     * FRB Services API Client (FedNow, FedWire, FedACH)
     * https://www.frbservices.org/news/fed360/issues/111524/education-resources-frfs-apis-transform
     */
    class FRBServicesClient(internal val credentials: FRBCredentials) {
        
        internal val baseUrl = "https://api.frbservices.org"
        
        /**
         * Get FedWire operational statistics
         */
        suspend fun getFedWireStats(
            date: LocalDate
        ): Flow<FedWireStat> = flow {
            val token = authenticateWithFRB()
            val params = mapOf("date" to date.toString())
            
            val response = httpGetSecure("$baseUrl/fedwire/stats", params, token)
            val data = Json.decodeFromString<FRBStatsResponse>(response)
            
            data.statistics.forEach { stat →
                emit(FedWireStat(
                    date = LocalDate.parse(stat.date),
                    totalValue = stat.totalValue,
                    totalVolume = stat.totalVolume,
                    averageValue = stat.averageValue,
                    peakHourVolume = stat.peakHourVolume
                ))
            }
        }
        
        /**
         * Get ACH operational data
         */
        suspend fun getACHOperationalData(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<ACHOperationalData> = flow {
            val token = authenticateWithFRB()
            val params = mapOf(
                "start_date" to startDate.toString(),
                "end_date" to endDate.toString()
            )
            
            val response = httpGetSecure("$baseUrl/ach/operational", params, token)
            val data = Json.decodeFromString<FRBACHResponse>(response)
            
            data.operationalData.forEach { op →
                emit(ACHOperationalData(
                    date = LocalDate.parse(op.date),
                    creditVolume = op.creditVolume,
                    debitVolume = op.debitVolume,
                    creditValue = op.creditValue,
                    debitValue = op.debitValue,
                    returnRate = op.returnRate
                ))
            }
        }
        
        /**
         * Get FedNow service status
         */
        suspend fun getFedNowStatus(): FedNowStatus = coroutineScope {
            val token = authenticateWithFRB()
            val response = httpGetSecure("$baseUrl/fednow/status", emptyMap(), token)
            val data = Json.decodeFromString<FRBFedNowResponse>(response)
            
            FedNowStatus(
                serviceAvailable = data.status.serviceAvailable,
                lastUpdate = Instant.parse(data.status.lastUpdate),
                participantCount = data.status.participantCount,
                dailyVolume = data.status.dailyVolume,
                averageSettlementTime = data.status.averageSettlementTime
            )
        }
        
        internal suspend fun authenticateWithFRB(): String {
            // OAuth 2.0 with client certificate authentication
            // Implementation would handle PKI certificate authentication
            return "bearer_token_placeholder"
        }
    }
    
    /**
     * Integrated fiduciary data aggregator
     */
    class FiduciaryDataAggregator(
        internal val fredClient: FREDClient,
        internal val nyFedClient: NYFedMarketsClient,
        internal val treasuryClient: TreasuryFiscalClient,
        internal val frbClient: FRBServicesClient?
    ) {
        
        /**
         * Get comprehensive fiduciary market snapshot
         */
        suspend fun getFiduciaryMarketSnapshot(): FiduciaryMarketSnapshot = coroutineScope {
            
            // Get interest rate environment
            val ratesDeferred = async {
                fredClient.getFiduciaryIndicators()
                    .filter { it.category == IndicatorCategory.INTEREST_RATES }
                    .toList()
            }
            
            // Get treasury operations
            val treasuryDeferred = async {
                treasuryClient.getTreasuryRates().take(10).toList()
            }
            
            // Get Fed operations
            val repoDeferred = async {
                val yesterday = Clock.System.now().minus(1.days).toLocalDateTime(TimeZone.UTC).date
                nyFedClient.getRepoOperations(yesterday, yesterday).toList()
            }
            
            // Get payment system status
            val paymentsDeferred = frbClient?.let { client →
                async {
                    val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                    client.getFedWireStats(today).take(1).toList()
                }
            }
            
            FiduciaryMarketSnapshot(
                timestamp = Clock.System.now(),
                interestRates = ratesDeferred.await(),
                treasuryRates = treasuryDeferred.await(),
                repoOperations = repoDeferred.await(),
                paymentSystemStats = paymentsDeferred?.await() ?: emptyList(),
                marketConditions = assessMarketConditions(ratesDeferred.await())
            )
        }
        
        /**
         * Monitor fiduciary-relevant economic changes
         */
        fun monitorFiduciaryIndicators(): Flow<FiduciaryAlert> = flow {
            while (true) {
                val snapshot = getFiduciaryMarketSnapshot()
                val alerts = analyzeForAlerts(snapshot)
                alerts.forEach { emit(it) }
                delay(3600000) // Check hourly
            }
        }
        
        internal fun assessMarketConditions(indicators: List<FiduciaryIndicator>): MarketConditions {
            val fedFundsRate = indicators.find { it.seriesId == "FEDFUNDS" }?.currentValue ?: 0.0
            val tenYearRate = indicators.find { it.seriesId == "GS10" }?.currentValue ?: 0.0
            val unemployment = indicators.find { it.seriesId == "UNRATE" }?.currentValue ?: 0.0
            
            return MarketConditions(
                yieldCurveSlope = tenYearRate - fedFundsRate,
                rateEnvironment = when {
                    fedFundsRate < 2.0 → RateEnvironment.LOW
                    fedFundsRate > 5.0 → RateEnvironment.HIGH
                    else → RateEnvironment.MODERATE
                },
                economicIndicator = when {
                    unemployment < 4.0 → EconomicIndicator.STRONG
                    unemployment > 7.0 → EconomicIndicator.WEAK
                    else → EconomicIndicator.MODERATE
                }
            )
        }
        
        internal fun analyzeForAlerts(snapshot: FiduciaryMarketSnapshot): List<FiduciaryAlert> {
            val alerts = mutableListOf<FiduciaryAlert>()
            
            // Alert on significant rate changes
            if (kotlin.math.abs(snapshot.marketConditions.yieldCurveSlope) < 0.5) {
                alerts.add(FiduciaryAlert(
                    type = AlertType.YIELD_CURVE_INVERSION,
                    severity = AlertSeverity.HIGH,
                    message = "Yield curve is flattening/inverting",
                    timestamp = snapshot.timestamp
                ))
            }
            
            return alerts
        }
    }
    
    // Data models
    data class EconomicDataPoint(
        val seriesId: String,
        val date: LocalDate,
        val value: Double,
        val timestamp: Instant
    )
    
    data class FiduciaryIndicator(
        val indicator: String,
        val seriesId: String,
        val currentValue: Double,
        val date: LocalDate,
        val category: IndicatorCategory,
        val fiduciaryRelevance: FiduciaryRelevance
    )
    
    enum class IndicatorCategory {
        INTEREST_RATES, MONETARY_POLICY, INFLATION, EMPLOYMENT, GDP, CURRENCY
    }
    
    enum class FiduciaryRelevance {
        HIGH, MEDIUM, LOW
    }
    
    data class SeriesInfo(
        val id: String,
        val title: String,
        val units: String,
        val frequency: String,
        val lastUpdated: LocalDate
    )
    
    data class RepoOperation(
        val operationDate: LocalDate,
        val operationType: String,
        val maturityDate: LocalDate,
        val amountAccepted: Double,
        val rate: Double?,
        val proposedAmount: Double
    )
    
    data class SOMAHolding(
        val asOfDate: LocalDate,
        val securityType: String,
        val parValue: Double,
        val percentOfPortfolio: Double
    )
    
    data class PrimaryDealerStat(
        val reportDate: LocalDate,
        val dealerName: String,
        val netPosition: Double,
        val grossPosition: Double,
        val securityType: String
    )
    
    data class TreasuryStatement(
        val recordDate: LocalDate,
        val openingBalance: Double?,
        val receipts: Double?,
        val outlays: Double?,
        val closingBalance: Double?,
        val accountType: String
    )
    
    data class DebtRecord(
        val recordDate: LocalDate,
        val totalDebt: Double,
        val intragovernmentalHoldings: Double,
        val debtHeldByPublic: Double
    )
    
    data class TreasuryRate(
        val recordDate: LocalDate,
        val securityType: String,
        val avgInterestRate: Double?,
        val securityDesc: String
    )
    
    data class FedWireStat(
        val date: LocalDate,
        val totalValue: Double,
        val totalVolume: Long,
        val averageValue: Double,
        val peakHourVolume: Long
    )
    
    data class ACHOperationalData(
        val date: LocalDate,
        val creditVolume: Long,
        val debitVolume: Long,
        val creditValue: Double,
        val debitValue: Double,
        val returnRate: Double
    )
    
    data class FedNowStatus(
        val serviceAvailable: Boolean,
        val lastUpdate: Instant,
        val participantCount: Int,
        val dailyVolume: Long,
        val averageSettlementTime: Double
    )
    
    data class FiduciaryMarketSnapshot(
        val timestamp: Instant,
        val interestRates: List<FiduciaryIndicator>,
        val treasuryRates: List<TreasuryRate>,
        val repoOperations: List<RepoOperation>,
        val paymentSystemStats: List<FedWireStat>,
        val marketConditions: MarketConditions
    )
    
    data class MarketConditions(
        val yieldCurveSlope: Double,
        val rateEnvironment: RateEnvironment,
        val economicIndicator: EconomicIndicator
    )
    
    enum class RateEnvironment {
        LOW, MODERATE, HIGH, VOLATILE
    }
    
    enum class EconomicIndicator {
        STRONG, MODERATE, WEAK, UNCERTAIN
    }
    
    data class FiduciaryAlert(
        val type: AlertType,
        val severity: AlertSeverity,
        val message: String,
        val timestamp: Instant
    )
    
    enum class AlertType {
        RATE_SPIKE, YIELD_CURVE_INVERSION, PAYMENT_SYSTEM_ISSUE, ECONOMIC_SHIFT
    }
    
    enum class AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    // JSON response models (simplified)
    @Serializable
    data class FREDResponse(
        val observations: List<FREDObservation>
    )
    
    @Serializable
    data class FREDObservation(
        val date: String,
        val value: String
    )
    
    @Serializable
    data class FREDSeriesResponse(
        val series: List<FREDSeries>
    )
    
    @Serializable
    data class FREDSeries(
        val id: String,
        val title: String,
        val units: String,
        val frequency: String,
        @SerialName("last_updated") val lastUpdated: String
    )
    
    // Simplified response models for other APIs
    @Serializable
    data class NYFedRepoResponse(val repo: List<Map<String, String>>)
    
    @Serializable
    data class NYFedSomaResponse(val soma: Map<String, List<Map<String, String>>>)
    
    @Serializable
    data class NYFedPDResponse(val pd: List<Map<String, String>>)
    
    @Serializable
    data class TreasuryDTSResponse(val data: List<Map<String, String?>>)
    
    @Serializable
    data class TreasuryDebtResponse(val data: List<Map<String, String>>)
    
    @Serializable
    data class TreasuryRatesResponse(val data: List<Map<String, String?>>)
    
    @Serializable
    data class FRBStatsResponse(val statistics: List<Map<String, String>>)
    
    @Serializable
    data class FRBACHResponse(val operationalData: List<Map<String, String>>)
    
    @Serializable
    data class FRBFedNowResponse(val status: Map<String, Any>)
    
    // Utility functions
    internal fun categorizeIndicator(seriesId: String): IndicatorCategory = when {
        seriesId.contains("RATE") || seriesId.startsWith("GS") || seriesId.startsWith("TB") → IndicatorCategory.INTEREST_RATES
        seriesId == "FEDFUNDS" || seriesId == "DFF" → IndicatorCategory.MONETARY_POLICY
        seriesId.contains("CPI") → IndicatorCategory.INFLATION
        seriesId == "UNRATE" → IndicatorCategory.EMPLOYMENT
        seriesId == "GDP" → IndicatorCategory.GDP
        seriesId.startsWith("DEX") → IndicatorCategory.CURRENCY
        else → IndicatorCategory.INTEREST_RATES
    }
    
    internal fun assessFiduciaryRelevance(seriesId: String): FiduciaryRelevance = when (seriesId) {
        "FEDFUNDS", "GS10", "GS2", "MORTGAGE30US" → FiduciaryRelevance.HIGH
        "TB3MS", "GS5", "UNRATE", "CPIAUCSL" → FiduciaryRelevance.MEDIUM
        else → FiduciaryRelevance.LOW
    }
    
    // Mock HTTP functions (would be implemented with actual HTTP client)
    internal suspend fun httpGet(url: String, params: Map<String, String>): String {
        // Implementation would use ktor-client or similar
        return "{\"observations\":[]}"
    }
    
    internal suspend fun httpGetSecure(url: String, params: Map<String, String>, token: String): String {
        // Implementation would use client certificate authentication
        return "{\"statistics\":[]}"
    }
}