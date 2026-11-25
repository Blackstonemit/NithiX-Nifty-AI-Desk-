# Nithix NIFTY Desk — Full QA, Security, SQLite, & Performance Audit Report

**Audit Date:** 2026-11-20  
**Application:** Nithix NIFTY Desk (`com.example` / `applicationId = "com.aistudio.niftydesk.fxopt"`)  
**Audit Scope:** Full 16-stage QA verification adapted for Android (Kotlin 2.2.10 + Jetpack Compose M3 + Room SQLite 2.7.0)  
**Production Code Modification Status:** **0 production files modified** (Zero-Modification Audit Boundary enforced strictly across `/app/src/main/**`, `AndroidManifest.xml`, `build.gradle.kts`, and `res/xml/**`)  
**Release Assessment Verdict:** **`NOT READY – CRITICAL ISSUES`**

---

## 1. Project Overview

**Nithix NIFTY Desk** is an institutional-style Indian equities and derivatives (NIFTY 50 / BANKNIFTY / NSE F&O) trading workstation Android application built on Jetpack Compose, Material 3, Room SQLite, OkHttp, and Firebase AI Logic (`gemini-3.8-flash` via App Check). It combines:
- A live/offline **Dashboard** (`MainActivity`, `DashboardScreen`, `NiftyDashboardView`) with market session gating (`MarketHoursEngine`), NSE MCP inspection, and quick indicators.
- An interactive **Chart Workstation** (`ChartWorkstationScreen`, `ChartWorkstationViewModel`, `ChartWorkstationDatabase`) featuring 10+ chart/oscillator sub-views, drawing tools, risk calculator, zero-lookahead backtester, and multi-LLM analyst streaming (`MultiLlmProviderManager`).
- A **Clean Architecture NIFTY Terminal** (`com.example.niftychart.*`) backed by `NiftyDatabase` (`NiftyOHLCVEntity` / `NiftyOHLCVDao`), `NiftyMarketRepositoryImpl`, real-time tick aggregation, custom Compose `Canvas` candlesticks, SMA(20/50/100/200), and Wilder RSI(14).
- **Multi-Agent Strategy, Options Chain, Futures, Execution Desk, Report & Audit, and Settings** (`NiftyMultiAgentSystem`, `OptionsGreeksCalculator`, `OptionsExecutionAgent`, `IndstocksApiClient`, `IndstocksWebSocketClient`, `McpPluginClient`, `McpOAuthClient`).

---

## 2. Environment Details

| Property | Observed Value |
|---|---|
| **Host Execution OS** | Debian GNU/Linux 12 (bookworm), headless cloud build container |
| **JDK** | OpenJDK 21.0.13 (target bytecode JVM 11) |
| **Android Gradle Plugin / Kotlin / KSP** | AGP `9.1.1` / Kotlin `2.2.10` / KSP `2.3.5` |
| **Android SDK Targets** | `compileSdk = 36` (minor 1), `targetSdk = 36`, `minSdk = 24` |
| **UI Framework** | Jetpack Compose BOM `2024.09.00` + Material 3 + Activity Compose `1.10.1` |
| **Database Engine** | Room `2.7.0` (`androidx.room.runtime`, `room-ktx`, `room-compiler`) backed by native SQLite (`3.50.6` CLI in container) |
| **Networking & AI SDKs** | OkHttp `4.10.0`, `logging-interceptor 4.10.0`, Firebase BOM `34.17.0` (`firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug`) |
| **Test Harness** | JUnit `4.13.2`, Robolectric `4.16.1` (SDK 36), AndroidX Compose UI Test (`junit4`), `kotlinx-coroutines-test 1.10.2` |
| **Container Limitations** | No Android Emulator / ADB / physical device GPU or TEE hardware available in cloud build container |

---

## 3. Architecture Findings

1. **Dual Parallel Persistence & Chart Stacks:** The codebase contains two independent database and market-chart architectures:
   - `ChartWorkstationDatabase` (version 1, `chart_workstation.db`) storing watchlist, layouts, drawings, backtests, and **plaintext LLM API keys**.
   - `NiftyDatabase` (version 2, `nifty_ohlcv.db`) storing Clean Architecture `NiftyOHLCVEntity` candles with unique composite indices and `MIGRATION_1_2`.
2. **Duplicated Technical Indicator Math:** Three separate indicator implementations (`TechnicalCalculator`, `IndicatorCalculationEngine`, and `CalculateSMAUseCase`/`CalculateRSIUseCase`) diverge on edge-case math (flat-price RSI, ATR averaging method, MACD signal warm-up, ADX fallback constants).
3. **Custom Raw TLS Socket Layer:** `IndstocksApiClient` bypasses `OkHttpClient` for GET-with-JSON-body broker requests (`/order`, `/order/trades`, `/margin`) via `SSLSocketFactory.getDefault().createSocket(host, port)` without endpoint hostname verification and implements a custom character-based HTTP chunk decoder that breaks multi-byte UTF-8 payloads.
4. **Heavy Manual Concurrency & Coupling:** `NiftyDeskViewModel` (551 LOC) and `ChartWorkstationViewModel` (683 LOC) manage multiple uncoordinated `scope.launch` coroutines; `NiftyMarketRepository.kt` alone spans 2,250 LOC.
5. **Rebranding Remnants:** While visible UI headers and strings (`strings.xml`, `metadata.json`, `DashboardScreen`) are updated to **Nithix NIFTY Desk**, internal network `User-Agent` headers and default broker order remarks still emit `"NiftyAiDesk*"` and `"NiftyTradingDesk*"`.

---

## 4. Modules Inspected (Read-Only Inventory)

All **91 Kotlin production files (31,303 LOC)** plus manifest/XML/Gradle configuration files were audited statically:

| Layer / Package | Files | Key Modules Inspected |
|---|---|---|
| **App Entry & Platform Config** | 6 | `MainActivity.kt` (605), `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `file_paths.xml`, `strings.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| **Local Persistence & Stores** | 6 | `ChartWorkstationDatabase.kt` (183), `ChartSettingsRepository.kt` (431), `NiftyDatabase.kt` (68), `NiftyOHLCVDao.kt` (206), `NiftyOHLCVEntity.kt` (88), `IndstocksCredentialStore.kt` (110), `McpConnectorStore.kt` (146) |
| **Repositories & Providers** | 6 | `NiftyMarketRepository.kt` (2,250), `MarketChartDataRepository.kt` (222), `MarketHoursEngine.kt` (95), `NiftyMarketRepositoryImpl.kt` (312), `MarketDataProvider.kt` (46), `NiftyMarketDataSource.kt` (273), `DatabaseModule.kt` (19), `RepositoryModule.kt` (78) |
| **Networking & Broker/MCP** | 6 | `IndstocksApiClient.kt` (1,567), `IndstocksWebSocketClient.kt` (257), `LiveMarketDataClient.kt` (359), `McpOAuthClient.kt` (496), `McpPluginClient.kt` (687), `NseMcpClient.kt` (245) |
| **AI & Multi-LLM** | 2 | `AppCheckInit.kt` (73), `MultiLlmProviderManager.kt` (434) |
| **Quantitative & Agent Engines** | 12 | `OptionsGreeksCalculator.kt` (106), `TechnicalCalculator.kt` (231), `IndicatorCalculationEngine.kt` (708), `MarketStructureEngine.kt` (238), `PatternDetectionEngine.kt` (218), `SignalRiskAndBacktestEngine.kt` (379), `SupportResistanceAndVolumeEngine.kt` (213), `TimeframeAggregationEngine.kt` (211), `NiftyMultiAgentSystem.kt` (725), `OptionsExecutionAgent.kt` (279), `CalculateRSIUseCase.kt` (96), `CalculateSMAUseCase.kt` (67), `GetHistoricalCandlesUseCase.kt` (48) |
| **Domain Models** | 8 | `ChartAnalysisModels.kt` (519), `IndstocksModels.kt` (367), `McpConnectorModels.kt` (111), `NseMcpModels.kt` (91), `TradingModels.kt` (341), `MarketTick.kt` (31), `OHLCV.kt` (113), `TechnicalIndicator.kt` (37) |
| **ViewModels & Theme** | 6 | `ChartWorkstationViewModel.kt` (683), `NiftyDeskViewModel.kt` (551), `ChartViewModel.kt` (348), `ChartPalette.kt` (100), `Color.kt` (26), `Theme.kt` (85), `Type.kt` (81) |
| **Compose UI Screens & Components** | 39 | `DashboardScreen.kt` (282), `ChartWorkstationScreen.kt` (622), `NiftyChartScreen.kt` (464), `CandlestickCanvas.kt` (212), `RSIIndicatorChart.kt` (265), `ExecutionDeskScreen.kt` (1,114), `OptionsChainScreen.kt` (375), `FuturesDeskScreen.kt` (221), `MultiAgentDeskScreen.kt` (308), `ReportAndAuditScreen.kt` (309), `SettingsScreen.kt` (1,196), dialogs & sub-charts |

