package com.example.model

enum class ExecutionSafetyMode(
    val title: String,
    val badgeLabel: String,
    val description: String
) {
    PAPER_SIMULATION(
        title = "Paper Simulation",
        badgeLabel = "PAPER SIM",
        description = "Local simulated F&O fills & P&L tracking with zero capital risk"
    ),
    READ_ONLY_BROKER(
        title = "Broker Read-Only",
        badgeLabel = "READ-ONLY",
        description = "Syncs live INDstocks Order Book, Trade Book & Margin Preview; order placement disabled"
    ),
    LIVE_EXECUTION(
        title = "Live + Smart GTT",
        badgeLabel = "LIVE + GTT",
        description = "Submits real INDstocks REST orders & Smart GTT triggers (requires static IP whitelist)"
    )
}

enum class IndstocksConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    AUTHENTICATING("Authenticating..."),
    CONNECTED("Connected"),
    TOKEN_EXPIRED("Token Expired"),
    ERROR("Error")
}

enum class IndstocksWebSocketStatus(val label: String) {
    DISCONNECTED("WS Off"),
    CONNECTING("WS Connecting"),
    STREAMING("WS Live"),
    ERROR("WS Error")
}

data class IndstocksUserProfile(
    val userId: String = "",
    val ucc: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val name: String = "",
    val email: String = "",
    val dematId: String = "",
    val phone: String = "",
    val isDdpiActive: Boolean = false,
    val isNseOnboarded: Boolean = false,
    val isBseOnboarded: Boolean = false,
    val isNseFnoOnboarded: Boolean = false,
    val isBseFnoOnboarded: Boolean = false,
    val segments: List<String> = emptyList()
)

data class IndstocksFundsSummary(
    val availableMargin: Double = 0.0,
    val optionBuyBalance: Double = 0.0,
    val optionSellBalance: Double = 0.0,
    val futureBalance: Double = 0.0,
    val eqMisBalance: Double = 0.0,
    val eqCncBalance: Double = 0.0,
    val withdrawalBalance: Double = 0.0,
    val sodBalance: Double = 0.0,
    val usedMargin: Double = 0.0,
    val collateral: Double = 0.0,
    val openingBalance: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val brokerage: Double = 0.0,
    val eqCharges: Double = 0.0,
    val fnoCharges: Double = 0.0,
    val totalBalance: Double = 0.0
)

data class IndstocksHolding(
    val symbol: String,
    val securityId: String = "",
    val isin: String = "",
    val exchange: String = "NSE",
    val quantity: Int = 0,
    val usedQuantity: Int = 0,
    val t1Quantity: Int = 0,
    val dpQuantity: Int = 0,
    val avgPrice: Double = 0.0,
    val lastPrice: Double = 0.0,
    val pnl: Double = 0.0,
    val pnlPct: Double = 0.0
)

data class IndstocksPosition(
    val positionId: String = "",
    val symbol: String,
    val securityId: String = "",
    val segment: String = "DERIVATIVE",
    val product: String = "MARGIN",
    val exchange: String = "NSE",
    val drvInstrument: String = "",
    val drvExpiryDate: String = "",
    val drvOptionType: String = "",
    val drvStrikePrice: Double = 0.0,
    val netQty: Int = 0,
    val avgPrice: Double = 0.0,
    val buyQty: Int = 0,
    val buyAvg: Double = 0.0,
    val sellQty: Int = 0,
    val sellAvg: Double = 0.0,
    val lastPrice: Double = 0.0,
    val realizedPnl: Double = 0.0,
    val unrealizedPnl: Double = 0.0,
    val mtm: Double = 0.0
)

