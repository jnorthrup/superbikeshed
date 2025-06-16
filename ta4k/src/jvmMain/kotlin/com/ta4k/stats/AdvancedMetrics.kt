package com.ta4k.stats

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j
import borg.trikeshed.lib.`▶`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow
import kotlin.math.sqrt
import java.math.MathContext

/**
 * Advanced metrics implementation ported from quantstats
 */
class AdvancedMetrics {
    companion object {
        private const val DEFAULT_SCALE = 8
    }

    /**
     * Calculate the Treynor ratio
     */
    fun treynorRatio(
        returns: Series<BigDecimal>,
        benchmark: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val excessReturns = returns.`▶`.zip(benchmark.`▶`) { ret, bench ->
            ret.subtract(bench)
        }.toList()
        
        val beta = calculateBeta(returns.`▶`.toList(), benchmark.`▶`.toList())
        if (beta == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val meanExcessReturn = excessReturns.average()
        return meanExcessReturn.multiply(BigDecimal(periods))
            .divide(beta, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Omega ratio
     */
    fun omega(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        requiredReturn: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val threshold = requiredReturn.add(rf)
        val gains = returns.`▶`.filter { it > threshold }
            .map { it.subtract(threshold) }
            .toList()
        val losses = returns.`▶`.filter { it < threshold }
            .map { threshold.subtract(it) }
            .toList()
            
        if (losses.isEmpty()) return BigDecimal.ONE
        
        val expectedGain = gains.average()
        val expectedLoss = losses.average()
        
        return if (expectedLoss == BigDecimal.ZERO) BigDecimal.ONE
        else expectedGain.divide(expectedLoss, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Calmar ratio
     */
    fun calmar(returns: Series<BigDecimal>, periods: Int = 252): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val cagr = StatsUtils.cagr(returns, periods = periods)
        val maxDrawdown = PortfolioMetrics().maxDrawdown(returns)
        
        return if (maxDrawdown == BigDecimal.ZERO) BigDecimal.ZERO
        else cagr.divide(maxDrawdown, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Ulcer Index
     */
    fun ulcerIndex(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        var peak = BigDecimal.ONE
        var sumSquaredDrawdowns = BigDecimal.ZERO
        
        returns.`▶`.fold(BigDecimal.ONE) { acc, ret ->
            val current = acc.multiply(BigDecimal.ONE.add(ret))
            peak = peak.max(current)
            val drawdown = peak.subtract(current).divide(peak, DEFAULT_SCALE, RoundingMode.HALF_UP)
            sumSquaredDrawdowns = sumSquaredDrawdowns.add(drawdown.pow(2))
            current
        }
        
        return BigDecimal(sqrt(sumSquaredDrawdowns.divide(BigDecimal(returns.size), DEFAULT_SCALE, RoundingMode.HALF_UP).toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Ulcer Performance Index
     */
    fun ulcerPerformanceIndex(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val cagr = StatsUtils.cagr(returns, rf, periods)
        val ulcerIndex = ulcerIndex(returns)
        
        return if (ulcerIndex == BigDecimal.ZERO) BigDecimal.ZERO
        else cagr.divide(ulcerIndex, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Serenity Index
     */
    fun serenityIndex(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val cagr = StatsUtils.cagr(returns, rf, periods)
        val ulcerIndex = ulcerIndex(returns)
        val skew = StatisticalMetrics().skew(returns)
        
        return if (ulcerIndex == BigDecimal.ZERO) BigDecimal.ZERO
        else cagr.multiply(BigDecimal.ONE.add(skew))
            .divide(ulcerIndex, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Recovery Factor
     */
    fun recoveryFactor(
        returns: Series<BigDecimal>,
        rf: BigDecimal = BigDecimal.ZERO,
        periods: Int = 252
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val totalReturn = returns.`▶`.fold(BigDecimal.ONE) { acc, ret ->
            acc.multiply(BigDecimal.ONE.add(ret))
        }.subtract(BigDecimal.ONE)
        
        val maxDrawdown = PortfolioMetrics().maxDrawdown(returns)
        
        return if (maxDrawdown == BigDecimal.ZERO) BigDecimal.ZERO
        else totalReturn.divide(maxDrawdown, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Risk-Return Ratio
     */
    fun riskReturnRatio(returns: Series<BigDecimal>, periods: Int = 252): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val cagr = StatsUtils.cagr(returns, periods = periods)
        val downsideStd = calculateDownsideStdDev(returns.`▶`.toList())
        
        return if (downsideStd == BigDecimal.ZERO) BigDecimal.ZERO
        else cagr.divide(downsideStd, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Gain to Pain Ratio (GPR)
     */
    fun gainToPainRatio(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val totalReturn = returns.`▶`.sumOf { it }
        val downside = returns.`▶`.filter { it < BigDecimal.ZERO }
            .sumOf { it.abs() }
            
        return if (downside == BigDecimal.ZERO) BigDecimal.ZERO
        else totalReturn.divide(downside, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Risk of Ruin
     */
    fun riskOfRuin(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val winRate = PortfolioMetrics().winRate(returns)
        val avgWin = PortfolioMetrics().avgWin(returns)
        val avgLoss = PortfolioMetrics().avgLoss(returns)
        
        if (avgLoss == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val riskPerTrade = avgLoss.abs().divide(avgWin, DEFAULT_SCALE, RoundingMode.HALF_UP)
        val probabilityOfRuin = BigDecimal.ONE.subtract(winRate)
            .pow(riskPerTrade.toInt())
            
        return probabilityOfRuin.setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate Value at Risk (VaR)
     */
    fun valueAtRisk(
        returns: Series<BigDecimal>,
        sigma: Int = 1,
        confidence: BigDecimal = BigDecimal("0.95")
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val mean = returns.`▶`.average()
        val std = calculateStdDev(returns.`▶`.toList())
        
        return mean.subtract(std.multiply(BigDecimal(sigma)))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate Conditional Value at Risk (CVaR)
     */
    fun conditionalValueAtRisk(
        returns: Series<BigDecimal>,
        sigma: Int = 1,
        confidence: BigDecimal = BigDecimal("0.95")
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val var = valueAtRisk(returns, sigma, confidence)
        val tailReturns = returns.`▶`.filter { it <= var }
        
        return if (tailReturns.isEmpty()) BigDecimal.ZERO
        else tailReturns.average().setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Tail Ratio
     */
    fun tailRatio(
        returns: Series<BigDecimal>,
        cutoff: BigDecimal = BigDecimal("0.95")
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val sortedReturns = returns.`▶`.sorted()
        val rightTail = sortedReturns.last()
        val leftTail = sortedReturns.first()
        
        return if (leftTail == BigDecimal.ZERO) BigDecimal.ZERO
        else rightTail.divide(leftTail.abs(), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Payoff Ratio
     */
    fun payoffRatio(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins = returns.`▶`.filter { it > BigDecimal.ZERO }
        val losses = returns.`▶`.filter { it < BigDecimal.ZERO }
        
        if (wins.isEmpty() || losses.isEmpty()) return BigDecimal.ZERO
        
        val avgWin = wins.average()
        val avgLoss = losses.average()
        
        return if (avgLoss == BigDecimal.ZERO) BigDecimal.ZERO
        else avgWin.divide(avgLoss.abs(), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Common Sense Ratio
     */
    fun commonSenseRatio(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val winRate = PortfolioMetrics().winRate(returns)
        val profitFactor = PortfolioMetrics().profitFactor(returns)
        
        return winRate.multiply(profitFactor)
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Outlier Win Ratio
     */
    fun outlierWinRatio(
        returns: Series<BigDecimal>,
        quantile: BigDecimal = BigDecimal("0.99")
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins: List<BigDecimal> = returns.`▶`.filter { it > BigDecimal.ZERO }
        if (wins.isEmpty()) return BigDecimal.ZERO
        
        val threshold: BigDecimal = wins.sorted()[((wins.size * quantile.toDouble()).toInt())]
        val outlierWins: List<BigDecimal> = wins.filter { it >= threshold }
        
        return if (wins.isEmpty()) BigDecimal.ZERO
        else BigDecimal(outlierWins.size).divide(BigDecimal(wins.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Outlier Loss Ratio
     */
    fun outlierLossRatio(
        returns: Series<BigDecimal>,
        quantile: BigDecimal = BigDecimal("0.01")
    ): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val losses: List<BigDecimal> = returns.`▶`.filter { it < BigDecimal.ZERO }
        if (losses.isEmpty()) return BigDecimal.ZERO
        
        val threshold: BigDecimal = losses.sorted()[((losses.size * quantile.toDouble()).toInt())]
        val outlierLosses: List<BigDecimal> = losses.filter { it <= threshold }
        
        return if (losses.isEmpty()) BigDecimal.ZERO
        else BigDecimal(outlierLosses.size).divide(BigDecimal(losses.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the CPC Index
     */
    fun cpcIndex(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val profitFactor = PortfolioMetrics().profitFactor(returns)
        val winRate = PortfolioMetrics().winRate(returns)
        val winLossRatio = payoffRatio(returns)
        
        return profitFactor.multiply(winRate).multiply(winLossRatio)
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Profit Ratio
     */
    fun profitRatio(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val wins = returns.`▶`.filter { it >= BigDecimal.ZERO }
        val losses = returns.`▶`.filter { it < BigDecimal.ZERO }
        
        if (wins.isEmpty() || losses.isEmpty()) return BigDecimal.ZERO
        
        val winRatio = wins.average().abs()
            .divide(BigDecimal(wins.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val lossRatio = losses.average().abs()
            .divide(BigDecimal(losses.size), DEFAULT_SCALE, RoundingMode.HALF_UP)
            
        return if (lossRatio == BigDecimal.ZERO) BigDecimal.ZERO
        else winRatio.divide(lossRatio, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Kelly Criterion
     */
    fun kellyCriterion(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val winRate = PortfolioMetrics().winRate(returns)
        val avgWin = PortfolioMetrics().avgWin(returns)
        val avgLoss = PortfolioMetrics().avgLoss(returns)
        
        if (avgLoss == BigDecimal.ZERO) return BigDecimal.ZERO
        
        val winLossRatio = avgWin.divide(avgLoss.abs(), DEFAULT_SCALE, RoundingMode.HALF_UP)
        val kelly = winRate.multiply(winLossRatio)
            .subtract(BigDecimal.ONE.subtract(winRate))
            .divide(winLossRatio, DEFAULT_SCALE, RoundingMode.HALF_UP)
            
        return kelly.max(BigDecimal.ZERO).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the R-squared value
     */
    fun rSquared(returns: Series<BigDecimal>, benchmark: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val returnsMean = returns.`▶`.average()
        val benchmarkMean = benchmark.`▶`.average()
        
        var ssTotal = BigDecimal.ZERO
        var ssResidual = BigDecimal.ZERO
        
        val returnsList = returns.`▶`.toList()
        val benchmarkList = benchmark.`▶`.toList()
        
        for (i in returnsList.indices) {
            val returnsDiff = returnsList[i].subtract(returnsMean)
            val benchmarkDiff = benchmarkList[i].subtract(benchmarkMean)
            ssTotal = ssTotal.add(benchmarkDiff.pow(2))
            ssResidual = ssResidual.add(returnsDiff.subtract(benchmarkDiff).pow(2))
        }
        
        return if (ssTotal == BigDecimal.ZERO) BigDecimal.ZERO
        else BigDecimal.ONE.subtract(ssResidual.divide(ssTotal, DEFAULT_SCALE, RoundingMode.HALF_UP))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Information Ratio
     */
    fun informationRatio(returns: Series<BigDecimal>, benchmark: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val excessReturns = returns.`▶`.zip(benchmark.`▶`) { ret, bench ->
            ret.subtract(bench)
        }.toList()
        
        val meanExcessReturn = excessReturns.average()
        val trackingError = calculateStdDev(excessReturns)
        
        return if (trackingError == BigDecimal.ZERO) BigDecimal.ZERO
        else meanExcessReturn.divide(trackingError, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the Greeks (beta, alpha)
     */
    fun greeks(
        returns: Series<BigDecimal>,
        benchmark: Series<BigDecimal>,
        periods: Int = 252
    ): Map<String, BigDecimal> {
        if (returns.isEmpty() || benchmark.isEmpty()) {
            return mapOf(
                "beta" to BigDecimal.ZERO,
                "alpha" to BigDecimal.ZERO
            )
        }
        
        val beta = calculateBeta(returns.`▶`.toList(), benchmark.`▶`.toList())
        val returnsMean = returns.`▶`.average()
        val benchmarkMean = benchmark.`▶`.average()
        
        val alpha = returnsMean.subtract(benchmarkMean.multiply(beta))
            .multiply(BigDecimal(periods))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
            
        return mapOf(
            "beta" to beta,
            "alpha" to alpha
        )
    }

    /**
     * Calculate Rolling Greeks
     */
    fun rollingGreeks(
        returns: Series<BigDecimal>,
        benchmark: Series<BigDecimal>,
        window: Int = 126,
        periods: Int = 252
    ): List<Map<String, BigDecimal>> {
        if (returns.isEmpty() || benchmark.isEmpty()) return emptyList()
        
        val result = mutableListOf<Map<String, BigDecimal>>()
        for (i in 0 until returns.size - window + 1) {
            val returnsWindow = returns.`▶`.drop(i).take(window).toList()
            val benchmarkWindow = benchmark.`▶`.drop(i).take(window).toList()
            
            result.add(greeks(
                returnsWindow.size j { j -> returnsWindow[j] },
                benchmarkWindow.size j { j -> benchmarkWindow[j] },
                periods
            ))
        }
        return result
    }

    private fun calculateBeta(returns: List<BigDecimal>, benchmark: List<BigDecimal>): BigDecimal {
        if (returns.isEmpty() || benchmark.isEmpty()) return BigDecimal.ZERO
        
        val returnsMean = returns.average()
        val benchmarkMean = benchmark.average()
        
        var covariance = BigDecimal.ZERO
        var benchmarkVariance = BigDecimal.ZERO
        
        for (i in returns.indices) {
            val returnsDiff = returns[i].subtract(returnsMean)
            val benchmarkDiff = benchmark[i].subtract(benchmarkMean)
            covariance = covariance.add(returnsDiff.multiply(benchmarkDiff))
            benchmarkVariance = benchmarkVariance.add(benchmarkDiff.pow(2))
        }
        
        return if (benchmarkVariance == BigDecimal.ZERO) BigDecimal.ZERO
        else covariance.divide(benchmarkVariance, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun List<BigDecimal>.average(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return sumOf { it }.divide(BigDecimal(size), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateDownsideStdDev(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        
        val mean = values.average()
        val downsideVariance = values.filter { it < BigDecimal.ZERO }
            .map { it.subtract(mean).pow(2) }
            .average()
            
        return BigDecimal(sqrt(downsideVariance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    private fun calculateStdDev(values: List<BigDecimal>): BigDecimal {
        if (values.isEmpty()) return BigDecimal.ZERO
        
        val mean = values.average()
        val variance = values.map { it.subtract(mean).pow(2) }
            .average()
            
        return BigDecimal(sqrt(variance.toDouble()))
            .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the monthly returns
     */
    fun monthlyReturns(returns: Series<BigDecimal>, eoy: Boolean = true, compounded: Boolean = true): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val result: MutableList<BigDecimal> = mutableListOf()
        var currentMonth: Int = -1
        var currentYear: Int = -1
        var monthlyReturn: BigDecimal = BigDecimal.ONE
        
        for (ret in returnsList) {
            val date: String = ret.toString() // Assuming date is in YYYYMM format
            val month: Int = date.substring(4, 6).toInt()
            val year: Int = date.substring(0, 4).toInt()
            
            if (month != currentMonth || year != currentYear) {
                if (currentMonth != -1) {
                    result.add(if (compounded) monthlyReturn.subtract(BigDecimal.ONE) else monthlyReturn)
                }
                currentMonth = month
                currentYear = year
                monthlyReturn = BigDecimal.ONE
            }
            
            monthlyReturn = monthlyReturn.multiply(ret.add(BigDecimal.ONE))
        }
        
        if (currentMonth != -1) {
            result.add(if (compounded) monthlyReturn.subtract(BigDecimal.ONE) else monthlyReturn)
        }
        
        return result.size j { i -> result[i] }
    }

    /**
     * Calculate the consecutive wins
     */
    fun consecutiveWins(returns: Series<BigDecimal>): Int {
        if (returns.isEmpty()) return 0
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        var maxConsecutive: Int = 0
        var currentConsecutive: Int = 0
        
        for (ret in returnsList) {
            if (ret > BigDecimal.ZERO) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 0
            }
        }
        
        return maxConsecutive
    }

    /**
     * Calculate the consecutive losses
     */
    fun consecutiveLosses(returns: Series<BigDecimal>): Int {
        if (returns.isEmpty()) return 0
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        var maxConsecutive: Int = 0
        var currentConsecutive: Int = 0
        
        for (ret in returnsList) {
            if (ret < BigDecimal.ZERO) {
                currentConsecutive++
                maxConsecutive = maxOf(maxConsecutive, currentConsecutive)
            } else {
                currentConsecutive = 0
            }
        }
        
        return maxConsecutive
    }

    /**
     * Calculate the best return
     */
    fun bestReturn(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        return returns.`▶`.maxOrNull() ?: BigDecimal.ZERO
    }

    /**
     * Calculate the worst return
     */
    fun worstReturn(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        return returns.`▶`.minOrNull() ?: BigDecimal.ZERO
    }

    /**
     * Calculate the geometric mean
     */
    fun geometricMean(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        var product: BigDecimal = BigDecimal.ONE
        
        for (ret in returnsList) {
            product = product.multiply(ret.add(BigDecimal.ONE))
        }
        
        return product.pow(1, MathContext(DEFAULT_SCALE))
            .subtract(BigDecimal.ONE)
    }

    /**
     * Calculate the expected return
     */
    fun expectedReturn(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        return returns.`▶`.average()
    }

    /**
     * Calculate the distribution
     */
    fun distribution(returns: Series<BigDecimal>): Map<String, BigDecimal> {
        if (returns.isEmpty()) return mapOf(
            "min" to BigDecimal.ZERO,
            "max" to BigDecimal.ZERO,
            "mean" to BigDecimal.ZERO,
            "std" to BigDecimal.ZERO,
            "q1" to BigDecimal.ZERO,
            "q2" to BigDecimal.ZERO,
            "q3" to BigDecimal.ZERO
        )
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList().sorted()
        val n: Int = returnsList.size
        
        return mapOf(
            "min" to returnsList.first(),
            "max" to returnsList.last(),
            "mean" to returnsList.average(),
            "std" to calculateStdDev(returnsList),
            "q1" to returnsList[n / 4],
            "q2" to returnsList[n / 2],
            "q3" to returnsList[3 * n / 4]
        )
    }

    /**
     * Calculate the probabilistic Sharpe ratio
     */
    fun probabilisticSharpeRatio(returns: Series<BigDecimal>, rf: BigDecimal = BigDecimal.ZERO): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val mean: BigDecimal = returnsList.average()
        val std: BigDecimal = calculateStdDev(returnsList)
        val sr: BigDecimal = mean.subtract(rf).divide(std, DEFAULT_SCALE, RoundingMode.HALF_UP)
        val srStd: BigDecimal = BigDecimal.ONE.add(sr.multiply(sr).divide(BigDecimal("2"), DEFAULT_SCALE, RoundingMode.HALF_UP))
            .sqrt(MathContext(DEFAULT_SCALE))
            .divide(returns.size.toBigDecimal().sqrt(MathContext(DEFAULT_SCALE)), DEFAULT_SCALE, RoundingMode.HALF_UP)
        
        return sr.divide(srStd, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the probabilistic Sortino ratio
     */
    fun probabilisticSortinoRatio(returns: Series<BigDecimal>, rf: BigDecimal = BigDecimal.ZERO): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val mean: BigDecimal = returnsList.average()
        val downside: BigDecimal = returnsList.filter { it < BigDecimal.ZERO }
            .map { it.multiply(it) }
            .average()
            .sqrt(MathContext(DEFAULT_SCALE))
        val sr: BigDecimal = mean.subtract(rf).divide(downside, DEFAULT_SCALE, RoundingMode.HALF_UP)
        val srStd: BigDecimal = BigDecimal.ONE.add(sr.multiply(sr).divide(BigDecimal("2"), DEFAULT_SCALE, RoundingMode.HALF_UP))
            .sqrt(MathContext(DEFAULT_SCALE))
            .divide(returns.size.toBigDecimal().sqrt(MathContext(DEFAULT_SCALE)), DEFAULT_SCALE, RoundingMode.HALF_UP)
        
        return sr.divide(srStd, DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the probabilistic adjusted Sortino ratio
     */
    fun probabilisticAdjustedSortinoRatio(returns: Series<BigDecimal>, rf: BigDecimal = BigDecimal.ZERO): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val mean: BigDecimal = returnsList.average()
        val downside: BigDecimal = returnsList.filter { it < BigDecimal.ZERO }
            .map { it.multiply(it) }
            .average()
            .sqrt(MathContext(DEFAULT_SCALE))
        val sr: BigDecimal = mean.subtract(rf).divide(downside, DEFAULT_SCALE, RoundingMode.HALF_UP)
        val skew: BigDecimal = calculateSkew(returnsList)
        val kurt: BigDecimal = calculateKurtosis(returnsList)
        val srStd: BigDecimal = BigDecimal.ONE.add(sr.multiply(sr).divide(BigDecimal("2"), DEFAULT_SCALE, RoundingMode.HALF_UP))
            .sqrt(MathContext(DEFAULT_SCALE))
            .divide(returns.size.toBigDecimal().sqrt(MathContext(DEFAULT_SCALE)), DEFAULT_SCALE, RoundingMode.HALF_UP)
        
        return sr.divide(srStd, DEFAULT_SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal.ONE.add(skew.multiply(sr).divide(BigDecimal("6"), DEFAULT_SCALE, RoundingMode.HALF_UP))
                .add(kurt.subtract(BigDecimal("3")).multiply(sr.multiply(sr)).divide(BigDecimal("24"), DEFAULT_SCALE, RoundingMode.HALF_UP)))
    }

    /**
     * Calculate the probabilistic ratio
     */
    fun probabilisticRatio(returns: Series<BigDecimal>, rf: BigDecimal = BigDecimal.ZERO, base: String = "sharpe"): BigDecimal {
        return when (base.lowercase()) {
            "sharpe" -> probabilisticSharpeRatio(returns, rf)
            "sortino" -> probabilisticSortinoRatio(returns, rf)
            "adjusted_sortino" -> probabilisticAdjustedSortinoRatio(returns, rf)
            else -> throw IllegalArgumentException("Invalid base ratio: $base")
        }
    }

    /**
     * Calculate the skewness
     */
    fun calculateSkew(returns: List<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val mean: BigDecimal = returns.average()
        val std: BigDecimal = calculateStdDev(returns)
        val n: BigDecimal = returns.size.toBigDecimal()
        
        return returns.map { it.subtract(mean).pow(3) }
            .sumOf { it }
            .divide(n.multiply(std.pow(3)), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the kurtosis
     */
    fun calculateKurtosis(returns: List<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val mean: BigDecimal = returns.average()
        val std: BigDecimal = calculateStdDev(returns)
        val n: BigDecimal = returns.size.toBigDecimal()
        
        return returns.map { it.subtract(mean).pow(4) }
            .sumOf { it }
            .divide(n.multiply(std.pow(4)), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the percentage rank
     */
    fun pctRank(returns: Series<BigDecimal>, window: Int = 60): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val result: MutableList<BigDecimal> = mutableListOf()
        
        for (i in returnsList.indices) {
            val start: Int = maxOf(0, i - window + 1)
            val windowReturns: List<BigDecimal> = returnsList.subList(start, i + 1)
            val currentReturn: BigDecimal = returnsList[i]
            val rank: BigDecimal = windowReturns.count { it <= currentReturn }.toBigDecimal()
                .divide(windowReturns.size.toBigDecimal(), DEFAULT_SCALE, RoundingMode.HALF_UP)
            result.add(rank)
        }
        
        return result.size j { i -> result[i] }
    }

    /**
     * Calculate the compound sum
     */
    fun compSum(returns: Series<BigDecimal>): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val result: MutableList<BigDecimal> = mutableListOf()
        var sum: BigDecimal = BigDecimal.ONE
        
        for (ret in returnsList) {
            sum = sum.multiply(ret.add(BigDecimal.ONE))
            result.add(sum)
        }
        
        return result.size j { i -> result[i] }
    }

    /**
     * Calculate the compound returns
     */
    fun comp(returns: Series<BigDecimal>): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val result: MutableList<BigDecimal> = mutableListOf()
        var sum: BigDecimal = BigDecimal.ONE
        
        for (ret in returnsList) {
            sum = sum.multiply(ret.add(BigDecimal.ONE))
            result.add(sum.subtract(BigDecimal.ONE))
        }
        
        return result.size j { i -> result[i] }
    }

    /**
     * Calculate the outliers
     */
    fun outliers(returns: Series<BigDecimal>, quantile: BigDecimal = BigDecimal("0.95")): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val sorted: List<BigDecimal> = returnsList.sorted()
        val threshold: BigDecimal = sorted[(sorted.size * quantile.toDouble()).toInt()]
        
        return returnsList.size j { i ->
            if (returnsList[i] > threshold) returnsList[i] else BigDecimal.ZERO
        }
    }

    /**
     * Remove outliers
     */
    fun removeOutliers(returns: Series<BigDecimal>, quantile: BigDecimal = BigDecimal("0.95")): Series<BigDecimal> {
        if (returns.isEmpty()) return 0 j { _ -> BigDecimal.ZERO }
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val sorted: List<BigDecimal> = returnsList.sorted()
        val threshold: BigDecimal = sorted[(sorted.size * quantile.toDouble()).toInt()]
        
        return returnsList.size j { i ->
            if (returnsList[i] <= threshold) returnsList[i] else threshold
        }
    }

    /**
     * Calculate the exposure
     */
    fun exposure(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val nonZero: Int = returnsList.count { it != BigDecimal.ZERO }
        
        return nonZero.toBigDecimal()
            .divide(returns.size.toBigDecimal(), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the win rate
     */
    fun winRate(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val wins: Int = returnsList.count { it > BigDecimal.ZERO }
        
        return wins.toBigDecimal()
            .divide(returns.size.toBigDecimal(), DEFAULT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Calculate the average return
     */
    fun avgReturn(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        return returns.`▶`.average()
    }

    /**
     * Calculate the average win
     */
    fun avgWin(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val wins: List<BigDecimal> = returnsList.filter { it > BigDecimal.ZERO }
        
        return if (wins.isEmpty()) BigDecimal.ZERO else wins.average()
    }

    /**
     * Calculate the average loss
     */
    fun avgLoss(returns: Series<BigDecimal>): BigDecimal {
        if (returns.isEmpty()) return BigDecimal.ZERO
        
        val returnsList: List<BigDecimal> = returns.`▶`.toList()
        val losses: List<BigDecimal> = returnsList.filter { it < BigDecimal.ZERO }
        
        return if (losses.isEmpty()) BigDecimal.ZERO else losses.average()
    }
}
