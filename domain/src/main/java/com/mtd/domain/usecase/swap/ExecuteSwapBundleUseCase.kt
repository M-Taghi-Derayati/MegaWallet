package com.mtd.domain.usecase.swap

import com.mtd.domain.interfaceRepository.ITransactionStatusRepository
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.swap.SwapExecutionLeg
import com.mtd.domain.model.swap.SwapExecutionOutcome
import com.mtd.domain.model.swap.SwapExecutionProgress
import com.mtd.domain.model.swap.SwapExecutionRequest
import com.mtd.domain.model.swap.SwapLegPayload
import com.mtd.domain.model.swap.SwapLegProgress
import com.mtd.domain.model.swap.SwapLegStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * اجرای بستهٔ مرتبِ `prepare`.
 *
 * ترتیب و انتظار، هر دو، شرطِ درستی‌اند: پای approve باید **ماین شده باشد** تا allowance روی
 * زنجیره دیده شود؛ ارسال هم‌زمانِ هر دو پا یعنی swap با allowance صفر اجرا می‌شود و revert می‌کند.
 * پس هیچ پایی قبل از قطعی‌شدنِ پای قبلی ارسال نمی‌شود.
 *
 * انتظارِ تمام‌نشده (`maxAttempts` تمام شود بدون وضعیت نهایی) شکست نیست:
 * [SwapExecutionOutcome.Stalled] برمی‌گردد تا کاربر تراکنشی را که روی زنجیره هست «ناموفق» نبیند و
 * دوباره ارسال نکند.
 *
 * امضا محلی است؛ فقط payload امضاشده از دستگاه خارج می‌شود — همان مسیرِ
 * [IUnifiedTransferCoordinator.sendPreparedTransaction] که Send هم از آن استفاده می‌کند.
 */
class ExecuteSwapBundleUseCase @Inject constructor(
    private val transferCoordinator: IUnifiedTransferCoordinator,
    private val transactionStatusRepository: ITransactionStatusRepository
) {

    operator fun invoke(request: SwapExecutionRequest): Flow<SwapExecutionProgress> = flow {
        var progress = SwapExecutionProgress(
            legs = request.legs.map { SwapLegProgress(kind = it.kind) }
        )
        emit(progress)

        request.legs.forEachIndexed { index, leg ->
            progress = progress.updateLeg(index, activeIndex = index) {
                it.copy(status = SwapLegStatus.SUBMITTING)
            }
            emit(progress)

            val txId = when (val result = transferCoordinator.sendPreparedTransaction(leg.toParams(request))) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> {
                    emit(
                        progress
                            .updateLeg(index) { it.copy(status = SwapLegStatus.FAILED) }
                            .copy(
                                activeIndex = null,
                                outcome = SwapExecutionOutcome.Failed(index, result.exception)
                            )
                    )
                    return@flow
                }
            }

            progress = progress.updateLeg(index) {
                it.copy(status = SwapLegStatus.CONFIRMING, txId = txId)
            }
            emit(progress)

            val finalStatus = transactionStatusRepository
                .observeStatus(networkId = request.networkId, txId = txId)
                .firstOrNull { it.isFinal }

            when {
                finalStatus == null -> {
                    emit(
                        progress
                            .updateLeg(index) { it.copy(status = SwapLegStatus.UNCONFIRMED) }
                            .copy(
                                activeIndex = null,
                                outcome = SwapExecutionOutcome.Stalled(index, txId)
                            )
                    )
                    return@flow
                }

                finalStatus.isFailed -> {
                    emit(
                        progress
                            .updateLeg(index) { it.copy(status = SwapLegStatus.FAILED) }
                            .copy(
                                activeIndex = null,
                                outcome = SwapExecutionOutcome.Failed(index, SwapLegRevertedException(index))
                            )
                    )
                    return@flow
                }

                else -> {
                    progress = progress.updateLeg(index) { it.copy(status = SwapLegStatus.CONFIRMED) }
                    emit(progress)
                }
            }
        }

        val swapTxId = progress.legs.lastOrNull()?.txId.orEmpty()
        emit(progress.copy(activeIndex = null, outcome = SwapExecutionOutcome.Completed(swapTxId)))
    }

    /**
     * انتخابِ ریلِ ارسال بر مبنای خانوادهٔ **همین پا**، نه شبکه و نه بستهٔ کلی. یک بسته می‌تواند
     * پاهای متفاوت داشته باشد، و اشتباه‌گرفتنِ شکل یعنی امضای بایت‌هایی که سرور نساخته.
     */
    private fun SwapExecutionLeg.toParams(request: SwapExecutionRequest): TransactionParams =
        when (val body = payload) {
            is SwapLegPayload.Evm -> TransactionParams.Evm(
                networkId = request.networkId,
                to = body.to,
                amount = body.value,
                data = body.data,
                gasPrice = body.gasPrice,
                gasLimit = body.gasLimit
            )

            is SwapLegPayload.Tvm -> TransactionParams.TvmPrepared(
                networkId = request.networkId,
                txId = body.txId,
                rawDataJson = body.rawDataJson,
                rawDataHex = body.rawDataHex,
                visible = body.visible
            )
        }

    private fun SwapExecutionProgress.updateLeg(
        index: Int,
        activeIndex: Int? = this.activeIndex,
        transform: (SwapLegProgress) -> SwapLegProgress
    ) = copy(
        legs = legs.mapIndexed { i, leg -> if (i == index) transform(leg) else leg },
        activeIndex = activeIndex
    )
}

/** پا روی زنجیره اجرا شد و شکست خورد (وضعیت نهاییِ FAILED/REJECTED از پروکسی). */
class SwapLegRevertedException(val legIndex: Int) :
    Exception("Swap leg $legIndex reverted on chain")