---

## 5. Modules Tested

Automated JVM + Robolectric test coverage spanned **10 test suites** (`5` pre-existing regression suites + `5` isolated QA audit suites under `/app/src/test/java/com/example/qa/`):

| Test Suite | Tests | Passed | Failed | Skipped | Focus |
|---|---|---|---|---|---|
| `com.example.qa.QaDatabaseAuditTest` | 8 | 8 | 0 | 0 | Room SQLite PRAGMA, indices, CRUD, dedup, rollback, `MIGRATION_1_2`, SQLi, concurrency |
| `com.example.qa.QaPerformanceBenchmarkTest` | 2 | 2 | 0 | 0 | 100 / 5,000 / 20,000 bar math + 5,000-row Room batch insert/query latency & heap delta |
| `com.example.qa.QaSecurityAndEdgeToEdgeTest` | 9 | 0 | **9** | 0 | Backup rules, OAuth CSRF `state`, TLS hostname, HTTP MCP, UTF-8 chunks, fuzz, lock/race/stream bugs |
| `com.example.qa.QaSideBySideCalculationTest` | 8 | 6 | **2** | 0 | Black-Scholes parity, SMA, RSI, ATR, MACD, lot-size parity, OHLCV aggregation |
| `com.example.qa.QaUiAndEndToEndRobolectricTest` | 5 | 4 | **1** | 0 | Startup, 5-tab nav, Settings/Theme flow, Workstation/OHLCV Terminal, 48dp touch targets |
| `com.example.ChartAnalysisRobolectricTest` *(baseline)* | 7 | 7 | 0 | 0 | Workstation patterns, SMC, MTF, backtest, Room round-trip |
| `com.example.ExampleRobolectricTest` *(baseline)* | 14 | 14 | 0 | 0 | Dashboard, Futures, Execution, Report, MCP plugin, LiveData HTTP |
| `com.example.ExampleUnitTest` *(baseline)* | 5 | 5 | 0 | 0 | Futures PnL, Greeks, EMA/MACD/Supertrend, MarketHours |
| `com.example.IndstocksAndOAuthRobolectricTest` *(baseline)* | 10 | 10 | 0 | 0 | INDstocks broker REST/WS, TOTP, GTT, MCP OAuth PKCE/DCR |
| `com.example.niftychart.NiftyChartModuleTest` *(baseline)* | 8 | 8 | 0 | 0 | Clean-architecture DAO/Repo, SMA/RSI use cases, ChartViewModel, Compose chart |

---

## 6. Quantitative Test Metrics (Sections 6–12 of Checklist)

- **Total Applicable Test Cases:** `82` (`76` executed automated tests + `6` hardware/live-service blocked tests)
- **Executed Applicable Test Cases:** `76`
- **Passed Tests:** `64` (`44` baseline regression + `20` targeted QA audit checks)
- **Failed Tests:** `12` (`12` targeted QA tests reproducing verified security, functional, math, and accessibility bugs)
- **Blocked Tests:** `6` (live emulator/ADB/device profiler & live brokerage/Firebase hardware attestation checks)
- **Not Applicable Tests:** `8` (Python/Lubuntu desktop-only tooling: `compileall`, `ruff`, `mypy`, `bandit`, `pip-audit`, `pytest`, `.desktop`/X11/Wayland checks)

$$\text{TEST EXECUTION COVERAGE} = \frac{76}{82} \times 100 = \mathbf{92.68\%}$$

$$\text{TEST PASS RATE} = \frac{64}{76} \times 100 = \mathbf{84.21\%}$$

*(On the 32 newly engineered QA audit assertions specifically targeting defect hypotheses and database/performance verification, 20 passed and 12 caught live defects: 62.50% pass rate.)*

---

## 7. Prioritized Defect Reports (Sections 13–16 of Checklist)

### 7.1 Critical Bugs

#### BUG-SEC-01 — Cloud/Device Backup Exposes Plaintext LLM API Keys in Unexcluded SQLite Database
- **BUG ID:** `SEC-01`
- **TEST CASE ID:** `QA-SEC-01`
- **MODULE:** Platform Manifest & Local Storage (`AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `ChartWorkstationDatabase`)
- **COMPONENT:** `llm_provider_configs` table inside `chart_workstation.db`
- **ISSUE TITLE:** `android:allowBackup="true"` backs up unencrypted SQLite database containing OpenAI/Anthropic/NVIDIA NIM API keys
- **SEVERITY:** **CRITICAL**
- **PRIORITY:** P0 (Immediate Release Blocker)
- **ENVIRONMENT:** Android `compileSdk/targetSdk 36`, `minSdk 24`, Kotlin `2.2.10`, Room `2.7.0`, Robolectric `4.16.1`
- **STEPS TO REPRODUCE:**
  1. Open Settings -> LLM & Theme -> save an OpenAI, Anthropic, or NVIDIA NIM API key (persisted via `ChartSettingsRepository` into `LlmProviderConfigEntity.apiKey` inside `chart_workstation.db`).
  2. Inspect `AndroidManifest.xml` line 9 (`android:allowBackup="true"`).
  3. Inspect `backup_rules.xml` and `data_extraction_rules.xml` exclusions.
- **EXPECTED RESULT:** Sensitive secret stores and databases containing user API keys are either encrypted with Android Keystore / EncryptedSharedPreferences or explicitly excluded via `<exclude domain="database" path="chart_workstation.db" />` (and `allowBackup="false"` unless justified).
- **ACTUAL RESULT:** `backup_rules.xml` and `data_extraction_rules.xml` only exclude SharedPreferences XML (`niftydesk_auth_vault_prefs.xml`, `indstocks_credentials_prefs.xml`, `mcp_connectors_prefs.xml`) and leave `chart_workstation.db` eligible for Google Drive cloud backup and device-to-device transfer in plaintext.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: SEC-01: allowBackup=true requires backup_rules.xml to exclude chart_workstation.db storing plaintext LLM API keys
      at com.example.qa.QaSecurityAndEdgeToEdgeTest.QA-SEC-01 ... (QaSecurityAndEdgeToEdgeTest.kt:82)
  ```
- **ROOT CAUSE:** Developer stored `apiKey` inside Room `LlmProviderConfigEntity` (`ChartWorkstationDatabase.kt:64`) while only adding `sharedpref` exclusions in `backup_rules.xml:3-5` and `data_extraction_rules.xml:4-11`.
- **RECOMMENDED FIX:** Set `android:allowBackup="false"` in `AndroidManifest.xml` or explicitly add `<exclude domain="database" path="chart_workstation.db" />` (including `-wal`/`-shm` or journal exclusions) to both XML rule files, AND move LLM `apiKey` fields into Android Keystore / EncryptedSharedPreferences keyed by `providerId`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-SEC-01` failed), `AndroidManifest.xml:9-11`, `ChartWorkstationDatabase.kt:64`.
- **RETEST RESULT:** Pending Approval (Zero production edits permitted during QA audit).

---

### 7.2 High-Priority Bugs

#### BUG-SEC-02 — OAuth 2.1 CSRF `state` Verification Bypass When Callback Omits or Blanks `state`
- **BUG ID:** `SEC-02`
- **TEST CASE ID:** `QA-SEC-02`
- **MODULE:** `com.example.network.McpOAuthClient`
- **COMPONENT:** `exchangeCodeForToken(flow, codeOrCallbackUrl)` (`McpOAuthClient.kt:337-338`)
- **ISSUE TITLE:** Missing or blank `state` parameter in `niftydesk://oauth/callback` bypasses CSRF validation
- **SEVERITY:** **HIGH**
- **PRIORITY:** P1
- **ENVIRONMENT:** Android `targetSdk 36`, Robolectric `4.16.1`, OkHttp `4.10.0` mock transport
- **STEPS TO REPRODUCE:**
  1. Initiate an MCP OAuth PKCE flow (`pendingFlow.state = "expected_csrf_state_123"`).
  2. Trigger deep-link callback `niftydesk://oauth/callback?code=attacker_auth_code` with `state` stripped from the query string.
  3. Call `McpOAuthClient.exchangeCodeForToken(pendingFlow, callbackUrl)`.
- **EXPECTED RESULT:** Callback without an exact matching non-blank `state` parameter MUST fail immediately before contacting the `/token` endpoint (RFC 6749 §10.12 / OAuth 2.1).
- **ACTUAL RESULT:** `exchangeCodeForToken` succeeds and exchanges the attacker's authorization code for tokens.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: SEC-02: OAuth callback missing 'state' MUST be rejected to prevent CSRF code-injection
      at com.example.qa.QaSecurityAndEdgeToEdgeTest$QA-SEC-02 ... (QaSecurityAndEdgeToEdgeTest.kt:126)
  ```
- **ROOT CAUSE:** `McpOAuthClient.kt:338` checks `if (!returnedState.isNullOrBlank() && returnedState != flow.state)` — allowing `null` or blank `returnedState` to fall straight through.
- **RECOMMENDED FIX:** Enforce `if (returnedState.isNullOrBlank() || returnedState != flow.state) throw IllegalStateException("OAuth CSRF state missing or mismatched")`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-SEC-02` failed), `McpOAuthClient.kt:337-338`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-SEC-03 — Raw `SSLSocket` Transport in Live Broker Client Omits HTTPS Hostname Verification
- **BUG ID:** `SEC-03`
- **TEST CASE ID:** `QA-SEC-03`
- **MODULE:** `com.example.network.IndstocksApiClient`
- **COMPONENT:** `executeGetWithJsonBody` (`IndstocksApiClient.kt:1291-1295`)
- **ISSUE TITLE:** Live order/trade/margin GET-with-body requests create raw `SSLSocket` without `ENDPOINT_IDENTIFICATION` hostname verification (MITM risk)
- **SEVERITY:** **HIGH**
- **PRIORITY:** P1
- **ENVIRONMENT:** Android `minSdk 24`, JVM 11, Java `javax.net.ssl.SSLSocketFactory`
- **STEPS TO REPRODUCE:**
  1. Call `IndstocksApiClient.fetchOrders`, `fetchOrderTrades`, or `fetchMargin` against an HTTPS broker host.
  2. Inspect `IndstocksApiClient.kt:1291-1295`.
