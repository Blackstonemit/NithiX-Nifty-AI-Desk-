package com.example.ai

import android.util.Log
import com.example.model.AiChartAnalysisUiState
import com.example.model.Candle
import com.example.model.DetectedCandlestickPattern
import com.example.model.IndicatorSeriesSet
import com.example.model.LlmProviderConfig
import com.example.model.LlmProviderType
import com.example.model.MarketStructureAnalysis
import com.example.model.MtfConfluenceSummary
import com.example.model.StructuredAiChartReport
import com.example.model.SupportResistanceLevel
import com.example.model.TechnicalSignalSummary
import com.example.model.Timeframe
import com.example.model.VolumeProfileSummary
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Compact quantitative context builder + Multi-LLM Provider Manager supporting:
 * 1. Google Gemini via Firebase AI Logic (gemini-3.8-flash by default, App Check protected, no key)
 * 2. Ollama (local / LAN endpoint streaming)
 * 3. NVIDIA NIM (OpenAI-compatible REST with API key)
 * 4. OpenAI (Chat Completions REST with API key)
 * 5. Anthropic Claude (Messages REST with API key)
 * 6. Custom OpenAI-Compatible Local / AI Edge endpoint
 */
class MultiLlmProviderManager(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MultiLlmManager"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val defaultGeminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig { temperature = 0.25f }
        )
    }

    fun analyzeChartStream(
        config: LlmProviderConfig,
        symbolName: String,
        timeframe: Timeframe,
        candles: List<Candle>,
        indicators: IndicatorSeriesSet,
        patterns: List<DetectedCandlestickPattern>,
        structure: MarketStructureAnalysis,
        srLevels: List<SupportResistanceLevel>,
        volumeProfile: VolumeProfileSummary,
        mtf: MtfConfluenceSummary,
        signal: TechnicalSignalSummary,
        customQuestion: String? = null
    ): Flow<AiChartAnalysisUiState> = flow {
        val providerLabel = config.providerType.displayName
        val quantitativeFallback = buildQuantitativeFallbackText(
            symbolName, timeframe, candles, patterns, structure, srLevels, volumeProfile, mtf, signal
        )
        emit(AiChartAnalysisUiState.Loading(providerLabel))

        val prompt = buildCompactStructuredPrompt(
            symbolName = symbolName,
            timeframe = timeframe,
            candles = candles,
            indicators = indicators,
            patterns = patterns,
            structure = structure,
            srLevels = srLevels,
            volumeProfile = volumeProfile,
            mtf = mtf,
            signal = signal,
            customQuestion = customQuestion
        )

        val startMs = System.currentTimeMillis()
        try {
            val fullText = when (config.providerType) {
                LlmProviderType.GEMINI_FIREBASE -> {
                    val model = if (config.modelName == "gemini-3.8-flash" && config.temperature == 0.25) {
                        defaultGeminiModel
                    } else {
                        Firebase.ai.generativeModel(
                            modelName = config.modelName.ifBlank { "gemini-3.8-flash" },
                            generationConfig = generationConfig { temperature = config.temperature.toFloat() }
                        )
                    }
                    val acc = StringBuilder()
                    withTimeout(60_000L) {
                        model.generateContentStream(prompt).collect { chunk ->
                            val delta = chunk.text.orEmpty()
                            if (delta.isNotEmpty()) {
                                acc.append(delta)
                                emit(AiChartAnalysisUiState.Streaming(acc.toString(), providerLabel))
                            }
                        }
                    }
                    acc.toString()
                }
                LlmProviderType.OLLAMA -> {
                    withTimeout(60_000L) {
                        callOllamaEndpoint(config, prompt)
                    }
                }
                LlmProviderType.ANTHROPIC -> {
                    withTimeout(60_000L) {
                        callAnthropicEndpoint(config, prompt)
                    }
                }
                LlmProviderType.NVIDIA_NIM,
                LlmProviderType.OPENAI,
                LlmProviderType.CUSTOM_LOCAL -> {
                    withTimeout(60_000L) {
                        callOpenAiCompatibleEndpoint(config, prompt)
                    }
                }
            }

            if (fullText.isBlank()) {
                emit(
                    AiChartAnalysisUiState.Error(
                        message = "Model returned an empty response.",
                        providerLabel = providerLabel,
                        quantitativeFallback = quantitativeFallback
                    )
                )
            } else {
                val report = parseStructuredReport(fullText)
                val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                emit(
                    AiChartAnalysisUiState.Success(
                        report = report,
                        providerLabel = providerLabel,
                        modelName = config.modelName,
                        latencyMs = latency
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI analysis timed out for $providerLabel", e)
            emit(
                AiChartAnalysisUiState.Error(
                    message = "$providerLabel request timed out after 60s.",
                    providerLabel = providerLabel,
                    quantitativeFallback = quantitativeFallback
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed for $providerLabel", e)
            emit(
                AiChartAnalysisUiState.Error(
                    message = e.localizedMessage ?: "Failed to reach $providerLabel",
                    providerLabel = providerLabel,
                    quantitativeFallback = quantitativeFallback
                )
            )
        }
    }

    suspend fun testConnection(config: LlmProviderConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeout(20_000L) {
                when (config.providerType) {
                    LlmProviderType.GEMINI_FIREBASE -> {
                        val reply = defaultGeminiModel.generateContent("Reply with OK only.").text.orEmpty()
                        require(reply.isNotBlank()) { "Empty reply from Firebase AI Gemini" }
                        "Connected: ${config.modelName}"
                    }
                    LlmProviderType.OLLAMA -> {
                        val tagsUrl = "${config.baseUrl.trimEnd('/')}/api/tags"
                        val req = Request.Builder().url(tagsUrl).get().build()
                        httpClient.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) throw IllegalStateException("Ollama HTTP ${res.code}")
                            "Ollama reachable (${config.baseUrl})"
                        }
                    }
                    LlmProviderType.ANTHROPIC -> {
                        require(config.apiKey.isNotBlank()) { "Anthropic API key required" }
                        val reply = callAnthropicEndpoint(config, "Reply with OK.")
                        "Connected: ${config.modelName} (${reply.take(24)})"
                    }
                    LlmProviderType.NVIDIA_NIM,
                    LlmProviderType.OPENAI,
                    LlmProviderType.CUSTOM_LOCAL -> {
                        if (config.providerType.requiresApiKey) {
                            require(config.apiKey.isNotBlank()) { "${config.providerType.displayName} API key required" }
                        }
                        val reply = callOpenAiCompatibleEndpoint(config, "Reply with OK.")
                        "Connected: ${config.modelName} (${reply.take(24)})"
                    }
                }
            }
        }
    }

    private suspend fun callOllamaEndpoint(config: LlmProviderConfig, prompt: String): String =
        withContext(Dispatchers.IO) {
            val base = config.baseUrl.trim().trimEnd('/')
            require(base.startsWith("http://") || base.startsWith("https://")) {
                "Enter a valid Ollama http(s):// URL"
            }
            val payload = JSONObject().apply {
                put("model", config.modelName.ifBlank { LlmProviderType.OLLAMA.defaultModel })
                put("prompt", prompt)
                put("stream", false)
                put("options", JSONObject().apply { put("temperature", config.temperature) })
            }.toString()

            val req = Request.Builder()
                .url("$base/api/generate")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            httpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IllegalStateException("Ollama HTTP ${res.code}: ${raw.take(180)}")
                JSONObject(raw).optString("response", "")
            }
        }

    private suspend fun callOpenAiCompatibleEndpoint(config: LlmProviderConfig, prompt: String): String =
        withContext(Dispatchers.IO) {
            val url = config.baseUrl.trim()
            require(url.startsWith("http://") || url.startsWith("https://")) { "Invalid endpoint URL" }
            if (config.providerType.requiresApiKey && config.apiKey.isBlank()) {
                throw IllegalStateException("${config.providerType.displayName} API key is not configured in Settings")
            }

            val payload = JSONObject().apply {
                put("model", config.modelName.ifBlank { config.providerType.defaultModel })
                put("temperature", config.temperature)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are an institutional NSE technical analyst. Cite only the verified numbers provided.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                )
            }.toString()

            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
            if (config.apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException("${config.providerType.displayName} HTTP ${res.code}: ${raw.take(200)}")
                }
                val root = JSONObject(raw)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
                } else ""
            }
        }

    private suspend fun callAnthropicEndpoint(config: LlmProviderConfig, prompt: String): String =
        withContext(Dispatchers.IO) {
            require(config.apiKey.isNotBlank()) { "Anthropic API key is not configured in Settings" }
            val url = config.baseUrl.trim().ifBlank { LlmProviderType.ANTHROPIC.defaultUrl }
            val payload = JSONObject().apply {
                put("model", config.modelName.ifBlank { LlmProviderType.ANTHROPIC.defaultModel })
                put("max_tokens", 1024)
                put("temperature", config.temperature)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                )
            }.toString()

            val req = Request.Builder()
                .url(url)
                .header("x-api-key", config.apiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()

            httpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw IllegalStateException("Anthropic HTTP ${res.code}: ${raw.take(200)}")
                val content = JSONObject(raw).optJSONArray("content")
                if (content != null && content.length() > 0) {
                    content.getJSONObject(0).optString("text", "")
                } else ""
            }
        }

    fun buildCompactStructuredPrompt(
        symbolName: String,
        timeframe: Timeframe,
        candles: List<Candle>,
        indicators: IndicatorSeriesSet,
        patterns: List<DetectedCandlestickPattern>,
        structure: MarketStructureAnalysis,
        srLevels: List<SupportResistanceLevel>,
        volumeProfile: VolumeProfileSummary,
        mtf: MtfConfluenceSummary,
        signal: TechnicalSignalSummary,
        customQuestion: String? = null
    ): String {
        val last = candles.lastOrNull()
        val recentBars = candles.takeLast(12).joinToString(" | ") { c ->
            String.format(Locale.US, "O:%.1f H:%.1f L:%.1f C:%.1f V:%d", c.open, c.high, c.low, c.close, c.volume)
        }
        val rsi = indicators.rsiSeries.lastOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
        val macd = indicators.macdSeries.third.lastOrNull()?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"
        val adx = indicators.adxSeries.first.lastOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
        val atr = indicators.atrSeries.lastOrNull()?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
        val srSummary = srLevels.take(6).joinToString(", ") {
            "${it.label}:${String.format(Locale.US, "%.1f", it.price)}(${it.strengthScore})"
        }
        val patSummary = patterns.takeLast(3).joinToString(", ") { "${it.title}(${it.confidencePct}%)" }.ifBlank { "None" }
        val smcSummary = "Zone=${structure.currentZoneLabel}, Breaks=${structure.structureBreaks.takeLast(2).joinToString { "${it.type}:${if (it.isBullish) "Bull" else "Bear"}" }}"
        val mtfSummary = "${mtf.overallBias.name} (${mtf.alignmentScorePct}%)"

        return buildString {
            appendLine("Analyze this verified NSE market snapshot strictly using the computed numbers below (do not invent indicator values):")
            appendLine("Instrument: $symbolName | Timeframe: ${timeframe.label} | Close: ${last?.close ?: 0.0}")
            appendLine("Quant Signal: ${signal.rating} (Score=${signal.score}/100, Invalidation=${String.format(Locale.US, "%.2f", signal.invalidationPrice)})")
            appendLine("Indicators: RSI=$rsi | MACD Hist=$macd | ADX=$adx | ATR=$atr")
            appendLine("Volume Profile: POC=${String.format(Locale.US, "%.1f", volumeProfile.pocPrice)} | VAH=${String.format(Locale.US, "%.1f", volumeProfile.vahPrice)} | VAL=${String.format(Locale.US, "%.1f", volumeProfile.valPrice)} | RVOL=${String.format(Locale.US, "%.2f", volumeProfile.rvolRatio)}x")
            appendLine("Key S/R Levels: $srSummary")
            appendLine("Market Structure / SMC: $smcSummary")
            appendLine("Recent Patterns: $patSummary")
            appendLine("MTF Alignment: $mtfSummary")
            appendLine("Recent 12 Bars: $recentBars")
            if (!customQuestion.isNullOrBlank()) appendLine("Trader Question: $customQuestion")
            appendLine()
            appendLine("Respond clearly using these exact 5 section headings:")
            appendLine("## Market Summary")
            appendLine("## Key Levels")
            appendLine("## Trade Idea")
            appendLine("## Risk Factors")
            appendLine("## Alternative Scenario")
        }
    }

    private fun buildQuantitativeFallbackText(
        symbolName: String,
        timeframe: Timeframe,
        candles: List<Candle>,
        patterns: List<DetectedCandlestickPattern>,
        structure: MarketStructureAnalysis,
        srLevels: List<SupportResistanceLevel>,
        volumeProfile: VolumeProfileSummary,
        mtf: MtfConfluenceSummary,
        signal: TechnicalSignalSummary
    ): String {
        val last = candles.lastOrNull()?.close ?: 0.0
        val nearestSup = srLevels.filter { it.isSupport }.minByOrNull { kotlin.math.abs(it.price - last) }
        val nearestRes = srLevels.filter { !it.isSupport }.minByOrNull { kotlin.math.abs(it.price - last) }
        return "Quantitative Offline Summary ($symbolName ${timeframe.label}): ${signal.rating} (${signal.score}/100), MTF ${mtf.overallBias.name} (${mtf.alignmentScorePct}%). " +
            "Nearest Support: ${nearestSup?.let { "${it.label} @ ${String.format(Locale.US, "%.1f", it.price)}" } ?: "N/A"}, " +
            "Resistance: ${nearestRes?.let { "${it.label} @ ${String.format(Locale.US, "%.1f", it.price)}" } ?: "N/A"}, " +
            "VPVR POC: ${String.format(Locale.US, "%.1f", volumeProfile.pocPrice)}, " +
            "Structure: ${structure.currentZoneLabel}, Pattern: ${patterns.lastOrNull()?.title ?: "None"}."
    }

    fun parseStructuredReport(rawText: String): StructuredAiChartReport {
        fun extractSection(heading: String, nextHeadings: List<String>): String {
            val regex = Regex("##\\s*$heading\\s*(.*?)(?=${nextHeadings.joinToString("|") { "##\\s*$it" } }|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            return regex.find(rawText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }

        val market = extractSection("Market Summary", listOf("Key Levels", "Trade Idea", "Risk Factors", "Alternative Scenario"))
        val levels = extractSection("Key Levels", listOf("Trade Idea", "Risk Factors", "Alternative Scenario"))
        val trade = extractSection("Trade Idea", listOf("Risk Factors", "Alternative Scenario"))
        val risk = extractSection("Risk Factors", listOf("Alternative Scenario"))
        val alt = extractSection("Alternative Scenario", emptyList())

        return if (market.isBlank() && levels.isBlank() && trade.isBlank()) {
            StructuredAiChartReport(
                marketSummary = rawText.trim(),
                keyLevels = "Refer to quantitative S/R & VPVR panel.",
                tradeIdea = "Use the ATR Risk Calculator to size entries against invalidation.",
                riskFactors = "Volatility spikes, exchange gaps, or MTF divergence.",
                alternativeScenario = "Invalidate setup on sustained close beyond key level.",
                rawText = rawText
            )
        } else {
            StructuredAiChartReport(
                marketSummary = market.ifBlank { rawText.take(240) },
                keyLevels = levels.ifBlank { "Refer to automatic S/R levels & POC." },
                tradeIdea = trade.ifBlank { "Wait for confirmation candle." },
                riskFactors = risk.ifBlank { "Tighten stops on ATR expansion." },
                alternativeScenario = alt.ifBlank { "Stand aside if invalidation level breaks." },
                rawText = rawText
            )
        }
    }
}
