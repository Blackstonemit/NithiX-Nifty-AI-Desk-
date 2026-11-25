package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AgentReport
import com.example.model.MarketBias
import com.example.ui.theme.LocalChartPalette

@Composable
fun MultiAgentDeskScreen(
    agentReports: List<AgentReport>,
    onSelectAgent: (AgentReport) -> Unit
) {
    val palette = LocalChartPalette.current
    val scrollState = rememberScrollState()
    val centralAgent = agentReports.find { it.agentId == 9 }
    val specializedAgents = agentReports.filter { it.agentId != 9 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(scrollState)
            .padding(12.dp)
            .testTag("multi_agent_desk_screen")
    ) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.agents_desk_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.textPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        Text(
            text = stringResource(R.string.agents_desk_subtitle),
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Prominent Central AI Decision Agent Card (Agent 9)
        centralAgent?.let { agent ->
            CentralAgentCard(agent = agent)
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = stringResource(R.string.agents_specialized_header),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textMuted,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // List of all 8 domain agents
        specializedAgents.forEach { agent ->
            AgentAccordionItem(
                agent = agent,
                onSelect = { onSelectAgent(agent) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CentralAgentCard(agent: AgentReport) {
    val palette = LocalChartPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, palette.accentCyan, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accentCyan)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = stringResource(R.string.agents_badge_agent_9), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = agent.agentName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (agent.bias == MarketBias.BULLISH) palette.bullGreenDim else if (agent.bias == MarketBias.BEARISH) palette.bearRedDim else palette.goldAccentDim)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = agent.bias.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (agent.bias == MarketBias.BULLISH) palette.bullGreen else if (agent.bias == MarketBias.BEARISH) palette.bearRed else palette.goldAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = agent.summary, fontSize = 12.sp, color = palette.textPrimary, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(10.dp))

            // Confidence Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.agents_consensus_confidence), fontSize = 10.sp, color = palette.textSecondary)
                Text(text = stringResource(R.string.agents_pct_format, agent.confidence), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { agent.confidence / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = palette.accentCyan,
                trackColor = palette.surfaceHighlight
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Key Findings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceHighlight)
                    .padding(10.dp)
            ) {
                agent.keyFindings.forEach { finding ->
                    Text(text = stringResource(R.string.agents_bullet_format, finding), fontSize = 11.sp, color = palette.textSecondary, lineHeight = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AgentAccordionItem(
    agent: AgentReport,
    onSelect: () -> Unit
) {
    val palette = LocalChartPalette.current
    var expanded by remember { mutableStateOf(false) }

    val biasColor = when (agent.bias) {
        MarketBias.BULLISH -> palette.bullGreen
        MarketBias.BEARISH -> palette.bearRed
        else -> palette.goldAccent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable {
                expanded = !expanded
                onSelect()
            },
        colors = CardDefaults.cardColors(containerColor = palette.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.surfaceHighlight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = stringResource(R.string.agents_id_badge_format, agent.agentId), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = agent.agentName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(text = stringResource(R.string.agents_weight_role_format, agent.weightPct, agent.agentRole), fontSize = 9.sp, color = palette.textMuted)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(biasColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = agent.bias.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = biasColor)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = agent.summary, fontSize = 11.sp, color = palette.textSecondary)

            // Expanded Findings & Metrics
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(text = stringResource(R.string.agents_key_findings), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    agent.keyFindings.forEach { f ->
                        Text(text = stringResource(R.string.agents_bullet_format, f), fontSize = 10.sp, color = palette.textSecondary, lineHeight = 14.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.agents_raw_metrics), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accentCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    agent.rawMetrics.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = k, fontSize = 10.sp, color = palette.textMuted)
                            Text(text = v, fontSize = 10.sp, color = palette.textPrimary, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}