- **EXPECTED RESULT:** TLS connections verify that the server X.509 certificate matches the target hostname (`SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")` or `HttpsURLConnection.getDefaultHostnameVerifier().verify(host, socket.session)`).
- **ACTUAL RESULT:** `SSLSocketFactory.getDefault().createSocket(host, port)` performs CA chain trust checks only; any valid CA-signed certificate for an unrelated domain intercepts live broker bearer tokens and order records.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: SEC-03: IndstocksApiClient raw SSLSocket lacks HTTPS hostname / endpoint identification verification
      at com.example.qa.QaSecurityAndEdgeToEdgeTest.QA-SEC-03 ... (QaSecurityAndEdgeToEdgeTest.kt:142)
  ```
- **ROOT CAUSE:** Custom raw `SSLSocket` workaround for OkHttp's prohibition on `GET` request bodies omitted setting `sslParameters.endpointIdentificationAlgorithm = "HTTPS"` and post-handshake `HostnameVerifier.verify`.
- **RECOMMENDED FIX:** Cast to `SSLSocket`, apply `sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }`, call `startHandshake()`, and verify `HttpsURLConnection.getDefaultHostnameVerifier().verify(host, sslSocket.session)`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-SEC-03` failed), `IndstocksApiClient.kt:1291-1295`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-SEC-04 — Unencrypted On-Disk Storage of Broker Access Tokens & OAuth Refresh Tokens
- **BUG ID:** `SEC-04`
- **TEST CASE ID:** Static Security Audit (`QA-SEC-STATIC`)
- **MODULE:** `IndstocksCredentialStore`, `McpConnectorStore`, `ChartWorkstationDatabase`
- **COMPONENT:** `SharedPreferencesIndstocksCredentialStore` (`IndstocksCredentialStore.kt:59`), `SharedPreferencesMcpConnectorStore` (`McpConnectorStore.kt:38`), `LlmProviderConfigEntity` (`ChartWorkstationDatabase.kt:64`)
- **ISSUE TITLE:** Broker bearer token, custom connector `authSecret`/`oauthRefreshToken`, and LLM API keys stored in plaintext XML/SQLite without Android Keystore encryption
- **SEVERITY:** **HIGH**
- **PRIORITY:** P1
- **ENVIRONMENT:** Android Runtime Storage (`/data/data/com.aistudio.niftydesk.fxopt/shared_prefs/` & `databases/`)
- **STEPS TO REPRODUCE:**
  1. Authenticate INDstocks broker token, OAuth MCP refresh token, or OpenAI/Anthropic/NIM key.
  2. Inspect `IndstocksCredentialStore.kt:59-68` and `McpConnectorStore.kt:38-110`.
- **EXPECTED RESULT:** Secrets encrypted with Android Keystore AES-GCM (`MasterKey` / `Cipher` AEAD) before writing to `SharedPreferences` or Room.
- **ACTUAL RESULT:** Standard `context.getSharedPreferences(..., MODE_PRIVATE)` and unencrypted Room columns store raw bearer tokens and refresh tokens on disk.
- **ERROR MESSAGE:** Static code audit confirmed zero Keystore / `Cipher` / `EncryptedSharedPreferences` usage across all 91 Kotlin source files.
- **ROOT CAUSE:** Direct plaintext serialization in persistence classes.
- **RECOMMENDED FIX:** Wrap all persisted secrets with an Android Keystore AES-256-GCM encryption helper (using `android.security.keystore.KeyGenParameterSpec`) before saving to SharedPreferences/Room.
- **EVIDENCE:** `IndstocksCredentialStore.kt:59-69`, `McpConnectorStore.kt:38-115`, `ChartWorkstationDatabase.kt:64`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-01 — Symbol-Switch Async Race Condition Overwrites Active Chart With Stale Response
- **BUG ID:** `BUG-FUN-01`
- **TEST CASE ID:** `QA-E2E-04`
- **MODULE:** `com.example.ui.viewmodel.ChartWorkstationViewModel`
- **COMPONENT:** `ChartWorkstationCoordinator.fetchActiveSymbolCandles()` (`ChartWorkstationViewModel.kt:553-584`)
- **ISSUE TITLE:** Rapidly switching symbols lets an earlier slow network response overwrite the newly selected symbol's candlesticks and analytics
- **SEVERITY:** **HIGH**
- **PRIORITY:** P1
- **ENVIRONMENT:** Kotlin Coroutines, Robolectric `4.16.1`, OkHttp mock latency (300ms on `%5ENSEI`, 0ms on BANKNIFTY)
- **STEPS TO REPRODUCE:**
  1. Launch `ChartWorkstationCoordinator` with `NIFTY 50` active (`fetchActiveSymbolCandles()` taking 300ms at ~25,000).
  2. After 30ms, call `selectSymbol(BANKNIFTY)` (fast response returning ~52,000).
  3. Wait 500ms for both coroutines to complete.
