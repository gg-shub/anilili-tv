package com.shubh.anililitv.ui

import kotlinx.coroutines.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
