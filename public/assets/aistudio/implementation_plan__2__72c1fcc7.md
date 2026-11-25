# QA, Automation, Security & Performance Audit Plan — Nithix NIFTY Desk

## 1. Platform Alignment & Discovery Snapshot

The attached QA checklist specifies a Python / Lubuntu / SQLite desktop pipeline (`compileall`, `ruff`, `mypy`, `bandit`, `pytest`). As confirmed, this workspace hosts the **Nithix NIFTY Desk** Android application built with **Kotlin, Jetpack Compose, and Room SQLite**, so every stage of the master audit is mapped directly to equivalent native Android verification tooling and JVM/Robolectric test execution.

| Audit Dimension | Discovered Project Reality | Verification Equivalence |
|---|---|---|
| **Runtime & Toolchain** | Android SDK (Compile/Target 36, Min 24), Kotlin 2.2.10, AGP 9.1.1, JVM 11, KSP | Gradle build + Android Lint |
| **GUI Framework** | Jetpack Compose (Material 3 BOM) + custom Canvas chart rendering | Robolectric + Compose UI Test (`junit4`) |
| **Database Engine** | Room SQLite (`2.7.0`) across two DB instances (`ChartWorkstationDatabase`, `NiftyDatabase`) | In-memory Room SQLite + `SupportSQLiteDatabase` `PRAGMA` inspection |
| **Application Entry Point** | Single-activity host (`MainActivity`) launching Edge-to-Edge Compose UI & OAuth deep-link callback | Activity scenario & intent verification |
| **Networking & AI** | OkHttp + HTTP Logging Interceptor, custom WebSocket/REST broker & MCP clients, Firebase AI Logic + App Check | OkHttp mock interceptors & static TLS/header/logging inspection |
| **Application Modules** | UI Screens & Dialogs, ViewModels, Multi-Agent & Options Greeks Engines, Chart Analysis Engines, Clean Architecture `niftychart` module, Broker/MCP/Market Repositories, Local Room & Settings Stores | Full package-by-package audit |

### Identified Testing Boundaries & Blocked Scope
Per Strict Testing Rule 19, checks that require hardware unavailable in this cloud Android build container will be executed wherever possible on Robolectric/JVM and explicitly documented as **BLOCKED** for physical/emulator-only behavior:
- **Blocked in Cloud Container:** Live Android Emulator rendering screenshots, ADB instrumentation tests, hardware GPU frame-pacing profiling, Android Keystore hardware-backed TEE verification on a real device, and unmocked live network logins against external brokerage/Yahoo/MCP servers.
- **Executable in Cloud Container:** Complete static source inspection, Android Lint analysis, Gradle debug/unit compilation, Robolectric Compose semantic/layout & navigation tests, in-memory SQLite/Room `PRAGMA` and migration tests, mocked HTTP/OAuth fault-injection tests, and JVM dataset stress/performance benchmarks.

---

## 2. QA Execution Pipeline

```text
+-------------------------------------------------------------------------+
|                    ZERO-MODIFICATION AUDIT BOUNDARY                     |
+-------------------------------------------------------------------------+
         |
         v
[Stage 1-2: Discovery]     -> Map screens, ViewModels, Room DBs, stores, clients
         |
         v
[Stage 3-4: QA Isolation]  -> Ephemeral in-memory Room DBs + temp prefs + Mock APIs
         |
         +---------------------------+---------------------------+
         |                           |                           |
         v                           v                           v
[Stage 5: Static & Lint]    [Stage 6-9: UI & Logic QA]  [Stage 10-11: DB, Sec, Perf]
- Android Lint check        - Compose tree & overlaps   - PRAGMA checks & constraints
- Coroutine / leak audit    - Greeks & SMA/RSI parity   - Token/secret storage & logs
- Manifest & deep links     - E2E & fuzz/boundary tests - Small/Med/Large benchmarks
         |                           |                           |
         +---------------------------+---------------------------+
                                     |
                                     v
[Stage 13-16: Findings & Report] -> Defect classification + QA Metrics + Release Verdict
                                     |
                                     v
                     (Halt before editing production code)
```

---

## 3. Detailed Audit Stages

### Stage 1–2: Architecture & Workflow Discovery (Read-Only)
1. **UI & Navigation Surfaces:** Inspect all 8 primary screens (Dashboard, Chart Workstation, Clean Architecture NIFTY Terminal, Multi-Agent Desk, Options Chain, Futures Desk, Execution Desk, Report & Audit, Settings) alongside interactive dialogs (Order Authorization, NSE MCP Desk, OAuth Authorize, Custom Connector, Broker Settings).
2. **State & Concurrency Ownership:** Trace state flows and coroutine scopes across the three ViewModels (`NiftyDeskViewModel`, `ChartWorkstationViewModel`, `ChartViewModel`), singleton repositories, and WebSocket/polling loops for thread safety, cancellation leaks, and main-thread blocking risks.
3. **Persistence Footprint:** Inspect both Room databases (entities, DAOs, type converters, indices, uniqueness constraints, migrations), SharedPreferences-backed credential/connector/theme stores, and backup/export rules in Android backup XML configurations.