- **EXPECTED RESULT:** Prior `fetchJob` is cancelled when switching symbol/timeframe/dateRange, OR completed results verify `requestedSymbol == currentState.activeSymbol && requestedTimeframe == currentState.timeframe` before mutating `_state`.
- **ACTUAL RESULT:** Active symbol label shows `BANKNIFTY`, while chart candles silently render stale `NIFTY 50` price `25034.0`.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: BUG-FUN-01: Stale slow NIFTY 50 fetch (25000) overwrote active BANKNIFTY chart! Actual price=25034.0
      at com.example.qa.QaSecurityAndEdgeToEdgeTest$QA-E2E-04 ... (QaSecurityAndEdgeToEdgeTest.kt:333)
  ```
- **ROOT CAUSE:** `fetchActiveSymbolCandles()` launches an untracked `scope.launch` without holding/cancelling a `fetchJob: Job?` and updates `_state` blindly upon completion.
- **RECOMMENDED FIX:** Track `private var candleFetchJob: Job? = null`, cancel it at the top of `fetchActiveSymbolCandles()`, and guard the `_state.update` check against matching `activeSymbol`, `timeframe`, and `dateRange`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-E2E-04` failed), `ChartWorkstationViewModel.kt:553-584`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-02 — Locked Chart Drawings Deleted by Delete, Undo, and Clear-All Actions
- **BUG ID:** `BUG-FUN-02`
- **TEST CASE ID:** `QA-E2E-03`
- **MODULE:** `com.example.ui.viewmodel.ChartWorkstationViewModel` & `ChartSettingsRepository`
- **COMPONENT:** `undoLastDrawing()`, `deleteDrawing(id)`, `clearAllDrawings()` (`ChartWorkstationViewModel.kt:444-469`)
- **ISSUE TITLE:** User-locked annotations (`isLocked = true`) are silently deleted by `deleteDrawing`, `undoLastDrawing`, and `clearAllDrawings`
- **SEVERITY:** **HIGH**
- **PRIORITY:** P2
- **ENVIRONMENT:** Coroutines, `InMemoryChartSettingsStore`, Robolectric `4.16.1`
- **STEPS TO REPRODUCE:**
  1. Create a horizontal line drawing on the workstation chart.
  2. Call `toggleLockDrawing(drawing)` so `isLocked == true`.
  3. Trigger `deleteDrawing(id)`, `undoLastDrawing()`, or `clearAllDrawings()`.
- **EXPECTED RESULT:** Locked drawings remain protected on the chart (`drawings.size == 1`) until explicitly unlocked.
- **ACTUAL RESULT:** `drawings.size` drops to `0`.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: BUG-FUN-02: Locked drawing must NOT be removed by delete/undo/clearAll expected:<1> but was:<0>
      at com.example.qa.QaSecurityAndEdgeToEdgeTest$QA-E2E-03 ... (QaSecurityAndEdgeToEdgeTest.kt:273)
  ```
- **ROOT CAUSE:** `ChartWorkstationViewModel.kt:444-469` and `ChartSettingsRepository.kt` (`ChartDrawingDao.clearDrawings`) execute deletions without filtering `!drawing.isLocked` (SQL `WHERE symbol = :symbol AND timeframe = :tf AND isLocked = 0`).
- **RECOMMENDED FIX:** Reject `deleteDrawing` / `undoLastDrawing` when target `isLocked == true`, and preserve locked drawings in `clearDrawings`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-E2E-03` failed), `ChartWorkstationViewModel.kt:444-469`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-08 — Real-Time Tick Stream Traps Permanently in Demo Simulator After First Network Drop
- **BUG ID:** `BUG-FUN-08`
- **TEST CASE ID:** `QA-E2E-05`
- **MODULE:** `com.example.niftychart.data.repository.NiftyMarketRepositoryImpl`
- **COMPONENT:** `streamRealTimeCandles(symbol, interval)` (`NiftyMarketRepositoryImpl.kt:268-305`)
- **ISSUE TITLE:** One transient `IOException` on `remoteProvider.streamMarketTicks` enters an infinite `demoProvider` loop, permanently preventing live feed reconnection
- **SEVERITY:** **HIGH**
- **PRIORITY:** P1
- **ENVIRONMENT:** Kotlin Coroutines / Flow, mock `MarketDataProvider` (`DemoTickMarketDataProvider` infinite loop)
- **STEPS TO REPRODUCE:**
  1. Collect `NiftyMarketRepositoryImpl.streamRealTimeCandles("NIFTY 50", ChartInterval.M1)`.
  2. Mock `remoteProvider` emits 1 tick (`25500.0`) then throws `IOException`, succeeding on attempt #2 (`26500.0`).
  3. Observe emissions across `4,000ms`.
- **EXPECTED RESULT:** After brief backoff/fallback ticks, repository retries `remoteProvider.streamMarketTicks` (`callCount >= 2`) and resumes emitting live `26500.0` ticks.
- **ACTUAL RESULT:** `callCount` stays permanently at `1`; stream emits only simulated ticks (`24000.0`) forever because `demoProvider.streamMarketTicks` contains `while (true)`.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: BUG-FUN-08: After transient error, streamRealTimeCandles trapped infinitely in demoProvider and never reconnected to liveProvider (calls=1)
      at com.example.qa.QaSecurityAndEdgeToEdgeTest$QA-E2E-05 ... (QaSecurityAndEdgeToEdgeTest.kt:390)
  ```
- **ROOT CAUSE:** `NiftyMarketRepositoryImpl.kt:296-300` executes `emitAll(demoProvider.streamMarketTicks(symbol))` synchronously without capping fallback emissions (e.g., `.take(3)`) or cancelling fallback once the reconnect timer expires.
- **RECOMMENDED FIX:** Bound fallback ticks per retry cycle (e.g., `demoProvider.streamMarketTicks(symbol).take(2)`) before looping to retry `remoteProvider` with exponential backoff (`backoffMs`).
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-E2E-05` failed), `NiftyMarketRepositoryImpl.kt:293-304`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-05 — Cross-Module Mathematical Indicator Divergence (Flat RSI, ATR Formula, MACD Warm-Up, ADX Stub)
- **BUG ID:** `BUG-FUN-05`
- **TEST CASE ID:** `QA-S2S-04` (executed fail) + Static Side-by-Side Inspection
- **MODULE:** `TechnicalCalculator`, `IndicatorCalculationEngine`, `CalculateRSIUseCase`
- **COMPONENT:** `calculateRsi`, `rsiSeries`, `calculateAtr`, `atrSeries`, `calculateMacd`, `calculateAdx`
- **ISSUE TITLE:** Three indicator engines produce conflicting RSI, ATR, MACD Signal, and ADX values for identical candle series
- **SEVERITY:** **HIGH**
- **PRIORITY:** P2
- **ENVIRONMENT:** Pure JVM Kotlin mathematical verification (`QaSideBySideCalculationTest`)
- **STEPS TO REPRODUCE:**
  1. Feed 40 identical flat candles (`close = 25000.0`) to `CalculateRSIUseCase`, `TechnicalCalculator.calculateRsi`, and `IndicatorCalculationEngine.computeAll`.
  2. Compare ATR(14), MACD(12,26,9), and short-history ADX between `TechnicalCalculator` and `IndicatorCalculationEngine`.
- **EXPECTED RESULT:**
  - Flat prices (`avgGain == 0 && avgLoss == 0`) yield neutral RSI `50.0` across all modules.
  - ATR(14) and MACD Signal use identical Wilder RMA / full-series EMA mathematics.
  - Insufficient history for ADX returns deterministic `0.0` or `null`, never fabricated trading numbers.
