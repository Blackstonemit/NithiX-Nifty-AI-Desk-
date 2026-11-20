package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val NiftyDeskDarkColorScheme = darkColorScheme(
    primary = TerminalCyan,
    onPrimary = Color.Black,
    primaryContainer = TerminalCyanDim,
    onPrimaryContainer = TerminalCyan,
    secondary = BullGreen,
    onSecondary = Color.Black,
    secondaryContainer = BullGreenDim,
    onSecondaryContainer = BullGreen,
    tertiary = GoldAccent,
    onTertiary = Color.Black,
    error = BearRed,
    onError = Color.White,
    errorContainer = BearRedDim,
    onErrorContainer = BearRed,
    background = DeskBackground,
    onBackground = TextPrimary,
    surface = DeskSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeskSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DeskBorder,
    outlineVariant = DeskBorderLight
)

private val NiftyDeskLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFF94A3B8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    chartThemeMode: ChartThemeMode = if (darkTheme) ChartThemeMode.FINANCIAL_DARK else ChartThemeMode.INSTITUTIONAL_LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (chartThemeMode == ChartThemeMode.INSTITUTIONAL_LIGHT) {
        InstitutionalLightChartPalette
    } else {
        FinancialDarkChartPalette
    }
    val colorScheme = if (chartThemeMode == ChartThemeMode.INSTITUTIONAL_LIGHT) {
        NiftyDeskLightColorScheme
    } else {
        NiftyDeskDarkColorScheme
    }

    CompositionLocalProvider(LocalChartPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
