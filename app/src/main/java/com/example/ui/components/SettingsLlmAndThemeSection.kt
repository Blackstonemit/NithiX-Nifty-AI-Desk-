package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LlmProviderConfig
import com.example.ui.theme.ChartThemeMode
import com.example.ui.theme.LocalChartPalette
import com.example.ui.theme.TabularNumericStyle

/**
 * Settings sub-page for selecting Financial Dark / Institutional Light chart theme and
 * configuring Multi-LLM providers (Google Gemini via Firebase AI Logic, Ollama, NVIDIA NIM,
 * OpenAI, Anthropic Claude, and Custom OpenAI-compatible Local URLs).
 */
@Composable
fun SettingsLlmAndThemeSection(
    themeMode: ChartThemeMode,
    llmConfigs: List<LlmProviderConfig>,
    isTestingLlm: Boolean,
    llmTestStatus: String?,
    onSelectTheme: (ChartThemeMode) -> Unit,
    onSelectActiveProvider: (String) -> Unit,
    onSaveConfig: (LlmProviderConfig) -> Unit,
    onTestConnection: (LlmProviderConfig) -> Unit
) {
    val palette = LocalChartPalette.current

    Column(
        modifier = Modifier.fillMaxWidth().testTag("settings_llm_and_theme_page"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Visual Chart & Workstation Theme Switcher
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "WORKSTATION VISUAL THEME",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = palette.goldAccent,
                style = TabularNumericStyle
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accentCyan else palette.surfaceVariant)
                            .border(1.dp, if (selected) palette.accentCyan else palette.border, RoundedCornerShape(8.dp))
                            .clickable { onSelectTheme(mode) }
                            .padding(vertical = 10.dp, horizontal = 10.dp)
                            .testTag("theme_option_${mode.name.lowercase()}")
                    ) {
                        Text(
                            mode.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.Black else palette.textPrimary
                        )
                    }
                }
            }
        }

        // 2. Multi-LLM Provider Manager
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = palette.accentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "MULTI-LLM AI ANALYST PROVIDERS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accentCyan,
                    style = TabularNumericStyle
                )
            }
            Text(
                "Google Gemini runs keyless via Firebase AI Logic + App Check. Optionally configure local Ollama, NVIDIA NIM, OpenAI, or Anthropic Claude endpoints.",
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            llmTestStatus?.let { status ->
                val ok = status.startsWith("PASS")
                Text(
                    text = status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) palette.bullGreen else palette.bearRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (ok) palette.bullGreenDim else palette.bearRedDim)
                        .padding(8.dp)
                )
            }

            llmConfigs.forEach { cfg ->
                LlmConfigEditorCard(
                    config = cfg,
                    isTesting = isTestingLlm,
                    onActivate = { onSelectActiveProvider(cfg.id) },
                    onSave = onSaveConfig,
                    onTest = onTestConnection
                )
            }
        }
    }
}

@Composable
private fun LlmConfigEditorCard(
    config: LlmProviderConfig,
    isTesting: Boolean,
    onActivate: () -> Unit,
    onSave: (LlmProviderConfig) -> Unit,
    onTest: (LlmProviderConfig) -> Unit
) {
    val palette = LocalChartPalette.current
    var expanded by remember { mutableStateOf(config.isActive) }
    var baseUrl by remember(config.id, config.baseUrl) { mutableStateOf(config.baseUrl) }
    var modelName by remember(config.id, config.modelName) { mutableStateOf(config.modelName) }
    var apiKey by remember(config.id, config.apiKey) { mutableStateOf(config.apiKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant)
            .border(1.dp, if (config.isActive) palette.accentCyan else palette.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.providerType.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("Model: ${config.modelName}", fontSize = 10.sp, color = palette.textSecondary, style = TabularNumericStyle)
            }
            if (config.isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Active provider", tint = palette.bullGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = palette.bullGreen, style = TabularNumericStyle)
                }
            } else {
                OutlinedButton(onClick = onActivate) {
                    Text("Use", fontSize = 10.sp)
                }
            }
        }

        if (expanded) {
            if (config.providerType != com.example.model.LlmProviderType.GEMINI_FIREBASE) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Endpoint URL", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model ID", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (config.providerType.requiresApiKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (stored locally on device)", fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSave(
                            config.copy(
                                baseUrl = baseUrl.trim(),
                                modelName = modelName.trim(),
                                apiKey = apiKey.trim(),
                                isActive = true
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accentCyan, contentColor = Color.Black)
                ) {
                    Text("Save & Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        onTest(config.copy(baseUrl = baseUrl.trim(), modelName = modelName.trim(), apiKey = apiKey.trim()))
                    },
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test Ping", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