- **ACTUAL RESULT:**
  - `QA-S2S-04` failed: `CalculateRSIUseCase` returned `50.0`, while `TechnicalCalculator.calculateRsi` (`TechnicalCalculator.kt:68`) and `IndicatorCalculationEngine.rsiSeries` (`IndicatorCalculationEngine.kt:286`) returned **`100.0` (extreme overbought)** on flat zero-volatility candles.
  - Static formula audit also confirmed:
    - `TechnicalCalculator.calculateAtr:129` uses **simple moving average (SMA)** of TR, while `IndicatorCalculationEngine.atrSeries:421` uses **Wilder RMA**.
    - `TechnicalCalculator.calculateMacd:105` slices signal EMA over `closes.size - 25`, whereas `IndicatorCalculationEngine` computes EMA over the full MACD series.
    - `TechnicalCalculator.calculateAdx:183` returns **hardcoded `(28.5, 26.0, 18.0)`** when `candles.size < period + 2` (masking missing data as a strong trend signal).
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: BUG-FUN-05: TechnicalCalculator & IndicatorCalculationEngine must agree with 50.0 neutral RSI on flat prices (not 100.0) expected:<50.0> but was:<100.0>
      at com.example.qa.QaSideBySideCalculationTest.QA-S2S-04 ... (QaSideBySideCalculationTest.kt:151)
  ```
- **ROOT CAUSE:** Three independently authored engines were never reconciled against a shared quantitative reference.
- **RECOMMENDED FIX:** Return `50.0` when `avgGain == 0.0 && avgLoss == 0.0` in both `TechnicalCalculator` and `IndicatorCalculationEngine`, unify ATR to Wilder's RMA, remove hardcoded ADX `(28.5, 26.0, 18.0)` fallback, and route all UI/agent modules through one tested indicator library.
- **EVIDENCE:** `TEST-com.example.qa.QaSideBySideCalculationTest.xml` (`QA-S2S-04` failed), `TechnicalCalculator.kt:68,105,129,183`, `IndicatorCalculationEngine.kt:286,421`.
- **RETEST RESULT:** Pending Approval.

---

### 7.3 Medium-Priority Bugs

#### BUG-SEC-05 — Remote Cleartext `http://` Allowed for Bearer-Authenticated MCP & Custom LLM Calls
- **BUG ID:** `SEC-05`
- **TEST CASE ID:** `QA-SEC-04`
- **MODULE:** `com.example.network.McpPluginClient` (`validateUrl`), `MultiLlmProviderManager`
- **COMPONENT:** `McpPluginClient.validateUrl` (`McpPluginClient.kt:64-72`)
- **ISSUE TITLE:** Remote non-loopback `http://` endpoints pass validation, exposing Bearer tokens and OAuth secrets in cleartext
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P2
- **STEPS TO REPRODUCE:** Call `McpPluginClient().validateUrl("http://remote-broker.example.com/mcp")`.
- **EXPECTED RESULT:** Non-HTTPS URLs are rejected unless host is loopback/LAN (`localhost`, `127.0.0.1`, `::1`, or explicit local Ollama opt-in).
- **ACTUAL RESULT:** `validateUrl` returns `null` (valid).
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: SEC-05: Remote cleartext http:// URL should be rejected for Bearer-authenticated MCP endpoints
      at com.example.qa.QaSecurityAndEdgeToEdgeTest.QA-SEC-04 ... (QaSecurityAndEdgeToEdgeTest.kt:156)
  ```
- **ROOT CAUSE:** `McpPluginClient.kt:68` checks `parsed.scheme != "http" && parsed.scheme != "https"` unconditionally.
- **RECOMMENDED FIX:** Require `https://` for any non-loopback/non-RFC1918 host when credentials or Bearer auth are attached.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-SEC-04`), `McpPluginClient.kt:64-72`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-06 — Hardcoded NIFTY Lot Size Mismatch (`25` vs `75`) Causes 3x Position-Sizing Error
- **BUG ID:** `BUG-FUN-06`
- **TEST CASE ID:** `QA-S2S-07`
- **MODULE:** `SignalRiskAndBacktestEngine` (`SignalRiskAndBacktestEngine.kt:214`), `OptionsExecutionAgent` (`OptionsExecutionAgent.kt:20`)
- **COMPONENT:** `calculateRiskPosition`, `recommendedNiftyLots`
- **ISSUE TITLE:** Offline Risk Calculator & Options Agent hardcode NIFTY lot size = `25`, contradicting broker `IndstocksApiClient` lot size `75`
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P2
- **STEPS TO REPRODUCE:** Compute risk plan for `capital=200,000`, `riskPct=1.5%` (₹3,000 risk), `entry=25800`, `SL=25740` (60-pt SL).
- **EXPECTED RESULT:** Consistent NSE NIFTY lot size (`75` units/lot -> `floor(3000 / (60 * 75)) = 0` coerced to `1` lot).
- **ACTUAL RESULT:** Risk planner outputs **`2` lots** (`floor(3000 / (60 * 25))`), which equates to 150 units (3x intended ₹3,000 risk = ₹9,000 actual risk) when placed via broker!
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: BUG-FUN-06: Offline risk lot sizing ($ {plan.recommendedNiftyLots} using hardcoded 25) mismatches NSE 75 lot size expected:<1> but was:<2>
      at com.example.qa.QaSideBySideCalculationTest.QA-S2S-07 ... (QaSideBySideCalculationTest.kt:207)
  ```
- **ROOT CAUSE:** `SignalRiskAndBacktestEngine.kt:214` hardcodes `25.0` and `OptionsExecutionAgent.kt:20` hardcodes `private const val LOT_SIZE = 25`, while `IndstocksApiClient.kt:528,570` uses `75` (NSE's revised lot size).
- **RECOMMENDED FIX:** Centralize symbol contract lot-size lookup (75 for NIFTY, configurable per instrument/expiry) and remove magic `25` constants.
- **EVIDENCE:** `TEST-com.example.qa.QaSideBySideCalculationTest.xml` (`QA-S2S-07`), `SignalRiskAndBacktestEngine.kt:214`, `OptionsExecutionAgent.kt:20`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-07 — UTF-8 Multi-Byte Corruption in `IndstocksApiClient` Chunked HTTP Decoder
- **BUG ID:** `BUG-FUN-07`
- **TEST CASE ID:** `QA-E2E-01`
- **MODULE:** `com.example.network.IndstocksApiClient`
- **COMPONENT:** `readHttpResponse` & `decodeChunkedBody` (`IndstocksApiClient.kt:1326-1360`)
- **ISSUE TITLE:** Multi-chunk HTTP/1.1 responses containing multi-byte UTF-8 characters (`₹`) corrupt chunk framing and drop JSON fields
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P2
- **STEPS TO REPRODUCE:**
  1. Connect `IndstocksApiClient.executeGetWithJsonBody` to a local HTTP/1.1 `ServerSocket` returning a 2-chunk response where chunk 1 contains `"Spot ₹25,800.60"` (3 UTF-8 bytes for `₹`, 1 UTF-16 `Char`).
  2. Call `executeGetWithJsonBody(token, "/order", ...)`.
- **EXPECTED RESULT:** HTTP chunk decoder reads exact `chunkSize` **bytes** from the raw byte stream before UTF-8 decoding, successfully parsing `order_id == "ORD1"`.
- **ACTUAL RESULT:** `executeGetWithJsonBody` decoded chunks on a UTF-8 `String` (`String.length` < byte length), overshot chunk 1 into chunk 2's size/CRLF boundary, and failed to parse `"ORD1"`.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: expected:<ORD1> but was:<null>
      at com.example.qa.QaSecurityAndEdgeToEdgeTest.QA-E2E-01 ... (QaSecurityAndEdgeToEdgeTest.kt:206)
  ```
