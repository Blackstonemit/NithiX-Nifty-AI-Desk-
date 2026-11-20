package com.example.engine.agents

import com.example.model.AlertAction
import com.example.model.ExecutionAction
import com.example.model.LiveTradeAlert
import com.example.model.MarketBias
import com.example.model.NiftySpotData
import com.example.model.OptionStrike
import com.example.model.OptionsChainSnapshot
import com.example.model.OptionsStrategyType
import com.example.model.PositionLifecycle
import com.example.model.TechnicalAnalysisSnapshot
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

object OptionsExecutionAgent {

    private const val NIFTY_LOT_SIZE = 75
    private const val SELLING_MARGIN_PER_LOT = 128000.0 // Standard NSE Span + Exposure margin for 1 NIFTY lot
    private const val BUYING_MARGIN_PER_LOT = 4500.0 // Premium capital for 1 lot

    fun evaluatePositionLifecycle(
        position: PositionLifecycle,
        spot: NiftySpotData,
        chain: OptionsChainSnapshot,
        tech: TechnicalAnalysisSnapshot
    ): Pair<PositionLifecycle, LiveTradeAlert?> {
        val strikeObj = chain.strikes.find { it.strikePrice == position.strike } ?: chain.strikes.first()
        val isSelling = position.strategy.isSelling
        val currentLtp = if (position.strategy == OptionsStrategyType.BUY_CE || position.strategy == OptionsStrategyType.SELL_CE) {
            strikeObj.callLtp
        } else {
            strikeObj.putLtp
        }

        // Calculate PnL
        val pointsDiff = if (isSelling) {
            position.entryPremium - currentLtp // In option selling, lower LTP = profit
        } else {
            currentLtp - position.entryPremium // In option buying, higher LTP = profit
        }
        val unrealizedPnl = pointsDiff * position.quantity
        val returnPct = if (isSelling) {
            (pointsDiff / position.entryPremium) * 100.0
        } else {
            (pointsDiff / position.entryPremium) * 100.0
        }

        // Evaluate Priority Exit Hierarchy:
        // 1. Hard Stop-Loss
        // 2. Thesis Invalidation
        // 3. Confirmed Reversal
        // 4. Profit Booking (Target 1 -> 50%, Target 2 -> 75%)
        // 5. Trailing Stop-Loss
        // 6. Time-based / Expiry risk exit

        var action = ExecutionAction.HOLD
        var alertAction: AlertAction? = null
        var exitReason = "Position moving within standard risk tolerance parameters."
        var priority = 6
        var updatedTrailingSl = position.trailingStopLoss

        when (position.strategy) {
            OptionsStrategyType.BUY_CE -> {
                when {
                    // Priority 1: Hard Stop-Loss Hit
                    currentLtp <= position.initialStopLoss -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.STOP_LOSS_HIT
                        exitReason = "Hard Stop-Loss triggered at ₹${String.format(Locale.US, "%.1f", currentLtp)} (Strict capital defense)."
                        priority = 1
                    }
                    // Priority 2: Thesis Invalidation (NIFTY breaks below confirmed breakout level or VWAP)
                    spot.spotPrice < tech.vwap - 15.0 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Thesis Invalidation: NIFTY broke below VWAP (${tech.vwap.toInt()}) with bearish order flow."
                        priority = 2
                    }
                    // Priority 3: Confirmed Reversal (Supertrend flips bearish & RSI drops below 45)
                    !tech.supertrendBullish && tech.rsi14 < 45.0 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Confirmed Reversal: Supertrend turned bearish and RSI collapsed to ${tech.rsi14.toInt()}."
                        priority = 3
                    }
                    // Priority 4: Profit Booking Target 2 (75% profit booking)
                    currentLtp >= position.target2 && position.status != ExecutionAction.PARTIAL_EXIT_75 -> {
                        action = ExecutionAction.PARTIAL_EXIT_75
                        alertAction = AlertAction.BOOK_75_PROFIT
                        exitReason = "Target 2 achieved at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Lock in 75% gains; trail remainder."
                        updatedTrailingSl = max(position.trailingStopLoss, position.target1)
                        priority = 4
                    }
                    // Priority 4: Profit Booking Target 1 (50% profit booking)
                    currentLtp >= position.target1 && position.status == ExecutionAction.HOLD -> {
                        action = ExecutionAction.PARTIAL_EXIT_50
                        alertAction = AlertAction.BOOK_50_PROFIT
                        exitReason = "Target 1 hit at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Book 50% partial profit; move SL to breakeven."
                        updatedTrailingSl = max(position.trailingStopLoss, position.entryPremium + 2.0)
                        priority = 4
                    }
                    // Priority 5: Trailing Stop-Loss
                    currentLtp > position.entryPremium + 25.0 -> {
                        val proposedSl = currentLtp - 18.0
                        if (proposedSl > updatedTrailingSl) {
                            updatedTrailingSl = proposedSl
                            action = ExecutionAction.TRAIL_STOP_LOSS
                            alertAction = AlertAction.TRAIL_STOP_LOSS
                            exitReason = "Trailing SL adjusted upward to ₹${String.format(Locale.US, "%.1f", updatedTrailingSl)} to lock momentum profits."
                            priority = 5
                        }
                    }
                }
            }

            OptionsStrategyType.BUY_PE -> {
                when {
                    currentLtp <= position.initialStopLoss -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.STOP_LOSS_HIT
                        exitReason = "Hard Stop-Loss hit on PE at ₹${String.format(Locale.US, "%.1f", currentLtp)}."
                        priority = 1
                    }
                    spot.spotPrice > tech.vwap + 15.0 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Thesis Invalidation: NIFTY recovered above VWAP (${tech.vwap.toInt()}); bear trap confirmed."
                        priority = 2
                    }
                    tech.supertrendBullish && tech.rsi14 > 55.0 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Confirmed Reversal: Supertrend turned bullish with RSI surge to ${tech.rsi14.toInt()}."
                        priority = 3
                    }
                    currentLtp >= position.target2 && position.status != ExecutionAction.PARTIAL_EXIT_75 -> {
                        action = ExecutionAction.PARTIAL_EXIT_75
                        alertAction = AlertAction.BOOK_75_PROFIT
                        exitReason = "PE Target 2 achieved at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Book 75% position."
                        updatedTrailingSl = max(position.trailingStopLoss, position.target1)
                        priority = 4
                    }
                    currentLtp >= position.target1 && position.status == ExecutionAction.HOLD -> {
                        action = ExecutionAction.PARTIAL_EXIT_50
                        alertAction = AlertAction.BOOK_50_PROFIT
                        exitReason = "PE Target 1 hit at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Book 50% profit; shift SL to cost."
                        updatedTrailingSl = max(position.trailingStopLoss, position.entryPremium + 2.0)
                        priority = 4
                    }
                    currentLtp > position.entryPremium + 25.0 -> {
                        val proposedSl = currentLtp - 18.0
                        if (proposedSl > updatedTrailingSl) {
                            updatedTrailingSl = proposedSl
                            action = ExecutionAction.TRAIL_STOP_LOSS
                            alertAction = AlertAction.TRAIL_STOP_LOSS
                            exitReason = "Trailing SL on PE raised to ₹${String.format(Locale.US, "%.1f", updatedTrailingSl)}."
                            priority = 5
                        }
                    }
                }
            }

            OptionsStrategyType.SELL_CE -> {
                // Selling CE: Profit occurs when premium decays towards 0
                when {
                    currentLtp >= position.initialStopLoss -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.STOP_LOSS_HIT
                        exitReason = "Stop-loss triggered on short CE at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Cut risk immediately."
                        priority = 1
                    }
                    spot.spotPrice > spot.r1 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Thesis Invalidation: Resistance broken above R1 (${spot.r1.toInt()}); Call short unwinding risk."
                        priority = 2
                    }
                    currentLtp <= position.target1 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.BOOK_50_PROFIT
                        exitReason = "CE premium decayed to Target ₹${String.format(Locale.US, "%.1f", currentLtp)}. 80% decay captured; book profit."
                        priority = 4
                    }
                }
            }

            OptionsStrategyType.SELL_PE -> {
                // Selling PE: Profit occurs when premium decays towards 0
                when {
                    currentLtp >= position.initialStopLoss -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.STOP_LOSS_HIT
                        exitReason = "Stop-loss triggered on short PE at ₹${String.format(Locale.US, "%.1f", currentLtp)}. Protect capital."
                        priority = 1
                    }
                    spot.spotPrice < spot.s1 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.EXIT_FULL_POSITION
                        exitReason = "Thesis Invalidation: Support breakdown below S1 (${spot.s1.toInt()}); Put seller trap avoided."
                        priority = 2
                    }
                    currentLtp <= position.target1 -> {
                        action = ExecutionAction.FULL_EXIT
                        alertAction = AlertAction.BOOK_50_PROFIT
                        exitReason = "PE premium decayed to Target ₹${String.format(Locale.US, "%.1f", currentLtp)}. Max decay captured."
                        priority = 4
                    }
                }
            }
        }

        val updatedPosition = position.copy(
            currentPremium = currentLtp,
            unrealizedPnl = unrealizedPnl,
            returnPct = returnPct,
            trailingStopLoss = updatedTrailingSl,
            status = action,
            exitReason = exitReason
        )

        val thetaPerDay = if (position.strategy == OptionsStrategyType.BUY_CE || position.strategy == OptionsStrategyType.SELL_CE) {
            abs(strikeObj.callTheta).toInt()
        } else {
            abs(strikeObj.putTheta).toInt()
        }

        val alert = if (alertAction != null) {
            LiveTradeAlert(
                id = "ALT-${UUID.randomUUID().toString().take(6).uppercase()}",
                timestamp = System.currentTimeMillis(),
                alertType = alertAction,
                strategy = position.strategy,
                instrument = position.instrument,
                strike = position.strike,
                expiry = position.expiry,
                entryPremium = position.entryPremium,
                currentPremium = currentLtp,
                stopLoss = position.initialStopLoss,
                trailingStopLoss = updatedTrailingSl,
                target1 = position.target1,
                target2 = position.target2,
                target3 = position.target3,
                pnlPoints = pointsDiff,
                pnlPct = returnPct,
                unrealizedPnlInr = unrealizedPnl,
                timeDecayImpact = if (isSelling) "Positive Theta Decay (+₹$thetaPerDay/day)" else "Negative Theta Decay (-₹$thetaPerDay/day)",
                remainingRisk = if (action == ExecutionAction.FULL_EXIT) "Zero (Exited)" else "₹${String.format(Locale.US, "%,.0f", abs(currentLtp - updatedTrailingSl) * position.quantity)}",
                exitTrigger = action.label,
                reason = exitReason,
                priorityLevel = priority
            )
        } else null

        return Pair(updatedPosition, alert)
    }

    fun generateTradeExecutionReport(positions: List<PositionLifecycle>): List<Map<String, String>> {
        return positions.map { pos ->
            mapOf(
                "Strategy" to pos.strategy.label,
                "Strike" to "${pos.strike.toInt()}",
                "Expiry" to pos.expiry,
                "Entry Premium" to "₹${String.format(Locale.US, "%.1f", pos.entryPremium)}",
                "Current Premium" to "₹${String.format(Locale.US, "%.1f", pos.currentPremium)}",
                "Stop-Loss" to "₹${String.format(Locale.US, "%.1f", pos.initialStopLoss)}",
                "Target 1" to "₹${String.format(Locale.US, "%.1f", pos.target1)}",
                "Target 2" to "₹${String.format(Locale.US, "%.1f", pos.target2)}",
                "Target 3" to "₹${String.format(Locale.US, "%.1f", pos.target3)}",
                "Exit Action" to pos.status.label,
                "Trailing SL" to "₹${String.format(Locale.US, "%.1f", pos.trailingStopLoss)}",
                "P&L" to "${if (pos.unrealizedPnl >= 0) "+" else ""}₹${String.format(Locale.US, "%,.0f", pos.unrealizedPnl)} (${String.format(Locale.US, "%.1f%%", pos.returnPct)})",
                "Exit Reason" to pos.exitReason
            )
        }
    }
}
