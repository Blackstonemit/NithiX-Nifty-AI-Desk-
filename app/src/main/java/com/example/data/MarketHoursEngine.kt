package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class MarketSessionStatus(
    val isExchangeOpen: Boolean,
    val sessionLabel: String,
    val statusColorHex: Long, // 0xFF00E676 for open, 0xFFFF334B for closed, 0xFFFFB300 for pre-market
    val currentTimeIst: String,
    val isSimulationActive: Boolean,
    val nextSessionText: String,
    val bannerExplanation: String
)

object MarketHoursEngine {

    fun getMarketSessionStatus(isSimulating: Boolean): MarketSessionStatus {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        val istTimeStr = SimpleDateFormat("hh:mm:ss a 'IST'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
            .format(calendar.time)

        val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)
        val isPreOpen = !isWeekend && (timeInMinutes in (9 * 60)..(9 * 60 + 15))
        val isMarketHours = !isWeekend && (timeInMinutes in (9 * 60 + 15)..(15 * 60 + 30))
        val isPostMarket = !isWeekend && (timeInMinutes in (15 * 60 + 30)..(16 * 60))

        return when {
            isWeekend -> {
                MarketSessionStatus(
                    isExchangeOpen = false,
                    sessionLabel = "MARKET CLOSED (WEEKEND)",
                    statusColorHex = 0xFFFF334B,
                    currentTimeIst = istTimeStr,
                    isSimulationActive = isSimulating,
                    nextSessionText = "Next Live NSE Session: Monday 09:15 AM IST",
                    bannerExplanation = "NSE is closed for the weekend. Running in Institutional Replay & Scenario Simulator mode."
                )
            }
            isMarketHours -> {
                MarketSessionStatus(
                    isExchangeOpen = true,
                    sessionLabel = "LIVE NSE MARKET",
                    statusColorHex = 0xFF00E676,
                    currentTimeIst = istTimeStr,
                    isSimulationActive = isSimulating,
                    nextSessionText = "Session Closes: Today 03:30 PM IST",
                    bannerExplanation = "Exchange feed active (09:15 - 15:30 IST)."
                )
            }
            isPreOpen -> {
                MarketSessionStatus(
                    isExchangeOpen = false,
                    sessionLabel = "PRE-OPEN SESSION",
                    statusColorHex = 0xFFFFB300,
                    currentTimeIst = istTimeStr,
                    isSimulationActive = isSimulating,
                    nextSessionText = "Normal Trading Begins: 09:15 AM IST",
                    bannerExplanation = "Pre-open price discovery underway."
                )
            }
            isPostMarket -> {
                MarketSessionStatus(
                    isExchangeOpen = false,
                    sessionLabel = "POST-MARKET CLOSING",
                    statusColorHex = 0xFFFFB300,
                    currentTimeIst = istTimeStr,
                    isSimulationActive = isSimulating,
                    nextSessionText = "Next Session: Tomorrow 09:15 AM IST",
                    bannerExplanation = "Post-market closing session."
                )
            }
            else -> {
                MarketSessionStatus(
                    isExchangeOpen = false,
                    sessionLabel = "MARKET CLOSED (OFF-HOURS)",
                    statusColorHex = 0xFFFF334B,
                    currentTimeIst = istTimeStr,
                    isSimulationActive = isSimulating,
                    nextSessionText = "Live Trading Resumes: 09:15 AM IST",
                    bannerExplanation = "NSE trading hours are 09:15 - 15:30 IST. Simulator active for research & strategy backtesting."
                )
            }
        }
    }
}
