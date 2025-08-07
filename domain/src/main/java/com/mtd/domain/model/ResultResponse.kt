package com.mtd.domain.model

sealed class ResultResponse<out T> {
    data class Success<out T>(val data: T) : ResultResponse<T>()
    data class Error(val exception: Throwable) : ResultResponse<Nothing>()
}