- **ROOT CAUSE:** `readHttpResponse` converts the entire socket byte stream to a `String` first and passes it to `decodeChunkedBody(chunked: String)` (`IndstocksApiClient.kt:1346-1359`), slicing `chunked.substring(dataStart, dataStart + chunkSize)` using character indices instead of RFC 9112 byte offsets.
- **RECOMMENDED FIX:** Decode headers & chunked transfer framing directly on `ByteArray` / `BufferedInputStream` before converting the assembled body bytes to UTF-8 `String`.
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-E2E-01`), `IndstocksApiClient.kt:1326-1360`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-E2E-02 — Custom Watchlist Ticker Normalizer Permits URL Path/Query Injection Delimiters
- **BUG ID:** `BUG-E2E-02`
- **TEST CASE ID:** `QA-E2E-02`
- **MODULE:** `com.example.model.NseWatchlistCatalog`
- **COMPONENT:** `normalizeCustomNseSymbol(rawInput)` (`ChartAnalysisModels.kt:55-70`)
- **ISSUE TITLE:** User-entered custom NSE ticker fails to strip `?`, `/`, `#`, `&` before building Yahoo Finance chart URLs
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P3
- **STEPS TO REPRODUCE:** Pass `"BAD/TICKER?foo=1"` to `NseWatchlistCatalog.normalizeCustomNseSymbol(...)`.
- **EXPECTED RESULT:** Non-ticker URL delimiters (`/`, `?`, `#`, `&`, spaces) are rejected or sanitized cleanly.
- **ACTUAL RESULT:** Output Yahoo ticker becomes `"BAD/TICKER?FOO=1.NS"`, injecting extra path segments and query parameters into `LiveMarketDataClient` URLs.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: Invalid URL delimiter '?' or '/' should be sanitized from Yahoo ticker: BAD/TICKER?FOO=1.NS
      at com.example.qa.QaSecurityAndEdgeToEdgeTest.QA-E2E-02 ... (QaSecurityAndEdgeToEdgeTest.kt:241)
  ```
- **ROOT CAUSE:** `normalizeCustomNseSymbol` only strips leading `$`, trims whitespace, uppercases, and appends `.NS` without character allowlist filtering or `URLEncoder`.
- **RECOMMENDED FIX:** Sanitize symbol tokens to `[A-Z0-9._^-]` (and URL-encode `yahooTicker` in `LiveMarketDataClient`).
- **EVIDENCE:** `TEST-com.example.qa.QaSecurityAndEdgeToEdgeTest.xml` (`QA-E2E-02`), `ChartAnalysisModels.kt:55-70`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-FUN-03 — `ChartWorkstationDatabase` Uses Destructive Schema Fallback Instead of Explicit Migrations
- **BUG ID:** `BUG-FUN-03`
- **TEST CASE ID:** Static Room Schema Audit (`QA-DB-STATIC`)
- **MODULE:** `com.example.data.local.ChartWorkstationDatabase`
- **COMPONENT:** `@Database` annotation & `getInstance()` (`ChartWorkstationDatabase.kt:160,179`)
- **ISSUE TITLE:** `exportSchema = false` + `.fallbackToDestructiveMigration(dropAllTables = true)` wipes user drawings, watchlist, layouts, LLM configs, and backtests on any future version bump
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P2
- **STEPS TO REPRODUCE:** Inspect `ChartWorkstationDatabase.kt:160-180`.
- **EXPECTED RESULT:** Room schema export enabled (`exportSchema = true`) with explicit `Migration(n, n+1)` instances (matching the pattern properly used in `NiftyDatabase`).
- **ACTUAL RESULT:** Any schema version increment drops all 5 tables silently (`dropAllTables = true`).
- **ROOT CAUSE:** Prototype database builder flags left in production code.
- **RECOMMENDED FIX:** Enable schema export and remove `fallbackToDestructiveMigration(dropAllTables = true)` once v2 migrations are introduced.
- **EVIDENCE:** `ChartWorkstationDatabase.kt:160,179`.
- **RETEST RESULT:** Pending Approval.

---

#### BUG-UI-01 — Incomplete Institutional Light Theme Parity Across Non-Chart Screens & Dialogs
- **BUG ID:** `BUG-UI-01`
- **TEST CASE ID:** Static Compose Theme & Robolectric Audit (`QA-UI-THEME`)
- **MODULE:** Multiple UI Screens & Dialogs
- **COMPONENT:** `SettingsScreen.kt`, `DashboardScreen.kt`, `NiftyDashboardView.kt`, `ExecutionDeskScreen.kt`, `OptionsChainScreen.kt`, `ReportAndAuditScreen.kt`, `MultiAgentDeskScreen.kt`, `IndstocksBrokerSettingsCard.kt`, `AddCustomConnectorDialog.kt`
- **ISSUE TITLE:** Institutional Light Theme applies only to Workstation/Settings-LLM card; major screens hardcode dark `DeskSurface` / `Color(0xFF...)` constants
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P3
- **STEPS TO REPRODUCE:**
  1. Switch active theme to `Institutional Light` via Settings -> LLM & Theme (`QA-UI-03`).
  2. Navigate across Dashboard, Agents, Options, Execution, Report, and Settings screens.
- **EXPECTED RESULT:** All surfaces, cards, text colors, and dividers dynamically resolve from `MaterialTheme.colorScheme` or `LocalChartPalette.current`.
- **ACTUAL RESULT:** `ExecutionDeskScreen`, `MultiAgentDeskScreen`, `OptionsChainScreen`, `SettingsScreen`, `ReportAndAuditScreen`, `NiftyDashboardView`, and broker/connector dialogs hardcode dark-navy `DeskSurface` (`0xFF0F1420`) and `DeskSurfaceHighlight` (`0xFF1E283D`), creating jarring dark blocks inside Light Mode.
- **ROOT CAUSE:** Screens import static constants from `ui/theme/Color.kt` instead of `MaterialTheme.colorScheme` or `LocalChartPalette.current`.
- **RECOMMENDED FIX:** Replace static dark `Color.kt` token imports across screens/dialogs with semantic `MaterialTheme.colorScheme` roles.
- **EVIDENCE:** Grep audit across `app/src/main/java/com/example/ui/` (e.g., `ExecutionDeskScreen.kt:369,399,449,497`, `MultiAgentDeskScreen.kt:136,238`).
- **RETEST RESULT:** Pending Approval.

---

#### BUG-UI-02 — Interactive Control Chips Fail Material 3 `48.dp` Minimum Touch Target Standard
- **BUG ID:** `BUG-UI-02`
- **TEST CASE ID:** `QA-UI-05`
- **MODULE:** `ChartWorkstationScreen`, `OptionsChainScreen`, `ReportAndAuditScreen`
- **COMPONENT:** `workstation_tf_1m` chip (`ChartWorkstationScreen.kt`), inspector tab chips, expiry chips, Report Copy button
- **ISSUE TITLE:** Workstation timeframe control chips measure `43.0.dp` height, below Material 3 / Android Accessibility `48.dp` minimum touch target
- **SEVERITY:** **MEDIUM**
- **PRIORITY:** P3
- **STEPS TO REPRODUCE:**
  1. Launch `MainActivity` -> tap `expand_workstation_button`.
  2. Measure semantics bounds height of `workstation_tf_1m` via `assertHeightIsAtLeast(48.dp)`.
- **EXPECTED RESULT:** All clickable interactive controls enforce `>= 48.dp` touch target height/width (`Modifier.minimumInteractiveComponentSize()` or `heightIn(min = 48.dp)`).
- **ACTUAL RESULT:** `workstation_tf_1m` renders at `43.0.dp` height.
- **ERROR MESSAGE:**
  ```
  java.lang.AssertionError: Actual height is 43.0.dp, expected at least 48.0.dp (tolerance: 0.5.dp)
      at com.example.qa.QaUiAndEndToEndRobolectricTest.QA-UI-05 ... (QaUiAndEndToEndRobolectricTest.kt:146)
  ```
- **ROOT CAUSE:** Custom clickable `Surface`/`Box` controls use compact `vertical = 4.dp..7.dp` padding without `minimumInteractiveComponentSize()` or `defaultMinSize(minHeight = 48.dp)`.
- **RECOMMENDED FIX:** Add `Modifier.minimumInteractiveComponentSize()` (or `heightIn(min = 48.dp)`) to interactive chips and compact action buttons.
- **EVIDENCE:** `TEST-com.example.qa.QaUiAndEndToEndRobolectricTest.xml` (`QA-UI-05` failed).
- **RETEST RESULT:** Pending Approval.

---

### 7.4 Low-Priority & Informational Issues

- **BUG-UI-03 (LOW — Residual Legacy Branding in HTTP Headers & Broker Order Remarks):** While visible UI strings and headers cleanly display `"Nithix NIFTY Desk"`, internal client `User-Agent` headers and default broker remarks retain legacy identifiers:
  - `IndstocksModels.kt:264,285` & `IndstocksApiClient.kt:84,1104` (`"NiftyAiDesk"`, `"NiftyAiDeskGTT"`)
  - `LiveMarketDataClient.kt:115,135` (`"NiftyAiDesk/1.0"`)
  - `McpPluginClient.kt:353,512` & `McpOAuthClient.kt:384,465` (`"NiftyAiDesk-MCP-Client/1.0 (Android)"`)
  - `NseMcpClient.kt:111` (`"NiftyTradingDesk-MCP-Client/1.0 (Android)"`)
- **BUG-FUN-04 (LOW/MEDIUM — 1-Second Full-List Room Persistence & Missing WS Auto-Reconnect):**
  - `NiftyMarketRepository.kt` ticker simulation loop persists chart snapshots repeatedly during 1Hz updates; `ChartViewModel` triggers frequent indicator recalculations on every tick.
  - `IndstocksWebSocketClient.kt` disconnects cleanly on failure without automated exponential-backoff reconnect logic.
- **INFO-01 (Android Lint Summary — 0 Errors, 57 Warnings):** Executed via `lint_applet`; breakdown from `/app/build/reports/lint-results-debug.html`:
  - `GradleDependency` (23), `NewerVersionAvailable` (10), `UseKtx` (12), `IconLauncherShape` (6), `AndroidGradlePluginVersion` (2), `ApplySharedPref` (1 — intentional `.commit()` in `AppCheckInit.kt:40`), `DiscouragedApi` (1 — `getIdentifier` in `AppCheckInit.kt:51`), `IconLocation` (1), `UnusedResources` (1).
- **INFO-02 (Positive Static Check — Secrets & SQLi):** Zero hardcoded API keys (`sk-`, `AIza`, `nvapi-`) found in `app/src/main`; all Room DAO queries are strictly compile-time parameterized.

---

## 8. SQLite / Room Database Audit Results

Executed via `QaDatabaseAuditTest` (**8/8 PASSED**) against temporary file-backed SQLite databases and Room in-memory instances:

| Check ID | Verification Scope | Observed Result | Status |
|---|---|---|---|
| `QA-DB-01` | `PRAGMA integrity_check`, `PRAGMA foreign_key_check`, `PRAGMA journal_mode`, `PRAGMA user_version` | Both `NiftyDatabase` (`user_version = 2`) and `ChartWorkstationDatabase` (`user_version = 1`) returned `integrity_check = ok`, zero FK violations | **PASS** |
| `QA-DB-02` | `NiftyDatabase` schema indices (`PRAGMA index_list('nifty_ohlcv')`) | Verified all 4 indices including composite unique index `(symbol, interval, timestamp)` | **PASS** |
| `QA-DB-03` | Insert single/batch, `OnConflictStrategy.IGNORE`, `upsertCandleDeduplicated`, delete & range queries | Duplicate candle ignored cleanly; upsert updated OHLCV while preserving original `id` and `createdAt` | **PASS** |
| `QA-DB-04` | Room `withTransaction` atomicity on mid-transaction `IllegalStateException` | Full rollback confirmed (`0` partial rows committed) | **PASS** |
| `QA-DB-05` | Real SQLite v1 -> v2 `NiftyDatabase.MIGRATION_1_2` upgrade on legacy file with duplicate keys | Legacy duplicate `(symbol, interval, timestamp)` collapsed preserving `MAX(id)` and unique constraint enforced cleanly | **PASS** |
| `QA-DB-06` | SQL injection strings (`"NIFTY'; DROP TABLE nifty_ohlcv; --"`) via Room DAOs | Stored safely as literal text; zero schema damage | **PASS** |
| `QA-DB-07` | 50 concurrent coroutines × 20 upserts via `NiftyMarketRepositoryImpl` (`Dispatchers.IO`) | Exactly `20` deduplicated rows stored with zero SQLite lock exceptions | **PASS** |
| `QA-DB-08` | `ChartWorkstationDatabase` CRUD across Watchlist, Layout, Drawing, LLM Config, Backtest DAOs | Full round-trip read/write/delete verified | **PASS** |

*Schema Risk Noted:* `ChartWorkstationDatabase` currently uses `fallbackToDestructiveMigration(dropAllTables = true)` (`BUG-FUN-03`).

---

## 9. Security & Privacy Audit Results

| Area | Verification Outcome | Finding Reference |
|---|---|---|
| **Hardcoded Secrets Scan** | No hardcoded OpenAI/Anthropic/Gemini/Broker keys in `app/src/main` | Clean |
| **Android Backup / Data Extraction** | `allowBackup="true"` with `chart_workstation.db` **unexcluded** (exposes LLM API keys) | **`SEC-01` (CRITICAL)** |
| **OAuth 2.1 Deep-Link CSRF** | Missing or blank `state` callback query param accepted without error | **`SEC-02` (HIGH)** |
| **Broker TLS Transport** | Raw `SSLSocketFactory.createSocket(host, port)` lacks HTTPS hostname verification | **`SEC-03` (HIGH)** |
| **At-Rest Token Encryption** | Broker tokens, OAuth refresh tokens, LLM API keys stored in plaintext XML/SQLite | **`SEC-04` (HIGH)** |
| **Cleartext Network Transport** | `McpPluginClient.validateUrl` allows remote non-loopback `http://` Bearer URLs | **`SEC-05` (MEDIUM)** |
| **FileProvider Path Scope** | Scoped tightly to `<cache-path name="chart_snapshots" path="chart_snapshots/" />` (`exported="false"`) | Clean |
| **Permissions Least Privilege** | Only `INTERNET` and `VIBRATE` declared in `AndroidManifest.xml`; zero storage permissions | Clean |

