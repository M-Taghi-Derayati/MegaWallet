package com.mtd.data.service

import com.mtd.data.dto.BalancesDto
import com.mtd.data.dto.BalancesRequestDto
import com.mtd.data.dto.BatchBalanceRequestDto
import com.mtd.data.dto.BatchNetworkBalanceDto
import com.mtd.data.dto.BroadcastDto
import com.mtd.data.dto.BroadcastRequestDto
import com.mtd.data.dto.FeeOptionsDto
import com.mtd.data.dto.HistoryRequestDto
import com.mtd.data.dto.HistoryResponseDto
import com.mtd.data.dto.MonitoringSubscribeRequestDto
import com.mtd.data.dto.MonitoringSubscribeResponseDto
import com.mtd.data.dto.NetworksDto
import com.mtd.data.dto.PrepareTxDto
import com.mtd.data.dto.PrepareContractCallRequestDto
import com.mtd.data.dto.PrepareTxRequestDto
import com.mtd.data.dto.ProxyEnvelope
import com.mtd.data.dto.TxDetailDto
import com.mtd.data.dto.TxStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Mobile Blockchain Proxy — `/api/mobile/v1` (§1.7). Polymorphic over EVM/TVM/UTXO; every
 * per-network route returns the BM-33 [ProxyEnvelope]. `POST /history` is intentionally absent
 * here (different shape + cursor) and is added in Phase 2.
 */
interface MobileProxyApiService {

    @GET("api/mobile/v1/networks")
    suspend fun networks(): Response<ProxyEnvelope<NetworksDto>>

    @POST("api/mobile/v1/networks/{networkId}/balances")
    suspend fun balances(
        @Path("networkId") networkId: String,
        @Body body: BalancesRequestDto
    ): Response<ProxyEnvelope<BalancesDto>>

    @POST("api/mobile/v1/balances/batch")
    suspend fun batchBalances(
        @Body body: BatchBalanceRequestDto
    ): Response<ProxyEnvelope<Map<String, BatchNetworkBalanceDto>>>

    // NOT BM-33-enveloped — a page spans many networks.
    @POST("api/mobile/v1/history")
    suspend fun history(
        @Body body: HistoryRequestDto
    ): Response<HistoryResponseDto>

    // Batch monitoring enrollment (TASK-32). NOT BM-33-enveloped — one call spans many networks;
    // durable + idempotent; max 25 pairs/call (chunk beyond). Auth: Bearer JWT (proxy:write-ish).
    @POST("api/mobile/v1/monitoring/subscribe")
    suspend fun monitoringSubscribe(
        @Body body: MonitoringSubscribeRequestDto
    ): Response<MonitoringSubscribeResponseDto>

    @GET("api/mobile/v1/networks/{networkId}/fees/options")
    suspend fun feeOptions(
        @Path("networkId") networkId: String,
        // Optional transaction context — enables context-aware fee estimation (EVM L1+L2, TRON energy).
        // All omitted when null → backend falls back to its blind/default estimate (backward compatible).
        @Query("sender") sender: String? = null,
        @Query("recipient") recipient: String? = null,
        @Query("tokenAddress") tokenAddress: String? = null,
        @Query("amount") amount: String? = null,
        @Query("contractData") contractData: String? = null,
        @Query("vbytes") vbytes: Int? = null
    ): Response<ProxyEnvelope<FeeOptionsDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/prepare")
    suspend fun prepareTransaction(
        @Path("networkId") networkId: String,
        @Body body: PrepareTxRequestDto
    ): Response<ProxyEnvelope<PrepareTxDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/prepare-contract-call")
    suspend fun prepareContractCall(
        @Path("networkId") networkId: String,
        @Body body: PrepareContractCallRequestDto
    ): Response<ProxyEnvelope<PrepareTxDto>>

    @POST("api/mobile/v1/networks/{networkId}/transactions/broadcast")
    suspend fun broadcastTransaction(
        @Path("networkId") networkId: String,
        @Body body: BroadcastRequestDto
    ): Response<ProxyEnvelope<BroadcastDto>>

    @GET("api/mobile/v1/networks/{networkId}/transactions/{txId}/status")
    suspend fun transactionStatus(
        @Path("networkId") networkId: String,
        @Path("txId") txId: String
    ): Response<ProxyEnvelope<TxStatusDto>>

    // On-demand full fee/energy/gas — proxy of gettransactioninfobyid (TRON) /
    // eth_getTransactionReceipt (EVM). Call LAZILY on tx-open only (never per list row); settled
    // results are cached server-side. PENDING → feeRaw:null.
    @GET("api/mobile/v1/networks/{networkId}/transactions/{txId}/detail")
    suspend fun transactionDetail(
        @Path("networkId") networkId: String,
        @Path("txId") txId: String
    ): Response<ProxyEnvelope<TxDetailDto>>
}
