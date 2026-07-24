package com.example.nextlist.core.result

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Content<T>(
        val data: T,
        val hasPendingWrites: Boolean = false,
    ) : LoadState<T>

    data class Empty(
        val reason: String,
    ) : LoadState<Nothing>

    data class Error(
        val kind: AppError,
        val canRetry: Boolean,
    ) : LoadState<Nothing>
}
