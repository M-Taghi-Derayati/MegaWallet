package com.mtd.domain.model.gassless

import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.GaslessDisplayPolicy

sealed class FeeState {
        object Idle : FeeState()
        object Loading : FeeState()
        data class Success(val options: List<FeeOption>) : FeeState()
        data class Error(val message: String) : FeeState()
    }

enum class FeeTrend { NONE, UP, DOWN }

    sealed class SubmitState {
        object Idle : SubmitState()
        object Submitting : SubmitState()
        data class Success(val txHash: String) : SubmitState()
        data class Error(val message: String) : SubmitState()
    }

    sealed class GaslessPreviewState {
        object Idle : GaslessPreviewState()
        object Loading : GaslessPreviewState()
        data class Ready(
            val gaslessPolicy: GaslessDisplayPolicy?,
            val sponsorPolicy: GaslessDisplayPolicy?,
            val needsApprove: Boolean
        ) : GaslessPreviewState()
        data class Error(val message: String) : GaslessPreviewState()
    }

    sealed class GaslessAvailability {
        object Loading : GaslessAvailability()
        data class Available(val note: String? = null) : GaslessAvailability()
        data class Unavailable(val reason: String? = null) : GaslessAvailability()
    }