### Stage 3–4: Isolated QA Environment Setup
- Create a dedicated QA Robolectric test suite package under unit tests using strictly isolated per-test `Context`, ephemeral `SharedPreferences`, `Room.inMemoryDatabaseBuilder` with direct query inspection via `SupportSQLiteDatabase`, and deterministic fake network transport.
- Ensure existing production source code, sample data, and default configuration files remain untouched throughout the audit.

### Stage 5: Static Code & Android Lint Audit
- Execute project compilation and full Android Lint checks to capture warnings, deprecations, API-level mismatches, Compose state-reading hazards, and manifest issues.
- Manually review critical modules for:
  - Unhandled exceptions or swallowed `CancellationException` inside coroutine `catch (Exception)` blocks.
  - Resource leaks (unclosed OkHttp responses, unclosed cursor/DB handles, leaked WebSockets or coroutine jobs).
  - Race conditions around shared mutable state, caches, or non-atomic multi-step Room writes.
  - Hardcoded credentials, debug-only secrets, or insecure HTTP logging levels.

### Stage 6–9: Compose UI, Side-by-Side, E2E & Edge-to-Edge QA
- **GUI & Accessibility Verification:** Launch Compose hosts under Robolectric (Light and Dark themes, compact and tablet screen qualifiers) to verify bottom navigation switching, workstation modal/screen transitions, back navigation handling, touch target presence, scrollable reachability, zero uncaught rendering exceptions, and state persistence across theme/timeframe switches.
- **Side-by-Side Calculation & Data Parity:** Cross-check `OptionsGreeksCalculator`, `TechnicalCalculator`, `IndicatorCalculationEngine`, `CalculateSMAUseCase`, `CalculateRSIUseCase`, and `TimeframeAggregationEngine` against independent mathematical reference calculations across edge inputs (zero volume, flat candles, NaN/Infinity guards, insufficient history, rounding precision).
- **End-to-End Journeys:** Verify realistic flows end-to-end (startup -> market snapshot sync -> Room OHLCV ingestion -> timeframe switch -> indicator toggle -> workstation drawing/layout save -> multi-agent report/export/execution validation -> simulated restart persistence).
- **Edge-to-Edge Input & State Fuzzing:** Test empty strings, whitespace-only inputs, extreme string lengths, Unicode/emoji inputs, SQL-like payload strings in symbol/note/connector fields, negative/zero/huge numeric strike/lot/capital values, rapid repeated clicks, corrupted JSON API payloads, HTTP 401/429/500 responses, and offline fallback transitions.

### Stage 10: Room SQLite Database Audit
- Run live SQLite integrity queries against test instances of both Room databases:
  - `PRAGMA integrity_check`
  - `PRAGMA foreign_key_check`
  - `PRAGMA journal_mode`
  - `PRAGMA user_version`
- Verify schema definitions, index coverage, `OnConflictStrategy` duplicate handling, transaction atomicity & `ROLLBACK` behavior on thrown errors, date-range query accuracy, retention pruning (`deleteOlderThan`), migration SQL correctness, and concurrent write synchronization.

### Stage 11: Security, Privacy & Performance Audit
- **Security Checks:**
  - Audit `IndstocksCredentialStore`, `McpConnectorStore`, `MultiLlmProviderManager`, and `ChartSettingsRepository` for plaintext secret/token persistence, key exposure in `AndroidManifest` backup rules (`allowBackup`), or `HttpLoggingInterceptor.Level.BODY` leakage of Authorization headers/API keys.
  - Audit OAuth deep-link handling (`niftydesk://oauth/callback`) for CSRF/`state` validation, open-redirect/scheme hijacking, and input sanitization.
  - Audit network clients for missing timeouts, cleartext traffic allowances, URL injection in user-supplied custom MCP endpoints, and `FileProvider` path scope exposure.
  - Review Gradle dependencies and Play Policy compliance (`android:allowBackup`, permissions least-privilege).
- **Performance & Stability Benchmarks (JVM / Robolectric):**
  - Measure execution latency and memory footprint across Small (100 candles), Medium (5,000 candles), and Large (50,000 candles) datasets for Room batch inserts/queries, SMA/RSI/MACD indicator computation, candle bucket aggregation, and chart coordinate scaling, plus repeated long-session tick streaming stability checks.

### Stage 12–16: Defect Documentation & Final QA Report Deliverable
- Classify every confirmed finding by severity (**CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL**) using the full standardized Bug Report schema (Bug ID, Test Case ID, Module, Component, Steps to Reproduce, Expected vs Actual Result, Evidence, Root Cause, Recommended Fix, Retest Status = Pending Approval).
- Save a complete, evidence-based `qa_report.md` artifact containing:
  - Environment & Module Inventory
  - Quantitative Test Metrics (`Test Execution Coverage %`, `Test Pass Rate %`, separate `Blocked` and `Not Applicable` tallies)
  - SQLite, Security, GUI, Side-by-Side, E2E, Edge-to-Edge, and Performance Audit Sections
  - Prioritized Bug Reports & Final Release Readiness Assessment (`PRODUCTION READY`, `PRODUCTION READY WITH MINOR ISSUES`, `NOT READY – MAJOR ISSUES`, or `NOT READY – CRITICAL ISSUES`)
- **Zero Unapproved Edits:** No production source files will be modified during the audit; defect remediation will await your explicit sign-off after reviewing the final QA report.
