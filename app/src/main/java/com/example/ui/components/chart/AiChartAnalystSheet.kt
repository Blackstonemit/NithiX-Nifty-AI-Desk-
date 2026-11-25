package com.example.ui.components.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AiChartAnalysisUiState
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle
import com.example.ui.viewmodel.ChartWorkstationUiState

/**
 * Multi-LLM AI Chart Analyst panel presenting quick provider switching (Gemini AI Logic, Ollama,
 * NVIDIA NIM, OpenAI, Claude, Local), streaming commentary, and 5 collapsible insight cards.
 */
@Composable
fun AiChartAnalystSheet(
    state: ChartWorkstationUiState,
    onSelectProvider: (String) -> Unit,
    onRunAnalysis: (String?) -> Unit,
    onOpenLlmSettings: () -> Unit
) {
    val palette = LocalChartPalette.current
    var customPrompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("inspector_ai_analyst_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ai_active_llm_header),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentCyan,
                style = TabularNumericStyle
            )
            OutlinedButton(onClick = onOpenLlmSettings) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ai_configure_providers), fontSize = 10.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.llmConfigs.forEach { cfg ->
                val active = cfg.id == state.activeLlmConfig.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) palette.accentCyan else palette.surfaceVariant)
                        .border(1.dp, if (active) palette.accentCyan else palette.border, RoundedCornerShape(6.dp))
                        .clickable { onSelectProvider(cfg.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("ai_provider_chip_${cfg.providerType.name.lowercase()}")
                ) {
                    Text(
                        cfg.providerType.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.Black else palette.textPrimary
                    )
                }
            }
        }

        OutlinedTextField(
            value = customPrompt,
            onValueChange = { customPrompt = it },
            label = { Text(stringResource(R.string.ai_focus_question_label), fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onRunAnalysis(customPrompt.takeIf { it.isNotBlank() }) },
            modifier = Modifier.fillMaxWidth().testTag("run_ai_chart_analysis_button"),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(
                    R.string.ai_analyze_cta_format,
                    state.activeSymbol.symbol,
                    state.timeframe.label,
                    state.activeLlmConfig.providerType.displayName
                ),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        when (val ai = state.aiAnalysisState) {
            is AiChartAnalysisUiState.Idle -> {
                Text(
                    stringResource(R.string.ai_idle_description_format, state.activeLlmConfig.modelName),
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
            is AiChartAnalysisUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = palette.accentCyan, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ai_connecting_format, ai.providerLabel), fontSize = 12.sp, color = palette.textPrimary)
                }
            }
            is AiChartAnalysisUiState.Streaming -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surfaceVariant)
                        .border(1.dp, palette.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        stringResource(R.string.ai_streaming_from_format, ai.providerLabel.uppercase()),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentCyan,
                        style = TabularNumericStyle
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(ai.partialText, fontSize = 11.5.sp, color = palette.textPrimary)
                }
            }
            is AiChartAnalysisUiState.Success -> {
                Text(
                    stringResource(R.string.ai_success_meta_format, ai.providerLabel, ai.modelName, ai.latencyMs),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.bullGreen,
                    style = TabularNumericStyle
                )
                CollapsibleInsightCard(stringResource(R.string.ai_card_market_summary), ai.report.marketSummary, true)
                CollapsibleInsightCard(stringResource(R.string.ai_card_key_levels), ai.report.keyLevels, true)
                CollapsibleInsightCard(stringResource(R.string.ai_card_trade_idea), ai.report.tradeIdea, true)
                CollapsibleInsightCard(stringResource(R.string.ai_card_risk_factors), ai.report.riskFactors, false)
                CollapsibleInsightCard(stringResource(R.string.ai_card_alt_scenario), ai.report.alternativeScenario, false)
            }
            is AiChartAnalysisUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.bearRedDim)
                        .border(1.dp, palette.bearRed, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.ai_unavailable_format, ai.providerLabel, ai.message), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = palette.bearRed)
                    Text(ai.quantitativeFallback, fontSize = 11.sp, color = palette.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleInsightCard(
    title: String,
    content: String,
    initiallyExpanded: Boolean
) {
    val palette = LocalChartPalette.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.goldAccent)
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.cd_toggle_card_format, title),
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(content, fontSize = 11.5.sp, color = palette.textPrimary, lineHeight = 16.sp)
        }
    }
}