data class IndstocksOrderRecord(
    val orderId: String,
    val exchOrderId: String = "",
    val symbol: String,
    val securityId: String = "",
    val txnType: String = "BUY",
    val exchange: String = "NSE",
    val orderType: String = "LIMIT",
    val product: String = "MARGIN",
    val segment: String = "DERIVATIVE",
    val validity: String = "DAY",
    val quantity: Int = 0,
    val filledQty: Int = 0,
    val price: Double = 0.0,
    val tradedPrice: Double = 0.0,
    val triggerPrice: Double = 0.0,
    val slTriggerPrice: Double = 0.0,
    val slLimitPrice: Double = 0.0,
    val tgtTriggerPrice: Double = 0.0,
    val tgtLimitPrice: Double = 0.0,
    val childOrderId: String = "",
    val parentOrderId: String = "",
    val status: String = "PENDING",
    val statusMessage: String = "",
    val remarks: String = "",
    val isSmartOrder: Boolean = false,
    val placedAt: String = ""
)

data class IndstocksTradeRecord(
    val tradeId: String,
    val exchOrderId: String = "",
    val orderId: String = "",
    val scripCode: String = "",
    val symbol: String = "",
    val txnType: String = "",
    val quantity: Int = 0,
    val fillPrice: Double = 0.0,
    val tradeTime: String = "",
    val tradeSerialNo: String = "",
    val remarks: String = ""
)

data class IndstocksChargesBreakdown(
    val stt: Double = 0.0,
    val exchangeCharges: Double = 0.0,
    val stampDuty: Double = 0.0,
    val sebiTurnoverCharges: Double = 0.0,
    val brokerage: Double = 10.0,
    val gst: Double = 1.8,
    val ipftCharges: Double = 0.0,
    val totalCharges: Double = 11.8
)

data class IndstocksMarginPreview(
    val requiredMargin: Double,
    val availableMargin: Double,
    val shortfall: Double,
    val brokerageEstimate: Double = 0.0,
    val spanMargin: Double = 0.0,
    val exposureMargin: Double = 0.0,
    val deliveryMargin: Double = 0.0,
    val chargesBreakdown: IndstocksChargesBreakdown? = null,
    val note: String = ""
)

data class IndstocksQuoteItem(
    val scripCode: String,
    val symbol: String,
    val lastPrice: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val prevClose: Double,
    val change: Double,
    val changePct: Double,
    val volume: Long,
    val upperCircuit: Double = 0.0,
    val lowerCircuit: Double = 0.0,
    val openInterest: Long = 0L,
    val openInterestChange: Long = 0L,
    val timestampSec: Long
)

data class IndstocksMarketDepthLevel(
    val buyQty: Double,
    val buyPrice: Double,
    val sellQty: Double,
    val sellPrice: Double
)

data class IndstocksMarketDepthSnapshot(
    val scripCode: String,
    val totalBuyQty: String = "0",
    val totalSellQty: String = "0",
    val buyPercentage: Double = 50.0,
    val sellPercentage: Double = 50.0,
    val levels: List<IndstocksMarketDepthLevel> = emptyList()
)

data class IndstocksInstrumentRow(
    val securityId: String,
    val symbol: String,
    val tradingSymbol: String,
    val segment: String,
    val exchange: String,
    val expiry: String = "",
    val strikePrice: Double = 0.0,
    val optionType: String = "",
    val instrumentType: String = "",
    val lotSize: Int = 75,
    val freezeQty: Int = 1800,
    val tickSize: Double = 0.05
) {
    val scripCode: String
        get() = when {
            segment.equals("DERIVATIVE", true) || segment.equals("FNO", true) || exchange.equals("NFO", true) -> "NFO_$securityId"
            exchange.equals("MCX", true) || segment.equals("COMMODITY", true) -> "MCX_$securityId"
            exchange.equals("BSE", true) -> "BSE_$securityId"
            else -> "NSE_$securityId"
        }
}

data class IndstocksOptionLegSecurity(
    val strikePrice: Double,
    val optionType: String,
    val securityId: String,
    val tradingSymbol: String,
    val lastPrice: Double,
    val lotSize: Int = 75
)

data class IndstocksOptionChainResult(
    val snapshot: OptionsChainSnapshot,
    val legSecurities: Map<String, IndstocksOptionLegSecurity> = emptyMap()
)

