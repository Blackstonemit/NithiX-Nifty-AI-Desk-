package com.example.model

/**
 * Data structures representing the official NSE India Model Context Protocol (MCP) server
 * and Index Tracker endpoints:
 *  - MCP Bhavcopy: https://mcp.nseindia.in/bhavcopy/cm/mcp
 *  - MCP Portal:   https://www.nseindia.com/nse-mcp
 *  - Index Tracker: https://www.nseindia.com/index-tracker/NIFTY%2050
 */
data class NseBhavcopyRecord(
    val symbol: String,
    val series: String = "EQ",
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val last: Double,
    val prevClose: Double,
    val totalTradedQty: Long,
    val totalTradedVal: Double,
    val tradeDate: String,
    val totalTrades: Long = 0,
    val deliveryQty: Long = 0,
    val deliveryPct: Double = 0.0,
    val isin: String = "INE769A01020"
)

data class IndexWeightageContributor(
    val symbol: String,
    val companyName: String,
    val weightPct: Double,
    val ltp: Double,
    val changePct: Double,
    val pointContribution: Double
)

data class NseIndexTrackerSnapshot(
    val indexName: String = "NIFTY 50",
    val officialTrackerUrl: String = "https://www.nseindia.com/index-tracker/NIFTY%2050",
    val ltp: Double = 25144.30,
    val change: Double = 5.05,
    val changePct: Double = 0.02,
    val advances: Int = 27,
    val declines: Int = 23,
    val unchanged: Int = 0,
    val yearHigh: Double = 26277.35,
    val yearLow: Double = 21281.45,
    val peRatio: Double = 21.65,
    val pbRatio: Double = 3.72,
    val divYield: Double = 1.24,
    val topContributors: List<IndexWeightageContributor> = listOf(
        IndexWeightageContributor("HDFCBANK", "HDFC Bank Ltd", 11.45, 1742.30, 0.85, 18.4),
        IndexWeightageContributor("RELIANCE", "Reliance Industries", 9.18, 1284.10, 0.42, 9.2),
        IndexWeightageContributor("ICICIBANK", "ICICI Bank Ltd", 7.92, 1268.50, 0.55, 10.6),
        IndexWeightageContributor("INFY", "Infosys Ltd", 5.64, 1852.20, -0.35, -6.1),
        IndexWeightageContributor("ITC", "ITC Ltd", 4.38, 482.10, -0.42, -5.2)
    )
)

data class McpToolDefinition(
    val name: String,
    val description: String,
    val parametersDescription: String
)

data class McpRpcLog(
    val id: String,
    val timestamp: Long,
    val direction: String, // "OUTGOING_REQUEST" or "INCOMING_RESPONSE"
    val method: String,
    val payloadJson: String,
    val isSuccess: Boolean = true
)

data class NseMcpState(
    val endpointUrl: String = "https://mcp.nseindia.in/bhavcopy/cm/mcp",
    val secondaryEndpointUrl: String = "https://mcp.nseindia.in/cmmkt/mcp",
    val portalUrl: String = "https://www.nseindia.com/nse-mcp",
    val indexTrackerUrl: String = "https://www.nseindia.com/index-tracker/NIFTY%2050",
    val isConnected: Boolean = false,
    val isFallbackData: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncTime: String = "Pending Live Sync",
    val latencyMs: Long = 0L,
    val protocolVersion: String = "2024-11-05 (JSON-RPC 2.0)",
    val activeTools: List<McpToolDefinition> = emptyList(),
    val latestBhavcopy: NseBhavcopyRecord? = null,
    val indexTracker: NseIndexTrackerSnapshot = NseIndexTrackerSnapshot(),
    val logs: List<McpRpcLog> = emptyList(),
    val syncStatusMessage: String = "Ready to sync with NSE MCP / Live Market Feed"
)
