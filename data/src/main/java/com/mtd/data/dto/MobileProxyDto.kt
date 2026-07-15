package com.mtd.data.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.math.BigInteger

/**
 * Request/response DTOs for the Mobile Blockchain Proxy (`/api/mobile/v1/networks/...`).
 *
 * IMPORTANT — provisional `data` field names: the API contract pins down the BM-33 envelope and
 * the error-code table, but the per-family `data` payload shapes are documented only at the
 * contract level ("request that module by name for a follow-up trace" — proxyEngines.js). The
 * field names below are the best-effort mapping and are marked for verification against the live
 * server during Phase 1 integration. Every raw money field is [BigInteger] and therefore decoded
 * via [com.mtd.data.network.wire.BigIntegerStringAdapter] (string on the wire).
 */

// ── balances ────────────────────────────────────────────────────────────────
data class BalancesRequestDto(
    @SerializedName("address") val address: String,
    @SerializedName("assetIds") val assetIds: List<String>? = null
)

data class ProxyBalanceDto(
    // Server sends `id`; older contract draft used `assetId`.
    @SerializedName(value = "id", alternate = ["assetId"]) val assetId: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("decimals") val decimals: Int? = null,
    @SerializedName("contractAddress") val contractAddress: String? = null,
    // The live proxy emits the raw amount under `balance`; `balanceRaw` is the legacy/contract name.
    @SerializedName(value = "balance", alternate = ["balanceRaw"]) val balanceRaw: BigInteger? = null
)

data class BalancesDto(
    @SerializedName("address") val address: String? = null,
    @SerializedName("native") val native: ProxyBalanceDto? = null,
    // Live proxy returns the full list (native + tokens) under `assets`. `tokens` is the legacy name
    // for tokens-only payloads; ProxyChainDataSource prefers `assets` when present.
    @SerializedName("assets") val assets: List<ProxyBalanceDto>? = null,
    @SerializedName("tokens") val tokens: List<ProxyBalanceDto>? = null
)

// ── fee options ───────────────────────────────────────────────────────────────
// Backend returns a `tiers` object (machine keys slow/standard/fast), NOT an array. Family-specific
// fields live on each tier; cross-family top-level fields describe the unit/model. All raw amounts
// are decimal BigInteger-as-String; `satPerVByte`/`vbytes`/`estimatedSeconds` are small ints.
data class ProxyFeeTierDto(
    // EVM
    @SerializedName("gasPrice") val gasPrice: BigInteger? = null,
    @SerializedName("maxFeePerGas") val maxFeePerGas: BigInteger? = null,
    @SerializedName("maxPriorityFeePerGas") val maxPriorityFeePerGas: BigInteger? = null,
    // UTXO — may be fractional on low-fee testnets (e.g. 0.1 sat/vByte), so it is a Double.
    @SerializedName("satPerVByte") val satPerVByte: Double? = null,
    // Tron
    @SerializedName("energyRequired") val energyRequired: BigInteger? = null,
    @SerializedName("energyFeeSun") val energyFeeSun: BigInteger? = null,
    @SerializedName("feeLimitSun") val feeLimitSun: BigInteger? = null,
    // All families — total native fee for the tier, smallest unit.
    @SerializedName("estimatedCost") val estimatedCost: BigInteger? = null,
    @SerializedName("estimatedSeconds") val estimatedSeconds: Long? = null,
    // ── Context-aware estimation (nullable for backward compat) ──────────────
    // When the request carried tx context, `totalFee` is the accurate all-in native cost for this tier
    // (EVM: l2ExecutionFee + l1DataFee on rollups). Prefer it over `estimatedCost` when present.
    @SerializedName("totalFee") val totalFee: BigInteger? = null,
    @SerializedName("l1DataFee") val l1DataFee: BigInteger? = null,
    @SerializedName("l2ExecutionFee") val l2ExecutionFee: BigInteger? = null
)

data class ProxyFeeTiersDto(
    @SerializedName("slow") val slow: ProxyFeeTierDto? = null,
    @SerializedName("standard") val standard: ProxyFeeTierDto? = null,
    @SerializedName("fast") val fast: ProxyFeeTierDto? = null
)

data class FeeOptionsDto(
    @SerializedName("unit") val unit: String? = null,            // "wei" | "sun" | "sat/vByte"
    @SerializedName("feeModel") val feeModel: String? = null,    // "gas" | "energy_bandwidth"
    @SerializedName("nativeSymbol") val nativeSymbol: String? = null,
    @SerializedName("nativeDecimals") val nativeDecimals: Int? = null,
    @SerializedName("gasLimit") val gasLimit: BigInteger? = null,   // EVM fee-screen estimate
    @SerializedName("vbytes") val vbytes: Long? = null,            // UTXO assumed tx size
    // Context-aware metadata (nullable for backward compat). `isContextAware == true` means the tiers'
    // `totalFee` reflect the supplied tx context (sender/token/recipient/amount/vbytes).
    @SerializedName("isContextAware") val isContextAware: Boolean? = null,
    @SerializedName("txType") val txType: String? = null,
    // Some backends return a single context-aware total at the top level rather than per-tier.
    @SerializedName("totalFee") val totalFee: BigInteger? = null,
    @SerializedName("tiers") val tiers: ProxyFeeTiersDto? = null
)

