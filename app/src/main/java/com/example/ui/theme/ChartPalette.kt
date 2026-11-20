package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ChartThemeMode(val label: String) {
    FINANCIAL_DARK("Financial Dark"),
    INSTITUTIONAL_LIGHT("Institutional Light")
}

@Immutable
data class ChartPalette(
    val mode: ChartThemeMode,
    val background: Color,
    val canvasBackground: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val gridLine: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val bullGreen: Color,
    val bullGreenDim: Color,
    val bearRed: Color,
    val bearRedDim: Color,
    val accentCyan: Color,
    val accentCyanDim: Color,
    val goldAccent: Color,
    val goldAccentDim: Color,
    val neutralBlue: Color,
    val afterHoursShade: Color,
    val popupBackground: Color,
    val axisLabelArgb: Int,
    val crosshairArgb: Int,
    val priceLineBgArgb: Int,
    val priceLineTextArgb: Int
)

val FinancialDarkChartPalette = ChartPalette(
    mode = ChartThemeMode.FINANCIAL_DARK,
    background = DeskBackground,
    canvasBackground = Color(0xFF0A0E16),
    surface = DeskSurface,
    surfaceVariant = DeskSurfaceVariant,
    surfaceHighlight = DeskSurfaceHighlight,
    border = DeskBorder,
    gridLine = Color(0xFF1D283A),
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    bullGreen = BullGreen,
    bullGreenDim = BullGreenDim,
    bearRed = BearRed,
    bearRedDim = BearRedDim,
    accentCyan = TerminalCyan,
    accentCyanDim = TerminalCyanDim,
    goldAccent = GoldAccent,
    goldAccentDim = GoldAccentDim,
    neutralBlue = NeutralBlue,
    afterHoursShade = Color(0x331E293B),
    popupBackground = Color(0xF20A0F1A),
    axisLabelArgb = 0xFF94A3B8.toInt(),
    crosshairArgb = 0xFF00E5FF.toInt(),
    priceLineBgArgb = 0xFF00E5FF.toInt(),
    priceLineTextArgb = 0xFF04070D.toInt()
)

val InstitutionalLightChartPalette = ChartPalette(
    mode = ChartThemeMode.INSTITUTIONAL_LIGHT,
    background = Color(0xFFF3F6FA),
    canvasBackground = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceHighlight = Color(0xFFE2E8F0),
    border = Color(0xFFCBD5E1),
    gridLine = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    bullGreen = Color(0xFF059669),
    bullGreenDim = Color(0xFFD1FAE5),
    bearRed = Color(0xFFDC2626),
    bearRedDim = Color(0xFFFEE2E2),
    accentCyan = Color(0xFF0284C7),
    accentCyanDim = Color(0xFFE0F2FE),
    goldAccent = Color(0xFFD97706),
    goldAccentDim = Color(0xFFFEF3C7),
    neutralBlue = Color(0xFF2563EB),
    afterHoursShade = Color(0x2294A3B8),
    popupBackground = Color(0xF5FFFFFF),
    axisLabelArgb = 0xFF475569.toInt(),
    crosshairArgb = 0xFF0284C7.toInt(),
    priceLineBgArgb = 0xFF0284C7.toInt(),
    priceLineTextArgb = 0xFFFFFFFF.toInt()
)

val LocalChartPalette = staticCompositionLocalOf { FinancialDarkChartPalette }
