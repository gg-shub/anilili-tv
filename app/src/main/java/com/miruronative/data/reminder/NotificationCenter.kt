package com.miruronative.data.reminder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide unread-notification count, so the Home bell can show a badge without its own
 * AniList request. Fed by whoever fetches the feed: the periodic release-sync worker in the
 * background, and the Notifications screen when it loads or marks everything read.
 */
object NotificationCenter {
    private val _unread = MutableStateFlow(0)
    val unread = _unread.asStateFlow()

    fun setUnread(count: Int) {
        _unread.value = count.coerceAtLeast(0)
    }
}
