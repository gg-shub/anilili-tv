package com.shubh.anililitv.data.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationCenter {
    private val _unread = MutableStateFlow(0)
    val unread = _unread.asStateFlow()

    fun setUnread(count: Int) {
        _unread.value = count.coerceAtLeast(0)
    }
}