---

## 10. Side-by-Side Calculation & Mathematical Parity Results

Executed via `QaSideBySideCalculationTest` (**6 passed, 2 failed**) + baseline `ExampleUnitTest` / `ChartAnalysisRobolectricTest`:

| Test ID | Calculation Under Test | Outcome | Detail |
|---|---|---|---|
| `QA-S2S-01` | `OptionsGreeksCalculator` vs Black-Scholes Put-Call Parity & Gamma Symmetry | **PASS** | Parity `C - P == S - K*e^(-rT)` within `0.25`; Call/Put Gamma identical (`< 1e-6`) |
| `QA-S2S-02` | `CalculateSMAUseCase` & `IndicatorCalculationEngine` vs independent reference oracle | **PASS** | Exact match (`< 1e-6`) |
| `QA-S2S-03` | Wilder RSI(14) trending parity across all 3 engines | **PASS** | Within `0.01` on 80 deterministic trending bars |
| `QA-S2S-04` | Flat-price RSI(14) parity (`avgGain == 0 && avgLoss == 0`) | **FAIL (`BUG-FUN-05`)** | `CalculateRSIUseCase` = `50.0`; `TechnicalCalculator` & `IndicatorCalculationEngine` = **`100.0`** |
| `QA-S2S-05` | ATR(14) deterministic trend run (tolerance `0.5`) | **PASS** *(static diff noted)* | Passed within `0.5` on uniform trend bars; static audit proves SMA vs Wilder RMA diverges on volatile candles |
| `QA-S2S-06` | MACD(12,26,9) deterministic trend run | **PASS** *(static diff noted)* | Matched within tolerance on 60 bars; static audit flags differing signal warm-up slices |
| `QA-S2S-07` | Risk & Options NSE NIFTY Lot Size consistency | **FAIL (`BUG-FUN-06`)** | `SignalRiskAndBacktestEngine` computes `2` lots (`lot=25`) vs broker `1` lot (`lot=75`) |
| `QA-S2S-08` | `TimeframeAggregationEngine` M5->M10 OHLCV conservation & Heikin-Ashi bounds | **PASS** | Total volume conserved identically; high/low bounds valid |

---

## 11. Performance & Scalability Benchmarks (`QaPerformanceBenchmarkTest`)

Captured directly from JVM execution (`TEST-com.example.qa.QaPerformanceBenchmarkTest.xml` stdout `[QA-BENCH]`):

### Compute Engine Benchmarks (Single-Threaded Latency)
| Dataset Size (Bars) | SMA(20) Latency | RSI(14) Latency | Full `IndicatorCalculationEngine.computeAll` | Zero-Lookahead `runBacktest` | Net Heap Delta |
|---|---|---|---|---|---|
| **Small (`100`)** | `0.03 ms` | `0.08 ms` | `4.19 ms` | `0.91 ms` | `0.00 MB` |
| **Medium (`5,000`)** | `1.55 ms` | `2.02 ms` | `194.44 ms` | `28.86 ms` | `-65.77 MB` *(post-GC)* |
| **Large (`20,000`)** | `5.20 ms` | `8.33 ms` | `273.67 ms` | `69.52 ms` | `+50.00 MB` |