data class IndstocksOrderRequest(
    val txnType: String,
    val exchange: String = "NSE",
    val segment: String = "DERIVATIVE",
    val product: String = "MARGIN",
    val orderType: String = "LIMIT",
    val validity: String = "DAY",
    val securityId: String,
    val qty: Int,
    val price: Double = 0.0,
    val isAmo: Boolean = false,
    val algoId: String = "99999",
    val remarks: String = "NithixNiftyDesk"
)

data class IndstocksSmartOrderRequest(
    val txnType: String,
    val exchange: String = "NSE",
    val segment: String = "DERIVATIVE",
    val product: String = "MARGIN",
    val orderType: String = "LIMIT",
    val validity: String = "DAY",
    val securityId: String,
    val qty: Int,
    val price: Double = 0.0,
    val triggerPrice: Double = 0.0,
    val triggerLimitPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val targetLimitPrice: Double = 0.0,
    val stopLossPrice: Double = 0.0,
    val stopLossLimitPrice: Double = 0.0,
    val algoId: String = "99999",
    val sliceOrder: Boolean = false,
    val remarks: String = "NithixNiftyDeskGTT"
)

data class IndstocksOrderModifyRequest(
    val orderId: String,
    val segment: String = "DERIVATIVE",
    val qty: Int,
    val limitPrice: Double
)

data class IndstocksSmartOrderModifyRequest(
    val orderId: String,
    val segment: String = "DERIVATIVE",
    val algoId: String = "99999",
    val qty: Int? = null,
    val limitPrice: Double? = null,
    val triggerPrice: Double? = null,
    val triggerLimitPrice: Double? = null,
    val slTriggerPrice: Double? = null,
    val slLimitPrice: Double? = null,
    val tgtTriggerPrice: Double? = null,
    val tgtLimitPrice: Double? = null
)

data class IndstocksOrderPlacementResult(
    val orderId: String,
    val status: String,
    val message: String,
    val isSmartOrder: Boolean = false,
    val childOrderId: String? = null,
    val childOrderStatus: String? = null,
    val reason: String? = null,
    val sliceOrderIds: List<String> = emptyList()
)

data class IndstocksPriceTick(
    val mode: String,
    val instrument: String,
    val ltp: Double,
    val timestampMs: Long
)

data class IndstocksOrderUpdateEvent(
    val orderId: String,
    val entityName: String,
    val orderType: String,
    val orderStatusCode: String,
    val statusLabel: String,
    val executedPrice: Double? = null,
    val reqQuantity: Int = 0,
    val requestedLot: Int = 0,
    val filledLot: Int? = null,
    val errorMessage: String = "",
    val timestampMs: Long = 0L
)

data class IndstocksBrokerState(
    val accessToken: String = "",
    val clientId: String = "",
    val status: IndstocksConnectionStatus = IndstocksConnectionStatus.DISCONNECTED,
    val wsPriceStatus: IndstocksWebSocketStatus = IndstocksWebSocketStatus.DISCONNECTED,
    val wsOrderStatus: IndstocksWebSocketStatus = IndstocksWebSocketStatus.DISCONNECTED,
    val profile: IndstocksUserProfile? = null,
    val funds: IndstocksFundsSummary? = null,
    val holdings: List<IndstocksHolding> = emptyList(),
    val positions: List<IndstocksPosition> = emptyList(),
    val orderBook: List<IndstocksOrderRecord> = emptyList(),
    val tradeBook: List<IndstocksTradeRecord> = emptyList(),
    val availableExpiries: List<String> = emptyList(),
    val selectedExpiry: String? = null,
    val activeContractLotSize: Int = 75,
    val activeContractFreezeQty: Int = 1800,
    val executionMode: ExecutionSafetyMode = ExecutionSafetyMode.PAPER_SIMULATION,
    val liveTradingRiskAcknowledged: Boolean = false,
    val marginPreview: IndstocksMarginPreview? = null,
    val lastOrderUpdateEvent: IndstocksOrderUpdateEvent? = null,
    val totpCooldownUntilMs: Long = 0L,
    val lastSyncedAtLabel: String = "Not connected",
    val lastError: String? = null,
    val statusNotice: String? = null,
    val isBusy: Boolean = false,
    val lastLatencyMs: Long = 0L
)