// ── prepare (unsigned tx material) ────────────────────────────────────────────
// Per backend contract: required fields are `sender`, `recipient`, `assetId`; amount as `amountRaw`
// (base units). Native-vs-token is decided server-side from the registry — the client never sends a
// contract address. `feeLimitSun` overrides the default TRC-20 fee ceiling (Tron only).
data class PrepareTxRequestDto(
    @SerializedName("sender") val sender: String,
    @SerializedName("recipient") val recipient: String,
    @SerializedName("assetId") val assetId: String,
    @SerializedName("amountRaw") val amountRaw: BigInteger,
    @SerializedName("feeLimitSun") val feeLimitSun: BigInteger? = null,
    // Selected tier ("slow" | "standard" | "fast"); the relayer scales gas/fee_limit. Null = default.
    @SerializedName("feeLevel") val feeLevel: String? = null
)

data class ContractCallParameterDto(
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String
)

data class PrepareContractCallRequestDto(
    @SerializedName("sender") val sender: String,
    // EVM
    @SerializedName("to") val to: String? = null,
    @SerializedName("data") val data: String? = null,
    @SerializedName("valueWei") val valueWei: BigInteger? = null,
    @SerializedName("gasLimit") val gasLimit: BigInteger? = null,
    // TRON/TVM
    @SerializedName("contractAddress") val contractAddress: String? = null,
    @SerializedName("functionSelector") val functionSelector: String? = null,
    @SerializedName("parameters") val parameters: List<ContractCallParameterDto>? = null,
    @SerializedName("feeLimitSun") val feeLimitSun: BigInteger? = null,
    @SerializedName("feeLevel") val feeLevel: String? = null
)

// Decimal-string mirror of the fee for display (Q6.2). BigInteger-as-String.
data class PrepareFeeDto(
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("gasLimit") val gasLimit: BigInteger? = null,
    @SerializedName("gasPrice") val gasPrice: BigInteger? = null,
    @SerializedName("maxFeePerGas") val maxFeePerGas: BigInteger? = null,
    @SerializedName("estimatedCost") val estimatedCost: BigInteger? = null
)

// One spendable UTXO from the proxy /prepare set. NOTE: the server does NOT return `scriptPubKey`,
// a change output, an absolute fee, or perform coin selection — the client does all four (Q2.12–14).
data class ProxyUtxoDto(
    @SerializedName("txid") val txid: String? = null,
    @SerializedName("vout") val vout: Int? = null,
    @SerializedName("value") val value: BigInteger? = null,   // raw sats (BigInteger-as-String)
    @SerializedName("confirmed") val confirmed: Boolean? = null,
    @SerializedName("blockHeight") val blockHeight: Long? = null
)

data class ProxyUtxoOutputDto(
    @SerializedName("address") val address: String? = null,
    @SerializedName("value") val value: BigInteger? = null
)

data class PrepareTxDto(
    @SerializedName("assetId") val assetId: String? = null,
    @SerializedName("model") val model: String? = null,         // "account" | "utxo"
    // Polymorphic per family — EVM: hex-quantity fields {to,value,data,nonce,gasLimit,...,type};
    // Tron: the full node-built unsigned tx {txID, raw_data, raw_data_hex, visible}. Kept raw so each
    // family's send path parses exactly what it needs (and Tron can round-trip raw_data verbatim).
    @SerializedName("transaction") val transaction: JsonObject? = null,
    /** Canonical ethers unsigned serialization — sign this directly (type-correct). EVM only. */
    @SerializedName("unsignedTxHex") val unsignedTxHex: String? = null,
    @SerializedName("fee") val fee: PrepareFeeDto? = null,
    @SerializedName("amountRaw") val amountRaw: BigInteger? = null,
    // UTXO family (model == "utxo").
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("utxos") val utxos: List<ProxyUtxoDto>? = null,
    @SerializedName("outputs") val outputs: List<ProxyUtxoOutputDto>? = null,
    @SerializedName("feeRate") val feeRate: Double? = null
)

// ── broadcast (relay a locally-signed raw tx) ─────────────────────────────────
data class BroadcastRequestDto(
    @SerializedName("rawSignedTx") val rawSignedTx: String
)

// All families normalize to `txHash` (the canonical id to display + poll); no separate txId.
// `idempotent: true` marks a replay of a prior broadcast (same X-Idempotency-Key) — not a fresh send.
data class BroadcastDto(
    @SerializedName("txHash") val txHash: String? = null,
    @SerializedName("accepted") val accepted: Boolean? = null,
    @SerializedName("simulated") val simulated: Boolean? = null,
    @SerializedName("idempotent") val idempotent: Boolean = false
)

// ── status ────────────────────────────────────────────────────────────────────
// Status returns `{ txId, status, confirmations, blockNumber }` — no txHash, no feeRaw.
data class TxStatusDto(
    @SerializedName("txId") val txId: String? = null,
    @SerializedName("status") val status: String? = null,         // PENDING | CONFIRMED | FAILED
    @SerializedName("confirmations") val confirmations: Long? = null,
    @SerializedName("blockNumber") val blockNumber: Long? = null
)

// ── diagnostics (GET /networks) ───────────────────────────────────────────────
data class NetworksDto(
    @SerializedName("networks") val networks: List<String>? = null
)