### Room SQLite Benchmarks (`NiftyDatabase` / `NiftyOHLCVDao`)
| Operation | Dataset Size | Measured Latency |
|---|---|---|
| **Batch Insert (`insertCandlesBatch`)** | `5,000` rows | `949.14 ms` (`~5,268 rows/sec`) |
| **Indexed Range/Latest Query (`getLatestNCandles(200)`)** | `5,000` stored rows | `3.97 ms` |
| **Deduplicated Upsert (`upsertCandleDeduplicated` via `NiftyMarketRepositoryImpl`)** | `500` overlap rows | `33.26 ms` |

**Performance Assessment:** Indicator math and indexed Room queries scale cleanly sub-300ms even at 20,000 bars, well suited for background `Dispatchers.Default` / `Dispatchers.IO`. However, on large datasets (`5,000–20,000` bars), running full-history `IndicatorCalculationEngine.computeAll` (`~194–274 ms`) on every 1-second tick if triggered repeatedly will allocate substantial intermediate collections (`+50 MB` at 20k bars).

---

## 12. GUI, End-to-End & Edge-to-Edge Testing Results

- **Robolectric Compose Navigation & Startup (`QA-UI-01..04` — PASSED):**
  - `MainActivity` boots cleanly with rebranded `"NITHIX NIFTY DESK"` header (0 occurrences of `"NIFTY AI DESK"`).
  - Full 5-tab navigation (`Dashboard` -> `9 Agents` -> `Options`/`Futures` -> `Execution` -> `Report`), Settings -> `Connectors` -> `LLM & Theme` -> `Institutional Light` switch, Fullscreen `ChartWorkstationScreen` inspector tabs, and Room `NiftyChartScreen` open/back flows all execute without exceptions.
- **Accessibility Touch Targets (`QA-UI-05` — FAILED):** `workstation_tf_1m` measured **`43.0.dp`** height (`< 48.0.dp` M3 standard, logged as `BUG-UI-02`).
- **Concurrency, Chunking & Stream Resilience (`QA-E2E-01..05` — FAILED catching real bugs):** Caught chunked UTF-8 corruption (`BUG-FUN-07`), ticker delimiter injection (`BUG-E2E-02`), locked drawing deletion (`BUG-FUN-02`), stale symbol fetch overwrite (`BUG-FUN-01`), and permanent simulator fallback lock-in (`BUG-FUN-08`).

---

## 13. Android Compatibility & Checklist Platform Mapping Results

Because the attached QA checklist targets a Python / Lubuntu desktop runtime (`compileall`, `ruff`, `mypy`, `bandit`, X11/Wayland), compatibility checks were mapped natively:
- **Android API Coverage Executed:** Verified under Robolectric SDK 36 configuration and Gradle compile/Lint targeting `minSdk 24` through `targetSdk 36` (`compileSdk 36.1`). Edge-to-edge system bar handling (`enableEdgeToEdge()`, `WindowInsets`) verified structurally in `MainActivity.kt` and top-level screens.
- **Blocked Hardware / Live-Device Checks (`6` cases):**
  - `QA-BLK-01`: Live Android Emulator visual rendering, scroll clipping & multi-touch pinch-zoom ergonomics (ADB/emulator unavailable).
  - `QA-BLK-02`: Live INDstocks Broker OAuth/TOTP authentication & live exchange order routing (no live broker credentials).
  - `QA-BLK-03`: Live Firebase AI Logic Gemini stream with Play Integrity / App Check attestation on real device.
  - `QA-BLK-04`: End-to-end `adb backup` / `adb restore` validation on physical hardware (`SEC-01` verified via manifest/XML rules).
  - `QA-BLK-05`: Real-device GPU frame pacing (Canvas jank), battery drain & memory profiler soak test.
  - `QA-BLK-06`: Multi-window / foldable posture & live system font scaling (`2.0x`) rendering check.
- **Not Applicable Python/Lubuntu Items (`8` cases):** Python syntax (`compileall`), `ruff`, `mypy`, `bandit`, `pip-audit`, `pytest`, `.desktop` launchers, and X11/Wayland display server checks.

---

## 14. Test Artifacts & Evidence

- **Isolated QA Test Suite:** `/app/src/test/java/com/example/qa/` (`QaDatabaseAuditTest.kt`, `QaPerformanceBenchmarkTest.kt`, `QaSecurityAndEdgeToEdgeTest.kt`, `QaSideBySideCalculationTest.kt`, `QaUiAndEndToEndRobolectricTest.kt`).
- **JUnit XML Reports:** `/app/build/test-results/testDebugUnitTest/TEST-*.xml` (10 XML reports covering all 76 executed tests).
- **Android Lint Report:** `/app/build/reports/lint-results-debug.html` (0 errors, 57 warnings).

---

## 15. Recommended Improvements (Prioritized Action Roadmap)

1. **P0 Security Hardening:** Exclude `chart_workstation.db` (or set `android:allowBackup="false"`) in `AndroidManifest.xml` + backup XMLs (`SEC-01`), enforce mandatory non-blank CSRF `state` equality in `McpOAuthClient` (`SEC-02`), enable TLS `HTTPS` hostname verification on `IndstocksApiClient` raw `SSLSocket` (`SEC-03`), and encrypt persisted tokens/API keys using Android Keystore AES-GCM (`SEC-04`).
2. **P1 Concurrency & Stream Reliability:** Cancel previous `fetchJob` / check active symbol-timeframe guard in `ChartWorkstationViewModel` (`BUG-FUN-01`), cap `demoProvider` emissions per retry loop in `NiftyMarketRepositoryImpl` (`BUG-FUN-08`), and add exponential-backoff WebSocket reconnect to `IndstocksWebSocketClient` (`BUG-FUN-04`).
3. **P2 Data Integrity, Transport & Quantitative Parity:** Guard `isLocked == true` drawings across delete/undo/clearAll (`BUG-FUN-02`), rewrite `IndstocksApiClient.decodeChunkedBody` over raw byte arrays (`BUG-FUN-07`), unify flat-price RSI (`50.0`), Wilder ATR, MACD warm-up, remove hardcoded ADX stub (`BUG-FUN-05`), standardize NSE NIFTY lot size = `75` (`BUG-FUN-06`), sanitize custom NSE tickers (`BUG-E2E-02`), require HTTPS for non-loopback Bearer MCP connectors (`SEC-05`), and add explicit Room migrations for `ChartWorkstationDatabase` (`BUG-FUN-03`).
4. **P3 UI Polish & Branding:** Migrate hardcoded `DeskSurface` colors to semantic `MaterialTheme.colorScheme` / `LocalChartPalette` for Light Theme parity (`BUG-UI-01`), enforce `48.dp` minimum interactive sizes (`BUG-UI-02`), and replace residual `"NiftyAiDesk*"` HTTP headers/order remarks with `"NithixNiftyDesk*"` (`BUG-UI-03`).

---

## 16. Fix Verification & Regression Testing Status

- **Fix Verification:** **Pending User Approval.** Zero production files were altered during this audit.
- **Regression Baseline:** All **44/44 pre-existing unit & Robolectric tests** passed alongside this audit. Post-fix regression verification will re-run all 76+ automated tests once your approval is granted to apply the fixes.

---

## 17. Final Release Assessment

**STATUS: `NOT READY – CRITICAL ISSUES`**

**Justification Based on Verified Evidence:**
- **1 CRITICAL** data-privacy defect (`SEC-01`: unencrypted `chart_workstation.db` containing user OpenAI/Anthropic/NIM API keys is backed up when `allowBackup="true"`).
- **7 HIGH** security, concurrency, stream recovery, and quantitative calculation defects (`SEC-02` OAuth CSRF `state` bypass, `SEC-03` missing HTTPS hostname check on live broker socket, `SEC-04` plaintext broker/OAuth token persistence, `BUG-FUN-01` stale symbol race overwrite, `BUG-FUN-02` locked drawing deletion, `BUG-FUN-08` permanent simulator trap on transient disconnect, `BUG-FUN-05` `100.0` false-overbought RSI & hardcoded ADX).
- **6 MEDIUM** and **2 LOW** functional, lot-sizing (`25` vs `75`), UTF-8 transport, migration, theme parity, and accessibility (`43.dp` < `48.dp`) defects.

Please confirm if you would like to proceed with fixing these prioritized defects (starting with CRITICAL & HIGH P0/P1 items) and re-running full regression verification.
