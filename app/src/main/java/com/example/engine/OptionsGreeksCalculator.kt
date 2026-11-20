package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

object OptionsGreeksCalculator {

    private const val RISK_FREE_RATE = 0.068 // 6.8% RBI Repo / T-Bill proxy

    data class GreeksResult(
        val price: Double,
        val delta: Double,
        val gamma: Double,
        val theta: Double, // Per calendar day
        val vega: Double   // Per 1% change in IV
    )

    fun normalCdf(x: Double): Double {
        // Abramowitz and Stegun approximation (formula 7.1.26)
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911

        val sign = if (x < 0) -1 else 1
        val absX = abs(x) / sqrt(2.0)

        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX)

        return 0.5 * (1.0 + sign * y)
    }

    private fun normalPdf(x: Double): Double {
        return (1.0 / sqrt(2.0 * PI)) * exp(-0.5 * x * x)
    }

    fun calculateCallGreeks(
        spot: Double,
        strike: Double,
        ivPercent: Double,
        daysToExpiry: Double,
        rate: Double = RISK_FREE_RATE
    ): GreeksResult {
        val t = max(daysToExpiry / 365.0, 0.0001)
        val sigma = max(ivPercent / 100.0, 0.01)
        val sqrtT = sqrt(t)

        val d1 = (ln(spot / strike) + (rate + 0.5 * sigma * sigma) * t) / (sigma * sqrtT)
        val d2 = d1 - sigma * sqrtT

        val nd1 = normalCdf(d1)
        val nd2 = normalCdf(d2)
        val nPrimeD1 = normalPdf(d1)

        val callPrice = max(0.05, spot * nd1 - strike * exp(-rate * t) * nd2)
        val delta = nd1
        val gamma = nPrimeD1 / (spot * sigma * sqrtT)
        // Daily theta in INR points
        val thetaAnnual = -(spot * nPrimeD1 * sigma) / (2.0 * sqrtT) - rate * strike * exp(-rate * t) * nd2
        val thetaDaily = thetaAnnual / 365.0
        val vega = (spot * sqrtT * nPrimeD1) / 100.0 // per 1% change

        return GreeksResult(callPrice, delta, gamma, thetaDaily, vega)
    }

    fun calculatePutGreeks(
        spot: Double,
        strike: Double,
        ivPercent: Double,
        daysToExpiry: Double,
        rate: Double = RISK_FREE_RATE
    ): GreeksResult {
        val t = max(daysToExpiry / 365.0, 0.0001)
        val sigma = max(ivPercent / 100.0, 0.01)
        val sqrtT = sqrt(t)

        val d1 = (ln(spot / strike) + (rate + 0.5 * sigma * sigma) * t) / (sigma * sqrtT)
        val d2 = d1 - sigma * sqrtT

        val nMinusD1 = normalCdf(-d1)
        val nMinusD2 = normalCdf(-d2)
        val nPrimeD1 = normalPdf(d1)

        val putPrice = max(0.05, strike * exp(-rate * t) * nMinusD2 - spot * nMinusD1)
        val delta = normalCdf(d1) - 1.0
        val gamma = nPrimeD1 / (spot * sigma * sqrtT)
        // Daily theta in INR points
        val thetaAnnual = -(spot * nPrimeD1 * sigma) / (2.0 * sqrtT) + rate * strike * exp(-rate * t) * nMinusD2
        val thetaDaily = thetaAnnual / 365.0
        val vega = (spot * sqrtT * nPrimeD1) / 100.0 // per 1% change

        return GreeksResult(putPrice, delta, gamma, thetaDaily, vega)
    }

    fun calculateExpectedMove(spot: Double, ivPercent: Double, daysToExpiry: Double): Double {
        val t = max(daysToExpiry / 365.0, 0.0001)
        return spot * (ivPercent / 100.0) * sqrt(t)
    }
}
